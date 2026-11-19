# R76 · 2026-11-20 05:1x · 验收 T-0071 + check_all 19→20 + 派 T-0072（原语注册对账）+ 修 projectile 表漂移

## 1. 验收 T-0071（`tools/check_commands.py`，命令树挂载对账）→ VERIFIED

卡面三条全过，逐条有证据：

1. **基线**：`python tools/check_commands.py` → `共 1 份命令树 · 0 条问题` EXIT=0；`-v` → `已检查 …/RedtaleCommand.java · 命令类 20 个 · 挂载引用 20 个`（当前基线 20 个 `*Command.java` 不含根命令、根命令引用 20 个外部命令类）。
2. **S7 独立三夹具**（自建，写到 `build/cmdfix/`：命令目录与根命令各复制一份改，用 `--cmd`/`--root` 指过去）：
   - A 删 `.then(DisplayCommand.node())` 与 `.then(ExpCommand.node())` → EXIT=1、恰好 2 条 `未挂进 RedtaleCommand`（两个类名逐字在）；
   - B 塞 `.then(GhostCommand.node())` → EXIT=1、恰好 1 条 `无对应命令类`；
   - C 目录副本新增 `WandererCommand.java` 不挂 → EXIT=1、恰好 1 条 `未挂进 RedtaleCommand`。
   夹具已清。
3. **静态断言**：`wc -l`=134≤200、`grep -c "def check_mounts"`=1、禁用模式 grep=0。

S7 另跑 5 组边界（5/5）：行尾 `//` 与 `/*…*/` 块内假命令不计入（EXIT=0）；字符串字面量 `XxxCommand` token 保留（接口「只剥注释」逐字，EXIT=1 报幽灵）；命令目录读失败 1 条 `命令目录读取失败` 不抛栈；根命令读失败 1 条 `根命令读取失败` 不抛栈；三类问题排序稳定（3 条未挂在前 + 1 条幽灵在后）。

**首跑 E5 FAIL 是 S7 夹具期望写错**（`ta` 已删 2 条挂载 → 应 3 未挂 + 1 幽灵 = 4 条问题，我写成 3 条），复测排序正确，非代码缺陷（同 R65-a「夹具也要核期望」）。

**裁定 S8 备注**（§13.79 R76-a）：字符串字面量里的 `XxxCommand` 计入挂载引用——接口逐字只要求「先剥注释」，剥字符串属超范围，采纳实现不打回。

## 2. 接线 `check_all` 19→20

`tools/check_all.py` TOOLS 末尾追加 `check_commands.py`（R76 接上）。`check_all` → `共 20 个脚本 · 跑成 20 · 跳过 0 · 红 0` EXIT=0；接线后 `check_checkall -v` → `清单 20 项 · 磁盘 20 项` 仍 0 问题（R75-b 自排除按接线时刻失效，实测再次正确）。

## 3. 派 T-0072（`check_primitives.py`，原语注册对账，L6 新建）

R75-c「补完一个元层洞后找同源不同对象的下一个」：T-0070 对账「哪些 check 会被跑到」、T-0071 对账「哪些命令会被挂上」，本卡对账「哪些原语类/内联 record 会被注册进 Builtins」。

**为什么是真缺口**：`check_skills.py` 的 `prim = SKILLS.md §4 表全部 id | DEBUG_PRIMITIVES`，只验配置 `type:` 在表内；`builtin/` 下新增 `*Effect.java`/`*Condition.java` 或 `Builtins.java` 加内联 `record … implements TriggerType` 却忘写 `register(new Xxx())` → 起飞前全绿、运行期才崩。卡面写死接口 `read_classes`/`read_records`/`read_regs`/`check_registration` + 三条负向夹具 + 静态断言，单文件 ≤200 行、零接线。在制品 1/3（T-0072）。

基线（S7 已核）：16 独立类 = 13 Effect + 3 Condition；2 内联 record（`ManualTrigger`/`PlayerJoinTrigger`，在 `Builtins.java` 内）；18 注册引用 = 16+2。

## 4. S7 自己最小一步：修 `SKILLS.md` 第 12 条 `projectile` 表漂移

**发现路径**：为 T-0072 核基线时顺带读 `Builtins.java` 注册，发现 `ProjectileEffect` 已 `register` 且 `id() = "projectile"`，但 `docs/SKILLS.md` §4 第 12 条状态列仍写 `—`（未做）；查 `PLAN.md` R39 台账：T-0033（`projectile`）2026-11-17 即 `[VERIFIED]`、测试服实测速度八例通过。**文档状态列漏更新**，非代码缺陷。已改为 `✅ R39` 并注明漂移来源。

**未做成检查的洞（记口径，不改 `check_skills`）**：`check_skills` 的 `prim` 含表里所有 25 个 id，其中 14 个标「—（不做）」的也会过检——夹具 `type: "teleport"`（表第 10 条标—）→ `0 条问题` EXIT=0；对照 `type: "totally_bogus"` → `1 条问题 effects[0].type 非法` EXIT=1。要不要把「表里标—却出现在配置里」由 note 升为 problem 属取舍（会改变 check_all 绿红语义），本轮不改，记 §13.79 R76-b 待拍板/下轮拆卡。

修表后回归：`check_skills` 4 份默认技能 `共 4 个文件 · 0 条问题` EXIT=0、`check_all` 20/20 全绿。

## 5. 环境

本轮 `src/` **零 diff**（验收/接线/派卡/文档修正均不碰 Java）：未构建、未启停任何服、无构建锁；25567/25577/9376 无 LISTEN。`check_all` 20/20、`check_tasks` 72 卡在制品 1 问题 0、`check_docs`（收尾后跑）、`check_contracts` 44 条 0 问题。

## 6. 口径沉淀

见 `PLAN.md` §13.79 R76-a/b/c。
