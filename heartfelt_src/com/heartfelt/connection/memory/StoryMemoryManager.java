package com.heartfelt.connection.memory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;

import java.util.UUID;

/**
 * 心契誓约剧情记忆(v1.3;v1.1.0 重构:读取全部委托 MaidMarriageCompat)。
 *
 * 把 maidmarriage 的既定剧情状态实时注入 AI 记忆(结婚/怀孕/双胞胎/产后/心情/
 * 丧子/告白/自身是孩子)。软依赖 maidmarriage,未安装时完全静默。
 *
 * v1.1.0 修复:
 * - A1:心情读取(mood_data)此前不在 TaskData 白名单 → 死代码;现经
 *   MaidMarriageCompat.resolveKey 已补 mood_data,心情/丧子记忆真正生效。
 * - 反射统一走 compat 层(带方法句柄缓存),删除本类重复的 getMethod/MM_ID。
 */
public final class StoryMemoryManager {
    private StoryMemoryManager() {
    }

    /** 返回剧情记忆文本;未安装 maidmarriage 或数据为空时返回空串 */
    public static String buildStoryMemory(EntityMaid maid) {
        StringBuilder sb = new StringBuilder();
        appendFact(sb, readMarriage(maid));
        appendFact(sb, readPregnancy(maid));
        appendFact(sb, readMood(maid));
        appendFact(sb, readConfession(maid));
        appendFact(sb, readSelfChild(maid));
        // v1.2.0:告白失败 / 关系破裂 记忆(读取 heartfelt 自己的 NBT 标记)
        appendFact(sb, readConfessionFailed(maid));
        appendFact(sb, readHeartbroken(maid));
        return sb.toString();
    }

    /** 主动告白被拒(v1.2.0):永久记忆,她再也不提;v1.2.3 带时间戳(第 N 天) */
    private static String readConfessionFailed(EntityMaid maid) {
        if (maid.getPersistentData().m_128471_(com.heartfelt.connection.tags.HeartfeltTags.CONFESSION_FAILED)) {
            long at = maid.getPersistentData().m_128454_(
                    com.heartfelt.connection.tags.HeartfeltTags.CONFESSION_FAILED_AT);
            return dayPrefix(at) + com.heartfelt.connection.prompt.PromptTexts.MEMORY_CONFESSION_FAILED;
        }
        return "";
    }

    /** 关系破裂(v1.2.0):心碎记忆(再告白后清除标记,记忆自然消失);v1.2.3 带时间戳 */
    private static String readHeartbroken(EntityMaid maid) {
        long heartbrokenAt = maid.getPersistentData().m_128454_(
                com.heartfelt.connection.tags.HeartfeltTags.HEARTBROKEN_AT);
        if (heartbrokenAt > 0L && !MaidMarriageCompat.readBool(maid, "relationship_progress_data", "confessionCompleted")) {
            return dayPrefix(heartbrokenAt) + com.heartfelt.connection.prompt.PromptTexts.MEMORY_HEARTBROKEN;
        }
        return "";
    }

    /** 游戏 tick → "第 N 天," 前缀(旧档无时间戳时返回空串) */
    private static String dayPrefix(long gameTime) {
        return gameTime > 0L ? "第 " + (gameTime / 24000L) + " 天，" : "";
    }

    private static String readMarriage(EntityMaid maid) {
        Object data = MaidMarriageCompat.readTaskData(maid, "marriage_data");
        if (MaidMarriageCompat.readBool(data, "married")) {
            return "我已与主人结为夫妻";
        }
        return "";
    }

    private static String readPregnancy(EntityMaid maid) {
        Object data = MaidMarriageCompat.readTaskData(maid, "pregnancy_data");
        if (data == null) {
            return "";
        }
        if (MaidMarriageCompat.readBool(data, "pregnant")) {
            return MaidMarriageCompat.readBool(data, "twinsPregnancy")
                    ? "我怀孕了，肚子里是双胞胎"
                    : "我怀孕了，肚子里有了小生命";
        }
        Long lastBirth = MaidMarriageCompat.readLong(data, "lastBirthGameTime");
        if (lastBirth != null && lastBirth > 0L) {
            // 只在生育后 30 个游戏日内算"不久"(该字段完成生育后永不归零)
            long now = maid.m_9236_().m_46467_();
            if (now - lastBirth < 30L * 24000L) {
                return "我刚生过宝宝不久";
            }
        }
        return "";
    }

    /** A1 修复:心情记忆真正生效(此前 mood_data 不在白名单,永远读不到) */
    private static String readMood(EntityMaid maid) {
        Object data = MaidMarriageCompat.readTaskData(maid, "mood_data");
        if (data == null) {
            return "";
        }
        if (MaidMarriageCompat.readBool(data, "childLossGrief")) {
            return "我刚刚失去了我的孩子，心里非常难受";
        }
        Integer mood = MaidMarriageCompat.readInt(data, "moodValue");
        if (mood == null) {
            return "";
        }
        int value = mood.intValue();
        if (value < 5) {
            return "我最近心情很低落";
        }
        if (value < 10) {
            return "我最近心情不太好";
        }
        if (value < 15) {
            return "我最近心情平平";
        }
        if (value < 25) {
            return "我最近心情很好";
        }
        return "我最近满心都是对主人的爱";
    }

    private static String readConfession(EntityMaid maid) {
        Object data = MaidMarriageCompat.readTaskData(maid, "relationship_progress_data");
        if (MaidMarriageCompat.readBool(data, "confessionCompleted")) {
            return "我们互相告白过，关系已经确定";
        }
        return "";
    }

    /** v1.2.2:女儿记忆按成长阶段细分(幼儿/少女/成年) */
    private static String readSelfChild(EntityMaid maid) {
        Object data = MaidMarriageCompat.readTaskData(maid, "child_state_data");
        if (data == null || !MaidMarriageCompat.readBool(data, "child")) {
            return "";
        }
        UUID ownerId = maid.m_269323_() != null ? maid.m_269323_().m_20148_() : null;
        UUID fatherId = MaidMarriageCompat.readUuid(data, "father");
        if (fatherId == null) {
            fatherId = MaidMarriageCompat.readUuid(maid, "child_lineage_data", "father");
        }
        boolean myDaughter = ownerId != null && fatherId != null && fatherId.equals(ownerId);
        MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
        if (myDaughter) {
            return switch (stage == null ? MaidMarriageCompat.ChildStage.INFANT : stage) {
                case ADULT -> "我已经长大了，是爸爸懂事的女儿——我会好好照顾他";
                case CHILD -> "我是主人的女儿，主人是我的爸爸，我还会缠着他撒娇";
                case JUVENILE -> "我是爸爸的小宝贝，已经会走会跑了，最喜欢爸爸抱着我";
                default -> "我还是刚出生不久的小婴儿，只会咿咿呀呀地向爸爸撒娇";
            };
        }
        return "我是主人家里的小孩子，还在慢慢长大";
    }

    private static void appendFact(StringBuilder sb, String fact) {
        if (fact == null || fact.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(fact);
    }
}
