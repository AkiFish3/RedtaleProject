# R77 · 2026-11-20 06:2x · 验 T-0072（`check_primitives.py`）+ 接线 20→21 + 派 T-0073（生成器前缀对账）

## 本轮性质
无 Java 缺口。验收 + 接线 + 派单，全程不碰 `src/`，未取构建锁、未构建、未启停任何服。

## 1. 验收 T-0072 → VERIFIED
`tools/check_primitives.py`（S8 T-0072 交付，原语注册对账）。三条验收全过：

1. 基线 `python tools/check_primitives.py` → `共 1 份原语注册表 · 0 条问题` EXIT=0；`-v` → `已检查 … · 原语类 16 个（内联 2）· 注册引用 18 个`。与卡面基线逐字一致。
2. S7 独立夹具（复制到 `build/primfix/` 改）：A 删 `HealEffect` + `AlwaysCondition` 两处 register → 2 条 `未注册进 Builtins`（字典序 `AlwaysCondition`/`HealEffect`）；B 塞 `new GhostEffect()` → 1 条 `无对应原语类`；C 增 `WandererEffect.java` 不注册 → 1 条 `未注册进 Builtins`。均 EXIT=1。
3. `wc`=113≤200、`grep -c "def check_registration"`=1、禁用 API grep=0；行注释 `// registries.register(new FakeEffect())` 与块注释内假注册均不计入（`read_regs` 只剩 `RealEffect`）；`--builtin` 指向不存在目录 → `原语目录读取失败` + `注册表读取失败` 各 1 条、不抛栈、EXIT=1。夹具已清。

## 2. 接线 `check_all` 20→21
`tools/check_all.py` 的 `TOOLS` 末尾追加 `"check_primitives.py"`。跑 `python tools/check_all.py` → `共 21 个脚本 · 跑成 21 · 跳过 0 · 红 0`；接线后 `python tools/check_checkall.py -v` → `清单 21 项 · 磁盘 21 项 · 0 条问题`（R75-b 自排除按接线时刻失效，二次验证对）。

## 3. 派 T-0073（lang 生成器前缀对账）
元层洞第四例。前三例：T-0070 清单↔磁盘、T-0071 命令类↔挂载、T-0072 原语类↔注册；本例 T-0073 = 生成器文件名↔`OWNED_PREFIX`/`KEY_PREFIX` 声明。

- 为什么成立：R59-a 早定「生成器只拥有自己的前缀」，但只靠 `gen_*.py` 自保护 + `check_lang` 验产物键覆盖；没人验「声明本身写歪没写歪」。R59 事故（`gen_attr_lang.py` 前缀越界把 93 键削成 67）正是此洞——`check_lang` 只会报下游「缺键」，定位不到根因。
- 为什么可静态提取（S7 已核基线）：`gen_attr_lang.py` 用 `OWNED_PREFIX = "attribute.redtale_project."`，其余三个用 `KEY_PREFIX`；四个值逐一可读、剥注释正则可覆盖。映射不规则：`attr`→`attribute.`、`enchant`→`enchantment.`（插入了字母），`entity`/`item` 直拼——故期望表 `GEN_FILES` 逐项写死，禁止 S8 从文件名推断类别再拼。
- 卡面已写死接口（`read_prefix`/`read_all`/`check_prefixes`/`main`）、独占文件（仅 `check_gen_prefix.py` + `build/genfix/` 夹具）、禁止面（不碰 `check_all.py`、四个 gen 脚本、其它 check）、三条机器验收。在制品 1/3。

## 4. 口径
见 `PLAN.md` §13.80 R77-a~c：①元层洞第四例 = 生成器前缀声明；②期望表写死不推断；③「未声明」与「声明错误」两类不共存、不跨文件查重。

## 5. 环境
`src/` 零 diff、未构建、未启停任何服、无构建锁；`check_all` 21/21、`check_tasks` 73 卡在制品 1 问题 0、`check_docs` 92 份文档。Q-021/Q-022 无人答，真人窗口 A~G 等 Aki。
