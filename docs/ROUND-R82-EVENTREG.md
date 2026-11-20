# R82 · 验 T-0077 + check_all 25→26 + 派 T-0078（事件监听器注册对账）

## 1. 验收 T-0077（`check_ownership.py`）

- `[DONE]` → **`[VERIFIED]`**。验收 1 的基线取自领卡时状态：`git show HEAD:docs/TASKS.md` → <该基线快照路径>，跑出 `已对账 1 张在制品卡 · 2 个独占文件声明` + `共 1 份卡板 · 0 条问题` EXIT=0。当前工作树里 T-0077 已 DONE、自指消失，复跑为 0 卡 / 0 声明 EXIT=0 —— 按 S8 交付注记处理，不打回。
- 三夹具各自从干净最小卡板重来（`build/ownerfix/`，用完已清）：A 两张 WIP 同写 `foo.py` → 1 条 `独占文件 <该夹具路径> 被 T-9001 / T-9002 同时声明`；B OPEN + VERIFIED 同文件 → 0 条；C 三张 WIP 两两重叠 → 2 条，顺序 `build/fix` 先、`foo.py` 后（夹具原文见卡面）。EXIT=1/0/1，逐字对上。
- 静态：wc=105 ≤ 200、`grep -c "def check_overlaps"`=1、禁用 grep=0、`py_compile` OK、`--tasks NO_SUCH` → 恰好 1 条 `卡板读取失败` EXIT=1 不抛栈。
- S7 独立边界 11/11：模板 `T-XXXX` 不算卡、body 止于下一个标题、1-based 标题行号、缺文件返回空、`./` 前缀与尾部 `/` 归一、同卡重复 token 去重、兄弟 `- ` 行截断、三卡同路径卡 id 升序、同卡重复不报、多路径按 path 升序、空声明集。首轮 3 个 FAIL 全是 S7 夹具期望写错（行号按每卡一行算、membership 当子串、无 `- ` 的自由文本本就不在接口扫描集），按 R65-a 修正期望后 11/11，非代码缺陷。

## 2. 接线

`tools/check_all.py` 的 `TOOLS` 末尾加 `check_ownership.py` → **`共 26 个脚本 · 跑成 26 · 跳过 0 · 红 0`**（EXIT=0）。接线后 `check_checkall -v` 26/26、`check_tasks` 78 卡在制品 1 问题 0、`check_ownership -v` 新基线 1 卡 / 2 声明（T-0078）。

## 3. 派 T-0078（`check_event_registration.py`）

- 洞：`check_commands.py` 核命令类 ↔ 挂载，但新增带 `@SubscribeEvent` 的监听器忘 `EVENT_BUS.register(...)` 时，编译绿、命令对账绿、运行期事件**永不触发且不报错**。两份已有检查之间这条缝此前无人核。
- 基线 S7 先核过：8 个含 `@SubscribeEvent` 的类（`CritLayer` / `DamageLayer` / `GameHooks` / `ItemTooltipHook` / `ItemUpgradeHooks` / `MobDrops` / `ProfessionHooks` / `SpawnReplacer`），8 个都有注册证据。直接 `register(damageLayer)` 是小写字段别名、`SpawnReplacer` 是自注册，所以接口必须允许「类型声明别名」和「自身文件 register 调用」两种证据。
- 接口由 S7 定死：`strip_comments` / `read_listeners` / `read_registrations` / `check_registration` / `main`；问题文案、三组夹具逐字写死；零接线，接线留 R83。
- 在制品 1/3。

## 4. 环境

本轮 `src/` 零 diff：未构建、未启停任何服、无构建锁；25567 / 25577 / 9376 无 LISTEN。所有验收为纯 Python 静态检查。`check_all` 26/26、`check_docs` 97 份 396 引用 0 问题、`check_contracts` 44 条 0 问题。
