package com.heartfelt.connection.compat;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * maidmarriage(心契誓约)兼容适配层(v1.1.0 全面重构)。
 *
 * 职责:
 * - TaskData 键解析与 record accessor 读取(原 RelationshipExemption 私有工具迁入,
 *   含 A1 修复:mood_data 补进白名单)
 * - A8 修复:产后状态改调 PregnancyData.isInPostpartumRecovery,不再硬编码 72000 tick
 * - 心情系统(MaidMoodManager)与亲密互动(Handlers)的反射入口
 */
public final class MaidMarriageCompat {
    public static final String MM_ID = "maidmarriage";
    public static final String MM_COMPAT = "com.example.maidmarriage.compat";

    // ---- TaskDataKey 缓存 ----
    private static TaskDataKey<?> MARRIAGE_KEY;
    private static TaskDataKey<?> PROGRESS_KEY;
    private static TaskDataKey<?> CHILD_KEY;
    private static TaskDataKey<?> LINEAGE_KEY;
    private static TaskDataKey<?> PREGNANCY_KEY;
    private static TaskDataKey<?> MOOD_KEY;
    private static boolean keysResolved = false;

    private MaidMarriageCompat() {
    }

    // ---------- TaskData 读取 ----------

    /** 读 maidmarriage 注册的 TaskData 数据;未装/未注册返回 null */
    public static Object readTaskData(EntityMaid maid, String keyName) {
        TaskDataKey<?> key = resolveKey(keyName);
        if (key == null) {
            return null;
        }
        return maid.getData(key);
    }

    /** 读取 record 布尔 accessor(data 为空返回 false) */
    public static boolean readBool(Object data, String accessor) {
        if (data == null) {
            return false;
        }
        Method m = accessor(data, accessor);
        return m != null && Boolean.TRUE.equals(ReflectUtil.invoke(m, data));
    }

    /** 女仆维度读布尔(未装 maidmarriage 返回 false) */
    public static boolean readBool(EntityMaid maid, String keyName, String accessor) {
        return readBool(readTaskData(maid, keyName), accessor);
    }

    /** 读取 record long accessor;失败返回 null */
    public static Long readLong(Object data, String accessor) {
        if (data == null) {
            return null;
        }
        Method m = accessor(data, accessor);
        Object value = ReflectUtil.invoke(m, data);
        return value instanceof Long l ? l : null;
    }

    /** 读取 record int accessor(如 moodValue);失败返回 null */
    public static Integer readInt(Object data, String accessor) {
        if (data == null) {
            return null;
        }
        Method m = accessor(data, accessor);
        Object value = ReflectUtil.invoke(m, data);
        return value instanceof Integer i ? i : null;
    }

    /** 读取 Optional<UUID> 类型 accessor;空返回 null */
    public static UUID readUuid(Object data, String accessor) {
        if (data == null) {
            return null;
        }
        Method m = accessor(data, accessor);
        Object value = ReflectUtil.invoke(m, data);
        if (value instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    /** 读父/母 UUID(child_state_data / child_lineage_data 同名 accessor 各自缓存,不会跨类误用) */
    public static UUID readUuid(EntityMaid maid, String keyName, String accessor) {
        return readUuid(readTaskData(maid, keyName), accessor);
    }

    /** A8:产后恢复判定——直接问 PregnancyData.isInPostpartumRecovery(gameTime) */
    public static boolean isInPostpartumRecovery(EntityMaid maid, long gameTime) {
        Object data = readTaskData(maid, "pregnancy_data");
        if (data == null) {
            return false;
        }
        Method m = accessor(data, "isInPostpartumRecovery");
        Object value = ReflectUtil.invoke(m, data, gameTime);
        return Boolean.TRUE.equals(value);
    }

    // ---------- 心情(mood) ----------

    /** 当前心情值 0~25;失败返回 -1 */
    public static int moodValue(EntityMaid maid) {
        Method m = ReflectUtil.staticMethod(MM_COMPAT + ".MaidMoodManager", "value", EntityMaid.class);
        Object value = ReflectUtil.invokeStatic(m, maid);
        return value instanceof Number n ? n.intValue() : -1;
    }

    /** 心情增减(悔改后的愧疚等);失败静默 */
    public static void addMood(EntityMaid maid, int amount) {
        Method m = ReflectUtil.staticMethod(MM_COMPAT + ".MaidMoodManager",
                "addMood", EntityMaid.class, int.class);
        ReflectUtil.invokeStatic(m, maid, amount);
    }

    // ---------- v1.2.0:告白与破裂 ----------

    /** 完成告白(maidmarriage 公开 API:写 confessionCompleted + 成就 heart_pact) */
    public static boolean completeConfession(EntityMaid maid) {
        Method m = ReflectUtil.staticMethod(MM_COMPAT + ".MaidRelationshipManager",
                "completeConfession", EntityMaid.class);
        return ReflectUtil.invokeStatic(m, maid) != null;
    }

    /** 重置告白状态(关系破裂用):relationship_progress_data → new RelationshipProgressData(false) */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean resetConfession(EntityMaid maid) {
        TaskDataKey<?> key = resolveKey("relationship_progress_data");
        if (key == null) {
            return false;
        }
        try {
            Class<?> cls = Class.forName("com.example.maidmarriage.data.RelationshipProgressData");
            Object empty = cls.getConstructor(boolean.class).newInstance(false);
            // raw TaskDataKey:TLM setAndSyncData 泛型在 TaskDataKey<?>+Object 下无法推断
            maid.setAndSyncData((TaskDataKey) key, empty);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- v1.4.3:调整器(测试工具)写关系 ----------

    /** 设置已婚:marriage_data → MarriageData(true, playerId, gameTime);失败返回 false */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setMarried(EntityMaid maid, UUID playerId, long gameTime) {
        TaskDataKey<?> key = resolveKey("marriage_data");
        if (key == null) {
            return false;
        }
        Class<?> cls = ReflectUtil.load("com.example.maidmarriage.data.MarriageData");
        Object record = ReflectUtil.newInstance(cls, Boolean.TRUE, Optional.of(playerId), gameTime);
        if (record == null) {
            return false;
        }
        maid.setAndSyncData((TaskDataKey) key, record);
        return true;
    }

    /** 解除婚姻:marriage_data → MarriageData.EMPTY;失败返回 false */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean clearMarriage(EntityMaid maid) {
        TaskDataKey<?> key = resolveKey("marriage_data");
        Object empty = ReflectUtil.staticField("com.example.maidmarriage.data.MarriageData", "EMPTY");
        if (key == null || empty == null) {
            return false;
        }
        maid.setAndSyncData((TaskDataKey) key, empty);
        return true;
    }

    /**
     * 设置女儿状态(v1.4.3):child_state_data → ChildStateData(true, ticks, stage, empty,
     * father, true, false, empty)。heartfelt 侧(关系判定/互动/成长事件)即刻生效;
     * maidmarriage 侧抱持动画/模型不在调整范围。失败返回 false。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setChildState(EntityMaid maid, String stageName, int growthTicks, UUID fatherId) {
        TaskDataKey<?> key = resolveKey("child_state_data");
        if (key == null) {
            return false;
        }
        Class<?> cls = ReflectUtil.load("com.example.maidmarriage.data.ChildStateData");
        Object record = ReflectUtil.newInstance(cls,
                Boolean.TRUE, growthTicks, stageName,
                Optional.empty(), Optional.of(fatherId),
                Boolean.TRUE, Boolean.FALSE, Optional.empty());
        if (record == null) {
            return false;
        }
        maid.setAndSyncData((TaskDataKey) key, record);
        return true;
    }

    /** 解除女儿状态:child_state_data → ChildStateData.EMPTY;失败返回 false */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean clearChildState(EntityMaid maid) {
        TaskDataKey<?> key = resolveKey("child_state_data");
        Object empty = ReflectUtil.staticField("com.example.maidmarriage.data.ChildStateData", "EMPTY");
        if (key == null || empty == null) {
            return false;
        }
        maid.setAndSyncData((TaskDataKey) key, empty);
        return true;
    }

    // ---------- v1.4.4:调整器场景模拟(产后/思慕) ----------

    /**
     * 产后状态(调整器):反射 PregnancyData.completeBirth(now, ticks) 后写回——
     * 进入产后恢复窗口(产后窗口判定/特殊奶语境测试用)。失败返回 false。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setPostpartum(EntityMaid maid, long gameTime, long postpartumTicks) {
        TaskDataKey<?> key = resolveKey("pregnancy_data");
        if (key == null) {
            return false;
        }
        Object data = maid.getData(key);
        if (data == null) {
            data = ReflectUtil.staticField("com.example.maidmarriage.data.PregnancyData", "EMPTY");
        }
        if (data == null) {
            return false;
        }
        Method m = ReflectUtil.method(data.getClass(), "completeBirth", long.class, long.class);
        Object next = ReflectUtil.invoke(m, data, gameTime, postpartumTicks);
        if (next == null) {
            return false;
        }
        maid.setAndSyncData((TaskDataKey) key, next);
        return true;
    }

    /** 思慕状态(调整器):反射 PregnancyData.forceLonging(now) 后写回(思慕天数测试用) */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setLonging(EntityMaid maid, long gameTime) {
        TaskDataKey<?> key = resolveKey("pregnancy_data");
        if (key == null) {
            return false;
        }
        Object data = maid.getData(key);
        if (data == null) {
            data = ReflectUtil.staticField("com.example.maidmarriage.data.PregnancyData", "EMPTY");
        }
        if (data == null) {
            return false;
        }
        Method m = ReflectUtil.method(data.getClass(), "forceLonging", long.class);
        Object next = ReflectUtil.invoke(m, data, gameTime);
        if (next == null) {
            return false;
        }
        maid.setAndSyncData((TaskDataKey) key, next);
        return true;
    }

    /**
     * 思慕判定数据(v1.5.4 修复):maidmarriage 的可见思慕效果(心形粒子 + 思慕
     * 对话)由 `mood_data.lastInteractionDay` 驱动(isLongingForInteraction =
     * lastInteractionDay 距今 ≥3 天),而 forceLonging 只改 pregnancy_data 的
     * lastRomanceDay(仅对话变量)——两者脱节,导致"思慕1天"毫无可见效果。
     * 本方法把 mood_data.lastInteractionDay 设为 3 天前,使思慕立即生效。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setLongingInteraction(EntityMaid maid, long gameTime) {
        TaskDataKey<?> key = resolveKey("mood_data");
        if (key == null) {
            return false;
        }
        // 保留当前心情字段,只改 lastInteractionDay
        Object data = maid.getData(key);
        Integer moodValue = data == null ? null : readInt(data, "moodValue");
        Long moodDay = data == null ? null : readLong(data, "moodDay");
        boolean grief = data != null && readBool(data, "childLossGrief");
        long lastInteractionDay = gameTime / 24000L - 3L; // 3 天前没亲近
        Class<?> cls = ReflectUtil.load("com.example.maidmarriage.data.MaidMoodData");
        Object next = ReflectUtil.newInstance(cls,
                moodValue == null ? 15 : moodValue,
                moodDay == null ? -1L : moodDay, lastInteractionDay, grief);
        if (next == null) {
            return false;
        }
        maid.setAndSyncData((TaskDataKey) key, next);
        return true;
    }

    /** 思慕判定(maidmarriage):lastInteractionDay 距今 ≥3 天;失败返回 false */
    public static boolean isLongingForInteraction(EntityMaid maid) {
        Method m = ReflectUtil.staticMethod(MM_COMPAT + ".MaidMoodManager",
                "isLongingForInteraction", EntityMaid.class);
        return Boolean.TRUE.equals(ReflectUtil.invokeStatic(m, maid));
    }

    // ---------- 亲密互动(Handlers,供 SmartIntimateTool 使用) ----------

    /**
     * v1.5.361:确保 HugManager 交互会话存在——反编译实证 handleHugPoseToggle 第一行
     * PLAYER_TO_SESSION.get(player),玩家不在交互会话(没开 Alt+J 面板)时【静默 return】,
     * 拥抱什么都不做(LLM 工具在玩家纯聊天时调用 → "AI 调用心契工具失败")。
     * 无会话时先用 handleInteractionToggle 开启(它内部有全套校验:主人/儿童/坐姿/骑乘/
     * 距离——坐姿由 MixinInteractionSittingAllow 预站起);女仆已在会话中则不动
     * (handleInteractionToggle 是开关,已有会话再调会变成"停止")。
     */
    public static boolean ensureInteraction(ServerPlayer player, EntityMaid maid) {
        try {
            Method get = ReflectUtil.staticMethod(MM_COMPAT + ".MaidHugManager",
                    "getInteractionPlayer", EntityMaid.class);
            if (ReflectUtil.invokeStatic(get, maid) != null) {
                return true; // 女仆已在交互会话中
            }
            Method toggle = ReflectUtil.staticMethod(MM_COMPAT + ".MaidHugManager",
                    "handleInteractionToggle", ServerPlayer.class, UUID.class);
            ReflectUtil.invokeStatic(toggle, player, maid.m_20148_());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * v1.5.114:女仆当前是否处于 maidmarriage 交互会话中(getInteractionPlayer 非 null)
     * ——服务端直调 handleInteractionToggle 后的【结果验证】(toggle 被内部拒绝时
     * 不抛异常,只有查会话才知道有没有真开起来)。
     */
    public static boolean isMaidInteracting(EntityMaid maid) {
        Method m = ReflectUtil.staticMethod(MM_COMPAT + ".MaidHugManager",
                "getInteractionPlayer", EntityMaid.class);
        return ReflectUtil.invokeStatic(m, maid) != null;
    }

    public static Method hugToggle() {
        return ReflectUtil.staticMethod(MM_COMPAT + ".MaidHugManager",
                "handleHugPoseToggle", ServerPlayer.class, UUID.class);
    }

    public static Method hugStop() {
        return ReflectUtil.staticMethod(MM_COMPAT + ".MaidHugManager",
                "forceStopInteraction", ServerPlayer.class, EntityMaid.class);
    }

    public static Method kiss() {
        return ReflectUtil.staticMethod(MM_COMPAT + ".MaidKissManager",
                "handleKissRequest", ServerPlayer.class, UUID.class);
    }

    public static Method lapStart() {
        return ReflectUtil.staticMethod(MM_COMPAT + ".LapPillowManager",
                "handleStart", ServerPlayer.class, UUID.class);
    }

    public static Method lapExit() {
        return ReflectUtil.staticMethod(MM_COMPAT + ".LapPillowManager",
                "handleExit", ServerPlayer.class);
    }

    public static Method pet() {
        return ReflectUtil.staticMethod(MM_COMPAT + ".LapPillowManager",
                "handlePetPlayerHead", ServerPlayer.class, UUID.class);
    }

    // ---------- v1.2.2:女儿成长阶段 ----------

    /**
     * 女儿成长阶段(v1.2.2):读取 child_state_data.growthStage。
     * maidmarriage 阶段:INFANT(1 天)→ JUVENILE → CHILD(旧名 MIDDLE)→ ADULT。
     */
    public enum ChildStage {
        INFANT, JUVENILE, CHILD, ADULT
    }

    /** 原始阶段字符串(未识别返回 null) */
    public static String childStageRaw(EntityMaid maid) {
        Object data = readTaskData(maid, "child_state_data");
        if (data == null) {
            return null;
        }
        Method m = ReflectUtil.method(data.getClass(), "growthStage");
        Object value = ReflectUtil.invoke(m, data);
        return value instanceof String s ? s : null;
    }

    /** 归一化阶段;非女儿/未知返回 null */
    public static ChildStage childStage(EntityMaid maid) {
        String raw = childStageRaw(maid);
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "JUVENILE" -> ChildStage.JUVENILE;
            case "CHILD", "MIDDLE" -> ChildStage.CHILD;
            case "ADULT" -> ChildStage.ADULT;
            default -> ChildStage.INFANT;
        };
    }

    /**
     * v1.5.354:按 maidmarriage 实时 childGrowthDays 配置计算"落到目标阶段"的安全 growthTicks。
     * 复刻 MaidChildEntity 的阈值推导(resolveGrowthStageByTicks 反编译实证):
     * - infantStage = 24000(1 天)
     * - adultAfter = max(3, childGrowthDays) * 24000
     * - childStage = 24000 + max(48000, adultAfter - 24000) / 2
     * 推导:ticks < 24000 → INFANT;< childStage → JUVENILE;< adultAfter → CHILD;否则 ADULT。
     * 各阶段取区间中点——任何配置(3~120 天)下都稳定落在目标阶段。
     * 旧版写死 48000/120000:配置=3 天时 48000 ≥ childStage 被推导成 CHILD(幼儿变少女)、
     * 默认 10 天时 120000 < childStage 被推导成 JUVENILE(少女变幼儿)——调整器设阶段
     * 与 maidmarriage 每 tick 重推导打架,isTooSmall 束缚随之失效。失败回退旧值。
     */
    public static int stageTicksFor(String stageName) {
        try {
            int adultAfter = Math.max(3, childGrowthDays()) * 24000;
            int childStage = 24000 + Math.max(48000, adultAfter - 24000) / 2;
            return switch (stageName) {
                case "JUVENILE" -> 24000 + (childStage - 24000) / 2;
                case "CHILD", "MIDDLE" -> childStage + (adultAfter - childStage) / 2;
                case "ADULT" -> adultAfter;
                default -> 0; // INFANT
            };
        } catch (Throwable t) {
            return switch (stageName) {
                case "JUVENILE" -> 2 * 24000;
                case "CHILD", "MIDDLE" -> 5 * 24000;
                case "ADULT" -> 999999;
                default -> 0;
            };
        }
    }

    /** maidmarriage 配置:孩子长成成年所需天数(默认 10,范围 3-120) */
    private static int childGrowthDays() {
        try {
            Method m = ReflectUtil.staticMethod("com.example.maidmarriage.config.ModConfigs", "childGrowthDays");
            Object v = ReflectUtil.invokeStatic(m);
            return v instanceof Integer i ? i : 10;
        } catch (Throwable t) {
            return 10;
        }
    }

    /**
     * v1.5.355:正规释放"妈妈抱着"状态(反射 MaidCarryChildManager.putDown 私有方法)。
     * 背景:婴儿女儿被妈妈抱着(骑乘妈妈,carry 链每 tick 维护)。升级幼儿瞬间 maidmarriage
     * 只发"妈妈轻轻把她放到地上"消息、并不真正放下——carry 管理器条件(child 是女儿、
     * 妈妈不是女儿、都没坐着)依然满足,继续抱着;单纯 m_8127_() 下马会在下一 tick 被
     * ensureCarryChain 重新抱回。必须走正规 putDown(清 OWNER_TO_CHILD/CHILD_TO_OWNER
     * 映射 + 下马),之后她才能自由站立行走。
     */
    public static void releaseCarry(ServerPlayer player, EntityMaid child) {
        try {
            Method m = ReflectUtil.staticMethod(MM_COMPAT + ".MaidCarryChildManager",
                    "putDown", ServerPlayer.class, EntityMaid.class);
            ReflectUtil.invokeStatic(m, player, child);
        } catch (Throwable ignored) {
            // 未装 maidmarriage/方法变动 → 静默;调用方再用 m_8127_ 兜底下马
        }
    }

    // ---------- 私有 ----------

    /** record accessor 句柄缓存(键 = 声明类#方法名,防同名 accessor 跨 record 误用) */
    private static Method accessor(Object data, String accessor) {
        return ReflectUtil.method(data.getClass(), accessor);
    }

    /** TaskDataKey 缓存(首次解析全部注册 key;v1.1.0 补 mood_data——修 A1 死代码) */
    private static TaskDataKey<?> resolveKey(String keyName) {
        if (!keysResolved) {
            MARRIAGE_KEY = TaskDataRegister.getValue(new ResourceLocation(MM_ID, "marriage_data"));
            PROGRESS_KEY = TaskDataRegister.getValue(new ResourceLocation(MM_ID, "relationship_progress_data"));
            CHILD_KEY = TaskDataRegister.getValue(new ResourceLocation(MM_ID, "child_state_data"));
            LINEAGE_KEY = TaskDataRegister.getValue(new ResourceLocation(MM_ID, "child_lineage_data"));
            PREGNANCY_KEY = TaskDataRegister.getValue(new ResourceLocation(MM_ID, "pregnancy_data"));
            MOOD_KEY = TaskDataRegister.getValue(new ResourceLocation(MM_ID, "mood_data"));
            // 审计 M6：只在全部 key 成功解析后才缓存，防止时序性 null 被永久缓存
            keysResolved = MARRIAGE_KEY != null && PROGRESS_KEY != null && CHILD_KEY != null
                    && LINEAGE_KEY != null && PREGNANCY_KEY != null && MOOD_KEY != null;
        }
        return switch (keyName) {
            case "marriage_data" -> MARRIAGE_KEY;
            case "relationship_progress_data" -> PROGRESS_KEY;
            case "child_state_data" -> CHILD_KEY;
            case "child_lineage_data" -> LINEAGE_KEY;
            case "pregnancy_data" -> PREGNANCY_KEY;
            case "mood_data" -> MOOD_KEY;
            default -> null;
        };
    }
}
