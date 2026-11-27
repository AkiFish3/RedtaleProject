# R139 · 2026-11-28 00:4x~01:1x · 验 T-0133（词条 3→8 + `core` 段首开，**死分支点亮**）+ 派 T-0134（护甲词条池）

## 0. 一句话

T-0133 `[DONE]` → `[VERIFIED]`（3/3，S8 零偏差）：`core` 段 18 轮以来的死分支第一次被摇到——连摇 15 次、15/15 命中 `runed`；顺带挖出「默认配置只补缺不覆盖」这条验证程序坑（R139-a），派 T-0134 补护甲词条池。

## 1. 卡板：T-0133 `[DONE]` → `[VERIFIED]`

### 1.1 静态三条（逐字复跑）

| 验收 | 命令 | 卡面期望 | 实测 |
|---|---|---|---|
| 1 | `check_affix_reach -v` | `已检查 8 份词条定义 · 1 个词条池 · 3 个有效槽位段 · 物品等级 1` / `0 条问题` | 逐字一致 |
| 1 | `check_affix_pool_refs -v` | `1 份 / 8 条定义 / 8 条引用` + 0 | 逐字一致 |
| 1 | `check_affix_weights` / `check_affix_levels` | 各 0 | 各 0 |
| 2 | `check_defaults` | `清单 53 条 · 磁盘 53 个 · 问题 0 条` | 逐字一致 |
| 2 | `check_numeric` / `check_items` / `check_all` | 0 / `23 个文件 · 0 条` / `74 跑成 74 红 0` | 逐字一致 |
| 3 | `wc -l` ×5 / `git status` / `numstat` / 禁词 | `13/11/11/11/11` / 5 `??` + 2 ` M` / `5 0` / 0 | 全对 |

五份新定义与池**逐字对比卡面表格**：`id` / `kind` / `weight` / `item_level` / `exclusive_group` / `stats` 数值 / 注释全部一致，`ConfigStore` 五行插入位置在 `affixes/of_the_bear.json5` 之后、顺序 `keen→executioner→of_the_hunt→of_ruin→runed`，插在 `affix_pools/sword_common.json5` 之前。

### 1.2 构建 + 真机（S7 跑的，S8 不启服）

- `gradle build --offline`（取锁 → 构建 → 删锁）EXIT=0，jar 699235 B；`unzip -l` 内 `redtale/defaults/affixes/{keen,executioner,of_the_hunt,of_ruin,runed}.json5` 五份齐全。
- 测试服 `Done (2.972s)`；`/redtale reload` = **57 份配置文件**（原 52，+5 逐字对上）。
- `/redtale validate` → `15 件内容 / 1 个词条池 / 8 条词条定义 / 2 个套装 · 技能 4 条 · 问题 0 条`。

### 1.3 `core` 死分支首证（本卡真正的出口）

第一次连摇 12 次，**全部只有 2 条词条**（`sharp/heavy · of_the_bear`），第 3 条与 4 条新词条一次都没出现。查盘后定位根因不在代码也不在内容，在**验证环境**：

```
E:/server-test/config/redtale_project/affix_pools/sword_common.json5   ← Nov 16 21:12（旧版：slots.core = 0、只有 2 前缀 1 后缀）
E:/server-test/config/redtale_project/affixes/{keen,...}.json5          ← Nov 28 00:48（新，自动补种）
```

`ConfigStore.ensureDefaults():148` 是 `if (Files.exists(target)) continue;` —— **只补缺、不覆盖**。新加的文件被种进去了（所以 `reload` 报 57 份、`validate` 认 8 条定义），但**改过**的池子仍是 11-16 的旧版。把新池同步到 live 后再 reload：

```
词条 3：sharp · of_ruin · runed         词条 3：executioner · of_the_hunt · runed
词条 3：keen · of_the_hunt · runed      词条 3：executioner · of_the_bear · runed
词条 3：keen · of_ruin · runed          词条 3：heavy · of_the_bear · runed
```

**15 / 15 都是 3 条，`core` 段 15/15 命中 `runed`**；prefix 四个（`sharp` / `heavy` / `keen` / `executioner`）、suffix 三个（`of_the_bear` / `of_the_hunt` / `of_ruin`）都出现过 → 三条分支全部通电。tooltip 完整样张：

```
[redtale] 示范长剑（redtale_project:sword）
  ≈ 15.744 /次（physical_damage +31.2%）
  攻速 12.5 tick · 1.6 次/秒（未持有，不含药水/急迫）
  词条 3：heavy · of_the_bear · runed
```

## 2. 派 T-0134（护甲词条池 `armor_common` + 4 条护甲词条）

**为什么派**：验收时顺手看 `demo_chestplate` 的 tooltip —— 只有「套装 灰烬誓约 0/3」，**零词条行**。查证：`affix_pools/` 只有 `sword_common`，`applies_to` = `sword/axe/dagger`；四件防具基底摇不出任何词条。D22 原文不限武器（「`affix_pools/*.json5`，`applies_to` 按基底分池」），属**内容缺口**不是范围决策。

**接口口径**：4 条词条只写原版属性（`armor` / `armor_toughness` / `max_health` / `knockback_resistance`，全是 `AttributeSpecs` 的 `v(...)` 条目，走原版 `ATTRIBUTE_MODIFIERS` 结算），排除 `damage_reduction` / `dodge_rating` / `magic_resistance` / `block_rating` 这些零消费者的自定义属性（R138-b 延续）。池 `slots: {prefix 1, suffix 1, core 0}` —— 护甲不需要 core 段。

**派前整份副本预验**（`build/armoraffix_s7/defaults`，内容整份写入后跑）：

| 尺 | 基线（现网） | 候选副本 |
|---|---|---|
| `check_affix_reach -v` | 8 定义 / 1 池 / 3 段 | **12 定义 / 2 池 / 5 段** |
| `check_affix_pool_refs -v` | 1 池 / 8 定义 / 8 引用 | **2 池 / 12 定义 / 12 引用** |
| `check_items` | 23 文件 / 0 | **28 文件 / 0** |
| `check_affix_weights` / `levels` / `check_numeric` / `check_name_coercions` | 0 | 0 |

副本用后已删。`check_defaults` 预期 53 → **58**（`ConfigStore` +5 行）。

## 3. 口径 §13.142（R139 五条）

| 编号 | 口径 |
|---|---|
| R139-a | **「只补缺不覆盖」是默认配置的硬语义，验收前必须同步 live**：`ensureDefaults()` 存在即跳过，所以改过的默认文件升级 jar 后永远到不了已有服。`reload` 报的是「读了几份」，不是「内容等于 jar」。本轮为此白摇 12 次、差点写「内容没生效」。 |
| R139-b | **内容卡分两类，验证成本不同**：只**新增**文件（T-0133 的 5 条词条、T-0134 全部）→ 自动补种，验一遍就过；**修改**已有文件（`sword_common.json5`）→ 必须手工同步 live，否则验的是旧内容。派卡时写明属于哪类。 |
| R139-c | **死分支点亮的判据是「每次都命中」，不是「出现过一次」**：`core` 段只有一条内容，理论上 100% 命中 —— 15/15 才算证明 `slots.core=1` 真的接线。若只有 3/15 出现，那说明 `slots` 读的是别的字段。 |
| R139-d | **tooltip 是内容卡的廉价真机判据**：`/redtale tooltip <id>` 走 `ItemFactory.build` 真摇一次，控制台可调、不需要玩家在线、不需要客户端。内容卡验收优先用它，别一上来就开客户端。 |
| R139-e | **裸名纪律第五十七次**：只写裸名（`check_affix_reach`），不写 `tools/` 全路径。 |

## 4. 环境

- 构建 1 次（取锁 → `gradle build --offline` EXIT=0 → 删锁），无残留锁。
- 测试服 **1 启 1 停**：`Done (2.972s)` → `stop` → `Thread RCON Listener stopped` + chunks saved；25567 / 25577 无 LISTEN。残留 `java.exe` 仅 Gradle daemon（`--add-opens` 命令行，非服务器），未杀。
- **正式服未触碰**（25565 全程无监听）。`src/` 本轮末零 diff（除 T-0133 那 5+2 项，已入库）。
- 部署：`mods/redtale_project-0.1.0.jar` 699235 B，旧包留 `.r138.bak`。
- 夹具 `build/armoraffix_s7/` 用后删除。

## 5. 下一步（R140）

1. **验 T-0134**：三条静态 + 构建 + 测试服 `reload` **57 → 62 份**、`tooltip demo_chestplate` 出现「词条 2：…」行（护甲首个词条实证）；玩家穿着时的属性实挂留真机。
2. **M7 真机进服**：`rt-test` → 多人游戏 → 直接连接 `127.0.0.1:25567`，需 Aki 在线或桌面可交互。
3. Q-021 ~ Q-025 等 Aki；Q-025（护甲 tooltip 自身属性行）与 T-0134 不冲突 —— 前者是显示面，后者是数值面。
