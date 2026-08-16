package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 婚纱 → 花嫁酒狐模型(v1.5.360)：穿戴 maidmarriage 的婚纱(wedding_dress,胸甲槽)
 * 时,女仆自动切换为 TLM 内置模型 geckolib:winefox_wedding(花嫁酒狐);脱下还原原模型。
 *
 * 判定与 maidmarriage 自己的对话变量一致(反编译实证 HugDialogueContextVariables:
 * maid.getItemBySlot(CHEST).is(WEDDING_DRESS) → maid_wearing_wedding_dress)。
 *
 * 原模型 ID 存女仆 persistentData(heartfelt_original_model)——切换前记录,脱下时
 * 还原;重启/卸载重装后状态不丢。每 10 tick 扫描一次(主人 48 格内的女仆)。
 * setModelId 走 TLM 实体数据同步,客户端即时生效。
 */
public final class WeddingDressModelManager {

    /** TLM 内置模型包 geckolib 的花嫁酒狐(maid_model.json 实证 model_id) */
    private static final String WEDDING_MODEL = "geckolib:winefox_wedding";
    /** 原模型 ID 存档键(EntityMaid ForgeData) */
    private static final String ORIGINAL_MODEL_TAG = "heartfelt_original_model";
    /** 婚纱物品 id */
    private static final String DRESS_RL = "maidmarriage:wedding_dress";

    private static Item cachedDress;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.m_129921_() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
                try {
                    boolean wearing = wearingDress(maid);
                    String model = maid.getModelId();
                    if (wearing) {
                        if (!WEDDING_MODEL.equals(model)) {
                            // 切换前记录原模型(只记一次)
                            net.minecraft.nbt.CompoundTag pd = maid.getPersistentData();
                            if (!pd.m_128425_(ORIGINAL_MODEL_TAG, 8)) {
                                pd.m_128359_(ORIGINAL_MODEL_TAG, model == null ? "" : model);
                            }
                            maid.setModelId(WEDDING_MODEL);
                        }
                    } else if (WEDDING_MODEL.equals(model)) {
                        // 脱下婚纱 → 还原原模型
                        net.minecraft.nbt.CompoundTag pd = maid.getPersistentData();
                        String orig = pd.m_128461_(ORIGINAL_MODEL_TAG);
                        if (orig != null && !orig.isEmpty()) {
                            maid.setModelId(orig);
                        }
                        pd.m_128473_(ORIGINAL_MODEL_TAG);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 胸甲槽是否穿着婚纱(与 maidmarriage 判定一致) */
    private static boolean wearingDress(EntityMaid maid) {
        if (cachedDress == null) {
            cachedDress = ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation(DRESS_RL));
        }
        if (cachedDress == null) {
            return false; // maidmarriage 未装/物品未注册
        }
        ItemStack chest = maid.m_6844_(EquipmentSlot.CHEST);
        return !chest.m_41619_() && chest.m_150930_(cachedDress);
    }
}
