# Round 1 · M0 地基（2026-11-15）

> 结论先行：**M0 出口条件已达成并在测试服实测通过** —— `/redtale reload` 生效、一条配置能触发 effect、
> 坏配置不影响其它脚本。**但 Gradle 构建链被网络卡死**，本轮用「离线 javac + 手工打 jar」绕过，
> 产物已在 `E:\server-test` 跑通；正式构建需要 Aki 拍板（见 §7）。

## 1. 里程碑

M0：Gradle（JDK21/ModDevGradle）+ 配置热载 + 脚本层骨架（各注册 1 个示例原语）。
出口条件：`/redtale reload` 生效，一条配置能触发一个 effect。→ **已满足**。

## 2. 环境实测

| 项 | 结果 | 证据 |
|---|---|---|
| JDK | 21（`C:\Program Files\Java\jdk-21`） | `java -version` → `openjdk 21 2023-09-19` |
| Gradle CLI | **未安装** | `gradle -version` → command not found |
| Gradle 发行版 | 8.8，下载到 `D:\redtale-work\tools\gradle-8.8`（腾讯镜像 12.3s / 138 MB） | `gradle --version` → `Gradle 8.8` |
| wrapper | 已生成（`gradle/wrapper/gradle-wrapper.properties` 指向腾讯镜像，官方源校验失败） | `gradle wrapper --gradle-distribution-url ...` BUILD SUCCESSFUL |
| ModDevGradle | 2.0.148（Plugin Portal 现查最新） | `plugins.gradle.org/m2/.../net.neoforged.moddev.gradle.plugin/maven-metadata.xml` |
| NeoForge | 21.1.257 POM 可达（经镜像） | `bmclapi2.bangbang93.com/maven/.../neoforge-21.1.257.pom` → 200 |

### 2.1 构建被卡死的具体位置

```
Could not GET 'https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/neoform-runtime-2.0.31.pom'
Connect to maven.neoforged.net:443 [199.59.148.201] failed: Connection timed out
```

- 本机 DNS 对 `maven.neoforged.net` 返回的是污染地址（223.5.5.5 → `199.59.150.45`，119.29.29.29 → `162.125.80.3`，均为境外 CDN 段），直连必超时。
- 该 artifact **在五个可访问镜像里都没有**（全部 404）：

| 镜像 | `neoform-runtime-2.0.31.pom` |
|---|---|
| `mirrors.cloud.tencent.com/nexus/repository/maven-public` | 404 |
| `repo.huaweicloud.com/repository/maven` | 404 |
| `maven.aliyun.com/repository/public` | 404 |
| `mirrors.tuna.tsinghua.edu.cn/maven` | 404 |
| `bmclapi2.bangbang93.com/maven`（能镜像 neoforge 本体） | 404 |

→ **neoforge 本体有镜像，构建工具链（neoform-runtime）没有**。镜像路线到此为止。

## 3. 绕过方案：离线 javac + 手工打 jar

`tools/offline_check.py`：

1. classpath = `E:\server-test\libraries` 下全部 96 个 jar（含 `neoforge-21.1.257-server.jar`，它是**未混淆的官方名**服务端 jar）+ gson 2.10.1。
2. `javac -encoding UTF-8 --release 21` 全量编译 `src/main/java`（22 个源文件）。
3. `--package` 时把 class + 资源 + 替换过变量的 `neoforge.mods.toml` 打成 `build/libs/redtale_project-0.1.0.jar`。

**它不是 Gradle 构建**：不做 neoform 反编译/补丁、不产 sources jar、不做 remap。它只是「保证代码在真 NeoForge 运行时上能编译并跑起来」。

一个坑（已写进脚本注释）：classpath 里 `MinecraftServer` 有两份，**服务端 jar 必须排最前**，否则 javac 会解析到 `libraries` 里另一份签名不全的同名类，报「找不到 overworld()」。

## 4. 代码结构（本轮新增 22 个源文件）

```
src/main/java/com/redtale/project/
├─ RedtaleProject.java              @Mod 入口，只注册事件钩子
├─ core/
│  ├─ RedtaleCore.java              单例：注册表 + 配置 + 执行器 + reload 语义
│  ├─ config/
│  │  ├─ Json5.java                 自写 JSON5 解析器（注释/单引号/裸键/尾逗号/0x）
│  │  ├─ Json5Exception.java        带行列号的解析错误
│  │  ├─ ConfigFile.java            一份已解析配置（path + root）
│  │  └─ ConfigStore.java           目录扫描、默认配置落地、README 生成
│  ├─ script/
│  │  ├─ ScriptRegistries.java      三张注册表（重复注册直接抛）
│  │  ├─ EffectType / TriggerType / ConditionType   原语接口
│  │  ├─ ScriptContext / ScriptCall / ScriptAction 数据结构
│  │  ├─ ScriptEngine.java          解析 + 校验 + 按 trigger 分发 + 冷却
│  │  └─ builtin/                   message / console_log / always / health_below / chance / manual / player_join
│  ├─ hook/GameHooks.java           开服加载、注册命令、玩家登录 → player_join
│  └─ command/RedtaleCommand.java   /redtale reload|status|list|fire
resources/
├─ META-INF/neoforge.mods.toml
└─ redtale/defaults/scripts/greeting.json5
tools/
├─ offline_check.py                 离线编译 + 打包
└─ rcon.py                          测试服 RCON 验收（密码读环境变量或 server.properties）
```

### 4.1 reload 语义（本轮定下来的，写进 PLAN）

| 情况 | 行为 |
|---|---|
| 文件**语法**错误 | 读取阶段中断，整个 reload 不生效，上一份配置继续跑 |
| 单条脚本**语义**错误（未知 trigger/effect、缺字段、id 重复） | 只跳过那一条，其余照常生效，错误一次性全报并显示在 `/redtale status` |
| 全部正常 | 换入新脚本集，重写 `config/redtale_project/README.md` |

理由：线上改配置写漏一个逗号不该让整套脚本停摆，也不该静默失败。

## 5. 测试服验收证据（E:\server-test，NeoForge 21.1.257）

### 5.1 启动即加载

```
[10:24:18] [Server thread/INFO] (DedicatedServer) Done (1.702s)! For help, type "help"
[10:24:18] [Server thread/INFO] (RedtaleProject) 配置重载完成：1 条脚本，1 个文件
```

`config/redtale_project/` 首次运行自动生成 `scripts/greeting.json5` + `README.md`（1837 B，由注册表实时生成）。

### 5.2 命令可用

```
> redtale status
[redtale] 状态：已就绪 · 脚本 1 条 · 原语 effect 2 / trigger 2 / condition 3

> redtale list
greeting · player_join · scripts/greeting.json5

> redtale fire greeting
[redtale] 已执行 greeting
[10:21:52] [Server thread/INFO] (RedtaleProject/Script) [script] 玩家 server 登录，greeting 脚本已执行
```

### 5.3 热重载生效（改文本 → reload → 立刻变）

```
> redtale reload
[redtale] 已载入 1 条脚本（1 份配置文件）
> redtale fire greeting
[10:22:05] [Server thread/INFO] (RedtaleProject/Script) [script] 热重载生效·第二版 server
```

### 5.4 坏配置：只跳过坏的那条，好的继续跑

```
[10:24:18] [Server thread/ERROR] (RedtaleProject) 脚本配置有 3 处错误，已跳过（仍生效 1 条）：
> redtale status
[redtale] 状态：已就绪 · 脚本 1 条 · 原语 effect 2 / trigger 2 / condition 3
  - scripts/broken.json5：未知 trigger 'no_such_trigger'，当前可用：[manual, player_join]
  - scripts/broken.json5：未知 effect 'not_an_effect'
  - scripts/broken.json5：脚本 "broken" 没有可执行的 effect
> redtale fire greeting
[redtale] 已执行 greeting
```

语法错误版本（缺右括号）也测过，报 `scripts/broken.json5 语法错误：无法识别的字符 '}'（第 7 行，第 1 列）`，整份配置不生效、旧配置继续跑。

## 6. 未做 / 未验证

| 项 | 状态 |
|---|---|
| Gradle `build` 出正式 jar | **未验证**（网络阻断，见 §2.1） |
| Gradle wrapper 实际跑通 | 生成成功，未执行 build（同上） |
| 玩家真实登录触发 `player_join` | 未验证（测试服无客户端连接，只验了 `/redtale fire`） |
| JSON5 解析器对深层嵌套/大文件 | 只测了本轮这几份配置 |
| 属性注册表 | 只有草案 `docs/ATTRIBUTE-REGISTRY.md`，M1 实现 |

## 7. 构建链路：已拍板并解决（2026-11-15 补记）

Aki 的选择：**用镜像源**。实测结论是「纯镜像 + MDG」走不通 —— `neoform-runtime` 在阿里 / 腾讯 / 华为 / 清华 / bmclapi 五家全部 404，官方源 DNS 被污染直连超时。见 `docs/BUILD.md` §1 的逐条记录。

改用**镜像源 + 服务端 libraries 直编**（NeoForge 1.20.2 起取消 reobf，不需要 MDG 的 neoform 阶段）：

```
gradle build --offline
BUILD SUCCESSFUL in 19s
build/libs/redtale_project-0.1.0.jar   (44 KB)
```

产物进 `E://server-test//mods` 后 `Done (2.353s)` 正常启动。`tools/offline_check.py` 降级为应急备用，不再是主路径。原始选项与证据保留在 `docs/BUILD.md`。

**待补**：`runClient` 调试仍需 MDG + NFRT，暂时做不了；客户端验证走「打进整合包直接开游戏」。
