package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 孩子专属技能成年后保留(v1.4.0,Bug3)。
 *
 * 原版:MaidWorkManager.isChildWorkMaid = MaidChildEntity.shouldStayChild(maid)
 * ——女儿长大(成年)后 shouldStayChild 变 false,附魔/酿药等孩子专属任务被
 * isHidden 隐藏,技能"消失"。
 *
 * 修复:出生女仆(maidmarriage_born_maid / 有血缘 UUID)无论成年与否都保留
 * 孩子工作技能;普通女仆不受影响(仍不显示)。
 *
 * 目标类不在编译 classpath → 字符串 targets;maidmarriage 是必装依赖,无需 @Pseudo。
 */
@Mixin(targets = "com.example.maidmarriage.compat.MaidWorkManager")
public abstract class MaidWorkManagerSkillRetentionMixin {

    @WrapMethod(method = "isChildWorkMaid")
    private static boolean heartfelt$isChildWorkMaid(EntityMaid maid, Operation<Boolean> original) {
        if (maid == null) {
            return false;
        }
        // 出生女仆(含成年女儿)保留孩子专属技能
        if (maid.m_19880_().contains("maidmarriage_born_maid")
                || maid.getPersistentData().m_128403_("maidmarriage_mother_uuid")
                || maid.getPersistentData().m_128403_("maidmarriage_father_uuid")
                || maid.getPersistentData().m_128403_("maidmarriage_grand_parent_uuid")) {
            return true;
        }
        return original.call(maid);
    }

    /**
     * 防重置保护:ensureDefaultFavorability 会把未初始化女仆的好感设为 64——
     * 成年女儿若从未进入过孩子工作循环(TAG_FAVOR_INITIALIZED 未设),会被重置为 64,
     * 覆盖玩家养到的高好感。已高于 64 时跳过重置。
     */
    @WrapMethod(method = "ensureDefaultFavorability")
    private static void heartfelt$keepHighFavor(EntityMaid maid, Operation<Void> original) {
        if (maid != null && maid.getFavorability() > 64) {
            maid.getPersistentData().m_128379_("maidmarriage_child_favor_initialized", true);
            return;
        }
        original.call(maid);
    }
}
