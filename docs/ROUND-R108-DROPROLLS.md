# R108 · 2026-11-23 02:2x · 验 T-0102 / 接线 51 / 派 T-0103（掉落表 rolls 上界）

## 1. 验收 T-0102（`check_mob_scales.py`）

**结论：PASS 3/3 + S7 独立六夹具 6/6，S8 零偏差不打回。**

| 验收项 | 实测 | 期望 | 结果 |
|---|---|---|---|
| 基线 | `共 1 份怪物体积口径对账表 · 0 条问题` EXIT=0 | 同 | PASS |
| `-v` | `已检查 2 份怪物 · 2 处 scale 引用 · 边界 [0.25,4.0]` | 同 | PASS |
| 六夹具 A~F | EXIT=0/1/1/0/1/0 | 同 | PASS |
| 静态 | wc=166≤200、`def check_scales`=1、禁用 grep=0、`py_compile` OK | 同 | PASS |
| 读失败 | `--dir NO_SUCH` → `[问题] mobs目录读取失败` + 1 条问题 EXIT=1、不抛栈 | 同 | PASS |

S7 独立夹具逐字：A 1.15/1.0 → 0；B `0.1` → `one.json5: scale 值 0.1 不在 [0.25,4.0]`；C `0.24` → 同模板；D `0.25` → 0；E `a.json5: scale 值 5 …` + `b.json5: scale 值 True …` 按 a/b 顺序；F `0.25`/`4.0` → 0 且 `-v` 逐字 2/2。探针目录已清。

接线：`tools/check_all.py` TOOLS 50→51（`check_mob_scales.py`），`共 51 个脚本 · 跑成 51 · 红 0`。

## 2. 派 T-0103（对账网第 34 例）

**真洞**：`drops/*.json5` 表内 `drops[].rolls` 的上界静默夹取。

- 排除对象辨析：`mobs[].drops` 是引用字段，已由 `check_mobs.check_mob:96-98` 覆盖；本例是掉落表自身数值，不是同一对象。
- 旧尺：`tools/check_mobs.py:126-128` 只核 `rolls>=1` 整数。内存反事实 `rolls=2/16/17/100` 的 `check_drop()` 报错集合逐字相同（唯一一条 `gem_ruby` item 名与 rolls 无关）；`rolls=12.7`/`True` 才多一条旧尺红，属运行期有 WARN 形态 → 不重复造红线。
- 运行期：`src/main/java/com/redtale/project/mobs/DropSpec.java:22` `MAX_ROLLS=16`；`:91` `Math.max(1, Math.min(MAX_ROLLS, integer(...)))` 对合法整超界静默夹 16 零 WARN。同文件 `:60`（xp 负）、`:89`（max<min）、`:107`（非整数）均带 WARN，反证夹取出口有意静默。
- 基线：`src/main/resources/redtale/defaults/drops/ember_guard.json5` xp=12、rolls=2/1/1、1 张表 3 处引用 0 问题。
- 卡面冻结：`ROLLS_MIN=1` / `ROLLS_MAX=16`、消息模板、CLI（`--dir` / `-v`）、六夹具、三条验收；单文件 `check_drop_rolls.py` ≤200 行，未实现未接线，S8 禁改 TOOLS、禁读源码。

**同族留池**：`xp` 上界 1e6、`min` 负值归零、`max` 上界 64 同样静默改写，按 R107-a/R107-d 留池，待本卡验后复评。

## 3. 本轮口径（§13.111）

1. 引用字段 ≠ 表内数值，排除清单同名不误伤。
2. 同族静默夹取按字段拆分，先派最干净的 `rolls`。
3. 兄弟路径有 WARN 是「出口形态不同」的反证，不是合并理由。
4. 旧尺已报且运行期有 WARN 的形态不重复造红线。
5. 裸名纪律第二十六次。

## 4. 环境与门禁

- `src/` 零 diff；纯 Python；未构建、未启停任何服、无构建锁；正式服常驻未动，测试服 25567/25577 无 LISTEN。
- 七门禁：check_all 51/51、check_checkall、check_tasks（103 卡在制品 1）、check_contracts（70 条）、check_docs（124 份/517 引用）、check_rounds、check_ownership 全 0。
- 未拍板：Q-021 / Q-022 / Q-023 继续按默认走；真人窗口 A~F 等 Aki。
