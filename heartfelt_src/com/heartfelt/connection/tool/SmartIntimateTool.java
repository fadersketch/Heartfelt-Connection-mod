package com.heartfelt.connection.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.compat.ReflectUtil;
import com.heartfelt.connection.tags.HeartfeltTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * smart_intimate(v1.5.9;v1.1.0 重构:亲密交互反射收敛到 MaidMarriageCompat)。
 *
 * 动作:hug(拥抱)/ kiss(亲吻)/ lap(膝枕)/ pet(摸头),
 * mode=start(默认)/ stop(hug/lap 支持松开)。
 *
 * 零硬依赖:ModList 检测 maidmarriage,Manager 静态方法经 compat 反射调用,
 * 未安装时工具返回明确提示而不是崩溃。
 */
public class SmartIntimateTool implements ITool<SmartIntimateTool.Result> {
    public static final String TOOL_ID = "smart_intimate";

    private static final String TOOL_DESC = "Use this when the user asks for intimate interaction with the maid: "
            + "hug (拥抱), kiss (亲吻), lap pillow (膝枕), pet head (摸头). "
            + "action must be one of: hug, kiss, lap, pet. mode: start (default) or stop (hug/lap only). "
            + "Requires the Heart Pact mod (心契誓约) to be installed.\n"
            + "Favorability requirements (aligned with Heart Pact's RelationshipThresholds: "
            + "PET_UNLOCK=32, HUG_UNLOCK=64, DATING_UNLOCK=128, MARRIAGE_UNLOCK=192; "
            + "check the Favorability level in <context> before calling):\n"
            + "- pet (摸头): 32+\n"
            + "- hug (拥抱): 64+\n"
            + "- lap (膝枕): 128+ (dating line)\n"
            + "- kiss (亲吻): 192+ (marriage line)\n"
            + "If the maid's favorability is below the requirement, DO NOT call the tool; "
            + "instead decline in character as the maid (she is still shy/estranged).";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("action").forGetter(Result::action),
            Codec.STRING.optionalFieldOf("mode", "start").forGetter(Result::mode)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return TOOL_DESC;
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties("action", StringParameter.create()
                .setDescription("hug (拥抱) / kiss (亲吻) / lap (膝枕) / pet (摸头)")
                .addEnumValues("hug", "kiss", "lap", "pet"));
        root.addProperties("mode", StringParameter.create()
                .setDescription("start (默认) 开始互动；stop 结束（hug/lap 支持）")
                .addEnumValues("start", "stop"));
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolId, Result result, LLMCallback callback) {
        if (!ModList.get().isLoaded(HeartfeltTags.MM_ID)) {
            return callback.addToolResult("心契誓约（maidmarriage）未安装，无法执行亲密互动", toolId);
        }
        EntityMaid maid = callback.getMaid();
        if (!(maid.m_269323_() instanceof ServerPlayer player)) {
            return callback.addToolResult("主人必须是玩家才能进行亲密互动", toolId);
        }
        UUID maidId = maid.m_20148_();
        String action = result.action();
        boolean stop = "stop".equals(result.mode());
        // v1.5.55:LLM 防乱伦行为层——女儿(任意阶段)禁恋爱向动作(kiss 亲吻/lap 膝枕);
        // 幼女禁全部亲密动作(摸头除外——1.5.52 已有婴儿旁白气泡);拒绝结果由 LLM 转述
        if (com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)) {
            if ("kiss".equals(action) || "lap".equals(action)) {
                return callback.addToolResult("她是你的女儿——不能对她做亲吻/膝枕这类恋人间的动作。", toolId);
            }
            if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(maid) && !"pet".equals(action)) {
                return callback.addToolResult("她还太小了，不能做这种互动。", toolId);
            }
        }
        // 好感度门槛兜底——不足时不执行动作,让 LLM 以女仆身份委婉拒绝
        int favor = maid.getFavorability();
        String requirement = null;
        switch (action) {
            case "pet":
                if (favor < 32) {
                    requirement = "摸头需要好感 32 以上（她还太陌生）";
                }
                break;
            case "hug":
                if (favor < 64) {
                    requirement = "拥抱需要好感 64 以上（她还太陌生）";
                }
                break;
            case "lap":
                if (favor < 128) {
                    requirement = "膝枕需要好感 128 以上（恋爱线）";
                }
                break;
            case "kiss":
                if (favor < 192) {
                    requirement = "亲吻需要好感 192 以上（婚姻线）";
                }
                break;
            default:
                return callback.addToolResult("未知动作：" + action + "，可选 hug/kiss/lap/pet", toolId);
        }
        if (!stop && requirement != null) {
            return callback.addToolResult(requirement + "，当前好感 " + favor + "。"
                    + "请以女仆的身份委婉拒绝主人的请求，并建议先培养感情。", toolId);
        }
        try {
            switch (action) {
                case "hug": {
                    if (stop) {
                        Method m = MaidMarriageCompat.hugStop();
                        if (m == null) {
                            return callback.addToolResult("心契誓约版本不兼容：拥抱停止不可用", toolId);
                        }
                        ReflectUtil.invokeStatic(m, player, maid);
                    } else {
                        // v1.5.361:反编译实证 handleHugPoseToggle 要求玩家处于交互会话
                        // (PLAYER_TO_SESSION.get 无会话静默 return)——LLM 在玩家未开交互
                        // 面板时调用,拥抱什么都不做。先确保会话存在(无会话时自动开启)。
                        MaidMarriageCompat.ensureInteraction(player, maid);
                        Method m = MaidMarriageCompat.hugToggle();
                        if (m == null) {
                            return callback.addToolResult("心契誓约版本不兼容：拥抱不可用", toolId);
                        }
                        ReflectUtil.invokeStatic(m, player, maidId);
                    }
                    break;
                }
                case "kiss": {
                    Method m = MaidMarriageCompat.kiss();
                    if (m == null) {
                        return callback.addToolResult("心契誓约版本不兼容：亲吻不可用", toolId);
                    }
                    ReflectUtil.invokeStatic(m, player, maidId);
                    break;
                }
                case "lap": {
                    if (stop) {
                        Method m = MaidMarriageCompat.lapExit();
                        if (m == null) {
                            return callback.addToolResult("心契誓约版本不兼容：膝枕停止不可用", toolId);
                        }
                        ReflectUtil.invokeStatic(m, player);
                    } else {
                        Method m = MaidMarriageCompat.lapStart();
                        if (m == null) {
                            return callback.addToolResult("心契誓约版本不兼容：膝枕不可用", toolId);
                        }
                        ReflectUtil.invokeStatic(m, player, maidId);
                    }
                    break;
                }
                case "pet": {
                    Method m = MaidMarriageCompat.pet();
                    if (m == null) {
                        return callback.addToolResult("心契誓约版本不兼容：摸头不可用", toolId);
                    }
                    ReflectUtil.invokeStatic(m, player, maidId);
                    break;
                }
                default:
                    return callback.addToolResult("未知动作：" + action + "，可选 hug/kiss/lap/pet", toolId);
            }
        } catch (Exception e) {
            return callback.addToolResult("亲密互动执行失败：" + e.getCause(), toolId);
        }
        // v1.5.98:情感引擎喂脉冲——成功亲密互动(拥抱/亲吻/膝枕/摸头)
        com.heartfelt.connection.affect.AffectStateManager.onIntimate(maid);
        return callback.addToolResult("已执行" + action + "互动", toolId);
    }

    public record Result(String action, String mode) {
    }
}
