package com.redtale.project.enchants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.items.ItemBase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 让 14 个基底物品「吃得上附魔」（R22-e，R23 落地）。
 *
 * <p>为什么必须有这一层：1.21.1 的 {@code supported_items} 是<b>数据驱动 item tag</b>，原版的
 * {@code #minecraft:enchantable/*} 里只有原版 id。我们的基底不在里面 → {@code /enchant} 直接报
 * {@code cannot support that enchantment}，连原版锋利都附不上（R22 实测）。
 *
 * <p><b>写法：追加，不替换</b>（R23 口径修订 R22-e）。在自家数据包里写
 * {@code data/minecraft/tags/item/enchantable/<名>.json}，只带 {@code "replace": false} 与本方块的 id，
 * 原版与其它 mod 的同名 tag 照常合并 —— 这正是 SimplySwords / Farmer's Delight 的既有做法
 * （本机 jar 内实测存在这些文件）。{@code replace: true} 才是「改别人的数据」，我们不用。
 *
 * <p>另附一件小事：{@code exclusive_set} 指向的自家 tag 若不存在，开局会刷
 * {@code Not all defined tags ... are present in data pack}。这里给缺失的自家互斥 tag 补一份
 * <b>空</b> tag（语义 = 暂无互斥），并记一条告警，免得「写错一个字母」静默失效。
 *
 * <p>映射跟着基底走（基底是代码，不是内容）：加第 15 种基底要换 jar，这张表跟着一起改，
 * 内容（{@code items/*.json5}）无论加多少件都自动落进对应 tag —— 因为内容栈的 item id 就是基底 id。
 */
public final class EnchantableTags {

    /** 原版 tag 的命名空间：我们只往这里<b>追加</b>值。 */
    private static final String VANILLA_NS = "minecraft";
    private static final String OWN_NS = "redtale_project";
    private static final String CONFIG_DIR = "enchantments/";
    private static final String CONFIG_SUFFIX = ".json5";

    private EnchantableTags() {
    }

    /**
     * 写出原版 enchantable tag 的追加文件，并补齐缺失的自家互斥 tag。
     *
     * @param datapackRoot 数据包根（{@code <world>/datapacks/redtale_project}）
     * @param configs      reload 时的全量配置，内部只筛 {@code enchantments/} 前缀（用于互斥 tag 补齐）
     * @param warnings     非致命告警出口（null 时丢弃）
     * @return 实际写出（新建或内容变化）的文件数
     */
    public static int generateAll(Path datapackRoot, List<ConfigFile> configs, List<String> warnings) {
        List<String> log = warnings == null ? new ArrayList<>() : warnings;
        if (datapackRoot == null) {
            log.add("enchantable：数据包根目录为空，本次不生成");
            return 0;
        }
        int written = 0;
        for (Map.Entry<String, List<String>> entry : vanillaTags().entrySet()) {
            Path out = datapackRoot.resolve("data").resolve(VANILLA_NS)
                    .resolve("tags").resolve("item").resolve("enchantable").resolve(entry.getKey() + ".json");
            if (writeTag(out, entry.getValue(), log)) {
                written++;
            }
        }
        written += ensureExclusiveTags(datapackRoot, configs, log);
        return written;
    }

    /** tag 名 → 该 tag 下本方块的 id（已排序，输出稳定）。 */
    static Map<String, List<String>> vanillaTags() {
        Map<String, TreeSet<String>> acc = new TreeMap<>();
        for (ItemBase base : ItemBase.values()) {
            String id = OWN_NS + ":" + base.id();
            for (String tag : tagsFor(base)) {
                acc.computeIfAbsent(tag, k -> new TreeSet<>()).add(id);
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, TreeSet<String>> e : acc.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return out;
    }

    /**
     * 一种基底进哪些原版 enchantable tag。
     *
     * <p>只挑**确实存在**且语义对得上的：{@code sharp_weapon} 是锋利/节肢杀手那批的入口，
     * {@code weapon} 是横扫/缴械那批，{@code armor} / {@code equippable} 是保护系的两个入口，
     * {@code durability} 是耐久/经验修补的入口（不可损坏的件进了也无害）。
     */
    static List<String> tagsFor(ItemBase base) {
        List<String> tags = new ArrayList<>();
        switch (base.id()) {
            case "sword" -> {
                tags.add("sharp_weapon");
                tags.add("weapon");
                tags.add("fire_aspect");
                tags.add("durability");
            }
            case "dagger", "axe", "hammer", "spear", "staff" -> {
                tags.add("sharp_weapon");
                tags.add("weapon");
                tags.add("durability");
            }
            case "bow" -> {
                tags.add("bow");
                tags.add("durability");
            }
            case "shield" -> tags.add("durability");
            case "helmet", "chestplate", "leggings", "boots" -> {
                tags.add("armor");
                tags.add("equippable");
                tags.add("durability");
            }
            default -> tags.add("durability"); // 饰品：只挂耐久系，不碰护甲/武器批
        }
        return tags;
    }

    /** 逐条扫 {@code exclusive_set}：指向自家命名空间但文件不存在 → 补一份空 tag。 */
    private static int ensureExclusiveTags(Path datapackRoot, List<ConfigFile> configs, List<String> log) {
        if (configs == null) {
            return 0;
        }
        int written = 0;
        for (ConfigFile file : configs) {
            if (file == null || file.path() == null || file.root() == null) {
                continue;
            }
            String path = file.path();
            if (!path.startsWith(CONFIG_DIR) || !path.endsWith(CONFIG_SUFFIX)) {
                continue;
            }
            JsonElement raw = file.root().get("exclusive_set");
            if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
                continue;
            }
            String ref = raw.getAsString().trim();
            if (!ref.startsWith("#")) {
                continue; // 单条附魔 id，不是 tag
            }
            String id = ref.substring(1);
            int colon = id.indexOf(':');
            if (colon < 0) {
                continue;
            }
            if (!OWN_NS.equals(id.substring(0, colon))) {
                continue; // 别人的 tag 不归我们造，缺了由对方负责
            }
            String tagPath = id.substring(colon + 1);
            if (tagPath.isEmpty() || tagPath.contains("..")) {
                continue;
            }
            Path out = datapackRoot.resolve("data").resolve(OWN_NS)
                    .resolve("tags").resolve("enchantment").resolve(tagPath + ".json");
            if (Files.exists(out)) {
                continue;
            }
            if (writeTag(out, List.of(), log)) {
                written++;
                log.add("enchantable：互斥 tag #" + id + " 原本不存在，已生成空 tag（values 为空 = 暂无互斥；要互斥请手填）");
            }
        }
        return written;
    }

    /**
     * 写一份 tag 文件；内容与磁盘上已有的一致就<b>不动</b>（幂等，避免每次 reload 都改 mtime
     * 触发整包重载）。
     *
     * <p>R39 起委托 {@link com.redtale.project.core.datapack.TagJson}：三处 tag 写手收口成一份，
     * 失败文案仍由各自的 {@code label} 区分（这就是 R27「不抽公共工具」的唯一理由，现在参数化了）。
     */
    private static boolean writeTag(Path out, List<String> values, List<String> log) {
        return com.redtale.project.core.datapack.TagJson.write(out, values, "enchantable", log);
    }
}
