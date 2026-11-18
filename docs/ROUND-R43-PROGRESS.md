# R43 · 2026-11-18 09:18-09:35 · 验收 T-0038 + M7 第三层存档落地

当前里程碑：**M7（等级/经验/职业）** —— L1 曲线已 VERIFIED、L3 存档已落地并有重启读回证据；L2 解析器已派 T-0039。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0038 `ExpCurve` | 验收 | **VERIFIED（3/3）**，S7 独立探针 284/284 |
| T-0039 `ProgressSpec` | 派单 | OPEN，在制品 **1/3** |

T-0038 验收明细：

- `gradle build --offline` EXIT=0（jar 504603 B）；`unzip -l | grep -c "core/progression/ExpCurve"` = 1。
- `wc -l` = 90 ≤ 180；`grep -c "net.minecraft"` = 0；`grep -cE "public static ExpCurve (geometric|polynomial)"` = 2。
- `build/probe/S7ExpCurveProbe.java` **PASS 284 / FAIL 0 / TOTAL 284**（自编，不复用 S8 的探针）。
- **裁定**：卡面定点表 `totalExpForLevel(4)=352` 是笔误，`337` 正确（语义 4 = `Σ_{i=1..L-1}`，同卡面 `total(3)=212` 自证；352 = `212+exp(4)=212+140` 含 L 项）。实现按语义，不打回。

## 2. 自研：M7 第三层 `ProgressStore`

新增 `core/progression/ProgressStore.java`（`extends SavedData`）：

- 常量：`FILE_NAME = redtale_project_progress`、`VERSION = 1`、`MAX_TOTAL_EXP = 1e15`。
- API：`mainExp` / `jobExp` / `jobExps` / `size` / `ids` / `addMainExp` / `addJobExp` / `setMainExp` / `clear` / `load(tag,provider)` / `factory()` / `get(server)`。
- NBT v1：`{ version:1, players:[{ id:"<uuid>", main:<long>, jobs:{"<jobId>":<long>} }] }`；UUID 存字符串；坏行整条跳过；负值归零；超顶截断。
- **只存 `total_exp`，不存等级**（R42-b 的落地面）。

新增 `core/command/ProgressCommand.java`：`/redtale progress [status|add <amount>|probe]`；`probe` 用固定 UUID `00000000-0000-0000-0000-0000000000ff`（控制台可跑），`add` 需真人。
`RedtaleCommand` +1 行挂节点；`GameHooks#onServerStarted` 走一次 `computeIfAbsent` 并打日志。

## 3. 实测证据（测试服 `E:\server-test`，起停 2 次，均已停）

| 步骤 | 输出 |
|---|---|
| 首启（`Done (2.226s)`） | `进度存档就绪：0 名玩家（redtale_project_progress.dat）` |
| `progress status` | `[redtale] 进度存档 redtale_project_progress.dat · 玩家 0 名 · 格式 v1` |
| `progress probe` ×2 | `main_exp 0 → 1000 · 累加正确 ✓` / `1000 → 2000 · 累加正确 ✓` |
| `save-all` | `world/data/redtale_project_progress.dat` **104 B 落盘**（此前该文件不存在） |
| 重启（`Done (2.037s)`） | `进度存档就绪：1 名玩家` |
| 重启后 `progress probe` | `main_exp 2000 → 3000`（= 从磁盘读回，非内存残留） |
| 回归 | `reload` 29 份配置 / `validate` 问题 0 / `drop list` 文案不变 / redtale 零 ERROR、零 WARN |

收尾：`stop` 干净退出，25567/25577 无 LISTEN；构建锁已删。

## 4. 数字

- `build --offline` exit 0，jar **512605 B**；`selftest` **78 PASS**；`offline_check` `sources: 114 exit: 0`。
- `build/probe/S7StoreProbe.java` **48/48**（NBT 往返、幂等、坏行、硬顶、`clear`、工厂）。

## 5. 本轮踩到的两条坑（已写 PLAN §13.46）

1. `Map.copyOf` / `Set.copyOf` **不保迭代序**（R18 记过）→ 本层探针 L1 当场红；改为 `Collections.unmodifiableMap(new LinkedHashMap<>(...))`。
2. srg 编译面上 `SavedData.Factory` **只有 3 参构造**（2 参是 universal 补丁版，javap 看得到、javac 看不到）→ 传 `DataFixTypes.LEVEL`。

## 6. 挂账

真人玩家 `progress add`（无客户端）、等级属性面板（L4 才存在）、其余历史挂账不变。
