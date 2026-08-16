package com.heartfelt.connection.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.CallResponseCompat;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.List;
import java.util.Set;

/**
 * 反击背叛女仆(v1.5.26;v1.1.0 A0 包名修正 + A10 性能调优)。
 *
 * **战斗状态**的女仆(当前任务是攻击类任务)会**主动攻击**附近的背叛女仆。
 *
 * 规则:
 * - **仅战斗状态**:当前任务是攻击类任务(近战/弓/弩/三叉戟/弹幕/枪械)
 * - **其余状态不做处理**;自保逃跑中(maid_smart_preserving 置位)不整改
 * - 优先级走配置(默认 150;低于自保 250、高于 TLM core 最高 99)
 *
 * v1.1.0:
 * - A0:isBetraying 走 CallResponseCompat(真实包名 com.github.JumDa5he)
 * - A10:扫描间隔进配置(默认 40 tick);先 8 格快探命中才做 24 格全量——
 *   后宫规模大时避免每 tick 全量遍历
 */
public class AttackBetrayerBehavior extends Behavior<EntityMaid> {
    /** 快探半径:8 格内无背叛女仆直接跳过全量扫描 */
    private static final double QUICK_PROBE_RANGE = 8.0;
    private static final double RANGE = 24.0;

    /** TLM 攻击类任务 UID(战斗状态判定) */
    private static final Set<ResourceLocation> ATTACK_TASK_UIDS = Set.of(
            ResourceLocation.parse("touhou_little_maid:attack"),
            ResourceLocation.parse("touhou_little_maid:ranged_attack"),
            ResourceLocation.parse("touhou_little_maid:crossbow_attack"),
            ResourceLocation.parse("touhou_little_maid:trident_attack"),
            ResourceLocation.parse("touhou_little_maid:danmaku_attack"),
            ResourceLocation.parse("touhou_little_maid:gun_attack")
    );

    private int scanCooldown = 0;

    public AttackBetrayerBehavior() {
        super(java.util.Collections.emptyMap());
    }

    @Override
    protected boolean m_6114_(ServerLevel level, EntityMaid maid) {
        if (!isCombatTask(maid) || isSelfPreserving(maid)) {
            return false; // 非战斗状态不处理;自保逃跑中不整改
        }
        if (this.scanCooldown-- > 0) {
            return false;
        }
        this.scanCooldown = HeartfeltConfig.BETRAYER_SCAN_INTERVAL.get();
        return this.findBetrayer(maid) != null;
    }

    @Override
    protected void m_6725_(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!isCombatTask(maid) || isSelfPreserving(maid)) {
            return;
        }
        LivingEntity betrayer = this.findBetrayer(maid);
        if (betrayer == null) {
            maid.m_6710_(null);
            maid.m_6274_().m_21936_(MemoryModuleType.f_26372_);
            return;
        }
        maid.m_6710_(betrayer);
        maid.m_6274_().m_21879_(MemoryModuleType.f_26372_, betrayer);
    }

    @Override
    protected boolean m_6737_(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!isCombatTask(maid) || isSelfPreserving(maid)) {
            return false; // 切走战斗任务 / 自保接管 → 让位
        }
        LivingEntity target = maid.m_5448_();
        return target instanceof EntityMaid other && other.m_6084_() && isBetraying(other);
    }

    /** 是否战斗状态:当前任务是攻击类任务 */
    private static boolean isCombatTask(EntityMaid maid) {
        try {
            if (maid.getTask() == null) {
                return false;
            }
            return ATTACK_TASK_UIDS.contains(maid.getTask().getUid());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSelfPreserving(EntityMaid maid) {
        return maid.getPersistentData().m_128471_(HeartfeltTags.SELF_PRESERVING);
    }

    /** 附近最近的背叛女仆;无返回 null(A10:8 格快探 → 24 格全量) */
    private LivingEntity findBetrayer(EntityMaid maid) {
        // 快探:8 格内没有任何女仆直接返回,省去全量扫描
        List<EntityMaid> nearby = maid.m_9236_().m_45976_(
                EntityMaid.class, maid.m_20191_().m_82400_(QUICK_PROBE_RANGE));
        boolean anyMaid = false;
        for (EntityMaid m : nearby) {
            if (m != maid && m.m_6084_()) {
                anyMaid = true;
                break;
            }
        }
        if (!anyMaid) {
            return null;
        }
        List<EntityMaid> maids = maid.m_9236_().m_45976_(
                EntityMaid.class, maid.m_20191_().m_82400_(RANGE));
        EntityMaid best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityMaid m : maids) {
            if (m == maid || !m.m_6084_() || !isBetraying(m)) {
                continue;
            }
            double d = maid.m_20270_(m);
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        return best;
    }

    /** 背叛判定(走 CallResponseCompat,A0 包名已修正) */
    public static boolean isBetraying(EntityMaid maid) {
        return CallResponseCompat.isBetraying(maid);
    }
}
