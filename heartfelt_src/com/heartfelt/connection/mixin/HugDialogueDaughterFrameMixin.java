package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 女儿对话选项专属文本(v1.5.366)——maidmarriage HugActionScreen 的帧级拦截。
 *
 * 背景(反编译实证):HugDialogueRuntimeBridge.currentFrame() 是对话帧的唯一出口
 * (内部调用 HugDialogueStageFlavorComposer.apply),帧含 nodeId/speaker/text——
 * 4 个选项(聊天 communicate_chat / 讨好 communicate_flatter / 讲笑话 communicate_joke /
 * 去摇曲柄 communicate_crank)及其子节点的女仆台词全部内嵌在场景 JSON 里(不走
 * componentForMaid,旧拦截覆盖不到),幼儿/少女文本量稀少且各阶段几乎一致,
 * 成年女儿还混着普通女仆文本(用户:"让原来的普通女仆文本永远都不会触发,
 * 再添入更多专属文本;每个阶段都是专属的文本,不再复用普通女仆的文本")。
 *
 * 修复:currentFrame() RETURN 拦截——帧返回后,若当前交互女仆是女儿(ClientOnlyState
 * 从成人 HugClientState/儿童 ChildInteractionClientState 双查 UUID),按
 * 阶段×选项分类 替换【女仆台词】(speaker=女仆名,旁白/玩家提示/选项标题不动):
 * - 幼儿/少女(儿童场景):摸头 pet / 举高高 lift / 让妈妈抱抱 carry / 陪她说话 comfort
 * - 成年女儿(hug_menu 4 选项):聊天 chat / 讨好 flatter / 讲笑话 joke / 摇曲柄 crank / 摸头 pet
 * 原版普通女仆文本在女儿身上永不触发;按天轮换,每个选项每个阶段都有专属文本池。
 *
 * maidmarriage 类不在编译 classpath:纯反射访问帧(方法名反编译实证),@Pseudo + 字符串 targets。
 */
@Pseudo
@Mixin(targets = "com.example.maidmarriage.client.dialoguesystem.runtime.HugDialogueRuntimeBridge")
public abstract class HugDialogueDaughterFrameMixin {

    @Inject(method = "currentFrame", at = @At("RETURN"), cancellable = true)
    private void maidsmart$daughterFrame(CallbackInfoReturnable<Object> cir) {
        Object frame = cir.getReturnValue();
        if (frame == null) {
            return;
        }
        try {
            String node = (String) frame.getClass().getMethod("nodeId").invoke(frame);
            String category = categoryOf(node);
            if (category == null) {
                return;
            }
            EntityMaid maid = findMaid();
            if (maid == null || !RelationshipExemption.isChild(maid)) {
                return;
            }
            // 选择菜单帧(choiceNode=true,如 chat_menu/chat_low_mood 的菜单提示)不是
            // 女仆台词,不替换——只替换 sequence 台词帧(有 line.speaker 的那类)。
            boolean choiceNode = (boolean) frame.getClass().getMethod("choiceNode").invoke(frame);
            if (choiceNode) {
                return;
            }
            MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
            if (stage == null) {
                return;
            }
            // v1.5.105:INFANT 婴儿也参与替换——她不会说话,由 daughterOptionText
            // 返回旁白/动作描写池,替换掉 maidmarriage 原版"婴儿会说话"的台词。
            // 阶段语义(用户澄清):INFANT=旁白动作、JUVENILE=简单句子、
            // CHILD=小女孩口吻、ADULT=父女向。
            // 只替换女仆台词。关键:currentFrame() 返回的 speaker 是【未渲染占位符】
            // (renderTemplate 在 HugActionScreen.refreshDialogueState 才执行)。实测剧本里
            // 女仆台词有两种形态:
            //  1. speaker="${maid}" 的字面台词(儿童 pet/lift/carry/comfort 的 L1、
            //     成人 joke_X_* 反应、早安吻等);
            //  2. speaker 为空 + text="${chat_topic_life_text}" 这类【池文本模板】
            //     (成人 chat_*_result / flatter_*_result / crank_*_result——真正的女仆
            //     台词在 hug_menu_v4 池里,renderTemplate 时才展开)。
            // 旧版与女仆名(m_7755_)比较永远不匹配,女儿专属文本因此从未生效
            // (v1.5.366 的实际 bug)。旁白(speaker 空、text 为字面中文)与玩家台词
            // ("${player}")不动。
            String speaker = (String) frame.getClass().getMethod("speaker").invoke(frame);
            String text = (String) frame.getClass().getMethod("text").invoke(frame);
            if ("${player}".equals(speaker)) {
                return; // 玩家台词,不替换
            }
            boolean maidLine = "${maid}".equals(speaker);
            boolean poolTemplate = (speaker == null || speaker.isEmpty())
                    && text != null && text.startsWith("${");
            if (!maidLine && !poolTemplate) {
                return; // 旁白/未识别,保持原样
            }
            String replacement = PromptTexts.daughterOptionText(stage, category, maid);
            if (replacement == null || replacement.equals(text)) {
                return;
            }
            cir.setReturnValue(buildFrame(frame, replacement));
        } catch (Exception ignored) {
        }
    }

    /** 反射重建帧(替换 text;构造签名反编译实证:13 参) */
    private static Object buildFrame(Object frame, String newText) throws Exception {
        Class<?> c = frame.getClass();
        Object[] args = new Object[13];
        args[0] = c.getMethod("scenarioId").invoke(frame);
        args[1] = c.getMethod("nodeId").invoke(frame);
        args[2] = c.getMethod("lineIndex").invoke(frame);
        args[3] = c.getMethod("speaker").invoke(frame);
        args[4] = newText;
        args[5] = c.getMethod("narration").invoke(frame);
        args[6] = c.getMethod("portraitId").invoke(frame);
        args[7] = c.getMethod("portraitTexture").invoke(frame);
        args[8] = c.getMethod("expressionId").invoke(frame);
        args[9] = c.getMethod("animationId").invoke(frame);
        args[10] = c.getMethod("choiceNode").invoke(frame);
        args[11] = c.getMethod("ended").invoke(frame);
        args[12] = c.getMethod("choices").invoke(frame);
        Class<?>[] params = {String.class, String.class, int.class, String.class, String.class,
                String.class, String.class, String.class, String.class, String.class,
                boolean.class, boolean.class, java.util.List.class};
        return c.getConstructor(params).newInstance(args);
    }

    /** 节点 → 选项分类;未命中返回 null(不替换)
     *  v1.5.101:成年女儿"随便聊聊"等子菜单按真实节点细分——旧版把所有 chat_*
     *  归为一个大类,生活/心事/休息/时间/依赖/未来全部返回同一句。现在按
     *  chat_topic_*_result 前缀细分;天气/心情/清晨/夜晚/日常/阶段同理各归其类。
     *  v1.5.102:摸头/关系阶段/摇曲柄也按子分支细分——warm/close/dating/marriage
     *  与 hard/soft 不再共用同一池(女儿不同关系阶段话术不同,不与普通女仆共用)。 */
    private static String categoryOf(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        String n = nodeId.toLowerCase();
        // ---- 儿童场景(pet_head / lift / carry / comfort) ----
        if (n.startsWith("pet_head")) {
            return "pet";
        }
        if (n.startsWith("lift")) {
            return "lift";
        }
        if (n.startsWith("carry")) {
            return "carry";
        }
        if (n.startsWith("comfort")) {
            return "comfort";
        }
        // ---- 成年女儿摸头:按关系阶段分支(warm/close/dating/marriage) ----
        if (n.startsWith("pet_intro")) {
            if (n.contains("marriage")) {
                return "pet_marriage";
            }
            if (n.contains("dating")) {
                return "pet_dating";
            }
            if (n.contains("close")) {
                return "pet_close";
            }
            return "pet_warm";
        }
        // ---- 成年女儿:4 选项 + 随便聊聊细分 ----
        if (n.startsWith("flatter")) {
            return "flatter";
        }
        if (n.startsWith("joke")) {
            return "joke";
        }
        // 摇曲柄:hard(冷拒)/ soft(软化)分池
        if (n.contains("crank")) {
            return n.contains("hard") ? "crank_hard" : "crank_soft";
        }
        // 随便聊聊(chat_general_menu)子选项:生活/心事/休息/时间/依赖/未来
        if (n.startsWith("chat_topic_life")) {
            return "chat_life";
        }
        if (n.startsWith("chat_topic_heart")) {
            return "chat_heart";
        }
        if (n.startsWith("chat_topic_rest")) {
            return "chat_rest";
        }
        if (n.startsWith("chat_topic_time")) {
            return "chat_time";
        }
        if (n.startsWith("chat_topic_depend")) {
            return "chat_depend";
        }
        if (n.startsWith("chat_topic_future")) {
            return "chat_future";
        }
        // 天气(含 thunder/rain/clear/snow 各分支)
        if (n.startsWith("chat_weather") || n.startsWith("chat_thunder")
                || n.startsWith("chat_rain") || n.startsWith("chat_clear")
                || n.startsWith("chat_snow")) {
            return "chat_weather";
        }
        // 心情低落
        if (n.startsWith("chat_low")) {
            return "chat_mood";
        }
        // 清晨 / 夜晚 / 日常
        if (n.startsWith("chat_morning")) {
            return "chat_morning";
        }
        if (n.startsWith("chat_night")) {
            return "chat_night";
        }
        if (n.startsWith("chat_daily")) {
            return "chat_daily";
        }
        // 关系阶段话题(warm/close/dating/marriage)——女儿父女向分池
        if (n.startsWith("chat_stage")) {
            if (n.contains("marriage")) {
                return "stage_marriage";
            }
            if (n.contains("dating")) {
                return "stage_dating";
            }
            if (n.contains("close")) {
                return "stage_close";
            }
            return "stage_warm";
        }
        // 其余 chat_*(chat_v4 等)兜底
        if (n.startsWith("chat")) {
            return "chat";
        }
        return null;
    }

    /** 按当前交互状态 UUID 找女仆(成人/儿童双查,64 格内) */
    private static EntityMaid findMaid() {
        try {
            Minecraft mc = Minecraft.m_91087_();
            if (mc.f_91073_ == null || mc.f_91074_ == null) {
                return null;
            }
            java.util.UUID uuid = com.heartfelt.connection.client.ClientOnlyState.interactionMaidUuid();
            if (uuid == null) {
                return null;
            }
            for (EntityMaid maid : mc.f_91073_.m_45976_(
                    EntityMaid.class, mc.f_91074_.m_20191_().m_82400_(64.0))) {
                if (maid.m_20148_().equals(uuid)) {
                    return maid;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
