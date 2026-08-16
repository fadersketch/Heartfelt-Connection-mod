package com.heartfelt.connection.relationship;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.config.HeartfeltConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冻结折算(v1.2.0,用户确认的关系模型)。
 *
 * 确认关系(恋人/妻子)后,女仆的信任/恐惧数值【冻结】:
 * - 玩家造成的信任/恐惧变化【等比折算为好感变化】——情绪通道被切断,一切走好感:
 *   信任 +N → 好感 +N×ratio;信任 -N → 好感 -N×ratio;
 *   恐惧 +N → 好感 -N×ratio;恐惧 -N → 好感 +N×ratio(比率可配)
 * - 非玩家来源的变化(怪物/环境/自然衰减):只冻结、不折算、不移动数值
 * - 负向折算(扣好感)受每日上限约束(复用 FEAR_FAVOR_DAILY_CAP,防战斗/冷落打崩)
 *
 * 好感跌破告白线(128)后冻结解除 → 数值恢复移动(配合 MaidConfessionManager 的破裂检查)。
 */
public final class FreezeConversion {
    /** maid UUID -> {游戏日, 当日已扣好感}(负向折算上限) */
    private static final Map<UUID, long[]> DAILY_DEDUCT = new ConcurrentHashMap<>();

    private FreezeConversion() {
    }

    /** 审计 H-M5：女仆卸载/移除时清理日扣减表 */
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().m_5776_() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        DAILY_DEDUCT.remove(maid.m_20148_());
    }

    /** 变化来源是否为主人本人 */
    public static boolean isOwnerSource(EntityMaid maid, UUID sourceUuid) {
        LivingEntity owner = maid.m_269323_();
        return owner != null && sourceUuid != null && sourceUuid.equals(owner.m_20148_());
    }

    /** 信任变化折算(amount 与好感同向;仅玩家来源折算) */
    public static void convertTrust(EntityMaid maid, UUID sourceUuid, float amount) {
        if (!isOwnerSource(maid, sourceUuid)) {
            return; // 非玩家来源:只冻结
        }
        int delta = Math.round(amount * HeartfeltConfig.TRUST_FAVOR_RATIO.get().floatValue());
        convert(maid, delta);
    }

    /** 恐惧变化折算(amount 与好感反向:恐惧+扣好感,恐惧-加好感;仅玩家来源折算) */
    public static void convertFear(EntityMaid maid, UUID sourceUuid, float amount) {
        if (!isOwnerSource(maid, sourceUuid)) {
            return; // 非玩家来源:只冻结
        }
        int delta = Math.round(-amount * HeartfeltConfig.FEAR_FAVOR_RATIO.get().floatValue());
        convert(maid, delta);
    }

    /** 折算入口:delta>0 加好感,delta<0 扣好感(受每日上限) */
    private static void convert(EntityMaid maid, int delta) {
        if (delta == 0) {
            return;
        }
        try {
            if (delta > 0) {
                maid.getFavorabilityManager().add(delta);
            } else {
                int allowed = dailyBudget(maid, -delta);
                if (allowed > 0) {
                    maid.getFavorabilityManager().reduce(allowed);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 每日扣减预算(负向折算);cap=0 不设限 */
    private static int dailyBudget(EntityMaid maid, int want) {
        int cap = HeartfeltConfig.FEAR_FAVOR_DAILY_CAP.get();
        if (cap <= 0) {
            return want;
        }
        UUID maidId = maid.m_20148_();
        long day = maid.m_9236_().m_46467_() / 24000L;
        long[] record = DAILY_DEDUCT.get(maidId);
        if (record == null || record[0] != day) {
            record = new long[]{day, 0L};
            DAILY_DEDUCT.put(maidId, record);
        }
        if (record[1] >= cap) {
            return 0;
        }
        int allowed = Math.min(want, cap - (int) record[1]);
        record[1] += allowed;
        // 防泄漏:超过 64 只女仆时清掉非当天的旧条目
        if (DAILY_DEDUCT.size() > 64) {
            DAILY_DEDUCT.entrySet().removeIf(e -> e.getValue()[0] != day);
        }
        return allowed;
    }
}
