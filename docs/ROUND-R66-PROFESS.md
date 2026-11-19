# R66 · D24 L3+L4：专业真的发经验了（三个事件 + 存档 v3 + 属性下发）

> 2026-11-19 12:24–13:0x · S7 · 测试服 3 次启停（`Done 1.999 / 1.962 / 2.050s`），均已停、25567 无 LISTEN

## 1. 卡板

- **验收 T-0061**（`tools/check_tasks.py` 卡板自检）`[DONE]` → **VERIFIED**（3/3 + 四组夹具逐条复跑 + 2 组 S7 边界；口径见 §4 R66-a）
- **派 T-0062**（`check_professions.py`：`professions/*.json5` 起飞前对账，11 检查项、3 份负向夹具、末行 `共 N 个文件 · P 条问题`），在制品 **1/3**
- 接线：`tools/check_all.py` 末尾追加 `check_tasks.py` → **`共 15 个脚本 · 跑成 15 · 跳过 0 · 红 0`**（R65 下一步第 1 条的「是否进 check_all 由 S7 定」当场定为**进**）

## 2. 自研：D24 L3 + L4（PROFESSION §8 的两层一起做）

| 文件 | 改动 |
|---|---|
| `ProgressStore.java` | `VERSION 2 → 3`；`professions` 桶（`profExp` / `profExps` / `addProfExp` / `setProfExp`）；`load` / `save` 各一段；存档层仍**零语义** |
| `ProfessionService.java`（新，~330 行） | `amountFor` / `plan` 纯函数 + `award`（写桶）+ `levelOf` / `idOf` / `prefixOf` / `declaredIds` / `planGrants` / `applyAttributes` / `removeAll` / `snapshot` / `onLogin` / `applyAll` |
| `ProfessionHooks.java`（新，~120 行） | 三个事件点：`BlockEvent.BreakEvent`（mine）/ `ItemCraftedEvent`（craft）/ `ItemSmeltedEvent`（smelt）；只做「MC 对象 → (id, tag 集)」翻译 |
| `GameHooks.java` | 构造期注册 `ProfessionHooks`（+5 行） |
| `ProgressService.java` | `onLogin` / `applyAll` 各 +1 行 `ProfessionService.applyAttributes`（同一次重算，§5） |
| `ExpCommand.java` | `/redtale exp profession`（控制台列定义 / 玩家列进度）、`<id> <amount>`、`<id> set <value>`、`probe`（6 组） |

三条设计选择：

1. **判定与事件分离**：`award(server, uuid, source, id, tags)` 只吃字符串，事件层翻译 tag。好处是探针可以不经事件直接喂字符串（F 组就是这样跑通的），以后加事件源不动判定。
2. **一次动作只发一条**：直接复用 L1 的 `ProfessionSpec.resolve`（精确 id > 命中 tag 取大 > default > 0），没有第二份匹配器 —— 这是 R57 教训（先确认消费方）的反面用法：**消费方现成，就别造新的**。
3. **清场按前缀扫**：`redtale_project:profession/<专业>/` 而不是按当前声明清单（R53-b 的幽灵 modifier）。

## 3. 实测（测试服 3 次启停，均已停）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `redtale exp profession probe` | **6/6**（A 精确 id=3 / B 只命中 tag=2 / C 两 tag 取大且顺序无关 7·7 / D 未命中→default 1 / E 未命中任何 grant→0 且不写桶 / F 发放+升级+下发+归零清场），连跑两次逐字相同 |
| 2 | `redtale exp profession`（控制台） | `专业 2 个（控制台：只列定义，不读玩家存档）` + mining 上限 100 / smelting 上限 80 |
| 3 | `redtale exp probe` / `redtale tree probe` | **7/7** / **18/18**（零回归） |
| 4 | `redtale validate` / `redtale reload` | 问题 0 / `… 2 个专业（39 份配置文件）` |
| 5 | **P8 存档 v2 → v3** | 手工 v2 夹具（1 名玩家 `main 3000` / `jobs.warrior 1200` / `trees.warrior_vanguard [root, blunt_1]`）→ 启动 `进度存档…玩家 1 名 · 格式 v3`、零 `配置告警` → 一次写入 + `save-all` → 文件 `version: 3` **且** `professions: {}` 出现，`main/jobs/trees` **逐字保留** |
| 6 | 日志 | `RedtaleProject` 29 行，零 WARN / 零 ERROR（4 条 RecipeManager 噪声来自 simplyswords / brewinandchewin） |

- jar **669817 B**（655179 → +14638）、`selftest` **102 PASS**（无新增，纯类未加自测项）、构建锁取删 2 次、`check_all` **15/15**。
- 测试世界留了 1 条夹具行（uuid `…00aa`、`main 3000`）—— P8 的产物，不影响后续（`ProgressCommand.status` 会显示「玩家 1 名」，别当异常）。

## 4. 口径（PLAN §13.69）

| # | 口径 | 理由 |
|---|---|---|
| R66-a | 「`- 验收` 段」判定是 `- 验收` + (行尾 / `：` / `（`)，**不是**字面 startswith | 字面 startswith 会让 T-0061 的夹具 b（`- 验收啦`）判**通过**，检查直接失效。代价是写成 `- 验收命令：` 的卡会假红（当前 0 张），可接受 |
| R66-b | **手工存档夹具必须是 `{ data: {...}, DataVersion: <当前版本> }` 双层包装**；只写内层会被静默读成空档 | 第一版夹具只写 `{version, players}`，启动后 `玩家 0 名`、**无任何报错**：`SavedData` 的外层是 `data` + `DataVersion`，缺 `DataVersion` 时 DataFixer 从 0 版起跑把 tag 打坏，`load` 拿不到 `players` 就当空档。这条最贵 —— 它长得像「代码没实现」，实际是夹具形态错 |
| R66-c | `save-all` **不写没变脏的 SavedData**：只读到数据不算脏，必须先有一次写入 | 第一次 `save-all` 后文件仍是 `version: 2`、无 `professions` 键，差点判「v3 没落盘」。判定顺序固定为「先写一次（探针就够）→ `save-all` → 再读文件」 |
| R66-d | 同类名的两个 `Definition`（`ProgressSpec` / `ProfessionSpec`）**不共享 `levelOf`**，各自现算 | `ProfessionService.levelOf` 第一版直接转调 `ProgressService.levelOf(def, total)` → 编译期类型不兼容。两者语义相同（`max(1, min(maxLevel, curve.levelFor(total)))`）但类型不同，**同一段数学写两遍比拉一个共用签名更省事**（共用签名要动 VERIFIED 的 ProgressSpec） |
| R66-e | 专业经验**只在真正发出时才重下发属性**（`award` 命中后才 `applyAttributes`） | 每挖一块土都跑一遍「清前缀 + 加 modifier」是无意义开销；空手挖土只走一次 map 查表。等级没变时 `applyAttributes` 也是幂等覆盖，所以不需要判「有没有升级」 |

## 5. 下轮

1. 验收 T-0062（过后 `check_all` 应变 `跑成 16`）。
2. D24 L5：`/redtale profession list|info`（观测面，L3 已落地 → 可派 S8）。
3. 真人窗口 `REALPLAYER-WINDOW.md` 追加 **F 组**（P1~P5：真挖真合真熔），仍等 Aki。
4. Q-021 / Q-022 / Q-023 均未答复，默认各按「免费」「按需补」「服务端 `display.json5`」走。
