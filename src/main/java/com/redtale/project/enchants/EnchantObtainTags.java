package com.redtale.project.enchants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 让生成的 L0 附魔「能被拿到」（M4 的 V4，T-0020 的落地物）。
 *
 * <p>为什么必须有这一层：附魔写进 {@code data/redtale_project/enchantment/} 只是「存在」，
 * 原版三条<b>可获取性 tag</b>不写 → 附魔台永远刷不出（V4 过不了）、村民不卖、战利品不掉。
 * 三条 tag 全是 {@code minecraft} 命名空间下的 {@code tags/enchantment}：
 *
 * <ul>
 *   <li>{@code in_enchanting_table} —— 附魔台可提供</li>
 *   <li>{@code tradeable} —— 村民交易 / 流浪商人可提供</li>
 *   <li>{@code on_random_loot} —— 战利品表随机附魔池</li>
 * </ul>
 *
 * <p><b>写法与 {@link EnchantableTags} 完全同构</b>：{@code "replace": false} 追加、
 * 文本相等不重写（幂等，避免每次 reload 改 mtime 触发整包重载）。
 *
 * <p>取值来源只有 {@code enchantments/*.json5} 的 {@code id} 字段 —— 不猜、不按文件名拼
 * 命名空间，因为「文件名 → id」的映射是 {@link EnchantmentGenerator} 的语义，复制一份必然漂。
 */
public final class EnchantObtainTags {

    private static final String VANILLA_NS = "minecraft";
    private static final String CONFIG_DIR = "enchantments/";
    private static final String CONFIG_SUFFIX = ".json5";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 三条原版可获取性 tag（**写死常量，不许从配置读**：读配置等于允许内容侧关掉获取途径还不报警）。 */
    private static final List<String> TAGS =
            List.of("in_enchanting_table", "tradeable", "on_random_loot");

    private EnchantObtainTags() {
    }

    /**
     * 把 {@code enchantments/*.json5} 声明的附魔 id 追加进三条原版可获取性 tag。
     *
     * @param datapackRoot 数据包根（{@code <world>/datapacks/redtale_project}）
     * @param configs      reload 时的全量配置，内部只筛 {@code enchantments/} 前缀
     * @param warnings     非致命告警出口（null 时丢弃）
     * @return 实际写出（新建或内容变化）的文件数
     */
    public static int generateAll(Path datapackRoot, List<ConfigFile> configs, List<String> warnings) {
        List<String> log = warnings == null ? new ArrayList<>() : warnings;
        if (datapackRoot == null) {
            log.add("obtain tag：数据包根目录为空，本次不生成");
            return 0;
        }
        List<String> values = collectIds(configs, log);
        int written = 0;
        for (String tag : TAGS) {
            Path out = datapackRoot.resolve("data").resolve(VANILLA_NS)
                    .resolve("tags").resolve("enchantment").resolve(tag + ".json");
            if (writeTag(out, values, log)) {
                written++;
            }
        }
        return written;
    }

    /** 从配置里取附魔 id（升序去重）。坏文件只 WARN，不拖累整批。 */
    static List<String> collectIds(List<ConfigFile> configs, List<String> log) {
        Set<String> acc = new TreeSet<>();
        if (configs == null) {
            return List.of();
        }
        for (ConfigFile file : configs) {
            if (file == null || file.path() == null || file.root() == null) {
                continue;
            }
            String path = file.path();
            if (!path.startsWith(CONFIG_DIR) || !path.endsWith(CONFIG_SUFFIX)) {
                continue;
            }
            JsonElement raw = file.root().get("id");
            if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
                log.add("obtain tag：" + path + " 没有字符串 id，已跳过（不按文件名猜命名空间）");
                continue;
            }
            String id = raw.getAsString().trim();
            if (!id.contains(":")) {
                log.add("obtain tag：" + path + " 的 id「" + id + "」不含命名空间，已跳过");
                continue;
            }
            acc.add(id);
        }
        return List.copyOf(acc);
    }

    /**
     * 写一份 tag 文件；内容与磁盘上已有的一致就<b>不动</b>（幂等）。
     *
     * <p>与 {@code EnchantableTags#writeTag} 同一实现口径：Gson pretty + 末尾换行 +
     * 逐字节比对。重复一份而不抽公共工具，是因为两边的失败文案不同（一个是「enchantable」，
     * 一个是「obtain tag」），日志里要能一眼看出是谁写坏的。
     */
    private static boolean writeTag(Path out, List<String> values, List<String> log) {
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        JsonArray arr = new JsonArray();
        for (String value : values) {
            arr.add(value);
        }
        json.add("values", arr);
        String text = GSON.toJson(json) + "\n";
        try {
            if (Files.exists(out) && text.equals(Files.readString(out, StandardCharsets.UTF_8))) {
                return false;
            }
            Path parent = out.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, text, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            log.add("obtain tag：写入 " + out + " 失败（" + e.getMessage() + "），已跳过");
            return false;
        }
    }
}
