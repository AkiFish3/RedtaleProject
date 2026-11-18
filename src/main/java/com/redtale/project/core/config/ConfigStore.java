package com.redtale.project.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.script.ConditionType;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptRegistries;
import com.redtale.project.core.script.TriggerType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 配置目录管理：{@code config/redtale_project/} 下所有 *.json5 全量载入。
 *
 * <p>约定：
 * <ul>
 *   <li>目录结构即语义：{@code scripts/} 是脚本，以后 {@code items/} {@code sets/}
 *       {@code enchants/} {@code mobs/} 同构。</li>
 *   <li>文件名以下划线开头的跳过（临时停用不删文件）。</li>
 *   <li>README.md 每次 reload 由注册表生成，手改会被覆盖。</li>
 * </ul>
 */
public final class ConfigStore {

    public static final String DIR_NAME = "redtale_project";

    /** 首次运行要落地的默认配置（classpath: /redtale/defaults/...）。 */
    private static final List<String> DEFAULT_FILES = List.of(
            "scripts/greeting.json5",
            "attack_speed.json5",
            "damage.json5",
            "crit.json5",
            "items/demo_longsword.json5",
            "items/demo_chestplate.json5",
            "items/demo_robe.json5",
            // D23 消耗品验收件：右键生效（非护甲基底 amulet）
            "items/demo_stew.json5",
            // D23 第二半验收件：磨刀石（repair 段，修另一只手那件）
            "items/demo_whetstone.json5",
            // 词条（D22，R11）：没有这几份默认文件，装备就永远 roll 不出词条
            // M3 宝石验收件：没有它们，demo_longsword 的孔位永远是空的（只能靠管理员手写）
            "items/gem_ruby.json5",
            "items/gem_sapphire.json5",
            "affixes/sharp.json5",
            "affixes/heavy.json5",
            "affixes/of_the_bear.json5",
            "affix_pools/sword_common.json5",
            // 套装（M3）：没有这份默认文件，demo_longsword / demo_chestplate / demo_robe
            // 的 set_id 就指向一个不存在的定义（静默不生效）
            "sets/emberforged.json5",
            // 附魔（M4 / D32）：L1 schema 的两份示范——一份纯数值、一份脚本化。
            // 没有它们，生成的 L0 数据包目录是空的，V3 之后全部无从验起
            "enchantments/super_sharpness.json5",
            "enchantments/soul_reap.json5",
            // 附魔脚本化的执行体（M4）：soul_reap 的 script id 是 enchants/soul_reap，
            // 没有这份文件 post_attack 会走「脚本不存在」分支（只 WARN 一次）
            "scripts/enchants_soul_reap.json5",
            // 技能（M5-A）：两份示范 = 一条物理单体 + 一条法术单体。
            // 没有它们 skills/ 目录是空的，K1（只写 JSON5 就能看见）无从验起
            "skills/gale_slash.json5",
            "skills/ember_burst.json5",
            // 法力（M5 收口 R35）：一份带 mana_cost 的示范 —— 没有它，法力链路只有代码没有内容，
            // 「蓝不够就放不出」这条永远没人踩到
            "skills/arc_bolt.json5",
            // 怪物（M6，R36）：两份示范 = 带装备的近战精英 + 双技能的施法者。
            // 没有它们 mobs/ 目录是空的，MobRegistry 恒为 0 只，M6 后面几步全都无从验起
            "mobs/ember_guard.json5",
            "mobs/ash_caster.json5",
            // 掉落表（M6 第四层，R41）：没有这份默认文件，ember_guard 的 drops 引用就悬空，
            // 死掉零产出 —— 「掉落接线通没通」这条验收永远没有内容可验
            "drops/ember_guard.json5",
            // 等级 / 职业（M7，R44）：没有这两份默认文件，levels/ 与 jobs/ 恒为空，
            // ProgressRegistry 恒 0 条，K1（只写 JSON5 就能加内容）无从验起。
            // progression.json5 是经验面唯一的全局旋钮（击杀倍率），缺了它 mob_exp_multiplier 恒走缺省
            "levels/main.json5",
            "jobs/miner.json5",
            "progression.json5"
    );

    private final Path root;

    public ConfigStore(Path configDir) {
        this.root = configDir.resolve(DIR_NAME);
    }

    public Path root() {
        return root;
    }

    // ------------------------------------------------------------ 默认配置

    public void ensureDefaults() throws IOException {
        Files.createDirectories(root);
        for (String name : DEFAULT_FILES) {
            Path target = root.resolve(name);
            if (Files.exists(target)) {
                continue;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = ConfigStore.class.getResourceAsStream("/redtale/defaults/" + name)) {
                if (in == null) {
                    continue;
                }
                Files.writeString(target, new String(in.readAllBytes(), StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8);
            }
        }
    }

    // ------------------------------------------------------------ 载入

    /**
     * 全量读取并解析。任何一份文件语法错误都会中断并抛异常 —— 让 reload 明确失败，
     * 而不是带着半套配置继续跑。
     */
    public List<ConfigFile> loadAll() throws IOException {
        List<ConfigFile> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json5"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        for (Path path : paths) {
            String rel = root.relativize(path).toString().replace('\\', '/');
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement parsed;
            try {
                parsed = Json5.parse(text);
            } catch (Json5Exception e) {
                throw new ConfigException(rel + " 语法错误：" + e.getMessage(), e);
            }
            if (!parsed.isJsonObject()) {
                throw new ConfigException(rel + " 顶层必须是对象");
            }
            out.add(new ConfigFile(rel, parsed.getAsJsonObject()));
        }
        return out;
    }

    public static List<ConfigFile> ofDirectory(List<ConfigFile> all, String dir) {
        String prefix = dir.endsWith("/") ? dir : dir + "/";
        return all.stream().filter(f -> f.path().startsWith(prefix)).toList();
    }

    // ------------------------------------------------------------ README

    /** 用注册表生成同目录 README.md（schema 说明），每次 reload 覆写。 */
    public void writeReadme(ScriptRegistries registries) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# redtale_project 配置说明\n\n");
        sb.append("> 本文件由模组在每次 `/redtale reload` 时生成，手改会被覆盖。\n\n");
        sb.append("## 目录结构\n\n");
        sb.append("| 路径 | 内容 | 可否热重载 |\n|---|---|---|\n");
        sb.append("| `scripts/*.json5` | 脚本：trigger + conditions + effects | 可以 |\n");
        sb.append("| `items/*.json5` | 装备内容：基底 + 属性数值 + 耐久 + 武器动作（白板化 D21） | 可以 |\n");
        sb.append("| `attack_speed.json5` | 攻速来源表（急迫每级、效果映射、饱和点、tick 地板） | 可以 |\n");
        sb.append("| `damage.json5` | 伤害闸门 + 异常保护 + L2 乘区白名单（默认全关 / 只乘 `redtale_project:*`） | 可以 |\n");
        sb.append("| 其它子目录 | 按里程碑逐步加入（items / sets / enchants / mobs / progression） | 可以 |\n\n");
        sb.append("文件名以 `_` 开头的文件会被跳过。语法是 JSON5：允许注释、单引号、未加引号的键、尾逗号。\n\n");

        sb.append("## 脚本文件格式\n\n");
        sb.append("```json5\n{\n");
        sb.append("  // 方式一：一个文件一条脚本\n");
        sb.append("  id: 'greeting',\n");
        sb.append("  trigger: 'player_join',\n");
        sb.append("  cooldown_ticks: 0,\n");
        sb.append("  conditions: [ { type: 'always' } ],\n");
        sb.append("  effects: [ { type: 'message', args: { text: '你好 %player%' } } ],\n");
        sb.append("}\n```\n\n");
        sb.append("也可以写成 `{ scripts: [ {...}, {...} ] }` 把一个文件装多条。\n\n");

        sb.append("## trigger 原语\n\n| id | 说明 | 需要玩家 |\n|---|---|---|\n");
        for (TriggerType t : registries.triggers()) {
            sb.append("| `").append(t.id()).append("` | ").append(t.description())
                    .append(" | ").append(t.requiresPlayer() ? "是" : "否").append(" |\n");
        }

        sb.append("\n## effect 原语\n\n| id | 参数 |\n|---|---|\n");
        for (EffectType e : registries.effects()) {
            sb.append("| `").append(e.id()).append("` | ").append(e.description()).append(" |\n");
        }

        sb.append("\n## condition 原语\n\n| id | 参数 |\n|---|---|\n");
        for (ConditionType c : registries.conditions()) {
            sb.append("| `").append(c.id()).append("` | ").append(c.description()).append(" |\n");
        }

        sb.append("\n## 命令\n\n");
        sb.append("| 命令 | 作用 |\n|---|---|\n");
        sb.append("| `/redtale reload` | 重读全部 JSON5，校验通过才生效 |\n");
        sb.append("| `/redtale status` | 当前载入的脚本数、最近一次错误 |\n");
        sb.append("| `/redtale list` | 列出已载入脚本（id / trigger / 来源文件） |\n");
        sb.append("| `/redtale fire <id>` | 手动跑一条脚本，用于冒烟测试 |\n");
        sb.append("| `/redtale attr <属性> [目标]` | 属性的来源拆解（D18 验收项） |\n");
        sb.append("| `/redtale speed` | 攻速规则 + mixin 注入状态 + 样例换算 |\n");
        sb.append("| `/redtale damage` | 伤害乘区规则 + 最近 8 次真实结算（谁被乘、乘了多少） |\n");
        sb.append("| `/redtale damage probe` | 用 `settle()` 演算给定进场值/百分比 |\n");
        sb.append("| `/redtale damage check <类型>` | 查一个伤害类型会不会被我们乘 |\n");
        sb.append("| `/redtale items` | 列出已载入的装备内容（基底 / 属性数 / 耐久） |\n");
        sb.append("| `/redtale give <内容id> [数量]` | 按内容 id 发放物品（身份在组件里，不在物品 id 里） |\n");
        sb.append("| `/redtale tooltip [内容id]` | 打印一件物品的 D20 数值行（服务端可验，不用客户端） |\n");

        Files.writeString(root.resolve("README.md"), sb.toString(), StandardCharsets.UTF_8);
    }

    /** 配置层异常，带上出错文件名。 */
    public static final class ConfigException extends RuntimeException {

        public ConfigException(String message) {
            super(message);
        }

        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 未使用但保留：把 JsonObject 转成紧凑字符串，日志排查用。 */
    static String compact(JsonObject obj) {
        return obj.toString();
    }
}
