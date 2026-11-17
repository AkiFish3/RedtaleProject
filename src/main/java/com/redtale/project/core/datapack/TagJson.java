package com.redtale.project.core.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据包文件写手（唯一实现，R39 抽出）。
 *
 * <p>为什么单独一个类：{@code EnchantableTags} / {@code EnchantObtainTags} / {@code MobEntityTags}
 * 三处都要写「{@code replace: false} + values」这一种 tag；再加上两个 JSON 生成器
 * （{@code EnchantmentGenerator} / {@code WeaponAttributesGenerator}），一共五个写盘点。
 * 不收口的话，「幂等比较」「末尾换行」「失败只记告警」这几条口径就会各写各的 ——
 * 而这几条正是「reload 会不会白重载整包资源」的开关。
 *
 * <p><b>两条硬口径</b>（实测结论，别改）：
 * <ul>
 *   <li><b>幂等</b>：内容与磁盘逐字相同就 {@code return false} 不动文件 —— 每次 reload 都改 mtime 会让
 *       {@code PackEnabler} 认为包变了、触发整包重载（R39：三个文件无条件重写 ⇒ 每次都白重载一遍）。</li>
 *   <li><b>空集也写</b>：{@code values: []} 是「本方不再贡献任何值」的合法表达（原版与其它 mod 的值靠
 *       {@code replace: false} 保留），不能因为空就跳过写 —— 否则删掉最后一条配置后旧值永远留在磁盘上。</li>
 * </ul>
 */
public final class TagJson {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TagJson() {
    }

    /**
     * 写一份 tag 文件；内容一致就不动。
     *
     * @param out    目标文件（父目录会自动创建）
     * @param values 本方的值（已排序、去重是调用方的事；这里原样写入）
     * @param label  告警前缀（例如 {@code "mob undead tag"}），只出现在 IO 失败那一条里
     * @param log    告警出口，null 时丢弃
     * @return 真的写了（新建或内容变化）→ {@code true}
     */
    public static boolean write(Path out, List<String> values, String label, List<String> log) {
        if (out == null) {
            return false;
        }
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        JsonArray arr = new JsonArray();
        if (values != null) {
            for (String value : values) {
                arr.add(value);
            }
        }
        json.add("values", arr);
        return writeIfChanged(out, GSON.toJson(json) + "\n", label, log);
    }

    /**
     * 文本版：内容与磁盘逐字相同就 {@code return false} 不动文件。
     *
     * <p>为什么生成器也要走它（R39）：两个 JSON 生成器原来无条件 {@code writeString} —— 内容没变也刷新
     * mtime，于是「本次写出 N 个文件」永远 ≥ 1；而 {@code /redtale reload} 现在拿这个计数决定要不要
     * 重载资源 → 每次 reload 都白重载一遍数据包（实测 3 个文件恒变）。
     *
     * @param text 完整文件内容（含末尾换行）
     * @return 真的写了（新建或内容变化）；IO 失败也返回 {@code false} 并记一条告警
     */
    public static boolean writeIfChanged(Path out, String text, String label, List<String> log) {
        if (out == null || text == null) {
            return false;
        }
        List<String> sink = log == null ? new ArrayList<>() : log;
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
            sink.add(label + "：写入 " + out + " 失败（" + e.getMessage() + "），已跳过");
            return false;
        }
    }
}
