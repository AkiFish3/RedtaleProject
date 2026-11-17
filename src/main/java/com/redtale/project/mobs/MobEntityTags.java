package com.redtale.project.mobs;

import com.redtale.project.core.datapack.TagJson;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 把我们自己的怪物原型追加进 {@code #minecraft:undead}（R39，M6 第三步配套）。
 *
 * <p><b>为什么必须有这一层</b>：{@code undead_damage} 条件乘区的唯一判据是
 * {@code EntityTypeTags.UNDEAD}（见 {@code TargetClass#of}）。8 个原型是我们自己注册的 EntityType，
 * 原版 tag 里当然没有它们 → 配置里写 {@code undead: true} 的怪<b>吃不到</b>「对亡灵伤害」那一段，
 * 而且现象是「伤害就是不涨」，没有报错。
 *
 * <p><b>为什么按 base 打、不按怪 id 打</b>：EntityType 是按 {@code MobSpec.BASES} 注册的 8 个
 * （R37-b：base 直接当实体 id 用），实体身上没有「我是哪只怪」的注册表身份 —— 内容 id 只存在于
 * scoreboard tag 与 {@code ContentData} 里。所以 tag 的粒度上限就是原型：
 * 同一 {@code base} 下只要有<b>一只</b>怪声明 {@code undead: true}，整个原型都进 tag。
 * 这是设计上的已知取舍，写在这里免得以后有人当 bug 修。
 *
 * <p><b>写法</b>：{@code data/minecraft/tags/entity_type/undead.json} + {@code "replace": false}
 * （R23 口径：只追加，不替换原版与其它 mod 的值）。没有亡灵怪时写 {@code values: []} = 「本方不贡献」，
 * 而不是跳过写 —— 否则删掉配置后旧值永远留在磁盘上（TagJson 的两条硬口径）。
 */
public final class MobEntityTags {

    /** 原版亡灵 tag 的相对路径（{@code entity_type}，不是 {@code entity_types}）。 */
    static final String TAG_FILE = "data/minecraft/tags/entity_type/undead.json";
    private static final String OWN_NS = "redtale_project";
    private static final String LABEL = "mob undead tag";

    private MobEntityTags() {
    }

    /**
     * 纯函数：哪些原型的 EntityType 该进 {@code #minecraft:undead}（已排序，便于断言与稳定输出）。
     *
     * @param defs {@code mobs/*.json5} 的解析结果，可为 null
     * @return {@code redtale_project:<base>} 列表；无亡灵怪 → 空表
     */
    public static List<String> undeadTypes(Map<String, MobSpec.Definition> defs) {
        if (defs == null || defs.isEmpty()) {
            return List.of();
        }
        TreeSet<String> bases = new TreeSet<>();
        for (MobSpec.Definition def : defs.values()) {
            if (def != null && def.undead() && MobSpec.BASES.contains(def.base())) {
                bases.add(def.base());
            }
        }
        List<String> out = new ArrayList<>(bases.size());
        for (String base : bases) {
            out.add(OWN_NS + ":" + base);
        }
        return List.copyOf(out);
    }

    /**
     * 写出 {@code #minecraft:undead} 的追加文件。
     *
     * @param datapackRoot 数据包根（{@code <world>/datapacks/redtale_project}）
     * @param defs         怪物快照
     * @param warnings     告警出口（null 丢弃）
     * @return 实际写出（新建或内容变化）→ 1，幂等未动 → 0
     */
    public static int generateAll(Path datapackRoot, Map<String, MobSpec.Definition> defs, List<String> warnings) {
        List<String> log = warnings == null ? new ArrayList<>() : warnings;
        if (datapackRoot == null) {
            log.add(LABEL + "：数据包根目录为空，本次不生成");
            return 0;
        }
        Path out = datapackRoot.resolve(TAG_FILE);
        return TagJson.write(out, undeadTypes(defs), LABEL, log) ? 1 : 0;
    }
}
