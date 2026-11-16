# R20 · M3 第 6 步：套装件数统计 + 属性下发

日期 2026-11-16 · 里程碑 **M3（装备系统）** · 本轮同时验收 T-0014、派发 T-0016。

## 1. 卡板

| 卡 | 动作 | 结论 |
|---|---|---|
| T-0014 `SetSpec` 套装解析器 | 验收 | **VERIFIED**（3/3 + S7 独立探针 `build/probe/S7SetProbe.java` **36/36**） |
| T-0015 `/redtale validate` | — | 仍在制（S8 未领） |
| T-0016 `/redtale set check` | **派单** | 新派（只读单类，四条检查；在制品 T-0015+T-0016 = 2/3） |

T-0014 的三条验收：`build --offline` exit 0；两个方法签名 `grep -c` 各 = 1；jar 内 `items/SetSpec` = **3**（主类 + `$Bonus` + `$Definition`）。
功能验由 S7 自写探针跑（**不复用 S8 的 `SetProbe`**），36 项覆盖：`sets/` 前缀筛、子目录 id 保斜杠、空 id 跳过、非字符串 id/name 回落、
跨文件重复保留先出现、pieces 去重+trim+空项跳过、`count` 缺/非数字/`<1` 跳过且小数取整、同 count 稳定升序、
**`stats` 键集与值与 `AffixPools.parseStats` 逐条相同**、`activeBonuses` 全档生效、三处不可变、`null` 入参不抛、两次解析顺序一致。

## 2. 本轮自研（S7）

### 2.1 新增

| 文件 | 作用 |
|---|---|
| `items/SetBonus.java` | 件数统计 + 属性下发（唯一写属性的地方），`apply/clear/applyAll/activeOf/amount/slotOf/modifierId/declaredCounts` |
| `core/command/SetCommand.java` | `/redtale set` / `set list` / `set info <id>` / `set sync` / `set probe` |
| `src/main/resources/redtale/defaults/sets/emberforged.json5` | 默认套装（2 件、两档），进 `DEFAULT_FILES` |

### 2.2 改动

- `ItemContent` 14 → **15** 字段（`+setId`；`set_id` trim，空白 = 无归属），`describe()` 加「套装 x」。
- `ItemFactory.build`：`ContentData.setId` 由 `""` 改为 `content.setId()`。
- `ItemUpgrader.ensure`：重建时 `set_id` 取**新定义**（定义型字段，R20-a）。
- `RedtaleCore`：新增 `sets()`；reload 时解析 `set_id` 并在解析后给在线玩家 `clear + apply`（配置换代要立刻生效）。
- `GameHooks`：`PlayerTickEvent.Post` 每 20 tick 复核件数。
- `ContentTooltip`：新增一行 `套装 <名> <n>/<总> · 生效 k 档`（数值不重复写，已在属性面板里）。
- 默认配置：三个 demo 件加 `set_id: "emberforged"` 并 `rev`+1（`demo_longsword` 1→2、其余 0→1）。

## 3. 证据（测试服 1 次启动，`Done (2.036s)`，已停）

```
/redtale reload        → 20 份配置 / 10 件内容
/redtale set list      → 套装定义 1 个：emberforged · 炽锻 · 2 件 · 档位 1/2
/redtale set info emberforged
                       → demo_longsword · 基底 sword / demo_chestplate · 基底 chestplate
                       → 1 件档 1 条属性 / 2 件档「炽锻·全」2 条属性
/redtale set probe     → 5/5 全过
   ✓ 0 件：件数 0 · 无残留档位 modifier
   ✓ 1 件：1 件档已下发 · 最大生命 20.0 → 22.0
   ✓ 2 件：2 件档已下发（redtale_project:set/emberforged/2）· 攻击伤害 1.0 → 3.0
   ✓ 脱回 1 件：2 件档已移除 · 1 件档保留
   ✓ clear：全部档位 modifier 已移除
/redtale tooltip demo_longsword → 多出「套装 炽锻 0/2」（无 holder = 未穿）
```

- `build --offline` exit 0（jar **309350 B**），`tools/offline_check.py` exit 0。
- redtale 零 ERROR / 零 WARN；日志里 4 条 `RecipeManager` 是既有噪声（配方 mod 缺依赖）。
- 测试服已停，25567 / 25577 无 LISTEN；构建锁已删。

## 4. 一条新事实

`ItemBase.slot()` 返回 **`EquipmentSlotGroup`**（`MAINHAND` / `HEAD` …），而 `LivingEntity#setItemSlot` 要 `EquipmentSlot`；
1.21.1 **没有公开的 group→slot 反查**。`SetBonus.slotOf(base)` 按组单例 `==` 映射，塞装备的入口统一走它
（`SetCommand.probe` 就是踩了这个编译错误才加的方法）。

## 5. 下一轮

1. 验收 T-0015 / T-0016。
2. M4 起步：`redtale_project:scripted` 组件 codec（先 codec 后脚本层 —— 组件是注册表项，加字段要换 jar）。
3. `/redtale validate` 并入套装引用检查（`SetCommand.danglingSets()` 已备好，等 T-0015 落定再动，避免撞卡）。
4. 挂账第 19 轮（需带客户端的窗口）：真人穿套装 → 客户端属性面板刷新；`rev` 落后 → tooltip 立刻变；右键磨刀石 / 消耗品 / 镶嵌；真人 pvp。
