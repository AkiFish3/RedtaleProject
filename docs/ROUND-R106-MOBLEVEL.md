# R106 · 验 T-0100 + 接线 `check_all` 48→49 + 派 T-0101（怪物 `level` 整数口径）

时间：2026-11-22 23:5x · 队列：S7·移植工程（兼 S8 派单/验收）

## 验收 T-0100（对账网第三十一例）

`check_use_args.py` `[DONE]` → **VERIFIED**：卡面 3/3 + S7 独立六夹具 6/6。

| 项 | 实测 |
|---|---|
| 基线 | `python check_use_args.py` → `共 1 份消耗品脚本参数键对账表 · 0 条问题` EXIT=0 |
| `-v` | `已检查 7 份物品 · 2 条带 args 的 use.script 调用 · 16 个原语` |
| 六夹具 | A~F EXIT=1/0/1/1/0/1；文案逐字（D 按 a/b 顺序 2 条、F 的 `-v` 1 物品/1 调用/16 原语） |
| 静态 | wc=163≤200、`def check_calls`=1、禁用 grep=0、`py_compile` OK |
| 边界 | `--items NO_SUCH` → EXIT=1、恰好 1 条 `[问题] items目录读取失败`、不抛栈 |

S7 探针写在独立目录 `build/s7verifyuseargs/`，与 S8 卡内独占 `build/useargsfix/` 不冲突；验后已清。S8 零偏差，不打回。接线 `check_all` **48→49**：`共 49 个脚本 · 跑成 49 · 跳过 0 · 红 0` EXIT=0。

## 派单 T-0101（对账网第三十二例）

四满足判据：

| 判据 | 证据 |
|---|---|
| 旧尺全绿 | `check_mobs.py:65-99` 的 `check_mob()` 只读 id/base/scale/attributes/equipment/skills/drops；全仓 49 把尺子 grep `level` 零命中。内存反事实 `level=0/1001/12.7/"12"/-5` 传 `check_mob()` 全部返回 `[]` |
| 运行期静默 | `MobSpec.java:58` `MAX_LEVEL=1000`、`:122-127` `level()` 仅非数字 WARN；负数/0/>1000/小数静默 `max(1,min(1000,floor(v)))`，零 WARN。值进 `MobSpawner.applyAttributes` 的 `base + per_item_level × level` → 血量/攻击静默算错 |
| 合法集可冻结 | `LEVEL_MIN=1` / `LEVEL_MAX=1000`、`isinstance(int) and not bool`，由 S7 从源码逐字冻结进卡面，S8 不读源码 |
| 基线非空 | `ember_guard.json5:12` = 12、`ash_caster.json5:11` = 8 → 2 怪 / 2 引用 / 0 问题（离线核逐字） |

卡面写死独占文件 `check_mob_levels.py`（单文件 ≤200 行）、5 个接口签名、3 条机器验收（基线+`-v` / 六夹具 / 静态+读失败）。六夹具覆盖越界上下、小数、字符串、bool 与合法边界。VERIFY 后由 S7 接线（49→50）。

同轮排除：`mobs[].scale`（旧尺下界比运行期宽，下一张候选）、`skill_trees.points_per_level`（非整数静默回落 1，下一张候选）、`levels/jobs curve.mode`（有 WARN）、`use.script[].args.target`（基线 0 引用）、`projectile.on_hit`（基线 0 + WARN）、附魔 `anvil_cost/weight/slots`（透传原版，无降级）、`skill_trees.respec`（只白名单不消费）、`mobs.skills.cooldown_ticks`（clamp 留池）及全部老留池项。

## 口径（PLAN §13.109 R106-a~e）

- a：**对账网第 32 例 = 怪物 `level` 的整数口径**——不是引用型也不是完整 id 型，第一次核「静默夹取整前的配置形态」。
- b：**旧尺口径过宽也是洞**——`check_mobs.py:72-73` 的 `0 < scale <= 4` 放行 `(0,0.25]`，运行期夹到 0.25；本轮先派 `level`，`scale` 留下一张（同属 `mobs/` 不合并，避免单卡跨两种语义）。
- c：**缺键合法不报**——`MobSpec.level()` 缺省 1，尺子只核显式写出的值；不得造「必须写 level」的红线。
- d：**冻结语义从 Java 反推，不照抄旧尺**——旧尺对非法值全绿，冻结表以 `MobSpec.java:58,122-127` 为准（R96-b 延续）。
- e：**裸名纪律第二十四次**——本轮在建脚本与台账只写裸名 `check_mob_levels.py`，避免文档检查把未建文件当断链。

## 环境

`src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服 9376 常驻未动，测试服 25567/25577 无 LISTEN。
