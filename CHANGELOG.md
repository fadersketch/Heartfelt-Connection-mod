# 更新日志

## v1.0.0(2026-08-17)正式发布

Heartfelt-connection(心契誓约 × 爱憎分明)关系联动补丁首个正式版本。

- 前置:Touhou Little Maid ≥1.5.0 / maidmarriage ≥2.0.0 / callresponse(爱憎分明) ≥2.0.0;
- 软联动:Promaid(更智能的车万女仆)——关系状态写入女仆长期记忆、共享 API 配额,推荐一并安装;
- 功能:关系栏注入、提示词运行时注入、信任/恐惧冻结与好感折算、吃醋绝对压制、告白系统(女仆主动+玩家主动)、纪念日、女儿四阶段线、家庭保护、背叛悔改、特殊奶增强、演绎剧情、调整器测试工具;
- 修复:告白屏文本居中与换行重做、主动告白概率提高、调试面板红字免责声明等。

## 1.5.116(2026-08-17) —— 深审补漏:伤害记录清理

在 1.5.115 基础上对对话核心/配额/命令/战斗层逐行复核,修复遗留小泄漏:

### 修复
- **伤害记录泄漏(1.5.116)**:`PlayerHarmPenaltyManager` 的伤害窗口表
  (harmHits/harmLastTick,实例表)与攻击还原表(LAST_PLAYER_ATTACK/
  LAST_ATTACK_TICK/LAST_TODDLER_HIT,静态表)女仆实体移除后永不清除——
  长会话/多女仆服务器缓慢累积。新增 `EntityLeaveLevelEvent` 处理器,
  女仆卸载/换维度时统一 `forgetMaid(uuid)` 清理;三张实例表改静态
  (类为服务器生命周期单例,等价且便于统一清理)。

### 复核结论(通过,无改动)
- 告白系统(周期扫描/破裂监视/玩家告白):资格门、平方距离修正、威胁检查、
  已宣布关系压制、拒绝幂等、日上限全部正确
- DialogueArbiter:按玩家分区槽位 + 2 秒清扫,无泄漏
- DialogueDispatcher:LLM 就绪降级链(幼儿拦截/promaid 开关/配额满→固定气泡)
- ApiQuotaBridge 门面、HeartfeltCommand 手持调整器守卫(物品即权限)
- SpecialMilkBucketMixin:父类注入 + instanceof 过滤(2.3.0 兼容设计)
- HeartfeltConfig 全部 define 范围与使用一致,无除零/越界
- 遗留(低):FreezeConversion.DAILY_DEDUCT 等纯计数表上界为历史女仆总数,
  量级小,暂不处理

## 1.5.115(2026-08-16) —— 审计修复:调整器动作权限校验

随 Promaid 全面审计联动复查 heartfelt:网络层静态扫描 + 包权限核对 + mixin 配对 + 会话清理核查。

### 修复
- **调整器动作越权(1.5.115)**:`AdjusterActionPacket`(C2S)旧版无权限校验——64 格内
  任意玩家可对任意女仆执行 favor+/mood=/trust+ 等全部调整动作(改好感/心情/信任/恐惧)。
  合法入口 `AdjusterInteractHandler` 要求手持调整器,网络包绕过该校验直接调
  `AdjusterManager.applyAction`。修复:动作前必须手持 `HeartfeltItems.ADJUSTER`(或 OP),
  否则拒绝并提示。

### 审计结论(通过,无改动)
- **mixin 配对**:mixins.heartfelt.json(16 主)+ mixins.heartfelt.opt.json(9 可选)全部
  类文件存在,MANIFEST MixinConfigs 双配置均已打包加载,无死代码/漏注册
- **网络包**:S2C 全部校验方向(PLAY_TO_CLIENT)+ 空 player 防护;C2S 全部以发送者
  64 格范围解析目标女仆(自作用域),无任意目标操作面
- **会话清理**:DialogueFreezeManager 有 PlayerLoggedOutEvent 清理 + 无活动超时自动恢复,
  SESSIONS 无泄漏;告白/对话会话均按玩家/女仆生命周期回收
- **线程模型**:全库无裸线程/无 CompletableFuture/无异步回调,静态容器全为
  ConcurrentHashMap,无跨线程风险;无文件写入删除点
- **遗留(低)**:FreezeConversion.DAILY_DEDUCT 等 UUID 键控表无显式实体卸载清理,
  上界为历史女仆总数,量级小,暂不处理

# Heartfelt-connection 变更日志

## 1.5.114(2026-08-16) —— 全面审计:mixin 签名比对 + 4 处潜伏 bug 修复

用户要求:重新审阅项目,找优化点并解决潜伏 bug。

### 审计(全部通过/已记录)
- **mixin 签名静态比对**:逐一 javap 验证全部 33 个 mixin 的 handler 修饰符与
  目标方法 static 性(Mixin 0.8.5 要求 @Inject/@Redirect handler 与【被注入
  方法】static 一致——1.5.113 的崩溃模式)。除已修的 DialogueBoxCenteredMixin
  外全部匹配:callresponse(Emotion*/Hunger/LoveLoathe 目标全 private static)、
  maidmarriage(HeartPactVoicePlayback.play/MaidWorkManager.resolveWorkBlockReason/
  MaidCarryChildManager 三方法 static;HugDialogueRuntimeBridge.currentFrame 实例)、
  TLM(OpenMaidAIChatMessage.handle private static;hurt 等实例方法实例 handler);
  @WrapMethod(SmartPrompt/ChildGift/MaidWorkSkill/MaidCheckFallback)走
  MixinSquared 注入器,无 static 约束。
- 状态 Map 盘点:各管理器 Map 均按 maid/玩家 UUID 覆写(有界);DialogueArbiter
  有 sweep、DialogueFreezeManager 有登出/保存/停止/超时四重兜底、
  RelationBroadcastManager 有 A7 日切清理——无泄漏。
- PlayerHarmPenaltyManager 每 2 秒全实体扫描:instanceof 为主的微秒级开销,
  且全扫描能覆盖跨重启伤心窗口残留(NBT 里有 HURT_UNTIL 但内存无记录的场景),
  保留不动。

### 修复
- **告白断点残留污染**(v1.5.112 服务端直调的设计缺口):到达后先发 OpenPacket
  客户端 remember 断点,若 handleInteractionToggle 被内部拒绝(不抛异常)或会话
  中断,HugStoryResumeState.pendingResume 残留——玩家下次与该女仆正常对话会被
  误跳进告白剧本。现在:OpenMaidMarriageConfessionPacket 的 maidUuid 允许 null
  (=清断点);服务端 ensureInteraction 后用 isMaidInteracting(getInteractionPlayer
  反射)验证会话真的建立,失败下发清除断点包 + 提示;
- **ReflectUtil.method 缓存键碰撞**:旧键只有"类#方法名",同名重载会拿到错误
  签名的 Method,invoke 参数不匹配抛 IllegalArgumentException(非
  ReflectiveOperationException,catch 接不住,直接崩调用方)。键加入参数类型;
  owner 为 null(类未加载)返回 null 不再 NPE;
- **DialogueFreezeManager 会话中切换目标女仆**:旧版已有会话只刷新时间不更新
  豁免名单(调整器不关直接对另一女仆打开时,新目标仍被冻着)。现在更新豁免
  并把新目标从冻结集合解冻;
- **FamilyMourningManager 停服清空 PENDING**:防跨重启处理陈旧死亡条目。

### 优化
- LongingEffectManager:距离过滤前置(便宜)——旧版 24~48 格女仆每秒先做
  longingActive(反射+任务数据读取)再被距离丢弃,白做昂贵判定。

### 其他
- 根目录 mixins.heartfelt.json/opt.json 陈旧副本(缺 2 个 client mixin)已与
  heartfelt_src(构建实际来源)同步,防后续维护误导。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.113(2026-08-16) —— 修复对话面板居中 mixin 崩溃(告白界面能开了)

崩溃日志:告白界面第一次真正弹出时(maidmarriage HugActionScreen 构造 →
加载 DialogueBoxComponent)→ MixinApplyError 崩溃:
`'static' modifier of handler method does not match target in
DialogueBoxComponent::heartfelt$drawCenteredText`。

### 根因
DialogueBoxCenteredMixin(对话面板正文居中,v1.5.103)的 @Redirect handler
声明为 static,但 Mixin 0.8.5 的 checkTargetModifiers 比较的是【被注入的
目标方法 render】(实例方法)与 handler 的 static 标志——不匹配即抛错。
此前该 mixin 从未被应用过:告白界面一直没能打开,DialogueBoxComponent
从未被加载;1.5.112 服务端直调让告白界面首次真正打开,立即触发。

### 修改
- DialogueBoxCenteredMixin.heartfelt$drawCenteredText 去掉 static(实例方法,
  与被注入方法 render 一致;被重定向的是 invokestatic 调用,参数仍为无
  receiver 的 8 个);
- 已确认其余 @Redirect(CarryToggleSittingMixin 等)目标方法本身是 static,
  无此问题。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;javap 确认编译后
  handler 为 private void(实例方法);已部署。

---

## 1.5.112(2026-08-16) —— 主动告白：互动开启改为服务端直调

用户反馈:"仍然无法触发主动告白"(前摇提示出现、女仆走向,但 maidmarriage
告白对话界面不弹出)。

### 根因
"前摇完成 → 弹告白界面"的最后一环原设计是客户端 `sendHugMaid` 往返:
服务端发 OpenPacket → 客户端 remember 断点 → 客户端再发 C2S 给 maidmarriage
→ 服务端 handleInteractionToggle 开启互动 → 回 S2C 开屏。这条链路的
canSendToServer 检查、两层反射、C2S 时序任一处静默失败,界面就不弹。

### 修改
- ConfessionApproachManager(到达分支):发 OpenPacket(客户端记断点)后,同一
  tick 服务端直调 MaidMarriageCompat.ensureInteraction(反射
  handleInteractionToggle)开启互动——移除客户端 sendHugMaid 往返;同连接包序
  保证 OpenPacket 先到(remember)→ HugStateSyncPayload 后到(开屏消费断点),
  告白前文(confession_intro)不丢;
- 到达后立即停掉女仆移动(clearNavigation + setTarget null)——防 endSession
  恢复原任务后走开,导致 maidmarriage 交互距离门槛(2.25 格)静默拒绝;
- startApproach 遇到残留会话由"静默 return"改为"强制重启"(清旧会话再启动)
  ——残留会话会让「立即触发主动告白」按钮永久无效(连提示都不出);
- 超时/被威胁打断/互动开启失败时发明确系统消息(不再静默,可定位卡点);
- HeartfeltNetwork.jumpToMaidMarriageConfession 只保留 remember 断点(移除
  sendHugMaid 反射),失败时聊天栏提示。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.111(2026-08-16) —— 死亡调侃真死核验(防不死图腾误判)

用户反馈:不死图腾/复活类机制救回时,女仆却判定主人已死并触发调侃(此前
死亡传送机制就有同样误判先例)。

### 修改(FamilyMourningManager)
- 死亡事件内仅【收集候选】(64 格内最近 5 只关系女仆,入口先核验濒死
  isDeadOrDying),不再立即发送;
- 推迟到【下一服务端 tick 结束】再发送——真正的一帧延迟(旧代码
  server.execute 在服务端线程上其实是立即执行,并非延迟),聊天栏稳定接收;
- 发送前【真死核验】:玩家实体未被移除且血量 >0(不死图腾/复活类救回)
  → 不调侃;真死(被击杀移除/血量归零)才发系统消息 + 气泡。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.110(2026-08-15) —— 死亡调侃只取最近 5 只

用户反馈:附近关系女仆全触发会刷屏(妻子/恋人/女儿各自发一条)。

### 修改(FamilyMourningManager.onPlayerDeath)
- 收集 64 格内所有符合条件的关系女仆(isFrozen、非婴儿幼儿),按距离
  (m_20280_ 平方距离)排序,只取【最近 5 只】触发调侃(系统消息 + 气泡);
- 其余女仆不出声——避免"死一次刷 2~3 条甚至更多"。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.109(2026-08-15) —— 全面检验:清理死代码 + 一致性核对

### 清理(1.5.108 删哀悼后的遗留死代码)
- PromptTexts 删除 7 处无调用者的死代码:
  GRIEF_DIALOGUE_DAUGHTER/WIFE、griefFallback、mourningStartMessage/
  mourningStartBubble(哀悼文本)、DAUGHTER_SECTION(旧版女儿准则,已被
  INFANT/JUVENILE/CHILD/ADULT 四段替代)、confessionPrompt、
  playerConfessionDeclined(哀悼婉拒用,哀悼已删)、babyCryLLMPrompt;
- AdjusterScreen 删除「清哀悼 / 设哀悼」两个按钮(服务端 case 已删,按钮
  残留会发无效 action);
- HeartfeltTags.MOURNING_UNTIL 常量保留(旧档残留键无害)。

### 一致性核对(全部通过)
- 关系判定三层语义自洽:isFrozen(妻子/恋人/女儿)、isDedicated(已婚/告白)、
  strictRelationKey(wife/lover/daughter)——各触发器门槛一致;
- isChild 判定与 maidmarriage ChildStateData 语义核对:child=true + father=玩家
  才是女儿,妻子(妈妈)不会误判成女儿;
- 各触发器(广播/家庭互动/思慕/抱起/死亡调侃/告白/成长)用的关系判定统一;
- 模组联动:CallResponseCompat(爱憎分明反射)、PromaidCompat(配额桥)、
  PromaidConfigScreen(heartfelt 联动页 + 立即触发告白按钮)均完好;
- 情感引擎 32 项独立测试全过(删改后回归)。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.108(2026-08-15) —— 修复死亡调侃不触发 + 删除哀悼机制

用户反馈:死亡调侃从未触发过(应该触发);哀悼是调侃之前的设计,已被调侃
替代,无存在意义,直接删(含调整器入口)。

### ① 修复死亡调侃不触发
- 根因:玩家死亡瞬间立即 sendSystemMessage,消息被死亡流程吞掉(死亡界面
  接管聊天),用户从未看到调侃;
- 修复:改 LivingDeathEvent + 延迟 1 tick(server.execute)再发——玩家聊天栏
  稳定接收;婴儿/幼儿(tooSmall)仍跳过(不会说话,大哭由伤害系统处理),
  少女/成女/妻子照常调侃。

### ② 删除哀悼机制(彻底)
- FamilyMourningManager:删 applyMourning / 到期扫描 tick / MOURNING_TICKS,
  只保留死亡调侃;
- 调整器(AdjusterManager):删 "mourn=1d" 与 "clear=mourning" 两个 case 及
  状态栏"哀悼中"标记;
- HeartfeltDebugApi:删 clearMourning(契约注释同步);
- 各读取分支全部移除:FamilyInteractionManager.isMourning(及 5 处调用)、
  LongingEffectManager / PickupResponseManager / SmartIntimateTool /
  MaidConfessionManager 的 MOURNING_UNTIL 判断、AftermathPrompt 哀悼段、
  AffectStateManager.onMourning;
- HeartfeltTags.MOURNING_UNTIL 常量保留(旧档残留键无害,读取分支已删)。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.107(2026-08-15) —— 成长站起气泡加幼儿短句

### 修改
- `growthFallbackToddler`(成长站起瞬间的气泡,ChildGuardManager 直接气泡路径):
  纯旁白「摇摇晃晃地站起来…伸出小手」→ 加一句奶声奶气短句
  「爸爸！站、站起来了！」——幼儿(JUVENILE)会说话,成长瞬间也应开口,
  与四档阶段语义一致(婴儿仍无台词)。

### 明确不动的(与既定意图一致)
- **死亡调侃**:`FamilyMourningManager.onPlayerDeath` 对 isTooSmall(婴儿/幼儿)
  `continue` 跳过——不触发;
- **哀悼**:`applyMourning` 对 isTooSmall 直接 return,且玩家死亡已不触发哀悼
  (1.5.6 改为死亡调侃)——不触发。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.106(2026-08-15) —— LLM 提示词按女儿阶段风格化(婴儿旁白/幼儿短句/少女/成年)

用户要求:大语言模型方面也加强,让模型大致按四档女儿风格来。

### 修改(LLM 提示词 + 降级文本)
- **关系准则段拆分**:原 DAUGHTER_INFANT_SECTION 是 INFANT+JUVENILE 合并
  (都当"奶声奶气会说话")——拆成:
  - DAUGHTER_INFANT_SECTION(婴儿):明确 CANNOT speak,只输出（旁白动作）
    + 至多一个"咿/呀"音节,禁止完整句子;
  - DAUGHTER_JUVENILE_SECTION(幼儿):会说话但只能 2~4 字短句
    (如"爸爸,抱!""高高!飞飞!"),带 STYLE RULE;
  - 少女/成年段补 STYLE RULE(活泼小女孩口吻 / 父女向),与四档一致;
- **父女互动 LLM 提示词**(fatherDaughterPrompt):INFANT 与 JUVENILE 拆开——
  婴儿只给旁白动作,幼儿给短句示例;
- **SmartPromptAppender.relationSection**:JUVENILE 走新段,INFANT 走旁白段;
- **父女互动降级气泡**(fatherDaughterFallback,无 LLM 时):婴儿旁白、
  幼儿"爸爸,抱!"短句,风格与 LLM 对齐。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.105(2026-08-15) —— 女儿阶段语义修正:婴儿旁白/幼儿句子/少女/成年

用户澄清阶段语义(maidmarriage 为 4 阶段 INFANT→JUVENILE→CHILD→ADULT):
- **INFANT 婴儿**:不会说话——只给旁白/动作描写;
- **JUVENILE 幼儿**:能说简单的句子(奶声奶气短句);
- **CHILD 少女**:小女孩口吻;
- **ADULT 成年**:父女向。

### 修正(v1.5.104 把幼儿错做成旁白)
- **对话面板**(HugDialogueDaughterFrameMixin):去掉 INFANT 的 return——婴儿
  也参与替换,显示旁白池;原 JUV_*(旁白)改名 INFANT_* 给婴儿,新增
  JUVENILE_* 简单句子池给幼儿(摸头/举高/抱抱/陪说话);
- **非面板交互**(ChildGiftTextMixin / daughterDialogueText):tooSmall 时也能
  拿到 stage(mixin 传值),婴儿走旁白池、幼儿走简单句子池——送礼/送花/
  摸头/抱抱/复活/思慕/拥抱/受伤关心全部按阶段分流;
- 新增幼儿句子池:送礼/通用/摸头/抱抱开始/抱抱结束/拥抱/复活/花/思慕/
  受伤关心,共 10 组;
- CHILD 少女、ADULT 成年维持原有专属池不动。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.104(2026-08-15) —— 幼儿/少女对话文本加厚(丰富度对齐成年女儿)

用户要求:看看其他阶段(幼儿/少女)的女儿能否同样改进。

### 结构核对(反编译 child_interaction_v1.json 实证)
儿童场景只有 4 个选项节点(pet_head/lift/carry_child/comfort),每个是单个
sequence,【没有】成年女儿那样的子菜单/分支——所以不存在"子选项共用同一句"
的问题,改进点在文本池丰富度:原每池仅 4 条,按天轮换四天就循环。

### 修复
幼儿(JUVENILE,旁白无台词)与少女(CHILD,小女孩口吻)的 4 个选项池
(pet/lift/carry/comfort)各从 4 条扩到 8 条,隔更久才重复:
- 幼儿:新增 4 条旁白(依赖/信赖/哈欠/讨亲亲等动作描写);
- 少女:新增 4 条台词(长高/摇尾巴/够云/悄悄话/留糖等小女孩口吻);
- 幼儿与少女风格保持区分(旁白 vs 台词),不共用。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过;已部署。

---

## 1.5.103(2026-08-15) —— 心契对话面板正文居中(不再偏左)

用户反馈:对话框弹出来的内容仍过于偏左,要求居中。

### 根因
反编译 maidmarriage 的 `DialogueBoxComponent.render` 实证:正文用
`DialogueUiRender.drawWrappedScaledText(...)` 绘制,内部是 `drawWordWrap`,
从 x=20%(固定百分比)起【左对齐换行】,整块贴左。说话人名/正文/提示全部
固定左对齐,没有居中逻辑。

### 修复(DialogueBoxCenteredMixin,客户端)
- @Redirect 拦截 DialogueBoxComponent.render 里的 drawWrappedScaledText 调用;
- 改为:按 maxWidth 用 Font.split 拆行,每行按自身宽度(StringSplitter.width)
  居中计算起点,逐行绘制;
- 参数(组件算好的 textX/textY/wrapWidth/textScale/textColor)原样传入,只把
  "整块左对齐"换成"逐行居中";
- 对全部对话(女儿/普通女仆)统一生效——maidmarriage 原版本就偏左,居中
  对所有女仆都是改善(与 heartfelt 告白屏的居中风格一致);
- @Pseudo + require=0:maidmarriage 可选,未装/版本变动静默跳过。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 33 个通过(新增 1 个);已部署。

---

## 1.5.102(2026-08-15) —— 女儿文本彻底分池:摸头/关系阶段/摇曲柄不再共用

用户要求:女儿不与普通女仆共用文本池,凡女儿共用的地方全部重构。

### 修复(在 1.5.101 基础上继续细分)
上一版把「随便聊聊」子选项分化了,但**摸头、关系阶段话题、摇曲柄**仍在共池/回退:
- 摸头(pet_intro)warm/close/dating/marriage 四分支全部共用 ADULT_PET_2;
- 关系阶段话题(chat_stage)回退通用聊天池(ADULT_CHAT)——女儿会说出
  "和普通女仆一样的句子";
- 摇曲柄 hard/soft 两分支共用同一池。

### 本次
- `categoryOf` 按子分支细分:
  - pet_intro_warm/close/dating/marriage → pet_warm/pet_close/pet_dating/pet_marriage;
  - chat_stage_warm/close/dating/marriage → stage_warm/stage_close/stage_dating/stage_marriage;
  - crank hard/soft → crank_hard/crank_soft;
- PromptTexts 新增 10 组成年女儿专属池(每组 4 条,按天轮换,父女向):
  - 摸头四阶段(初识害羞/亲近安心/黏人撒娇/一家三口依恋);
  - 关系阶段话题四阶段(初识怕被不喜欢/亲近如家/黏人最重要/一家三口完整);
  - 摇曲柄 hard(冷拒)/ soft(软化撒娇);
- 删除不再使用的旧 ADULT_CRANK 池;
- 关系阶段话题不再回退通用聊天——女儿永远说父女向专属文本。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 32 个通过;已部署。

---

## 1.5.101(2026-08-15) —— 女儿「随便聊聊」子选项差异化

用户反馈:女儿对话面板的「随便聊聊」里,生活/心事/休息等子选项最后的回复句都一样。

### 根因
`HugDialogueDaughterFrameMixin.categoryOf` 把所有 `chat_*` 节点归为一个大类
`"chat"`,`daughterOptionText` 对成年女儿统一返回 `ADULT_CHAT` 池——子选项
(生活/心事/休息/时间/依赖/未来)全部复用同一句,毫无区分。

### 修复
- `categoryOf` 按真实节点细分(反编译 hug_menu_v2.json 实证):
  `chat_topic_life_result → chat_life`、`heart → chat_heart`、`rest → chat_rest`、
  `time → chat_time`、`depend → chat_depend`、`future → chat_future`、
  天气(thunder/rain/clear/snow/weather)→ `chat_weather`、心情低落 → `chat_mood`、
  清晨/夜晚/日常/阶段 → `chat_morning`/`chat_night`/`chat_daily`/`chat_stage`;
- PromptTexts 新增 11 组成年女儿子选项独立文本池(每组 4 条,按天轮换):
  生活/心事/休息/时间/依赖/未来/天气/心情/清晨/夜晚/日常——每个子选项
  回复句不再相同,且保持父女向口吻;
- `chat_stage`(关系阶段话题)暂回退通用聊天池,后续可再补专属池。

### 验证
- heartfelt 编译 0 错误;heartfelt_connection-1.5.94.jar 打包,mixin 校验 32 个
  通过;已部署。

---

## 1.5.100(2026-08-15) —— 手册「立即触发主动告白」调试按钮

Promaid 手册的 heartfelt 联动页新增「立即触发主动告白」按钮——跳过概率与冷却,
直接触发告白前摇,方便验证告白流程。

### heartfelt 侧
- `MaidConfessionManager.forceConfession(ServerPlayer)`:找玩家 48 格内好感最高的
  【资格女仆】(复用 isEligible:好感≥触发线/未告白/未婚/非女儿/未失败),跳过
  概率/冷却直接 `startApproach`(系统提示 → 走向 → 到身边弹告白);已宣布关系
  (妻子/恋人)时拒绝,与周期扫描同语义;结果文案系统消息反馈。
- 新增 C2S 包 `ForceConfessionPacket`(无字段):服务端处理 → forceConfession →
  系统消息反馈结果。

### Promaid 侧
- heartfelt 联动页告白分区加「立即触发主动告白」按钮,点击反射
  `HeartfeltNetwork.channel().sendToServer(new ForceConfessionPacket())`
  (零硬依赖,与现有软联动同模式)。

### 验证
- 两模组编译 0 错误;heartfelt jar mixin 校验 32 个通过、promaid jar 233 个类
  全部通过;已部署。

---

## 1.5.99(2026-08-15) —— 修复主动告白卡掉(提示出现但对话框不弹)

用户反馈:女仆主动告白,系统提示("她似乎有什么想说的")出现了,但没进入告白对话框。

### 根因(逐环反编译核对)
告白前摇到达后,heartfelt 发跳转包 → 客户端反射 sendHugMaid → maidmarriage 的
`MaidHugManager.handleInteractionToggle` 打开互动屏。反编译确认该入口有两道
静默拦截,恰好卡住这个场景:

1. **距离门槛不一致(主因)**:heartfelt 前摇"到达"判定用 `√(m_20280_) <= 2.5`
   (线性 2.5 格);maidmarriage 的 handleInteractionToggle 要求
   `m_20280_() <= 5.0625`(2.25 格平方),超过即静默返回(不发任何提示)。
   → 女仆停在 2.25~2.5 格之间时:heartfelt 判"已到身边"→ 发包 → maidmarriage
   判"太远"→ 拒绝,对话框不弹。
2. **残留互动会话**:handleInteractionToggle 第一行"玩家已有会话 → 当作开关
   关闭并返回"。玩家之前 Alt+J 面板没关干净 / 与其他女仆互动中 → 告白界面
   被当成"再次按键=关闭",同样不弹。

### 修复(ConfessionApproachManager)
- 到达判定距离 clamp 到 2.0 格(maidmarriage 门槛 2.25 格内,留 0.25 余量),
  配置默认值同步 2.5 → 2.0(上限 2.0);
- 到达发包前先调用 maidmarriage 公共 API `forceStopInteraction`(hugStop 反射),
  清掉玩家残留会话,确保 handleInteractionToggle 走"无会话 → 新建"分支。
- 客户端跳转链路(HugStoryResumeState.remember / HugMaidPayload /
  ModNetworking.sendHugMaid)逐一反编译核对,签名全部正确,无需改动。

### 验证
- heartfelt 编译 0 错误;heartfelt_connection-1.5.94.jar 打包,mixin 校验 32 个
  通过;已部署。

---

## 1.5.98(2026-08-15) —— 情感引擎(连续情绪快照,借鉴 MaidSoulCore AffectEngine)

在现有机制之上【增量叠加】一层连续情感状态,让 LLM 对话语气贴合"她此刻
的情绪",而不是每次只看当下好感。不改动 maidmarriage 确认关系/TLM 好感/
心情/伤心窗口/哀悼/思慕任何现有逻辑。

### 新增 affect 包(移植自 MaidSoulCore,精简)
- **AffectProfile**:多维连续状态(valence/arousal/dominance 三维 +
  intimacy/conflict/hurtDebt/repairDebt/longing,全 0~1)+ 连续正向事件计数
  + 主导情绪(EmotionLabel)+ 情感阶段(RelationshipStage);
- **AffectEngine**:事件 → 多维脉冲表(被打冲突+0.24/受伤债+0.22、喂食亲密+0.11、
  亲密互动、告白成功亲密+0.22、告白失败低落、破裂冲突+0.3/受伤债+0.3、
  哀悼思慕+0.08 等)+ 安静恢复(周期向阶段基线回退,债消退/思慕升高);
- **RelationshipHmm**:事件 + 连续情感 → 情感阶段推导(初识/甜蜜/热烈/稳定/
  冷淡/修复中,与 maidmarriage 确认关系正交);
- **AffectStateManager**:NBT 读写(heartfelt_affect 子标签,收魂符/跨维度
  实体重建不丢)+ 每 5 秒安静恢复 + 事件入口。

### 事件接入(现有触发点喂脉冲,一行调用)
- 玩家攻击女仆(近战/远程,EntityMaidHurtRecordMixin 路径)→ 冲突/受伤债↑;
- 喂蛋糕(EventHistoryManager)→ 亲密↑;
- 亲密互动成功(拥抱/亲吻/膝枕/摸头,SmartIntimateTool)→ 亲密↑;
- 告白成功(玩家/女仆主动两路)→ 亲密大涨;
- 告白被拒 → 低落 + 修复债;
- 关系破裂 → 冲突/受伤债大涨;
- 哀悼(主人死亡)→ 悲伤 + 思慕。

### prompt 注入
SmartPromptAppender 追加【当前情绪】段:情绪 + 情感阶段 + 亲密/冲突/受伤/
修复/思念百分比 + 表达建议(如"她仍在修复关系,语气应温柔但谨慎")——
LLM 每次对话感知连续情绪,而不是只看当下好感档位。

### 验证
- heartfelt 编译 0 错误(91 个源文件);jar 打包,mixin 校验 32 个通过;
  affect 包 8 个类 + 7 个接入类均在 jar;已部署。

---

## 1.5.97(2026-08-15) —— Runtime 生命周期:强制状态跨实体保留(收魂符不再丢)

借鉴 MaidSoulCore `MaidSoulRuntimeFactory` 的"实体重建状态兜底"思路,修复
heartfelt 里两个"实体重建后功能丢失"的内存状态。

### ① 拾取/声音【原值】落 NBT(核心)
- `PICKUP_FORCED`(婴儿被强制 PickType.ONLY_XP 前的原拾取类型)从内存 Map
  改为写女仆 NBT `heartfelt_pickup_orig`;
- `SOUND_SILENCED`(幼儿/婴儿/伤心窗口被静音前的原声音频率)改为写 NBT
  `heartfelt_sound_orig`;
- 原 bug:原值只存内存 Map(按实体 UUID)。收魂符/跨维度/卸载重载后实体
  重建、UUID 变 → 新实体找不到旧条目 → 长大时恢复不了原类型/原频率
  (拾取永久 ONLY_XP、声音永久静音),旧条目还泄漏。
- 写 NBT 后魂符带实体走:放出来原值还在,长大即还原,键自动清除。

### ② 收魂符时清理瞬态会话
`ChildGuardManager.onMaidToItem`(妈妈被收进魂符)统一调用:
- `ConfessionApproachManager.purgeMaid`(清告白前摇会话——旧 UUID 条目
  会让新实体无法发起新的前摇);
- `PickupResponseManager.purgeMaid`(清抱起回应冷却)。
对应 MaidSoulCore 的 `invalidate()`:实体转物品时清掉按旧 UUID 的运行时状态。

### 有意不做
- 每女仆的 per-day 去重 Map(家庭互动/广播)保留内存——它们按游戏日 clear,
  实体重建后旧条目只是当天残留,不影响正确性,不值得为它们加 NBT。

### 验证
- heartfelt 编译 0 错误;jar 打包,mixin 校验 32 个通过;DialogueArbiter/
  ConfessionApproachManager/PickupResponseManager/ChildGuardManager 类均在 jar;
  已部署。

---

## 1.5.96(2026-08-15) —— 主动说话仲裁器(第一期)

借鉴 MaidSoulCore 的 SpeechArbiter 思路,解决"多个关系女仆同时抢着冒气泡"。

### 新增 DialogueArbiter(主动说话仲裁)
- 每个玩家同时只让一只女仆"主动开口"(按 owner UUID 分区,多人各自独立);
- 通道分级:AMBIENT(思慕气泡等日常) < INTIMATE(关系互动) < PLAYER_REPLY(回应玩家动作);
- 判定:独占/续期 → SPEAK;更高通道且优先级更高 → INTERRUPT;否则 WAIT(让位,下轮重试);
- 优先级 = 距离 0.3 + 关系 0.4(妻子>女儿>恋人>深爱>普通)+ 相识 0.3(越久越优先)。

### 接入范围(第一期,只挂"周期性主动气泡",零回归)
- **思慕气泡**(LongingEffectManager)→ AMBIENT:每 30 秒触发、多女仆时最容易同时冒泡,
  被让位时本轮不发,下轮冷却自然重试,不丢话;
- **女儿任务汇报**(ChildActionReportManager)→ INTIMATE:学习/探险归来汇报。
- **有意不做**:广播/家庭互动等【一次性】互动不上仲裁——它们本就每日去重、
  按场景错开;强行排他反而会吞掉"所有女仆一起庆祝"的设计(让位后当日标记
  已置、不再重试)。这避免了把"谁先开口"变成"只有一只女仆能开口"。

### 验证
- heartfelt 编译 0 错误;heartfelt_connection-1.5.94.jar 打包,mixin 校验 32 个通过;已部署。

---

## 1.5.95(2026-08-15) —— 女儿对话选项文本实际生效 + 思慕距离修复

反编译 maidmarriage 2.3.0 剧本 JSON 逐节点核对后修复三处：

### ① 女儿对话选项专属文本从未生效(核心)
`HugDialogueDaughterFrameMixin` 旧版用 `speaker.equals(女仆名)` 判断"女仆台词"，
但 `HugDialogueRuntimeBridge.currentFrame()` 返回的 `speaker` 是【未渲染占位符】
——`renderTemplate` 要到 `HugActionScreen.refreshDialogueState` 才执行，此刻
`frame.speaker()` 仍是字面量 `"${maid}"`(儿童 pet/lift/carry/comfort 的 L1、
成人早安吻、joke 反应等)，或 speaker 为空 + text=`"${chat_topic_life_text}"`
这类【池文本模板】(成人 chat_*_result / flatter_*_result / crank_*_result 的
女仆台词在 hug_menu_v4 池里)。旧版与女仆名比较永远不匹配 → 女儿专属文本
(1.5.366 那套)其实一行都没触发。
- 改为:choiceNode 帧跳过(菜单提示不是台词);`${player}` 跳过;`${maid}` 或
  `speaker 空且 text 以 ${ 开头` 视为女仆台词,才替换。
- 顺带修正:`flatter_praise_result` 等池文本节点此前被 `startsWith("flatter")`
  误归类也能替换,但旧版因 speaker 匹配失败从未走到——本次一并打通。

### ② 幼儿/少女 carry 文本口吻修正
`child_interaction_v1.json` 实证:carry_intro 是"她的**妈妈**轻轻靠近,把她稳稳
抱在怀里"——carry 是妈妈抱,不是爸爸抱。JUV_CARRY / CHILD_CARRY_2 保持"妈妈
抱"视角正确;仅统一为第三人称旁白口吻(与 JUV_LIFT 的"你"视角区分开)。

### ③ 思慕效果距离判断平方距离修复
`LongingEffectManager` 旧版 `m_20280_() > 24.0` 把平方距离当线性距离——实际
约 4.9 格外就判"太远",思慕心形粒子几乎只在贴身触发。改为与 24² 比较。

### 验证
- heartfelt 编译 0 错误(仅 13 个既有 deprecation 警告);heartfelt_connection-1.5.94.jar 打包,
  mixin class 校验 32 个全通过;部署到 mods 目录。

---

## 1.5.68(2026-08-14) —— 婴儿幼儿系统消息全面盘点

用户要求：婴儿和幼儿除了哇哇大哭以外的系统消息都不太应该触发，符合身份。

### 盘点（全部 26 个玩家系统消息调用点）
**不应触发（本次修复）**：
- **死亡调侃**（玩家死亡时关系女仆按档调侃/关心）——婴儿/幼儿不会说话、不理解死亡；
- **哀悼**（悲伤窗口，拒绝亲密/暂停互动）——婴儿/幼儿不哀悼。

**保留（符合婴儿/幼儿身份）**：
- 哇哇大哭（被打）✓ / 年龄太小不能做任务 ✓ / 武器拿不住 ✓ /
  等妈妈·妈妈回来（旁白）✓ / 成长"又长大了一点"（旁白式）✓。

**此前已跳过/本就不触发**：父女互动·母女互动·纪念日·广播·暗恋·补位说话
（v1.5.31 chatWithQuota 幼儿跳过）、告白/主动告白（婴儿不告白）、伤心惩罚
（婴儿不进伤心）、思慕（已父女向旁白）、特殊奶（婴儿不产奶）、悔改（婴儿不背叛）。

### 实现（FamilyMourningManager）
- `onPlayerDeath` 死亡调侃：`isTooSmall` → continue；
- `applyMourning` 哀悼：`isTooSmall` → return（婴儿不设哀悼窗口）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.68.jar 打包安装两个 mods 目录；
- 游戏内：玩家死亡 → 婴儿/幼儿不再调侃；调整器对婴儿设哀悼 → 不生效（无窗口）；
  少女/成女/妻子的调侃与哀悼照常。

---

## 1.5.67(2026-08-14) —— 修复幼儿被打好感度惩罚丢失 + 反馈可见化

用户反馈：打小女仆似乎不会触发减好感度的事件。

### 排查结论
- maidmarriage 儿童好感就是 TLM favorability（实证：`favorRate`/`checkFavorabilityGate`
  都读 `getFavorability`）——好感 API 正确；
- 薄弱点在 hurt 入口的 `EntityMaidHurtRecordMixin`——**只查 directEntity
  （m_7640_）**：远程弓/弩在 hurt 入口记录不上，一旦 LivingHurtEvent 链被
  其他 mod 取消/时序打断，幼儿好感度惩罚就丢失。

### 修复
1. **hurt 入口补查 getEntity（m_7639_，远程造成者）**——近战/远程/重放场景
   都记录玩家攻击 → `recordPlayerAttack → onToddlerHit` 好感 -1 双保险；
2. **大哭消息带明确反馈**——主人来源扣好感时消息显示「好感度 -1」，
   不再困惑"减没减"（若好感度已 0 封底，消息不带 -1）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.67.jar 打包安装两个 mods 目录；
- 游戏内：近战/远程打幼儿女儿 → 大哭消息 + 「好感度 -1」+ 气泡；
  好感度 0 时只哭不减。

---

## 1.5.66(2026-08-14) —— 调整器解除关系时清压制标记

用户反馈：调整器里结婚后再解除，其他女仆仍受结婚关系影响——生存模式可接受，
但创造模式调整器应该能彻底解掉。

### 根因
- `clearRelations`（调整器 rel=none）解除了 maidmarriage 的婚姻/告白/女儿，
  但**没清除玩家 ForgeData 的 `heartfelt_dedicated` 标记**（确认过关系即
  吃醋隔离/告白压制）——标记残留，其他女仆继续受已解除关系的影响。

### 修复（AdjusterManager.clearRelations）
- 解除关系后调用 `RelationshipExemption.clearDedicatedIfNone(owner)`——
  该玩家**已无其他确认关系女仆**时清除标记（与 v1.5.19 破裂语义一致）；
  还有其他关系女仆（如恋人）则保留压制。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.66.jar 打包安装两个 mods 目录；
- 游戏内：调整器设妻子 → 解除关系 → 其他女仆恢复吃醋/告白（无其他关系时）；
  同时有恋人时解除妻子 → 压制保留。

---

## 1.5.65(2026-08-14) —— 广播女儿事件跳过妈妈

用户反馈：如果是妻子，系统提示应该删掉——"（xx好奇地望着那个孩子）：这就是
丈夫的女儿吗？真可爱"——妈妈是孩子的亲生母亲，不会好奇自己的孩子。

### 根因
- 关系广播（"主人有女儿"）遍历玩家名下**所有其他女仆**——妻子（妈妈）也在其中，
  播出了这段"外人口吻"的文本。

### 修复（RelationBroadcastManager.broadcastToOtherMaids）
- 先解析关系女仆（女儿）的妈妈 UUID（readMotherUuid）；
- 遍历时**跳过孩子的妈妈**——她不需要"得知"自己的孩子，也不会说
  "这就是主人的女儿吗"；
- 其他女仆的广播文本不受影响。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.65.jar 打包安装两个 mods 目录；
- 游戏内：女儿诞生/认女儿广播 → 妻子（妈妈）不播"好奇地望着"文本；
  其他女仆照常祝福/感慨。

---

## 1.5.64(2026-08-14) —— 婴儿幼儿语音彻底禁 + 幼儿可行走

用户反馈：婴儿和幼儿仍然可以播放语音包，应把 TLM 声音频率调成 0；幼儿可以行走
但婴儿不行，其他无区别，两者都不能播放语音包。

### ① 语音彻底禁（补最后缺口）
- 反编译实证：TLM 女仆**声音频率**（global_maid_sound_frequency）驱动的自主
  讲话声（ambient 声音包语音）走 `EntityMaid.getAmbientSound`——不经 AI 聊天
  tts（v1.5.2/1.5.21 已拦）也不经 maidmarriage 心契语音（v1.5.24 已拦）；
- 新增 `MaidAmbientVoiceMixin`：拦 `getAmbientSound` 返回 null（原版
  playAmbientSound 对 null 有安全检查，不播放——**等效声音频率 = 0**）
  + 拦 `tryPlayMaidPickupSound`（捡拾声）——婴儿/幼儿完全不发声包语音，
  文本对话/气泡不受影响。

### ② 行走区分（isInfant 拆分）
- 新增 `ChildGuardManager.isInfant`（仅 INFANT）；
- 强制坐下**只对婴儿（INFANT）**——幼儿（JUVENILE）可以行走；
- 任务锁空闲 / 手持限食物 / 禁语音与婴儿一致（`isTooSmall` 不变，两者相同）；
- 被妈妈抱着时维持原版抱起（不变）。

### 验证
- heartfelt 编译 0 错误（31 mixin）；heartfelt_connection-1.5.64.jar 打包安装
  两个 mods 目录；
- 游戏内：婴儿坐着不动、无任何语音包；幼儿可站立行走、同样无语音包；
  两者任务锁空闲、手持限食物照常。

---

## 1.5.63(2026-08-14) —— 修复幼女丢武器无限循环

用户反馈：要求女儿丢武器，结果她一直丢、丢到地上手中仍握武器、武器无限刷新增多。

### 根因（反编译实证）
- 原版 `spawnAtLocation`（m_19983_）**只生成物品实体，不扣减、不清手持**；
- TLM `MaidBrain` 的捡拾 AI 会把丢出的武器**自己捡回**（`pickupItem` 尊重拾取延迟
  hasPickUpDelay，但旧版没设延迟）——**丢→捡→再丢**循环；
- 每次用**同一 ItemStack 引用**生成实体（不复制）——地上武器无限增多。

### 修复（stripHand）
1. **先清空手持**（m_21008_），再丢**副本**（m_41777_ 复制）——实体与手持彻底脱钩；
2. 实体**归属玩家**（m_32052_=setOwner）——玩家碰触会清拾取延迟，正常捡起；
3. 实体**无限拾取延迟**（m_32010_=setPickUpDelay 1000000）——女仆 AI 尊重延迟
   **永不捡回**，循环断开；玩家不捡则 5 分钟后按原版物品规则消失。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.63.jar 打包安装两个 mods 目录；
- 游戏内：幼女手中塞武器 → 丢出 1 个实体（玩家可捡），她不再捡回、不再循环；
  反复塞同一武器 → 只丢出 1 个，无新增。

---

## 1.5.62(2026-08-14) —— 修复女儿纪念日基准仍按告白日计算

用户反馈：对女儿点击"首见"，弹出来的仍然是告白类型的纪念日。

### 根因
- v1.5.48 只把女儿纪念日的**文本**改成父女向，但 `milestoneDue` 的**天数基准**
  仍是"告白 > 首见"——**对女儿也一样**；
- 女儿的 `CONFESSION_AT` 若有残留（如调整器测试时点过"告白=今天"），点"首见"
  后纪念日仍按**告白日**计算——弹出的天数/事件就是"告白类型"的。

### 修复（milestoneDue 女儿分支）
- 女儿纪念日基准**只用出生/初见**（EVENT_FIRST_MEET），彻底无视任何残留告白日；
- 非女儿维持原逻辑（告白 > 首见）；
- 调整器纪念日按钮（首见=今天/首见7天前/时间+N天/纪念日重置）对女儿同样有效。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.62.jar 打包安装两个 mods 目录；
- 游戏内：女儿残留告白时间戳时，点"首见=今天"→ 纪念日按首见日计算
  （"与你的出生/初见已经第 N 天"）；点"首见7天前"→ 立即触发 7 天里程碑。

---

## 1.5.61(2026-08-14) —— 调整器状态行居中

用户反馈：调整器最上面的字段仍然偏左，应移至正中间。

### 实现（AdjusterScreen）
- 标题本就居中于面板中心（v1.4.8 起）；
- **状态行**（好感/心情/信任/恐惧、关系/阶段/奶/哀悼/伤心/背叛标记）原从面板
  左缘 +12px 左对齐绘制——改为与标题一致**居中于面板中心**；
- **操作结果行**（黄色反馈）同步居中；
- 截断逻辑保留（超长仍省略号防溢出面板右缘）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.61.jar 打包安装两个 mods 目录；
- 游戏内：调整器顶部标题、状态行、操作结果均居中于面板中心。

---

## 1.5.60(2026-08-14) —— 坐姿女仆 Alt+J 辅助瞄准扩展到所有女仆

用户反馈：Alt+J 还是不能对着坐下的女仆使用。

### 根因（反编译实证）
- TLM `maidCheck` 只认每帧准星命中（`Minecraft.f_91077_` 为 EntityHitResult）——
  坐着的女仆（幼儿实体小/坐姿姿态）准星射线极易穿过她瞄到后方，hitResult 变
  BlockHitResult → maidCheck 返回 null → Alt+J 无反应（"瞄不准"而非"不让进"）；
- v1.5.37 的兜底**只认 maidmarriage 女儿实体（MaidChildEntity）**——普通女仆/
  成女女儿坐着（等妈妈/强制坐/手动坐）同样瞄不准，却无兜底。

### 修复（MaidCheckFallbackMixin）
- 兜底从"仅女儿实体"扩展到**所有属于该玩家的女仆**——坐姿/小体型一律兜底；
- 视线锥角 32°→41°（cos 0.85→0.75）——坐下的小女仆在玩家斜下方/脚下时
  低头视角也能命中；
- 潜行限制保留（TLM 原版潜行时 Alt+J 无效）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.60.jar 打包安装两个 mods 目录；
- 游戏内：普通女仆/成女女儿/幼儿女儿坐着（等妈妈、强制坐、手动坐下）→
  Alt+J 打开会话面板；站着精确瞄准照常；潜行时仍无效。

---

## 1.5.59(2026-08-14) —— 调整器成长阶段改 4 档

用户反馈：成长体系一直是三阶段——从婴儿到少女的跨度有、到幼女的跨度没有；调试器同样只有三阶段。

### 实证
- maidmarriage 官方 `GrowthStage` 枚举即 **4 档**（INFANT 婴儿 / JUVENILE 幼儿 /
  CHILD 少女 / ADULT 成年）；
- 成长事件本就按 4 档推进（每次阶段变化都触发）——原调整器只有**幼年/少女/成年 3 档**，
  且"幼年"实际设的是 **INFANT（婴儿）档**（无 JUVENILE 幼儿档）——"婴儿→幼儿"的
  成长跨越**无法测试**，这就是"没有到幼女的跨度"的根因。

### 实现
- **调整器阶段按钮 4 档**：婴儿（INFANT）/ 幼儿（JUVENILE）/ 少女（CHILD）/ 成年（ADULT），
  "奶=3" 按钮挪到清背叛行（阶段行 4 个按钮正好占满）；
- **状态行阶段显示 4 档分开**（原 INFANT/JUVENILE 合并显示"幼年"）；
- 补上幼儿档后**三条成长跨越都可测试**：
  婴儿→幼儿 播"又长大了一点"+ 旁白气泡 / 幼儿→少女 播"会自己站起来了"+ 真正站起 /
  少女→成年 播"长大成人了"。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.59.jar 打包安装两个 mods 目录；
- 游戏内：调整器第 1 页阶段行显示 婴儿/幼儿/少女/成年 四按钮；设婴儿→设幼儿
  触发成长事件（又长大了一点）；状态行阶段显示 婴儿/幼儿/少女/成年 各自独立。

---

## 1.5.58(2026-08-14) —— 心契对话界面文本池扩充（+262 条）

用户反馈：心契对话界面（HugActionScreen）的文本池复用严重、各身份差别不大，
希望增加更多文本。

### 实证
- 盘点 maidmarriage 2.3.0 五个对话文本池：话题池（hug_chat_topics）**每档仅 1 条**、
  普通池（hug_menu_v4）entry 每档 2 条、儿童池（child_family）2-3 条——复用严重；
- 加载源确认：`HugDialogueTextPools` 经 `ResourceManager`（游戏资源系统）加载
  `maidmarriage:dialogue/pools/<lang>/*.json`——heartfelt 提供 maidmarriage
  命名空间同名资源即可覆盖（加载顺序在后，不改原 jar）。

### 实现（五个池 +262 条原创文本）
| 池 | 扩充 |
|---|---|
| hug_menu_v4（普通池 entry） | 25 档（5 关系阶段×5 心情）每档 2→6 条——陌生/熟络/暧昧/恋人/妻子**五档口吻层层递进**（initial 规矩疏离→marriage 老夫老妻自然亲昵） |
| hug_chat_topics（聊天话题） | life/heart/rest 3 主题×5 阶段每档 1→4 条 |
| adult_child_family（成女女儿） | 全类 8→14 条——回家迎接/摸头嘴硬/抱抱奖励/关心爸爸作息 |
| child_family（儿童） | 全类 2-3→7-8 条——幼儿旁白与少女台词分层 |
| child_family_topics（儿童话题） | 4 时段各 1→4 条 |

- 文本保持原版格式（台词+旁白两行式、{player}/{maid}/${player_child} 变量一致）；
- 覆盖仅在资源加载层，原版文本完整保留（原版条目 + 新增条目共存，随机/轮换选取）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.58.jar 打包安装两个 mods 目录；
- jar 内 assets/maidmarriage/dialogue/pools/zh_cn/ 五个池文件在位，条数对比
  98→198 / 115→160 / 80→140 / 20→65 / 4→16；
- 游戏内：对话界面进入/聊天话题/女儿互动文本明显更丰富，各关系阶段口吻差异清晰。

---

## 1.5.57(2026-08-14) —— 女儿思慕文本父女化

盘点心契誓约对话类文本时发现：`longing` 思慕触发链**无女儿检查**——女儿 3 天没互动
会触发恋爱向思慕台词（"好想被你抱着""靠近一点嘛"），完全出戏。

### 实现
- 拦截 `dialogue.maidmarriage.longing*` 全部键（dating/marriage/low_mood/loop/
  longing_night/longing_saddle/longing_wait）——女儿改用**父女向思慕池**（按天轮换）：
  - 幼女：旁白（"（爸爸不在身边，她坐在原地，眼巴巴地望着爸爸离开的方向。）"）；
  - 少女："爸爸，你今天都没有怎么陪陪我……" / "爸爸什么时候回来呀……我一直在等爸爸呢。"；
  - 成女："您不在的时候，家里总觉得空落落的。" / "爸爸，回来了？饭还温着，先去洗手吧。"

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.57.jar 打包安装两个 mods 目录；
- 游戏内：女儿 3 天不互动 → 思慕触发显示"想爸爸"文本；妻子/恋人/普通女仆
  思慕照常原版恋爱向。

---

## 1.5.56(2026-08-14) —— 女儿文本拦截点移到真正汇聚点（D 界面调查副产品）

D（互动界面父女化）调查的结论 + 收尾改进。

### 调查结论
- `ui.maidmarriage.hug_action.idle_line_1-4`（互动界面恋爱向独白键）在 maidmarriage
  代码中**无任何引用**——是遗留/外部脚本才用的键（`ui.maidmarriage.hug_action.`
  只出现在 DEFAULT_INCLUDED_PREFIXES 示例脚本生成分类里）；
- maidmarriage 儿童互动界面（childInteraction 模式）**自带女儿隔离**（用户确认）；
- 界面文本走 maidmarriage 对话脚本系统（外部脚本/服务端下发），客户端 UI 层替换
  成本高、收益不确定——**界面层不做改动**。

### 实现（收尾改进）
- 反编译实证：`RomanceSleepManager.scriptForMaid` 只是
  `DialogueScriptManager.componentForMaid` 的一行薄封装，而摸头（PetHeadManager）/
  抱放（MaidCarryChildManager）/拥抱（MaidHugManager）/送礼（GiftManager）/
  学习（MaidWorkManager）/儿童互动（ChildInteractionManager）等**全部**直接或
  间接调用 `componentForMaid`——它才是唯一真正的文本汇聚点；
- `ChildGiftTextMixin` 拦截点从 `scriptForMaid` 移到 `componentForMaid`——
  一次全覆盖所有调用者（旧版会漏掉直接调用者），分流逻辑不变
  （普通女仆原版 / 幼女旁白 / 少女口吻 / 成女父女向，按天轮换）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.56.jar 打包安装两个 mods 目录；
- 游戏内：女儿摸头/拥抱/送礼等全部交互文本照常按阶段显示（拦截点迁移无感知变化）。

---

## 1.5.55(2026-08-14) —— LLM 防乱伦三层防线

用户要求：大语言模型防止乱伦很有必要——心契誓约自己的对话面板有女儿隔离，
但 LLM 侧（heartfelt 注入提示词 + promaid 记忆上下文 + LLM 亲密工具）没有。

### 实现（三层防线）
- **行为层**（最硬）：`SmartIntimateTool`（LLM 亲密工具 hug/kiss/lap/pet）加女儿检查——
  女儿（任意阶段）执行 kiss 亲吻/lap 膝枕（恋人向动作）→ 拒绝并返回
  "她是你的女儿——不能对她做亲吻/膝枕这类恋人间的动作"；幼女禁全部亲密动作
  （摸头除外，已有婴儿旁白气泡）；
- **heartfelt 提示词层**：四段女儿准则（通用 DAUGHTER_SECTION / 幼儿 INFANT /
  少女 CHILD / 成年 ADULT）各追加 **RELATIONSHIP GUARD 铁律**——对爸爸
  严禁任何恋爱/亲密/暧昧言行（no kissing, no dating, no lovers' talk, no romantic
  feelings），遇到恋爱话题困惑拒绝——成女段特别强调"即使已经长大"；
- **promaid 提示词层（1.0.6）**：`AiMemoryContext` 女儿记忆标签在称呼铁律后
  追加防乱伦铁律——"我是他的女儿，对他只有亲情——严禁任何恋爱、亲密、暧昧的
  言行与情感，即使已经长大也永远是父女关系，遇到恋爱或亲密话题要困惑地拒绝"。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.55.jar + Promaid-1.0.6.jar
  打包安装两个 mods 目录；
- 游戏内：LLM 对女儿调用 kiss/lap 工具 → 拒绝结果转述；女儿 AI 对话被引导
  恋爱话题 → 困惑拒绝；妻子/恋人/普通女仆的亲密工具不受影响。

---

## 1.5.54(2026-08-14) —— 学习归来文本去"老师"

用户反馈：游戏里没有老师这个说法。

### 实现
- 少女学习归来汇报两处文本改贴 maidmarriage 学习任务语境
  （附魔学/药剂学/战术学——消耗书本/玻璃瓶/武器自学）：
  "老师讲的东西，我记住了好多好多！" → "书上的东西，我记住了好多好多！"；
  "今天的功课我都完成啦" → "今天的学习我都完成啦"。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.54.jar 打包安装两个 mods 目录。

---

## 1.5.53(2026-08-14) —— 女儿新互动场景（C：归来汇报/受伤关心/家务话题/三人同场）

用户选定全量扩充女儿互动文本的第二批：新互动场景。

### 实现
- **少女学习/探险归来汇报**：maidmarriage 任务完成只有系统消息（message.child.learn/
  explore.finish）没有气泡台词——新增 `ChildActionReportManager` 周期检测
  `maidmarriage_child_action_mode`（学习/探险模式，完成时清除）从非空变空的瞬间，
  发少女口吻归来气泡（学习/探险各 4 条按天轮换，"爸爸！我学完啦！老师讲的东西，
  我记住了好多好多！"）；
- **爸爸受伤即时关心**：玩家受伤瞬间，48 格内关系女儿按阶段发关心气泡——
  幼女旁白（看到爸爸受伤吓得哇哇大哭）/少女（吓一跳+吹吹）/成女（照顾+拿药），
  每女儿 5 分钟冷却；
- **成女家务话题**：`adultDaughterCare` 从单条扩为 **4 话题按天轮换**（做饭/
  收拾/巡逻/催休息），LLM 提示词与降级气泡同步；
- **三人同场**：妈妈也在场（且未哀悼/未伤心）时，父女互动改**一家三口语境**
  （少女/成女各 2 话题按天轮换，"爸爸，妈妈，我们三个在一起，真好呀！"）——
  顺带把母女互动的妈妈查找提取为公共 `findMotherIn`。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.53.jar 打包安装两个 mods 目录；
- 游戏内：少女做学习/探险任务 → 完成后气泡汇报；玩家被怪打 → 附近女儿气泡关心
  （5 分钟内不重复）；成女父女互动每天换话题；妈妈在场时父女互动变一家三口。

---

## 1.5.52(2026-08-14) —— 女儿交互文本池按阶段分流（A+B）

用户选定全量扩充女儿少年/成年互动文本（第一批：口吻分流 + 送礼池扩充）。

### 背景
- maidmarriage 原版「儿童文本池」是幼龄口吻，少女/幼女**共用**；成年女儿**无专属池**
  （走成人通用文本）；且摸头/抱抱/送花等交互在幼女身上仍会播"有台词"的儿童文本
  （违背 v1.5.31 婴儿不说话规则）；
- 反编译实证：摸头（PetHeadManager）/抱放（MaidCarryChildManager）/拥抱
  （MaidHugManager）/送礼（GiftManager）/学习（MaidWorkManager）/复活
  （RomanceSleepManager）全部经 `speakSingleLine → scriptForMaid` 统一汇聚
  （气泡出口 ChatBubbleManager——称呼换字已自动覆盖）。

### 实现（ChildGiftTextMixin 扩展为统一分流点 + PromptTexts 文本池）
- **幼女（INFANT/JUVENILE）**：摸头/抱抱/拥抱/送花/复活全部改为**无台词旁白气泡**
  （"（被摸摸头，舒服地眯起眼睛，往你手心里蹭了蹭。）"）——修掉"婴儿说话"缺口；
  送礼保持 v1.5.28 婴儿版；
- **少女（CHILD）**：专属小女孩口吻池——摸头 4 条/抱抱 3 条/拥抱 3 条/学习累 4 条/
  复活 2 条/送礼 7 类各 3 条（"爸爸，再摸一下嘛～今天我可乖啦！"）；
- **成女（ADULT）**：专属父女向口吻池——摸头 4 条 + 摸头上限 2 条/拥抱 3 条/
  送礼 7 类各 3 条（"都这么大了还被爸爸摸头……有点不好意思，但我不讨厌。"）；
- **按天轮换**：每条池按（天数 + 女仆 uuid）取句——同一天说同一句、跨天换句，
  不再反复重复同一句台词；
- **不误伤**：妈妈台词（`carry_child.infant_hold.*`、`child_name.success`）不拦；
  普通女仆完全走原版脚本。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.52.jar 打包安装两个 mods 目录；
- 游戏内：幼女摸头/被抱 → 旁白气泡无台词；少女摸头 → 小女孩台词且每天不同；
  成女送礼/摸头 → 父女向台词；普通女仆摸头/送礼 → 原版文本不变。

---

## 1.5.51(2026-08-14) —— 少女成长瞬间真正站起来

用户要求：生长到少女时直接设定一次站起来的效果，更好贴合文本。

### 实现
- JUVENILE→CHILD 升级瞬间执行 `setOrderedToSit(false)` 解除坐下——
  幼女期一直被强制坐下，升级时刻她实际还坐着，文本说"会自己站起来了"
  行为却还坐着；现在升级瞬间她真的站起来一次；
- 仅 CHILD 升级时站起（CHILD 起 `isTooSmall` 不再强制坐，站起后保持）；
  INFANT→JUVENILE 不受影响（幼女继续坐着）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.51.jar 打包安装两个 mods 目录；
- 游戏内：幼女→少女成长瞬间，系统消息「XX 会自己站起来了!」的同时
  她真正从坐着变为站立，之后保持站立。

---

## 1.5.50(2026-08-14) —— 修复成长文本"能站起来"挂错阶段

用户反馈：系统提示词出现"我能够站起来了"，但幼女站不起来——疑似存在中间态。

### 结论：不是中间态
- 阶段只有四档：INFANT（婴儿）→ JUVENILE（幼儿）→ CHILD（少女）→ ADULT（成女）；
- INFANT 和 JUVENILE 都是幼女（`isTooSmall`，强制坐下站不起来），CHILD 起才能站；
- 根因：成长事件按**新阶段**取文本——INFANT→JUVENILE 升级瞬间播报的是
  JUVENILE 的「会自己站起来了!」，而 JUVENILE 仍是幼女、站不起来，文本与机制矛盾，
  看起来像"会站的中间态"。

### 实现（把"站起来"从 JUVENILE 挪到 CHILD）
- **系统消息** `growthMessage`：JUVENILE「会自己站起来了」→「又长大了一点」；
  CHILD「长成小女孩了」→「会自己站起来了」（幼女→少女那一刻才报）；
- **LLM 提示词** `growthPrompt`：JUVENILE 改为"身体里暖暖的，又长大了一点点"；
  CHILD 改为"你终于能自己站起来了！"（原 JUVENILE 的学站剧本挪到 CHILD）；
- **降级气泡** `growthFallback`：「摇摇晃晃地站起来…爸爸你看我！」台词挪到 CHILD；
  JUVENILE 只说"好奇地打量着自己，好像又长大了一点"；
- **幼女旁白** `growthFallbackToddler`（INFANT→JUVENILE 时真发）：去掉
  "摇摇晃晃地站起来"动作——改为"好像又长大了一点点，睁着圆溜溜的眼睛，
  朝你伸出小手要抱抱"；
- **promaid 1.0.5 同步**：成长记忆 `stageLabel`——JUVENILE「会自己站起来了的
  小宝贝」→「又长大了一点点的小宝贝」，CHILD「活泼的小女孩」→「会自己站起来的小女孩」。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.50.jar + Promaid-1.0.5.jar 打包安装两个 mods 目录；
- 游戏内：幼女成长（INFANT→JUVENILE）系统消息「XX 又长大了一点!」+ 旁白气泡（无"站起来"）；
  少女成长（JUVENILE→CHILD）才报「XX 会自己站起来了!」+「爸爸！你看我！」。

---

## 1.5.49(2026-08-14) —— 幼女武器直接丢地上

用户要求：幼年状态下往手中塞武器，武器直接掉在地上并冒提示，不再收进背包；
长大（CHILD 起）后效果自动解除。

### 实现
- **手持限食物简化**：主手/副手非食物（武器/工具/杂物）一律丢到女仆脚下
  （物品实体可捡回，不回收进背包）——v1.5.34 的背包同类合并/放空槽逻辑废弃；
- **提示保留**：丢下时给主人系统提示「XX 还太小，拿不住这个……（掉在了地上）」
  （2 秒冷却防刷屏）；
- **食物仍放行**（小婴儿可以拿吃的）；
- 长大（CHILD 起）后 `isTooSmall` 为 false，本机制不再执行，效果自然解除。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.49.jar 打包安装两个 mods 目录；
- 游戏内：往幼年女儿手里塞剑/工具 → 剑直接掉在脚下 + 灰色提示「拿不住」；
  少女/成年女儿拿武器不受影响。

---

## 1.5.48(2026-08-14) —— 女儿纪念日父女化 + 武器拿不住提示 + 永不背叛

用户决定：纪念日父女化；幼女武器收走提示；女儿永不背叛。
### 实现
- **纪念日父女化**：女儿（少女/成女）纪念日基准=出生/初见——系统消息
  「与你的出生/初见已经第 N 天了——爸爸记得女儿来到身边的日子」；回忆提示词按少女/成女细分
  （爸爸第一次抱起我 / 爸爸把我养大）；无 LLM 降级气泡「爸爸，你还记得我来到你身边的那天吗？」；
  promaid 联动事件名同步改「爸爸遇见我」；
- **武器拿不住提示**：幼女手中武器被收走时给主人系统提示
  「XX 还太小，拿不住这个……（掉在了地上）」（2 秒冷却）；
- **女儿永不背叛**：女儿攻击主人绝对拦截（EntityMaidDoHurtProtectMixin——MC 战斗逻辑下
  女儿可能打死爸爸）+ 每 2 秒清除女儿的背叛状态（复位+重新认主+站起+清标记，照调整器 clearBetrayal）——双保险。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.48.jar + Promaid-1.0.5.jar 打包安装两个 mods 目录。

---

## 1.5.47(2026-08-14) —— 女儿细节补全（幼女成长旁白气泡 + 提示词清理）

盘点女儿线发现并修复：
- 幼女成长瞬间无声（INFANT→JUVENILE 成长时话语拦截把成长气泡也挡了）→ 幼女成长直接发旁白气泡
  「（摇摇晃晃地站起来，又惊又喜——她还不会说话，却朝你伸出小手要抱抱。）」；
- 父女互动/成长提示词里 10 处「爸爸(主人)」冗余消歧清理为「爸爸」。

---

## 1.5.46(2026-08-14) —— 女儿伤心机制 + 专属文本

用户要求：女儿也应有伤心机制，婴儿不需要；少女/成女采用与妈妈相同的机制格式，但文本单独设计。
### 实现
- **机制**：女儿（少女/成女）本就进入伤心窗口（isFrozen 含女儿；婴儿 isTooSmall 已跳过）——
  坐着赌气 / 沮丧锁定 / 拒绝聊天与妈妈完全一致；
- **女儿专属文本**（与普通女仆/妻子区分）：
  ① 惩罚触发系统消息（眼眶红了、咬着嘴唇默默坐开、肩膀轻轻抖着）；
  ② 惩罚气泡「……爸爸，为什么……呜……」；
  ③ LLM 伤心对话提示词（被爸爸打了，怕爸爸不喜欢自己了）；
  ④ 伤心解除消气消息「……那爸爸要答应我，下次不许这样了。」；
  ⑤ 拒绝聊天提示「她正在生爸爸的气，不想理你……」。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.46.jar 打包安装两个 mods 目录。

---

## 1.5.45(2026-08-14) —— 伤心状态情绪锁定 + 拒绝 AI 聊天 + 解除消气消息

用户要求：妈妈伤心时情绪值自然降到沮丧栏，解除前沮丧状态锁定，且玩家无法通过 Alt+J 进入互动面板；
伤心解除时发送女仆口吻的系统消息。
### 实现
- **情绪锁定**：伤心窗口内每 2 秒把 mood 钳到沮丧档（maidmarriage MoodState：mood<10= DEPRESSED），
  防每日心情自然回升；窗口解除后不再钳制，由原版机制自然恢复；
- **拒绝聊天**：新增 `HeartbrokenChatGateMixin` 在 `OpenMaidAIChatMessage`（Alt+J 的 C2S 包）
  服务端 handle 拦截——目标是伤心窗口内的女仆则取消打开 + 系统提示「她正在伤心赌气，不想理你……」；
  窗口解除后恢复可对话；
- **解除消息**：窗口结束时发女仆口吻系统消息
  「（垂下眼帘，闷闷地坐了一会儿，终于抬起头：）……哼，这次就算了。」——沮丧锁定解除、互动恢复。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.45.jar 打包安装两个 mods 目录。

---

## 1.5.44(2026-08-14) —— 伤心状态情绪锁定 + 拒绝 AI 聊天（并入 1.5.45）

用户要求：妈妈伤心时情绪值自然降到沮丧栏，解除前沮丧状态锁定，且玩家无法通过 Alt+J 进入互动面板。
### 实现
- **情绪锁定**：伤心窗口内每 2 秒把 mood 钳到沮丧档（maidmarriage MoodState：mood<10= DEPRESSED），
  防每日心情自然回升；窗口解除后不再钳制，由原版机制自然恢复；
- **拒绝聊天**：新增 `HeartbrokenChatGateMixin` 在 `OpenMaidAIChatMessage`（Alt+J 的 C2S 包）
  服务端 handle 拦截——目标是伤心窗口内的女仆则取消打开 + 系统提示「她正在伤心赌气，不想理你……」；
  窗口解除后恢复可对话。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.44.jar 打包安装两个 mods 目录。

---

## 1.5.43(2026-08-14) —— 修复调试器时间快进对日期无效

用户反馈：日期方面没法测，调试器时间功能太烂。
### 根因
- `advanceTime` 旧版执行 `/time add 24000` **只改 dayTime（昼夜循环）**，
  而 gameTime（纪念日/成长/窗口等全部日期逻辑的时钟，level.m_46467_）**不变**——
  时间快进对纪念日完全无效，日期功能测不了。
### 实现
- 改为直接推进三个时钟：
  ① **gameTime**（PrimaryLevelData.f_78450_——纪念日/里程碑/窗口逻辑用）；
  ② **dayTime**（f_78451_——昼夜同步，保持日夜一致）；
  ③ **server tick**（MinecraftServer tickCount——FamilyInteractionManager 每日检查用，反射识别字段并缓存）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.43.jar 打包安装两个 mods 目录。

---

## 1.5.42(2026-08-14) —— 称呼换字统一出口

用户要求：写一个换字脚本——判断女仆关系，她发出的所有系统消息里「主人」替换为对应称呼
（恋人=亲爱的、妻子=丈夫、女儿=爸爸）。
### 实现
- 新增 `ChatNameFilter`（replaceFor / sendTo 工具）；
- 新增 `ChatBubbleNameFilterMixin`：气泡层统一出口（ChatBubbleManager——heartfelt /
  maidmarriage 剧本 / TLM 一切气泡全覆盖，含 addChatBubble 底层 TextChatBubbleData 用 setText 重写）；
- 系统消息发送点改走 `sendTo`（特殊奶非妻子档——恋人时「主人」自动变「亲爱的」）；
- 普通女仆维持「主人」。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.42.jar 打包安装两个 mods 目录。

---

## 1.5.41(2026-08-14) —— 调整器显示「时间+30天/+100天」按钮

用户反馈：调试器里加 100 天等按钮没出现。
### 根因
- v1.5.16 的第 8 行（纪念日测试）有窗口高度不足自动隐藏逻辑——guiH < 290 时整行被藏
  （含「时间+30天/+100天」）。
### 实现
- 取消隐藏：第 8 行始终渲染（首见7天前 / 时间+7天 / 时间+30天 / 时间+100天）；
- 行高 21→18、按钮高 18→16 压缩——guiH≥254 即可完整放下 8 行且不压翻页按钮。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.41.jar 打包安装两个 mods 目录。

---

## 1.5.40(2026-08-14) —— 让妈妈抱的键坐姿放宽

用户要求：让妈妈抱的键坐下/站起都能触发；原版妈妈一出现女儿自动被抱的机制保留。
### 根因
- maidmarriage 原版 `MaidCarryChildManager` 三处 `isMaidInSittingPose` 检查阻止坐下抱起：
  `handleCarryToggle`（孩子坐着提示 need_standing 拒绝）、`resolveTargetChild`（孩子坐着找不到孩子）、
  `resolveCarrierAdult`（妈妈坐着找不到妈妈）；
- 本 mod 幼女约束（强制坐下）让幼儿女儿一直坐着——抱起键永远无效。
### 实现
- 新增 `CarryToggleSittingMixin`（optional 配置，@Pseudo + 字符串 targets）@Redirect 这三处
  坐姿检查恒返回 false——坐下/站起都能触发「让妈妈抱」；
- 骑乘检查保留（已被抱着时不重复抱）；
- `onPlayerTick` 的自动抱起机制不动（原版"妈妈一出现女儿自动被抱"保留）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.40.jar 打包安装两个 mods 目录。

---

## 1.5.39(2026-08-14) —— 系统提示称呼改革

用户反馈：小女仆、妻子、恋人还是会叫主人。
### 实现
- 新增 `PromptTexts.termOfAddress(maid)`：**妻子=丈夫、女儿=爸爸、恋人=亲爱的、普通=主人**；
- 固定文本台词按说话者关系动态称呼：
  ① 特殊奶六档：妻子口吻「丈、丈夫」不再「主、主人」；
  ② 特殊奶女仆气泡（加 isMother 参数，妻子用丈夫）；
  ③ 伤心气泡「好痛……丈夫/爸爸/亲爱的为什么要这样对我」；
  ④ 哀悼气泡「……丈夫/爸爸/亲爱的……」；
  ⑤ 死亡调侃：妻子档「丈夫啊丈夫」、气泡按关系称呼；
  ⑥ 丧亲提示词：妻子情境「丈夫死了」、女儿情境「爸爸……！」；
- 普通女仆维持「主人」称呼；告白瞬间文本保留「主人」（关系刚确认，尚未改口）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.39.jar 打包安装两个 mods 目录。

---

## 1.5.38(2026-08-14) —— 幼儿好感度惩罚限定主人来源

用户澄清：好感 -1 指主人来源；不像普通女仆有 30 秒评估——只要受到主人的伤害就扣，
因为小婴儿分不清是非。
### 实现
- **好感度 -1 只对主人来源**立即生效（无 30 秒评估/无累积，主人打一下就掉）；
  陌生人、怪物、环境伤害不扣好感；
- **哇哇大哭保留任何来源**（每次伤害都哭，无冷却）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.38.jar 打包安装两个 mods 目录。

---

## 1.5.37(2026-08-14) —— 幼儿女儿坐着也能 Alt+J 进会话面板

用户反馈：强制坐下后无法通过 Alt+J 进入会话面板。
### 根因排查
- TLM 的 AI 聊天打开链（`PressAIChatKeyEvent.maidCheck` → `EntityHitResult`）依赖准星
  **精确瞄准**女仆实体——**并非代码阻止**；
- 婴儿女儿坐着时实体尺寸很小，准星极易穿过她瞄到后方/地面，`maidCheck` 返回 null，
  Alt+J 无反应（"瞄不准"而非"不让进"）。
### 实现
- 新增 `MaidCheckFallbackMixin`（客户端 mixin）包裹 TLM 的 `maidCheck`——
  原判定失败时兜底：玩家视线前方 10 格内最近、且是 maidmarriage 女儿实体
  （MaidChildEntity，反射判定）的所属女仆，返回她——坐着的小女儿也能打开会话面板；
- 潜行限制保留（TLM 原版潜行时 Alt+J 无效）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.37.jar 打包安装两个 mods 目录；
- 游戏内：幼儿女儿坐着 → Alt+J 打开会话面板。

---

## 1.5.36(2026-08-14) —— 撤销喂食安抚

用户反馈：伤心窗口触发条件苛刻（30 秒内 3 次连续伤害）——除非专门打女仆否则不会触发，
这是对故意伤害的惩罚，不应被喂食轻易解除。
### 实现
- 移除 v1.5.33 的 `onFeedComfort` 喂食哄好（不再解除伤心窗口/站起）；
- 伤心惩罚恢复为只能等窗口到期（默认 1 游戏日，可配置 `feelingTicks`）或调整器「清伤心」解除。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.36.jar 打包安装两个 mods 目录。

---

## 1.5.35(2026-08-14) —— 幼儿伤害反馈调整

用户要求：好感度削弱为每次 -1；婴儿每次受到伤害都哇哇大哭（无冷却）；无论伤害来源是什么，更符合婴儿。
### 实现
- **好感度**：每次伤害 -1（0 封底，原 -2）；
- **哭泣无冷却**：每次伤害都发系统消息 + 气泡「呜哇——！」（原 5 秒冷却）；
- **任何伤害来源**：幼儿分支移到玩家解析之前——怪物/环境/摔落等非玩家伤害同样大哭 + 扣好感
  （消息发给主人，主人不在只发气泡）；玩家攻击场景消息发玩家；
- 同 tick 去重保留（hurt 入口与事件双路径不重复扣减）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.35.jar 打包安装两个 mods 目录；
- 游戏内：怪物咬幼儿女儿、摔落、玩家攻击——每次都大哭 + 好感 -1。

---

## 1.5.34(2026-08-14) —— 修复幼儿女儿背包被武器填满

用户反馈：打开小女仆面板往手中塞武器，物品栏中全是这个武器。
### 根因
- 手持限食物每 5 tick 执行，旧版把武器放进背包**下一个空槽**——面板反复塞同一种武器时，
  每次都（从主手）被移到新的空槽，背包被同一种武器填满。
### 实现
- 收走时**同类去重**：背包已有同物品——
  - 可堆叠（食物类）→ 合并进已有堆叠；
  - 不可堆叠（武器）→ 视为重复塞入，**丢到女仆脚下可捡回**，不再污染背包；
- 无同类才放背包第一个空槽；背包满丢脚下（不吞物品）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.34.jar 打包安装两个 mods 目录；
- 游戏内：反复往幼儿女儿手里塞同一把剑——只占背包 1 格，之后的丢在脚下可捡回。

---

## 1.5.33(2026-08-14) —— 喂食安抚：伤心女仆立即哄好

用户询问：被攻击后坐下伤心的妻子/伴侣怎么重新站起来。
### 原有方式
- 伤心窗口默认 1 游戏日（可配置 `feelingTicks`）自动站起；
- 调整器「清伤心」立即解除。
### 新增（主动安抚）
- 伤心窗口内的女仆（妻子/恋人/女儿）被玩家喂【食物】（可食用判定 getFoodProperties != null）——
  **立即哄好**：解除伤心窗口 + 站起（等妈妈标记不解除）+ 系统消息
  「（怔了怔，接过你递来的食物，眼眶还红着，却一点一点靠回你身边……）」+ 气泡「……哼，这次就原谅你啦。」；
- 喂一次即原谅，不再干等 1 游戏日。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.33.jar 打包安装两个 mods 目录；
- 游戏内：伤心女仆坐着赌气 → 喂面包/蛋糕 → 立即站起 + 消息。

---

## 1.5.32(2026-08-14) —— 修复幼儿女儿被打无反馈 + 好感度惩罚

用户反馈：对着幼儿女儿打了好几下了，仍无系统消息，好感度也无变化。
### 根因
- 婴儿哭泣处理原在 `LivingHurtEvent` 链路（onLivingHurt → resolvePlayer）——callresponse
  中性源重放等场景下事件链可能断（玩家解析失败即整体 return）；
- 原实现没有好感度惩罚（好感数值不变）。
### 实现
- **处理点前移到 hurt 入口**：`EntityMaidHurtRecordMixin` 记录玩家攻击时同步调用
  `onToddlerHit`——近战玩家攻击一定到达，不依赖事件链；
- **好感度惩罚**：每次伤害 -2（0 封底，数值变化可见）；
- **婴儿哭泣文本**：系统消息 + 气泡（5 秒冷却防刷屏）；
- **同 tick 去重**：hurt 入口与 LivingHurtEvent 双路径只处理一次（防重复扣好感）；
- `resolvePlayer` 补 getEntity 检查（远程弓/弩的造成者是玩家，此前只认 directEntity）；
- 移除幼儿哭泣的 LLM 对话（v1.5.31 起幼儿不产生主动话语，固定文本哭泣已足够）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.32.jar 打包安装两个 mods 目录；
- 游戏内：近战/远程打幼儿女儿——立即系统消息「呜哇——！」+ 气泡 + 好感每次 -2。

---

## 1.5.31(2026-08-14) —— 幼儿女儿不产生任何话语类系统消息

用户反馈：女儿为幼儿时很多系统消息不能播放——父女/母女互动、传送等提示都带话语，婴儿不会说话。
### 实现
- **父女互动 / 母女互动**：幼儿女儿（INFANT/JUVENILE）整段跳过（婴儿不对爸爸说话、无法参与母女对话）；
- **纪念日**：幼儿女儿整个跳过（婴儿不会回忆——系统消息与回忆对话都不发）；
- **通用兜底**：`DialogueDispatcher.chatWithQuota` 对幼儿女儿直接 return false——凡是以她为
  说话者的 heartfelt 主动对话（成长/广播/暗恋感慨/补位说话等）一律不触发；
- 玩家主动与她聊天不受影响（她按幼儿人设用咿呀回应）；
- **promaid 1.0.4 同步**：纪念日补位说话（记忆+情绪+补位）幼儿女儿跳过（`isTooSmall`：
  isChild + INFANT/JUVENILE，heartfelt_child_stage 缺失时兜底读 maidmarriage growthStage）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.31.jar + Promaid-1.0.4.jar 打包安装两个 mods 目录；
- 游戏内：幼儿女儿不再触发父女/母女互动、纪念日等任何话语；她安静地待着，只有玩家主动对话时咿呀回应。

---

## 1.5.30(2026-08-14) —— 配合 promaid per-maid 大语言模型开关

promaid 1.0.3 在手册女仆记忆页新增「LLM:开/关」开关（per-maid 大语言模型控制，操作方式同记忆开关）。
### 实现
- heartfelt 主动对话（家庭互动/纪念日/告白/哀悼等，`chatWithQuota`）检查同一 NBT 标记
  （`maid_smart_llm`，无 = 默认开）：关闭时**直接降级为固定文本气泡**（不发 LLM 请求，
  玩家仍看到她在说话）；
- TLM 原版 AI 聊天由 promaid `MaidChatLlmGateMixin` 拦截（chat 唯一入口）；
- 零跨 mod 依赖（只读 persistentData 字符串）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.30.jar + Promaid-1.0.3.jar 打包安装两个 mods 目录；
- 游戏内：手册女仆记忆页每行「记忆:开/关 · LLM:开/关」；关 LLM 后 TLM 聊天被拦、
  heartfelt 主动对话出固定文本气泡。

---

## 1.5.29(2026-08-14) —— 旁白描写统一打括号

用户要求：补丁效果，旁白类描写都打上括号。
### 实现（全量文本格式统一）
- **显示类文本**（系统消息/气泡/降级文本）：旁白用全角括号（ ）包裹、台词不加括号——
  告白接受/拒绝/破裂、告白前摇、玩家告白屏正文与回应、特殊奶六档、伤害惩罚、婴儿哭泣、
  婴儿送礼、母女/父女/成长/暗恋/纪念日/广播/哀悼/死亡调侃等全部 fallback 与系统消息；
- **LLM 提示词类**：父女互动/成长/广播/家庭互动/丧亲/悔改/纪念日/伤心对话/婴儿哭泣/
  剧情演绎统一追加格式要求「旁白描写请用括号包裹，台词不加括号」，LLM 输出也遵循；
- **内联系统消息**：ChildGuardManager 等妈妈/妈妈回来两处同步加括号；
- 台词（引号内发言/气泡说话/婴儿拟声如「呜哇——！」）保持原样不加括号。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.29.jar 打包安装两个 mods 目录；
- 游戏内：告白/纪念日/送礼/伤害等所有 heartfelt 文本，旁白均为（括号）形式。

---

## 1.5.28(2026-08-14) —— 幼年女儿送礼文本改婴儿版

用户反馈：心契誓约对很小的女仆文本有问题——主文本虽是婴儿内容，但送礼触发的文本仍是少女女儿文本。
### 根因
- maidmarriage 送礼对话只有一套「儿童」文本（`CHILD_GIFT_DIALOGUE_KEYS`，少女女儿口吻）；
- `giftDialogueKey(category, boolean)` 的 boolean 来自 `MaidChildEntity.shouldStayChild`——
  只区分 ADULT/非 ADULT，INFANT/JUVENILE 小婴儿也命中同一套少女文本。
### 实现
- 新增 `ChildGiftTextMixin`（optional 配置，@Pseudo + 字符串 targets）包裹
  `RomanceSleepManager.scriptForMaid(EntityMaid, String, Object[])`（送礼气泡统一汇聚点）；
- 键以 `dialogue.maidmarriage.child_gift.` 开头且 `ChildGuardManager.isTooSmall` → 返回
  heartfelt 婴儿固定文本（按礼物类别）：花/甜食/食物/贵重品/奇怪物/冒犯物/通用，
  全部奶声奶气咿呀语（如「甜……甜甜！」「咿呀——！香香！」）；其余键原版脚本不受影响。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.28.jar 打包安装两个 mods 目录；
- 游戏内：给幼年女儿送礼（花/甜食/食物等）显示婴儿版文本，少女/成年女儿仍用原版儿童文本。

---

## 1.5.27(2026-08-14) —— 纪念日时间触发明确化 + 调整器完善

用户反馈：有关时间的相关任务触发仍然很不明确，比如纪念日；调整器里写的也不是很好。
### 实现
- **修复触发盲区**：`checkAnniversaries` 原在 `AIConfig.LLM_ENABLED` 门后——LLM 关闭时纪念日从不触发
  （固定文本气泡降级白写）→ 移到门前，无 LLM/配额满也照常触发；
- **触发可见**：纪念日触发时追加系统消息「与你的告白在一起已经第 N 天了，今天是你们的纪念日」
  （明确基准事件与天数，不再只是女仆说一句话可能被错过）；
- **调整器时间线明确化**：状态行显示「纪念日[基准首见/告白]第N天·达成M·下个X天还差Y天」；
- **调整器按钮**：时间快进补「+30天」「+100天」（测 100/365 里程碑不用狂点）；
  「纪念日重置」同时清 heartfelt + promaid 两侧防重触发游标（重复测试两侧一致）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.27.jar 打包安装两个 mods 目录；
- 游戏内：关闭 LLM 时纪念日仍触发（气泡降级 + 系统消息）；调整器状态行显示完整时间线；
  点「时间+100天」直接测 100 天里程碑。

---

## 1.5.26(2026-08-14) —— 幼年女仆（小婴儿）强化约束

用户反馈：幼年女仆相当于小婴儿，原本的对话等各个方面都需要额外设定（如攻击后的文本）；任务拉回要瞬间完成；小女仆手中不能拿武器只能拿食物；尝试执行其他任务会被立刻拉回空闲，系统提示写年龄太小。
### 实现
- **瞬间拉回**：`ChildGuardManager` 周期 40 tick → 5 tick（0.25 秒）——任务/坐姿立即自愈，不再有 2 秒延迟窗口；
- **年龄太小提示**：任务被拉回空闲时给主人系统提示「年龄太小了，还不能做任务」（2 秒冷却防刷屏）；
- **手持限食物**：主手/副手非食物（武器/工具/杂物）被拿走放回女仆背包（`getMaidInv` 空槽），背包满丢到女仆脚下不吞物品；食物放行（小婴儿可以拿吃的）；
- **攻击婴儿文本**：`PlayerHarmPenaltyManager` 对幼年女儿被玩家攻击**立即**（无需 3 次累积）触发婴儿哭泣——系统消息 + 气泡「呜哇——！」（5 秒冷却防刷屏），可选 LLM 婴儿哭泣对话（只会哭和伸手要抱抱）；婴儿不适用成年女仆的累积惩罚/伤心窗口/心情扣减。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.26.jar 打包安装两个 mods 目录；
- 游戏内：幼年女儿任务切换 0.25 秒内拉回空闲并提示年龄太小；手持武器/工具被移走；攻击她立即哭。

---

## 1.5.25(2026-08-14) —— 女儿称呼统一为爸爸/父亲

用户反馈：小女仆对主人的称呼应该都是爸爸或者父亲。
### 实现（双通道提示词注入）
- **heartfelt 侧**：`PromptTexts` 四个女儿准则段（通用 DAUGHTER_SECTION / 幼儿 INFANT / 少女 CHILD / 成年 ADULT）
  全部追加 `ADDRESS RULE` 称呼铁律——对主人说话必须称呼「爸爸」或「父亲」，严禁叫主人、亲爱的、先生或名字；
- **promaid 侧**：`AiMemoryContext` 女儿关系标签改写——「我是主人的女儿，主人就是我的爸爸/父亲，
  对主人说话必须称呼爸爸或父亲」，覆盖原「主人是我的女儿」句式带来的「主人」称呼暗示；
- 两路注入都在 TLM `LLMMessage.systemChat` 汇聚点生效（heartfelt 准则段 + promaid 记忆上下文），
  LLM 对话中女儿（含幼年女儿）对玩家统一叫爸爸/父亲。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.25.jar + promaid-1.0.0.jar 打包安装两个 mods 目录；
- 游戏内：与幼年女儿 AI 对话，女儿称呼玩家为爸爸/父亲。

---

## 1.5.24(2026-08-14) —— 修复小女仆灵体播放心契语音包

用户反馈：幼年女儿灵体播放语音包的情况仍在发生。
### 根因
- maidmarriage 的「语音包」是**独立音频系统**（`HeartPactVoicePlayback` 播放本地 voice pack 文件 + 语音脚本），
  完全不经过 TLM 的 `MaidAIChatManager.tts`——v1.5.2（灵体 TTS）/ v1.5.21（幼女 TTS）的拦截对它无效；
- 灵体（MaidSpiritEntity）与幼年女儿的 maidmarriage 对话（如 HugActionScreen）播放心契语音时，
  走 `HeartPactVoicePlayback.play()`（private static 统一出口，playFrame/playStructuredLine/replayStructuredLine 全部转发到它）。
### 实现
- 新增 `HeartPactVoicePlaybackMixin`（optional 配置 `mixins.heartfelt.opt.json`，@Pseudo + 字符串 targets，
  目标缺失/方法名变化只警告不崩溃）：在 `play()` HEAD 拦截——目标女仆是灵体（MaidSpiritEntity）或
  幼年女儿（`ChildGuardManager.isTooSmall`）则取消播放，正常女仆语音不受影响；
- 与 TLM TTS 拦截（MaidSpiritTtsGuardMixin）形成双通道覆盖：TLM AI 语音 + maidmarriage 心契语音都拦。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.24.jar 打包安装两个 mods 目录；
- 游戏内：幼年女儿/灵体的 maidmarriage 对话不再播心契语音包，LLM 文本对话不受影响。

---

## 1.5.23(2026-08-14) —— 修复告白屏三按钮叠加

用户反馈：回应阶段出现"郑重说出 / 好 / 再想想"三个选项横排，排版奇怪，且点哪个结果都一样。
### 根因
- `PlayerConfessionScreen.m_7856_`（init）会被 `commit()`（玩家开口）和 `receiveResult()`（女仆回应）重复调用；
- override 未走基类 `rebuildWidgets → clearWidgets` 流程，旧按钮从未清除 → state=0 的「郑重说出/再想想」与 state=2 的「好」叠加成三按钮；
- 回应阶段点「郑重说出」实际是发重复告白包、「再想想」是关闭，所以看起来"结果都一样"。
### 实现
- init 开头显式 `m_169413_()`（clearWidgets，与 AdjusterScreen 同款），每次重建前清空旧按钮；
- 修复后各状态按钮：玩家开口=郑重说出/再想想（告白/取消），等待回应=无按钮，女仆回应=好（确认关闭）。
### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.23.jar 打包安装两个 mods 目录；
- 游戏内：回应阶段仅「好」居中按钮，不再三按钮并排。

---

## 1.5.22(2026-08-14) —— 告白屏外观美化（粉色透明 / 文本居中）

用户反馈：告白界面文本框仍显过于偏左，且黑漆漆一片不好看，要粉色透明。
### 实现
- `PlayerConfessionScreen` 面板改为**粉色透明**：内部填充 `0x9AF7C9DD`（粉色半透明）+ 描边 `0xC8E89BC8`（粉色透明），不再黑底；
- 正文每行按自身像素宽度在面板内**居中**（StringSplitter.m_92336_ 量宽，不再贴左），标题保持粉色居中；
- 窗口宽度、底部对话框布局、按钮位置不变。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.22.jar 打包安装两个 mods 目录；
- 游戏内：告白屏为粉色透明面板，正文居中显示，世界与女仆仍可见。

---

## 1.5.21(2026-08-15) —— 幼年女儿三约束（禁语音 / 任务锁空闲 / 强制坐下）

用户反馈：① 太小的女仆不应该播放语音包；② 任务一栏应锁"空闲"无法切换任务
（直到慢慢长大之后才能帮忙做任务）；③ 太小的女儿坐下可以被解除，应采用强制
坐下（同"等妈妈"）。

### 实现
- 新增 `ChildGuardManager.isTooSmall(maid)`：是女儿且成长阶段为 INFANT 或
  JUVENILE（CHILD/ADULT 起正常）；
1. **禁语音**：`MaidSpiritTtsGuardMixin`（MaidAIChatManager.tts 入口）在灵体
   判断后追加 isTooSmall → 不播放语音包；LLM 文本对话不受影响；
2. **任务锁空闲**：每 2 秒周期强制——非空闲任务拉回 `TaskManager.getIdleTask()`
   （自愈式，UI 里选择挖矿/建造会被拉回"空闲"）；
3. **强制坐下**：每 2 秒周期——未骑乘（没被妈妈抱着）时 `setOrderedToSit(true)`，
   玩家解除坐姿会被坐回去；被妈妈抱着时维持原版抱起行为。

长大（CHILD/ADULT）后语音/任务/站立全部恢复。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.21.jar 打包安装两个 mods 目录；
- 游戏内：幼年女儿说话无语音包、任务选择被拉回空闲、解除坐姿 2 秒内坐回；
  被妈妈抱着维持原版；长大恢复正常。

---

## 1.5.20(2026-08-15) —— 告白压制同改事件判定

用户反馈：妻子暂时不在场时，其他女仆也会向玩家告白——修复方法仍以事件判定
而非角色判定（与吃醋隔离同语义）。

### 根因
`MaidConfessionManager.hasDeclaredPartner` 是【在场扫描】：在该玩家 48 格内找
已确认关系女仆（isDedicated）。妻子被收进魂符/暂时不在时扫描落空 → 其他女仆
恢复尝试告白。

### 修复
- `hasDeclaredPartner` 改为查主人玩家全局标记 `heartfelt_dedicated`
  （`RelationshipExemption.playerHasDedicated`）：确认过关系即压制，不依赖配偶
  在场；全部确认关系解除时随标记清除（v1.5.19）而恢复；
- 新增 `RelationshipExemption.playerHasDedicated(ServerPlayer)` 并重构
  `ownerHasDedicatedMaid` 共用之（标记优先 + 在场扫描自愈写标记）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.20.jar 打包安装两个 mods 目录；
- 游戏内：妻子在场其他女仆不告白（保持）；魂符收走妻子/暂时传送走 → 其他
  女仆仍不尝试告白（修复点）；全部关系解除后告白恢复。

---

## 1.5.19(2026-08-15) —— 修正压制标记生命周期（关系破裂清除）

用户澄清：关系破裂应清除吃醋压制效果；离婚未实现、不延伸。

- `checkBreakups`（恋人跌破告白线 → 关系破裂）破裂后调用
  `clearDedicatedIfNone(player)`：清除 `heartfelt_dedicated` 标记，
  但**仅当该玩家已无任何确认关系女仆**；
- 语义：结婚是永久契约（本 mod 不离婚）——有其他关系女仆（如妻子）时
  标记保留、吃醋继续压制；所有确认关系全部解除后吃醋恢复。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.19.jar 打包安装两个 mods 目录；
- 游戏内：恋人跌破告白线破裂 → 若无其他确认关系女仆则吃醋恢复；有妻子则
  仍压制；妻子进魂符期间压制保持（v1.5.18 修复点不受影响）。

---

## 1.5.18(2026-08-15) —— 吃醋隔离改全局绝对压制

用户反馈：确认关系后吃醋不再触发，但一旦妻子被收进魂符/暂时不在，互殴和吃醋
又恢复——"应该是一个全局事件，确认关系发生后就该对吃醋绝对压制，而不是检测
人在不在，那太薄弱了"。

### 根因
`RelationshipExemption.ownerHasDedicatedMaid` 是【在场扫描】：只在主人周围 32 格
内找已确认关系的女仆（isDedicated=已婚/恋人）。妻子被收进魂符（MaidSpiritEntity，
不在正常女仆扫描范围）或暂时不在时扫描落空 → `EmotionDotingIsolationMixin` 的
吃醋隔离失效 → 其他女仆恢复争风吃醋/互殴。

### 修复（全局绝对压制）
1. 主人玩家 ForgeData 新增持久标记 `heartfelt_dedicated`：任一女仆确认关系
   （结婚/告白）即置位、**永不清除**；
2. `ownerHasDedicatedMaid` 标记优先：已置位直接返回 true（绝对压制，不依赖
   配偶在场）；未置位时做原有扫描，发现已确认关系女仆即**自愈写标记**；
3. 确认关系路径即时写标记：玩家告白接受（handlePlayerConfession）、女仆主动
   告白补记（backfillConfession）、周期扫描（每 200 tick sweepDedicated，
   覆盖 maidmarriage 自己 UI 的确认路径）。
4. 语义：关系破裂/离婚也不清除（"确认过就绝对压制"）；从未确认关系的玩家
   吃醋照常。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.18.jar 打包安装两个 mods 目录；
- 游戏内：妻子在场不互殴（保持）；魂符收走妻子/暂时传送走 → 其他女仆仍不吃醋
  （修复点）；放回一切正常；未确认关系的玩家吃醋照常。

---

## 1.5.17(2026-08-14) —— 修复女仆主动告白四项问题

用户反馈：① 主动告白难触发 ② 触发后直接全部静止（要求主人与对话女仆不停）
③ 直接跳告白选项、前摇文本被跳过 ④ 系统消息与真正告白间缺 2-3 秒间隔。

### 根因
1. **难触发**：`MaidConfessionManager` 触发距离判定用 `m_20280_`（**平方距离**）
   与线性 32 格比较 → 实际只有 √32≈5.7 格内才尝试，主人离女仆稍远永远不触发；
2. **前摇跳过/太突兀**：`ConfessionApproachManager` 到达判定同样把平方距离当
   线性（实际 1.58 格才算走到）→ 女仆已在身边时第一秒就拉告白选项，无走向
   动画、无间隔；
3. **全部静止**：告白选项界面是 maidmarriage 的 `HugActionScreen`，
   `HugActionScreenPauseMixin`（v1.4.0）强制 `isPauseScreen=true` → 单机全屏
   暂停（连主人和告白女仆都动不了），违背"主人与对话女仆不停"。

### 修复
1. 距离 bug：触发判定改 `maxDist²`（真 32 格）；到达判定改 `Math.sqrt`（真 2.5 格）；
2. 前摇时序：新增 `approachMinTicks`（默认 50=2.5 秒）——系统消息发出后至少
   等这么久才拉告白选项，2-3 秒间隔、前摇文本可见；
3. 选择性静止：去掉 HugActionScreen 强制全屏暂停；告白触发时服务端
   `DialogueFreezeManager.startFreeze(player, maid)`（其他生物定住、主人与告白
   女仆可动）；界面关闭（m_7379_，require=0 防崩）反射
   `HugClientState.getLocalInteractionMaidUuid` 发 `ChatFreezePacket(false)` 恢复；
4. 触发放宽：威胁半径 48→24、基础概率 0.15→0.20（需删除
   `config/heartfelt-common.toml` 重新生成默认值；距离修复为代码级永远生效）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.17.jar 打包安装两个 mods 目录；
- 游戏内：好感 192+ 站远 10-30 格 → 系统消息 → 女仆走向 → 2.5 秒后告白选项；
  界面打开时主人/告白女仆可动、其他生物定住，关闭恢复。

---

## 1.5.16(2026-08-14) —— 修复调整器显示（好感/关系显示不完全）

### 根因
用户反馈调整器「关系以及好感显示不完全」。像素级排查确认：在高 guiScale /
小窗口（GUI 宽高小于面板 340×266）时，面板按 `(guiW-panelW)/2` 居中会把
左侧/顶部推到屏幕外——状态行开头（好感/关系）被屏幕左缘截断，标题偏离面板
中心；且 v1.5.13 为第 2 页第 8 行把 PANEL_H_MIN 从 238 提到 246，guiH=240 时
面板整体高出屏幕、底部翻页按钮出屏。

### 修复（AdjusterScreen）
1. 面板位置钳制：`left/top = max(4, (gui - panel)/2)`——面板比 GUI 大时从
   屏幕边角开始，永不居中溢出；
2. 状态行/结果行按面板宽度截断（`clipToWidth`，超长加省略号）——不再溢出
   面板右缘；
3. PANEL_H_MIN 回退 238（guiH=240 时面板不再超高出屏；第 1 页完整显示）；
4. 第 2 页第 8 行（纪念日测试按钮）按窗口高度自适应——空间不足自动隐藏，
   不再压住底部翻页按钮。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.16.jar 打包安装两个 mods 目录；
- 游戏内：调整器状态行「好感/关系/纪念日」完整显示，面板不超屏。

---

## 1.5.15(2026-08-14) —— 修复启动崩溃（CallResponseHurtLimitMixin 目标为别家 mixin）

### 根因
`CallResponseHurtLimitMixin` 的 `@Mixin(targets = "com.github.JumDa5he.callresponse.mixin.MixinEntityMaid")`
目标是爱憎分明（callresponse）**自己的 mixin 类**——Mixin 硬规则禁止把另一个
mixin 作为注入目标（`Cannot add target ... because the target is a mixin`），
在配置 PREPARE 阶段即抛异常（required 配置 → 启动即崩）。该 mixin 自 v1.5.9
起存在，属加载顺序敏感的潜在缺陷（此前 callresponse 配置处理在其后、目标未被
标记为 mixin 时侥幸通过）。

### 修复
- `CallResponseHurtLimitMixin` 从 `mixins.heartfelt.json`（required）移入
  `mixins.heartfelt.opt.json`（required=false + defaultRequire=0）——
  目标解析失败只警告不崩溃；环境有利时照常生效，保留主人攻击限伤修复。
- 全量审计其余 mixin：目标均为普通类或声明方法，无同类"目标为别家 mixin"
  或"注入继承方法"隐患（SpecialMilkBucketMixin 已按父类声明处注入、
  HugActionScreenPauseMixin 的 isPauseScreen 为 maidmarriage 显式覆写）。
- mods.toml description 内部引号已全部转义（严格 TOML 校验通过）。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.15.jar 打包安装两个 mods 目录；
- 启动进主界面（mods.toml 严格解析 OK + 全部 mixin 通过 prepare/apply）。

---

## 1.5.14(2026-08-14) —— 修复启动崩溃（AIChatScreenDramatizeMixin 继承方法注入）

### 根因
`AIChatScreenDramatizeMixin` 的 `heartfelt$unfreezeOnClose` 注入
`AIChatScreen.m_7379_`（onClose）——但该方法由 Screen **继承**，AIChatScreen
并未声明。特定加载时机（TLM NetworkHandler.init 在 DeferredWorkQueue 中
触发 AIChatScreen 类加载）下，Mixin 对继承方法的目标解析失败 →
`InvalidInjectionException: could not find any targets matching 'm_7379_'`
（required 配置 → 启动即 FATAL 崩溃）。mixin 类自 1.5.11 起字节未变，属
环境/加载时机敏感的潜在缺陷，本次启动暴露。

### 修复
- `@Inject(method = "m_7379_", ..., require = 0)`：目标解析不到时只警告不崩溃；
- 对话静止恢复不依赖该注入——`DialogueFreezeManager` 已有 5 分钟无活动
  自动解冻 + 登出/世界保存/停服兜底，功能不受影响；
- 排查确认：其余 heartfelt mixin 均注入目标类的**声明方法**，无同类隐患。

### 验证
- heartfelt 编译 0 错误；heartfelt_connection-1.5.14.jar 打包安装两个 mods 目录；
- 启动进主界面无崩溃（AIChatScreen 加载不再触发 MixinApplyError）。

### 补充修复（同版 1.5.14，二次启动报"corrupt or misconfigured toml"）
- 根因：mods.toml 的 description 是 TOML 基础字符串，内部历史文本混有大量**未转义
  ASCII 双引号**（v1.4.9"设女儿后再解除"、v1.5.10"玩家非潜行攻击"、v1.5.12
  "上次触发的绝对游戏日"等）——严格 TOML 解析直接失败（1.5.13 恰好因引号
  已转义而幸免，1.5.12/1.5.14 未转义即被 Forge 拒绝加载）。
- 修复：description 内全部未转义 `"` 转为 `\"`（18 处），tomllib 严格校验通过；
  promaid mods.toml 同步校验合法。

---

## 1.5.13(2026-08-14) —— 调整器补全纪念日/promaid 联动测试

- 状态行（第 2 行 flags）追加「纪念日第N天·达成M」：基准=告白/初遇，
  已达成取 promaid 游标（maid_smart_anniv_mark）与 heartfelt 上次触发日的较大者
  （同一女仆 persistentData 直读，零 promaid 依赖）。
- 新增测试按钮（第 2 页，面板高度上调 21px）：
  - 「首见7天前/anniv=7」：首见=7 天前 → 7 天里程碑立即到期（不用推时间）；
  - 「纪念日重置/anniv=reset」：清防重触发标记 LAST_ANNIVERSARY_DAY（可重复测试）；
  - 「时间+3天/time+3d」「时间+7天/time+7d」：快进（照 time+1d 的 /time add 24000）。
- 「调试信息」按钮输出追加纪念日里程碑 + Promaid 联动状态：基准日/上回触发日/
  达成游标/临近游标/下个里程碑距触发天数（maidDebug）。
- 验证：heartfelt 编译 0 错误；1.5.13 jar 打包安装两个 mods 目录；
  游戏内：首见=今天 → 时间+7天 → 状态行「纪念日7天」+ promaid 手册面板同步；
  首见7天前 → 立即触发 7 天里程碑（heartfelt 说话 + promaid 记忆/情绪/核心记忆沉淀）。

---

## 1.5.12(2026-08-14) —— 纪念日里程碑比较修复 + Promaid 纪念日联动

### 根因
`FamilyInteractionManager.milestoneDue` 把"上次触发的绝对游戏日"(last,如 5007)
与"相对里程碑天数"(mark=7/30/100/365)直接比较：
- 首次触发后 last 被写成当天的绝对游戏日 → `last < mark` 恒 false；
- 只要告白/首见发生在开局 24 天之后(baseDay > 23)，30/100/365 里程碑
  永远无法触发——纪念日系统只在"开局即确立关系"时才完整工作。

### 修复
- 改为与该里程碑对应的**绝对触发日**比较：`last < baseDay + mark`——
  大 baseDay 场景(世界运行很久才告白/首见)30/100/365 恢复正常。
- 与 Promaid 侧新增的纪念日联动配合，双方检测结果一致：
  - Promaid `RelationshipMemoryAdapter.checkAnniversary` 读同一批 heartfelt NBT
    key(heartfelt_ev_first_meet / heartfelt_confession_at / heartfelt_ev_last_anniversary_day)；
  - 里程碑达成 → 写关系记忆(fact:anniversary_state:N, relationship_event, daily,
    salience 9 永久) + 情绪脉冲(AffectManager.onAnniversaryDay) + 气泡；
  - 临近 3 天 → 期待记忆 + 情绪(onAnniversaryApproaching) + 主动提前提起
    (heartfelt 无临近概念，此项为 Promaid 新增价值)；
  - heartfelt 已触发说话(last==day)则不重复开口；heartfelt 因 LLM 关/配额/距离
    未触发时 Promaid 补位说话。
- Promaid 走独立游标(maid_smart_anniv_mark / _app)，与 heartfelt 游标互不干扰；
  新配置项"纪念日联动"(memory.heartfeltAnniversary)默认开，可一键关停。

### 验证
- heartfelt 编译 0 错误;heartfelt_connection-1.5.12.jar 打包安装。
- Promaid 编译 0 错误;promaid-1.0.0.jar(1.5.312)重新打包安装。
- 游戏内:mem=confession 置告白日 → /time add 24000×N 推天数 → 达成时
  heartfelt 说话 + Promaid 记忆出现"纪念日"+ 情绪上涨;大 baseDay 场景
  (世界运行很久后告白)30/100/365 里程碑同样触发。

---

## 1.5.11(2026-08-14) —— 修复"空手/方块连打都打不到女仆"(Better Combat 冷却)

> 用户实测:空手或拿方块时完全无法攻击女仆(不是打了没伤害,是连打都打不到);
> BC 下界合金剑能打到但无伤心提示。

### 根因(字节码级确认)
- Better Combat 挥击后 `setMiningCooldown(完整攻击冷却 1 秒+)` 设置
  `Minecraft.missTime(f_91078_)`;
- `Minecraft.doAttack(m_202354_)` 开头 `if (missTime > 0) return false`——
  冷却期间所有原版攻击路径(空手/方块)【连攻击包都不发】,表现就是
  "连打都打不到";
- BC 剑走自己的 C2S_AttackRequest 路径(绕过 doAttack)→ 能打。

### 修复
- 新客户端 mixin `MaidAttackCooldownMixin`(@Mixin Minecraft,doAttack HEAD):
  准星目标是女仆且 missTime>0 → 清零(missTime 豁免,仅女仆);
  配合 v1.5.10 空手豁免 ÷5,空手攻击女仆恢复原版手感。
- "无伤心提示":伤害惩罚需【冻结层女仆(妻子/恋人/女儿)】+ 30 秒窗口内
  3 次才触发——若打的是普通女仆或次数不足,不会提示(设计如此)。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.11.jar(193199 字节,mixins 校验 24 项)
  打包安装到两个 mods 目录。
- 游戏内:挥剑后立刻空手打女仆 → 能命中(受击反馈 + 伤害);空手 1.0 可见、
  武器 ÷5 ≤2;打冻结女仆 30 秒 3 次 → 伤心提示/赌气。

---

## 1.5.10(2026-08-14) —— 空手攻击女仆恢复可见(驯养革新查证)

> 用户提示:可能与驯养革新有关。查证确认:
> - 驯养革新 CommonProxy.onLivingDamage 有"防误伤"保护:玩家非潜行攻击
>   自己的宠物(TamableAnimal,女仆天然命中)→ 取消伤害;
> - 但 callresponse 的中性源重放会绕过该取消(重放伤害源不是玩家)→ 伤害
>   生效;空手 1.0 经 TLM ÷5 = 0.2 → 被女仆护甲吸收 → 完全无反馈,
>   看起来"空手无效、只有武器能打"。

### 修复
- `CallResponseHurtLimitMixin`:主人攻击时,若主手为空手(m_21205_().m_41619_())
  → 不 ÷5(空手 1.0 本身低伤害,无需限伤,恢复"空手能打女仆"的可见性);
  武器/其他伤害保持 TLM ÷5 封顶 2(限伤不秒杀)。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.10.jar(192182 字节)打包安装到两个
  mods 目录。
- 游戏内:空手攻击自己的女仆 → 有受击反馈/伤害(≈1.0,破甲可见);
  武器攻击 → ÷5 封顶 2(不秒杀);30 秒窗口 3 次 → 惩罚照常。

---

## 1.5.9(2026-08-14) —— 修复主人攻击限伤失效(根因:爱憎分明中性源重放)

> 用户反馈:① 原版可以空手攻击女仆,当前只能武器攻击;② Better Combat
> 影响下限伤机制不生效。

### 根因(深度调查,Better Combat 无罪)
- Better Combat 1.9.0 走标准攻击链(Player.attack → hurt → Forge
  LivingHurtEvent 正常触发),不拦截空手攻击、不替换伤害;
- 真正的根因:callresponse(爱憎分明)的 `MixinEntityMaid.example$onHurtHead`
  在【主人攻击】时,用 `DamageSources.generic()` 中性源重放伤害并取消原
  hurt——TLM 原版限伤(÷5 封顶 2)、promaid 1% 上限(@Redirect 在
  EntityMaid.hurt 内)、heartfelt 伤害惩罚(LivingHurtEvent 要求玩家源)
  全部被绕过。仅主人受影响,陌生人限伤正常。
- 空手攻击:机制上一直有效(1 点伤害);感知"无效"是因为 ① 主人空手被
  重放为全额但观感弱;② promaid 1% 上限把非主人空手伤害压到 ≈0.2;
  ③ Better Combat 挥击后攻击冷却卡顿。

### 修复
- 新 mixin `CallResponseHurtLimitMixin`(@Pseudo + 字符串 target
  callresponse 的 MixinEntityMaid,@ModifyVariable example$onHurtHead 的
  amount 参数,require=0 防升级崩服):主人攻击时伤害改为 TLM 原版语义
  (÷5 封顶 2)→ callresponse 重放的就是限伤后的值;
- 新 mixin `EntityMaidHurtRecordMixin`(@Mixin EntityMaid,hurt HEAD):
  记录"玩家攻击女仆"(maid→player+tick);
- `PlayerHarmPenaltyManager.resolvePlayer`:中性源事件(generic)在窗口内
  查攻击记录还原玩家身份 → 伤害惩罚重新生效。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.9.jar(191973 字节,mixins 校验 23 项)
  打包安装到两个 mods 目录。
- 游戏内:主人用武器打冻结女仆 → 单次伤害 ≤2(不再秒);30 秒窗口内 3 次 →
  惩罚触发(心情/赌气坐下);空手攻击有伤害(主人 0.2 左右,原版语义)。

---

## 1.5.8(2026-08-14) —— 对话安全区:告白与 Alt+J 对话前必须先确认无敌对生物

> 用户需求(原版心契誓约缺失、不合理处):女仆主动告白、玩家按 Alt+J 打开
> 对话面板,都需先确认周围一定范围内没有敌人才能进行——至少先把敌人打掉。

### 实现
- `DialogueDispatcher.isSafeArea(player, maid, radius)` 公共检查:
  玩家与女仆周围 radius 内均无敌对生物(Monster)+ 女仆不在战斗中;
- **女仆主动告白前摇**:attemptConfessions 威胁检查改用 isSafeArea
  (原只查女仆周围,现加玩家周围;半径用 confession.threatRadius 48);
- **Alt+J 对话面板**:新 mixin `AIChatOpenSafetyMixin`(@Mixin TLM
  OpenMaidAIChatMessage,注入服务端私有 handle HEAD cancellable)——玩家与
  目标女仆周围 dialogueSafeRadius(默认 32)内有敌人 → 取消打开面板
  (客户端不会弹 AIChatScreen)+ 系统消息"附近有敌人……还是先把它们解决掉,
  再来好好说话吧。";
- 配置:`dialogue.dialogueSafeRadius`(默认 32,8-128)。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.8.jar(189358 字节,mixins 校验 21 项)
  打包安装到两个 mods 目录。
- 游戏内:旁边有僵尸/骷髅 → Alt+J 按不出面板并收到红色提示;打掉后正常打开;
  女仆主动告白同样只在安全区触发。

---

## 1.5.7(2026-08-14) —— 恢复调整器的选择性静止(与 Alt+J 共用机制)

> 用户要求恢复:调整器打开时同样触发选择性静止(其他生物定住,玩家与目标
> 女仆可动),与 Alt+J 对话面板共用 DialogueFreezeManager。

### 实现
- `AdjusterManager.openGui` 恢复 `DialogueFreezeManager.startFreeze(player, maid)`
  (幂等,刷新不重建);
- `AdjusterScreen.onClose` 恢复发送 close 包(通知服务端恢复);
- `AdjusterActionPacket.handle` 恢复 close 特判(找女仆之前处理,玩家走远
  也能恢复)。
- 机制与 Alt+J 完全共用:一个玩家一个会话,幂等;关闭任一界面即恢复;
  超时/登出/世界保存/服务器停止/实体重载残留兜底不变。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.7.jar(187749 字节)打包安装到两个
  mods 目录。
- 游戏内:打开调整器 → 周围怪/动物/其他女仆定住,玩家与目标女仆可动;
  ESC 关闭 → 恢复;Alt+J 对话面板同样生效,两者互不冲突。

---

## 1.5.6(2026-08-14) —— 死亡哀悼改为死亡调侃(与复活机制自洽)

> 用户指出:玩家是可以复活的,且主人死亡后女仆会立刻传送重生点——女仆在
> 重生点看着复活的玩家"哀悼 1 天拒绝互动"非常矛盾。建议改为调侃或其他设计。

### 设计修正
- **玩家死亡不再触发哀悼窗口**——改为死亡瞬间的【调侃/关心】反馈
  (一次文本 + 女仆气泡,无状态窗口),按关系分档:
  - 妻子:"叉着腰看你复活回来,又气又好笑:'主人啊主人,你要是再这么乱来,
    我可要生气了……来,先让我看看伤着哪儿了没有。'"
  - 女儿:"眼眶红红的,扑过来抱住你:'爸爸!吓死我了!你答应我,下次不许这样了!'"
  - 恋人:"轻轻叹了口气,替你拍掉身上的灰:'……你啊,总是这么让人担心。
    下次,让我陪着你一起好不好?'"
  - 普通女仆:"关切地看着你:'主人,您没事吧?要不要休息一下?'"
- **哀悼窗口保留为手动工具**:调整器"设哀悼"(系统消息+坐下+气泡,测试
  拒绝亲密/暂停互动/告白婉拒机制用)、"清哀悼"、到期自动恢复站姿、状态行
  "哀悼中" 全部保留;SmartIntimateTool/FamilyInteractionManager/
  MaidConfessionManager 的哀悼检查仅对手动哀悼生效。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.6.jar(187512 字节)打包安装到两个
  mods 目录。
- 游戏内:死亡 → 复活 → 聊天栏出现关系女仆的调侃/关心(按身份分档)+ 气泡,
  无任何限制窗口;调整器"设哀悼"仍可测窗口机制。

---

## 1.5.5(2026-08-14) —— 选择性静止改挂 Alt+J 对话面板(调整器不再触发)

> 用户澄清:要的是"点击 Alt+J 以后触发的时间暂停",其他场合(调整器)
> 不触发时间暂停。此前误把选择性静止做在了调整器上——纠正。

### 实现
- 移除调整器的冻结:AdjusterManager.openGui 不再 startFreeze;
  AdjusterScreen 删除 onClose close 通知;AdjusterActionPacket 删除 close 特判
  (调整器打开 = 完全正常的世界,玩家/生物照常动)。
- `AdjusterFreezeManager` 改名迁入 `dialogue/DialogueFreezeManager`
  (机制不变:非玩家、非目标女仆的 Mob setNoAi,幂等/补冻/超时/登出/
  世界保存/服务器停止/重载残留兜底)。
- 新 C2S `ChatFreezePacket(maidUuid, freeze)`;
- `AIChatScreenDramatizeMixin` 扩展:
  - init(TAIL)→ 发 ChatFreezePacket(true):打开 Alt+J 面板 → 选择性静止
    (其他生物定住,玩家与对话女仆可移动);
  - onClose(HEAD)→ 发 ChatFreezePacket(false):关闭 → 恢复。
- TLM 的 AIChatScreen 本身 m_7043_=false(不暂停)——选择性静止由服务端
  机制接管,玩家可在对话中自由移动。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.5.jar(186873 字节)打包安装到两个
  mods 目录。
- 游戏内:alt+J 打开对话面板 → 周围怪/动物/其他女仆定住,玩家与对话女仆
  可移动;ESC 关闭 → 恢复。调整器打开 → 世界完全正常(不冻结)。

---

## 1.5.4(2026-08-14) —— 思慕/破裂可见化(不再"看不出效果")

> 用户反馈:思慕一天和破裂一天看不出效果。

### 根因(调查确认)
- **思慕**:maidmarriage 的可见思慕效果(每 20 tick 检测 isLongingForInteraction
  → 玩家 5 格内冒心形粒子 + 思慕循环对话气泡)由 `mood_data.lastInteractionDay`
  驱动(距今 ≥3 天);而调整器"思慕1天"调 forceLonging 只改
  `pregnancy_data.lastRomanceDay`(仅影响对话变量池)——**两套数据脱节**,
  思慕判定永远不成立,自然无效果。
- **破裂**:EVENT_BREAKUP_COUNT 是事件历史计数,只注入 LLM 提示词(Shared
  History),无即时可见反馈。

### 修复
- `MaidMarriageCompat.setLongingInteraction(maid, gameTime)`:反射构造
  MaidMoodData(保留心情字段,lastInteractionDay = 3 天前)→
  isLongingForInteraction 立即成立 → maidmarriage 自己冒心形粒子 +
  思慕对话(可见);forceLonging 顺带保留(对话变量);
- 调整器状态行新增 **"思慕中"** 标记(反射 isLongingForInteraction);
- 设置反馈:思慕 → 系统消息("她看起来有些寂寞……靠近时她会冒出心形粒子
  与思慕对话")+ 结果行"思慕 = 3 天没亲近";破裂 → 系统消息说明它影响
  对话与记忆("已记录 1 次关系破裂——她会记得这件事")。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.4.jar(181139 字节)打包安装到两个
  mods 目录。
- 游戏内:调整器"思慕1天" → 状态行"思慕中" → 靠近女仆(5 格内)→
  心形粒子 + 思慕对话气泡;破裂×1 → 系统消息说明,LLM 对话中体现历史。

---

## 1.5.3(2026-08-14) —— 哀悼可见表现(不再"看不出效果")

> 用户反馈:哀悼的效果看不出来——此前设哀悼只有状态行文字变化,
> 女仆没有任何可见表现。

### 实现
- `FamilyMourningManager.applyMourning(player, maid, ticks)` 统一入口:
  - 哀悼标记 + **玩家系统消息**(即时可见:"她安静下来,眼神有些失焦……
    整个人都沉了下去")
  - **女仆坐下**(悲伤蜷坐,原版机制,无 LLM 也生效)
  - **女仆气泡**("……主人……")
  - 悲痛 LLM 对话 / 固定文本气泡(原有)
- 入口统一:主人死亡(onPlayerDeath)与调整器"设哀悼"都走 applyMourning。
- 调整器"清哀悼":清标记 + 若无其他坐姿原因(等妈妈/伤心)则恢复站起。
- 新增 onServerTick(每 2 秒):哀悼窗口自然到期 → 清标记 + 恢复站姿。
- PromptTexts:mourningStartMessage / mourningStartBubble。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.3.jar(180325 字节)打包安装到两个
  mods 目录。
- 游戏内:调整器"设哀悼" → 立即看到系统消息 + 女仆坐下 + 气泡;哀悼期内
  亲密被拒/家庭互动暂停/告白婉拒;1 天后(或"清哀悼")自动恢复站起。

---

## 1.5.2(2026-08-14) —— 修复 maidmarriage 原版 bug:灵体不再播放 TTS 语音

> 用户反馈:小女仆变成灵体(MaidSpiritEntity)之后,语音包仍然可以播放——
> 灵魂不该有语音。

### 根因(调查确认)
- 灵体 `MaidSpiritEntity extends EntityMaid`,所有 `instanceof EntityMaid` 的
  TLM 语音入口对它全部放行;
- 语音总入口在服务端 `MaidAIChatManager.tts(TTSSite, String, String, long)`:
  远程 TTS(TTSAudioToClientMessage,实体绑定播放)与系统 TTS
  (TTSSystemAudioToClientMessage,客户端本机合成、无实体参数可拦)
  都由它发起——客户端只能拦远程路径,系统 TTS 必须服务端拦。

### 修复
- 新 mixin `MaidSpiritTtsGuardMixin`(@Mixin MaidAIChatManager,Inject tts HEAD
  cancellable):目标女仆是 MaidSpiritEntity → 取消(不发起任何语音)。
  灵体判定用反射(maidmarriage 类不在编译 classpath),类不存在不拦截;
  灵体的 LLM 文本对话不受影响(只有 TTS 静默)。
- 注册进 mixins.heartfelt.json core 段(mixins 校验 20 项)。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.2.jar(179183 字节)打包安装到两个
  mods 目录。
- 游戏内:灵体出现后与它对话(alt+J/LLM)→ 有文本气泡,无 TTS 语音;
  正常女仆语音不受影响。

---

## 1.5.1(2026-08-14) —— 修复叛变悔改:喂蛋糕无效 / 次数过高 / 清背叛后无法互动

> 用户反馈:① 拿蛋糕根本没法给叛变女仆吃;② 5 个蛋糕难度太高,正常玩家
> 很难准备;③ 清除背叛的女仆仍无法互动,不会变回正常女仆。

### 根因
1. **喂蛋糕无效**:`BetrayalRedemptionManager` 用 `Items.f_42446_` 判断蛋糕——
   经映射核对该字段实为 **MILK_BUCKET(奶桶)**(TLM 挤奶任务 MaidMilkTask 用它),
   拿蛋糕永远不匹配。
2. **清背叛后无法互动**:爱憎分明背叛时 `triggerBetrayal` 会
   `setOwnerUUID(null)` 解除认主,而它的 `resetBetrayal` **不恢复认主**——
   调整器只调 resetBetrayal,女仆无主,右键无响应。

### 修复
- `BetrayalRedemptionManager`:蛋糕改为按注册名获取
  `ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft","cake"))`,
  不依赖 SRG 字段名(避免再踩映射坑)。
- `HeartfeltConfig.REDEMPTION_FEEDS` 默认 5 → **3**(安抚 3 次即可悔改)。
- `AdjusterManager.clearBetrayal(maid, playerId)`:resetBetrayal 后**补重新认主**
  (`m_21816_` + `m_7105_(true)`)+ 站起(`m_21837_(false)`)——变回可互动正常女仆。

### 验证
- 编译 0 错误;heartfelt_connection-1.5.1.jar(178114 字节)打包安装到两个 mods 目录。
- 游戏内:设背叛 → 手持蛋糕右键 → 气泡进度反馈(喂 3 次)→ 恐惧≤90 且信任≥10
  → 悔改完成重新认主;调整器"清背叛" → 女仆立即恢复互动。

---

## 1.5.0(2026-08-14) —— 告白流程重构:前摇机制 + 跳转心契誓约告白界面 + 界面自适应

> 用户反馈:① 告白文本没有自适应,且突然铺满全屏吓人;② 应该有前摇——
> 女仆先检索威胁,无威胁则系统消息提示,然后走向玩家,走到身边才触发对话;
> ③ 触发对话应跳转到心契誓约的告白选项(源文本刚好);④ 界面形式应与
> 心契誓约一致——能看到周边环境和女仆的脸,不是全屏 GUI 突脸。

### 实现
- **告白前摇**(新 `dialogue/ConfessionApproachManager` + MaidConfessionManager 改造):
  - attemptConfessions 判定通过后不再直接拉对话框:系统消息提示
    (PromptTexts.confessionApproachHint:"她似乎有什么话,想单独对你说")→
    预写 CONFESSION_BY="maid" → 站起(坐姿会清 WALK_TARGET)→ Brain
    WALK_TARGET 走向玩家 → 记录会话;
  - 每 20 tick:刷新走向目标(跟随玩家移动)/ 取消(女仆死亡、玩家离线、
    超时默认 60s、期间出现威胁)/ 到达(≤2.5 格)→ 发 S2C 跳转包;
  - 距离 bug 修正:`m_20270_`(平方)直比 32 → `m_20280_`(真实距离)≤ 32。
- **跳转心契誓约告白选项**(新 S2C `OpenMaidMarriageConfessionPacket`):
  - 客户端反射两步:① HugStoryResumeState.remember(uuid,
    "maidmarriage:hug_menu_v2", "confession_accept_choice")(断点直达告白选项);
    ② ModNetworking.sendHugMaid(new HugMaidPayload(uuid)) → maidmarriage 自己
    开 HugActionScreen(不渲染背景、世界与女仆立绘可见、不暂停)→ 直达告白选项;
  - 反射失败静默(不崩溃);
  - **补记**:maidmarriage accept 后(completeConfession 由它写),每 40 tick
    检测 CONFESSION_BY="maid" 且无 CONFESSION_AT → 补写时间戳/心情+2/系统消息,
    heartfelt 记忆不丢。
- **废弃删除**:OpenConfessionPacket / ConfessionScreen / ConfessionResponsePacket
  / MaidConfessionManager.handleResponse(女仆主动整链);网络协议版本升 "3"
  (破坏性变更);被拒标记(CONFESSION_FAILED)由 maidmarriage 剧本接管。
- **玩家主动告白界面**(PlayerConfessionScreen)改叠加式:不渲染全屏背景
  (世界与女仆实体可见)、面板改底部半透明对话框(宽 min(340,屏宽-24) 自适应,
  文本换行随面板)、不暂停游戏;网络链路(PlayerConfessionPacket/
  ResultPacket/receiveResult)保留。
- 配置:`confession.approachEnabled(默认 true)/approachDistance(2.5)/
  approachTimeout(1200 tick)/approachSpeed(1.0)`。

### 验证
- 编译 0 错误(62 源文件);heartfelt_connection-1.5.0.jar(177735 字节)
  打包安装到两个 mods 目录。
- 游戏内:好感 192+ 女仆 → 2 分钟周期 → 系统消息"她似乎有什么想说的" →
  女仆起身走向玩家(沿途怪物不影响)→ 走到身边自动打开心契誓约告白选项
  (世界可见)→ 选"我也喜欢你" → 成恋人 + heartfelt 补记;玩家主动告白
  (心契誓约界面选表白)→ 底部半透明面板(环境可见)执行文本;小窗口文本
  不溢出。

---

## 1.4.11(2026-08-14) —— 调整器"选择性静止":其他生物冻结,玩家与目标女仆可动

> 用户需求:调整器打开时不要完全暂停(isPauseScreen 会卡住玩家),要的是
> "其他生物停止静止,但玩家和女仆可以移动";并澄清:只豁免【玩家 + 正在
> 对话的目标女仆】,其他女仆同样要冻结。

### 实现
- 新增 `item/AdjusterFreezeManager`(选择性静止):
  - 打开调整器(openGui)→ 冻结当前维度所有【非玩家、非目标女仆】的 Mob:
    `setNoAi(true)`(m_21553_,生物不执行 AI:不移动/不攻击/不寻路);
    目标女仆按 UUID 豁免,其余女仆同样冻结;
  - 关闭界面(客户端 onClose → C2S close 包,不依赖找到女仆)→ 恢复;
  - 兜底恢复:玩家登出 / LevelEvent.Save(世界保存)/ 服务器停止 /
    超时(5 分钟无活动自动恢复);
  - 补冻:会话期间每 2 秒扫描,新刷怪/召唤自动补冻(同样豁免玩家与目标女仆);
  - 防残留:实体重新加入世界时,非冻结目标的 noAI 实体一律恢复
    (崩溃/区块卸载导致的 NoAI 持久化残留自动修复,天然 noAI 实体无副作用)。
- `AdjusterScreen`:isPauseScreen 改回 false(玩家可移动);onClose 发送
  close 包通知服务端恢复冻结。
- `AdjusterActionPacket.handle`:close 动作在找女仆之前处理(玩家走远也能恢复)。
- 幂等:openGui 每次刷新调用 startFreeze,已有会话只刷新活动时间,不重建
  (生物不会一闪一动)。

### 验证
- 编译 0 错误(63 源文件);heartfelt_connection-1.4.11.jar(170190 字节)
  打包安装到两个 mods 目录。
- 游戏内:右击女仆 → 周围的怪/动物/其他女仆全部定住,玩家和目标女仆可
  自由移动;点按钮操作照常;ESC 关闭 → 生物恢复移动;等 5 分钟不操作
  自动恢复。

---

## 1.4.10(2026-08-14) —— 调整器操作即时反馈(界面内结果行)

> 用户反馈:调整器的操作除了少数有系统消息的功能外,没有任何反馈——点完
> 按钮只能靠状态行数值猜测是否生效。需要每次操作的明确结果提示。

### 实现
- `AdjusterManager.applyAction` 改返回**结果文案**(42 个 action 全部覆盖,
  如"好感 = 384""已设为妻子""已解除全部关系""已清除哀悼""时间 +1 天"
  "已标记思慕(1天)";数值类带操作后实际值);null 表示无效/关闭(不刷新)。
- S2C `OpenAdjusterPacket` 新增 result 字段(可空,boolean+utf 序列化);
  按钮点击(C2S)/命令执行后 → openGui(player, maid, result) → 客户端
  `AdjusterScreen` 在状态行下方显示**黄色结果行**(§e)。
- 布局微调:按钮区下移一行空间给结果行(按钮起点 top+58、行距 21、
  面板最小高 238),小窗口仍不重叠。

### 验证
- 编译 0 错误;heartfelt_connection-1.4.10.jar(166321 字节)打包安装到
  两个 mods 目录。
- 游戏内:点任意按钮 → 界面内黄色行即时显示操作结果,状态行同步刷新。

---

## 1.4.9(2026-08-14) —— 修复调整器"设女儿后再解除"永久覆盖

> 用户反馈:调整成女儿后再调回正常状态,无法真正解除——女儿状态被永久覆盖。
> 根因:解除关系只清了 maidmarriage 的 TaskData(child_state_data → EMPTY),
> 但 maidmarriage 的 MaidWorkManager.onMaidTick(MaidTickEvent,每 tick)会调
> tickExternalChildLifecycle 恢复女儿状态:isBornMaid 判定包含 shouldStayChild,
> 而 shouldStayChild 在 TaskData child=false 时读 persistent 残留标记
> (child_active/growth_ticks/growth_stage 任一存在即 true)→ 每 tick 从
> persistent 恢复 TaskData child=true,即"永久覆盖"。

### 实现
- `AdjusterManager.clearChildStateFully`(解除关系时调用):
  - 优先反射 maidmarriage 官方解除 API `MaidChildEntity.markAsAdult`
    (清 persistent 女儿标记 + TaskData EMPTY + 保留名字);
  - 兜底手动清 TaskData + persistent:
    child_active=false、清 growth_ticks/growth_stage/infant_carry_end_tick/
    child_tame_initialized;
  - 额外清血统 UUID(persistent mother/father/grand_parent_uuid)与 born tag
    (getTags() 可变 Set 直接 remove)——isBornMaid 的另一判定源,残留仍会
    被视为 born maid(重进世界时 SoulSlabChildBridge 还会补回血统标记)。

### 验证
- 编译 0 错误;heartfelt_connection-1.4.9.jar 打包安装到两个 mods 目录。
- 游戏内:设女儿 → 等几秒 → 解除关系 → 状态行应回到"普通"且保持,
  重进世界/切换维度后仍是普通女仆。

---

## 1.4.8(2026-08-14) —— 调整器界面自适应(小窗口不再偏左超屏)

> 用户反馈:界面最上面一行文字偏左超出屏幕。根因:面板固定宽 340px——
> 窗口/GUI 缩放较小时(854×480 窗口在 GUI 缩放 2 下虚拟宽 427、虚拟高 240),
> 面板超出屏幕,左边缘变负数,文字从屏幕外开始绘制。

### 实现
- `AdjusterScreen` 面板宽/高改为自适应(参照手册 v1.5.71 小窗口自适应的教训):
  - 宽:`min(340, 窗口宽-24)`(最小 120);高:`min(244, 窗口高-24)`
    (最小 232,保证 7 行按钮 + 翻页不重叠);
  - 按钮列宽/按钮宽随面板收窄(colW = (panelW-16)/4,btnW = colW-4);
  - 标题/状态行/翻页全部相对面板坐标(标题居中于面板中心)。

### 验证
- 编译 0 错误;heartfelt_connection-1.4.8.jar 打包安装到两个 mods 目录。
- 游戏内:854×480 窗口(任意 GUI 缩放)打开调整器,面板完整在屏内。

---

## 1.4.7(2026-08-14) —— 调整器界面与手册一致:打开时暂停世界

> 用户观察:手册打开时世界不再加载(单机暂停);调整器界面(1.4.6)却
> 不暂停,与手册不一致。查证:手册未覆写 isPauseScreen(m_7043_),继承
> Screen 默认 true → 单机打开时世界静止;1.4.6 的 AdjusterScreen 显式
> 返回了 false。

### 实现
- `AdjusterScreen.m_7043_()` 改为返回 true——打开调整器时世界暂停
  (生物/方块静止),与手册、告白界面等一致;ESC 关闭后观察反应。

### 验证
- 编译 0 错误;heartfelt_connection-1.4.7.jar 打包安装到两个 mods 目录。

---

## 1.4.6(2026-08-14) —— 调整器改为 GUI 界面(与手册同格式)

> 用户反馈:聊天栏按钮式"完全没法操作",要求改成与 Promaid 手册一致的形式。
> 改为纯客户端 Screen 界面(GUI),替代聊天栏按钮菜单。

### 实现
- 客户端 `client/AdjusterScreen`(纯 Screen,无 Menu 容器,与手册同模式):
  - 暗色面板 + 标题(调整器 · 女仆名)+ 2 行实时状态(好感/心情/信任/恐惧、
    关系/阶段/奶/哀悼/伤心/背叛标记,§ 着色);
  - 4 列按钮网格:第 1 页基础(好感/关系/心情/情绪/阶段/记忆/清理 25 个)、
    第 2 页高级(场景模拟/数值精度/实用/关系细节 27 个),底部翻页;
  - isPauseScreen=false(不暂停,边操作边观察世界)。
- 网络(`HeartfeltNetwork` 追加 id 5/6,不 bump 协议版本):
  - S2C `OpenAdjusterPacket`:服务端组装状态快照下发;已打开同一女仆时
    `screen.refresh` 原地刷新,否则新开界面;
  - C2S `AdjusterActionPacket`(maidUuid + action):服务端 applyAction →
    成功后回发刷新,界面不关闭、可连续点。
- `AdjusterManager`:聊天栏菜单(openMenu/openPage2/button/buttonLine)全部
  移除,新增 `openGui`(状态快照 + 发包);applyAction 新增 `time+1d`
  (服务端执行 /time add 24000,替代原直连命令按钮)。
- `AdjusterInteractHandler`/`HeartfeltCommand` 改接 openGui;命令保留为
  底层通道(执行后同样刷新 GUI)。
- 修正:OpenAdjusterPacket 的 int 读写改用 Netty 不混淆的 writeInt/readInt
  (m_130079_ 实为 writeNbt、m_130242_ 为 readVarInt,不能混用);
  标题 drawCenteredString 用 Component 重载(m_280430_)、状态行 drawString
  用 FormattedCharSequence(m_280364_ + Component.m_7532_)。

### 验证
- 编译 0 错误(62 源文件);heartfelt_connection-1.4.6.jar(163853 字节,85 条目)
  打包安装到两个 mods 目录(根 + PCL 隔离),jar 内含 AdjusterScreen/
  OpenAdjusterPacket/AdjusterActionPacket。
- 游戏内验证:手持调整器(紫光)右击女仆 → GUI 弹出(状态实时)→ 两页按钮
  逐个点击,界面原地刷新不关闭;ESC 关闭;命令 /heartfelt adjust 仍可用。

---

## 1.4.5(2026-08-14) —— 修复调整器右击无效果 + 附魔光泽特效

> 用户反馈:拿着调整器右击女仆没有任何效果(1.4.3/1.4.4 的菜单/命令都实现了,
> 唯独漏了右击事件入口);并希望物品有附魔特效更特殊一点。

### 实现
- 新增 `item/AdjusterInteractHandler`(补漏):`PlayerInteractEvent.EntityInteract`
  监听——手持调整器右击女仆 → `event.setCanceled(true)`(阻止 TLM/原版女仆交互
  吃掉点击)→ 服务端 `AdjusterManager.openMenu` 打开聊天栏按钮菜单。
  (@Mod.EventBusSubscriber FORGE bus;与 BetrayalRedemptionManager 的喂蛋糕
  监听兼容——对方已检查 isCanceled)
- `AdjusterItem` 覆写 `m_5812_`(isFoil)返回 true——物品带附魔紫色光泽。

### 验证
- 编译 0 错误(61 源文件);heartfelt_connection-1.4.5.jar(157406 字节,
  mixins 校验 19 项)打包安装到【两个】mods 目录:
  D:\.minecraft\mods(无隔离)+ D:\.minecraft\versions\1.20.1-Forge_47.4.21\mods
  (PCL 版本隔离,实际加载位置);mods.toml 经 tomllib 解析验证有效。
- 注:PCL 曾把运行中被替换的 1.4.4 标记 corrupt 生成 .bak——已清理,不影响 1.4.5。
- 游戏内验证:手持调整器(有紫光)→ 右击女仆 → 菜单弹出 → 按钮逐个生效。

---

## 1.4.4(2026-08-14) —— 调整器扩展:场景模拟/数值精度/实用/关系细节(分页)

> 需求:调整器还能放什么功能调整?用户四组全选。菜单分页:
> 第 1 页保留原内容(好感/关系/心情/情绪/阶段/记忆/清理 + 「更多▼」),
> 第 2 页为新增四组 + 「返回▲」;按钮带页码前缀(p2.xxx),执行后自动刷新回所在页。

### 实现
- 分页(AdjusterManager.openMenu 加 page 参数;HeartfeltCommand 解析
  page=N 翻页 / p2. 前缀):
  - 翻页按钮直接切换页面,无状态设计;时间+1天 按钮直连 /time add 24000
    (零服务端逻辑)。
- A 场景模拟(测机制流程):
  - 设背叛:写爱憎分明 IsBetraying NBT(其 isBetraying 的持久判定源)+
    m_21816_(null) 解除认主 + 记 REDEMPTION_BETRAYED_AT —— 与 triggerBetrayal
    等价,可测背叛隔离/悔改全流程;
  - 设哀悼/设伤心:写 MOURNING_UNTIL / HURT_UNTIL = 今天 + 1 天;
  - 等妈妈:WAITING_MOTHER + 坐下(模拟魂符收妈,测 ChildGuardManager 恢复线);
  - 产后1天:反射 PregnancyData.completeBirth(now, 24000) 写回(产后窗口判定);
  - 丧子哀悼:写 maidmarriage_child_loss_grief_active。
- B 数值精度:信任/恐惧绝对值(=100/=0,差值调用 addTrustFloat/addFearFloat)、
  饥饿(hungerSet 10/50/90)、心情全档(=5/=10/=20)。
- C 实用:调试信息(直接调 HeartfeltDebugApi.maidDebug 全量状态)、拉回
  (m_6034_ 瞬移)、坐/站(m_21837_)、认主/解除认主(m_21816_ + m_7105_)。
- D 关系细节:告白失败(CONFESSION_FAILED + 时间戳)、心碎(HEARTBROKEN_AT)、
  思慕1天(反射 PregnancyData.forceLonging)、破裂×1(EVENT_BREAKUP_COUNT)。
- MaidMarriageCompat 补 setPostpartum / setLonging(反射 record 方法 + setAndSyncData)。

### 验证
- 编译 0 错误(60 源文件);heartfelt_connection-1.4.4.jar(80 条目)打包安装,
  旧版 1.4.3 入 backup。
- 游戏内建议:第2页逐个验证——设背叛后喂蛋糕走悔改;设哀悼后亲密被拒;
  设伤心后女仆坐下赌气;等妈妈后站起;产后1天后特殊奶语境;调试信息显示
  全量状态;时间+1天 后每日重置/纪念日;思慕1天 后互动渴望文本。

---

## 1.4.3(2026-08-14) —— 新增测试工具:调整器

> 需求:这类功能的测试相当困难——设计一个形象与纸相同、名为「调整器」的物品,
> 右击女仆即可调整好感度与相关数值、调整关系等各方面内容,方便测试。
> 仅创造模式物品栏与指令可获取,无合成、无自然生成。

### 实现
- 物品(`item/AdjusterItem` + `item/HeartfeltItems`):
  - 注册 `heartfelt_connection:adjuster`,堆叠 1;模型复用原版纸贴图
    (`minecraft:item/paper`,零贴图资源);tooltip「右击女仆打开调整菜单」;
  - 创造栏注入原版「工具与杂项」页(BuildCreativeModeTabContentsEvent);
    无合成配方 → 仅创造栏 + `/give @p heartfelt_connection:adjuster` 获取。
- 交互(用户选定:聊天栏按钮式,零客户端代码):
  - 右击女仆 → `AdjusterManager.openMenu` 发送按钮菜单(标题 + 实时状态行 +
    按钮行),每按钮为 ClickEvent(RUN_COMMAND)跳 `/heartfelt adjust <uuid> <action>`;
  - `HeartfeltCommand`(`command/HeartfeltCommand`,RegisterCommandsEvent):
    守卫=必须手持调整器(物品即权限,不设 op 限制),64 格内按 UUID 找女仆,
    执行 `AdjusterManager.applyAction` 后自动重发菜单刷新数值。
- 调整能力(全部走现有 compat/原生 API,不改原模组):
  - 好感:TLM getFavorability/setFavorability(+10/-10/=64/128/192/384);
  - 关系:恋人(completeConfession)/妻子(setMarried)/女儿(setChildState)/
    解除(clearMarriage+resetConfession+clearChildState)——反射构造
    MarriageData/ChildStateData record 写 TaskData;
  - 心情:addMood(=15/=25 走差值);信任+20/恐惧-20(addTrustFloat/addFearFloat);
  - 成长阶段:幼年/少女/成年——同步 maidmarriage growth_stage/growth_ticks +
    heartfelt CHILD_STAGE(可触发成长事件);奶=3(测特殊奶三档文本);
  - 记忆:首见=今天/告白=今天(测纪念日);清理:清伤心/清哀悼/清背叛。
- 基建:ReflectUtil 补 `newInstance`(record 构造器)/`staticField`(读 EMPTY);
  MaidMarriageCompat 补 `setMarried/clearMarriage/setChildState/clearChildState`。
- 构建:build_heartfelt.py required 校验新增 assets 与物品/命令类;
  mods.toml description 追加 v1.4.2/v1.4.3(顺带修复 v1.4.2 构建时被
  PowerShell 写坏的 description 行——以 1.4.1 backup jar 为准重建)。

### 验证
- 编译 0 错误(60 源文件);heartfelt_connection-1.4.3.jar(80 条目:4 新类 +
  assets 资源)打包安装,旧版 1.4.2 入 backup。
- 游戏内建议按序验证:创造栏可见(纸外形)→ 右击女仆出菜单 → 好感四档 →
  恋人/妻子/女儿(看 RelationshipExemption 判定与状态行)→ 心情/信任恐惧 →
  阶段切换触发成长事件 → 奶=3 喝奶看三档文本 → 首见/告白=今天等纪念日 →
  清伤心/哀悼/背叛 → 不手持调整器执行命令被拒。

---

## 1.4.2(2026-08-14) —— 无 LLM 完整运作:主动对话类功能降级固定文本

> 用户设计原则"无 LLM 也必须完整运作"的落地延续。审查发现:maidmarriage 本身
> 零 LLM 集成(全固定脚本);TLM 在 LLM 关闭/未配置站点/DeepSeek 缺 key 时只发
> 红色报错提示。补丁此前仅惩罚/特殊奶/成长/悔改有固定文本兜底,主动对话类
> (家庭互动/广播/哀悼/纪念日)在 LLMEnabled=true 但站点未配置时会刷红色报错。
> 本次统一降级:LLM 不可用或配额满 → 发固定文本气泡,玩家看到"她在说话"。

### 实现
- DialogueDispatcher:
  - 新增 isLLMReady(maid):与 TLM MaidAIChatManager.chat() 拦截链一致——
    LLMEnabled 总开关 + 当前站点存在且启用(反射 public getLLMSite,
    定义在父类 MaidAIChatData)+ DeepSeek 未填 SecretKey 视为不可用;
  - chatWithQuota 新增带 fallbackBubble 的重载:不可用/配额满 → 发固定文本
    气泡并返回 false;单参旧重载保留(伤害惩罚/悔改已有固定文本,不重复)。
- 降级文本(PromptTexts 新增 v1.4.2 段):母女/父女(按成长阶段)/成年女儿照顾/
  成长事件/暗恋感慨/纪念日(按里程碑)/关系广播(按关系标签)/哀悼(女儿/妻子)/
  演绎剧情不可用提示。
- 调用点:FamilyInteractionManager(5 处)、RelationBroadcastManager(降级同样
  记去重,防扫描刷屏)、FamilyMourningManager、DramatizePacket handler
  (失败 → 玩家系统消息提示)。
- 保持不动:PlayerHarmPenaltyManager / BetrayalRedemptionManager 的 LLM 调用
  (已有系统消息+气泡兜底);LLMEnabled=false 时家庭互动/广播整体禁用语义不变。

### 验证
- 编译 0 错误;heartfelt_connection-1.4.2.jar(71 条目,18 mixin 校验通过)
  打包安装,旧版 1.4.1 入 backup。

---

## 1.4.1(2026-08-14)——玩家伤害惩罚(关系不破坏,但有后果)

> 用户观察:冻结层女仆(妻子/恋人/女儿)关系不会被破坏,但玩家伤害她们也
> 没有任何可见后果——不公平,也不像 galgame。本次补上"后果"。
> 设计原则(用户确认,应成为整个 mod 的约束):
> ① 无 LLM 也必须完整运作;② 误伤不能累积惩罚。

### 设计
- **误伤豁免**:仅玩家主动攻击(近战/远程的 source.getEntity 都是玩家;爆炸/环境不算)
  计入判定;且采用"30 秒窗口内对同一女仆累积 N 次(默认 3)伤害才确认故意"——
  打怪误伤 1-2 下不算,偶尔误伤不会累积。
- **无 LLM 降级**:惩罚机制本体(心情/伤心窗口/赌气坐下/系统消息/气泡)全部是
  原版机制;LLM 伤心对话只是可选增强(`harmPenalty.llmReaction`,无 LLM/配额满
  自动跳过,固定文本已兜底)。

### 实现(`combat/PlayerHarmPenaltyManager`,已注册)
- `LivingHurtEvent` 收集伤害(仅冻结层女仆 + 玩家来源 + 窗口累积);
- 确认故意后:
  - 心情扣减(默认 5,每日同一女仆上限 2 次防刷);
  - 伤心窗口 `heartfelt_hurt_until`(默认 1 游戏日):窗口内**坐着赌气**
    (坐下不移动不工作)、家庭互动(母女/父女/暗恋感慨)暂停;
  - 系统消息("她怔怔地看着你,眼眶红了……")+ 女仆气泡("好痛……主人为什么要这样对我……");
  - 可选 LLM 伤心对话("用颤抖的、带着哽咽的声音……绝不是讨好")。
- `AftermathPrompt` 新增"被主人打伤"档(优先级:哀悼 > 打伤 > 心碎 > 被拒 > 悔改):
  委屈、疏远、需要道歉与哄,但明确"依然爱他、不会离开"。
- 配置:`harmPenalty.triggerHits/moodDrop/dailyCap/feelingTicks/llmReaction`。

### 验证
- 双模组编译 0 错误;heartfelt_connection-1.4.1.jar(71 条目,18 mixin 校验通过)
  打包安装,旧版 1.4.0 入 backup。

---

## 1.4.0(2026-08-14)——游玩反馈修复:魂符孤儿/技能保留/对话暂停/LLM 剧情演绎

> 用户游玩时遇到的难绷 bug 集合(部分反馈原作者被拒修),全部在 heartfelt 侧落地。

### Bug1 魂符收妈妈 → 女儿不再"落地变普通女仆"
- 原版:收掉抱着女儿的妈妈时,maidmarriage 只把孩子放地上(SoulSlabChildBridge.onToItem
  → releaseBeforeMaidTransform),女儿变"普通女仆":能走、能配置、能用孩子任务,
  但对话仍说"太小不能走路"——行为与文本脱节。
- 修复(`compat/ChildGuardManager`,监听 MaidAndItemTransformEvent.ToItem/ToMaid):
  妈妈被收走时女儿**强制坐下 + 写 heartfelt_waiting_mother 标记 + 系统消息**;
  坐下后 TLM 不移动、MaidWorkManager 按 SITTING 不工作——行为与"太小不能走路"一致。
  妈妈放回来(ToMaid)时清除标记、解除坐下,INFANT 由原版自动重新抱起。
- 用户选择方案:女儿强制坐下不能动(而非一起收进魂符)。

### Bug2 女儿吃醋/爱恋父亲——确认已由冻结层覆盖(无需新代码)
- `EmotionDotingIsolationMixin`(isFrozen 不攻击情敌)、`EmotionFear/TrustExemptionMixin`
  (女儿信任恐惧完全冻结)、`EmotionDevotedMixin`(女儿自动忠诚)——原版无补丁时
  "孩子=普通女仆"会吃醋爱恋,heartfelt 冻结层已隔离。

### Bug3 孩子专属技能成年后保留(用户选择)
- 原版:`MaidWorkManager.isChildWorkMaid = shouldStayChild`——女儿成年后附魔/酿药等
  孩子任务被 isHidden 隐藏,技能"消失"。
- 修复(`MaidWorkManagerSkillRetentionMixin`):出生女仆(maidmarriage_born_maid /
  有血缘 UUID)无论成年与否都保留技能;普通女仆仍不显示。
- 附带:`ensureDefaultFavorability` 防重置保护——已高于 64 的好感不会被覆盖为 64。

### Bug4 对话期间世界静止(原作者拒绝修)
- 原版 `HugActionScreen.isPauseScreen()` 显式 false——对话期间生物与方块继续活动。
- 修复(`HugActionScreenPauseMixin`,client 段):改为 true,单机对话时世界暂停;
  heartfelt 自己的告白屏(PlayerConfessionScreen/ConfessionScreen)同步加 isPauseScreen=true。
- 注意:isPauseScreen 只在单机生效(服务器无法暂停)。

### LLM 剧情演绎(alt+J 对话面板)
- 原版固定剧本(告白/丧子/纪念日)体验差;配置 LLM 后应让 LLM 根据共同记忆
  【沉浸式演绎剧情】,脱离既定文本。
- 实现(`AIChatScreenDramatizeMixin`,client 段):TLM 的 AIChatScreen(alt+J 打开)
  init 末尾加"✨ 演绎剧情"按钮 → C2S `DramatizePacket` → 服务端
  `DialogueDispatcher.dramatize`(事件历史 Shared History + 演绎提示词 →
  女仆 LLM 演绎,走全局配额)→ 回复出现在对话面板。
- 演绎提示词:要求第一人称叙述+台词、贴合关系阶段、不提"演绎/剧本"等系统词。

### 验证
- 双模组编译 0 错误;heartfelt_connection-1.4.0.jar(70 条目,18 mixin 校验通过,
  core 10 + client 3)、promaid-1.0.0.jar(238 条目)打包安装,旧版 1.3.2 入 backup。
- 新类全部在 jar 内验证 OK。

---

## 1.3.2(2026-08-14)——特殊奶文本按次数分级(妈妈三档口吻)

> 用户要求:考虑到特殊奶的获取途径(产后哺乳期女仆产出)与玩家喝奶的行为,
> 妈妈(妻子)应有专属文本;且喝的次数越多文本反馈越不同,最多三档。

### 改动
- `PromptTexts.specialMilkMessage(maidName, count, isMother)` 按累计次数分三档
  (≥3 封顶):
  - **妈妈(妻子)口吻**:第 1 次"那、那是从我这里来的奶……你真的喝下去了?"(害羞)/
    第 2 次"要是喜欢,我以后都给你留着……只给主人一个人喝"(默许)/
    第 3 次起"主人是我的人了……连这个也只给主人一个人喝哦"(当成日常,骄傲甜蜜);
  - **非妻子通用口吻**:好奇 → 留意 → 见怪不怪,三档对应。
- `specialMilkMaidBubble(maidName, count)`:气泡同步按次数分级,并修掉旧版
  `{maid}` 字面量占位符(系统消息/气泡不做模板渲染,直接嵌入女仆名)。
- `SpecialMilkBucketMixin`:女仆侧计数先 +1 再取档;妻子(married)判定走妈妈口吻。

### 验证
- 双模组编译 0 错误;heartfelt_connection-1.3.2.jar(65 条目,15 mixin 校验通过)
  打包安装,旧版 1.3.1 入 backup。

---

## 1.3.1(2026-08-14)——特殊奶增强(解负面 + 生命回复 I + 记忆)

> 用户反馈:maidmarriage(心契誓约)的特殊奶(special_milk_bucket,产后哺乳期
> 产出)对玩家饮用没有任何效果。heartfelt 侧补齐(不碰原模组)。

### 现状查明
- 特殊奶注册:`ModItems.SPECIAL_MILK_BUCKET` = `SpecialMilkBucketItem extends MilkBucketItem`
  (仅加 tooltip,饮用行为完全继承原版牛奶);
- 获取:产后恢复期女仆喂普通牛奶 → 10 分钟冷却 → 给玩家一桶特殊奶;
- 原版用途:右键**女仆** → 缩短女儿成长 12000 tick(`handleSpecialMilk`);
- 玩家自己喝:仅继承 MilkBucketItem 的解负面效果,无额外价值。

### heartfelt 修正(SpecialMilkBucketMixin,core 配置)
- `@WrapMethod` 包裹 `SpecialMilkBucketItem.m_5922_`(finishUsingItem):
  - 玩家饮用完成 → **生命回复 I**(20 秒;解负面由原版牛奶逻辑完成,不重复);
  - 找主人 48 格内最近的心契女仆(妻子 > 恋人 > 女儿 > 普通)→ 系统消息
    "女仆看着你喝下她为你准备的奶,脸一下子红了…" + 女仆气泡;
  - 写记忆:女仆 NBT `heartfelt_special_milk_at/count`(玩家 NBT 兜底)。
- `EventHistoryManager.buildHistoryText`:Shared History 注入"主人喝过我为他
  准备的奶 N 次(最近一次是 X 天前)"。
- Promaid 记忆桥:Snapshot 增加 `specialMilkCount`,diff 写
  `special_milk_state`("主人喝下了我为他准备的奶",带事件 tick)。

### 验证
- 双模组编译 0 错误;heartfelt_connection-1.3.1.jar(65 条目,15 mixin 校验通过)、
  promaid-1.0.0.jar(238 条目)打包安装,旧版入 backup。
- SRG 核对:`MobEffects.f_19604_`=REGENERATION、`m_147207_`=addEffect、
  `Level.f_46443_`=isClientSide。

---

## 1.3.0(2026-08-14)——galgame 化:告白方向修正 + 事件历史 + 情境/后果/纪念日

> 用户选定的 P0-P5 全部落地。核心思路:让文本真正回应玩家的过去与当下,
> 这是"最像 galgame 的一步"。

### P0 告白方向修正(玩家主动告白)
- 之前:玩家在 maidmarriage 对话菜单点"表白"→ 走 `confession_intro` 剧本,弹的是
  【女仆开口告白】——玩家发起却看女仆表白,方向错位。
- 现在:客户端 mixin(`PlayerConfessionEntryMixin`,仅客户端)拦截
  `HugDialogueRuntimeBridge.choose("confession")`,关闭 maidmarriage 的
  HugActionScreen,改开 heartfelt 自己的 **PlayerConfessionScreen**(玩家开口 +
  女仆回应,反向复用 ConfessionScreen 布局):
  - 玩家说出告白词 → C2S `PlayerConfessionPacket`;
  - 服务端判定(好感≥128、未告白、未婚、非女儿、非哀悼)→ 接受:completeConfession
    + 记录告白发起方 + 心情+2 → S2C `PlayerConfessionResultPacket` 回女仆甜蜜回应;
  - 哀悼期婉拒(不惩罚);不满足条件 → 系统提示"还没准备好"。
- 配置 `dialogue.playerConfessionEnabled=true`(关掉回退 maidmarriage 原剧本)。
- maidmarriage 告白剧本保留,供"女仆主动"场景(heartfelt 自己的 ConfessionScreen 流程)。

### P1 事件历史系统
- 新 `memory/EventHistoryManager`(服务端,已注册):记录首见/首礼/救主/破裂史到
  女仆 NBT;告白发起方与时刻在告白时记录(`heartfelt_confession_by/at`)。
- 注入:SmartPromptAppender 追加 **Shared History** 段("昨天,我们初次见面…"
  "我曾在危险中守护过主人 2 次"),让 LLM 知道你们共同走过的时间线。

### P2 情境变量扩充
- 新 `prompt/SituationalPrompt`:饥饿(callresponse HungerData)/ 昼夜 / 生物群系 /
  下雨 / 主人血量与饥饿 → **Situation** 段注入提示词。

### P3 后果文本弧
- 新 `prompt/AftermathPrompt`:告白被拒后(绝口不提、保持距离)、破裂后(心碎冷淡,
  随天数缓和)、和好后(愧疚+加倍忠诚)、哀悼期(悲伤回避)——按状态注入相处模式文本池。

### P4 女儿成年线
- ADULT 女儿新增**照顾爸爸**的父女日常(`adultDaughterCarePrompt`,与既有
  fatherDaughterPrompt 按日交替),体现"长大成人的女儿"。

### P5 纪念日 / 回忆杀
- `FamilyInteractionManager.checkAnniversaries`:距告白/首见日满 7/30/100/365 天
  里程碑时,触发一次 LLM 回忆对话("今天是特别的日子——我们告白已经 30 天了")。

### 验证
- 双模组编译 0 错误;heartfelt_connection-1.3.0.jar(64 条目,14 mixin 含客户端
  mixin 校验通过)、promaid-1.0.0.jar 打包安装;旧版入 backup。
- 新类全部在 jar 内验证 OK。

---

## 1.2.3(2026-08-14)——记忆事件带时间戳(第 N 天)

> 用户要求:"记录的时候也需要记录时间。"此前 heartfelt 的事件标记只有
> `heartfelt_heartbroken_at` 带时间,告白被拒/悔改/女儿成长都是无时间布尔/字符串;
> 记忆文本也从不提"哪天"。本次让所有记录的事件都带上游戏时刻,并让 AI
> 记忆与提示词注入都能感知"第 N 天"。

### heartfelt 侧(记录点写时间戳)
- 新增 NBT 时间戳键(与事件标记同写):`heartfelt_confession_failed_at`
  (告白被拒)、`heartfelt_redempted_at`(悔改完成)、`heartfelt_child_stage_at`
  (女儿成长阶段升级)。`heartfelt_heartbroken_at` 原本就带时间。
- `StoryMemoryManager` 的告白失败/心碎记忆改为带"第 N 天"前缀
  ("第 37 天，我曾向主人告白，但被拒绝了……")——LLM 提示词注入能感知事发时间。

### Promaid 侧(记忆桥带上事件时间)
- `writeRelationFact` 新增带 `eventTick` 的重载:段落的 `eventTimeStart/End`
  用真实事件时刻,而非扫描时刻;记忆内容自动加"第 N 天，"前缀(气泡保留原文)。
- 各事件的时间来源:告白被拒/悔改/成长 → heartfelt `_at` 时间戳;
  结婚 → `marriage_data.marriedGameTime`;怀孕/分娩/母亲 → `conceivedGameTime`/
  `lastBirthGameTime`;其余无权威时刻的沿用扫描时刻。
- 效果:AI 记忆与对话能引用具体日子("我们是第 37 天在一起的""那是三天前的事")。

### 验证
- 双模组编译 0 错误;heartfelt_connection-1.2.3.jar(57 条目,13 mixin 校验通过)、
  promaid-1.0.0.jar(238 条目)打包并安装到 `D:\.minecraft\mods`,旧版入 backup。

---

## 联动:heartfelt 记忆事件 → Promaid 记忆系统(2026-08-14)

> Promaid 侧改动(其 `RelationshipMemoryAdapter`),让 heartfelt 记录的记忆事件
> 能被 Promaid 的 AI 记忆系统读取(差异检测 → 写记忆段 + 关系三元组 + 画像):

| heartfelt 事件 | NBT 标记 | Promaid 写入的记忆 |
|---|---|---|
| 告白被拒 | `heartfelt_confession_failed` | "我曾向主人告白，但被拒绝了……" |
| 关系破裂·心碎 | `heartfelt_heartbroken_at` | "我们的恋情结束了，我心碎了" |
| 背叛悔改 | `heartfelt_redempted` | "我从背叛中清醒过来，重新回到主人身边" |
| 女儿成长 | `heartfelt_child_stage` | "我长大了——现在的我是会自己站起来了的小宝贝/活泼的小女孩/长大成人的女儿" |
| 主人死亡哀悼 | `heartfelt_mourning_until` | (已有)悲痛/走出悲痛 |

- 桥接模式与既有 `isMourning` 完全一致:直接读 heartfelt 的 NBT 标记,
  **未装 heartfelt 时标记永不出现 → 自动静默**,零硬依赖。
- 悔改特殊处理:背叛女仆重新认主后快照被重置,故在"首见补写"路径用会话级
  `redemptionWritten` 集合防重启重复写。

## 1.2.2(2026-08-14)——女儿线拓展(阶段感知 + 父女专属)

> 背景:原模组(maidmarriage)的女儿文本量少(幼儿池 2KB)、阶段不分、与"家庭女眷"
> 共用池子导致和母亲串味。heartfelt 侧补齐(不碰原模组)。

- **① 成长阶段感知**:`MaidMarriageCompat.childStage()` 读 growthStage
  (INFANT→JUVENILE→CHILD→ADULT,含旧名 MIDDLE),heartfelt 首次"知道女儿几岁"。
- **② 提示词细分**:daughter 准则按阶段三分——幼儿(奶声奶气、被爸爸抱着)/
  少女(活泼撒娇、缠着爸爸)/ 成年(懂事的大女儿,关心爸爸但依然撒娇);
  与妻子准则彻底区隔。
- **③ 记忆细分**:StoryMemory 按阶段注入女儿事实(婴儿咿呀 / 会跑会走 /
  缠着撒娇 / 长大了照顾爸爸)。
- **④ 父女互动(单双日交替)**:偶数日母女互动、奇数日父女互动——女儿对爸爸说
  一句按阶段区分的话(幼儿咿呀 / 少女汇报小发现 / 成年女儿关心爸爸),与母女文本
  区隔;各每 2 天 1 次,走配额。
- **⑤ 成长事件**:成长阶段升级瞬间触发 LLM 小剧情("爸爸,你看!我长大了!")+
  系统消息(会自己站起来了 / 长成小女孩了 / 长大成人了)。
- 配置新增:`dialogue.fatherDaughterEnabled=true`、`dialogue.growthEventEnabled=true`。

---

## 1.2.1(2026-08-14)——冻结边界重定义 + 自动忠诚 + 中和缓和

### 冻结边界重定义(用户确认)
- **冻结只限确认关系**:冻结层 = 恋人(好感≥告白线 128)/ 妻子 / 女儿。
  **深爱暗恋(好感≥192 未告白)不再冻结**——她的信任/恐惧恢复活跃,
  由好感度中和缓和自然推向忠诚数值(而不是静止在冻结瞬间)。
- 代价(按你确认的选项):暗恋者不再自动免疫背叛/淡忘/吃醋——但高好感下
  数值健康,几乎不会触发;家庭保护/悼念也按冻结层收窄(只保护确认关系+女儿)。

### 自动忠诚(新)
- 确认关系(冻结层)女仆在爱憎分明的 4 条关系线中,**给玩家的加成自动判定为忠诚**
  (EmotionDevotedMixin 拦截 isDevoted 强制 true),获得:
  - 属性加成:攻击 +5、移动速度 +0.1;
  - 自动攻击附近背叛女仆(16 格,60% 概率从叛徒身上扒装备——扒的是叛徒不是主人);
  - 主人血量 <10% 时自动回血(每 60 秒冷却)。
- 信任/恐惧对确认关系女仆【完全失效】:冻结 + 不驱动任何关系线,一切以好感度衡量。

### 好感度对信任/恐惧的中和缓和(新)
- 每 5 秒批量(可配):信任 += rate×好感/384,恐惧 -= rate×好感/384
  (默认 rate=0.2/秒,满好感约 8 分钟从 0 拉满;rate=0 关闭)。
- 只作用于未确认关系女仆;冻结层跳过(否则经 FreezeConversion 折算成好感,双重计算)。
- 效果:信任/恐惧成为好感的"跟随者"——对高好感女仆,爱憎分明数值上自然趋向忠诚,
  不再是一套独立的刷信任系统。

### 工程
- 新增:mixin/EmotionDevotedMixin(core 配置)、relationship/EmotionSmoothingManager。
- 配置新增:emotionSmooth.ratePerSecond=0.2、emotionSmooth.batchTicks=100。
- 版本 1.2.1;打包校验 13 个 mixin 全通过。

---

## 1.2.0(2026-08-14)——关系模型升级 + 女仆主动告白

### 关系模型升级(用户确认)
- **冻结语义重定义**:确认关系(恋人/妻子)后,女仆的信任/恐惧数值【完全冻结】——
  玩家造成的信任/恐惧变化【等比折算为好感变化】(FreezeConversion):
  信任+N→好感+N×ratio;信任-N→好感-N×ratio;恐惧+N→好感-N×ratio;恐惧-N→好感+N×ratio
  (比率可配,默认信任 1:1、恐惧 2:1);非玩家来源只冻结不折算;负向折算受每日上限。
- **关系破裂**:恋人好感跌破告白线(默认 128)→ 冻结解除、关系破裂:
  重置告白状态 + 心碎记忆(StoryMemory 注入)+ 心情惩罚;其他暗恋者重新有机会。

### 女仆主动告白(新)
- 好感 >192(恋爱线)的女仆意识到自己喜欢主人 → 周期性【主动告白】尝试,
  概率随好感线性升高(192→384:基础 15% + 最高 45% 加成,可配);
- 主人【已宣布关系】(已婚/有恋人)时不发起尝试(已宣布则不尝试);
- 触发前【威胁检查】:以女仆为中心 48 格(可配)内无敌对生物、女仆不在战斗中、
  主人距离 ≤32 格(可配)→ 发送 S2C 拉出告白对话框(心契誓约同款格式,自建界面);
- **接受** → 完成告白成为恋人(maidmarriage 公开 API)+ 心情小涨;
- **委婉拒绝** → 正式失败:心情扣减(默认 -6)+ 记忆写入(她再也不提)+ 永久标记
  (不再主动告白;调试 API 可重置);直接关窗 → 暂缓,不惩罚,稍后再试。

### 工程
- 补丁首次引入【网络通道】(HeartfeltNetwork:S2C OpenConfession / C2S Response)
  与【客户端界面】(ConfessionScreen,SRG 名,仿 maidmarriage 格式)。
- 新增:relationship/FreezeConversion、dialogue/MaidConfessionManager、
  network/HeartfeltNetwork、client/ConfessionScreen + ClientInit。
- 配置新增:freeze(告白线 128/信任 1.0/恐惧 0.5)、confession(间隔 2400/基础 0.15/
  加成 0.45/威胁半径 48/最大距离 32/失败心情 6/触发好感 192)。
- 调试 API:maidDebug 增加告白状态(可主动/已被拒/已确认/心碎)与
  resetConfessionFailure(重置失败标记,后悔药)。

---

## 1.1.0(2026-08-14)——全面重构 + 功能修复 + 悔改系统

### 修复(对照反编译基线逐项验证)
- **A0 包名错位(致命)**:爱憎分明兼容层 9 个 @Mixin targets + 4 处反射全部从
  `com.github.tartaricacid.callresponse` 修正为真实包名 **`com.github.JumDa5he.callresponse`**
  ——恐惧/信任冻结、背叛/遗忘/吃醋隔离、对话配额、饥饿缓存与钳制、猎杀背叛者
  **从"静默未生效"变为真正工作**。
- **A1 心情记忆死代码**:`mood_data` 补进 TaskData 白名单,心情/丧子记忆真正注入 LLM。
- **A2 仇恨标记永久残留**:`heartfelt_hated_player` 带时间戳,超期(默认 7 游戏日,可配)
  自动清除;悔改时同步清除。
- **A3 引用比较**:11 处实体引用 `!=` 改为 UUID 比较(跨维度/重进不再静默失效)。
- **A4 恐惧来源过滤**:冻结层女仆只有"主人造成的恐惧"才折算好感扣减,且每日上限
  (默认 8,可配)——战斗流不再悄悄跌破婚姻线。
- **A5 远程家庭保护**:`LivingHurtEvent` 追查间接来源(getEntity),箭/弹幕/枪械同样受保护。
- **A6/A7 广播防御与清理**:server 判空 + 快照随日清理(女仆死亡/解雇不再留内存)。
- **A8 产后窗口对齐**:硬编码 72000 tick 改调 `PregnancyData.isInPostpartumRecovery`。
- **A9 缓存与解析**:饥饿档缓存加"效果存在性"作废条件;解析失败不再永久降级(可重试)。
- **A10 性能**:反击背叛者优先级 199→150(可配)、扫描 20→40 tick(可配)、8 格快探。
- **A11 mixin 分级**:拆 `mixins.heartfelt.json`(core,必装)+ `mixins.heartfelt.opt.json`
  (enhancement,可选)——增强型注入失败不再崩游戏。
- **A12 提示词档位**:好感等级改为明确区间(0-63/64-191/192-383/384)。

### 新增功能
- **背叛悔改(和解)系统** `BetrayalRedemptionManager`:背叛后对女仆喂蛋糕安抚
  (每次恐惧-8、信任+4,可配)→ 5 次(可配)且恐惧≤90、信任≥10 → 调用原版公开 API
  `resetBetrayal` + 重新认主 + LLM 悔改对话 + 当天心情-5(愧疚)+ 清除仇恨标记;
  安抚中断 3 天(可配)进度清零。
- **调试 API** `debug.HeartfeltDebugApi`(包名固定,Promaid 手册可反射调用):
  `featureSummary()` 运行时探测各功能真实 ON/OFF、`maidDebug()` 单女仆调试信息、
  清仇恨/强制悔改/清悼念。
- **公共配置** `config.HeartfeltConfig`:仇恨保留天数、恐惧扣减上限、悔改参数、
  行为优先级、三类对话扫描间隔。
- **对话公共分发器** `dialogue.DialogueDispatcher`:主人判定(UUID 比较)/女仆扫描/
  配额对话统一入口。

### 结构重构
- 新增 `compat/` 反射层:`ReflectUtil`(句柄缓存+安全调用)、`CallResponseCompat`、
  `MaidMarriageCompat`(含 A1/A8 修复)、`PromaidCompat`(配额桥实现)。
- `RelationshipExemption` / `ApiQuotaBridge` 保留门面转发,调用方零改动。
- 文案集中 `prompt.PromptTexts`;常量集中 `tags.HeartfeltTags`(含新键)。
- 匿名 brain 提为 `combat.BetrayerAttackBrain`;新增 `dialogue.DialogueDispatcher`。

### 构建链(标准工作流)
- `fix_classpath.py`:修正 compile_addon.txt 失效的 Desktop 库路径 → D:\.minecraft\libraries
- `compile_heartfelt.bat` / `build_heartfelt.py`(双 MixinConfigs 声明 + mixin class 校验)
- `build_all.bat`:一键 修复路径→生成参数→编译→打包→安装(需管理员写 mods 目录)
- `WORKFLOW.md`:开发→编译→安装→游戏内验证全流程与常见问题

### 兼容性
- 不改 maidmarriage / callresponse / TLM 任何代码(反射 + @Pseudo 混入软依赖);
  未装相关模组时逐项优雅降级。
- 与 Promaid 共存:共享全局 API 配额;调试信息经 HeartfeltDebugApi 暴露给 Promaid 手册。
