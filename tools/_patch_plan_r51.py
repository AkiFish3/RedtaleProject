p = 'PLAN.md'
s = open(p, encoding='utf-8').read()

row_r51 = (
u'| R51 | 2026-11-18 | **验收 T-0046（`TreePoints`）+ 存档 v2（技能树分树持久化）+ 派 T-0047** | '
u'①卡板：T-0046 `[DONE]` → **`[VERIFIED]`**（3/3：`build --offline` RC=0 / jar 内 `TreePoints`=**3**、`wc -l`=**58** ≤150、`net.minecraft`=0 / '
u'S8 探针 **42/42**；另跑 S7 独立探针 `build/probe/S7TreePointsProbe.java` **41/41**，自编 A~L 十二组）。'
u'**派 T-0047**（`/redtale tree list|info` 只读观测面，独占 `TreeCommand.java` + `RedtaleCommand` 1 行，文案逐字写死），在制品 **1/3**。'
u'②自研 `ProgressStore` v1→**v2**：`trees: {treeId: [nodeId]}` + `trees(id)` / `nodes(id,treeId)` / `hasNode` / `addNode` / `removeNode` / `setNodes` / `clearTree`；'
u'**按树分桶**（PLAN §13.53 第 3 条原案），不是扁平 list。'
u'③实测（测试服 2 次启停 `Done` 均干净，已停、25567/25577 无 LISTEN）：'
u'**A 组 v1 真档**（R50 留在 `world/data` 的 126 B、`version=1`、`main=3000`）直接读起、零告警，`exp probe 7/7`；'
u'`exp primitive` 弄脏 → `save-all` → 文件变 `version=2` 且出现 `trees` 复合键、`main 3000` 逐字保留；'
u'**B 组带旧扁平 `nodes` 键的档**（本轮第一版写的 139 B）也能读起（旧键忽略）→ 再存时旧键消失、只剩 `trees`。'
u'④数字：`build --offline` exit 0（jar **604428 B**）、`selftest` 78 PASS、S7 独立探针 `S7ProgressStoreProbe` **53/53**（A~J 十组）。 | '
u'**§13.54 R51-a~d**。方向修正一次：第一版按「扁平 `nodes: [nodeId]`」写，做到一半发现 **节点 id 只在树内唯一**'
u'（`root` 这种名字每棵树都有）→ 按 §13.53 原案改回 `trees: {treeId: [...]}`；这正是「派单前先读 PLAN 的下一轮清单」的价值，'
u'省的是一次静默的数据模型返工。另一条：`ProgressStore.load` 里已有 `ListTag list`（players），新增分支再用 `list` 直接编译失败 —— 局部变量重名是加密代码时的常见坑。 |\n'
)

anchor_row = u'\n### 13.53 R50 的五条口径（技能树快照层）+ 下一步（R51）'
assert anchor_row in s
s = s.replace(anchor_row, u'\n' + row_r51 + anchor_row.lstrip('\n'), 1)

sec_1354 = u'''
### 13.54 R51 的四条口径（存档 v2）+ 下一步（R52）

| # | 口径 | 理由 |
|---|---|---|
| R51-a | 技能树已点节点**按树分桶**存：`trees: { treeId: [nodeId...] }`，**不存扁平 list** | 节点 id（`root` / `atk_spd_1`）只在**树内唯一**，两棵树都会有 `root`。扁平存法在第二棵树上线当天就会静默串味：点 A 树的 `root` 变成「两棵树都点了 root」。PLAN §13.53 第 3 条本来就写的 `trees: {treeId: [...]}`，是 S7 第一版图省事写扁了，做到一半改回来 |
| R51-b | 存档层**零语义**：不校验节点存在性 / 前置 / 点数 / 树是否存在，只如实存取字符串；`removeNode` 不删空树桶（保住树的插入序），清空走 `clearTree` | 与 `ProgressStore` 既有定位一致（「纯数据层：不解析配置、不算曲线、不下发属性」）。语义判定在 `TreePoints`（L3 纯函数）与将来的 `TreeService`，两处都有探针；存档层再判一遍就是第二份真相，且会与 L3 的口径漂 |
| R51-c | 反序列化**容忍未知键**：缺 `trees` → 空表；旧扁平 `nodes` 键读时忽略、写时自然丢弃；`version` 只写不校验 | v1 真档（R50 线上那份）必须原样读起 —— 实测能读，`exp probe 7/7` 零回归。不做 `nodes`→`trees` 的迁移代码：扁平键只存在于本轮两小时的自测档里，写迁移分支等于给一个从没上线过的格式留后门 |
| R51-d | 观测面不接判定层：`tree list|info` **不调 `TreePoints`**，只报静态定义 | 点数是派生值（`SKILL-TREE.md` §5.2），观测面若自己算一遍 `used/available` 就有了第二套口径；等 `TreeService` 落地后，`used/available` 从同一个入口出。卡里把 `grep -c "TreePoints" = 0` 写成硬验收就是为了钉死这条 |

下一步（R52）：

1. **验收 T-0047**（`TreeCommand`：build + jar 类 + `redtale tree list|info` 三组输出，需测试服）。
2. **M7 L3 落地（归 S7）**：`TreeService`（`take` / `untake`：`TreePoints.canTake` → `ProgressStore.addNode` → 按 `effects` 下发属性 + 撤销；洗点 `clearTree`）+ `/redtale tree take|untake`。**这是技能树第一次真正影响面板**，验收必须读 `AttributeMap` 差值而不是看返回值。
3. **`spawn.json5` 开关持久化**（R48/R49 遗留）：`replacer_enabled` / `per_tick_cap` 落盘 + 记账 §5 路径。
4. 挂账不变，另加**技能树真人在线路径**：`treesForJob` 与 `take` 的玩家上下文至今无调用方。
'''

anchor_next = u'\n下一步（R51）：'
i = s.index(anchor_next)
# 找到 R51 列表结尾（下一个空行 + 表格行或文件尾）
tail_marker = u'技能树的持久化'
j = s.index(tail_marker, i)
# 从该项起找到该列表的最后一行（以数字编号或 "- " 开头，直到空行）
k = s.index(u'\n\n', j)
s = s[:k] + u'\n' + sec_1354 + s[k:]

open(p, 'w', encoding='utf-8', newline='').write(s)
print('ok')
