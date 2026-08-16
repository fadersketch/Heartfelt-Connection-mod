package com.heartfelt.connection.protect;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.CallResponseCompat;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 孕期 / 产后保护(v1.1.0;重构:A0 包名修正 + A9 解析重试 + A3 UUID 比较)。
 *
 * 痛点:爱憎分明的 HungerManager 饥饿 ≤9 每秒 2 点伤害、≥91 每秒 1 点
 * "吃撑"伤害——怀孕女仆没有豁免,可能饿到濒死;产后虚弱叠加更糟。
 *
 * 做法:每 N tick 扫描,怀孕中或产后恢复期内的女仆:
 * - 饥饿 <25 → 提到 25(低于饥饿伤害阈值 9,且不在"非常饿"档)
 * - 饥饿 >85 → 压回 85(不触发吃撑伤害)
 *
 * v1.1.0:
 * - A0:HungerData 走 CallResponseCompat(真实包名 com.github.JumDa5he)
 * - A9:解析只缓存成功——早期调用时 callresponse 尚未加载,之后自动恢复
 * - A3:主人判定走 DialogueDispatcher(UUID 比较)
 * - 扫描间隔进配置
 */
public class PregnancyProtectionManager {
    private static final float FLOOR = 25.0f;
    private static final float CEIL = 85.0f;

    private int tick = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        if (++this.tick < HeartfeltConfig.PREGNANCY_SCAN_INTERVAL.get()) {
            return;
        }
        this.tick = 0;
        long gameTime = server.m_129921_();
        for (ServerLevel level : server.m_129785_()) {
            for (ServerPlayer player : level.m_6907_()) {
                for (EntityMaid maid : level.m_45976_(EntityMaid.class, player.m_20191_().m_82400_(48.0))) {
                    if (!maid.m_6084_() || !DialogueDispatcher.isOwner(maid, player)) {
                        continue; // A3:UUID 比较
                    }
                    if (!RelationshipExemption.isPregnantOrPostpartum(maid, gameTime)) {
                        continue;
                    }
                    try {
                        float hunger = CallResponseCompat.hungerOf(maid);
                        if (hunger < FLOOR) {
                            CallResponseCompat.hungerSet(maid, FLOOR);
                        } else if (hunger > CEIL) {
                            CallResponseCompat.hungerSet(maid, CEIL);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
