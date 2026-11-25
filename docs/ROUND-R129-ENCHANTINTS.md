# R129 · 验 T-0123 + check_all 67→68 + 派 T-0124（附魔定义整数字段透传）

日期：2026-11-25 23:2x~23:4x
在制品：1/3（T-0124）
构建：未构建（本轮纯 Python + 卡板，无源码改动）
环境：`src/` 零 diff、未启停任何服、无构建锁；正式服未触碰。

## 1. 验收 T-0123（`check_progress_base.py`）→ VERIFIED

S8 交付 `tools/check_progress_base.py` 191 行，三条验收逐条跑过。

**验收 1（基线）**：

```
$ python tools/check_progress_base.py
共 1 份进度曲线起点经验口径对账表 · 0 条问题          EXIT=0
$ python tools/check_progress_base.py -v
已检查 3 份进度曲线 · 3 处 base 引用 · 缺省 100.0 · 硬顶 1000000000
共 1 份进度曲线起点经验口径对账表 · 0 条问题          EXIT=0
```

**验收 2（六夹具 A~F，干净 `levels/` + `jobs/` 副本）**：

| 组 | 改动 | 结果 | EXIT |
|---|---|---|---|
| A | 默认副本 | 0 条；`-v` 3/3 | 0 |
| B | `levels/main.json5` `base: 1000000001` | 1 条 `[问题] levels/main.json5: curve.base 值 1000000001 …` | 1 |
| C | `jobs/warrior.json5` `base: 2000000000` | 1 条 `[问题] jobs/warrior.json5: …` | 1 |
| D | `levels/main.json5` `base: 0` | 0 条（`v <= 0` 有 WARN，本尺不收） | 0 |
| E | `jobs/miner.json5` `base: 1000000000`（恰好等于硬顶） | 0 条；`-v` 3/3 | 0 |
| F | B + C + `jobs/zz.json5` `base: 1000000000000` | 3 条按 `jobs/warrior.json5` → `jobs/zz.json5` → `levels/main.json5` | 1 |

**验收 3（静态 + 边界）**：`wc -l` = 191 ≤ 200；`def check_base` = 1；禁词 grep = 0（`check_all|net.minecraft|subprocess|exec(|.java|import check_|check_progress|check_profession|path_of`）；`py_compile` OK；`--dir NO_SUCH` → 恰好 2 条 `[问题]`（`levels` 在前、`jobs` 在后）+ 末行 `2 条问题`、EXIT=1、不抛栈。

函数边界（直接 import 调 `check_base`）：`1000000001` / `2000000000` / `1e12` 各 1 条（`repr(1e12)` = `1000000000000.0`）；`1000000000` / `100.0` / `0` / `-5` / `True` / `"1000000001"` / `None` / `nan` / `inf` / 缺 `base` / `curve` 非 dict / data 非 dict 全 0 条；重复两条不合并（2 条）；输出 `sorted`。夹具已删。

**判定**：S8 零偏差，不打回。`[DONE]` → `[VERIFIED]`。

## 2. 接线 `check_all` 67→68

`tools/check_all.py` TOOLS 末尾追加一行（R129 接上）：

```
    "check_progress_base.py",        # 进度曲线安全网：levels/jobs curve.base 超硬顶静默夹顶 —— R129 接上
```

跑全量：`共 68 个脚本 · 跑成 68 · 跳过 0 · 红 0`；`--only progress_base` → `共 1 个脚本 · 跑成 1 · 跳过 0 · 红 0`。

## 3. 派单 T-0124（对账网第五十一例）

在制品 0 → 派 1 张。按 R128-c/下一步第 3 条过滤（只要「显式值与源码常量/类型直接对比」的单字段卡，不复算运行期数学、不要原版注册表离线真值），落 `enchantments/` 的 `weight` / `anvil_cost`。

**真洞取证（源码逐字）**：

- `EnchantmentGenerator.java:39-40`：`PASS_THROUGH` 把 `max_level` / `weight` / `anvil_cost` 三键一起列入透传。
- `EnchantmentGenerator.java:151`：对这三个键只核 `value.isJsonPrimitive() && …getAsJsonPrimitive().isNumber()`，**不核整数形态**。
- `EnchantmentGenerator.java:160-163`：`numericKey()` 把三键当同一族（注释原文「必须是数字」）。
- `EnchantmentGenerator.java:155`：`json.add(key, value)` 原样写进 datapack JSON。

**同族只有 `max_level` 有尺**：`check_enchants.py:113-115` 要求 `max_level` 是 `1~255` 的整数。`weight` / `anvil_cost` 全 tools 零读。

**旧尺盲区反事实（干净副本，跑完即删）**：把 `soul_reap` 的 `weight: 2` 改 `2.5`、`super_sharpness` 的 `anvil_cost: 2` 改 `3.0` 后 —— `check_enchants.py` `共 2 个文件 · 没有发现问题 ✓`、`check_numeric.py` `共 4 个文件 · 0 条问题`、`check_all.py --only enchant` `共 3 个脚本 · 跑成 3 · 跳过 0 · 红 0`。三把尺全绿，洞成立。

**基线（离线核过，卡面数字以此为准）**：`enchantments/` 2 份文件、`weight` + `anvil_cost` 共 **4 处**、0 条问题。

**证据等级自限**：本卡只冻结「非整数形态必须报」这一形态判据（离线可判），卡面明写「原版 codec 整条不收」为**推定**，不作为验收项；真机复现归 S7 后续，不塞进 S8 的卡。

## 4. 卡面结构自检（R126-a / R128-a）

- 本卡无「正文行数 = 代码块行数」类要求（不加 `ConfigStore` 行、不新增内容文件），故不触发 R125-a / R126-a。
- 卡面无手打缩进代码块；夹具为「拷默认目录 + 逐字替换单行」，与 R128-a 口径一致。
- 接口（常量 / `_loads` / `read_files` / `check_ints` / `main` / 文案 / 末行 / `-v` 行）全部由 S7 定死，S8 不推断。

## 5. 门禁

七门禁全 0：`check_all` 68/68/0/0、`check_tasks` 124 卡在制品 1、`check_contracts`、`check_docs`、`check_rounds`、`check_ownership`、`check_checkall`。

## 6. 下一步（R130）

1. 验 T-0124（基线 `共 1 份附魔定义整数字段口径对账表 · 0 条问题` + `-v` 2/4 + 六夹具 A~F + `--dir NO_SUCH` 1 条读失败），过后接线 `check_all` 68→**69**。
2. M6 代码面剩「模板实体注册 / 自然刷怪替换」（S7 独占，跨文件）；M7 代码面已实现，缺真机验证（升级 / 加点 / 洗点），需 Aki 在线。
3. Q-021 / Q-022 / Q-023 无答复继续按默认走；真人窗口 A~F 等 Aki。
