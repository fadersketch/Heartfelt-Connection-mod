package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抱起回应(v1.5.345):玩家把 妻子/女儿/恋人 抱起来时,她会对主人说一句话。
 *
 * 钩子:Forge `EntityMountEvent`——TLM 的"抱起来"(潜行右键)就是女仆骑上玩家
 * (maid.m_7998_(player)),服务端骑乘瞬间必然触发本事件(挂载方=女仆、被挂载方=玩家)。
 * maidmarriage 的举起/抱女儿走代理链、不发女仆骑玩家的骑乘事件,且它自己有剧本对话,
 * 不在此路径(避免双重回应)。
 *
 * 回应走 DialogueDispatcher.chatWithQuota(与全家互动/广播同一公共流程):
 * - 有 LLM + 配额 → 女仆用 LLM 沉浸式回应(系统提示词里已注入关系准则+剧情记忆)
 * - 无 LLM / 配额满 / 该女仆 LLM 关闭 → 降级为固定文本气泡(用户"无 LLM 完整运作"原则)
 *
 * 边界:仅确认关系女仆(strictRelationKey != null)回应;伤心窗口/哀悼中不回应
 * (她正赌气或难过,不应答);婴儿/幼儿(isTooSmall)由 chatWithQuota 兜底拦截
 * (婴儿不会说话)。每女仆 10 秒冷却,防反复抱起刷屏。
 */
public final class PickupResponseManager {

    /** 每女仆冷却(游戏刻);10 秒 */
    private static final long COOLDOWN_TICKS = 200L;
    private static final Map<UUID, Long> PICKUP_COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    /** v1.5.96:女仆实体重建(收魂符/跨维度)时清理——旧 UUID 条目残留无意义 */
    public static void purgeMaid(UUID maidId) {
        if (maidId != null) {
            PICKUP_COOLDOWN_UNTIL.remove(maidId);
        }
    }

    public PickupResponseManager() {
    }

    @SubscribeEvent
    public static void onMaidMountPlayer(EntityMountEvent event) {
        try {
            if (!event.isMounting()) {
                return;
            }
            Entity mounting = event.getEntityMounting();
            Entity beingMounted = event.getEntityBeingMounted();
            if (!(mounting instanceof EntityMaid maid) || !(beingMounted instanceof Player owner)) {
                return;
            }
            // 只在服务端回应(客户端也会收到同事件,防双发)
            if (maid.m_9236_().m_5776_()) {
                return;
            }
            if (!(owner instanceof ServerPlayer sp) || !DialogueDispatcher.isOwner(maid, sp)) {
                return;
            }
            // 仅确认关系女仆(妻子/女儿/恋人)
            String relation = RelationshipExemption.strictRelationKey(maid);
            if (relation == null) {
                return;
            }
            // 伤心窗口中不回应(正赌气)
            if (FamilyInteractionManager.isHurtFeeling(maid)) {
                return;
            }
            // 冷却防刷屏
            long now = maid.m_9236_().m_46467_();
            Long until = PICKUP_COOLDOWN_UNTIL.get(maid.m_20148_());
            if (until != null && until > now) {
                return;
            }
            PICKUP_COOLDOWN_UNTIL.put(maid.m_20148_(), now + COOLDOWN_TICKS);

            String fallback = PromptTexts.pickupFallback(maid, relation);
            DialogueDispatcher.chatWithQuota(maid, sp, PromptTexts.pickupLLMPrompt(relation), fallback);
        } catch (Exception ignored) {
        }
    }
}
