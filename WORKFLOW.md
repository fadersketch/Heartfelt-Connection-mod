# Heartfelt-connection 标准工作流(v1.5.116)

## 一、目录结构

```
maidmods/                     # 唯一事实源(非 git 仓库)
├── heartfelt_src/            # heartfelt_connection 源码(唯一修改点)
├── promaid_src/              # Promaid 源码(唯一修改点)
├── build_heartfelt.py        # 打包 heartfelt_connection-1.5.116.jar
├── build_promaid.py          # 打包 promaid-1.5.385.jar
├── compile_heartfelt.bat     # javac @compile_heartfelt.txt → out_heartfelt/
├── compile_promaid.bat       # javac @compile_promaid.txt → out_promaid/
├── gen_compile.py            # 生成 compile_promaid.txt / compile_heartfelt.txt
├── build_all.bat             # 一键:生成参数→编译→打包→安装
├── sync_to_git.bat           # 同步到 promaid-mod / heartfelt-mod 并提交
├── GIT_SYNC.md               # Git 同步规范
├── out_*/ staging_*/ patched/ # 编译产物/打包暂存/成品(不入 git)
└── CHANGELOG.md              # heartfelt 变更日志
```

## 二、标准流程

### 2.1 完整一键(推荐)
以**管理员权限**运行(写 `D:\.minecraft\mods` 需要管理员):

```
build_all.bat
```

当前构建/安装的版本:

- `heartfelt_connection-1.5.116.jar`
- `promaid-1.5.385.jar`

### 2.2 改代码后的 Git 同步(每次必做)

```
sync_to_git.bat
```

脚本会把 `maidmods` 的最新内容镜像到:

- `C:\Users\Sketch\.zcode\workspace\default\promaid-mod`(promaid,分支 `experimental/memory-port`)
- `C:\Users\Sketch\.zcode\workspace\default\heartfelt-mod`(heartfelt,分支 `main`)

并自动 `git add -A && git commit`。推送远程需要你本机凭据,脚本不代推。

### 2.3 手动分步

```bat
python gen_compile.py
compile_heartfelt.bat
python build_heartfelt.py
:: 安装(需管理员 PowerShell):
Move-Item "D:\.minecraft\versions\1.20.1-Forge_47.4.21\mods\heartfelt_connection-1.5.116.jar" "D:\.minecraft\mods\backup\"
Copy-Item "patched\heartfelt_connection-1.5.116.jar" "D:\.minecraft\versions\1.20.1-Forge_47.4.21\mods\"
```

## 三、游戏内验证清单(每次改动后)

1. **启动无错**:日志无 `Mixin apply failed`、无 `ClassNotFoundException`(JumDa5he 包)。
2. **功能探测**:Promaid 手册 → 详细设置 →「心契补丁」栏,`featureSummary()` 应全 ON。
3. **情绪冻结实测**:确认关系女仆信任调 0 再打她——不背叛只扣好感。
4. **悔改闭环实测**:背叛女仆喂蛋糕 ×5 → 悔改成功。
5. **远程保护实测**:同主人两只女仆,好感≥64 的持弓射击另一只——伤害应为 0。

## 四、版本与发布

- 版本号统一:改 `heartfelt_src\META-INF\mods.toml`、`build_heartfelt.py` 的 `VERSION`、`build_all.bat` 的 `HF_NAME` 三处,三者必须一致。
- 发布产物:`patched\heartfelt_connection-<版本>.jar`。
- `patched/` 只保留最新版本 jar;旧版本一律删除,不留备份。

## 五、常见问题

| 现象 | 处理 |
|---|---|
| 编译报 Desktop 路径 | 更新 `compile_addon.txt` 的 classpath 到 `D:\.minecraft\libraries`;`fix_classpath.py` 已不再维护 |
| jar 缺 mixin class 启动即崩 | `build_heartfelt.py` 的校验会拦截——不要绕过 |
| 源文件中文乱码 | 确认以 UTF-8 保存(不带 BOM);javac 已带 -encoding UTF-8 |
| git 出现大量 CRLF 修改 | 两个仓库已有 `.gitattributes`,正常应干净;不要手动批量改行尾 |
| 女仆功能"没生效" | 先看游戏内调试面板 featureSummary;某行 OFF = 对应模组未装/版本不匹配 |
