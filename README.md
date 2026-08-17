# Heartfelt-connection (心契誓约 × 爱憎分明)

《心契誓约》(maidmarriage) × 《爱憎分明》(Love & Loathe) 的**关系联动补丁**。不对任何原版模组动刀，全部以 Mixin 运行时补丁实现——让两只模组的女仆关系系统（告白、信任/恐惧、吃醋、家庭）真正协同工作，并把关系状态注入 AI 对话上下文。

- **Minecraft**:1.20.1
- **加载器**:Forge 47.4.21(1.20.1-47.4.21)
- **许可证**:MIT
- **仓库**:github.com/fadersketch/Heartfelt-Connection-mod
- **版本**:v1.0.0

> 开发过程中使用了 AI 辅助编程工具;作者并非专业程序员,代码中可能存在缺陷,遇到问题欢迎反馈。

## 前置模组(必装,缺失将无法启动)

| 模组 | 要求版本 | 说明 |
|---|---|---|
| **Touhou Little Maid(车万女仆)** | ≥ 1.5.0(推荐 1.5.3) | API 基础,本模组的母体 |
| **maidmarriage(心契誓约)** | ≥ 2.0.0(推荐 2.3.0) | 结婚/家庭/女儿系统 |
| **callresponse(爱憎分明 Love & Loathe)** | ≥ 2.0.0(推荐 2.0.4) | 信任/恐惧/饥饿系统 |

## 推荐联动(软联动,非必装)

- **[Promaid(更智能的车万女仆)](https://github.com/fadersketch/Promaid-mod)** —— **强烈推荐一并安装**:heartfelt 的关系状态会通过 Promaid 的记忆系统写入女仆长期记忆(结婚/告白/破裂/女儿成长都成为她的记忆),双方共享全局 API 配额,告白概率、纪念日、情绪联动等跨模组功能完整生效。两个模组互不依赖,但装在一起体验最佳。
- **Better Combat 等武器模组**:天然兼容。

## 适用版本

- Minecraft **1.20.1** + Forge **47.4.21**(即 1.20.1-Forge_47.4.21)
- 仅 Forge 加载器;服务端/客户端均为 both(单人、局域网、服务器均可)

## 功能特性

### 关系联动
- **关系栏注入**:结构化关系状态(`<context>`)注入 AI 对话,LLM 知道"谁是谁";
- **提示词运行时注入**:favorability 四档量表 / 关系专属准则(妻子/恋人/女儿按阶段细分),女儿线带防乱伦铁律;
- **信任/恐惧冻结**:确认关系后信任/恐惧完全冻结、玩家造成的信任/恐惧变化等比折算为好感;
- **吃醋分层隔离 + 绝对压制**:已确认关系(妻子/恋人)后其他女仆吃醋被压制,不依赖在场检测;
- **关系破裂监视**:恋人好感跌破告白线 → 关系破裂 + 心碎记忆 + 心情惩罚。

### 告白系统
- **女仆主动告白**:好感 >192 周期性概率尝试 → 威胁检查 → 前摇(系统消息 + 走向玩家)→ 走到身边弹出告白界面;接受成恋人、拒绝扣心情 + 写记忆 + 不再主动;
- **玩家主动告白**:玩家开口 + 女仆回应(独立告白屏,方向修正);
- **纪念日/回忆杀**:7/30/100/365 天里程碑(女儿为出生/初见基准)。

### 家庭与女儿线
- 女儿四阶段(婴儿/幼儿/少女/成年):父女互动单双日交替、成长事件、专属文本池;
- 家庭保护:女儿永不背叛、幼儿语音/任务/手持约束、灵体不播语音包;
- 特殊奶增强、产后窗口、魂符收妈妈时女儿等待。

### 机制修正
- 背叛悔改(和解)系统、心情记忆、仇恨标记过期;
- 对话安全区、选择性静止(其他生物定住)、演绎剧情按钮;
- 调整器(测试工具,形象=纸):一键调整好感/关系/心情/成长阶段/时间快进,方便验证全部功能。

## 安装

1. 安装 Minecraft 1.20.1 + Forge 47.4.21 + 三个前置模组(见上表);
2. 下载 `heartfelt_connection-1.0.0.jar` 放入 `mods` 文件夹;
3. (可选)安装 [Promaid](https://github.com/fadersketch/Promaid-mod) 获得完整联动体验。

## 从源码构建

仓库自带一键构建链(仅 Windows,需要 JDK 17+):

```
gen_compile.py        # 扫描 heartfelt_src 自动生成编译参数(需先准备 compile_addon.txt 类路径)
compile_heartfelt.bat # javac 编译,输出 out_heartfelt
build_heartfelt.py    # 组装 jar(classes + 资源 + META-INF + LICENSE)→ patched/heartfelt_connection-1.0.0.jar
```

编译需要完整的 Forge 1.20.1 开发类路径(可直接使用 `.minecraft/libraries` 下的库 + 原版 TLM/maidmarriage/callresponse jar),按 `compile_addon.txt` 的格式写入本地路径后运行。

## 反馈

遇到问题、建议,欢迎在本仓库 **Issues** 页面提交(需登录 GitHub)。

## 致谢与相关项目

- [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid) —— 前置与开发基础;
- [Promaid(更智能的车万女仆)](https://github.com/fadersketch/Promaid-mod) —— 软联动,推荐一并安装。

## 许可证

[MIT](LICENSE) © 2026 fadersketch
