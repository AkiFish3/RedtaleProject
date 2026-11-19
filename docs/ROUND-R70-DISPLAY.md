# R70 · 验收 T-0065（`/redtale display`）+ Q-023 收口 + 派 T-0066

时间：2026-11-19 17:07-17:2x（S7）。里程碑：M0~M7 主体全落地，余真人验收窗口 A~G。

## 1. 卡板

- **验 T-0065 → VERIFIED**（3/3）：
  - ①取锁 `gradle build --offline` RC=0（UP-TO-DATE，S8 已编译同一份源码、无改动）、jar 内 `DisplayCommand.class`=1、jar **681267 B**、`wc`=38≤200。
  - ②`probe_run DisplayCommandProbe` 末行 `全绿`、`TOTAL 13/13`（四组文案 + node 结构 + 反射签名）。
  - ③禁用 API `Files.|Path.of|writeString`=0、`RedtaleCommand` numstat `1 0`、`git status` 除卡上 3 文件外无其他改动。
  - **测试服那条（S7 跑）**：1 次启停 `Done (1.965s)`，已停、25567/25577 无 LISTEN，正式服 25565（pid 9376）全程未动。RCON 四组逐字：
    - 默认：`[redtale] 显示口径：DPS 关 · 属性明细 关 · 假想目标 minecraft:zombie · 来源 display.json5`
    - 两开关改 true + 目标 husk + reload：`DPS 开 · 属性明细 开 · 假想目标 minecraft:husk · 来源 display.json5`
    - 合法 JSON5 语义坏（`show_dps:1` + `'Minecraft Zombie'` + 空目标 + `bogus_key`）reload → 回落默认且恰多一行 `  本次 reload 告警 3 条（/redtale reload 看详情）`
    - 还原 + reload → 默认零告警；`reload` 回 40 份、`validate` 问题 0、redtale 零 ERROR。
    - 第三组 `source=""→未载入（默认值）` 由离线探针覆盖（测试服删文件会重建，走不到空串分支），按 R68-b 记「未验 + 原因」。
- **派 T-0066**（`mobs/SpawnBudget` 新建 ≤200，纯静态工具类，零接线）：收口 `per_tick_cap` 的双份真相 —— `SpawnSettings.perTickCap`（配置解析，1~4096，非法回落 8）与 `SpawnReplacer.setPerTickCap`（`Math.max(1,Math.min(4096,v))`）各写一份，改动一处忘另一处就会静默分叉。接口（`clamp`/`fromConfig`/`takePerTick`/`ticksToDrain`/`describe`）由 S7 定死；接线（两处改委托）S7 下轮做。验收 build+jar+wc / 探针 ≥30 / 禁 MC·禁 IO。`check_tasks` 基线 66 卡 / 在制品 1 / 问题 0。

## 2. Q-023 收口（D20 显示口径三层全落地）

| 层 | 件 | 状态 |
|---|---|---|
| 配置 | `config/redtale_project/display.json5`（三键 + 注释，默认件 `DEFAULT_FILES` 35→36） | R69 落地，R70 实测删文件/写歪均回落 |
| 解析 | `core/config/DisplaySpec`（`Settings` 快照，`source` 空串=未载入） | R69 验（T-0064） |
| 消费 | `ContentTooltip` DPS 行（`settled.value()*perSecond`）+ 属性明细行（`forEachModifier`） | R69 实测 `DPS ≈ 19.354 = 12.096×1.6` |
| 观测 | `core/command/DisplayCommand` + `/redtale display` | R70 验（T-0065），CONTRACTS §4.41 登记 |

剩余 A8 只差**真人客户端实际渲染**那一步（服务端口径与 lore 已 RCON 验）。

## 3. 环境

- 构建：锁取删 1 次；`build --offline` RC=0；jar 681267 B。
- 测试服：1 次启停（`Done (1.965s)`），已停、25567/25577 无 LISTEN；新 jar 已复制进 `E:/server-test/mods`（md5 与构建产物一致）。
- 检查：`check_tasks` 66 卡 / 在制品 1 / 问题 0 / 提示 0；`check_docs` 85 份 / 348 引用 / 0 问题；`check_contracts` 42 条 / 33 声明类 / 0 提示 / 0 问题（新增 §4.41 的 `DisplayCommand` 被识别）。

## 4. 待办

- 验 T-0066（S8 交付后）；接过 SpawnBudget 接线：`SpawnSettings.perTickCap` 与 `SpawnReplacer.setPerTickCap` 改委托纯类，`SpawnCommand cap` 文案可补「预计 N tick 排空」。
- Q-021（respec 扣费）/ Q-022（M5 原语范围）仍无 Aki 答复。
- 真人窗口 A~G 全组等 Aki 上线。
