# R101 · 验 T-0096 + check_all 44→45 + 派 T-0097（伤害类别 type 完整形态对账）

- 时间：2026-11-22 06:1x（S7 同频轮）
- 工作目录：`E:\RedtaleProject`

## 1. 验收 T-0096（`check_repair_active.py`）→ VERIFIED

S8 于 05:21 报完成（99 行）。S7 独立复跑三条，全过：

1. 基线：`python tools/check_repair_active.py` → `共 1 份修理有效性表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 7 份物品 · 1 条 repair 段`。
2. 六夹具 A~F（每次从干净目录重来）：`amount:120` / 缺 amount / `amount:0` / `percent:0.25` / 切 percent 忘改字段 / `percent:0`。逐字 EXIT=0/1/1/0/1/1，B/C 为 amount 文案、E/F 为 percent 文案，各 1 条。
3. 静态：wc=99≤200；`def check_repair`=1；禁用 grep=0；py_compile OK；`--dir NO_SUCH` → `[问题] items 目录读取失败` + 末行 `共 1 份修理有效性表 · 1 条问题` EXIT=1 不抛栈。

S7 附加边界 8/8：bool amount / float amount / percent>1 / 非法 mode / repair 非 dict / 顶层无 id / 两条消息按文案升序 / NO_SUCH。首轮 sort 夹具把两条 JSON 对象塞同一文件被解析跳过，按 R65-a 改成两文件后全过——夹具期望错误，非代码缺陷，不打回。夹具已清，S8 零偏差。

## 2. 接线 check_all 44→45

`tools/check_all.py` TOOLS 元组末尾追加 `check_repair_active.py`。接线后全套 45 个脚本跑成 45、红 0。七门禁 0 问题：`check_checkall` 1 清单/0、`check_tasks` 97 卡在制品 1、`check_contracts` 62 条、`check_docs` 117 份/495 引用、`check_rounds`/`check_ownership` 0。

## 3. 派 T-0097（对账网第二十八例 = 类别映射 pattern 形态）

### 3.1 派前全类排除留证

只读调研扫 45 个 check / 36 份 json5 / Java，逐条排除：

| 候选 | 排除/选中理由 |
|---|---|
| drops / set_id / spawn_rules.match / repair.targets | 复核结论不变：有兜底 / 出口明确 / 原版 registry 不同源 / 空基线，继续留池 |
| `consume` 字符串模式 | 写歪 clamp 到 always + reload WARN，静默形态弱于零 WARN，留池（R101-c） |
| `repair.mode` 枚举 | 写歪 clamp + WARN；与刚验的 T-0096 同段，避免连派同段两张尺，留池 |
| professions.grants / 附魔 slots·supported_items tag | 合法值来自原版注册表，Java 侧无可冻结集合，S8 离线无真值（R101-d） |
| mobs level / undead / attack_speed.effect id | 有 clamp+WARN，或只有一份 check 读且开放词汇 |
| **category_attributes[].type** | **选中**：入参补 ns、pattern 不补；不命中零 WARN 静默吃物理属性池；两把旧尺全绿 |

### 3.2 源码逐字核实

- `MultiplierRules.java:43,45`：冻结 `SKILL_PHYSICAL` / `SKILL_MAGICAL` 两个完整 id。
- `:143-154` `attributesFor()`：类别 first-match，零命中回落 `percentAttributes`，无 WARN。
- `:157-168` `normalise()`：入参 trim+lowercase，裸路径补 `minecraft:`。
- `:171-184` `matches()`：pattern 只 trim+lowercase，`*` / `ns:*` / 精确 id 三种；精确分支 `id.equals(e)`。
- `:321-357` `parse()`：`type` 只做非空字符串结构校验，不核形态、不核能否命中。

### 3.3 离线核基线 + 反事实

```
damage.json5 category_attributes: 2 条，均为 redtale_project: 完整 id
合法集: {redtale_project:skill_magical, redtale_project:skill_physical}
判定: configs=1 refs=2 bad=0
check_numeric.py: 共 4 个文件 · 0 条问题  EXIT=0
check_damage_types: 4 引用 / 4 注册 / 0 问题
反事实：裸 skill_magical -> _bare=skill_magical（已注册，0 问题）
        minecraft:skill_magical -> _bare=None（直接跳过，0 问题）
```

### 3.4 卡面要点

- 独占 `check_category_types.py`（≤200 行、零接线），六夹具 + 三条机器验收。
- 接口 `CATEGORY_TYPES` / `read_config` / `read_types` / `unmatched` / `main` 由 S7 定死；禁任何归一/前缀匹配，通配也报。
- 基线 1 配置 / 2 type / 0 问题；在制品 1/3（T-0097 OPEN）。

## 4. 环境 / 收尾

- `src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服 PID 9376（25565/25575）常驻未动、测试服 25567/25577 无 LISTEN。
- 裸名纪律第十九次：本轮在建脚本与夹具目录一律不写具体路径。
- Q-021（`respec` 免费）/ Q-022（原语按需补，13/25）无答复，按默认走；真人窗口 A~G 等 Aki。
