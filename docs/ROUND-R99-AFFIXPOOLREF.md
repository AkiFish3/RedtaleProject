# R99 · 验 T-0094 + check_all 42→43 + 派 T-0095（词条池词条 id 完整字符串引用对账）

- 时间：2026-11-22 03:4x（S7 同频轮）
- 工作目录：`E:\RedtaleProject`

## 1. 验收 T-0094（`tools/check_mob_skills.py`）→ VERIFIED

S8 于 03:12 报完成（114 行）。S7 独立复跑验收命令，三条全过：

1. 基线：`python tools/check_mob_skills.py` → `共 1 份怪物技能引用表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 2 份怪物 · 4 条技能 id · 3 条 skills 引用`。
2. 六夹具 A~F（S7 自造独立探针，用完即删，每次从干净目录重来）：
   - A 怪绑 `redtale_projectX:gale_slash`（写歪 ns）→ 1 条 EXIT=1
   - B `redtale_project:gale_slash` → 0 条 EXIT=0
   - C `minecraft:gale_slash` → 1 条 EXIT=1
   - D 两技能两引用 → 0 条 EXIT=0 + `-v` 1 怪/2 技能/2 引用
   - E 怪无 skills 字段 → 0 条 EXIT=0
   - F 两怪一写一合法 → 1 条（h 的）+ `-v` 2 怪/2 技能/2 引用 EXIT=1
   - 逐字 `EXIT=1/0/1/0/0/1`，六组全 PASS。
3. 静态：`wc`=114≤200；`def unmatched`=1；禁用 grep（`check_all.*append|net\.minecraft|subprocess|exec\(|\.java|check_mobs|split(":")|path_of`）=0；`py_compile` OK；`--dir NO_SUCH` → `mobs/skills 目录读取失败` 1 条 EXIT=1 不抛栈。

S8 自测与 S7 独立复跑逐字一致，零偏差不打回。探针与 pyc 已清。

坑记录：`grep -c` 零匹配返回退出码 1，导致首轮 `&&` 链在禁用词断言处断开、py_compile/NO_SUCH 未执行。改为分组重跑后全过——非脚本问题，S7 探针组织问题。

## 2. 接线 check_all 42→43

`tools/check_all.py` TOOLS 元组末尾追加 `check_mob_skills.py`（R99 接）；接线前 `check_checkall.py` 报「脚本 check_mob_skills.py 未接进 check_all」1 条（预期）；接线后：
- `python tools/check_all.py` → `共 43 个脚本 · 跑成 43 · 跳过 0 · 红 0` EXIT=0。
- 七门禁全 0：`check_checkall` 1 清单/0、`check_tasks` 94 卡在制品 0、`check_contracts` 59 条、`check_docs` 114 份/479 引用、`check_rounds`/`check_ownership`/`check_primitives` 全 0。

## 3. 派 T-0095（对账网第二十六例 = 词条池词条 id 完整字符串引用）

### 3.1 派前全类排除留证

只读调研扫 `tools/check_*.py` 覆盖对象、defaults 全部 json5、运行期 Java，逐条排除：

| 候选 | 排除/选中理由 |
|---|---|
| drops 物品 id（`check_mobs` 归一） | 运行期 `MobDrops:96-99`/`:117-121` 有 `warnOnce` + 原版掉落兜底，静默形态弱 → **留池** |
| items set_id | `check_items:69-70` 存在性检查 + `ValidateCommand:123-125` 完整 key 双向核对，出口明确 → **留池** |
| spawn_rules.match[] | 比对值是 `BuiltInRegistries.ENTITY_TYPE`（原版 registry），离线不同源 → 排除 |
| repair.targets[] | `Repairer:96-120` 真静默无日志，但默认件 `targets: []`、基线 0 引用 → 永远绿红线，留池 |
| jobs[].exp_sources / skill_trees unlock_skill / items.use.effects / mobs.attributes / 附魔 scripted.script | 运行期均有 WARN 出口或已有 check 覆盖 → 排除 |
| **affix_pools prefix/suffix/core 段键** | **选中**：两把尺子都归一；运行期完整 key 取不到 → deadEntries → `Result.warnings()` 全仓无人读（R83-b + R88-b） |

### 3.2 源码逐字核实（R96-b：以源码为准）

- `AffixPools.resolveId:139-146`：定义 id 取 `string(root,"id",fallback,...)` **原文**，不归一 → 裸名 `sharp` 入表。
- `AffixPools.parseDefs:78`：`out.put(id, def)` 用完整 id 作 key。
- `AffixPools.parsePools:103-109`：段内键 `trim(e.getKey())` 原文作 `affixId` 入 entries。
- `AffixRoller.pick:133-138`：`defs.get(entry.affixId())` 完整 key，取不到 → `deadEntries.add`。
- `AffixRoller.roll:119-122`：deadEntries → `log.add("词条池 … 引用了未定义的词条 … 已跳过")` → `new Result(stats, picked, log)`。
- `ItemFactory:148`：调用四参重载（warnings=null，`AffixRoller:93` 自建局部 list）；`:117` 只读 `rolled.stats()`/`affixIds()`。
- 全仓 `.warnings()` 读取点只有 `ItemUpgradeCommand:68,88`、`SkillCommand:97`、`ItemUpgradeHooks:114`、`ItemUpgrader:133`，**无一处读 AffixRoller.Result.warnings()**。

两把尺子归一：`check_items.py:28` `path_of(obj["id"])`；`check_affix_reach.py:23-25` `ident(v)=v.rsplit(":",1)[-1]`、`:104` `key=ident(ref)`。

### 3.3 离线核基线

```
affix_defs=3 pools=1 refs=3 bad=0
ids= ['heavy', 'of_the_bear', 'sharp']
反事实：池 prefix 引用改 "redtale_project:sharp" → 完整 key 不命中，但两把旧尺仍绿。
```

配置：`affix_pools/sword_common.json5` 的 `prefix: { sharp: 100, heavy: 80 }` / `suffix: { of_the_bear: 60 }`，定义均为裸名。

### 3.4 卡面要点

- 独占 `check_affix_pool_refs.py`（≤200 行、零接线），六夹具 + 三条机器验收。
- 接口 `read_defs` / `read_pools` / `unmatched` / `main` 由 S7 定死，**禁** `path_of` / `split(":")` / `rsplit` / `ident(`。
- 在制品 1/3（T-0095 OPEN）。

## 4. 环境 / 收尾

- `src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服 9376 常驻未动、测试服 25567/25577 无 LISTEN。
- 裸名纪律第十七次：派单当轮文档与台账在建脚本一律裸名 `check_affix_pool_refs.py`，不写 `tools/` 全路径。
- Q-021（`respec` 免费）/ Q-022（原语按需补，13/25）无答复，按默认走；真人窗口 A~G 等 Aki。
