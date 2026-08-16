package com.heartfelt.connection;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.combat.BetrayalRedemptionManager;
import com.heartfelt.connection.combat.BetrayerAttackBrain;
import com.heartfelt.connection.dialogue.FamilyInteractionManager;
import com.heartfelt.connection.dialogue.FamilyMourningManager;
import com.heartfelt.connection.dialogue.MaidConfessionManager;
import com.heartfelt.connection.dialogue.RelationBroadcastManager;
import com.heartfelt.connection.memory.RelationshipMaidContext;
import com.heartfelt.connection.memory.StoryMaidContext;
import com.heartfelt.connection.protect.FamilyProtectionManager;
import com.heartfelt.connection.protect.PregnancyProtectionManager;
import com.heartfelt.connection.tool.SmartIntimateTool;
import net.minecraftforge.common.MinecraftForge;

/**
 * Heartfelt-connection 扩展注册(v1.0.0;v1.1.0 注册背叛悔改系统 + brain 提为命名类)。
 *
 * 通过官方 @LittleMaidExtension 机制被 TLM 自动发现:
 * - 事件监听:关系传播广播、家庭保护、孕期保护、哀悼、家庭互动、背叛悔改
 * - AI context:relationship 分类(关系栏 + 剧情记忆)注入 <context>
 * - AI 工具:亲密互动(hug/kiss/lap/pet,LLM 可调用)
 * - ExtraMaidBrain:反击背叛女仆(优先级走配置,默认 150)
 */
@LittleMaidExtension
public class HeartfeltExtension implements ILittleMaid {

    public HeartfeltExtension() {
        MinecraftForge.EVENT_BUS.register(new RelationBroadcastManager());
        MinecraftForge.EVENT_BUS.register(new FamilyProtectionManager());
        MinecraftForge.EVENT_BUS.register(new PregnancyProtectionManager());
        MinecraftForge.EVENT_BUS.register(new FamilyMourningManager());
        MinecraftForge.EVENT_BUS.register(new FamilyInteractionManager());
        // v1.5.345:抱起回应(妻子/女儿/恋人被抱起 → LLM 或无 LLM 降级气泡)
        MinecraftForge.EVENT_BUS.register(new com.heartfelt.connection.dialogue.PickupResponseManager());
        // v1.5.346:思慕明显效果(心形粒子爆发 + 思慕气泡 + 每日系统消息)
        MinecraftForge.EVENT_BUS.register(new com.heartfelt.connection.dialogue.LongingEffectManager());
        // v1.5.360:穿婚纱 → 花嫁酒狐模型(maidmarriage wedding_dress 胸甲槽 → geckolib:winefox_wedding)
        MinecraftForge.EVENT_BUS.register(new com.heartfelt.connection.dialogue.WeddingDressModelManager());
        // v1.1.0:背叛悔改(和解)系统
        MinecraftForge.EVENT_BUS.register(new BetrayalRedemptionManager());
        // v1.2.0:女仆主动告白 + 关系破裂
        MinecraftForge.EVENT_BUS.register(new MaidConfessionManager());
        // v1.2.1:好感度对信任/恐惧的中和缓和(未确认关系女仆)
        MinecraftForge.EVENT_BUS.register(new com.heartfelt.connection.relationship.EmotionSmoothingManager());
        // v1.3.0:事件历史(首见/首礼/救主/破裂史)→ 提示词注入
        MinecraftForge.EVENT_BUS.register(new com.heartfelt.connection.memory.EventHistoryManager());
        // v1.4.0:孩子监护(魂符收妈妈 → 女儿强制坐下等妈妈)
        MinecraftForge.EVENT_BUS.register(new com.heartfelt.connection.compat.ChildGuardManager());
MinecraftForge.EVENT_BUS.register(new com.heartfelt.connection.compat.ChildActionReportManager());
        // v1.4.1:玩家伤害惩罚(冻结层女仆:关系不破坏但有后果;误伤豁免)
        MinecraftForge.EVENT_BUS.register(new com.heartfelt.connection.combat.PlayerHarmPenaltyManager());
    }

    @Override
    public void registerAITool(ToolRegister register) {
        register.register(new SmartIntimateTool());
    }

    @Override
    public void registerAIMaidContext(GameContextRegister register) {
        register.registerCategory("relationship", "Relationship status with the owner", true);
        register.registerContext("relationship", new RelationshipMaidContext());
        register.registerContext("relationship", new StoryMaidContext());
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        // v1.1.0:匿名类提为命名类 BetrayerAttackBrain;优先级走配置(默认 150)
        manager.addExtraMaidBrain(new BetrayerAttackBrain());
    }
}

