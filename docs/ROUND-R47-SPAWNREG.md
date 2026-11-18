# R47 · 2026-11-18 · 验收 T-0042 + M6 第二层刷怪快照（D26）

## 一句话

`SpawnRuleSpec`（L1）验收通过；自己补上 L2 `SpawnRegistry`（全序排序 + `mob` 交叉校验 + 双视图），默认刷怪规则进 `DEFAULT_FILES`；派 T-0043（`/redtale spawn list|check`）。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0042 `SpawnRuleSpec` | 验收 | **VERIFIED**（3/3 + S7 独立探针 108/108） |
| T-0043 `/redtale spawn list\|check` | 派单 | OPEN，在制品 1/3 |

### T-0042 验收证据

| 卡面 | 命令 | 结果 |
|---|---|---|
| 1 | `gradle build --offline` | EXIT=0，jar **565750 B**，jar 内 `mobs/SpawnRuleSpec*`=**3** |
| 2 | `grep -c net.minecraft SpawnRuleSpec.java` | **0** |
| 3 | `wc -l` = 186 ≤ 200；探针 ≥24 条 | S8 探针 76/76；**S7 独立探针 `build/probe/S7SpawnRuleProbe.java` 108/108**（A~O 十五组，断言自带） |

S8 的五条 `NEED-S7` **全采纳**：

| Δ | S8 报的 | 裁定 |
|---|---|---|
| ① | `chance: 1e999` → `Infinity` 按非数字 WARN+1.0 | 采纳。`Infinity` 不是有效概率，落 1.0 且告警比静默夹成 1.0 好 |
| ② | `conditions` 非对象 → WARN+ANY；`light`/`y` 非对象 → 静默 null；`outside` 非布尔 → WARN+null | 采纳。整体写错（类型不对）告警，子对象缺失静默 —— 与「夹取不报错」同源 |
| ③ | 重复 id 未去重，后者覆盖前者 | **采纳，不改「保留先出现」**。L2 按 id 排序，重复本身就是配置错误；`LinkedHashMap.put` 覆盖是 Java 最不意外的语义，位置仍留首次 |
| ④ | `name` 非字符串 → id + WARN | 采纳（§4 只写「缺省 = id」，坏值等同缺省 + 告警符合「语义会变才告警」） |
| ⑤ | `priority` 排序属 L2 | 采纳，L1 保持配置顺序 |

另记：未知键只在 `id`/`match`/`mob` 都合法时才报（被跳过的坏规则不叠噪声）。

## 2. 自研 M6 第二层

新类 `mobs/SpawnRegistry`：

| 成员 | 语义 |
|---|---|
| `EMPTY` | 空快照（`describe()` = `0 条刷怪规则`） |
| `load(all, mobs, warnings)` | 调 L1 解析 → `mob` 交叉校验 → 排序打包 |
| `ordered()` | **执行层唯一顺序**：`priority` 降序 → `id` 升序 |
| `declared()` | 配置文件顺序（给人看） |
| `get/has/size/sortedIds/warnings` | 常规只读 |
| `countByAction()` | 按 `replace`/`add` 计数 |
| `danglingCount(mobs)` | 悬空 `mob` 条数（观测面用） |
| `describe()` | `1 条刷怪规则（replace 1）` |

接线：`RedtaleCore.spawnRules()` + `reload()` 1 行（**排在 `mobs` 之后**）+ 回显/日志带条数 + `DEFAULT_FILES` 加 `spawn_rules/overworld_night_elite.json5`。

## 3. 实测（测试服起停 1 次）

启动 `Done (2.043s)`：

```
配置重载完成：2 条脚本，33 个文件，…，1 张掉落表，2 只怪物（3 条技能引用，原型 2 种），1 条刷怪规则（replace 1），主等级 1 条 / 职业 1 条（3 条按级属性）
```

零配置告警。坏夹具 `spawn_rules/t_bad.json5`（`action: multiply` / `mob: ghost_mob` / `chance: 0` / `light.min>max` / `time: dusk` / 未知键 `moon`）→ `/redtale reload` **5 条告警逐条对**：

```
action 只认 replace/add（写了 "multiply"），已回落 replace
spawn_rules/t_bad.json5.conditions 未知键 moon，已忽略
spawn_rules/t_bad.json5.conditions.light 的 min > max，已交换
spawn_rules/t_bad.json5.conditions.time 只认 day/night/any（写了 "dusk"），已用 any
spawn_rules/t_bad.json5: mob ghost_mob 在 mobs/ 里找不到（规则保留，补齐定义后 reload 即生效）
```

回显 `2 条刷怪规则`。删夹具后回落 `1 条`、零告警、连跑两次回显逐字相同（幂等）。测试服 `14:21:10` 干净退出，25567/25577 无 LISTEN。

## 4. 离线探针

| 探针 | 结果 | 覆盖 |
|---|---|---|
| `S7SpawnRuleProbe` | **108/108** | 全字段 / 全缺省 / 坏 id 三态 / match 四态 / action / mob / chance 五态（含 `Infinity`）/ priority·cap / min>max 交换 / time 四值 / 未知键 / 保序+不可变 / null+跳过+根非对象 / 重复 id / conditions 五变体 |
| `S7SpawnRegProbe` | **34/34** | EMPTY / 全序排序 / declared 保序 / 悬空告警 + 规则保留 / 告警随快照换代 / describe / 查找 / `mobs=null` 不炸 |

`selftest` 仍 78 PASS。

## 5. 口径（PLAN §13.50）

- **R47-a** 快照层必须排在它引用的快照之后（`spawnRules` 在 `mobs` 之后），否则交叉校验恒空表、满屏假告警且不报错。
- **R47-b** 悬空 `mob` 告警但保留规则；L3 见悬空不拦（回落原版照生成）。
- **R47-c** 排序是全序（`priority` 降序 + `id` 升序），不靠「稳定排序」兜底。
- **R47-d** L1 保配置顺序、排序只在 L2；`declared()`/`ordered()` 双视图。

## 6. 下一步

1. 验 T-0043。
2. M6 L3 `SpawnReplacer`：`MobSpawnEvent.PositionCheck` **先判可行性再 FAIL**，`FAIL` 后下一 tick 同坐标生成，`/redtale spawn replace on|off`。
3. `/redtale level info` 补当前玩家等级（R45 漏项）。
