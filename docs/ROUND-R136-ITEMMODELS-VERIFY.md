# R136 · 2026-11-27 06:1x~06:5x · 验 T-0129（资源面首尺）+ check_all 71→72 + 派 T-0131（护甲三件套内容）

## 1. 卡板：T-0129 `[DONE]` → `[VERIFIED]`

`tools/check_item_models.py`（S8 03:17 交付，127 行）三条验收逐字复跑，**S8 零偏差，不打回**。

| 验收 | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `python check_item_models.py` | `共 14 份基底物品模型 · 0 条问题` EXIT=0 | 逐字对 |
| 1 | `python check_item_models.py -v` | `已检查 14 个基底 · 14 份模型 · 14 处 layer0 · 原版贴图 605 张` | 逐字对 |
| 2 | 六夹具 A~F（`build/modelcheck_s7/` 干净副本 + `--root`） | 各 1 条、文案逐字、EXIT=1 | 6/6 逐字对 |
| 3 | `wc -l` / `py_compile` / `--jar NO_SUCH.jar` / `--root NO_SUCH` / 禁词 grep | 127 / 无输出 / 1 条 / 2 条 / 0 | 全对 |

六夹具逐字输出（S7 独立复跑，不是照抄 S8 的结论）：

```
A 删 ring.json        → 基底 'ring' 缺少模型 models/item/ring.json                        EXIT=1
B 加 zz.json          → 模型 zz.json 没有对应基底（多余文件）                              EXIT=1
C sword.parent        → sword.json parent 应为 minecraft:item/generated，实际 'minecraft:item/handheld'  EXIT=1
D axe.textures        → axe.json textures 应恰有一个键 layer0，实际 ['layer0', 'layer1']    EXIT=1
E bow.layer0 换命名空间 → bow.json layer0 命名空间应为 minecraft，实际 'redtale_project'      EXIT=1
F hammer.layer0 死引用 → hammer.json layer0 'minecraft:item/nonexistent' 在原版贴图里不存在  EXIT=1
```

读失败形态：`--jar NO_SUCH.jar` → 恰 1 条 `原版 jar 读取失败：NO_SUCH.jar` EXIT=1；`--root NO_SUCH` → 恰 2 条（基底源码 + 模型目录）EXIT=1，均不抛栈。夹具副本已删。

**接线**：`check_all` **71→72**（`共 72 个脚本 · 跑成 72 · 跳过 0 · 红 0`）。这是 `assets/` 面第一次进全量门禁。

## 2. 派 T-0131（内容面：护甲三件套 + `ashen_vow`）

**为什么是内容卡而不是又一把尺**：本轮先按 R129-a「静默放行」继续扫尺，三条候选**逐条证伪**（记录在案，避免下轮重挖）：

| 候选 | 取证 | 结论 |
|---|---|---|
| `en_us` ↔ `zh_cn` 键对称 | `check_lang.py:95-114` 已核「缺键 / 只在一边 / 占位符两侧不一致」；两份各 93 键、差集均空 | 已覆盖，封 |
| 技能树 `unlock_skill` 引用 | `check_trees.py:97-101` 已报 `skill 未命中` | 已覆盖，封 |
| 词条曲线 `per_item_level` 非数字 | `AffixPools.number()` 自述「不是数字就告警并用 0」 | 有 WARN，按 R133-d 不算洞 |

转内容面取证：`items/` 9 份覆盖 **5 个基底**（`chestplate`×2 / `amulet`×4 / `dagger` / `sword` / `hammer`），**`helmet` / `leggings` / `boots` 零内容**；套装只有 `emberforged`（2 件、2 档，两件挤在 CHEST + MAINHAND），**三档套装从未被任何验收证明可达**。

`ashen_vow` = HEAD/LEGS/FEET 三槽三件，`count 1/2/3` 三档，一次补齐三个基底 + 三档可达性。纯 JSON5 + `ConfigStore` 恰 +4 行（D17：不换 jar）。

派前用**整份 `defaults/` 副本**（`build/setprep/`，`--dir` 指向）预验，避免 R125-a「新默认文件没登记清单 → `check_defaults` 必红」的坑：

```
check_items         --dir 副本  → 共 18 个文件 · 0 条问题            EXIT=0
check_set_reach -v  --dir 副本  → 2 份套装 · 5 件引用 · 5 档 · 5 槽  EXIT=0
check_numeric       --dir 副本  → 共 4 个文件 · 0 条问题             EXIT=0
check_affix_reach   --dir 副本  → 0 条问题                          EXIT=0
check_name_coercions--dir 副本  → 0 条问题                          EXIT=0
```

首版把 `rev` 写成 `0` 被 `check_items` 判 `rev 必须是 >=1 的整数` —— **卡面已改成 `rev: 1` 并写进「注意」**（预验的价值就在这：卡面缺陷不落到 S8 头上）。副本已删。

在制品 **2/3**（T-0130 + T-0131）。

## 3. 台账纠偏：M6 代码面**已 100% 落地**（不是「剩模板实体注册 / 刷怪替换」）

R128~R135 的「下一步」里我一直写「M6 代码面剩模板实体注册 / 刷怪替换（S7 独占）」。本轮核实，**这句是过期表述**：

| 项 | 证据 |
|---|---|
| 8 个 `EntityType` 注册 | `mobs/RedtaleEntities.java:40-52`（`DeferredRegister<EntityType<?>>`，id 与 `MobSpec.BASES` 一一对应）+ `RedtaleProject.java:34` `register(modBus)` |
| 每个类型的属性表 | `RedtaleEntities.onAttributes` + `RedtaleProject.java:35` `modBus.addListener(RedtaleEntities::onAttributes)`（**别再被「grep 不到」骗第 3 次**：方法引用不是 `@SubscribeEvent`，按 `RedtaleEntities.attributes` 搜是搜不到的） |
| 自然刷怪替换 | `mobs/SpawnReplacer.java`（挂 `MobSpawnEvent.PositionCheck`）+ `GameHooks.java:52` `install()` + `:126` `tick(server)` |

M6 的验收口径（`PLAN.md:142`「配置出来的怪能放技能并掉落；野外原版刷出的怪按概率被替换」）在代码与内容两面都已闭合；**剩下的只有真机那一半**（自然刷怪替换需要真人在线才跑自然刷怪，R48-e）。M6 不再有「S7 独占的代码活」，PLAN 的下一步表述本轮改口径。

## 4. 环境与门禁

- 七门禁全 0：`check_all` 72/72 红 0、`check_tasks` 131 卡 · 在制品 2 · 问题 0、`check_contracts`、`check_docs` 151 份 / 622 引用、`check_rounds`、`check_ownership`、`check_checkall`。
- `src/` diff：0 行（本轮没改 Java / 资源；`check_item_models.py` 已在 T-0129 交付时落盘）。
- 未构建、未取构建锁、未启停测试服、正式服未触碰（25565 无监听，仍等 Aki 手动 `E:/server/run.bat`）。
- 测试客户端：本轮尝试复跑以销**欠账 D-01**（见 §5）。

## 5. 欠账 D-01 复跑（客户端资源面）

桌面侧条件与 R135 不同：`quser` 显示 `rdp-tcp#1` 会话 `运行中`、`root\wmi` 报 1 个显示器、内存 12.81 GB 空闲、无 java 进程（正式服仍停）。用 `E:/client/rt-test/launch.py ""`（空参关掉 `--quickPlayMultiplayer`）跑，`-Xmx3G`，日志 `client_r136.log`。

**结果：D-01 销账（两项判据均 0）**。

```
client_r136.log（57 行，06:20:44 起，06:20:52 到图集完成）
Setting user: AkiTest
NeoForge mod loading, version 21.1.257, for MC 1.21.1
[RedtaleProject/Attr] 属性注册完成：92 个实体类型 → 66 条属性镜像（实体属性 6072 条）
Reloading ResourceManager: vanilla, mod_resources, mod/redtale_project, mod/neoforge
TextureAtlas × 14（blocks / signs / shield_patterns / … / gui）
Loaded 0 entity animations（GeckoLib 无动画 = 预期，M6 模型尚未做）

grep -c "Unable to load model"                       → 0
grep -c "Missing textures in model redtale_project:" → 0
grep -c "FileNotFoundException.*redtale_project"     → 0
grep -c "ERROR"                                      → 0
```

R132 那 14 条 `Unable to load model`、R134 那条 `Missing textures … shield#inventory` 现在是 **0 / 0**，14 份 `models/item/*.json` 与 shield 的 `turtle_scute` 修正在**真实资源重载路径**上成立（不是只有离线 jar 表命中）。日志按 `logs/client_r136.log` 留档，进程按 **PID 16560**（命令行含 `-Xmx3G` 与 `E:\client\赤物语…`）关闭，`taskkill /IM` 未使用；关闭后无 java 残留。

**仍未做**：进服（要 GUI 点「多人游戏 → 直接连接」，本会话无 computer-use 工具；`--quickPlayMultiplayer` 两次实验在 R132 已被 MC 忽略，不再依赖）。M7 真机那条挂账不变。

## 6. 口径（§13.139 R136-a~e）

| 编号 | 口径 |
|---|---|
| R136-a | **尺子候选要先证伪再换方向**：本轮三个「静默放行」候选全部被旧尺/旧 WARN 覆盖，写进台账而不是硬派一张重复卡。证伪也是产出。 |
| R136-b | **内容卡派前必须整份副本预验，且要把 `rev >= 1` 这类硬约束写进卡面**：`rev: 0` 是 S7 首版写错，预验把它挡在派单之前。 |
| R136-c | **方法引用（`X::method`）式监听器搜不到就别急着判「没挂」**：M6 属性事件我 grep `RedtaleEntities.attributes` 零命中，实际在 `modBus.addListener(RedtaleEntities::onAttributes)`。查接线看**入口类**（`RedtaleProject` / `GameHooks`），别在实现类里搜。 |
| R136-d | **过期表述要在台账里改，不要每轮抄一遍**：「M6 代码面剩模板实体注册」抄了 8 轮，实际 R37/R48 就落地了。下一步清单必须带证据（类名 / 行号），带不出来就别写。 |
| R136-e | **裸名纪律第五十四次**：只写裸名，不写 `tools/` 全路径（`check_docs` 会按路径存在性判红）。 |

## 7. 下一步（R137）

1. **验 T-0131**（三件套 + `ashen_vow`）：`check_items` 18/0、`check_set_reach` 2 套/5 档、`check_defaults` 48·48·0；过后构建 + 测试服验 `set list` 2 套、`tooltip` 三件套、`set probe` 三档逐级生效（1→2→3 件）。
2. **验 T-0130**（`weapon_attributes` 外部引用尺），过后接线 **72→73**。
3. **M7 真机**：`rt-test` 客户端进服走「多人游戏 → 直接连接 → `127.0.0.1:25567`」；`quickPlayMultiplayer` 两次实验均被 MC 忽略，不再依赖。
4. Q-024（正式服事故口径）等 Aki；Q-021/022/023 无答复继续按默认走。
