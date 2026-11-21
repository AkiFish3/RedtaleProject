# R95 · 2026-11-21 22:5x~23:1x · 验 T-0090 + 接线 38→39 + 派 T-0091（词条等级可达性）

## 1. 验收 T-0090 `check_enchant_args_sync.py` → VERIFIED

- 验收 1：`python tools/check_enchant_args_sync.py` → `共 1 份附魔脚本参数对账表 · 0 条问题` EXIT=0；`-v` → `已检查 1 条 scripted 调用 · 2 个数值参数 · 1 份脚本`。
- 验收 2：`build/enchargsfix/` 六组独立干净夹具 A=1（`脚本参数 ratio 与附魔声明不一致（附魔 0.12 / 脚本 0.08）`）· B=0（别名命中）· C=1（`脚本缺少参数 chance（附魔声明 chance=0.5）`）· D=1（`引用的脚本 enchants/missing 不存在`）· E=0（非 scripted 不进范围）· F=0（嵌套两层，`-v` 1/1/1）逐字 EXIT=1/0/1/1/0/1；夹具已清。
- 验收 3：wc=174≤200、`def diffs`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 恰好 1 条 `目录读取失败` EXIT=1 不抛栈。
- S7 独立边界 18/18：冻结别名 / bool 与 NaN·Inf 排除 / 同名多值 / 递归保序 / 首个不一致 / 文案升序 / 脚本自有常量不报 / 重复 id 先写赢 / 坏文件与缺目录不抛栈。S8 零偏差。
- 裁定 S8 两处口径备注：`-v` 的 `<k>` = 被引用且存在的脚本份数（基线 scripts/ 2 份、被引 1 份，卡面期望值优先，R79-a）；解析失败文案卡面未定义、补齐合理形态采纳不打回（R79-a 家族第七次）。

## 2. 接线

`tools/check_all.py` TOOLS 增加裸名 `check_enchant_args_sync.py`（R95 接）：`共 39 个脚本 · 跑成 39 · 跳过 0 · 红 0`。全套 0 问题：`check_checkall` 1 清单、`check_docs` 110 份/465 引用、`check_tasks` 90 卡在制品 1、`check_contracts` 55 条、`check_rounds` / `check_ownership`。

## 3. 派 T-0091 —— 词条等级 / 类型可达性对账

- 真洞（对账网第二十二例，首次核「池引用词条在固定物品等级下可达」）：物品工厂源码里 `ITEM_LEVEL = 1`（等级系统 M3 接入前固定），`AffixRoller.pick()` 中等级越界与 `def.kind() != kind` 都只 `continue`，候选空则 `break` 留空槽，**无 WARN / ERROR、无红**。`check_items.check_affix` 只核 item_level min/max 是数字且 `min<=max`，`check_pool` 只核池段引用 id 存在与权重>0——两份检查全绿，玩法静默退化。
- 派前全类排除留证（R88-c/R89-c/R90-c/R91-b 延续）：Explore 全量扫 39 个 check + 37 份 json5 + 142 个 Java 文件后，三候选中排除 ① 怪物技能 `chance`（`MobSpec` 有 clamp，按 R86-b 排除）② 互斥组与槽位联合可填满性（是否故意留空槽属取舍，S7 独占留池，R92-b）；落词条可达性。常量 `1` 由 S7 从源码冻结写死，S8 不读源码、不推断。基线 3 词条 / 1 池 / 2 有效槽位段 / 0 问题（S7 离线跑判定逻辑逐字核过，R82-c 正面版）。
- 只核「有槽位段至少有一条同 kind、等级覆盖 1 的被引用词条」，不核 id/weight/stats/applies_to（归 `check_items`），不核互斥组联合占用（留池）。

## 4. 环境

`src/` 零 diff、纯 Python 轮次；未构建、未启停任何服、无构建锁。正式服 25565/25575（PID 9376）常驻未动、测试服 25567/25577 无 LISTEN。源码推送仍被 443 reset 阻断（本地 `7e5076f` ahead 1 + 本轮未提交），镜像同步待重试。
