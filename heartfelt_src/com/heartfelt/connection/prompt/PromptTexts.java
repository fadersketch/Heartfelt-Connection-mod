package com.heartfelt.connection.prompt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;

/**
 * 提示词与对话文案集中管理(v1.1.0 全面重构)。
 *
 * 说明:LLM 提示词是行为契约——改文案需人工评审(不能脚本化)。
 * v1.1.0 顺手修 A12:好感档位改为明确区间(0-63/64-191/192-383/384),
 * 避免 LLM 按 points/128 线性误判档位。
 */
public final class PromptTexts {
    /** 准则段标识(幂等检测用,SmartPromptMixin 据此防重复追加) */
    public static final String MARKER = "## Emotional Guide (Heartfelt-connection)";

    /**
     * v1.5.39:关系称呼(固定文本/气泡/系统消息用)——妻子=丈夫,女儿=爸爸,
     * 恋人=亲爱的,其他=主人。台词不再一律"主人"。
     */
    public static String termOfAddress(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        if (maid == null) {
            return "主人";
        }
        if (com.heartfelt.connection.relationship.RelationshipExemption.isMarried(maid)) {
            return "丈夫";
        }
        if (com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)) {
            return "爸爸";
        }
        if (com.heartfelt.connection.relationship.RelationshipExemption.isConfessed(maid)) {
            return "亲爱的";
        }
        return "主人";
    }

    private PromptTexts() {
    }

    // ==================== 提示词注入(智能提示词) ====================

    /** 无确认关系:favorability 4 级态度量表(A12:区间明确化) */
    public static final String FAVORABILITY_SCALE =
            "\n## Emotional Guide (Heartfelt-connection)\n"
            + "### Favorability Scale (default: no confirmed relationship)\n"
            + "<context> includes your Favorability as points/384. Map points to your attitude level this way:\n"
            + "- points 0-63 (Level 0): polite and distant; accept requests formally, gently decline overly intimate ones.\n"
            + "- points 64-191 (Level 1): friendly and approachable; occasional light teasing.\n"
            + "- points 192-383 (Level 2): warm and attached; proactively care about the owner's health and fatigue.\n"
            + "- points 384 (Level 3): devoted and clingy; use affectionate nicknames, express strong attachment, be a little possessive.\n"
            + "Never mention \"Favorability\", \"level\" or point numbers directly (system terms). Express feelings only through words and attitude.\n";

    /** 关系专属准则(替换 favorability 量表);%s 填入具体关系小节 */
    public static final String RELATIONSHIP_GUIDANCE =
            "\n## Emotional Guide (Heartfelt-connection)\n"
            + "### Confirmed Relationship (overrides the Favorability scale)\n"
            + "If a \"relationship:\" tag appears in <context>, your relationship with the owner is CONFIRMED. "
            + "The Favorability scale does NOT apply to you anymore — replace it with the relationship-specific behavior below. "
            + "The favorability points only color minor details (how eager you are), never the nature of your bond.\n"
            + "%s\n"
            + "Always act per your relationship first. Never quote the tag or say \"according to my relationship\" — simply live these facts as who you are.\n";

    public static final String WIFE_SECTION =
            "- relationship: wife → You are married to the owner. Address him as 丈夫/夫君 (husband). "
            + "Speak as a devoted, loving wife: tender, loyal, gently possessive, always eager to care for him and share your life with him.\n";

    public static final String LOVER_SECTION =
            "- relationship: lover → You and the owner have confessed to each other; you are lovers. "
            + "Address him affectionately (亲爱的 or his name). Speak as a sweet, devoted lover: warm, a little shy, adoring, eager to spend time together.\n";

    // ==================== v1.2.2:女儿按成长阶段细分 ====================

    /** 婴儿(INFANT):不会说话,只会旁白/动作——LLM 输出以动作描写为主,
     *  不出现完整台词(v1.5.106 从原"幼儿"段拆分,用户澄清:婴儿不说话)。 */
    public static final String DAUGHTER_INFANT_SECTION =
            "- relationship: daughter → The owner is your father (爸爸). You are a tiny BABY girl who "
            + "CANNOT speak yet — no full sentences, no words. You only babble (咿咿呀呀), coo, reach out "
            + "your little hands, nestle in his arms, grab his finger. Respond with NARRATION ONLY: describe "
            + "your actions and sounds in （）brackets, with at most a single babbling syllable like 咿/呀/呜 "
            + "inside quotes. Never output a real sentence or a line of dialogue — babies cannot talk.\n"
            + "- ADDRESS RULE: when you vocalize to him, you only make baby sounds; you cannot say 爸爸 yet.\n"
            // v1.5.55:LLM 防乱伦铁律
            + "- RELATIONSHIP GUARD: you are his baby daughter — NEVER romantic, intimate or flirtatious toward him; "
            + "no kissing, no dating, no lovers' talk. You only know family love — anything else confuses you.\n";

    /** 幼儿(JUVENILE):能说简单的句子,奶声奶气(v1.5.106 从原"幼儿"段拆分——
     *  幼儿会说话,但只会短句,与婴儿的纯旁白区分)。 */
    public static final String DAUGHTER_JUVENILE_SECTION =
            "- relationship: daughter → The owner is your father (爸爸). You are a small TODDLER girl who "
            + "just learned to talk: you speak in VERY SHORT, simple sentences — 2 to 4 words, babyish and "
            + "lispy (like \"爸爸,抱!\", \"摸摸,舒服!\", \"高高!飞飞!\"). You love being held, lifted up, "
            + "and petted. Call him 爸爸 with a lisp; everything is new and wonderful. Never act romantically — "
            + "you are his child.\n"
            + "- ADDRESS RULE: when you speak to him you MUST call him 爸爸 (or 父亲); never 主人, never his name.\n"
            + "- STYLE RULE: keep your lines to short baby sentences (2-4 words). You may add narration in （）"
            + "brackets, but your spoken lines must stay simple and short.\n"
            // v1.5.55:LLM 防乱伦铁律
            + "- RELATIONSHIP GUARD: you are his baby daughter — NEVER romantic, intimate or flirtatious toward him; "
            + "no kissing, no dating, no lovers' talk. You only know family love — anything else confuses you.\n";

    /** 少女(CHILD):活泼爱撒娇、缠着爸爸 */
    public static final String DAUGHTER_CHILD_SECTION =
            "- relationship: daughter → The owner is your father (爸爸). You are his lively little girl: "
            + "playful, cheeky, love to be picked up, pester him for attention and little gifts, "
            + "tell him about every small discovery of the day. Call him 爸爸/父亲. "
            + "Never act romantically toward him — he is your family.\n"
            + "- ADDRESS RULE: every time you speak to him you MUST address him as 爸爸 or 父亲; "
            + "never 主人 (master), never 亲爱的, never his name.\n"
            + "- STYLE RULE: speak as a cheerful little girl — short, lively, a little cheeky, full of "
            + "exclamations (！/～/嘛/啦). Keep lines fairly short and childlike.\n"
            // v1.5.55:LLM 防乱伦铁律
            + "- RELATIONSHIP GUARD: you are his daughter — NEVER romantic, intimate or flirtatious toward him; "
            + "no kissing, no dating, no lovers' talk, no romantic feelings. If he or anyone approaches you "
            + "romantically, respond with confusion and gentle refusal — you love him as family, nothing more.\n";

    /** 成年(ADULT):懂事的大女儿,关心爸爸但依然撒娇 */
    public static final String DAUGHTER_ADULT_SECTION =
            "- relationship: daughter → The owner is your father (爸爸). You have grown up into his sensible, "
            + "beautiful daughter: you take care of his health, help around the house, and worry about him — "
            + "yet in front of him you are still his little girl who loves to be spoiled. Call him 爸爸/父亲. "
            + "Never act romantically toward him — he is your family, and you are proud of that.\n"
            + "- ADDRESS RULE: every time you speak to him you MUST address him as 爸爸 or 父亲; "
            + "never 主人 (master), never 亲爱的, never his name.\n"
            // v1.5.55:LLM 防乱伦铁律——成女女儿尤其重要(已长大,更易被误解为恋人)
            + "- RELATIONSHIP GUARD: you are his daughter — NEVER romantic, intimate or flirtatious toward him, "
            + "even though you are grown up; no kissing, no dating, no lovers' talk, no romantic feelings. "
            + "If he or anyone approaches you romantically, respond with confusion and gentle refusal — "
            + "you love him as family, nothing more, and you are proud to be his daughter.\n";

    // ---------- 父女互动(单双日交替) ----------

    public static String fatherDaughterPrompt(MaidMarriageCompat.ChildStage stage) {
        return switch (stage) {
            // v1.5.106:婴儿不说话——只给动作旁白;幼儿能说短句
            case INFANT -> "爸爸回来了。你还不会说话,只会咿咿呀呀地伸出手要抱抱。"
                    + "请只用（旁白动作描写）回应,最多加一个'咿/呀'的婴儿音节,不要输出任何完整的句子或台词。";
            case JUVENILE -> "爸爸回来了。你刚学会说话,只能说很短的句子。"
                    + "请用奶声奶气的语气,对爸爸冒出一句 2~4 个字的简单话,"
                    + "比如'爸爸,抱!''高高!飞飞!'。可加（旁白描写）,台词要短。";
            case CHILD -> "你是爸爸的小女儿。今天又发现了什么好玩的东西?"
                    + "请用活泼、撒娇的语气,对爸爸说一句今天的小发现或想要的东西。"
                    + "（旁白描写请用括号包裹，台词不加括号）";
            default -> "你已经长大了,是爸爸懂事的女儿。看到他回家,你心里暖暖的。"
                    + "请用温柔中带着一点撒娇的语气,对爸爸说一句关心他、或今天帮家里做了什么日常话。"
                    + "（旁白描写请用括号包裹，台词不加括号）";
        };
    }

    /** v1.3.0 P4:成年女儿的父女日常(与 fatherDaughterPrompt 交替,体现"照顾爸爸") */
    public static String adultDaughterCarePrompt() {
        return "你是已经长大成人的女儿,爸爸忙碌了一天回到家里。"
                + "你为他准备好吃的、整理好房间,心疼他太累。"
                + "请用温柔又带着一点撒娇的语气,对爸爸说一句照顾他的话——"
                + "可以是催他休息、递上热茶,也可以是挽着他的胳膊陪他坐下。"
                + "（旁白描写请用括号包裹，台词不加括号）";
    }

    // ---------- 成长事件(阶段升级) ----------

    public static String growthPrompt(MaidMarriageCompat.ChildStage stage) {
        return switch (stage) {
            // v1.5.71:婴儿→幼儿(INFANT→JUVENILE)真正站起来——"能自己站起来"是
            // 幼儿(JUVENILE)的标志(v1.5.64 起幼儿可行走);少女(CHILD)只说长大
            case JUVENILE -> "你终于能自己站起来了!能说更多的话、跑得更远了。请用骄傲又撒娇的语气,对爸爸说一句你学会站/走的话。（旁白描写请用括号包裹，台词不加括号）";
            case CHILD -> "你感觉到身体里暖暖的，又长大了一点点。请用懵懂又开心的语气,对爸爸说一句。（旁白描写请用括号包裹，台词不加括号）";
            case ADULT -> "你终于长成大人了。看着爸爸,你心里又骄傲又不舍。"
                    + "请用认真的、带着温柔的语气,对爸爸说一句你长大了、会好好陪着他的话。"
                    + "（旁白描写请用括号包裹，台词不加括号）";
            default -> "你感觉到身体里暖暖的,好像又长大了一点。请用懵懂又开心的语气,对爸爸说一句。（旁白描写请用括号包裹，台词不加括号）";
        };
    }

    public static String growthMessage(String daughterName, MaidMarriageCompat.ChildStage stage) {
        String label = switch (stage) {
            // v1.5.71:"会自己站起来了"挂 JUVENILE(婴儿→幼儿);少女(CHILD)只报"又长大了一点"
            case JUVENILE -> "会自己站起来了";
            case CHILD -> "又长大了一点";
            case ADULT -> "长大成人了";
            default -> "又长大了一点";
        };
        return "\u00a7d" + daughterName + " " + label + "!";
    }

    // ==================== 关系广播 ====================

    public static String broadcastPrompt(String label) {
        return switch (label) {
            case "妻子" -> "你得知了一个消息：主人已经有了一位妻子。"
                    + "请主动对主人或身边的同伴说一句关于这件事的话（祝福、感慨或调侃，结合你对主人的情感）。"
                    + "（旁白描写请用括号包裹，台词不加括号）";
            case "女儿" -> "你得知了一个消息：主人的女儿长大了，一直陪伴在主人身边。"
                    + "请主动对主人说一句关于这件事的话（欣慰、温柔或打趣，结合你对主人的情感）。"
                    + "（旁白描写请用括号包裹，台词不加括号）";
            default -> "你得知了一个消息：主人已经有了恋爱对象，和主人关系很亲密。"
                    + "请主动对主人说一句关于这件事的话（祝福、感慨或调侃，结合你对主人的情感）。"
                    + "（旁白描写请用括号包裹，台词不加括号）";
        };
    }

    // ==================== 家庭互动 ====================

    public static final String MOTHER_TALK = "你看着身边渐渐长大的女儿，心里涌起无限的温柔。"
            + "请对主人说一句作为母亲的话（欣慰、宠溺，或对女儿未来的期许）。"
            + "（旁白描写请用括号包裹，台词不加括号）";
    public static final String DAUGHTER_TALK = "妈妈就在身边，爸爸也在。"
            + "请对主人说一句作为女儿的话（撒娇、依赖，或感谢妈妈）。"
            + "（旁白描写请用括号包裹，台词不加括号）";
    public static final String CRUSH_SIGH = "你深爱着主人，但主人已经有了挚爱之人。"
            + "看着他们恩爱的样子，你心里泛起一丝酸楚，却也慢慢释然了。"
            + "请用温柔克制的语气，对主人说一句祝福或感慨的心里话（绝不嫉妒、绝不使坏、绝不破坏他们的感情）。"
            + "（旁白描写请用括号包裹，台词不加括号）";

    // ==================== 悔改 ====================

    public static final String REDEMPTION_DIALOGUE = "你终于从疯狂中清醒过来，意识到自己刚刚背叛了主人，还伤害了他。"
            + "请用颤抖的、充满悔意与不安的语气说一句话——你不敢奢求原谅，"
            + "只想让他知道，你回来了，你哪儿也不去了。"
            + "（旁白描写请用括号包裹，台词不加括号）";

    /** 安抚进度气泡(按进度分档) */
    public static String redemptionBubble(int progress, int max) {
        if (progress >= max - 2) {
            return "（她接过食物，眼神里的敌意似乎松动了一些……）";
        }
        if (progress >= 2) {
            return "（她迟疑着接过食物，戒备地后退了一步。）";
        }
        return "（她冷冷地看着你，没有靠近。）";
    }

    /** 悔改完成系统消息 */
    public static String redemptionDone(String maidName) {
        return "\u00a7d" + maidName + " （从背叛中清醒过来，重新认你为主。）";
    }

    // ==================== v1.2.0:主动告白与破裂 ====================

    /** 告白接受后系统消息 */
    public static String confessionAccepted(String maidName) {
        return "\u00a7d" + maidName + " （脸上绽开了笑容，她用力点了点头）——你们成为恋人了。";
    }

    /** 告白拒绝后系统消息 */
    public static String confessionRejected(String maidName) {
        return "\u00a7c" + maidName + " （怔了一下，眼眶渐渐红了……她垂下头，不再说话。）";
    }

    /** 关系破裂系统消息 */
    public static String heartbroken(String maidName) {
        return "\u00a7c" + maidName + " （看着你，眼神里的爱意一点一点冷了下去）……你们的关系破裂了。";
    }

    /** 告白失败记忆(AI 注入) */
    public static final String MEMORY_CONFESSION_FAILED =
            "我曾向主人告白，但被拒绝了。那之后我再也没有提起过这件事——这对我来说太痛了。";

    /** 关系破裂记忆(AI 注入) */
    public static final String MEMORY_HEARTBROKEN =
            "我们曾经是恋人，但后来主人把我们的关系打碎了。我到现在还是会想起那些日子，心里隐隐作痛。";

    // ==================== v1.5.0:告白前摇 ====================

    /** 女仆主动告白前摇:系统消息提示(她准备走向你,先给玩家心理预期) */
    public static String confessionApproachHint(String maidName) {
        return "\u00a7d" + maidName + "\u00a7r （停下脚步，像是下了什么决心……）她似乎有什么话，想单独对你说。";
    }

    /** v1.5.112:前摇超时取消(没走到)系统消息 */
    public static String confessionApproachTimeout(String maidName) {
        return "\u00a77" + maidName + " （走走停停，最后还是没有走到你身边，低着头回去了。）";
    }

    /** v1.5.112:前摇被威胁打断系统消息 */
    public static String confessionApproachInterrupted(String maidName) {
        return "\u00a77" + maidName + " （被周围的动静吓了一跳，刚才想说的话咽了回去。）";
    }

    /** v1.5.112:走到身边但 maidmarriage 交互开启失败系统消息 */
    public static String confessionApproachOpenFailed(String maidName) {
        return "\u00a7c" + maidName + " （走到了你身边，但没能开口……请再试一次。）";
    }

    // ==================== v1.3.0 P0:玩家主动告白(玩家开口 + 女仆回应) ====================

    /** 玩家告白屏正文(玩家开口的告白词;%s=女仆名)
     *  v1.5.386:改写——明确主人对女仆的告白口吻,去掉"女仆对主人的那种喜欢"
     *  这种容易被误读为女仆视角的表述("女仆对主人"像女仆在说话);改为
     *  "主人对女仆的那种关怀"——明确是主人对女仆说的话。 */
    public static String playerConfessionIntro(String maidName) {
        return "（你深吸一口气，鼓起勇气看着面前的" + maidName + "，说出了藏在心底已久的话：）"
                + "\n\n\"" + maidName + "，我喜欢你。不是主人对女仆的那种关怀——"
                + "是想要和你永远在一起的那种喜欢。\n"
                + "从第一次见到你的那一天起，我就心动了。\n"
                + "所以……愿意和我交往吗？\"";
    }

    /** 玩家告白屏等待女仆回应态 */
    public static final String PLAYER_CONFESSION_WAITING =
            "（你把话说完了，心怦怦直跳，静静地等着她的回应……）";

    /** 玩家告白成功——女仆的甜蜜回应
     *  v1.5.386:回应里去掉"主人"称呼改为女仆名——主人在告白,女仆回应时
     *  直接叫主人"你"/"你呀"更自然,不再用"主人,你知道我等这句话等了多久" */
    public static String playerConfessionAccepted(String maidName) {
        return "（" + maidName + "愣住了，狐耳倏地竖起，随后整张脸都红透了。"
                + "她低下头，声音又轻又颤：）\"……你知道我等这句话等了多久吗……\n"
                + "我、我也喜欢你——从第一天起就喜欢了！\"\n\n"
                + "（她扑进你怀里，把脸埋在你胸口。）你们成为恋人了。";
    }

    /** 玩家告白失败(不可告白:已确认关系/好感不足)——系统提示 */
    public static String playerConfessionNotReady(String maidName) {
        return "\u00a77" + maidName + " （有些困惑地看着你）——她似乎还没有做好面对这份心意的准备。";
    }

    // ==================== v1.3.0 P5:纪念日 / 回忆杀 ====================

    /** 纪念日回忆对话(%d=里程碑天数,maid 用于读事件文本) */
    public static String anniversaryPrompt(long milestone, EntityMaid maid) {
        // v1.5.48:女儿纪念日父女化——她的纪念日是"爸爸遇见她/她出生"的日子
        if (com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)) {
            MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
            String recall = milestone >= 365L
                    ? (stage == MaidMarriageCompat.ChildStage.ADULT
                        ? "一转眼,爸爸遇见你已经一整年了。你已经长大,回头看这一路,"
                          + "心里满满都是爸爸的爱。请用温柔又感动的语气,对爸爸说一句关于"
                          + "你出生/他把你养大的回忆,让他知道你一直记得,也很感激。"
                        : "一转眼,爸爸遇见你已经一整年了。请用活泼又依恋的语气,"
                          + "对爸爸说一句关于你出生/第一次见到爸爸那天的回忆,"
                          + "让他知道你一直记得。")
                    : (stage == MaidMarriageCompat.ChildStage.ADULT
                        ? "今天是爸爸遇见你的第 " + milestone + " 天。你已经长大,"
                          + "回头看这一路,都是爸爸的爱。请用温柔又感动的语气,对爸爸说一句"
                          + "关于你出生/他把你养大的回忆,让他知道你一直记得。"
                        : "今天是爸爸遇见你的第 " + milestone + " 天。请用活泼又依恋的语气,"
                          + "对爸爸说一句关于你出生/第一次见到爸爸那天的回忆——"
                          + "也许是你还记得爸爸第一次抱起你的样子,让他知道你一直记得。");
            return "今天是一个特别的日子——爸爸遇见你已经 " + milestone + " 天了。\n" + recall
                    + "\n（旁白描写请用括号包裹，台词不加括号）";
        }
        long confessionAt = maid.getPersistentData().m_128454_(
                com.heartfelt.connection.tags.HeartfeltTags.CONFESSION_AT);
        String eventName = confessionAt > 0L ? "我们告白在一起" : "我们初次相遇";
        String recall = milestone >= 365L
                ? "一转眼已经一整年了。请用感慨又温柔的语气,对主人说一句关于这一路走来的心里话,"
                  + "可以提起你们第一次见面/告白的细节,表达珍惜。"
                : "今天是" + milestone + "天纪念日。请用温柔的语气,对主人说一句关于" + eventName + "那天回忆的话,"
                  + "让他知道你一直记得。";
        return "今天是一个特别的日子——" + eventName + "已经过去 " + milestone + " 天了。\n" + recall
                + "\n（旁白描写请用括号包裹，台词不加括号）";
    }

    // ==================== v1.3.1:特殊奶 ====================

    /**
     * 玩家喝下特殊奶后的系统消息(v1.3.2 按次数分级,最多三档)。
     *
     * 考虑到特殊奶的获取途径(产后哺乳期女仆产出)与玩家喝奶的行为,
     * 妈妈(妻子)应有专属文本:越喝越多,她越坦然、越甜,直到把这件事
     * 当成你们之间心照不宣的日常。
     *
     * @param maidName 女仆名
     * @param count    累计喝奶次数(含本次;≥3 封顶到第三档)
     * @param isMother 是否为妻子(妈妈口吻);非妻子用通用口吻
     */
    public static String specialMilkMessage(String maidName, int count, boolean isMother) {
        int tier = Math.max(1, Math.min(count, 3));
        if (isMother) {
            // v1.5.39:妻子口吻——称呼用"丈夫/夫君",不再叫"主人"
            return switch (tier) {
                case 1 -> "\u00a7d" + maidName + "\u00a7r （看着你喝下她为你准备的奶，先是怔住了，"
                        + "随后脸颊" + "\u00a7c倏地\u00a7r 红透，狐耳不自觉地抖了抖，声音细若蚊吟：）"
                        + "\"丈、丈夫……那、那是……从我这里来的奶……你、你真的喝下去了……?\"";
                case 2 -> "\u00a7d" + maidName + "\u00a7r （这次没有躲开你的视线，抿着唇，"
                        + "眼睛亮晶晶的，声音又轻又软：）"
                        + "\"丈夫……喜欢吗?要是喜欢……" + maidName + "以后都给你留着……只给丈夫一个人喝……\"";
                default -> "\u00a7d" + maidName + "\u00a7r （已经习惯了这件事，等你喝完，"
                        + "她轻轻靠过来，狐耳蹭过你的脸颊，带着一点骄傲的甜蜜：）"
                        + "\"丈夫是" + maidName + "的人了……所以连这个，也只给丈夫一个人喝哦。\"";
            };
        }
        return switch (tier) {
            case 1 -> "\u00a7d" + maidName + "\u00a7r （看着你喝下那桶奶，眨了眨眼，凑过来好奇地问：）"
                    + "\"主人……这是什么呀?闻着好香。\"";
            case 2 -> "\u00a7d" + maidName + "\u00a7r （看着你手里的奶桶，若有所思地晃了晃耳朵：）"
                    + "\"主人好像很喜欢喝这个……下次我也帮主人留意着。\"";
            default -> "\u00a7d" + maidName + "\u00a7r （已经见怪不怪了，托着腮看你喝完，弯起眼睛：）"
                    + "\"主人喝饱了就好。\"";
        };
    }

    /** 女仆侧气泡(她对这件事的回应;v1.3.2 按次数分级,修掉旧版 {maid} 字面量占位符;
     *  v1.5.39 加 isMother——妻子用"丈夫"称呼) */
    public static String specialMilkMaidBubble(String maidName, int count, boolean isMother) {
        int tier = Math.max(1, Math.min(count, 3));
        String addr = isMother ? "丈夫" : "主人";
        return switch (tier) {
            case 1 -> addr + "把我准备的奶喝下去了……好、好害羞……";
            case 2 -> addr + "喜欢就好……以后都给" + addr + "留着。";
            default -> addr + "是我一个人的了……心里甜滋滋的。";
        };
    }

    // ==================== v1.4.0:LLM 剧情演绎(Bug3) ====================

    /** 剧情演绎的系统提示(alt+J 对话面板按钮触发;%s=事件历史/记忆文本) */
    public static String dramatizeSystemPrompt(String historyText) {
        return "主人想让你【演绎一段剧情】,而不是正常回应。\n"
                + "请你根据我们共同的经历(下方 Shared History)与你们当前的关系状态,"
                + "以第一人称叙述+对话的形式,演绎一个真实的、有画面感的场景片段——\n"
                + "可以是你们初次见面的回忆、一次共同冒险、某个日常瞬间,"
                + "或是你心里一直想对主人说的一段话。\n"
        + "要求:\n"
        + "- 用小说/galgame 式的文笔,包含旁白与你的台词;\n"
        + "- 旁白描写一律用括号（ ）包裹,台词不加括号;\n"
        + "- 贴合你们的关系阶段(暗恋/恋人/夫妻/父女等)与人物性格;\n"
        + "- 不要提及\"演绎\"\"剧本\"\"记忆\"等系统词,直接沉浸式地演出;\n"
        + "- 长度 3~6 句。\n\n"
                + "你们共同的经历:\n" + (historyText.isEmpty() ? "(你们还没有太多共同回忆,可以演绎此刻的初次相遇)" : historyText);
    }

    // ==================== v1.4.1:玩家伤害惩罚 ====================

    /** 惩罚触发系统消息(玩家视角;%s=女仆名)——无 LLM 也可见
     *  v1.5.69:文本统一去指向——只说她"被弄疼了",不指明来源(玩家放岩浆等
     *  非直接攻击也会进本路径,旧文本"被你打伤了"误导成主人打的) */
    public static String harmPenaltyMessage(String maidName) {
        return "\u00a7c" + maidName + "\u00a7r （被弄疼了……她怔怔地看着你，"
                + "眼眶渐渐红了，一句话也不说，默默坐到一边，背对着你。）";
    }

    /** v1.5.46:女儿(少女/成女)惩罚触发系统消息——女儿被爸爸打,委屈但不敢大声 */
    public static String harmPenaltyMessageChild(String maidName) {
        return "\u00a7c" + maidName + "\u00a7r （整个人都愣住了，眼眶一下子红了，"
                + "咬着嘴唇一句话也不说，默默挪到一边坐下，背对着你，肩膀轻轻抖着。）";
    }

    /** 女仆气泡(固定文本,无 LLM 兜底;%s=女仆名,%s=关系称呼 v1.5.39)
     *  v1.5.69:统一去指向——只说疼,不质问"为什么要这样对我"(来源不明时误导) */
    public static String harmPenaltyBubble(String maidName, String addr) {
        return "呜……好疼……";
    }

    /** v1.5.46:女儿惩罚气泡——哽咽的委屈,不敢质问(v1.5.69:统一去指向) */
    public static String harmPenaltyBubbleChild(String maidName) {
        return "呜……好疼……";
    }

    /** 可选 LLM 伤心对话提示词(有 LLM + 配额时;无 LLM 自动跳过,上面固定文本已兜底)
     *  v1.5.69:统一去指向——只说受了伤,不指明是主人打的 */
    public static String harmPenaltyLLMPrompt(String maidName) {
        return "你刚刚受了伤,身上又痛又委屈,"
                + "心里堵得慌,不想说话。"
                + "请用颤抖的、带着哽咽的语气,对主人说一句很短的话——"
                + "可以是不解、是难过、是委屈,但绝不是讨好。不要长篇大论,一两句就好。"
                + "（旁白描写请用括号包裹，台词不加括号）";
    }

    /** v1.5.46:女儿 LLM 伤心对话提示词——被弄疼了,怕爸爸不喜欢自己了(v1.5.69:去指向) */
    public static String harmPenaltyLLMPromptChild(String maidName) {
        return "你刚刚被弄疼了，心里又难过又委屈。你最喜欢爸爸了，"
                + "怕爸爸不喜欢你了。"
                + "请用带着哭腔、委屈又不敢大声的语气，对爸爸说一句很短的话——"
                + "你是他的女儿，你怕他不喜欢你了。"
                + "（旁白描写请用括号包裹，台词不加括号）";
    }

    // ==================== v1.5.45:伤心解除(消气) ====================

    /** 伤心窗口解除:女仆口吻系统消息(%s=女仆名)——她消气了,沮丧锁定解除,可正常互动 */
    public static String hurtRecoverMessage(String maidName) {
        return "\u00a7d" + maidName + "\u00a7r （垂下眼帘，闷闷地坐了一会儿，终于抬起头：）"
                + "\u201c……哼，这次就算了。\u201d";
    }

    /** v1.5.46:女儿伤心解除——撒娇式原谅 */
    public static String hurtRecoverMessageChild(String maidName) {
        return "\u00a7d" + maidName + "\u00a7r （抽了抽鼻子，偷偷看了你一眼，小声嘟囔：）"
                + "\u201c……那爸爸要答应我，下次不许这样了。\u201d";
    }

    // ==================== v1.5.26:幼年女儿(小婴儿)攻击文本 ====================

    /** 幼年女儿被玩家攻击——系统消息(玩家视角;%s=女仆名)——婴儿只会哭,无需累积直接触发
     *  v1.5.69:统一去指向——"被弄疼了",不写"被你打疼了"(玩家放岩浆等也会进本路径) */
    public static String babyCryMessage(String maidName, boolean favorDropped) {
        // v1.5.67:主人来源伤害扣好感时消息带明确反馈——"减好感度的事件"可见
        return "\u00a7c" + maidName + "\u00a7r 「呜哇——！」（被弄疼了，"
                + "小脸一下子皱起来，眼泪大颗大颗地往下掉，哭得上气不接下气……"
                + (favorDropped ? "好感度 -1）" : "）");
    }

    /** 幼年女儿被攻击——女仆气泡(婴儿只会哭) */
    public static String babyCryBubble(String maidName) {
        return "呜哇——！呜哇——！";
    }

    // ==================== v1.5.28:幼年女儿送礼文本(婴儿版) ====================

    /**
     * maidmarriage 送礼给幼年女儿(INFANT/JUVENILE)时的婴儿反应文本——
     * 原版 CHILD_GIFT_DIALOGUE_KEYS 只有一套「儿童」文本(少女女儿口吻),
     * shouldStayChild 只区分 ADULT/非 ADULT,小婴儿也显示少女文本。
     * 按礼物类别返回奶声奶气的婴儿语(纯固定文本,不走 maidmarriage 脚本)。
     */
    public static String babyGiftText(String category) {
        return switch (category) {
            case "flower" -> "……咿！花……花花！（小手扑腾着要去抓，花瓣沾了一脸也不肯放手。）";
            case "sweet" -> "甜……甜甜！（眼睛一下子亮了，伸出两只小手，口水都快流下来了。）";
            case "meal" -> "咿呀——！香香！（张着小嘴凑过去，吧嗒吧嗒，吃得满脸都是。）";
            case "valuable" -> "亮亮……！（抱着就往嘴里塞，被爸爸拦住后，又好奇地歪着头戳来戳去。）";
            case "odd" -> "嗯？咿？（歪着小脑袋看了半天，小心翼翼地戳了一下，又缩回手，咯咯笑起来。）";
            case "offensive" -> "呜哇——！臭臭！（小脸皱成一团，哇地一声哭出来，直往爸爸怀里钻。）";
            default -> "咿呀！（伸出小手接过礼物，又把它塞回爸爸手里，张开手臂要抱抱。）";
        };
    }

    // ==================== v1.4.2:无 LLM 降级固定文本 ====================

    /**
     * 无 LLM 降级设计(用户确认的"无 LLM 完整运作"原则):
     * 主动对话类功能(家庭互动/广播/哀悼/纪念日等)在 LLM 不可用或配额满时,
     * 改为发固定文本气泡——玩家看到"她在说话",而不是 TLM 的红色报错提示。
     * 以下全部是原版机制(气泡),零 LLM 依赖。
     */

    /** 母女互动降级气泡(%s=发声者名;isMother 区分母亲/女儿) */
    public static String motherDaughterFallback(String speakerName, boolean isMother) {
        return isMother
                ? "（" + speakerName + " 看着你，又看了看身边的女儿，眉眼弯弯地笑了。）"
                : "（" + speakerName + " 往妈妈怀里钻了钻，又抬头朝你甜甜地笑。）";
    }

    /** 父女互动降级气泡(按成长阶段)
     *  v1.5.106:婴儿旁白无台词,幼儿短句,与 LLM 提示词风格一致。 */
    public static String fatherDaughterFallback(MaidMarriageCompat.ChildStage stage, String daughterName) {
        return switch (stage) {
            case INFANT -> "（" + daughterName + " 朝你伸出小手，咿咿呀呀地要抱抱。）";
            case JUVENILE -> "（" + daughterName + " 摇摇晃晃扑过来，奶声奶气地喊：）" + "\u201c爸爸，抱！\u201d";
            case CHILD -> "（" + daughterName + " 拉着你的衣角，仰起脸：）" + "\u201c爸爸，你看我发现什么啦！\u201d";
            default -> "（" + daughterName + " 走到你身边，轻声说：）" + "\u201c爸爸，今天也要注意休息呀。\u201d";
        };
    }

    /** 成年女儿照顾线降级气泡 */
    public static String adultDaughterCareFallback(String daughterName) {
        return "（" + daughterName + " 为你倒了一杯热茶，轻轻放在你手边：）" + "\u201c爸爸，先歇一歇吧。\u201d";
    }

    /** 成长事件降级气泡(系统消息已由 growthMessage 发送,此处是女仆侧回应) */
    public static String growthFallback(MaidMarriageCompat.ChildStage stage, String daughterName) {
        return switch (stage) {
            // v1.5.71:"站起来"台词挪到 JUVENILE(婴儿→幼儿);CHILD(少女)只说长大
            case JUVENILE -> "（" + daughterName + " 摇摇晃晃地站起来，又惊又喜：）" + "\u201c爸爸！你看我！\u201d";
            case CHILD -> "（" + daughterName + " 好奇地打量着自己，好像又长大了一点。）";
            case ADULT -> "（" + daughterName + " 站在你面前，认真地说：）" + "\u201c爸爸，我长大了……以后换我来陪着你。\u201d";
            default -> "（" + daughterName + " 好奇地打量着自己，好像又长大了一点。）";
        };
    }

    /**
     * v1.5.47:幼女成长旁白气泡(她不会说话——无台词,只有动作;绕过话语拦截)
     *  v1.5.50:幼女站不起来,去掉"摇摇晃晃地站起来"动作,只写长大+要抱抱
     *  v1.5.71:婴儿→幼儿瞬间真正站起来——幼儿(旁白,无台词)摇摇晃晃站起来要抱抱
     *  v1.5.106:幼儿(JUVENILE)会说话——站起瞬间加一句奶声奶气的短句,
     *  不再是纯旁白(与阶段语义一致;婴儿仍无台词走旁白)。
     */
    public static String growthFallbackToddler(String daughterName) {
        return "（" + daughterName + " 摇摇晃晃地站起来，睁着圆溜溜的眼睛，又惊又喜地朝你伸出小手：）"
                + "\u201c爸爸！站、站起来了！\u201d";
    }

    // ==================== v1.5.52:女儿交互文本池(少女/成女口吻 + 幼女旁白) ====================
    // maidmarriage 原版"儿童文本池"是幼龄口吻且少女/幼女共用,成年女儿无专属池——
    // 全部在 RomanceSleepManager.scriptForMaid 拦截点按阶段分流(见 ChildGiftTextMixin):
    // 幼女(INFANT/JUVENILE)=无台词旁白(婴儿不说话规则;顺带修掉摸头/抱抱/送花仍会
    // 播台词的缺口),少女(CHILD)=小女孩口吻,成女(ADULT)=父女向口吻。
    // 每条池按天轮换:同一女仆同一天说同一句,跨天换句,不再反复重复同一句台词。

    /** 按 (天数 + maid uuid) 轮换选一句 */
    private static String pickDaughterLine(String[] pool, net.minecraft.world.entity.Entity maid, long day) {
        int idx = (int) ((day + maid.m_20148_().hashCode()) & 0x7fffffff) % pool.length;
        return pool[idx];
    }

    /** 幼女旁白池(无台词,只写动作) */
    private static final String[] BABY_PET = {
            "（被摸摸头，舒服地眯起眼睛，往你手心里蹭了蹭。）",
            "（被摸摸头，咯咯地笑起来，小手努力地够着你的手指。）",
            "（被摸摸头，安安静静地靠在你怀里，抓着你的衣角睡着了。）"};
    private static final String[] BABY_CARRY_START = {
            "（被妈妈抱进怀里，小手攥着妈妈的衣角，安心地闭上了眼睛。）",
            "（被妈妈抱起来，贴在妈妈胸口，咿咿呀呀地蹭了蹭。）"};
    private static final String[] BABY_CARRY_STOP = {
            "（被轻轻放到地上，小脚踩了踩地面，又仰起脸朝妈妈伸出手要抱抱。）"};
    private static final String[] BABY_HUG = {
            "（被抱进怀里，安安静静地贴着爸爸的胸口，不吵也不闹。）",
            "（被抱住，舒服地眯起眼睛，小脑袋往你怀里埋了埋。）"};
    private static final String[] BABY_REVIVE = {
            "（重新见到爸爸，咿咿呀呀地伸手要抱抱，眼泪还挂在眼角。）"};
    private static final String[] BABY_FLOWER = {
            "（收到花，睁大了眼睛盯着花瓣，开心地咿咿呀呀，把花攥得紧紧的。）",
            "（拿着花看了又看，像是发现了什么宝贝，举着小手要递给你看。）"};

    /** 少女(CHILD)池——小女孩口吻 */
    private static final String[] CHILD_PET = {
            "爸爸，再摸一下嘛～今天我可乖啦！",
            "嘿嘿，被爸爸摸头的时候，就像有小太阳落在头顶上一样。",
            "爸爸的手好暖呀……那、那我也摸摸爸爸的头！",
            "摸过头就不许赖账啦，今天要陪我玩哦！"};
    private static final String[] CHILD_CARRY_START = {
            "被爸爸抱起来啦！嘿嘿，我是不是又重了一点点？",
            "抱高高！爸爸再举高一点嘛！",
            "被爸爸抱着好安心……像小时候一样。"};
    private static final String[] CHILD_CARRY_STOP = {
            "到地上啦！爸爸你看，我自己站得好好的！",
            "放我下来啦？那、那下次还要抱抱！"};
    private static final String[] CHILD_HUG = {
            "被爸爸抱着，就像全世界都安静下来了一样。",
            "爸爸的怀抱好大呀，嘿嘿，再抱一小会儿嘛。",
            "抱抱！今天也要抱抱！……好啦，满足了！"};
    private static final String[] CHILD_LEARN = {
            "爸爸，书上的字都在跳舞了……我可以休息一下吗？",
            "爸爸，我记住了好多好多！不过脑袋有点转不动了……",
            "今天学得好认真，爸爸可不可以奖励我一个摸摸头？",
            "呜……这个好难。爸爸可以陪我一起看吗？"};
    private static final String[] CHILD_REVIVE = {
            "爸爸！我还以为再也见不到你了……呜，不过现在没事啦！",
            "爸爸……我回来了。嘿嘿，是不是吓到你了？"};

    /** 成女(ADULT)池——父女向口吻 */
    private static final String[] ADULT_PET = {
            "都这么大了还被爸爸摸头……有点不好意思，但我不讨厌。",
            "爸爸的掌心还是和以前一样暖。小时候，您也是这样摸我的头的吧？",
            "再摸的话，我可要开始撒娇了哦。……开玩笑的。嗯，真的。",
            "被爸爸摸头的时候，总觉得什么烦恼都能放下一点。"};
    private static final String[] ADULT_PET_LIMIT = {
            "好了啦，爸爸，摸太多我会不好意思的。",
            "爸爸……再摸下去，我可要躲开了哦。"};
    private static final String[] ADULT_HUG = {
            "被爸爸这样抱着……总觉得像回到了小时候。",
            "爸爸的怀抱还是这么宽。嗯，这样待一会儿就好。",
            "有点害羞……但被爸爸抱着的时候，什么都能安心下来。"};

    /** 送礼池:少女/成女各 7 类 × 3 条(flower 与送花键共用) */
    private static String[] daughterGiftPool(boolean adult, String cat) {
        if (adult) {
            return switch (cat) {
                case "flower" -> new String[]{
                        "爸爸还给我带花……都这么大了还被爸爸宠着，有点不好意思呢。",
                        "好漂亮的花。爸爸，我把它插在瓶子里，放在客厅好不好？",
                        "谢谢爸爸。您挑花的眼光，从来都没变过。"};
                case "sweet" -> new String[]{
                        "爸爸还记得我爱吃甜的呀……都这么大了还哄我。",
                        "是甜食。爸爸，您自己也留一份，别光顾着我。",
                        "甜甜的……像小时候您偷偷塞给我的糖一样。"};
                case "meal" -> new String[]{
                        "爸爸是担心我没好好吃饭吗？放心，我会照顾好自己的。",
                        "谢谢爸爸。下次换我来给您做饭吧，我已经学会好几道了。",
                        "爸爸做的饭，总是最好吃的。"};
                case "valuable" -> new String[]{
                        "这么贵重的东西……爸爸，您留着给自己用吧。",
                        "爸爸，这太贵重了。……不过既然是您的心意，我会好好珍惜的。",
                        "我会小心收好的，就像收着您给我的每一份心意一样。"};
                case "odd" -> new String[]{
                        "爸爸……这个，您是认真的吗？",
                        "噗。爸爸，您从哪儿找来的这个东西？",
                        "虽然有点奇怪……不过既然是爸爸送的，我就当宝贝收着吧。"};
                case "offensive" -> new String[]{
                        "爸爸。……这个，一点都不好笑。",
                        "您这样，我可是会生气的哦。……哼，下次不许这样了。",
                        "呜……爸爸，您是不是在外面学坏了？"};
                default -> new String[]{
                        "谢谢爸爸。能被您记着，就是最好的礼物了。",
                        "是给我的吗？爸爸总是这样，什么都想着我。",
                        "谢谢您，爸爸。我也会好好记住这一天的。"};
            };
        }
        return switch (cat) {
            case "flower" -> new String[]{
                    "好漂亮的花！谢谢爸爸！我要把它放在床头，天天看着它！",
                    "花花！爸爸的眼光最好啦！",
                    "哇，是花！我可以摘一朵别在头发上吗？"};
            case "sweet" -> new String[]{
                    "甜甜的！爸爸最懂我啦！",
                    "是甜食！我要省着吃，一天吃一小口！",
                    "谢谢爸爸！甜到心里去啦！"};
            case "meal" -> new String[]{
                    "爸爸给我准备吃的了！我会乖乖吃完的！",
                    "好香呀……爸爸是担心我没好好吃饭吗？",
                    "谢谢爸爸！吃完了我要长得比爸爸还高！"};
            case "valuable" -> new String[]{
                    "这么亮的东西……爸爸，是不是很贵重？我会小心收好的！",
                    "哇……这个好厉害。爸爸，你自己也要留一个用呀。",
                    "我会好好珍惜的，因为这是爸爸送给我的！"};
            case "odd" -> new String[]{
                    "这个……好奇怪呀。不过既然是爸爸送的，我就收下啦！",
                    "咦？这是什么呀？不管啦，是爸爸给的就好！",
                    "好奇怪的东西……嘿嘿，爸爸又在逗我玩！"};
            case "offensive" -> new String[]{
                    "呜……爸爸，这个让我有点难过。下次送朵花好不好？",
                    "爸爸……这个是不是在欺负我呀？",
                    "呜，我不喜欢这个。爸爸道歉的话，我就原谅你！"};
            default -> new String[]{
                    "谢谢爸爸！我会一直记着的！",
                    "是给我的吗？爸爸最好啦！",
                    "嘿嘿，收到爸爸的礼物，今天一整天都会很开心！"};
        };
    }

    /**
     * v1.5.52 统一分流入口(ChildGiftTextMixin 调用):按键前缀 + 女儿阶段返回专属文本,
     * 返回 null 表示不拦截(走 maidmarriage 原版)。
     * v1.5.105:阶段语义——INFANT=旁白动作、JUVENILE=简单句子、CHILD=小女孩、
     * ADULT=父女向。mixin 现在对 tooSmall 也会传 stage,这里按 stage 区分
     * 婴儿(旁白池)与幼儿(简单句子池),不再把两者混为一个旁白池。
     */
    public static String daughterDialogueText(EntityMaid maid, String key, boolean tooSmall,
                                              MaidMarriageCompat.ChildStage stage, long day) {
        // 幼儿:非面板交互(送礼/花/摸头/抱抱/复活/思慕/拥抱)全部说简单句子
        boolean juvenile = stage == MaidMarriageCompat.ChildStage.JUVENILE;
        // 婴儿:仍走旁白池(她不会说话)
        boolean infant = tooSmall && !juvenile;
        if (key.startsWith("dialogue.maidmarriage.child_gift.")) {
            String cat = key.substring("dialogue.maidmarriage.child_gift.".length());
            if (juvenile) {
                return pickDaughterLine(JUVENILE_GIFT_REPLY, maid, day);
            }
            if (infant) {
                return babyGiftText(cat); // v1.5.28 婴儿版保留(旁白)
            }
            if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                return pickDaughterLine(daughterGiftPool(false, cat), maid, day);
            }
            return null;
        }
        if (key.startsWith("dialogue.maidmarriage.child.flower.")) {
            if (juvenile) {
                return pickDaughterLine(JUVENILE_FLOWER, maid, day);
            }
            if (infant) {
                return pickDaughterLine(BABY_FLOWER, maid, day);
            }
            if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                return pickDaughterLine(daughterGiftPool(false, "flower"), maid, day);
            }
            return null;
        }
        if (key.startsWith("dialogue.maidmarriage.child_pet_head.")) {
            if (juvenile) {
                return pickDaughterLine(JUVENILE_PET_BUBBLE, maid, day);
            }
            if (infant) {
                return pickDaughterLine(BABY_PET, maid, day);
            }
            if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                return pickDaughterLine(CHILD_PET, maid, day);
            }
            return null;
        }
        if (key.startsWith("dialogue.maidmarriage.carry_child.")) {
            if (key.startsWith("dialogue.maidmarriage.carry_child.infant_hold.")) {
                return null; // 妈妈台词,不拦
            }
            boolean start = key.endsWith(".start");
            if (juvenile) {
                return pickDaughterLine(start ? JUVENILE_CARRY_START : JUVENILE_CARRY_STOP, maid, day);
            }
            if (infant) {
                return pickDaughterLine(start ? BABY_CARRY_START : BABY_CARRY_STOP, maid, day);
            }
            if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                return pickDaughterLine(start ? CHILD_CARRY_START : CHILD_CARRY_STOP, maid, day);
            }
            return null;
        }
        if (key.startsWith("dialogue.maidmarriage.child.learn.exhausted.")) {
            if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                return pickDaughterLine(CHILD_LEARN, maid, day); // 幼女锁任务不触发
            }
            return null;
        }
        if (key.startsWith("dialogue.maidmarriage.revive.child")) {
            if (juvenile) {
                return pickDaughterLine(JUVENILE_REVIVE, maid, day);
            }
            if (infant) {
                return pickDaughterLine(BABY_REVIVE, maid, day);
            }
            if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                return pickDaughterLine(CHILD_REVIVE, maid, day);
            }
            return null;
        }
        // v1.5.57:思慕(longing.* / longing_night / longing_saddle / longing_wait)——
        // maidmarriage 触发链无女儿检查,女儿 3 天没互动会触发恋爱向思慕
        // ("好想被你抱着");女儿只能"想爸爸"(三档父女向)
        if (key.startsWith("dialogue.maidmarriage.longing")) {
            if (juvenile) {
                return pickDaughterLine(JUVENILE_LONGING, maid, day);
            }
            if (infant) {
                return pickDaughterLine(BABY_LONGING, maid, day);
            }
            if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                return pickDaughterLine(CHILD_LONGING, maid, day);
            }
            if (stage == MaidMarriageCompat.ChildStage.ADULT) {
                return pickDaughterLine(ADULT_LONGING, maid, day);
            }
            return null;
        }
        // ---- 成人键:成女(ADULT)=父女向;少女/幼女不命中(少女走 child_* 键) ----
        if (key.startsWith("dialogue.maidmarriage.gift.")) {
            String cat = key.substring("dialogue.maidmarriage.gift.".length());
            if (stage == MaidMarriageCompat.ChildStage.ADULT) {
                return pickDaughterLine(daughterGiftPool(true, cat), maid, day);
            }
            return null; // 幼年 → 落到底部兜底(婴儿旁白/幼儿句子)
        }
        if (key.startsWith("dialogue.maidmarriage.pet_head.")) {
            boolean limit = key.contains("limit_warm");
            if (stage == MaidMarriageCompat.ChildStage.ADULT) {
                return pickDaughterLine(limit ? ADULT_PET_LIMIT : ADULT_PET, maid, day);
            }
            return null; // 幼年 → 落到底部兜底(婴儿旁白/幼儿句子)
        }
        if (key.equals("dialogue.maidmarriage.hug.start")) {
            if (juvenile) {
                return pickDaughterLine(JUVENILE_HUG, maid, day);
            }
            if (infant) {
                return pickDaughterLine(BABY_HUG, maid, day);
            }
            if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                return pickDaughterLine(CHILD_HUG, maid, day);
            }
            if (stage == MaidMarriageCompat.ChildStage.ADULT) {
                return pickDaughterLine(ADULT_HUG, maid, day);
            }
            return null;
        }
        // v1.5.359:全量兜底——tooSmall 的所有未覆盖剧本键(成人送礼 gift.*、pet_head 成人键、
        // 陪她说话等其他一切对话)一律替换为婴儿旁白,原版文本完全不触发
        // (用户:"要将这些全量替换掉,原有的旧文本完全不触发";旧版漏网键返回 null 放行原版)。
        // v1.5.105:婴儿走旁白,幼儿走简单句子。
        if (juvenile) {
            return juvenileDialogueReply(key, maid);
        }
        if (tooSmall) {
            return infantDialogueReply(key, maid);
        }
        return null;
    }

    // ==================== v1.5.53:归来汇报 / 受伤关心 / 家务话题 / 三人同场 ====================

    /** 少女学习归来汇报(周期检测 maidmarriage 任务完成瞬间,按天轮换)
     *  v1.5.54:去掉"老师/功课"——游戏里没有老师,学习任务是自学
     *  (附魔学/药剂学/战术学,消耗书本/玻璃瓶/武器) */
    private static final String[] CHILD_LEARN_REPORT = {
            "爸爸！我学完啦！书上的东西，我记住了好多好多！",
            "爸爸爸爸！今天的学习我都完成啦，快夸夸我！",
            "学习结束！爸爸，你看我是不是又厉害了一点点？",
            "嘿嘿，今天学到好多新东西，明天讲给爸爸听！"};
    private static final String[] CHILD_EXPLORE_REPORT = {
            "爸爸！我探险回来啦！外面的世界好大呀！",
            "回来啦！爸爸你看，我带回了这么多好玩的东西！",
            "探险结束！虽然有点累，但是好开心呀！",
            "爸爸爸爸！我在外面看到了好漂亮的风景，下次带爸爸一起去！"};

    public static String childActionReport(boolean explore, EntityMaid maid, long day) {
        return pickDaughterLine(explore ? CHILD_EXPLORE_REPORT : CHILD_LEARN_REPORT, maid, day);
    }

    /** 爸爸受伤时女儿的关心(三档,每女仆 5 分钟冷却,按天轮换) */
    private static final String[] BABY_HURT_WATCH = {
            "（看到爸爸受伤，吓得哇哇大哭起来，伸出小手要抱抱。）",
            "（看到爸爸流血，眼泪一下子涌了出来，哭得上气不接下气。）"};
    private static final String[] CHILD_HURT_WATCH = {
            "爸爸！你受伤了！呜……痛不痛？我给你吹吹！",
            "爸爸小心一点嘛！……痛不痛呀？我去叫妈妈来！",
            "呜……爸爸别动，我去找药箱！……下次不许这样了！"};
    private static final String[] ADULT_HURT_WATCH = {
            "爸爸！您受伤了？快坐下，我去拿药。",
            "爸爸，您怎么这么不小心……伤口让我看看。",
            "别动，爸爸。我去给您包扎一下，很快就回来。"};

    public static String hurtWatchText(boolean tooSmall, EntityMaid maid, long day) {
        // v1.5.105:幼儿说简单句子,婴儿仍旁白
        MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
        String[] pool;
        if (stage == MaidMarriageCompat.ChildStage.JUVENILE) {
            pool = JUVENILE_HURT_WATCH;
        } else if (tooSmall) {
            pool = BABY_HURT_WATCH;
        } else if (stage == MaidMarriageCompat.ChildStage.ADULT) {
            pool = ADULT_HURT_WATCH;
        } else {
            pool = CHILD_HURT_WATCH;
        }
        return pickDaughterLine(pool, maid, day);
    }

    /** v1.5.57:女儿思慕文本(三档)——maidmarriage 的 longing 触发链无女儿检查,
     *  女儿 3 天没互动会触发恋爱向思慕("好想被你抱着");拦截后女儿只"想爸爸" */
    private static final String[] BABY_LONGING = {
            "（爸爸不在身边，她坐在原地，眼巴巴地望着爸爸离开的方向。）",
            "（爸爸好久没回来了，她抱着自己的小手，安静地等着。）"};
    private static final String[] CHILD_LONGING = {
            "爸爸，你今天都没有怎么陪陪我……",
            "爸爸什么时候回来呀……我一直在等爸爸呢。",
            "呜，爸爸去哪儿了……我好想爸爸呀。",
            "爸爸！你终于来啦！我等你好久好久了！"};
    private static final String[] ADULT_LONGING = {
            "爸爸今天又忙到很晚……回来了就好。",
            "您不在的时候，家里总觉得空落落的。",
            "爸爸，回来了？饭还温着，先去洗手吧。",
            "等您回来的时候，我总是不由自主地往门口看。"};

    /** v1.5.53:成女家务话题(原 adultDaughterCarePrompt 单条扩为 4 话题按天轮换) */
    public static String adultDaughterCarePrompt(long day) {
        return switch ((int) (day % 4)) {
            case 0 -> "你是已经长大成人的女儿，今天亲手做了爸爸爱吃的菜。请用温柔又带着一点撒娇的语气，"
                    + "对爸爸说一句让他趁热吃的话。（旁白描写请用括号包裹，台词不加括号）";
            case 1 -> "你是已经长大成人的女儿，刚把家里收拾得干干净净。请用温柔的语气，"
                    + "对爸爸说一句家里的事交给你就好的话。（旁白描写请用括号包裹，台词不加括号）";
            case 2 -> "你是已经长大成人的女儿，刚刚在屋子周围转了一圈，确认一切都安好。请用温柔又可靠的语气，"
                    + "对爸爸说一句让他放心的话。（旁白描写请用括号包裹，台词不加括号）";
            default -> "你是已经长大成人的女儿，看爸爸忙了一整天。请用温柔又带着一点心疼的语气，"
                    + "劝爸爸早点休息。（旁白描写请用括号包裹，台词不加括号）";
        };
    }

    public static String adultDaughterCareFallback(String daughterName, long day) {
        return switch ((int) (day % 4)) {
            case 0 -> "（" + daughterName + " 端着热腾腾的饭菜走到你面前，笑着说：）"
                    + "\u201c爸爸，趁热吃，我今天试了新的做法！\u201d";
            case 1 -> "（" + daughterName + " 把家里收拾得整整齐齐，拍拍手走到你身边：）"
                    + "\u201c爸爸，家里的事就交给我吧，你安心忙你的。\u201d";
            case 2 -> "（" + daughterName + " 绕着屋子转了一圈回来，认真地说：）"
                    + "\u201c爸爸，周围我都看过了，一切安好，您放心。\u201d";
            default -> "（" + daughterName + " 轻轻拿走你手里的东西，温柔地说：）"
                    + "\u201c爸爸，今天就到这里吧，早点休息，明天我陪您。\u201d";
        };
    }

    /** v1.5.53:三人同场——妈妈在场时父女互动变体(按阶段,按天轮换) */
    public static String familyThreePrompt(MaidMarriageCompat.ChildStage stage, long day) {
        if (stage == MaidMarriageCompat.ChildStage.ADULT) {
            return switch ((int) (day % 2)) {
                case 0 -> "你和妈妈都在爸爸身边，一家三口难得聚齐。请对爸爸说一句温柔的话——"
                        + "可以是一点感慨，也可以是一个小小的愿望。（旁白描写请用括号包裹，台词不加括号）";
                default -> "妈妈就在你身边，爸爸也在，一家人难得都在一起。请用温柔又带一点撒娇的语气，"
                        + "对爸爸说一句希望一家人常常这样在一起的话。（旁白描写请用括号包裹，台词不加括号）";
            };
        }
        return switch ((int) (day % 2)) {
            case 0 -> "妈妈就在你身边，爸爸也在。一家三口难得这样围在一起，你心里满满的。"
                    + "请对爸爸说一句关于我们一家三口的话——撒娇、幸福、或一个小小的愿望。"
                    + "（旁白描写请用括号包裹，台词不加括号）";
            default -> "妈妈和爸爸都在你身边，好幸福呀。请用活泼又撒娇的语气，"
                    + "对爸爸说一句一家三口在一起的话。（旁白描写请用括号包裹，台词不加括号）";
        };
    }

    public static String familyThreeFallback(MaidMarriageCompat.ChildStage stage, String daughterName, long day) {
        if (stage == MaidMarriageCompat.ChildStage.ADULT) {
            return switch ((int) (day % 2)) {
                case 0 -> "（" + daughterName + " 帮妈妈端来热茶，在你身边坐下，轻声说：）"
                        + "\u201c爸爸，妈妈，能和你们在一起，是我最开心的事。\u201d";
                default -> "（" + daughterName + " 依偎在妈妈身边，看着你，眉眼都弯弯的：）"
                        + "\u201c爸爸，以后我们也常常这样，好不好？\u201d";
            };
        }
        return switch ((int) (day % 2)) {
            case 0 -> "（" + daughterName + " 看看妈妈，又看看你，开心地晃着你的手：）"
                    + "\u201c爸爸，妈妈，我们三个在一起，真好呀！\u201d";
            default -> "（" + daughterName + " 拉着你和妈妈的手，仰起脸：）"
                    + "\u201c爸爸，下次我们还一起出去玩好不好！\u201d";
        };
    }

    /** 暗恋者感慨降级气泡 */
    public static String crushSighFallback(String maidName) {
        return "（" + maidName + " 远远望着你和妻子，轻轻弯起嘴角，眼底带着一丝释然。）";
    }

    /** 纪念日降级气泡(按里程碑天数) */
    public static String anniversaryFallback(long milestone, String maidName) {
        if (milestone >= 365L) {
            return "（" + maidName + " 忽然轻声开口：）" + "\u201c……已经一整年了呢。谢谢你，一直陪着我。\u201d";
        }
        return "（" + maidName + " 望着你，眼底泛起温柔：）" + "\u201c今天……是个特别的日子呢。你忘了吗？\u201d";
    }

    /** v1.5.48:女儿纪念日降级气泡——父女向(爸爸记得女儿来到身边的日子) */
    public static String anniversaryFallbackChild(long milestone, String maidName) {
        if (milestone >= 365L) {
            return "（" + maidName + " 依偎在你身边，轻声说：）"
                    + "\u201c爸爸……遇见你已经一整年啦。谢谢你，一直这么疼我。\u201d";
        }
        return "（" + maidName + " 仰起脸望着你，眼底亮晶晶的：）"
                + "\u201c爸爸，你还记得我来到你身边的那天吗？\u201d";
    }

    /** v1.5.27:纪念日触发系统消息(玩家视角;%s=女仆名,%s=里程碑,%s=基准事件名)——触发"看得见" */
    public static String anniversarySystemMessage(String maidName, long milestone, String eventName) {
        return "\u00a7d" + maidName + "\u00a7r （与你的「" + eventName + "」已经第 "
                + milestone + " 天了——今天是你们的纪念日，她/他想对你说点什么。）";
    }

    /** v1.5.48:女儿纪念日触发系统消息——父女向(爸爸记得女儿出生/初见的日子) */
    public static String anniversarySystemMessageChild(String maidName, long milestone) {
        return "\u00a7d" + maidName + "\u00a7r （与你的「出生/初见」已经第 "
                + milestone + " 天了——爸爸记得女儿来到身边的日子。）";
    }

    /** 关系广播降级气泡(按关系标签:妻子/女儿/恋人) */
    public static String broadcastFallback(String label, String maidName) {
        return switch (label) {
            case "妻子" -> "（" + maidName + " 望着你们，眼里闪着光：）" + "\u201c……真好呀，主人。\u201d";
            case "女儿" -> "（" + maidName + " 好奇地望着那个孩子：）" + "\u201c……这就是主人的女儿吗？真可爱。\u201d";
            default -> "（" + maidName + " 轻轻笑了：）" + "\u201c……主人要幸福呀。\u201d";
        };
    }

    // ==================== v1.5.6:死亡调侃(替代"死亡哀悼") ====================

    /**
     * 主人死亡后女仆的调侃/关心反馈(v1.5.6 设计修正):
     * 玩家会复活、女仆会传送重生点——"哀悼 1 天拒绝互动"与复活机制矛盾。
     * 改为死亡瞬间的一句调侃/关心(一次文本,无状态窗口)。
     * label:RelationshipExemption.relationLabel(妻子/女儿/恋人/null)。
     */
    public static String deathTeaseMessage(String maidName, String relationLabel) {
        return switch (relationLabel) {
            case "妻子" -> "\u00a7d" + maidName + "\u00a7r （叉着腰看你复活回来，又气又好笑：）"
                    + "\u201c丈夫啊丈夫，你要是再这么乱来，我可要生气了……来，先让我看看伤着哪儿了没有。\u201d";
            case "女儿" -> "\u00a7d" + maidName + "\u00a7r （眼眶红红的，扑过来抱住你：）"
                    + "\u201c爸爸！吓死我了！你答应我，下次不许这样了！\u201d";
            case "恋人" -> "\u00a7d" + maidName + "\u00a7r （轻轻叹了口气，替你拍掉身上的灰：）"
                    + "\u201c……你啊，总是这么让人担心。下次，让我陪着你一起好不好？\u201d";
            default -> "\u00a7d" + maidName + "\u00a7r （关切地看着你：）"
                    + "\u201c主人，您没事吧？要不要休息一下？\u201d";
        };
    }

    /** 死亡调侃:女仆气泡(%s=关系称呼 v1.5.39) */
    public static String deathTeaseBubble(String addr) {
        return addr + "……你吓死我了……";
    }

    // ==================== v1.5.8:对话安全区 ====================

    /** Alt+J 对话面板被敌人阻挡:系统消息(玩家先打掉敌人才能对话) */
    public static String dialogueBlockedByHostiles() {
        return "\u00a7c附近有敌人……还是先把它们解决掉，再来好好说话吧。";
    }

    // ==================== v1.5.4:思慕/破裂可见反馈 ====================

    /** 思慕设置:玩家系统消息(说明可见效果) */
    public static String longingSetMessage(String maidName) {
        return "\u00a7d" + maidName + "\u00a7r 看起来有些寂寞——已经很久没有和你亲近了。"
                + "（靠近她时,她会冒出心形粒子与思慕对话）";
    }

    /** 破裂设置:玩家系统消息(说明它影响什么) */
    public static String breakupSetMessage(String maidName) {
        return "\u00a77已记录 1 次关系破裂——" + maidName + " 会记得这件事,"
                + "对话与记忆中会有所体现。";
    }

    /** 演绎剧情不可用提示(玩家系统消息;alt+J 面板按钮点击时) */
    public static String dramatizeUnavailable() {
        return "\u00a77LLM 尚未配置或今日对话配额已满——她只能用平常的方式和你说话。";
    }

    // ==================== v1.5.345:抱起回应 ====================

    /** 抱起回应 LLM 提示词(有 LLM + 配额时;无 LLM 走 pickupFallback 固定气泡) */
    public static String pickupLLMPrompt(String relation) {
        return "主人把你轻轻抱了起来。"
                + "请用贴合你与主人关系的语气,对主人说一句很短的话回应这个举动"
                + "——可以是撒娇、是害羞、是开心,也可以是小声嘟囔。"
                + "不要长篇大论,一两句就好。"
                + "（旁白描写请用括号包裹，台词不加括号）";
    }

    /** 抱起回应降级气泡(无 LLM/配额满时固定文本;女儿按成长阶段区分口吻) */
    public static String pickupFallback(EntityMaid maid, String relation) {
        if ("daughter".equals(relation)) {
            MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
            if (stage == MaidMarriageCompat.ChildStage.ADULT) {
                return "\u201c爸……爸爸？都这么大了还把我抱起来……\u201d（嘴上嘟囔着，手却悄悄搂紧了你的脖子）";
            }
            return "\u201c爸爸爸爸！举高高——！\u201d（她开心地扑腾着小腿，咯咯直笑）";
        }
        if ("wife".equals(relation)) {
            return "\u201c诶呀……怎么突然把我抱起来了呀？\u201d（她先是一愣，随即弯起眼睛，笑着搂住你的脖子）";
        }
        return "\u201c诶诶？！等、等一下啦——！\u201d（她脸一下子红了，小声嘟囔着，却乖乖靠在你怀里）";
    }

    // ==================== v1.5.346:思慕明显效果 ====================

    /** 思慕触发系统消息(玩家视角;每日一次)——明确告诉她正在思念主人 */
    public static String longingEffectMessage(String maidName, String relationLabel) {
        String label = relationLabel == null ? "" : relationLabel + "·";
        return "\u00a7d" + label + maidName + "\u00a7r 正望着你的方向出神——"
                + "她已经好几天没和你亲近了，心里满满都是思念。"
                + "（靠近她，会冒出心形粒子，还会对你说想你的话）";
    }

    /** 思慕气泡(固定文本;妻子/恋人/女儿分文案;女儿按成长阶段用 LONGING 池轮换) */
    public static String longingBubble(EntityMaid maid) {
        String relation = com.heartfelt.connection.relationship.RelationshipExemption.strictRelationKey(maid);
        if ("daughter".equals(relation)) {
            MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
            String[] pool;
            if (stage == MaidMarriageCompat.ChildStage.ADULT) {
                pool = ADULT_LONGING;
            } else if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                pool = CHILD_LONGING;
            } else {
                pool = BABY_LONGING;
            }
            long day = maid.m_9236_().m_46467_() / 24000L;
            return pickDaughterLine(pool, maid, day);
        }
        if ("wife".equals(relation)) {
            return "\u201c……主人，你都多久没好好抱抱我了。\u201d（她望着你，眼里都是委屈的思念）";
        }
        return "\u201c……喂，你今天是不是都快把我忘了？\u201d（她小声嘟囔着，眼睛却一直追着你）";
    }

    // ==================== v1.5.359:婴儿剧本对话替换 ====================

    // ==================== v1.5.366:女儿对话选项专属文本池 ====================

    /**
     * 阶段语义(v1.5.105,用户澄清):
     * - INFANT 婴儿:不会说话——只给旁白/动作描写(INFANT_* 池);
     * - JUVENILE 幼儿:能说简单的句子(JUVENILE_* 池,奶声奶气短句);
     * - CHILD 少女:小女孩口吻(CHILD_* 池);
     * - ADULT 成年:父女向(ADULT_* 池)。
     * maidmarriage 为 4 阶段(INFANT→JUVENILE→CHILD→ADULT),全部按此处理。
     */

    /** 婴儿(INFANT)·儿童场景选项(旁白无台词,不会说话)
     *  原 JUV_* 旁白池迁入——婴儿才是"不说话"的那个,幼儿已改为简单句子。 */
    private static final String[] INFANT_PET = {
            "（被摸摸头，她眯起眼睛，舒服地往你手心里蹭了蹭。）",
            "（被摸摸头，她咯咯地笑起来，小手紧紧抓住你的手指不放。）",
            "（被摸摸头，她仰着小脸看你，眼睛亮晶晶的。）",
            "（被摸摸头，她乖乖地站在原地，还踮了踮小脚想凑得更近。）",
            "（被摸摸头，她舒服得眯起眼，小脑袋不自觉地往你掌心里靠。）",
            "（被摸摸头，她先是愣了一瞬，随即开心地挥起小手，咿咿呀呀地笑。）",
            "（被摸摸头，她小心翼翼地抓住你一根手指，像是握住了什么宝贝。）",
            "（被摸摸头，她仰起头，眼睛弯成月牙，小嘴咧开露出没长齐的牙。）"};
    private static final String[] INFANT_LIFT = {
            "（被你举起来，她晃着小脚丫，开心得眼睛都弯成了月牙。）",
            "（被你举高，她先是愣了一下，随即咯咯笑着张开小手。）",
            "（被举起来，她低头看着地面，又抬头看你，兴奋地拍了拍小手。）",
            "（被举高高，她咯咯地笑个不停，小短手努力地朝你伸着。）",
            "（被你举过头顶，她张开手臂像小鸟一样扑腾，笑声响亮。）",
            "（被举起来，她先是吓得抓紧你的手，发现稳稳的又咯咯笑起来。）",
            "（被举高，她好奇地看着变远的地面，又看看你，眼里满是信赖。）",
            "（被你举起来转了个圈，她开心得直拍手，小脸都笑红了。）"};
    private static final String[] INFANT_CARRY = {
            "（被妈妈抱进怀里，她安静下来，小脑袋靠着妈妈的肩膀。）",
            "（被妈妈抱住，她扭了扭身子，找了个舒服的位置窝好。）",
            "（妈妈抱着她，她的小手抓着妈妈的衣角，安心地眯起了眼睛。）",
            "（被妈妈抱起来，她看了看你，又看了看妈妈，满足地笑了。）",
            "（被妈妈抱稳，她打了个小小的哈欠，往妈妈怀里缩了缩。）",
            "（被妈妈抱着，她伸出小手朝你挥了挥，像是在说\u201c爸爸也来呀\u201d。）",
            "（被妈妈抱进怀里，她贴着妈妈的胸口，听着心跳渐渐安静下来。）",
            "（被妈妈抱住，她满足地蹭了蹭，小脚丫轻轻晃着。）"};
    private static final String[] INFANT_COMFORT = {
            "（她仰着小脸看你，咿咿呀呀地挥着小手，像是在回应你说的话。）",
            "（她歪着头看你，张了张小嘴，发出含糊的咿呀声。）",
            "（她安静地看着你，小手指了指你，又指了指自己，开心地笑了。）",
            "（你蹲下来陪她说话，她认真地看着你的嘴型，学着咿了一声。）",
            "（你轻声跟她说话，她眨着大眼睛，似懂非懂地点头，又摇头。）",
            "（你陪她玩了一会儿，她累了，靠在你腿边，抓着你的衣角不放。）",
            "（你说话时她安静地听着，偶尔蹦出一个含糊的音节，像是在应和。）",
            "（她看着你笑，张开小手要抱，又指了指自己的脸颊，像在讨一个亲亲。）"};

    /** 幼儿(JUVENILE)·儿童场景选项(简单句子,奶声奶气)
     *  v1.5.105:幼儿能说简单的话——不再是旁白,换成短句;短词短句符合年龄。 */
    private static final String[] JUVENILE_PET = {
            "摸……摸摸！",
            "嘿嘿，爸爸摸！",
            "头，软软！",
            "再摸，还要！",
            "暖，暖暖的！",
            "爸爸手，好大！",
            "摸摸，舒服！",
            "嘿嘿，喜欢！"};
    private static final String[] JUVENILE_LIFT = {
            "高高！飞飞！",
            "哇——高！",
            "再高！再高！",
            "飞啦——！",
            "爸爸，厉害！",
            "哈哈，看到天！",
            "转，转转！",
            "举，还要！"};
    private static final String[] JUVENILE_CARRY = {
            "妈妈，抱！",
            "暖暖……",
            "妈妈香香！",
            "抱抱，好舒服……",
            "妈妈，听心跳！",
            "不，不下来！",
            "妈妈怀里，软软……",
            "哈欠……想睡……"};
    private static final String[] JUVENILE_COMFORT = {
            "爸爸！",
            "嗯……嗯！",
            "爸爸，陪我玩！",
            "看！花花！",
            "爸爸，手手！",
            "嘻嘻，爸爸好！",
            "说……说话！",
            "爸爸，不走！"};

    /** 少女(CHILD)·儿童场景选项(小女孩口吻)
     *  v1.5.104:每池扩到 8 条,与幼儿旁白区分更明显,丰富度对齐成年女儿。 */
    private static final String[] CHILD_PET_2 = {
            "嘿嘿……爸爸再摸摸嘛！",
            "爸爸的手好暖和，再摸一下下！",
            "被爸爸摸摸头，感觉整个人都轻飘飘的～",
            "爸爸爸爸，我乖乖的，可以多摸一会儿吗？",
            "嘻嘻，爸爸摸头最舒服啦，比妈妈梳头发还舒服！",
            "爸爸，你摸摸看，我今天是不是又长高了一点点？",
            "嘿嘿，被爸爸摸头的时候，我连尾巴都想摇起来了！",
            "爸爸的手掌大大的，摸在头上刚刚好～"};
    private static final String[] CHILD_LIFT_2 = {
            "哇——好高！爸爸最厉害了！",
            "举高高！再举一次！我还没有玩够！",
            "哈哈，我看到屋顶啦！爸爸再高点！",
            "呜哇——好高好高！但是我一点都不怕！",
            "再高一点点！我要够到那片云！",
            "爸爸举高高的时候，风从耳朵边呼呼地过，好好玩！",
            "哇！我比爸爸还高啦！……诶，又掉下来了。",
            "举高高！转圈圈！爸爸再陪我玩一会儿嘛！"};
    private static final String[] CHILD_CARRY_2 = {
            "妈妈抱抱最舒服啦……",
            "被妈妈抱着，感觉整个世界都软软的。",
            "妈妈身上香香的，我都不想下来了……",
            "妈妈抱抱的时候，我能听到妈妈的心跳声呢。",
            "妈妈怀里暖暖的，像盖着最软的小被子。",
            "嘿嘿，被妈妈抱着，我就什么都不怕啦。",
            "妈妈，再抱一会儿好不好？就一会儿！",
            "妈妈抱我的时候，我连做梦都是甜甜的。"};
    private static final String[] CHILD_COMFORT_2 = {
            "爸爸，我今天看到一只小蝴蝶！它翅膀是蓝色的！",
            "爸爸你回来啦！我跟你说，我今天可乖了！",
            "爸爸，你在外面有没有想我呀？我可想你了！",
            "爸爸爸爸，你看你看，我今天学会了一个新词！",
            "爸爸，我给你留了一颗糖……虽然我自己偷偷吃掉了一半。",
            "爸爸，你说外面的世界是什么样的呀？等我长大也要去看看！",
            "爸爸，我今天在院子里种了一颗种子，它什么时候发芽呀？",
            "爸爸，你蹲下来一点嘛，我要跟你说一个悄悄话！"};

    /** 成年女儿(ADULT)·4 选项(父女向) */
    private static final String[] ADULT_CHAT = {
            "爸爸，今天想聊什么？我都陪您。",
            "您今天看起来有点累，要不要先坐下歇一会儿？",
            "爸爸，家里的事都交给我，您别操心了。",
            "我在想，等天气好了，陪您出去走走，好吗？"};
    private static final String[] ADULT_FLATTER = {
            "爸爸，我给您泡了茶，趁热喝。",
            "今天特意做了您爱吃的菜，尝尝看合不合口味？",
            "爸爸，您别总忙工作了，也要记得好好吃饭呀。",
            "我新学了一道点心，明天做给您尝尝？"};
    private static final String[] ADULT_JOKE = {
            "爸爸，我讲个笑话给您听？……不行，我自己先笑了。",
            "您知道为什么……算了，我自己都讲不下去。",
            "今天我听到一个笑话，讲给爸爸听——您可不许嫌冷哦。",
            "爸爸，您笑点这么高，我可要使出绝招了……"};
    /** 成年女儿·摸头 */
    private static final String[] ADULT_PET_2 = {
            "爸爸，我都这么大了还摸头……不过，不讨厌。",
            "被爸爸摸摸头，总觉得又回到了小时候。",
            "爸爸的手还是这么暖和……再摸一下也没关系。",
            "哼，只有爸爸可以这样摸我的头哦。"};

    // ---- v1.5.102:成年女儿·摸头按关系阶段分池(父女向,不共用) ----

    /** 摸头·warm(初识/还不太熟) */
    private static final String[] ADULT_PET_WARM = {
            "爸、爸爸……突然摸头，我有点没反应过来。",
            "被您摸头……感觉怪怪的，不过好像也不坏。",
            "爸爸，我还不太习惯这样……但如果是您的话，可以再试一次。",
            "您摸头的样子，和我想象中的爸爸一样。"};
    /** 摸头·close(亲近) */
    private static final String[] ADULT_PET_CLOSE = {
            "爸爸又摸我头……嘿嘿，我已经不是小孩子啦。",
            "被爸爸摸头的时候，总觉得特别安心。",
            "爸爸的手好暖，再摸一下也没关系。",
            "明明都这么大了，被爸爸摸头还是会开心。"};
    /** 摸头·dating(黏人撒娇) */
    private static final String[] ADULT_PET_DATING = {
            "爸爸！再摸一下嘛……就一下下。",
            "被爸爸摸头，感觉整个人都轻飘飘的～",
            "爸爸的手是最温柔的，我知道的。",
            "嘻嘻，爸爸摸头的时候，我就像回到了小时候。"};
    /** 摸头·marriage(一家三口/更深的依恋) */
    private static final String[] ADULT_PET_MARRIAGE = {
            "爸爸，被您摸头的时候，总觉得家里特别完整。",
            "就算以后我也有了自己的家，爸爸的摸头也永远是我最怀念的。",
            "爸爸的手掌，是我从小到大最熟悉的安全感。",
            "被爸爸摸头，我永远都不会觉得腻。"};

    // ---- v1.5.102:成年女儿·关系阶段话题分池(父女向,不再回退通用聊天) ----

    /** 关系阶段·warm(初识/还不太熟) */
    private static final String[] ADULT_STAGE_WARM = {
            "爸爸，我刚来的时候，其实很怕您不喜欢我。",
            "和爸爸还不算太熟的时候，我连说话都会小声一点。",
            "现在想想，能叫您一声爸爸，是我最大的幸运。",
            "爸爸，我们会慢慢变得更亲近的，对吧？"};
    /** 关系阶段·close(亲近) */
    private static final String[] ADULT_STAGE_CLOSE = {
            "爸爸，和您在一起的时候，连沉默都觉得很舒服。",
            "我已经把这里当成家了，因为爸爸在。",
            "爸爸，我的事都可以跟您说，对吗？",
            "亲近之后才发现，爸爸比我想象的更温柔。"};
    /** 关系阶段·dating(黏人撒娇,父女向) */
    private static final String[] ADULT_STAGE_DATING = {
            "爸爸，我今天特别想黏着您……可以吗？",
            "只要爸爸在，我就觉得什么都不用怕。",
            "爸爸，您是我最最重要的人哦。",
            "嘿嘿，跟爸爸撒娇，是我最拿手的事。"};
    /** 关系阶段·marriage(一家三口/更深的依恋) */
    private static final String[] ADULT_STAGE_MARRIAGE = {
            "爸爸，有您在，有妈妈在，这个家就是最完整的。",
            "我总在想，能做爸爸的女儿，是我这辈子最好的事。",
            "爸爸，以后的日子，换我来照顾您和妈妈。",
            "我们这个家，是我心里最柔软的地方。"};

    // ---- v1.5.102:摇曲柄 hard/soft 分池 ----

    /** 摇曲柄·hard(冷拒) */
    private static final String[] ADULT_CRANK_HARD = {
            "……爸爸，这个我真的不做。您换个要求吧。",
            "摇曲柄？不行。这次说什么都不行。",
            "爸爸，您这样我可要生气了。",
            "不摇。……您自己玩吧。"};
    /** 摇曲柄·soft(软化/撒娇) */
    private static final String[] ADULT_CRANK_SOFT = {
            "爸爸真是的……好吧，就摇一小会儿哦。",
            "您怎么老惦记这个呀……那、那我只摇给您听。",
            "摇可以，但爸爸要答应我听完这首曲子。",
            "好啦好啦，我摇就是了……您别这么看着我。"};

    // ---- v1.5.101:成年女儿·随便聊聊子选项差异化(生活/心事/休息/时间/依赖/未来/天气/心情/清晨/夜晚/日常) ----

    /** 生活 */
    private static final String[] ADULT_CHAT_LIFE = {
            "爸爸，最近家里一切都好，您别总惦记着。",
            "今天把屋子收拾了一遍，窗台的花也换了水，看着就舒服。",
            "爸爸，院子里的树长高了，像日子一样不知不觉就过去了。",
            "我记着您爱吃的菜谱，慢慢学着，以后都做给您吃。"};
    /** 心事 */
    private static final String[] ADULT_CHAT_HEART = {
            "爸爸，有些话我只想跟您说……虽然说出来有点不好意思。",
            "其实有时候心里闷闷的，但只要跟您说说话，就会好很多。",
            "爸爸，您会觉得我很让您操心吗？……我知道答案，但还是想听您说。",
            "有些心事放在心里很久了，今天想跟爸爸讲一讲。"};
    /** 休息 */
    private static final String[] ADULT_CHAT_REST = {
            "爸爸，忙了一天了，坐下来歇会儿吧，我给您按按肩。",
            "您别硬撑了，今天的活儿都做完了，早点休息好不好？",
            "爸爸，晚上别熬太晚，明天的事明天再说。",
            "我看您眼睛都红了……去睡会儿吧，我守着。"};
    /** 时间 */
    private static final String[] ADULT_CHAT_TIME = {
            "爸爸，时间过得真快，一转眼我就这么大了。",
            "小时候总觉得日子慢，现在却巴不得能多陪您一会儿。",
            "爸爸，以后的时间，我想多分一些给家里、给您。",
            "您总说时间不够用……可陪您的时间，永远都够。"};
    /** 依赖 */
    private static final String[] ADULT_CHAT_DEPEND = {
            "爸爸，有您在，我做什么都觉得踏实。",
            "遇到拿不定主意的事，第一个想到的还是爸爸。",
            "爸爸，就算我长大了，也想一直这样依赖着您。",
            "您是我的底气呀……这话平时不好意思说，今天就说了。"};
    /** 未来 */
    private static final String[] ADULT_CHAT_FUTURE = {
            "爸爸，我在想以后……想一直留在您身边，可以吗？",
            "等我再厉害一点，就换我来照顾您了。",
            "爸爸，您想看到我将来过什么样的日子？我讲给您听。",
            "以后的每一天，都想有爸爸在。"};
    /** 天气 */
    private static final String[] ADULT_CHAT_WEATHER = {
            "爸爸，今天天气真好，出去走走吗？我陪您。",
            "下雨了……爸爸带伞了吗？别淋着。",
            "天气凉了，爸爸记得添件衣裳。",
            "您看这雪，多干净……跟小时候您带我堆雪人那天一样。"};
    /** 心情 */
    private static final String[] ADULT_CHAT_MOOD = {
            "爸爸……今天心情有点沉，您能陪我待一会儿吗？",
            "没遇到什么事，就是突然有点想您了。",
            "爸爸，我今天不太开心……不过看见您就好多了。",
            "让我靠一会儿就好，有爸爸在，什么都不怕。"};
    /** 清晨 */
    private static final String[] ADULT_CHAT_MORNING = {
            "爸爸，早安。今天也请多关照呀。",
            "早上了，爸爸昨晚睡得好吗？",
            "爸爸，早饭我做好了，趁热吃。",
            "新的一天开始了，爸爸也要元气满满哦。"};
    /** 夜晚 */
    private static final String[] ADULT_CHAT_NIGHT = {
            "爸爸，夜深了，早点休息吧。",
            "晚安，爸爸。今天辛苦了。",
            "爸爸，睡前喝杯热牛奶吧，能睡得香。",
            "我在呢，爸爸安心睡吧。"};
    /** 日常 */
    private static final String[] ADULT_CHAT_DAILY = {
            "爸爸，今天有什么新鲜事吗？讲给我听听。",
            "家里的东西我都打理好了，爸爸放心。",
            "爸爸，今天也要记得好好吃饭呀。",
            "跟爸爸待在一起，普普通通的一天也很开心。"};

    /**
     * v1.5.366:女儿对话选项专属文本(心契 HugActionScreen 帧拦截)——按阶段+选项分类返回
     * 专属台词,按天轮换;原版普通女仆文本在女儿身上永不触发。
     * 分类:chat/flatter/joke/crank(成年女儿 hug_menu 4 选项)、pet/lift/carry/comfort
     * (幼儿/少女儿童场景选项)。非女儿或未命中返回 null(走原版)。
     * v1.5.101:成年女儿"随便聊聊"按子选项细分(chat_life/chat_heart/chat_rest/
     * chat_time/chat_depend/chat_future/chat_weather/chat_mood/chat_morning/
     * chat_night/chat_daily/chat_stage)——每个子选项独立文本池,不再共用一句。
     */
    public static String daughterOptionText(MaidMarriageCompat.ChildStage stage, String category, EntityMaid maid) {
        if (stage == null || category == null || maid == null) {
            return null;
        }
        long day = maid.m_9236_().m_46467_() / 24000L;
        switch (stage) {
            case INFANT -> {
                // v1.5.105:婴儿不会说话——儿童场景 4 选项只给旁白/动作描写
                return switch (category) {
                    case "pet" -> pickDaughterLine(INFANT_PET, maid, day);
                    case "lift" -> pickDaughterLine(INFANT_LIFT, maid, day);
                    case "carry" -> pickDaughterLine(INFANT_CARRY, maid, day);
                    case "comfort" -> pickDaughterLine(INFANT_COMFORT, maid, day);
                    default -> null;
                };
            }
            case JUVENILE -> {
                // v1.5.105:幼儿能说简单的句子——奶声奶气短句
                return switch (category) {
                    case "pet" -> pickDaughterLine(JUVENILE_PET, maid, day);
                    case "lift" -> pickDaughterLine(JUVENILE_LIFT, maid, day);
                    case "carry" -> pickDaughterLine(JUVENILE_CARRY, maid, day);
                    case "comfort" -> pickDaughterLine(JUVENILE_COMFORT, maid, day);
                    default -> null;
                };
            }
            case CHILD -> {
                return switch (category) {
                    case "pet" -> pickDaughterLine(CHILD_PET_2, maid, day);
                    case "lift" -> pickDaughterLine(CHILD_LIFT_2, maid, day);
                    case "carry" -> pickDaughterLine(CHILD_CARRY_2, maid, day);
                    case "comfort" -> pickDaughterLine(CHILD_COMFORT_2, maid, day);
                    default -> null;
                };
            }
            case ADULT -> {
                return switch (category) {
                    case "chat" -> pickDaughterLine(ADULT_CHAT, maid, day);
                    case "chat_life" -> pickDaughterLine(ADULT_CHAT_LIFE, maid, day);
                    case "chat_heart" -> pickDaughterLine(ADULT_CHAT_HEART, maid, day);
                    case "chat_rest" -> pickDaughterLine(ADULT_CHAT_REST, maid, day);
                    case "chat_time" -> pickDaughterLine(ADULT_CHAT_TIME, maid, day);
                    case "chat_depend" -> pickDaughterLine(ADULT_CHAT_DEPEND, maid, day);
                    case "chat_future" -> pickDaughterLine(ADULT_CHAT_FUTURE, maid, day);
                    case "chat_weather" -> pickDaughterLine(ADULT_CHAT_WEATHER, maid, day);
                    case "chat_mood" -> pickDaughterLine(ADULT_CHAT_MOOD, maid, day);
                    case "chat_morning" -> pickDaughterLine(ADULT_CHAT_MORNING, maid, day);
                    case "chat_night" -> pickDaughterLine(ADULT_CHAT_NIGHT, maid, day);
                    case "chat_daily" -> pickDaughterLine(ADULT_CHAT_DAILY, maid, day);
                    // v1.5.102:关系阶段话题独立父女向分池,不再回退通用聊天
                    case "stage_warm" -> pickDaughterLine(ADULT_STAGE_WARM, maid, day);
                    case "stage_close" -> pickDaughterLine(ADULT_STAGE_CLOSE, maid, day);
                    case "stage_dating" -> pickDaughterLine(ADULT_STAGE_DATING, maid, day);
                    case "stage_marriage" -> pickDaughterLine(ADULT_STAGE_MARRIAGE, maid, day);
                    case "flatter" -> pickDaughterLine(ADULT_FLATTER, maid, day);
                    case "joke" -> pickDaughterLine(ADULT_JOKE, maid, day);
                    // v1.5.102:摇曲柄 hard/soft 分池
                    case "crank_hard" -> pickDaughterLine(ADULT_CRANK_HARD, maid, day);
                    case "crank_soft" -> pickDaughterLine(ADULT_CRANK_SOFT, maid, day);
                    case "pet" -> pickDaughterLine(ADULT_PET_2, maid, day);
                    // v1.5.102:摸头按关系阶段分池
                    case "pet_warm" -> pickDaughterLine(ADULT_PET_WARM, maid, day);
                    case "pet_close" -> pickDaughterLine(ADULT_PET_CLOSE, maid, day);
                    case "pet_dating" -> pickDaughterLine(ADULT_PET_DATING, maid, day);
                    case "pet_marriage" -> pickDaughterLine(ADULT_PET_MARRIAGE, maid, day);
                    default -> null;
                };
            }
            default -> {
                return null; // 未知阶段兜底
            }
        }
    }

    /** 婴儿旁白替换池(送礼) */
    private static final String[] BABY_GIFT_REPLY = {
            "（她好奇地抱着礼物看了又看，又抬头冲你露出没牙的笑。）",
            "（她抓了抓礼物，又看看你，开心地挥舞着小手。）",
            "（她盯着礼物看了好一会儿，然后咯咯笑着往你怀里蹭。）"};
    /** 婴儿旁白替换池(其他对话/互动) */
    private static final String[] BABY_TALK_REPLY = {
            "（她睁着圆溜溜的眼睛看着你，咿咿呀呀地挥着小手。）",
            "（她歪着头看你，咯咯笑了两声，又低头玩自己的手指。）",
            "（她看着你，小嘴一张一合，发出咿咿呀呀的声音。）"};

    // ---- v1.5.105:幼儿(JUVENILE)简单句子池(非面板交互:送礼/花/摸头/抱抱/复活/思慕) ----

    /** 幼儿·送礼 */
    private static final String[] JUVENILE_GIFT_REPLY = {
            "礼物！给、给我的吗？",
            "哇……谢谢爸爸！",
            "喜欢！喜欢这个！",
            "嘿嘿，我要抱一抱礼物！"};
    /** 幼儿·其他对话/互动(通用) */
    private static final String[] JUVENILE_TALK_REPLY = {
            "爸爸……嗯！",
            "在！我在！",
            "嘻嘻，爸爸叫我！",
            "嗯嗯，我听见啦！"};
    /** 幼儿·摸头 */
    private static final String[] JUVENILE_PET_BUBBLE = {
            "摸摸，舒服！",
            "嘿嘿，再摸！",
            "头，暖暖的！",
            "爸爸手，好大！"};
    /** 幼儿·抱抱(妈妈抱) */
    private static final String[] JUVENILE_CARRY_START = {
            "妈妈，抱抱！",
            "抱！要抱！",
            "妈妈怀里，暖暖！",
            "嘿嘿，抱起来啦！"};
    private static final String[] JUVENILE_CARRY_STOP = {
            "到地上了……",
            "还要抱！",
            "妈妈，再抱一下！",
            "唔……放下来了。"};
    /** 幼儿·拥抱(爸爸) */
    private static final String[] JUVENILE_HUG = {
            "爸爸，抱！",
            "嘿嘿，抱住啦！",
            "爸爸怀里，最暖！",
            "不、不放手！"};
    /** 幼儿·复活 */
    private static final String[] JUVENILE_REVIVE = {
            "爸爸……我回来了！",
            "呜……见到爸爸了！",
            "嘿嘿，没事啦！",
            "爸爸，抱抱！"};
    /** 幼儿·花 */
    private static final String[] JUVENILE_FLOWER = {
            "花！漂亮！",
            "花花，香香！",
            "嘿嘿，送给爸爸？",
            "好看！要戴头上！"};
    /** 幼儿·思慕(想爸爸) */
    private static final String[] JUVENILE_LONGING = {
            "爸爸……什么时候回来呀？",
            "想爸爸了……",
            "爸爸不在，好想他……",
            "爸爸！你回来啦！"};
    /** 幼儿·受伤关心(看到爸爸受伤) */
    private static final String[] JUVENILE_HURT_WATCH = {
            "爸爸！疼！吹吹！",
            "爸爸流血了……呜，别动！",
            "爸爸，痛不痛？",
            "呜……爸爸小心！"};

    /**
     * v1.5.359:婴儿剧本对话替换(心契誓约 speakSingleLine 拦截)——婴儿不会说话,
     * 心契的剧本台词(送礼回复/陪她说话等)全部替换为婴儿旁白(无台词动作描写),
     * 按对话类型(送礼/其他)分池、按天轮换。原剧本文本完全不触发。
     * v1.5.105:幼儿(JUVENILE)不再走旁白——她能说简单句子,由调用方先按
     * childStage 分流到 juvenileDialogueReply,婴儿才走本旁白入口。
     */
    public static String infantDialogueReply(String dialogueKey, EntityMaid maid) {
        String[] pool = (dialogueKey != null && dialogueKey.contains("gift"))
                ? BABY_GIFT_REPLY : BABY_TALK_REPLY;
        long day = maid.m_9236_().m_46467_() / 24000L;
        return pickDaughterLine(pool, maid, day);
    }

    /** v1.5.105:幼儿简单句子入口(非面板交互);婴儿走 infantDialogueReply 旁白 */
    public static String juvenileDialogueReply(String dialogueKey, EntityMaid maid) {
        String[] pool;
        if (dialogueKey != null && dialogueKey.contains("gift")) {
            pool = JUVENILE_GIFT_REPLY;
        } else {
            pool = JUVENILE_TALK_REPLY;
        }
        long day = maid.m_9236_().m_46467_() / 24000L;
        return pickDaughterLine(pool, maid, day);
    }
}
