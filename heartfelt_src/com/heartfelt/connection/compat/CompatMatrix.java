package com.heartfelt.connection.compat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动兼容性探测(v1.0.2)——「永不崩,坏哪看得见」。
 *
 * <p>v1.0.2 起所有以第三方模组类为目标的 mixin 全部移入 optional 配置
 * (required=false + defaultRequire=0):前置模组(爱憎分明/心契誓约/车万女仆)
 * 更新改名后注入失败只警告、功能静默降级,不再崩游戏。代价是降级无声——
 * 本类在加载期用反射逐项检查各功能依赖的类/关键方法,把「兼容矩阵」写进日志,
 * 并缓存摘要供调整器「调试信息」展示:
 *
 * <pre>
 * [heartfelt] === 兼容矩阵 (callresponse=2.0.6 maidmarriage=2.3.0 touhou_little_maid=1.5.3) ===
 * [heartfelt] [OK]   爱憎分明·信任/恐惧读写
 * [heartfelt] [方法缺失] 爱憎分明·主动对话配额
 * </pre>
 *
 * <p>探测本身绝不抛出(整体与逐项双重 try/catch),探测失败不影响游戏运行。
 * 方法检查为「任一存在即可」(宽松语义,只防改名级断裂,不校验签名)。
 */
public final class CompatMatrix {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 探测项:功能名 + 目标类全名 + 关键方法(任一存在即 OK;留空 = 只查类存在) */
    private record Check(String feature, String className, String... methods) {
    }

    private static final List<Check> CHECKS = List.of(
            // ---- 爱憎分明(callresponse)——方法名按 2.0.6 实测(javap 核对,2.0.4 同名兼容) ----
            new Check("爱憎分明·恐惧豁免", "com.github.JumDa5he.callresponse.compat.emotion.EmotionData",
                    "addFear", "addFearFloat"),
            new Check("爱憎分明·信任豁免", "com.github.JumDa5he.callresponse.compat.emotion.EmotionData",
                    "addTrust", "addTrustFloat"),
            new Check("爱憎分明·信任/恐惧读取", "com.github.JumDa5he.callresponse.compat.emotion.EmotionData", "get"),
            new Check("爱憎分明·背叛判定/悔改", "com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager",
                    "isBetraying", "resetBetrayal"),
            new Check("爱憎分明·背叛触发拦截", "com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager",
                    "triggerBetrayal", "selectTarget"),
            new Check("爱憎分明·吃醋隔离", "com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager",
                    "driveNearbyMaids", "attackNearbyMaids"),
            new Check("爱憎分明·吃醋动作选择", "com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager",
                    "selectAction"),
            new Check("爱憎分明·专一", "com.github.JumDa5he.callresponse.compat.emotion.EmotionDevotedManager",
                    "isDevoted"),
            new Check("爱憎分明·淡忘隔离", "com.github.JumDa5he.callresponse.compat.emotion.EmotionForgettingManager",
                    "triggerForgetting"),
            new Check("爱憎分明·主动对话配额", "com.github.JumDa5he.callresponse.compat.emotion.EmotionActiveDialogue",
                    "triggerAIDialogue"),
            new Check("爱憎分明·见证配额", "com.github.JumDa5he.callresponse.compat.emotion.EmotionPassiveManager",
                    "triggerWitnessDialogue"),
            new Check("爱憎分明·饥饿速度缓存", "com.github.JumDa5he.callresponse.compat.hunger.HungerManager",
                    "applySpeedEffect"),
            new Check("爱憎分明·饥饿读写", "com.github.JumDa5he.callresponse.compat.hunger.HungerData", "get", "set"),
            // ---- 心契誓约(maidmarriage) ----
            new Check("心契誓约·任务管理", "com.example.maidmarriage.compat.MaidWorkManager"),
            new Check("心契誓约·抱娃管理", "com.example.maidmarriage.compat.MaidCarryChildManager"),
            new Check("心契誓约·对话文本汇聚", "com.example.maidmarriage.config.DialogueScriptManager", "componentForMaid"),
            new Check("心契誓约·互动界面", "com.example.maidmarriage.client.HugActionScreen"),
            new Check("心契誓约·对话运行时", "com.example.maidmarriage.client.dialoguesystem.runtime.HugDialogueRuntimeBridge"),
            new Check("心契誓约·语音包", "com.example.maidmarriage.client.voice.HeartPactVoicePlayback", "play"),
            new Check("心契誓约·对话气泡框", "com.example.maidmarriage.client.dialogueui.DialogueBoxComponent"),
            // ---- 车万女仆(TLM) ----
            new Check("车万女仆·女仆实体", "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid"),
            new Check("车万女仆·AI聊天网络包", "com.github.tartaricacid.touhoulittlemaid.network.message.ai.OpenMaidAIChatMessage"),
            new Check("车万女仆·AI聊天界面", "com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.AIChatScreen"),
            new Check("车万女仆·气泡管理", "com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager", "addTextChatBubble"),
            new Check("车万女仆·Alt+J按键", "com.github.tartaricacid.touhoulittlemaid.client.event.PressAIChatKeyEvent", "maidCheck"),
            new Check("车万女仆·AI聊天管理器", "com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager"),
            new Check("车万女仆·LLM消息", "com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage"));

    /** 最近一次探测摘要(调整器调试信息展示);未探测时为空 */
    private static volatile List<String> summaryLines = List.of();

    private CompatMatrix() {
    }

    /** 加载期探测(FMLCommonSetupEvent 调用);整体兜底,绝不抛出 */
    public static void probe() {
        try {
            doProbe();
        } catch (Throwable t) {
            LOGGER.error("[heartfelt] 兼容矩阵探测失败(不影响游戏运行)", t);
        }
    }

    private static void doProbe() {
        String versions = "callresponse=" + modVersion("callresponse")
                + " maidmarriage=" + modVersion("maidmarriage")
                + " touhou_little_maid=" + modVersion("touhou_little_maid");
        LOGGER.info("[heartfelt] === 兼容矩阵 ({}) ===", versions);
        List<String> summary = new ArrayList<>();
        summary.add("兼容矩阵 " + versions);
        List<String> missing = new ArrayList<>();
        for (Check check : CHECKS) {
            String status = checkOne(check);
            LOGGER.info("[heartfelt] [{}] {}", status, check.feature());
            if (!"OK".equals(status)) {
                missing.add("[" + status + "] " + check.feature());
            }
        }
        if (missing.isEmpty()) {
            summary.add("全部联动功能依赖检查通过");
        } else {
            summary.addAll(missing);
            summary.add("以上功能已静默降级(游戏不会崩溃);若前置模组刚更新,等待 heartfelt 适配即可");
        }
        summaryLines = List.copyOf(summary);
        LOGGER.info("[heartfelt] === 兼容矩阵结束:共 {} 项,{} 项缺失 ===", CHECKS.size(), missing.size());
    }

    /** 单项检查:OK / 类缺失 / 方法缺失(方法任一存在即可;getDeclaredMethods 含私有) */
    private static String checkOne(Check check) {
        Class<?> cls;
        try {
            cls = Class.forName(check.className());
        } catch (Throwable t) {
            return "类缺失";
        }
        if (check.methods().length == 0) {
            return "OK";
        }
        for (Method m : cls.getDeclaredMethods()) {
            for (String want : check.methods()) {
                if (m.getName().equals(want)) {
                    return "OK";
                }
            }
        }
        for (Method m : cls.getMethods()) {
            for (String want : check.methods()) {
                if (m.getName().equals(want)) {
                    return "OK";
                }
            }
        }
        return "方法缺失";
    }

    /** 前置模组版本(经 ModList 查询;反射取版本号避免编译期依赖,失败返回「未知」) */
    private static String modVersion(String modId) {
        try {
            Object container = ModList.get().getModContainerById(modId).orElse(null);
            if (container == null) {
                return "未安装";
            }
            Object info = container.getClass().getMethod("getModInfo").invoke(container);
            return String.valueOf(info.getClass().getMethod("getVersion").invoke(info));
        } catch (Throwable t) {
            return "未知";
        }
    }

    /** 摘要行(调整器「调试信息」展示):首行为版本,其后仅列缺失项;全部正常时只有两行 */
    public static List<String> summaryLines() {
        return summaryLines;
    }
}
