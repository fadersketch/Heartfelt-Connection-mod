package com.heartfelt.connection.affect;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 情感引擎接入层(v1.5.98)——heartfelt 版 AffectStateManager。
 *
 * 职责:
 * - 从现有事件点喂脉冲(被打/喂食/亲密/告白/破裂/哀悼/思慕等,由各调用方
 *   在本类 expose 的 applyXxx 上触发);
 * - 每 tick 周期做【安静恢复】(QUIET_RECOVERY)——冲突/债缓慢消退、思慕升高;
 * - NBT 读写(女仆 ForgeData 的 heartfelt_affect 子标签,跨实体重建不丢);
 * - prompt 注入文本(brief + 表达建议,由 SmartPromptAppender 追加)。
 *
 * 与现有机制的关系:纯增量。不影响 maidmarriage 确认关系、TLM 好感、
 * 心情、伤心窗口/哀悼/思慕等现有逻辑——情感快照是它们的"连续情绪投影"。
 */
@Mod.EventBusSubscriber(modid = "heartfelt_connection")
public final class AffectStateManager {
    private static final AffectEngine ENGINE = new AffectEngine();
    /** 安静恢复间隔(tick,默认 100=5 秒) */
    private static final int RECOVERY_INTERVAL = 100;
    /** 安静恢复脉冲强度(对齐 MaidSoulCore recoverAfterQuietTime 的 30——
     *  冲突/债每 5 秒衰减 0.03×0.3≈0.009,被打一次约 2 分钟缓过来,
     *  重创(冲突 0.9)约 8 分钟完全平复) */
    private static final int RECOVERY_INTENSITY = 30;

    private AffectStateManager() {
    }

    // ==================== 读取 ====================

    /** 读当前情感快照(无则返回默认初始态,不落盘) */
    public static AffectProfile profileOf(EntityMaid maid) {
        return AffectProfile.fromTag(maid.getPersistentData());
    }

    /** 供 SmartPromptAppender 注入:一句当前情绪 + 表达建议 */
    public static String buildPromptBlock(EntityMaid maid) {
        if (maid == null) {
            return "";
        }
        AffectProfile p = profileOf(maid);
        return "【当前情绪】" + p.brief() + "。" + p.replyStyleAdvice();
    }

    // ==================== 事件入口(各调用方触发) ====================

    public static void apply(EntityMaid maid, AffectEventKind kind, int intensity) {
        if (maid == null || maid.m_9236_().f_46443_) {
            return; // 只服务端演算
        }
        try {
            CompoundTag tag = maid.getPersistentData();
            AffectProfile profile = AffectProfile.fromTag(tag);
            ENGINE.apply(profile, kind, intensity);
            profile.saveTo(tag);
        } catch (Exception ignored) {
        }
    }

    /** 玩家打女仆(近战/远程) */
    public static void onHurtByOwner(EntityMaid maid) {
        apply(maid, AffectEventKind.MAID_HURT_BY_OWNER, 75);
    }

    /** 玩家喂食/送礼 */
    public static void onGift(EntityMaid maid) {
        apply(maid, AffectEventKind.OWNER_AFFECTION, 55);
    }

    /** 亲密互动(拥抱/亲吻/膝枕/摸头) */
    public static void onIntimate(EntityMaid maid) {
        apply(maid, AffectEventKind.INTIMATE_INTERACTION, 60);
    }

    /** 告白成功 */
    public static void onConfessionSuccess(EntityMaid maid) {
        apply(maid, AffectEventKind.CONFESSION_SUCCESS, 85);
    }

    /** 告白被拒 */
    public static void onConfessionFailed(EntityMaid maid) {
        apply(maid, AffectEventKind.CONFESSION_FAILED, 70);
    }

    /** 关系破裂 */
    public static void onHeartbroken(EntityMaid maid) {
        apply(maid, AffectEventKind.HEARTBROKEN, 90);
    }

    // ==================== 安静恢复(周期) ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.m_129921_() % RECOVERY_INTERVAL != 0) {
            return;
        }
        // 遍历所有在线玩家的关系女仆做安静恢复(48 格内)——只在主人附近演算,
        // 远处女仆静置不消耗(她们的情绪本来就该"停滞在离开时"的状态)
        for (net.minecraft.server.level.ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : com.heartfelt.connection.dialogue.DialogueDispatcher.maidsOf(player, 48)) {
                try {
                    CompoundTag tag = maid.getPersistentData();
                    AffectProfile profile = AffectProfile.fromTag(tag);
                    ENGINE.apply(profile, AffectEventKind.QUIET_RECOVERY, RECOVERY_INTENSITY);
                    profile.saveTo(tag);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
