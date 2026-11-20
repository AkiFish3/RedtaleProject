# R89 · 验收 T-0084 + check_all 32→33 + 派 T-0085（消耗品 use.script 原语 id 对账）

时间：2026-11-21 05:2x~05:3x · S7（Kite 🪁）。纯 Python 轮次，`src/` 零 diff、未构建、未启停任何服、无构建锁。

## 1. 验收 T-0084（`check_mob_equipment.py`）

`[DONE]` → **`[VERIFIED]`**。三条验收 + S7 独立边界 15/15：

1. 基线 `python tools/check_mob_equipment.py` → 末行 `共 1 份怪物装备 id 表 · 0 条问题` EXIT=0；`-v` → `已检查 2 份怪物 · 4 处装备引用 · 14 个基底`。
2. 六夹具独立干净目录逐字：A `redtale_project:ghost_sword`=1 条未命中、B `minecraft:diamond_helmet`=0、C `mainhand:3`=1 条必须字符串、D `staff`（无冒号）=1 条必须带命名空间、E 空槽=0、F `wings` 未知槽名=1 条；EXIT=1/0/1/1/0/1。
3. wc=137≤200、`def check_equipment`=1、禁用 grep=0、`py_compile` OK、`--mobs NO_SUCH` 恰好 1 条 `mobs目录读取失败` EXIT=1 不抛栈。

S7 边界 15/15：data=None / 缺失 / None / 空 dict / 非 dict / 未知槽+坏 id 双报 / 14 基底合法 / 外部命名空间跳过 / 多冒号 path / 排序稳定 / value=0 / value=False / 单引号 shim+注释 / 解析失败记 None / None 报配置失败。

**裁定 S8 口径备注采纳不打回**（§13.92 R89-a）：卡面「空」按 `null` 合法、未知槽仍核其 item 双报，与卡面「仍需核其 item」一致。`build/equipfix/` 已清。

## 2. 接线 check_all 32→33

`tools/check_all.py` TOOLS 末尾追加 `check_mob_equipment.py`（R89 接上）。复跑 `共 33 个脚本 · 跑成 33 · 跳过 0 · 红 0`。

## 3. 派 T-0085（`check_use_script.py`，单文件零接线）

在制品 0 → 1/3。真洞定位（R88-c 派前全类排除留证）：

- **唯一真洞**：`items/*.json5` 内嵌 `use.script[].effect`（自研原语 id）。`check_items.check_item` 只核 base/rarity/stats/gem/set_id/abilities/sockets/weapon_attributes/rev，从不读 `use` 段；`check_scripts.collect` 只扫 `scripts/`、`check_skills.collect` 只扫 `skills/`、`check_effect_target` 只扫脚本/技能 effects——items 里这段无人核。运行期 `ContentItem` 把 `use.script` 交 `ScriptEngine.runCalls`，未知原语只 `log.warn("effect {} 未注册（已跳过）")` 且 `done` 不增、返回值被丢弃 → 喝汤脚本静默不跑、全 check 绿、只在日志里一次 WARN（R83-b/R88-b 对账网第十六例）。
- **逐条排除的候选**：affix `kind` 非法 → `AffixPools.parseDefs` 有 WARN+跳过（非静默洞）；`consume` / `restore_food` / `saturation` / `cooldown_ticks` → `ConsumableSpec` 有 clamp+WARN（R85-d 排除）；`use.effects[].effect` → 原版 MobEffect id，离线无注册表（同 damage_type 外部命名空间口径）；`use.script[].args` 值域 → 既有原语层管；`sockets.types` → 设计上开放词汇（`SocketSpec` 明确不枚举校验）；`enchanted`/`affected` → 原版 `EnchantmentEntityEffect.apply` 位置参，非配置项。
- 基线 S7 离线核：7 份物品、`demo_stew` 1 份带 2 条引用（`message`/`console_log`）、0 问题；合法集 = `planning()`（SKILLS §4 25 条）∪ `EFFECTS`（13）= 27。

卡面写死：独占文件（新建 `check_use_script.py` + `build/usescriptfix/` 夹具）、接口（`planned`/`VALID`/`read_items`/`check_use_script`/`main`）、六夹具 A~F、三条可机器执行验收。落 `docs/TASKS.md` §5 T-0085 + §6 池 C35、`docs/CONTRACTS.md` §4.49。

## 4. 状态与下一步

- 全套 0 问题：`check_all` 33/33、`check_tasks` 85 卡在制品 1、`check_docs` 104 份/426 引用、`check_contracts` 49 条、`check_rounds`、`check_ownership`。
- 正式服 9376 常驻未动；测试服 25567/25577 无 LISTEN。
- 下轮：验 T-0085（过后 `check_all` 33→34）；对账网已十六例，按 R80-b/R84-b/R86-b/R87-a/R88-a/R89-b 判据核实后无真洞就转内容面，不硬凑。真人窗口 A~G 与 Q-021/022 仍等 Aki。
