package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/**
 * 特殊奶增强(v1.3.1)——maidmarriage `SpecialMilkBucketItem` 对玩家无效果修正。
 *
 * maidmarriage 的特殊奶(special_milk_bucket,产后哺乳期产出)目前:
 * - 右键女仆 → 缩短女儿成长(handleSpecialMilk,原版逻辑,不动);
 * - 玩家自己喝 → 仅继承 MilkBucketItem(解 buff),没有额外效果。
 *
 * 本 mixin 包裹其 finishUsingItem(m_5922_):
 * - 玩家饮用完成时 → 生命回复 I(20 秒,和普通牛奶一样先解负面 buff——原版已解,
 *   此处补回复);
 * - 找主人最近的心契女仆(妻子/恋人/女儿)发一条特殊系统消息(galgame 化:
 *   "她看着你喝下她为你准备的奶,脸一下子红了");
 * - 写记忆:heartfelt_special_milk_at/count(Promaid 记忆桥可读)。
 *
 * v1.4.4 修复(maidmarriage 2.3.0 兼容):2.3.0 的 SpecialMilkBucketItem 只声明了
 * 构造器与 appendHoverText(m_7373_),不再覆写 finishUsingItem(m_5922_)——
 * @WrapMethod 只能包裹目标类【自己声明】的方法,打在子类上会
 * "could not find any targets matching 'm_5922_'" 启动崩溃。
 * → 注入目标改为父类 MilkBucketItem(m_5922_ 声明处),包裹后运行时用
 * instanceof 过滤:只有 SpecialMilkBucketItem 才生效,普通牛奶桶原样返回。
 */
@Mixin(net.minecraft.world.item.MilkBucketItem.class)
public abstract class SpecialMilkBucketMixin {

    /** 生命回复 I 时长(tick,默认 400=20 秒) */
    private static final int REGENERATION_TICKS = 400;

    /** 特殊奶类探测缓存(类不在编译 classpath → 字符串反射,只探测一次) */
    private static Class<?> specialMilkCls = null;
    private static boolean specialMilkProbed = false;

    @WrapMethod(method = "m_5922_")
    private ItemStack heartfelt$afterDrink(ItemStack stack, Level level, LivingEntity drinker,
            Operation<ItemStack> original) {
        ItemStack result = original.call(stack, level, drinker);
        if (!isSpecialMilkBucket(stack) || level.f_46443_ || !(drinker instanceof ServerPlayer player)) {
            return result;
        }
        // 生命回复 I(与普通牛奶一样先解负面 buff——MilkBucketItem 原版已调 curePotionEffects)
        player.m_147207_(new MobEffectInstance(MobEffects.f_19604_, REGENERATION_TICKS, 0), null);
        // 女仆特殊系统消息:找主人最近的心契女仆(妻子/恋人/女儿)
        EntityMaid maid = nearestBoundMaid(player, level);
        // 写记忆(女仆 NBT 为主——Promaid 桥读女仆侧;玩家 NBT 兜底)
        long now = level.m_46467_();
        if (maid != null) {
            maid.getPersistentData().m_128356_(HeartfeltTags.SPECIAL_MILK_AT, now);
            int count = maid.getPersistentData().m_128451_(HeartfeltTags.SPECIAL_MILK_COUNT) + 1;
            maid.getPersistentData().m_128405_(HeartfeltTags.SPECIAL_MILK_COUNT, count);
            // v1.3.2:按次数分级(最多三档);妻子=妈妈口吻,其余通用口吻
            // v1.5.42:统一换字出口——非妻子档(恋人)的"主人"按关系替换为"亲爱的"
            boolean isMother = RelationshipExemption.isMarried(maid);
            com.heartfelt.connection.prompt.ChatNameFilter.sendTo(player, maid,
                    PromptTexts.specialMilkMessage(maid.m_7755_().getString(), count, isMother));
            // 女仆侧气泡(她对这件事的回应;按次数分级;v1.5.39 妻子用"丈夫"称呼)
            maid.getChatBubbleManager().addTextChatBubble(
                    PromptTexts.specialMilkMaidBubble(maid.m_7755_().getString(), count, isMother));
        }
        player.getPersistentData().m_128356_(HeartfeltTags.SPECIAL_MILK_AT, now);
        int playerCount = player.getPersistentData().m_128451_(HeartfeltTags.SPECIAL_MILK_COUNT);
        player.getPersistentData().m_128405_(HeartfeltTags.SPECIAL_MILK_COUNT, playerCount + 1);
        return result;
    }

    /** 运行时过滤:只对 maidmarriage 特殊奶生效(普通牛奶桶原样返回) */
    private static boolean isSpecialMilkBucket(ItemStack stack) {
        if (stack == null || stack.m_41619_()) {
            return false;
        }
        if (!specialMilkProbed) {
            specialMilkProbed = true;
            try {
                specialMilkCls = Class.forName("com.example.maidmarriage.item.SpecialMilkBucketItem");
            } catch (Throwable t) {
                specialMilkCls = null;
            }
        }
        return specialMilkCls != null && specialMilkCls.isInstance(stack.m_41720_());
    }

    /** 主人 48 格内最近的心契女仆(妻子 > 恋人 > 女儿 > 普通);无则 null */
    private static EntityMaid nearestBoundMaid(ServerPlayer player, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        List<EntityMaid> maids = serverLevel.m_6443_(EntityMaid.class,
                player.m_20191_().m_82400_(48.0),
                e -> e.m_6084_() && e.m_21824_() && DialogueDispatcher.isOwner(e, player));
        EntityMaid best = null;
        int bestRank = Integer.MAX_VALUE;
        for (EntityMaid m : maids) {
            int rank = maidRank(m);
            if (rank < bestRank) {
                bestRank = rank;
                best = m;
            }
        }
        return best;
    }

    /** 关系优先级:妻子 0 / 恋人 1 / 女儿 2 / 普通 3 */
    private static int maidRank(EntityMaid maid) {
        if (RelationshipExemption.isMarried(maid)) {
            return 0;
        }
        if (RelationshipExemption.isConfessed(maid)) {
            return 1;
        }
        if (RelationshipExemption.isChild(maid)) {
            return 2;
        }
        return 3;
    }
}
