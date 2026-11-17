package com.redtale.project.items;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.redtale.project.core.config.ConfigFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 武器动作生成器（R6-1，D33 放开命名空间限制）：把内容里引用的定义转发成运行期数据包。
 *
 * <p>为什么需要它：BetterCombat 的 {@code weapon_attributes} 只认数据包里的
 * {@code data/<ns>/weapon_attributes/<path>.json}，而我们的装备定义在 JSON5 配置里、
 * 不可能在 jar 内预知。所以 reload 时按引用把定义抄进
 * {@code <world>/datapacks/redtale_project/}。
 *
 * <p><b>命名空间规则（2026-11-16 改，Aki：「什么东西都要可改」）</b>：不再限制只处理自家 id。
 * 配置文件名决定落到哪个命名空间：
 * <ul>
 *   <li>{@code weapon_attributes/foo.json5} → {@code data/redtale_project/weapon_attributes/foo.json}</li>
 *   <li>{@code weapon_attributes/bettercombat__claymore.json5} → {@code data/bettercombat/weapon_attributes/claymore.json}
 *       （双下划线分隔，因为 {@code :} 在 Windows 文件名里非法）</li>
 * </ul>
 * 覆盖外部命名空间时打一条 WARN —— 数据包优先级高于 mod 内置资源，同名覆盖会改掉**别人整合包里
 * 所有引用该动作的物品**。这是有意的能力，但必须留痕。
 *
 * <p>另两条边界不变：<b>不校验 schema</b>（原样转发，只保证路径对 + JSON 合法）；
 * <b>不抛异常</b>（任何失败记 {@code warnings} 一行并跳过，reload 不能因为一个动作定义停摆）。
 */
public final class WeaponAttributesGenerator {

    /** 裸文件名（不带 {@code ns__}）落到这个命名空间。 */
    public static final String NAMESPACE = "redtale_project";
    /** 配置侧目录（相对 {@code config/redtale_project/}）。 */
    public static final String CONFIG_DIR = "weapon_attributes/";
    /** 数据包内子目录名。 */
    public static final String DATA_DIR = "weapon_attributes/";
    /** 文件名里的命名空间分隔符（{@code :} 在 Windows 文件名里非法，用双下划线）。 */
    public static final String NS_SEP = "__";

    private static final String CONFIG_SUFFIX = ".json5";
    private static final String PACK_MCMETA =
            "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"redtale generated\"\n  }\n}\n";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private WeaponAttributesGenerator() {
    }

    /**
     * 生成全部被引用的武器动作文件。
     *
     * @param datapackRoot 数据包根（{@code <world>/datapacks/redtale_project}）
     * @param configs      reload 时的全量配置列表，内部只筛 {@code weapon_attributes/} 前缀
     * @param contents     已载入的内容定义
     * @param warnings     非致命告警出口
     * @return 实际写出的文件数；没有任何引用或全部失败时为 0
     */
    public static int generateAll(Path datapackRoot, List<ConfigFile> configs,
                                  Collection<ItemContent> contents, List<String> warnings) {
        if (datapackRoot == null) {
            warnings.add("weapon_attributes：数据包根目录为空，本次不生成");
            return 0;
        }
        Map<String, ConfigFile> defs = indexDefinitions(configs);

        // 先算出「要生成哪些」：同一个 id 被多件内容引用时只生成一次
        Map<String, ConfigFile> todo = new LinkedHashMap<>();
        for (ItemContent content : contents) {
            String ref = normalizeRef(content.weaponAttributes());
            if (ref.isEmpty()) {
                continue;
            }
            String ns = namespaceOf(ref);
            String path = pathOf(ref);
            if (!isValidNamespace(ns) || !isValidPath(path)) {
                warnings.add(content.source() + "：" + content.id() + " 的 weapon_attributes \""
                        + ref + "\" 不是合法 id，已跳过");
                continue;
            }
            ConfigFile def = defs.get(ref);
            if (def == null) {
                // 外部命名空间没有配置定义是常态（用它 jar 里的内置项），不告警
                if (!ns.equals(NAMESPACE)) {
                    continue;
                }
                warnings.add(content.source() + "：" + content.id() + " 引用的 " + ref
                        + " 在 config/redtale_project/" + CONFIG_DIR + path + CONFIG_SUFFIX
                        + " 找不到定义，已跳过生成");
                continue;
            }
            if (!ns.equals(NAMESPACE)) {
                warnings.add("weapon_attributes：将覆盖外部命名空间的定义 " + ref
                        + "（来源 " + def.path() + "）—— 所有引用该动作的物品都会受影响");
            }
            todo.putIfAbsent(ref, def);
        }
        if (todo.isEmpty()) {
            return 0;
        }

        try {
            Files.createDirectories(datapackRoot);
        } catch (IOException e) {
            warnings.add("weapon_attributes：创建数据包目录失败（" + e.getMessage() + "），本次不生成");
            return 0;
        }
        ensurePackMeta(datapackRoot, warnings);

        int written = 0;
        for (Map.Entry<String, ConfigFile> entry : todo.entrySet()) {
            String ref = entry.getKey();
            Path out = datapackRoot.resolve("data")
                    .resolve(namespaceOf(ref))
                    .resolve(DATA_DIR)
                    .resolve(pathOf(ref) + ".json");
            try {
                Path parent = out.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                // R39：改了才写（幂等），理由同 EnchantmentGenerator
                if (com.redtale.project.core.datapack.TagJson.writeIfChanged(
                        out, GSON.toJson(entry.getValue().root()) + "\n", "weapon_attributes", warnings)) {
                    written++;
                }
            } catch (IOException e) {
                warnings.add("weapon_attributes：写入 " + out + " 失败（" + e.getMessage() + "），已跳过");
            }
        }
        return written;
    }

    /**
     * 内容侧引用归一化成完整 id：无冒号按 {@code redtale_project:} 补全，去空白。
     * 内容作者写 {@code test_sword} 和写 {@code redtale_project:test_sword} 应当等价。
     */
    public static String normalizeRef(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        return s.indexOf(':') >= 0 ? s : NAMESPACE + ":" + s;
    }

    private static String namespaceOf(String ref) {
        int i = ref.indexOf(':');
        return i < 0 ? NAMESPACE : ref.substring(0, i);
    }

    private static String pathOf(String ref) {
        int i = ref.indexOf(':');
        return i < 0 ? ref : ref.substring(i + 1);
    }

    /** 与 {@code ResourceLocation} 的命名空间字符集一致。 */
    private static boolean isValidNamespace(String ns) {
        return !ns.isEmpty() && ns.matches("[a-z0-9_.-]+");
    }

    /** 允许子目录（{@code /}），但禁 {@code ..}、禁绝对路径、禁大写。 */
    private static boolean isValidPath(String path) {
        return !path.isEmpty() && !path.contains("..") && !path.startsWith("/")
                && path.matches("[a-z0-9_./-]+");
    }

    /**
     * {@code weapon_attributes/<name>.json5} → 完整 id。
     * {@code foo} → {@code redtale_project:foo}；{@code bettercombat__claymore} → {@code bettercombat:claymore}。
     */
    private static Map<String, ConfigFile> indexDefinitions(List<ConfigFile> configs) {
        Map<String, ConfigFile> defs = new LinkedHashMap<>();
        if (configs == null) {
            return defs;
        }
        for (ConfigFile file : configs) {
            String path = file.path();
            if (path == null || !path.startsWith(CONFIG_DIR) || !path.endsWith(CONFIG_SUFFIX)) {
                continue;
            }
            String key = path.substring(CONFIG_DIR.length(), path.length() - CONFIG_SUFFIX.length());
            if (key.isEmpty() || key.contains("..")) {
                continue;
            }
            int sep = key.indexOf(NS_SEP);
            String id = sep > 0
                    ? key.substring(0, sep) + ":" + key.substring(sep + NS_SEP.length())
                    : NAMESPACE + ":" + key;
            if (!isValidNamespace(namespaceOf(id)) || !isValidPath(pathOf(id))) {
                continue;
            }
            defs.putIfAbsent(id, file);
        }
        return defs;
    }

    /** 没有 pack.mcmeta 的目录 MC 不认成数据包；已存在则不覆盖。 */
    private static void ensurePackMeta(Path datapackRoot, List<String> warnings) {
        Path meta = datapackRoot.resolve("pack.mcmeta");
        if (Files.exists(meta)) {
            return;
        }
        try {
            Files.writeString(meta, PACK_MCMETA, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("weapon_attributes：写 pack.mcmeta 失败（" + e.getMessage() + "）");
        }
    }
}
