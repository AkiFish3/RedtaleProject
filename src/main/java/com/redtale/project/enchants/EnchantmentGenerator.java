package com.redtale.project.enchants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 附魔生成器（D32 的 L1 → L0 层）：把 {@code config/redtale_project/enchantments/*.json5}
 * 转成数据包里的 {@code data/<ns>/enchantment/<path>.json}，即真·原版 {@code Enchantment} 注册表项。
 *
 * <p>只做格式转换：<b>不写 lang、不接 reload、不注册任何东西</b>（M4 才接线）。附魔是 datapack
 * registry 元素（{@code ENCHANT-API.md} F1），生成物进 {@code <world>/datapacks/redtale_project/} 后
 * {@code /reload} 即可读。
 *
 * <p><b>透传原则</b>（§3.1）：除 {@code description} 改写成 lang key 外，白名单字段原样搬进 JSON，
 * 不做二次抽象、不做 schema 校验。失败策略：类型不符 / 缺必填 → 告警一行 + 跳过，绝不抛异常。
 */
public final class EnchantmentGenerator {

    /** 配置侧目录（相对 {@code config/redtale_project/}）。 */
    public static final String CONFIG_DIR = "enchantments/";

    private static final String DEFAULT_NAMESPACE = "redtale_project";
    private static final String CONFIG_SUFFIX = ".json5";
    /** 数据包内子目录名（单数，原版注册表固定）。 */
    private static final String DATA_DIR = "enchantment";
    /** 白名单字段，数组顺序即 JSON 里的写出顺序（§3.1）。 */
    private static final String[] PASS_THROUGH = {
            "max_level", "weight", "anvil_cost", "slots", "supported_items", "primary_items",
            "min_cost", "max_cost", "exclusive_set", "effects"
    };
    private static final String PACK_MCMETA =
            "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"redtale generated\"\n  }\n}\n";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private EnchantmentGenerator() {
    }

    /**
     * @param datapackRoot 数据包根（{@code <world>/datapacks/redtale_project}）
     * @param configs      reload 时的全量配置，内部只筛 {@code enchantments/} 前缀
     * @param warnings     非致命告警出口（null 时丢弃）
     * @return 实际写出的文件数
     */
    public static int generateAll(Path datapackRoot, List<ConfigFile> configs, List<String> warnings) {
        List<String> log = warnings == null ? new ArrayList<>() : warnings;
        if (datapackRoot == null) {
            log.add("enchantments：数据包根目录为空，本次不生成");
            return 0;
        }
        if (configs == null) {
            return 0;
        }
        // 先在内存里全转完再落盘：一条坏配置不该留下半个数据包
        List<Emit> emits = new ArrayList<>();
        for (ConfigFile file : configs) {
            if (file == null || file.path() == null) {
                continue;
            }
            String path = file.path();
            if (!path.startsWith(CONFIG_DIR) || !path.endsWith(CONFIG_SUFFIX)) {
                continue;
            }
            Emit emit = convert(file, path.substring(CONFIG_DIR.length(), path.length() - CONFIG_SUFFIX.length()), log);
            if (emit != null) {
                emits.add(emit);
            }
        }
        if (emits.isEmpty()) {
            return 0;
        }
        try {
            Files.createDirectories(datapackRoot);
        } catch (IOException e) {
            log.add("enchantments：创建数据包目录失败（" + e.getMessage() + "），本次不生成");
            return 0;
        }
        ensurePackMeta(datapackRoot, log);

        int written = 0;
        for (Emit emit : emits) {
            Path out = datapackRoot.resolve("data")
                    .resolve(emit.namespace())
                    .resolve(DATA_DIR)
                    .resolve(emit.path() + ".json");
            try {
                Path parent = out.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(out, GSON.toJson(emit.json()) + "\n", StandardCharsets.UTF_8);
                written++;
            } catch (IOException e) {
                log.add("enchantments：写入 " + out + " 失败（" + e.getMessage() + "），已跳过");
            }
        }
        return written;
    }

    /** 一份配置 → 一份待写出的 L0 JSON；不合格返回 {@code null}（已记告警）。 */
    private static Emit convert(ConfigFile file, String rel, List<String> warnings) {
        JsonObject root = file.root();
        if (root == null) {
            warnings.add(file.path() + "：根不是 JSON 对象，已跳过");
            return null;
        }
        String ref = resolveId(root, rel, file.path(), warnings);
        if (ref == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(ref);
        if (id == null || id.getPath().contains("..") || id.getPath().startsWith("/")) {
            warnings.add(file.path() + "：id \"" + ref + "\" 不是合法 id，已跳过");
            return null;
        }
        JsonElement supported = root.get("supported_items");
        if (supported == null || supported.isJsonNull()) {
            warnings.add(file.path() + "：缺 supported_items（原版必填），已跳过");
            return null;
        }
        if (!supported.isJsonPrimitive() && !supported.isJsonArray()) {
            warnings.add(file.path() + "：supported_items 类型不符（原版必填），已跳过");
            return null;
        }

        JsonObject json = new JsonObject();
        // 中文名不进 JSON：只留 lang key，原文由 lang 生成步骤负责（§3.1）
        JsonObject description = new JsonObject();
        description.addProperty("translate",
                "enchantment." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        json.add("description", description);

        for (String key : PASS_THROUGH) {
            JsonElement value = root.get(key);
            if (value == null || value.isJsonNull()) {
                continue; // 缺省不写：schema 里 primary_items: null 是正常写法
            }
            if (numericKey(key) && !(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())) {
                warnings.add(file.path() + "：" + key + " 类型不符，已省略该字段");
                continue;
            }
            json.add(key, value);
        }
        return new Emit(id.getNamespace(), id.getPath(), json);
    }

    /** {@code max_level} / {@code weight} / {@code anvil_cost} 必须是数字，别的字段原样透传。 */
    private static boolean numericKey(String key) {
        return "max_level".equals(key) || "weight".equals(key) || "anvil_cost".equals(key);
    }

    /**
     * {@code root.id} 有就用，没有 = {@code redtale_project:} + 路径（去掉前缀与后缀）。
     * 没写命名空间的补默认命名空间；id 类型不符则告警并让调用方跳过整条。
     */
    private static String resolveId(JsonObject root, String rel, String source, List<String> warnings) {
        JsonElement raw = root.get("id");
        String ref;
        if (raw == null || raw.isJsonNull()) {
            ref = rel;
        } else if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()) {
            ref = raw.getAsString().trim();
            if (ref.isEmpty()) {
                ref = rel;
            }
        } else {
            warnings.add(source + "：id 类型不符，已跳过该条");
            return null;
        }
        return ref.indexOf(':') >= 0 ? ref : DEFAULT_NAMESPACE + ":" + ref;
    }

    /** 没有 pack.mcmeta 的目录 MC 不认成数据包；已存在则不覆盖（T-0001 同形，独立一份，不共享可见性）。 */
    private static void ensurePackMeta(Path datapackRoot, List<String> warnings) {
        Path meta = datapackRoot.resolve("pack.mcmeta");
        if (Files.exists(meta)) {
            return;
        }
        try {
            Files.writeString(meta, PACK_MCMETA, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("enchantments：写 pack.mcmeta 失败（" + e.getMessage() + "）");
        }
    }

    /** 待写出的一份附魔：命名空间 / 路径 / L0 JSON。 */
    private record Emit(String namespace, String path, JsonObject json) {
    }
}
