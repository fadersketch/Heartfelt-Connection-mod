# Heartfelt-connection 标准工作流(v1.1.0)

## 一、目录结构

```
maidmods/
├── heartfelt_src/            # heartfelt_connection 源码(唯一修改点,勿动其他模组源码)
├── promaid_src/              # Promaid 源码(你的模组)
├── compile_addon.txt         # javac 参数模板(classpath;含旧路径时先跑 fix_classpath.py)
├── fix_classpath.py          # 修正 compile_addon.txt 的失效库路径(Desktop→D:\.minecraft\libraries)
├── gen_compile.py            # 由模板生成 compile_promaid.txt / compile_heartfelt.txt
├── compile_heartfelt.bat     # javac @compile_heartfelt.txt → out_heartfelt/
├── compile_promaid.bat       # javac @compile_promaid.txt → out_promaid/
├── build_heartfelt.py        # 打包 heartfelt_connection-1.1.0.jar(校验 mixin class)
├── build_promaid.py          # 打包 promaid-1.0.0.jar
├── build_all.bat             # 一键:修复路径→生成参数→编译→打包→安装
├── out_heartfelt/            # 编译产物(自动生成)
├── staging_heartfelt/        # 打包暂存(自动生成)
└── patched/                  # 成品 jar
```

## 二、标准流程

### 2.1 完整一键(推荐)
以**管理员权限**运行(写 D:\.minecraft\mods 需要管理员,与 PCL 一致):

```
build_all.bat
```

等价于:
1. `python fix_classpath.py` — 修复 compile_addon.txt 里的失效库路径
2. `python gen_compile.py` — 重新收集源码生成编译参数
3. `call compile_heartfelt.bat`(+ 尽力编译 promaid)
4. `python build_heartfelt.py`(+ promaid)— 打包并校验 mixin class
5. 旧 jar 移入 `D:\.minecraft\mods\backup\`,新 jar 复制进 `D:\.minecraft\mods\`

### 2.2 手动分步
```bat
python fix_classpath.py
python gen_compile.py
compile_heartfelt.bat
python build_heartfelt.py
:: 安装(需管理员 PowerShell):
Move-Item "D:\.minecraft\mods\heartfelt_connection-1.0.0.jar" "D:\.minecraft\mods\backup\heartfelt_connection-1.0.0.jar"
Copy-Item "patched\heartfelt_connection-1.1.0.jar" "D:\.minecraft\mods\heartfelt_connection-1.1.0.jar"
```

### 2.3 编译环境说明
- 类路径指向 **D:\.minecraft\libraries**(游戏实例开发库;旧的 Desktop 路径已失效,由 fix_classpath.py 修正)
- 编译对 **original_tlm.jar**(原版 TLM)而非 smart_tlm.jar;maidmarriage / callresponse **不在 classpath**——全部经反射软调用
- 源码为 **UTF-8**(含中文注释),javac `-encoding UTF-8` 防 GBK 误读;bat 脚本保持 ASCII
- 需要 JDK 17+(--release 17)

## 三、游戏内验证清单(每次改动后)

1. **启动无错**:日志无 `Mixin apply failed`、无 `ClassNotFoundException`(JumDa5he 包);heartfelt 两个 mixin 配置均加载
2. **功能探测**:Promaid 手册 → 详细设置 →「心契补丁」栏,`featureSummary()` 应全 ON:
   关系冻结/背叛隔离/遗忘隔离/吃醋隔离/主动对话配额/见证对话配额/饥饿档缓存/怀孕饥饿钳制/工作坐姿兼容/Promaid 配额
3. **情绪冻结实测**:把一只已告白女仆的信任调到 0 再打她——修复前会背叛(爆炸+敌对),修复后**不背叛只扣好感**(每日上限内)
4. **悔改闭环实测**:
   - 普通女仆:信任 <10 且恐惧 >90 持续 10 秒 → 背叛(解除认主、敌对、砸箱子)
   - 对背叛女仆喂蛋糕 ×5(每次恐惧-8 信任+4)→ 恐惧≤90 且信任≥10 → 悔改成功:重新认主 + LLM 悔改对话 + 当天心情-5
   - 安抚中断 3 天进度清零;debug 面板可看「悔改安抚: n/5」
5. **远程保护实测**:同主人两只女仆,好感≥64 的持弓射击另一只——伤害应为 0(远程已纳入家庭保护)
6. **A1 心情记忆实测**:把 maidmarriage 心情调到 <5,对话中女仆应表达"心情低落"记忆

## 四、版本与发布

- 版本号:改 `heartfelt_src\META-INF\mods.toml` + `build_heartfelt.py` 的 VERSION,同步更新 `changelog.md`
- 发布产物:`patched\heartfelt_connection-<版本>.jar`,旧版进 `D:\.minecraft\mods\backup\`

## 五、常见问题

| 现象 | 处理 |
|---|---|
| 编译报 Desktop 路径 | 先跑 `python fix_classpath.py` |
| jar 缺 mixin class 启动即崩 | build_heartfelt.py 的校验会拦截——不要绕过;`compile_heartfelt.bat` 后必须重新 build |
| 源文件中文乱码 | 确认以 UTF-8 保存(不带 BOM);javac 已带 -encoding UTF-8 |
| 女仆功能"没生效" | 先看游戏内调试面板 featureSummary;某行 OFF = 对应模组未装/版本不匹配 |
| 原模组更新后崩 | 本补丁只反射+mixin,原模组升级改签名会让对应 mixin 静默跳过或报错——把报错行贴给维护者;增强型 mixin(opt 配置)不崩溃 |
