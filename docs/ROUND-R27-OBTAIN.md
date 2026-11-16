# R27 · 2026-11-17 06:29-07:05 · M4 收尾（V4 可获取性 tag）+ M5-A 第一条原语

## 0. 本轮定位

M4 出口 8/11，剩 V4/V5/V6 全是 GUI 面。其中 **V4（附魔台能刷出自家附魔）缺的不是界面，是三条原版可获取性 tag** ——
那张卡（T-0020）自 11-16 21:15 派出去挂了两轮无人领，按 `B-01·默认` 收回自做。

同时 M5-A 的引擎被 T-0021（`SkillSpec`）/ T-0022（`SkillCooldowns`）挡着（引擎要 import 那两个类），
所以本轮自研挑的是**不被挡的那一段**：`damage` 原语（SKILLS §4 第 1 条）。它是 24 条原语里唯一
能把「三层模型」一次验穿的那条 —— 白值 → L2 乘区 → 原版护甲，三段的数字都能单独对账。

## 1. 卡板

| 卡 | 动作 | 依据 |
|---|---|---|
| T-0020 | `[OPEN]` → **`[DROPPED] (S8 静默两轮，S7 自做)`**，进度行补成完整交付记录 | 11-16 21:15 派，到本轮 06:29 已两轮；`B-01·默认` 明写「仍 OPEN → 收回自做」。**不重派**（R27-d）：重派等于让 S8 去改 S7 刚写完的文件 |
| T-0021 / T-0022 | 保持 `OPEN`（05:50 / 05:52 派，S8 05:41 刚交付 T-0019，属正常在制） | 未到三振，不拆 |
| T-0023 | **新派**：`/redtale enchant obtain` 可获取性 tag 只读对账 | 见 §4 |

在制品：T-0021 + T-0022 + T-0023 = **3/3**（封顶）。

## 2. 自研 A：`EnchantObtainTags`（T-0020 的落地物）

`src/main/java/com/redtale/project/enchants/EnchantObtainTags.java`（156 行）

- 三份 tag **写死**（`in_enchanting_table` / `tradeable` / `on_random_loot`），不从配置读 —— 从配置读等于
  允许内容侧静默关掉获取途径。
- 写法与 `EnchantableTags` 同构：`data/minecraft/tags/enchantment/<tag>.json`，`"replace": false` 追加，
  文本逐字相等就不写（幂等，不改 mtime）。
- 取值只读 `enchantments/*.json5` 的 `id`；不含 `:` 或非字符串 → 1 条 WARN 跳过。**不按文件名拼命名空间**。
- 空集也写三份空 `values`（清场语义，R27-c）。
- `RedtaleCore.reload()` 第 165 行 +1 行接线（`EnchantableTags` 之后）。

### 离线探针 `build/probe/ObtainProbe.java` → **20/20 PASS**

覆盖：3 份写出且 `replace:false` 且 values 2 条升序 / 坏 id 只 1 条 WARN 且不进 values /
`items/` 前缀被忽略 / 末尾换行 / 二次调用返 0 且 mtime 不变 / `datapackRoot=null` → 0 + 1 WARN /
`configs=null` 仍写 3 份空 tag / `warnings=null` 不 NPE / 缺 `id` 与数字 `id` 各 1 条 WARN。

> 探针踩的坑：Git Bash 下 `javac -cp "a;b;c"` 会被 MSYS 路径转换吃掉分号 → Gson 找不到。
> 绕行 = `export MSYS2_ARG_CONV_EXCL='*'` 或写 argfile。

## 3. 自研 B：`damage` 原语（M5-A）

`src/main/java/com/redtale/project/core/script/builtin/DamageEffect.java`（约 190 行）+ `Builtins` +1 行。

口径：
- 只 `target.hurt(source, (float) amount)`，**绝不 `setAmount`**（SKILLS §5 红线 1 / R25-c）。
- `damage_type` 缺省 `redtale_project:skill_physical`；**两级回退**：不在注册表 → 回缺省 + WARN 一次；
  连缺省都查不到（数据包没加载）→ `level.damageSources().generic()` + WARN，**本次不吃乘区**（R27-b）。
- `radius` 不支持，>0 打一行 WARN 并忽略（R27-a）。
- 打 `ctx.target()`；没有就跳过 + WARN 一次（不退回打自己）。
- 施法者 = `player()` → `vars.attacker` → null（只影响记账与死亡文案）。

### 验收命令 `/redtale damage hit probe [amount]`

一次跑五组（FakePlayer 攻击一只 1000 血 / NoAI 的牛，每次打前把血与 `invulnerableTime` 归零）：

```
[redtale] damage 原语端到端（白值 10，目标：牛 1000 血 / NoAI，攻击者：FakePlayer）：
  ✓ A 白值 · 零属性：白值 10 → 实际掉血 10（期望 10，×1）
  ✓ B 白值 · physical_damage +0.25：白值 10 → 实际掉血 12.5（期望 12.5，×1.25）
  ✓ D 法术类型 · 只堆物理（应不吃）：白值 10 → 实际掉血 10（期望 10，×1）
  ✓ C 法术类型 · magical_damage +0.5：白值 10 → 实际掉血 15（期望 15，×1.5）
  ✓ E 不存在的类型 · 回缺省物理轴：白值 10 → 实际掉血 12.5（期望 12.5，×1.25）
[redtale] 五组里 5 组符合预期 ✓
```

**A 与 B 必须一起看**：A 证明原语没自己乘（否则会是 12.5），B 证明 `DamageLayer` 接上了（否则还是 10）。
C 与 D 一起证明 Q-018 的分轴是真的（法术只吃 `magical_damage`）。

`docs/SKILLS.md` §4 状态列 `| 1 | damage | … | — |` → `✅ R27`；`grep -c "^| [0-9]"` 仍 = **24**。

## 4. 派单 T-0023：`/redtale enchant obtain`

为什么派这张：tag 生成出来了，但**没有观测面** —— 附魔台刷不出时，无法区分「tag 没写」「附魔没生成」
「写错 id」。命令把两份产物对账，逐 tag 打「已收录 N/应收录 M + 差集」，`tag 未生成` 单独成一种文案
（与「生成但为空」区分）。

接口写死在卡里：`node()` / `obtain(ctx)` / 两个**包内可见**纯函数 `collectEnchantIds(root)`、
`readTagValues(root, tag)`（后者「不存在 → null」是为了让探针能区分未生成与空集）。
独占 `EnchantObtainCommand.java`（新建）+ `RedtaleCommand.java`（+≤2 行）。

## 5. 测试服实测（1 次启动，已停）

- `Done (2.213s)`；reload = 2 条脚本 / 23 份配置文件 / 10 件装备内容。
- 三份 tag 落盘：`world/datapacks/redtale_project/data/minecraft/tags/enchantment/{in_enchanting_table,tradeable,on_random_loot}.json`，
  内容 `{"replace": false, "values": ["redtale_project:soul_reap", "redtale_project:super_sharpness"]}`（升序）。
- `/redtale reload` 后三份文件 mtime 仍 `06:33:35.80` → 幂等。
- README 的 effect 表多出 `damage` 一行（注册表已含该原语）。
- 日志：redtale **只有 1 条 WARN**，正是 E 组「未知类型回缺省」这条**预期**告警；4 条 ERROR 是
  `RecipeManager`（simplyswords / brewinandchewin / minecraft:red_dye）既有噪声，无 tag 解析告警。
- 停服后 `netstat | grep LISTEN` 对 25567/25577 无输出（只剩客户端侧 TIME_WAIT）。

## 6. 数字

| 项 | 值 |
|---|---|
| `gradle build --offline` | exit 0，jar **354540 B**（R26 是 344079 B） |
| `gradle selftest --offline` | **78 PASS**（无新增单测：本轮两个新类的行为都在探针与命令里验） |
| `build/probe/ObtainProbe.java` | 20/20 |
| `/redtale damage hit probe` | 5/5 |
| `grep -c "^| [0-9]" docs/SKILLS.md` | 24（原语预算未变） |

## 7. 未验证 / 挂账

1. **V4 的最后一格**：附魔台真能刷出自家附魔 —— 需要客户端窗口点附魔台。tag 侧已就位（资源层，
   启动即加载，无 tag 解析告警），但「刷不刷得出」本轮只能到「tag 存在且 id 对上」为止。
2. **`damage` 原语在真人技能释放里**：本轮走的是 `ScriptContext.ofEntity(FakePlayer, 牛)`，
   与未来 `SkillEngine` 的上下文构造路径不同（技能会是 `ofPlayer` + `target`）。等引擎落地再验一次。
3. **`damage` 与暴击**：`CritLayer` 挂在 `CriticalHitEvent` 上（只有玩家平砍会触发），技能伤害不吃暴击 ——
   这是现状，**不是 bug**，但 M5-B 若要「技能暴击」得另开一条路径（记在这里免得以后当缺陷查）。
4. **T-0021 / T-0022 / T-0023** 三张卡待下轮验收。
