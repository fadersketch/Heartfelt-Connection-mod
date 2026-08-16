package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.ChildGuardManager;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * 女儿对话文本统一分流(v1.5.28 送礼婴儿版 → v1.5.52 全交互按阶段 → v1.5.56
 * 拦截点移到真正汇聚点)——maidmarriage 的女儿文本池是「儿童口吻」一套
 * (少女/幼女共用)+ 成人通用一套(成年女儿无专属池),且摸头/抱抱/送花等交互
 * 在幼女身上仍会播"有台词"的儿童文本(违背婴儿不说话规则)。
 *
 * 反编译实证(v1.5.56):RomanceSleepManager.scriptForMaid 只是
 * DialogueScriptManager.componentForMaid(EntityMaid, String, Object...) 的一行
 * 薄封装,而摸头 PetHeadManager / 抱放 MaidCarryChildManager / 拥抱 MaidHugManager /
 * 送礼 GiftManager / 学习 MaidWorkManager / 儿童互动 ChildInteractionManager 等
 * 全部直接或间接调用 componentForMaid——它是唯一真正的文本汇聚点。旧版拦
 * scriptForMaid 会漏掉直接调 componentForMaid 的调用者;改为拦 componentForMaid
 * 一次全覆盖(气泡出口 ChatBubbleManager 已被称呼换字 ChatBubbleNameFilterMixin
 * 覆盖)。
 *
 * 分流逻辑(PromptTexts.daughterDialogueText 维护文本池,按天轮换):
 * - 普通女仆:原样走原版脚本;
 * - 幼女(INFANT/JUVENILE,isTooSmall):无台词旁白(婴儿不说话规则);
 * - 少女(CHILD):小女孩口吻;成女(ADULT):父女向口吻;
 * - 妈妈台词(carry_child.infant_hold.* / child_name.success)不拦。
 *
 * maidmarriage 类不在编译 classpath:@Pseudo + 字符串 targets + optional 配置
 * (mixins.heartfelt.opt.json,required=false + defaultRequire=0)。
 */
@Pseudo
@Mixin(targets = "com.example.maidmarriage.config.DialogueScriptManager")
public abstract class ChildGiftTextMixin {

    private static final String MM_DIALOGUE = "dialogue.maidmarriage.";

    /** 签名与 maidmarriage 的 componentForMaid(EntityMaid,String,Object...) 一致 */
    @WrapMethod(method = "componentForMaid")
    private static Component heartfelt$daughterDialogue(EntityMaid maid, String key, Object[] args,
            Operation<Component> original) {
        if (key == null || !key.startsWith(MM_DIALOGUE)) {
            return original.call(maid, key, args);
        }
        boolean tooSmall = ChildGuardManager.isTooSmall(maid);
        MaidMarriageCompat.ChildStage stage = null;
        // v1.5.105:tooSmall 时也解析阶段——婴儿(INFANT)仍走旁白,幼儿(JUVENILE)
        // 要说简单句子,二者不能混为一个"旁白池"。旧版 tooSmall 时 stage=null,
        // daughterDialogueText 只能把 INFANT+JUVENILE 一起当旁白。
        if (RelationshipExemption.isChild(maid)) {
            stage = MaidMarriageCompat.childStage(maid);
        }
        if (!tooSmall && stage == null) {
            return original.call(maid, key, args); // 普通女仆,原版脚本
        }
        long day = maid.m_9236_().m_46467_() / 24000L;
        String text = PromptTexts.daughterDialogueText(maid, key, tooSmall, stage, day);
        if (text == null) {
            return original.call(maid, key, args); // 不拦截的键(妈妈台词等)
        }
        return Component.m_237113_(text);
    }
}
