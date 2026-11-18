# R60 · 验收 T-0055 + `check_deps.py`（依赖面 / 命名面）+ 派 T-0056

日期：2026-11-19 05:21–05:5x · 里程碑：M7（工程面收口：发布面安全网）

本轮无 Java 改动（`src/` 零 diff）→ **未构建、未启测试服**；正式服/测试服均未动。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0055 `tools/check_lang.py` | 验收 | `[DONE]` → **`[VERIFIED]`**（3/3 + 独立探针 23/23） |
| T-0056 `tools/check_attrs.py` | 派单 | `[OPEN]`（在制品 1/3） |

T-0055 三条验收：

1. `python tools/check_lang.py` → `EXIT=0`，末行逐字 `共 2 个文件 · 0 条问题`
2. 自编夹具（真 lang 副本 + 六处改动）→ `EXIT=1`，末行 `共 2 个文件 · 6 条问题`：两侧缺 `attribute.redtale_project.air_damage`（2 条）、`只在 zh_cn 存在：item.redtale_project.axe`、`zh_cn 空值或非字符串：item.redtale_project.amulet`（空串）、`en_us 空值或非字符串：item.redtale_project.boots`（数字 `7`）、`enchantment.redtale_project.soul_reap 占位符两侧不一致`；孤儿键 `item.redtale_project.ghost` 两侧各 1 条 `提示`，**不计入问题数**（断言末行仍是 6）
3. `wc`=122 ≤200 / `grep -c "def clean_json5"`=0 / `gen_attr_lang.py --check` 独立跑 `EXIT=0`（`93 键，自有 67`）

S7 独立探针 `build/probe/S7LangProbe.py` **23/23**（不复用 S8 自测断言）：

| 组 | 覆盖 |
|---|---|
| A | 未改动副本 → `EXIT=0` + 零 `提示` |
| B | 五类负向 + 反向对称（不应出现 `只在 en_us`）+ 孤儿键两侧提示 + **类序**（attribute 问题排在 item 之前） |
| C | 坏 JSON（另一份完整 → 只报 1 条，不误报缺键）、顶层是数组、只在 `en_us` 存在、纯空白值 |
| D | `enchantments/broken.json5` 解析失败 → 计问题数 |
| E | 真仓库回归仍 `0 条问题` |

**连带证据**：`tools/check_all.py` 末行从 R59 的 `共 9 个脚本 · 跑成 8 · 跳过 1 · 红 0` 变 `共 9 个脚本 · 跑成 9 · 跳过 0 · 红 0` —— PLAN §13.62 写的「过验收后跳过数应归零」当场兑现。

## 2. 自研：`tools/check_deps.py`（依赖面与命名面）

### 为什么做

编译器只看「本机能不能解析符号」，看不到三件事：

1. **越界 import** —— 我们直吃 `E:/server-test/libraries` 当 compileOnly。构建机上恰好有 GeckoLib 的 jar，`import software.bernie...` 就编得过；换台机器或发给玩家就 `NoClassDefFoundError`。D11/D12 说「零 mod 依赖」，但没有东西在守。
2. **`neoforge.mods.toml` 的 `[[dependencies]]`** —— 多写一条 `required`，编译期零反应，运行期 NeoForge 直接拒绝加载。这是「本地绿、上线红」的典型。
3. **D6 禁用专名** —— 专名漏进 `lang`/`json5` 会变成玩家可见文案，且改名成本越晚越高（PLAN §2）。

### 检查项（4 份来源）

| 来源 | 判据 | 红文案 |
|---|---|---|
| `META-INF/neoforge.mods.toml` | `modId = "x"` 只允许 `neoforge` / `minecraft` / D11 白名单 9 个；`modId = "${mod_id}"` 占位必须在 | `越界前置：<id>` / `缺 modId = "${mod_id}" 占位` / 实体依赖 <2 条 |
| `gradle.properties` | `mod_id == redtale_project` | `mod_id 被改名：<x>` |
| `build.gradle` | 禁 `implementation|api`（运行时依赖）；禁 `moddevgradle` / `neoforged.moddev` / `neoform-runtime` | `出现运行时依赖：<行>` / `引用 ModDevGradle / neoform-runtime` |
| `src/main/java/**`（133 个） | import 白名单 9 个前缀：`java.` `javax.` `net.minecraft.` `net.neoforged.` `com.mojang.` `com.google.` `com.redtale.project.` `org.slf4j.` `org.spongepowered.` | `<文件>:<行> 越界 import：<包>` |
| `src/main/resources/**`（39 个，跳过 `META-INF`） | D6 六个专名，大小写不敏感 | `<文件> 出现上游专名「<名字>」` |

末行 `共 4 份来源 · M 条问题`；M>0 → 1。基线：`java 133 个 · 资源 39 个` / `共 4 份来源 · 0 条问题`。

### 两条口径落在这里

- **R60-b**：D11 白名单只约束「能写什么」，不要求「必须写什么」。现在 `mods.toml` 只有 neoforge + minecraft，这是最好的状态；写成「必须有 GeckoLib」反而增加装机失败面。所以白名单是**允许集**，不是**必填集**。
- **R60-c**：D6 专名只扫资源，不扫 java 注释。实测 java 里有 2 处溯源注释（`DamageLayer.java:199`「上游 MMOItems 的观感」、`StatValue.java:6`「上游 MMOItems 的物品数值模型」）——禁到注释层等于逼人删历史依据。

### 探针 `build/probe/S7DepsProbe.py` 14/14

| 组 | 覆盖 |
|---|---|
| A | 干净夹具 `EXIT=0` + 计数行逐字 |
| B | 五类红路径各 1 条，末行 `共 4 份来源 · 5 条问题`（`spell_power` required / `mod_id` 改名 / `implementation` / `import software.bernie.geckolib...` / json5 里 `MMOItems`） |
| C | `id 'net.neoforged.moddev'` 判红；`geckolib optional` **放行**；缺 `mods.toml` 判红；注释里的 `// import com.example.Banned;` **不算**（只认行首 `import`） |
| D | 真仓库回归 `0 条问题` |

### 接进总入口

`check_all.py` 的 `TOOLS` **只加不改**（R60-d）：`check_deps.py` 追加在 `check_lang.py` 之后（发布面属最后一类）。末行：
`共 10 个脚本 · 跑成 10 · 跳过 0 · 红 0`。

## 3. 派单 T-0056 · `tools/check_attrs.py`

为什么轮到它：属性是**唯一不能热加**的注册表（D17），`AttributeSpecs.ALL` 与 `docs/ATTRIBUTE-REGISTRY.md` §2 必须逐条对齐，但今天只靠手。`EXPECTED_COUNT = 80` 只拦「少写一条」，拦不住：

- id 拼错（文档 `physical_damage` vs 代码 `physical_damge`）
- 文档删了一行没改代码（条数变了但 `EXPECTED_COUNT` 没动 → 启动照过）
- 同一条注册两次（`BY_ID` 后者覆盖前者，静默）
- 顺序错位（`/redtale attr list` 与文档分组不一致）

已实测基线可用：文档 80 行 / 代码 80 条 / **顺序逐位相同** / 原版 14 两侧一致 —— 所以卡里可以把「顺序不一致」写成硬检查而不会一上来就红。

卡里写死了三件事：解析正则（含 **F 段标题「预留 16 条（65~80…）」的数字不在全角括号里**这个坑）、九条检查项文案、5 类负向夹具。接 `check_all` 留给 S7 在 VERIFIED 之后做（同 T-0055 的模式）。

## 4. 未验 / 卡点

- **真人一次性验收窗口**仍是 M6/M7 唯一出口阻塞（自然刷怪替换、`per_tick_cap` 行为、升级/`take`·`untake`、`tree info`/`tree skills` 玩家段、武器 tooltip D20、`#minecraft:undead` 条件乘区）。不排期，等人。
- `respec` 扣费挂 **Q-021**；M5 原语范围挂 **Q-022**（默认「按需补」）——两条都等人。
- 本轮 `check_deps.py` 的 import 白名单是**静态文本匹配**，不做字节码分析：间接依赖（A 类 import B 类、B 类越界）不覆盖。真要抓得住得扫常量池，成本不符合当轮粒度，记为已知边界。
