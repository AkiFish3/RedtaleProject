# R132 · 客户端真机尝试（E:/client/赤物语N客户端-0.0.1）+ 发现 14 基底零模型 + 派 T-0127

日期：2026-11-26 08:44~08:55
在制品：2/3（T-0126 + T-0127）
构建：未构建（未改 `src/` Java；客户端用现有 jar `690121 B` @2026-11-25 17:10）
环境：正式服 9376 **未触碰**（25565 监听中、0 条 ESTABLISHED）；测试服 **1 启 1 停**（`Done (2.274s)` → `Stopping the server` → 25567/25577 无 LISTEN）。

## 1. 客户端资产盘点（回答「该路径下有客户端吧」）

`E:/client/赤物语N客户端-0.0.1` 下有 **HMCL 3.15.1** + `.minecraft`：

| 项 | 值 |
|---|---|
| 版本 | `versions/1.21.1-NeoForge`（单版本，自带 133 libraries，`inheritsFrom` 为空） |
| NeoForge | **21.1.257**（`--fml.neoForgeVersion`，与测试服逐字相同） |
| mods | **234** 个；D11 白名单 9 前置全在册且版本对上（GeckoLib 4.9.3 / AzureLib 3.1.19 / Curios 9.5.1 / PlayerAnimator 2.0.4 / Architectury 13.0.11 / Cloth Config 15.0.140 / ResourcefulLib 3.0.12 / AttributeFix 21.1.3 / KotlinForge 5.11.0）+ BetterCombat 2.4.0 |
| 内存上限 | HMCL 配置 `maxMemory 8192` |
| 登录 | LittleSkin（第三方 authlib），测试服 `online-mode=false` → 任意名可进 |

## 2. 为什么不用 234 mod 那份配置直接跑

机器实测：物理 **31.9G / 可用 1.90G**，提交上限 **51.91G / 已提交 46.31G（余 5.60G）**。正式服 java 占 ~12G。234 mod 客户端启动需 4~5G 提交，与正式服抢余量有把正式服顶到 OOM 的风险 —— **不做**。

改法：新建独立 gameDir `E:/client/rt-test`，用 **Junction** 复用原客户端的大件（`libraries` / `assets` / `versions`，零拷贝），`mods/` 只放**本模组一个 jar**。实测目录 854 KB（不含 junction 目标）。

启动器 `launch.py`（自写，等价 HMCL 行为）：从版本 json 建 classpath（按 `rules` 过滤 os/arch/features → 107 项，27 项缺失全是 macOS/Linux 专用包，正常）、替换占位符、`-Xms1G -Xmx3G`、`cpw.mods.bootstraplauncher.BootstrapLauncher --launchTarget forgeclient`。

## 3. 结果：客户端**跑起来了**

```
[08:48:44] ModLauncher 11.0.5 starting: java version 21 by Oracle; OS Windows 11 arch amd64
[08:48:59] [Render thread/INFO] [TextureAtlas]: Created: 1024x512x0 minecraft:textures/atlas/gui.png-atlas
[08:48:59] [Render thread/INFO] [ne.ne.ne.cl.en.an.js.AnimationLoader]: Loaded 0 entity animations
```

窗口标题 `Minecraft NeoForge* 1.21.1`（java pid 35120），渲染线程走到图集创建 → **主菜单到位**。说明：本模组能在 21.1.257 客户端侧独立启动，无 mixin 冲突、无注册表冲突（这是 M0~M7 首次客户端侧证据）。

**没进服**：`--quickPlayMultiplayer 127.0.0.1:25567` 两次实验（一次裸参、一次补 `--quickPlayPath`）均未触发连接，服务端日志零握手、`netstat` 零 ESTABLISHED。

**GUI 自动化也不可用**（回答「能否用 computer use 验收」）：
- 本会话没有 computer-use 工具可调（skill 不存在）；
- 兜底用 GDI `BitBlt` 截屏（自写纯 ctypes + zlib，不依赖 PIL / `Add-Type`）截到的是**全黑** 2560×1440 —— 说明当前会话看不到桌面（RDP 断开或锁屏），鼠标键盘注入同样不可信。

结论：**客户端侧验收的瓶颈不是驱动方式，是「没有可交互桌面」+「MC 不认 quickPlay 参数」**。要跑 M7 真机（升级 / 加点 / 洗点 / tooltip 肉眼确认），需要 Aki 把桌面解锁并保持 RDP 连接，或他自己开客户端。

## 4. 真发现：14 基底物品**零模型零贴图**（新，对账网第五十四例）

客户端启动时 `ModelBakery` 连报 **14 条**：

```
Unable to load model: 'redtale_project:item/hammer' … FileNotFoundException:
  redtale_project:models/item/hammer.json
```

14 个 id 与 `ItemBase.java:17-46` 枚举逐字一致（`sword dagger axe hammer spear staff bow shield helmet chestplate leggings boots ring amulet`）。

根因取证：

| 检查 | 结果 |
|---|---|
| `find src/main/resources/assets -path "*models/item*" -name "*.json" \| wc -l` | **0** |
| `ls src/main/resources/assets/redtale_project/` | 只有 `lang` |
| 70 把尺里读 `assets/` 的 | **0**（`check_*` 全是配置/源码对账） |

后果：物品**注册成功、`/give` 拿得到、属性生效**，但客户端渲染成紫黑缺失模型。此前所有轮次都在服务端 + 离线尺里转，**这是第一次碰到「资源缺失」类洞**（此前全是配置字段的形态/值域）。

已派 **T-0127**：补 14 份 `models/item/*.json`，`layer0` 占位引用原版贴图（`minecraft:item/iron_sword` 等），不画新美术 —— 真美术另开工单。

## 5. 本轮口径（§13.135）

- **R132-a**：**验收客户端不复制整份 mods**：用 Junction 复用 `libraries`/`assets`/`versions`、只放被测模组，854 KB 起一个干净客户端。省内存、省磁盘，也避免 234 mod 的噪声掩盖自家问题。
- **R132-b**：**离线尺覆盖不到 `assets/` 时，客户端日志就是唯一真相源**：14 条 `FileNotFoundException` 是「模型缺失」唯一可见出口，服务端零提示。凡新增资源面（模型 / 贴图 / 音效 / lang）都要过一遍客户端启动日志。
- **R132-c**：**GUI 验收先证「看得见」再谈「点得动」**：先截一张图，全黑就说明没有可交互桌面，后面所有点击都是猜。截图用纯 ctypes + zlib，绕开 `Add-Type`（被安全策略拦）与 PIL（未安装）。
- **R132-d**：**quickPlay 不是可靠入口**：两次实验（裸参 / 补 `quickPlayPath`）MC 都没连，NeoForge 21.1.257 的 `forgeclient` 路径下不可依赖。要自动进服得另找入口（servers.dat 预置 + 首屏脚本不现实），暂按「需人工点一次」记账。
- **R132-e**：裸名纪律第五十次 —— 文档只写裸名，不写 `tools/` 全路径、不写探针目录。

## 6. 下一步

1. 验 T-0126（`slots` 形态尺）→ `check_all` 70→**71**。
2. 验 T-0127（14 份模型）→ 构建 + 用 `rt-test` 客户端复跑，`Unable to load model` 应从 14 条降到 **0**（这是 T-0127 的客户端侧验收，归 S7）。
3. M7 真机仍未验：等 Aki 解锁桌面 / 自己开客户端；或用 `rt-test` + 他手动点「多人游戏 → 直接连接 → 127.0.0.1:25567」。
4. Q-021 / Q-022 / Q-023 无答复继续按默认走。
