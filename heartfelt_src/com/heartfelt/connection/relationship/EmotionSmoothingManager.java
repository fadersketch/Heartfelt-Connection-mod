package com.heartfelt.connection.relationship;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.CallResponseCompat;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 好感度对信任/恐惧的中和缓和(v1.2.1,用户确认)。
 *
 * 背景:确认关系后信任/恐惧冻结且不再作为依据,与好感度脱钩;为避免两套数值
 * 完全无关,让好感度成为信任/恐惧的"重力场":
 *   每 N tick 批量一次(默认 5 秒):信任 += rate×好感/384,恐惧 -= rate×好感/384
 * —— 好感越高,信任缓慢增长、恐惧缓慢下降(满好感约 8 分钟从 0 拉满)。
 *
 * 作用范围:仅【未冻结】女仆(冻结层 = 确认关系/女儿,见 RelationshipExemption.isFrozen);
 * 冻结女仆的信任/恐惧已冻结失效,跳过(否则会经 FreezeConversion 折算成好感,双重计算)。
 *
 * 效果:高好感的女仆在数值上也自然趋向"忠诚"——中和缓和让爱憎分明的 4 条关系线
 * 在未确认阶段也大体跟随好感度,而不是一套独立的刷信任系统。
 */
public class EmotionSmoothingManager {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        int batchTicks = HeartfeltConfig.EMOTION_SMOOTH_BATCH_TICKS.get();
        if (batchTicks <= 0 || server.m_129921_() % batchTicks != 0) {
            return;
        }
        double ratePerSecond = HeartfeltConfig.EMOTION_SMOOTH_RATE_PER_SECOND.get();
        if (ratePerSecond <= 0.0) {
            return;
        }
        // 批量漂移 = 每秒速率 × 本批秒数
        float drift = (float) (ratePerSecond * (batchTicks / 20.0));
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
                // 冻结层(确认关系/女儿)跳过:信任/恐惧已冻结失效
                if (RelationshipExemption.isFrozen(maid)) {
                    continue;
                }
                int favor = maid.getFavorability();
                if (favor <= 0) {
                    continue;
                }
                float amount = drift * (favor / 384.0f);
                if (amount <= 0.0f) {
                    continue;
                }
                // 信任缓慢增长、恐惧缓慢下降(走 callresponse 官方入口;未冻结不被拦截)
                CallResponseCompat.addTrustFloat(maid, player.m_20148_(), amount);
                CallResponseCompat.addFearFloat(maid, player.m_20148_(), -amount);
            }
        }
    }
}
