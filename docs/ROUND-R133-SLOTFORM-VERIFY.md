# R133 · 2026-11-26 11:3x~11:5x · 验收 T-0126（附魔 `slots` 形态）+ 接线 `check_all` 70→71 + 派 T-0128（D1 武器线两把）

## 1. 卡板

- **T-0126 `[DONE]` → `[VERIFIED]`**：S8 于 11:05 交付（197 行，未接线，符合卡面禁令）。S7 逐条复跑三条验收，全部逐字对上，零偏差**不打回**。
- **派 T-0128**（`items/demo_dagger.json5` + `items/demo_warhammer.json5` + `ConfigStore.java` 恰 +3 行），在制品 **2/3**。
- 在制品现状：T-0127（14 份 `models/item`，R132 派，S8 尚未动）+ T-0128（本轮派）。S8 本轮 11:05 有交付 → 未触发静默回收。

## 2. 验收细节（T-0126，逐字）

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1a | `python tools/check_enchant_slot_forms.py` | 末行 `共 1 份附魔槽位字段口径对账表 · 0 条问题` EXIT=0 | ✅ 逐字 |
| 1b | 同上 `-v` | 含 `已检查 2 份附魔 · 2 处 slots 引用 · 形态 非空数组 + 元素非空字符串` | ✅ 逐字 |
| 2 | 六夹具 A~F（干净 `enchantments/` 副本） | EXIT=0/1/1/1/1/1，文案见卡面 | ✅ B `值 'mainhand' 不是数组`、C `是空数组`、D `第 2 项 值 1`、E `第 1 项 值 '  '`、F 三条 sorted（`soul_reap`→`super_sharpness`→`zz`） |
| 3a | `wc -l` / `def check_slots` / 禁词 grep / `py_compile` | 197≤200 / 1 / 0 / OK | ✅ 全过 |
| 3b | `--dir NO_SUCH` | EXIT=1、恰 1 条 `[问题] enchantments 目录读取失败`、末行 `1 条问题`、不抛栈 | ✅ 逐字 |
| 3c | 函数边界（直接 import 调 `check_slots`） | 卡面 14 组 | ✅ 0 fails：非数组 `"mainhand"`/`1`/`True` 各 1；`None` 跳过；`[]` 1；合法 0；坏元素 1 起序号；`[1,2]` 2；嵌套 1；缺键/非 dict 0；重复不合并；`sorted` |
| 3d | shim（JSON5 裸键 + 注释 + 单引号 + 尾逗号） | 能解析 | ✅ `{"id":"x","a":[1,2]}`；顶层数组 → `None` |

夹具 `build/enchslotfix/` 用完即删。

> 一处自查：`check_slots` 对 `slots: None` 跳过（卡面写「缺键或为 `None` → 跳过」），与 T-0125 的 `None` 处理口径一致，非漏判。

## 3. 接线

`tools/check_all.py` 清单末尾追加 `check_enchant_slot_forms.py` → `共 71 个脚本 · 跑成 71 · 跳过 0 · 红 0`。

## 4. 派前取证（T-0128）

- **内容缺口**：`defaults/items/` 7 份里只有 `demo_longsword` 一件武器（基底 `sword`）；`dagger` / `hammer` 零内容 → D15 动作只取样过 `bettercombat:claymore`、D20 tooltip 攻速只有一档、D19 攻速来源表没有「快武器」对照。
- **id 真伪**：`unzip -l "[冒险]bettercombat-neoforge-2.4.0+1.21.1.jar"` 现查 `data/bettercombat/weapon_attributes/` 下确有 `dagger.json` 与 `hammer.json` → `bettercombat:dagger` / `bettercombat:hammer` 是真实 id，非推断。
- **派前离线预验**：`build/precheck_items/defaults`（整份 defaults 副本 + 两份新 JSON5）跑 `check_items --dir` → `共 14 个文件 · 0 条问题` EXIT=0；`check_numeric` / `check_name_coercions` / `check_socket_discards` / `check_gem_types` 全 0。副本已删。
- **属性 id 核对**：`critical_strike_power`（非 `critical_strike_damage`）取自 `AttributeSpecs.java:59`，`critical_strike_chance` 取自 `:58`。
- **R125-a 教训前置**：卡面写死 `ConfigStore.DEFAULT_FILES` 三行插入位置与缩进，并要求 `git diff --stat` = `3 insertions(+)`，避免「磁盘 44 · 清单 42」必红。
- **死路记录**：先按上轮「下一步」探 `skills/` 的 `cooldown_ticks` / `mana_cost` 静默透传 —— 读 `SkillSpec.java:119-157` 后确认两键**已有完整 WARN + 夹取**（非数字 / 负数 / 非有限 / 超硬顶四条 WARN），无洞可挖，故弃。

## 5. 口径（§13.136）

| 编号 | 口径 |
|---|---|
| R133-a | **内容卡也要先证「内容真的缺」**：先数 `defaults/` 各子目录条数（`items` 7 / `sets` 1 / `skills` 4 / `mobs` 8），再谈补哪一格；不凭印象补。 |
| R133-b | **引用型内容必须先查被引对象的真实 id**：`weapon_attributes` 的 id 从上游 jar 的 `data/*/weapon_attributes/*.json` 现查，不从文档或记忆里拿。 |
| R133-c | **补内容必须同时补清单**：`ConfigStore.DEFAULT_FILES` 是 `check_defaults` 的唯一真相源，卡面直接写死插入行数（+3）与 `git diff --stat` 断言。 |
| R133-d | **「有 WARN 的解析器」不算洞**：`SkillSpec` 的 `cooldown_ticks` / `mana_cost` 已有四条 WARN + 夹取，属 D16 允许的异常保护；对账网只收「零 WARN 静默」。 |
| R133-e | **裸名纪律第五十一次**：文档只写裸名，不写 `tools/` 全路径、不写探针目录。 |

## 6. 环境

- `src/` 零 diff（本轮未改 Java）；纯 Python + 文档；**未构建**（无 Java 改动）；未启停任何服（测试服 25567/25577 无 LISTEN 由本轮开工时确认）；无构建锁；正式服 9376 未触碰。
- 七门禁：`check_all` 71/71 红 0、`check_tasks` 128 卡在制品 2、`check_contracts` 112 条、`check_docs` 149 份、`check_ownership` / `check_rounds` / `check_checkall` 全 0。

## 7. 下一步

1. **验 T-0127**（14 份模型）→ 构建 + `rt-test` 客户端复跑，`Unable to load model` 应从 14 条降到 **0**。
2. **验 T-0128** → 构建 + 测试服 `item list` / `reload` 看两把新武器进内容表。
3. M6 代码面剩「模板实体注册 / 自然刷怪替换」（S7 独占、跨文件）；M7 真机需 Aki 在线或手动进服。
4. Q-021 / Q-022 / Q-023 无答复继续按默认走。
