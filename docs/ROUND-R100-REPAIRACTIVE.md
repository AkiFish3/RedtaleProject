# R100 · 验 T-0095 + check_all 43→44 + 派 T-0096（修理模式激活字段有效性对账）

- 时间：2026-11-22 04:5x（S7 同频轮）
- 工作目录：`E:\RedtaleProject`

## 1. 验收 T-0095（`tools/check_affix_pool_refs.py`）→ VERIFIED

S8 于 04:17 报完成（121 行）。S7 独立复跑三条，全过：

1. 基线：`python tools/check_affix_pool_refs.py` → `共 1 份词条池引用表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 1 份词条池 · 3 条词条定义 · 3 条词条引用`。
2. 六夹具 A~F（S7 自造独立探针，每次从干净目录重来，用完即删）：
   - A 定义裸名 `sharp` + 池引用 `redtale_project:sharp`（加自家 ns）→ 1 条 EXIT=1
   - B 裸名引用 → 0 条 EXIT=0
   - C `redtale_projectX:sharp`（写歪 ns）→ 1 条 EXIT=1
   - D 两定义两引用 → 0 条 EXIT=0 + `-v` 1 池/2 定义/2 引用
   - E 池无 prefix/suffix/core 段 → 0 条 EXIT=0
   - F 两池一合法一写歪 → 1 条（q 的）+ `-v` 2 池/2 定义/2 引用 EXIT=1
   - 逐字 `EXIT=1/0/1/0/0/1`，六组全 PASS。
3. 静态：`wc`=121≤200；`def unmatched`=1；禁用 grep（`check_all.*append|net\.minecraft|subprocess|exec\(|\.java|check_items|check_affix_reach|split(":")|rsplit|path_of|ident(`）=0；`py_compile` OK；`--dir NO_SUCH` → `affixes/affix_pools 目录读取失败` 1 条 EXIT=1 不抛栈。

S8 自测与 S7 独立复跑逐字一致，零偏差不打回。探针与 pyc 已清。

## 2. 接线 check_all 43→44

`tools/check_all.py` TOOLS 元组末尾追加 `check_affix_pool_refs.py`（R100 接）。接线后：

- `python tools/check_all.py` → `共 44 个脚本 · 跑成 44 · 跳过 0 · 红 0` EXIT=0。
- 七门禁全 0：`check_checkall` 1 清单/0、`check_tasks` 96 卡在制品 1、`check_contracts` 60 条、`check_docs` 115 份/485 引用、`check_rounds`/`check_ownership` 0。

## 3. 派 T-0096（对账网第二十七例 = 互斥模式 ↔ 激活字段一致性）

### 3.1 派前全类排除留证

只读调研扫 43 个 check 的实际读取键、defaults 全部 36 份 json5、运行期 Java，逐条排除：

| 候选 | 排除/选中理由 |
|---|---|
| drops / set_id / spawn_rules.match / repair.targets | R99-b 四项复评结论不变：出口明确 / 原版 registry 不同源 / 默认空基线永远绿 → 继续留池 |
| sets[].pieces 完整 ns | `check_items:127-130` + `check_set_reach:29-33,92` 两端归一，但 `SetBonus.apply` 按 set_id 计件、`ValidateCommand:116-125` 显式输出 → 非静默 |
| skill_trees unlock_skill / items.use.effects / mobs.attributes / 附魔 scripted.script | 运行期有 WARN 或已有 check → 排除 |
| consume / restore_food / saturation / cooldown_ticks / points_per_level | 有 clamp / WARN，或属设计值非引用 → 排除 |
| message.text / console_log.text 缺失 | 静默但仅文案缺失、非玩法退化 → 排除 |
| 原版类引用（use.effects 等） | 需 vanilla registry，离线无真值 → 排除 |
| **repair.mode ↔ amount/percent 激活字段** | **选中**：`grep -lE '"repair"|repair\.' tools/check_*.py` = 0（43 个 check 无一读 repair）；运行期合法 mode + 激活字段 0/缺失 → `ALREADY_FULL` 零日志 |

### 3.2 源码逐字核实（R96-b：以源码为准，不采信调研初稿）

- `RepairSpec.java:73-84`：`mode` 只校验二选一 `amount`/`percent`，缺省 `amount`；非法值 WARN + 回落（**非本洞**）。
- `RepairSpec.java:87-94`：`amount` 缺省 0 且**零告警**；仅 `<0` 才 WARN + 归 0。
- `RepairSpec.java:97-105`：`percent` 缺省 0.0 且**零告警**；仅越界才 clamp + WARN。
- `Repairer.java:72-77`：`wanted = mode=="percent" ? ceil(maxDamage*percent) : amount`；`wanted<=0` → `Status.ALREADY_FULL`，注释明写「定义合法但修不动」，零 WARN 零异常。
- `ContentItem.java:142-161`：只按 `result.applied()` 扣件/回显，失败仅泛化玩家提示；`:164-169` `notify()` 只发玩家连接、不写 console。
- `RedtaleCore.java:307-309` 虽会 LOG.warn warnings，但本场景**根本不产生 warning** → 日志全静。

典型事故：把 `demo_whetstone.json5:16` 的 `mode: "amount"` 改成 `"percent"`，忘写 `percent: 0.25`（只留 `amount: 120`）→ 磨刀石永远修不动，全 check 绿。

### 3.3 离线核基线

```
json5 total: 36   items total: 7
items with repair: 1  ->  items/demo_whetstone.json5
  mode=amount  amount=120  percent=None  targets=[]
repair 读取 check 数: 0（grep -lE '"repair"|repair\.' tools/check_*.py）
```

### 3.4 卡面要点

- 独占 `check_repair_active.py`（≤200 行、零接线），六夹具 + 三条机器验收。
- 接口 `read_items` / `check_repair` / `main` 由 S7 定死；语义从 Java 冻结进卡面（mode 非法/percent>1/负数跳过，激活字段缺失/非数字/<=0 才报，amount 额外要求整数），S8 不读源码。
- 基线 7 物品 / 1 repair 段 / 0 问题；在制品 1/3（T-0096 OPEN）。

## 4. 环境 / 收尾

- `src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服 PID 9376（25565/25575）常驻未动、测试服 25567/25577 无 LISTEN。
- 裸名纪律第十八次：派单当轮文档与台账在建脚本一律裸名 `check_repair_active.py`，不写 `tools/` 全路径。
- Q-021（`respec` 免费）/ Q-022（原语按需补，13/25）无答复，按默认走；真人窗口 A~G 等 Aki。
