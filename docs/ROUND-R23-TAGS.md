# R23 · M4 第三块：基底附魔归属（内容武器终于吃得上附魔）+ 空互斥 tag 收口

时间：2026-11-16 20:00–20:25 · 测试服 `E:\server-test`（起停 2 次，均已停、25567/25577 无 LISTEN）

## 1. 卡板

| 卡 | 结果 |
|---|---|
| T-0017 `tools/gen_enchant_lang.py` | **VERIFIED**（3/3，见 §2） |
| T-0018 `validate` 第 5 条 | 仍 **OPEN**（连续第 2 轮无人领，未到三振拆卡线） |
| T-0019 `/redtale enchant list` | **新派**（OPEN，独占 `EnchantListCommand.java` + `RedtaleCommand.java` 追加 2 行） |
| 在制品 | T-0018 + T-0019 = **2/3** |

## 2. 验收 T-0017

| # | 命令 | 结果 |
|---|---|---|
| 1 | `python tools/gen_enchant_lang.py` | exit **0**，输出 `解析到 2 份附魔定义：soul_reap, super_sharpness` / 两份各 83 键，`enchantment 键 2 → 2` |
| 2 | 卡里的 `json.load` 断言 | 逐字输出 `超级锋利 摄魂 66 14 Soul Reap` ⇒ `attribute.*` 66 条、`item.*` 14 条一条没丢 |
| 3 | 连跑两次 `md5sum` | zh `8ed451042cd0c31bc9fd549e649d22ed` / en `07a7f16cba41558720b9b7c68348ceb0`，**前后逐字相同**（幂等） |

S8 在进度行提的口径②（新键追加末尾，与 `gen_item_lang.py` 的整体 `sorted()` 不一致）**不改**：
两个脚本各自拥有互不相交的键前缀，交替跑不会丢值，顺序差异只体现在 diff 噪点；等 M4 附魔数量上两位数再统一排序。
文件归 S7。

## 3. 本轮自研：`enchants/EnchantableTags`

### 3.1 问题（R22 实测的硬边界）

`/enchant redtale_project:sword redtale_project:soul_reap 1` 报 `Sword cannot support that enchantment`。
原因不是我们的附魔写错：1.21.1 的 `supported_items` 是**数据驱动 item tag**，
原版 `#minecraft:enchantable/sharp_weapon` 里只有原版 id，14 个基底一个都不在里面。
连带后果：**原版锋利/保护/耐久也一件都上不去** —— 这比自己附魔失效严重得多。

### 3.2 口径修订（R22-e → R23-a）

R22-e 原话是「不往原版 `#minecraft:enchantable/*` 里塞」。本轮实测推翻了它的前提：

- 本机 `[冒险]simplyswords-neoforge-1.70.2-1.21.1.jar` 里就有
  `data/minecraft/tags/item/enchantable/{sharp_weapon,sword,weapon,fire_aspect,durability,...}.json`；
- `[农业]FarmersDelight-1.21.1-1.3.4.jar` 里也有 `data/minecraft/tags/enchantment/non_treasure.json`（内容是别的 mod 的 id）。

即：**往别人的命名空间里写 tag、只追加值，是 1.21 生态的通行做法**（tag 默认按值合并）。
红线应该落在 `"replace": true`（那才是抹掉别人的数据），不是「不许写在 `data/minecraft/` 下」。
按新口径落地：`"replace": false` + 只写自家 id。

### 3.3 实现

`src/main/java/com/redtale/project/enchants/EnchantableTags.java`（新类，9.9 KB，无内部类）：

- `vanillaTags()`：遍历 `ItemBase.values()` → `tagsFor(base)` → 7 个 tag 的 id 集合（`TreeSet`，输出稳定）；
- `generateAll(root, configs, warnings)`：写 7 份 `data/minecraft/tags/item/enchantable/<名>.json`；
  再扫 `enchantments/*.json5` 的 `exclusive_set`，指向 `#redtale_project:` 而文件不存在 → 补 `values: []` + 一条告警；
- `writeTag`：**内容与磁盘一致就不写**（幂等，mtime 不动，避免整包反复重载）；
- `RedtaleCore.reload()` 接线 2 行（紧接 `EnchantmentGenerator`，同一个包根）。

归属映射（跟着基底走，不跟内容走）：

| 基底 | tag |
|---|---|
| sword | sharp_weapon · weapon · fire_aspect · durability |
| dagger / axe / hammer / spear / staff | sharp_weapon · weapon · durability |
| bow | bow · durability |
| shield | durability |
| helmet / chestplate / leggings / boots | armor · equippable · durability |
| ring / amulet | durability |

## 4. 实测（测试服 2 次启停）

### 4.1 第一次启动：生成落盘

`Done (2.132s)`；`/redtale reload` = 2 条脚本 / 23 份配置 / 10 件内容。
`world/datapacks/redtale_project/` 下新增 **7 份** item tag + **1 份**空互斥 tag，8 份全部 `json.load` 通过：

```
data/minecraft/tags/item/enchantable/{sharp_weapon,weapon,fire_aspect,bow,armor,equippable,durability}.json
data/redtale_project/tags/enchantment/exclusive_set/lifesteal.json   ← {"replace": false, "values": []}
```

`sharp_weapon.json` 内容 = `axe / dagger / hammer / spear / staff / sword`（6 条，升序）；
`armor.json` = 四件甲；告警一行：`互斥 tag #redtale_project:exclusive_set/lifesteal 原本不存在，已生成空 tag`。

### 4.2 `/reload` 之后立即可附（tag 是资源，不进「要重启」那一档）

同一次启动内 `reload` 后，木桩僵尸主手换 `redtale_project:sword`：

| 命令 | 结果 |
|---|---|
| `/enchant … minecraft:sharpness 3` | **Applied enchantment Sharpness III**（原版锋利，R22 报 cannot support 的那条） |
| `/enchant … redtale_project:soul_reap 1` | **Applied … Soul Reap I** |
| 再叠 `redtale_project:super_sharpness 2` | `Sword cannot support` ⇒ **互斥生效**（两者同在 `#minecraft:exclusive_set/damage`） |
| 干净剑上 `super_sharpness 2` | **Applied … Super Sharpness II**（换件即可，说明上一条是互斥而不是缺失） |
| `bow` + `minecraft:power 2` | **Applied … Power II** |
| `helmet`（主手位）+ `protection 2` / `unbreaking 3` | **Applied** ×2 |
| `shield` + `unbreaking 2` | **Applied** |
| `ring` + `sharpness 1` | `Ring cannot support`（饰品不进武器批，符合 §3.3 映射） |

### 4.3 第二次启动（`Done (2.086s)`）：空互斥 tag 收口 + 幂等

- `Not all defined tags for registry … enchantment are present in data pack` 计数 **1 → 0**（第一次启动 20:02:37 仍有，
  因为空 tag 是 20:02:40 才写出来的 —— 与 R21-c 同型的「启动期生成物本次看不到」）；
- **不跑任何 `/reload`**，`dagger` 直接 `super_sharpness 1` → Applied ⇒ tag 随数据包在启动期就进了注册表；
- `dagger` 先 `super_sharpness` 再 `minecraft:sharpness` → 第二条被拒（互斥双向成立）；
- 8 份 tag 的 mtime 在第二次启动 + 一次 `/redtale reload` 后**仍是 20:02:40**（幂等，未重写）；
- redtale 零 ERROR；`ERROR` 总数 8 = 4 条 RecipeManager 既有噪声（启动）+ 同 4 条（我跑的那次 `/reload`）。

## 5. 三条要记住的事实

1. **`supported_items` 是 tag，不是列表**：内容物品吃不上附魔，先怀疑 tag，不要先怀疑附魔 JSON。
   验 tag 是否生效最快的办法：主手换那件，`/enchant` 一条原版附魔看返回。
2. **追加 ≠ 覆盖**：`data/minecraft/tags/...` 是合法落点，只要 `replace` 不是 `true`；
   真正会「改别人数据」的是 `replace: true`。R22-e 的红线位置本轮已修正（§3.2）。
3. **tag 与附魔注册表的生效层不同**：tag 属资源（`/reload` 就生效），`enchantment` 属启动期 datapack registry
   （必须重启）。同一个数据包里两样东西的生效时机不一样，排障时别一起重启。

## 6. 下一步（R24）

1. 验收 **T-0018 / T-0019**（T-0018 已连挂两轮，若下轮仍 `OPEN` 且无进度行，考虑换更小的卡）。
2. **`soul_reap` 的执行体**：`heal` 原语 + `ScriptContext` 带 affected 实体（现在 `ofServer` 拿不到实体）
   → 加原语要换 jar，按 D17 记账。
3. **V11 硬指标演练**：不编译，只写一份新的 `enchantments/*.json5` + reload + 重启，看能否附上（加第 N 个附魔只写 JSON5）。
4. 挂账第 22 轮（需客户端窗口）：附魔 tooltip 中文名现已可验（T-0017 已 VERIFIED）。
