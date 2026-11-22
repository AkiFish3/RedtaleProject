# R105 · 派 T-0100（消耗品 use.script args 键名）+ 冻结 6 只缺失原型字段

时间：2026-11-22 22:5x · 队列：S7·移植工程（兼 S8 派单/验收）

## 验收：无卡可验，48 全绿守底

卡板 `[DONE]` 0 张、`OPEN`/`DOING`/`BOUNCED` 0 张。基线 `python check_all.py` → `共 48 个脚本 · 跑成 48 · 跳过 0 · 红 0` EXIT=0。本轮不接线（`check_use_args.py` 尚未落地）。

## 派单 T-0100（对账网第三十一例）

四满足判据逐条核过：

| 判据 | 证据 |
|---|---|
| 旧尺全绿 | `check_arg_keys.py:78-93,144-155` 只扫 `skills/` + `scripts/` 的 `conditions`/`effects`；`check_use_script.py:60-85` 只核 `use.script[].effect` id（`:81-82` 带冒号直接 continue） |
| 运行期静默 | `ConsumableSpec.java:123-155` 把 `args` 原样入 `ScriptCall`；`ScriptEngine.runCalls:210-215` 不查键；`MessageEffect.java:30` 缺 `text` 静默 return、`ConsoleEffect.java:26` 缺 `text` 用「(空)」，零 WARN |
| 合法集可冻结 | T-0099 的 16 原语 `ARG_KEYS` 原样复用，S8 不读源码、不推断 |
| 基线非空 | `demo_stew.json5:24-25` 两条调用（message + console_log），离线核 7 份物品 / 2 条带 args / 0 问题 |

卡面已写死独占文件（`check_use_args.py` 单文件）、5 个接口签名、三条机器验收（基线 + 六夹具 + 静态/读失败）。六夹具 A~F 覆盖写歪键、合法键、`type` 别名、带冒号与未知原语跳过、跨文件排序。禁止改 `check_all.py`；VERIFY 后接线由 S7 做（48→49）。

同轮逐条排除：`professions.grants` / `spawn_rules.match`（原版 id/tag 无离线真值）、`exclusive_with`（基线 0）、`exclusive_group`（有运行期冲突状态）、`set_id`（出口明确）、`repair.targets`（空基线）、附魔 `scripted.args` 数值（T-0090 已覆盖）、`use.effects` / `consume`（clamp + WARN）、`mobs.skills` chance/cooldown（clamp，次优候选留池）、技能 cost / attack_speed / display / spawn（已有尺）。

## S7 最小一步：冻结 6 只缺失原型的完整字段（只定字段，不写配置）

R104 盘点出 8 原型仅 `melee`（ember_guard 用 elite 顶）与 `caster` 有默认怪。本轮按 `MobSpec.Definition` 与现有两份配置逐字定死剩余 6 只，避免 S8 发明内容。共同约束：`drops` 省略（走原版掉落，不新建掉落表）；`skills[].skill` 用已存在 4 个完整 id；装备值用 14 基底注册 id（`redtale_project:` 前缀）；`scale ∈ [0.25,4]`、`level ∈ [1,1000]`；`undead` 只在该原型需要时置 true（tag 粒度 = 原型）。

| id | name | base | scale | level | undead | attributes | equipment | skills |
|---|---|---|---|---|---|---|---|---|
| `ash_archer` | 灰烬弓手 | ranged | 1.0 | 6 | false | max_health {base:18,per_item_level:2}, attack_damage 5, movement_speed 0.03 | mainhand=bow | arc_bolt 160/0.30 |
| `grave_caller` | 唤冢者 | summoner | 1.05 | 14 | true | max_health {base:32,per_item_level:3}, max_mana 120, mana_regeneration 2.5 | mainhand=staff | quake_slam 240/0.35 |
| `ember_charger` | 焰锋冲锋者 | charger | 1.2 | 10 | false | max_health {base:40,per_item_level:3}, attack_damage 8, movement_speed 0.05 | mainhand=axe | gale_slash 140/0.45 |
| `cinder_wisp` | 烬灵 | flyer | 0.8 | 5 | false | max_health {base:14,per_item_level:1}, movement_speed 0.06, magical_damage 0.15 | 空 | arc_bolt 200/0.30 |
| `cinder_warden` | 烬卫精英 | elite | 1.35 | 18 | false | max_health {base:90,per_item_level:6}, attack_damage 12, armor 9 | mainhand=hammer, chest=chestplate | quake_slam 180/0.40 |
| `ember_mote` | 焰尘 | ambient | 0.6 | 1 | false | max_health 8, movement_speed 0.02 | 空 | 空 |

技能列格式 `完整 id / cooldown_ticks / chance`，id 一律 `redtale_project:` 前缀。落地时还需同步 `gen_entity_lang.py` 产物（新增 6 组 zh/en 键）；本轮未写任何 `src/` 文件。

## 口径（PLAN §13.108 R105-a~e）

- a：**对账网第 31 例 = 第三处 ScriptCall 调用点**——skills / scripts / items 三条调用路径，`ARG_KEYS` 尺必须覆盖每一处；只核其中两处等于没核。
- b：**冻结表跨卡复用不重推**——T-0100 原样继承 T-0099 的 16 原语签名字面量，合法集不因调用点变化而扩张。
- c：**同调用点多原语不拆卡**——`message` 静默 return 强于 `console_log` 兜底「(空)」（R98-b 静默分级），但两者共用冻结表与文件，拆开只会造两张互相依赖的空卡。
- d：**内容面冻结先于派单**——6 只缺失原型字段由 S7 先定死，后续机械填充卡不得自行取舍（R104 遗留项结案）。
- e：**裸名纪律第二十三次**——本轮在建脚本与文档只写裸名 `check_use_args.py`，避免文档路径检查把未建文件当断链。

## 环境

`src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服常驻未动、测试服 25567/25577 无 LISTEN。
