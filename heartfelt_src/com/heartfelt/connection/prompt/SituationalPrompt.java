package com.heartfelt.connection.prompt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.CallResponseCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

/**
 * 情境变量扩充(v1.3.0,P2)——饥饿/地点/时间/主人状态进提示词。
 *
 * 让 LLM 的回应能感知女仆与主人的【当下处境】:
 * - 饥饿(callresponse HungerData,0-100,低=饿)
 * - 时间(白天/夜晚)
 * - 地点(biome 名 + 天气)
 * - 主人状态(血量百分比/饥饿/在线)
 *
 * 全部软读取:未装 callresponse 时饥饿读不到 → 跳过,不产生噪声。
 * 注入入口:SmartPromptAppender.build() 末尾追加本段。
 */
public final class SituationalPrompt {
    private SituationalPrompt() {
    }

    /** 生成情境段;无有效信息返回空串 */
    public static String build(EntityMaid maid) {
        StringBuilder sb = new StringBuilder();
        // 饥饿(callresponse;未装时 hungerOf 兜底 50 → 不写)
        float hunger = CallResponseCompat.hungerOf(maid);
        if (hunger >= 0.0f && hunger <= 100.0f && hunger < 40.0f) {
            sb.append("你现在很饿(").append((int) hunger).append("/100),说话会有点没精神。");
        } else if (hunger > 85.0f) {
            sb.append("你刚吃饱,心情不错。");
        }
        // 时间 + 地点 + 天气
        if (maid.m_9236_() instanceof ServerLevel level) {
            long dayTime = level.m_46468_();
            boolean night = dayTime >= 13000L && dayTime < 23000L;
            if (night) {
                sb.append("现在是夜晚,");
            } else {
                sb.append("现在是白天,");
            }
            BlockPos pos = maid.m_20183_();
            Holder<Biome> biome = level.m_204166_(pos);
            String biomeName = "未知地带";
            try {
                biomeName = biome.m_203543_().map(k -> k.m_135782_().toString()).orElse(biomeName);
            } catch (Exception ignored) {
            }
            sb.append("你们在 ").append(biomeName)
                    .append(level.m_46722_(1.0f) >= 0.2f ? "，正在下雨" : "").append("。");
        }
        // 主人状态
        LivingEntity owner = maid.m_269323_();
        if (owner instanceof Player player) {
            int hpPct = (int) (player.m_21223_() / Math.max(1.0f, player.m_21233_()) * 100.0f);
            int food = player.m_36324_().m_38702_();
            if (hpPct < 40) {
                sb.append("主人受伤了(").append(hpPct).append("% 生命),你很担心。");
            } else if (food < 8) {
                sb.append("主人饿了,你想给他找点吃的。");
            } else {
                sb.append("主人状态不错。");
            }
        }
        if (sb.length() == 0) {
            return "";
        }
        return "## Situation (Heartfelt-connection)\n" + sb;
    }
}
