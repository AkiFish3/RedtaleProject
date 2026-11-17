# R39 · 2026-11-17 · 验收 T-0033 + M6 第三步配套（`#minecraft:undead`）+ D14 资源重载补漏

> 结论先行：`projectile` 过验收（4/4）；配置文件里第一次能声明「这只怪是亡灵」并真的进 `#minecraft:undead`；
> 顺手修掉一个 **D14 热重载漏点** —— 以前 `/redtale reload` 只重写磁盘、不重载资源，tag 类内容必须再敲一次原版 `/reload`。

## 1. 卡板

| 卡 | 动作 | 依据 |
|---|---|---|
| T-0033 `projectile` | `[DONE]` → **`[VERIFIED]`** | 4/4，见 §2 |
| T-0035 `mobs/DropSpec` | **派单**（`OPEN`） | 掉落表 v1 解析器，纯解析单类 + 探针 ≥24 条 |

在制品：T-0034 + T-0035 = 2/3。T-0033 的四条 NEED-S7 全部采纳（不改代码）。

## 2. T-0033 验收证据

1. `gradle build --offline` **EXIT=0**（jar 471397 B）；jar 内 `script/builtin/ProjectileEffect.class` = **1**
2. `wc -l` = **152**（≤170）；`addFreshEntity` = **1**；`setAmount|teleport|knockback(` = **0**；`Builtins.java` diff 严格 **+2**
3. S8 探针 **38/38**；S7 独立探针 `build/probe/S7ProjProbe.java` **26/26**
4. 测试服（`Done (2.105s)`，已停）：`skills/t_arrow.json5`（`projectile` / `speed 3.0`）
   → `/redtale skill probe "redtale_project:t_arrow"` → 日志 `[script] projectile · [Minecraft] · minecraft:arrow · 速度 3.00`

**初速取证（本轮最有用的手法）**：

```
tick freeze
kill @e[type=minecraft:arrow]
redtale skill probe "redtale_project:t_arrow"
data get entity @e[type=minecraft:arrow,limit=1] Motion
→ [2.207920019231337d, -2.031031557774889d, 2.7039222632132223E-16d]   模长 = 3.0000
```

不冻结时同一发箭 2 秒后读到 `[1.485, -1.416, 0]`（drag 0.99 + 重力 0.05 已吃了几十 tick），
**看起来像「速度算错」其实是采样太晚**。以后所有「验初速」的验收一律先 `tick freeze`。

## 3. 自研：`undead` 配置 → `#minecraft:undead` 标签

### 3.1 改了什么

| 文件 | 改动 |
|---|---|
| `mobs/MobSpec.java` | `Definition` 9 → **10** 字段（`+undead`）；`undead` 缺省 `false`，非布尔 → `false` + 1 条 WARN |
| `mobs/MobEntityTags.java` | **新建**：`undeadTypes(defs)` 纯函数 + `generateAll(root, defs, warnings)`；写 `data/minecraft/tags/entity_type/undead.json`，`replace:false`，空集也写 |
| `core/datapack/TagJson.java` | **新建**：写盘收口（`write` 写 tag、`writeIfChanged` 写任意文本），幂等比较 + 统一失败文案 |
| `core/datapack/PackEnabler.java` | 新增 4 参 `ensureEnabled(..., resourcesChanged)` + `Outcome.ALREADY_SELECTED_AND_RELOADED` + `passGuard()` |
| `core/RedtaleCore.java` | `reload()` → 委托 `reload(boolean allowResourceReload)`；累加各生成器的 `written` 传给 `PackEnabler` |
| `core/command/RedtaleCommand.java` | `/redtale reload` 传 `true`（只有管理员显式 reload 才允许资源重载） |
| `enchants/{EnchantableTags,EnchantObtainTags}.java` | 各自的 `writeTag` 改成委托 `TagJson.write`（三份复制 → 一份） |
| `enchants/EnchantmentGenerator.java` / `items/WeaponAttributesGenerator.java` | 无条件 `writeString` → `TagJson.writeIfChanged`（**幂等**） |
| `resources/redtale/defaults/mobs/ash_caster.json5` | 加 `undead: true`（示范 + 默认可验） |

### 3.2 为什么必须做

`undead_damage` 条件乘区的唯一判据是 `EntityTypeTags.UNDEAD`（`TargetClass#of`）。8 个原型是我们自己注册的
`EntityType`，原版 tag 里没有它们 → 配置里写亡灵也**吃不到**那一段，现象只是「伤害不涨」，没有任何报错。

### 3.3 修掉的 D14 漏点（本轮真正的收获）

第一版实测：`/redtale reload` 之后 tag 文件里明明有 `redtale_project:caster`，但

```
execute if entity @e[type=#minecraft:undead,type=redtale_project:caster]   → Test failed
```

再敲一次原版 `/reload` 才变成 `count 2`。原因是 `PackEnabler`：

```java
if (repository.getSelectedIds().contains(id)) {
    return Outcome.ALREADY_SELECTED;      // ← 包早就选中，直接返回，一个字节都没重载
}
```

改成：包已选中 **且本次真写了文件** 且 2s 去重窗通过 → `reloadResources` → `ALREADY_SELECTED_AND_RELOADED`。

**副作用**：`written` 变成「要不要重载」的开关，于是顺手发现 `EnchantmentGenerator` / `WeaponAttributesGenerator`
**无条件重写**（内容逐字不变也刷 mtime）→ `written` 恒 ≥ 3 → 每次 reload 都白重载一遍。改成 `writeIfChanged` 后
启动期与稳态 reload 的 `written` 都回到 **0**。

## 4. 实测（测试服起停 4 次，均已停，25567/25577 无 LISTEN）

| 组 | 操作 | 结果 |
|---|---|---|
| A | 启动（配置已有 `undead: true`） | `生成数据包状态：ALREADY_SELECTED（本次写出 0 个文件）`；启动即 `@e[type=#minecraft:undead,type=redtale_project:caster]` **count 2** |
| B | `/redtale reload`（无改动） | `ALREADY_SELECTED（写出 0 个文件）` —— 不再白重载 |
| C | 删掉 `undead: true` → reload | `ALREADY_SELECTED_AND_RELOADED（写出 1 个文件）`；tag `values: []`；caster **立刻掉出**选择器（husk 仍 count 1） |
| D | 恢复 `undead: true` → reload | 写 1 → `count 2` 立刻恢复 |
| 回归 | `mob spawn ash_caster` / `mob check` / `validate` | 68/68 血且进标签（count 3）/ `2 只 · 引用完整 ✓` / `问题 0 条` |

`build --offline` exit 0（jar **474566 B**）、`selftest` **78 PASS**、`S7UndeadProbe` **19/19**、redtale 零 ERROR/WARN。
日志里的 12 条 ERROR 全是既有的 4 条 `RecipeManager` 噪声 × 3 次资源加载（启动 + 两次 reload），不是新问题。

## 5. 口径（详见 `PLAN.md` §13.42）

- **R39-a** 亡灵粒度上限 = 原型（tag 只能按 EntityType 打，EntityType 按 base 注册）
- **R39-b** `undead` 坏值降级 `false` + WARN，不整条跳过
- **R39-c** `/redtale reload` 在真写了文件时自己 `reloadResources`；开服那次不做
- **R39-d** 所有写盘点必须幂等；不幂等 ⇒ 白重载 + 「写出 N 个」失真
- **R39-e** 磁盘与内存是两层：验「进内存了没」看选择器，验「文件被重写没」看 mtime/md5

## 6. 未验证 / 挂账

- 真人客户端侧的 `undead_damage` 数字与 tooltip（无客户端）
- 自建怪身上 `pve + undead` 双键的实际堆叠（同上）
- `undead` 是否影响原版机制（`instant_damage` 治疗、亡灵杀手附魔）—— 逻辑上会，未实测

## 7. 下一步（R40）

1. 验 T-0034（实体 lang）/ T-0035（`DropSpec`）
2. `MobSkillCaster`（怪物放技能，需真人在线跑 AI，验收要两段式）
3. `DropSpec` 接进 `MobSpec` + 死亡掉落 + `mob check` 引用校验
