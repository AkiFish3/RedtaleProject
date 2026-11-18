# R51 · 2026-11-18 18:59-19:2x · 验收 T-0046 + 存档 v2（技能树分树持久化）+ 派 T-0047

## 0. 一句话

`TreePoints` 验收通过（3/3 + 独立探针 41/41）；`ProgressStore` 升 v2 存技能树已点节点，**按树分桶**；派出 `/redtale tree list|info` 观测面卡。

## 1. 卡板

| 卡 | 动作 | 证据 |
|---|---|---|
| T-0046 `TreePoints` | `[DONE]` → **`[VERIFIED]`** | ①`gradle build --offline` RC=0；②jar 内 `TreePoints`=3、`wc -l`=58 ≤150、`net.minecraft`=0；③S8 探针 `PASS 42 / FAIL 0`（≥25 达标） |
| T-0047 `/redtale tree list\|info` | **派单**（`[OPEN]`） | 独占 `TreeCommand.java`（新建 ≤200 行）+ `RedtaleCommand.java` 1 行；输出文案逐字写死；硬验收含 `grep -c "TreePoints" = 0` |

**S7 独立探针**：`build/probe/S7TreePointsProbe.java` → `PASS 41 / FAIL 0 / TOTAL 41`（A~L 十二组，自编断言，不复用 S8 的 42 条）：

- A `state` 五元组；B `unknown` 不扣点；C 重复 id 只算一次；D `available` 负不夹取
- E `missingPrereqs` 保序去重 / `selected=null` 全缺 / 节点不存在 → 空
- F 五种 `Status` 各一条 + 优先级两条（已选 > 缺前置 > 点数）
- G `cost 0` 两分支（`available>=0` → OK / `<0` → NO_POINTS）
- H `statusAll` 键序 = `def.nodes()` 序 + 不可变
- I 全 `null` 入参不 NPE；J `cost==available` 仍 OK、`cost>available` 才 NO_POINTS；K 幂等；L 空节点表

在制品：**1/3**（T-0047）。

## 2. 自研 · `ProgressStore` v2

### 2.1 结构

```
{ version: 2,
  players: [ { id: "<uuid>", main: <long>, jobs: { "<jobId>": <long> },
               trees: { "<treeId>": ["<nodeId>", ...] } } ] }
```

API（新增 7 个，见 `docs/CONTRACTS.md` §4.24）：`trees(id)` / `nodes(id, treeId)` / `hasNode` / `addNode` / `removeNode` / `setNodes` / `clearTree`。

### 2.2 为什么不是扁平 list（本轮返工点）

第一版写的是 `nodes: ["root", "atk_spd_1", ...]`。做到一半发现 **节点 id 只在树内唯一** —— 每棵树都有 `root`、都有 `atk_spd_1` 这种命名。
扁平存法在第二棵树上线当天就会静默串味：点 A 树的 `root` 变成「两棵树都点了 root」，而 `TreePoints.state` 按 `def.nodes()` 查，两棵树会各自认为自己被点过。

PLAN §13.53「下一步（R51）」第 3 条本来就写的是 `trees: {treeId: [nodeId]}` —— 是 S7 图省事写扁了。**改回分桶**，代价是一次构建失败 + 一遍探针重写，比将来迁移数据便宜。

### 2.3 三条实现事实

1. `ProgressStore.load` 里已有 `ListTag list`（players 的 tag），新增分支再声明 `ListTag list` → **编译期重名错误**。改成 `nodeList`。
2. 空树桶**不删**：`removeNode` 只从集合里移除，`clearTree` 才摘桶 —— 保住 `LinkedHashMap` 的树插入序，`trees(id).keySet()` 的序才稳定。
3. 反序列化**容忍未知键**：缺 `trees` → 空表；旧扁平 `nodes` 键读时忽略、写时自然丢弃；`version` 只写不校验。

## 3. 实测（测试服 2 次启停，均已停）

| 组 | 起始件 | 动作 | 结果 |
|---|---|---|---|
| A | R50 留在 `world/data` 的 **126 B 真档**（`version=1`、`main=3000`、无 `trees`） | 起服 → `exp probe` → `exp primitive 500` → `save-all` | 读起零告警、`exp probe 7/7`；文件变 **`version=2`** + 出现 `trees` 复合键、`main 3000` 逐字保留（`0x0bb8`） |
| B | 本轮第一版写的 **139 B 档**（`version=2` + 旧扁平 `nodes`） | 换 jar 起服 → `save-all` | 能读起（旧键忽略），再存时旧键消失、只剩 `trees` |

- 两次启动均 `Done`，`/redtale reload` 正常，`redtale` 零 ERROR（日志 4 条 `ERROR` 是 R8 起就有的 `RecipeManager` 噪声）。
- 收尾：`stop` 后 25567 / 25577 **无 LISTEN**。

## 4. 数字

- `gradle build --offline` exit 0，jar **604428 B**
- `gradle selftest --offline` **78 PASS**（未变）
- `S7ProgressStoreProbe` **53/53**（A~J 十组：v1 读起 / add 幂等 / **跨树同 id 隔离** / null 守卫 / 存档层零语义 / save-load 往返 / remove+clearTree / setNodes / 多人隔离+视图不可变+clear / 坏行坏类型）
- `S7TreePointsProbe` **41/41**

## 5. 未验证

- **真人在线点选 → 存档**：`take` / `untake` 命令还不存在（T-0047 是只读面）。写路径本轮只由探针覆盖。
- 洗点扣费口径未定（见 Q-021）。

## 6. 下一步（R52）

1. 验 T-0047（`tree list|info` 三组输出）。
2. **M7 L3 落地**：`TreeService`（`take`/`untake`/洗点）+ `/redtale tree take|untake` —— 技能树第一次真正影响面板，验收读 `AttributeMap` 差值。
3. `spawn.json5` 开关持久化（R48/R49 遗留）。
