# R7 · 验收 T-0001 + 打通武器动作链路

> 2026-11-16 00:33-00:52 · 双队列第二轮（S7 派单/验收）
> 前情：R6 发现「`weapon_attributes` 只有数据、没有组件」（`PLAN.md` §13.10 R7-1），本轮闭环。

## 1. 验收 T-0001 → `VERIFIED`

三条验收全过。

| 条 | 命令 | 结果 |
|---|---|---|
| 1 | `gradle build --offline` | 退出码 **0**，`build/libs/redtale_project-0.1.0.jar` 154391 B |
| 2 | `grep -c generateAll core/RedtaleCore.java` | **1** |
| 3 | 起测试服（S7 跑） | 见下 |

第 3 条的实测：

- 测试服 `Done (2.542s)`，无启动异常。
- 验证件（S7 放）：`config/redtale_project/items/t_test_sword.json5`（引用 `redtale_project:test_sword`）
  + `config/redtale_project/weapon_attributes/test_sword.json5`（照 `bettercombat:claymore` 的真实 schema 写）。
- 生成物：`world/datapacks/redtale_project/data/redtale_project/weapon_attributes/test_sword.json`，
  `json.load` 通过，顶层只有 `attributes`，5 个子键（`range_bonus`/`two_handed`/`pose`/`category`/`attacks`）齐。
- `pack.mcmeta` = `{"pack":{"pack_format":48,"description":"redtale generated"}}`。
- `data/` 下**只有** `redtale_project` 一个命名空间 → `demo_longsword` 的 `bettercombat:claymore` **没有被写出/覆盖**。
- `/redtale reload` 二次执行：文件数不变（1 json + 1 mcmeta），`pack.mcmeta` 未被重写，无告警。
- `/reload` 后日志 **零** `Failed to parse`（日志里的 `JsonParseException` 全是 SimplySwords 的配方，
  引用了不存在的 `mythicmetals` / `eldritch_end` 物品，与我方无关）。

## 2. 新发现：运行期新生成的 datapack **不会自动启用**

`datapack list` 在生成后显示：

```
There are 2 data pack(s) enabled: [vanilla (built-in)], [mod_data]
There are 1 data pack(s) available: [file/redtale_project (world)]
```

即文件写对了，但它是 **available** 不是 **enabled**，`/reload` 也不会替你启用。
手动 `datapack enable "file/redtale_project"` 后立刻出现在 enabled 列表，`/reload` 无解析错误。

**边界实测**：停机时新建一个空数据包 `probe_pack`，重启 → 它**自动 enabled**。
结论：

| 场景 | 是否自动启用 |
|---|---|
| 重启服务端（文件已在盘中） | **是**（启动期发现的新包直接进 enabled） |
| 运行期由 `/redtale reload` 写出 | **否**（要一次 `/datapack enable`） |

`D14`「全内容可热重载」在 datapack 这一层因此有个一次性门槛，已在 `PLAN.md` §13.11 列为 R8 第一项。

## 3. R7-1：把「引用」挂到物品上（自研）

### 3.1 为什么必须做（字节码证据）

`bettercombat-neoforge-2.4.0+1.21.1.jar`（`javap -p -c`）：

- `BetterCombatDataComponents` 静态块：
  `ResourceLocation.fromNamespaceAndPath("bettercombat","preset_id")` 注册了一个
  `DataComponentType<ResourceLocation>`，persistent codec 就是 `ResourceLocation.CODEC`。
- `WeaponRegistry#getAttributes(ItemStack)` 的字节码顺序：
  1. `stack.get(WEAPON_PRESET_ID)` → 拿到 id 就去 `containers`（数据包 `weapon_attributes/*.json`）里取；
  2. 取不到才回落 `getAttributes(BuiltInRegistries.ITEM.getKey(item))`。

所以整条链是**两件事**：数据包给**定义**，物品组件给**引用**。
R6 只做了前者，等于物品根本不会指向我们生成的预设。

### 3.2 实现

`items/ItemFactory.java` 新增两个方法 + 一行调用：

```java
public static final ResourceLocation PRESET_ID_COMPONENT =
        ResourceLocation.fromNamespaceAndPath("bettercombat", "preset_id");

public static boolean applyWeaponPreset(ItemStack stack, String ref) {
    // 空 / 非法 id → false；BuiltInRegistries 里没有这个组件（没装 BetterCombat）→ false，静默
    DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(PRESET_ID_COMPONENT);
    if (type == null) return false;
    stack.set((DataComponentType) type, id);
    return true;
}
```

- **零 import**、零 `compileOnly`（D15）：组件按字符串从注册表捞，`ResourceLocation` 与
  `DataComponentType` 都是原版类型。
- `build()` 末尾 `applyWeaponPreset(stack, content.weaponAttributes())`。
- `ContentTooltip.lines()` 末尾追加 `动作 <id>` 一行——**这是服务端唯一能证的证据**（组件挂上了），
  不声称「挥得动」（那要客户端）。

### 3.3 一处口径修正（R7-b）

初版只给 `redtale_project:` 前缀挂组件，理由是「别碰别人的东西」。实测发现这是错的：

- 生成器跳外部命名空间是对的（不能写别人 jar 里的文件、更不能同名覆盖）；
- 但**组件必须任何命名空间都挂**——`bettercombat:claymore` 的定义在别人 jar 里已经存在，
  我们只是**引用**它，引用不是覆盖。

不挂的后果：`demo_longsword` 写了 `weapon_attributes: "bettercombat:claymore"`，实际被静默忽略，
BetterCombat 回落到「按物品注册 id 找预设」→ 找不到 → 不挥动。这是「配置写了但没生效」的典型坑。

### 3.4 实测

```
> redtale tooltip demo_longsword
[redtale] 示范长剑（redtale_project:sword）
  ≈ 9.6 /次（physical_damage +20%）
  攻速 12.5 tick · 1.6 次/秒（未持有，不含药水/急迫）
  动作 bettercombat:claymore          ← 外部命名空间：只挂引用，不生成文件

> redtale tooltip test_sword
[redtale] 验收长剑（redtale_project:sword）
  ≈ 6 /次
  攻速 16.667 tick · 1.2 次/秒（未持有，不含药水/急迫）
  动作 redtale_project:test_sword     ← 自家命名空间：引用 + 生成定义
```

同时 `data/` 下仍只有 `redtale_project`，`bettercombat` 目录没被创建 —— 生成器的边界没被破坏。

`gradle selftest --offline` = **55 PASS**（本轮无纯 Java 逻辑变更，与 R6 同数，符合预期）。

## 4. 派单

在制品 R6 剩 T-0002 / T-0003（都还 `OPEN`），T-0001 验收后清空 → 派 **T-0004**：

> 附魔 schema → 数据包生成器（`docs/ENCHANT-API.md` §3 的 L1→L0 层）。
> 单文件 `enchants/EnchantmentGenerator.java`，接口 `generateAll(Path, List<ConfigFile>, List<String>)`，
> **不接 `RedtaleCore`**（M4 才接线），验收 = build 退出码 + 两处 grep + jar 条目。

选它的理由：纯 JSON 转换、零跨模块依赖、与 T-0002（占 `RedtaleCommand`）/ T-0003（占 `items/`）
文件不相交，S8 不会撞卡；而且把 `enchants/` 源码集开出来（D2）。

在制品 3/3 封顶，本轮不再派。

## 5. 留下的东西

| 路径 | 是什么 |
|---|---|
| `src/main/java/com/redtale/project/items/ItemFactory.java` | +`PRESET_ID_COMPONENT` / `applyWeaponPreset` / `weaponPresetOf` |
| `src/main/java/com/redtale/project/items/ContentTooltip.java` | +「动作 <id>」行 |
| `docs/TASKS.md` | T-0001 → `VERIFIED`（含 3/3 验收记录）、新增 T-0004 |
| `PLAN.md` | §13 R7 行、§13.11（R7-a~d 四条口径 + R8 五步） |
| `E:\server-test\config\redtale_project\{items/t_test_sword.json5, weapon_attributes/test_sword.json5}` | 持久验证件（下次验武器动作链路直接用） |

测试服已停（25567 无监听），`probe_pack` 已删。

## 6. 挂账（第 7 轮仍挂）

1. 运行期生成的数据包需一次 `/datapack enable` → R8 用 `PackRepository#addPack` + `reloadResources` 解决，
   **未验证能否在 reload 中重入**，先单独验。
2. 真人手持时的 tooltip / 挥动性 / BetterCombat 双持 ×1.2 是否与我们重复计 —— 全部需要一次带客户端的窗口。
3. `pvp` 真目标（R6 起）。
