package com.redtale.project.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一份装备内容定义（{@code config/redtale_project/items/*.json5} 解析结果）。
 *
 * <p>字段口径（写在 {@code docs/ROUND-R5-ITEMS.md}，这里只留结论）：
 * <ul>
 *   <li>{@code stats} 的值一律是**该属性的平加量**（{@code ADD_VALUE}）。哪怕是「+20% 物理伤害」，
 *       也写 {@code physical_damage: 0.2} 作为平加 —— 因为自建属性基值全是 0，
 *       用 {@code ADD_MULTIPLIED_TOTAL} 乘 0 永远是 0。百分比只影响**显示**（{@link StatFormat}）。</li>
 *   <li>{@code attack_speed} 例外：写**目标次/秒**（{@code 1.6}），建栈时换算成
 *       {@code 1.6 - 4.0 = -2.4} 的原版式 modifier（原版剑就是 -2.4）。</li>
 *   <li>{@code strip_vanilla_effects} 目前只记录不生效（要移除 {@code enchantable} + tag，
 *       M4 附魔层再接），留着是为了配置先行、以后不返工。</li>
 * </ul>
 *
 * @param stats 属性全名（原版带 {@code minecraft:}，自建裸路径）→ 平加值，顺序即配置顺序
 * @param rev   **定义修订号**（D33）：改这件物品的任何「定义型字段」都要 +1。物品上存的是
 *              「生成时的那一版 rev」，与当前定义不等就触发升级（{@code docs/ITEM-UPDATE.md}）。
 *              漏加的代价是「改了配置但老物品不更新」，所以发布检查要盯它。
 * @param consumable {@code use} 段的解析结果（D23）；{@code null} = 不是消耗品。
 *              **不落进 {@link ContentData}**：右键时按 {@code contentId} 现查定义，
 *              所以改 {@code use} 段 + {@code /redtale reload} 对存量物品立刻生效（不需要 rev+1）。
 *              **只能挂在非护甲基底上** —— 护甲基底是 {@code ArmorItem}，右键语义是「穿戴」，
 *              解析期遇到就告警并丢弃 {@code use} 段（见 {@link #parse}）。
 * @param repair {@code repair} 段的解析结果（D23 第二半，R16）；{@code null} = 不是修理物品。
 *              与 {@code consumable} 同口径：不落组件、右键时现查，改配置不必 `rev`+1。
 *              同样只能挂非护甲基底（右键是唯一入口）。结算见 {@link Repairer}。
 * @param sockets {@code sockets} 段的解析结果（M3，{@code ITEM-MODEL.md} §5.2）：孔数与每孔类型。
 *                <b>定义型字段</b> —— 改它要 `rev`+1，否则存量物品的孔位数不会跟着变。
 *                缺省 = {@link SocketSpec.Layout#EMPTY}（无孔）。
 * @param gem     {@code gem} 段的解析结果（§5.3）：{@code null} = 这不是宝石。
 *                宝石本身也是内容物品（另一件 {@code items/*.json5}），镶嵌时按内容 id 引用。
 * @param setId   {@code set_id}（M3 套装，§5.4）：这件物品属于哪个套装，空串 = 不属于任何套装。
 *                <b>定义型字段</b> —— 改它要 `rev`+1，否则存量物品的套装归属不会跟着变。
 *                这里只存「归属」，套装数值**永不进 {@code ContentData}**：件数一变就整张重算，
 *                写进玩家身上固定 id 的 modifier（{@code SetBonus}）。
 * @param abilities 绑定技能（M5-B，{@code SKILLS.md} §7 v1.5）：{@code ["redtale_project:gale_slash"]}。
 *                右键施放**第一条**（多技能键位留给 M5-C）。<b>定义型字段</b> —— 改它要 `rev`+1，
 *                否则存量物品的 {@code ContentData.abilities} 不会跟着变。
 *                这里**不校验技能是否存在**（R28-d：技能与物品是两份并行配置，校验归
 *                {@code /redtale validate}）；缺的技能到施放时由引擎报 {@code NO_SKILL}。
 */
public record ItemContent(
        String id,
        ItemBase base,
        String name,
        int durability,
        String rarity,
        String weaponAttributes,
        Map<String, Double> stats,
        boolean stripVanillaEffects,
        int rev,
        String source,
        ConsumableSpec.Spec consumable,
        RepairSpec.Spec repair,
        SocketSpec.Layout sockets,
        SocketSpec.Gem gem,
        String setId,
        List<String> abilities) {

    public ItemContent {
        stats = Map.copyOf(stats);
        abilities = List.copyOf(abilities);
    }

    /**
     * 解析一份内容定义。**语义错误只跳过这一条**（R1-a 的分级失败原则）：返回 {@code null}
     * 并把原因塞进 {@code warnings}，其余内容照常载入。
     */
    public static ItemContent parse(JsonObject root, String source, List<String> warnings) {
        String id = str(root, "id", "");
        if (id.isEmpty()) {
            warnings.add(source + "：缺 id");
            return null;
        }
        if (!id.matches("[a-z0-9_]+")) {
            warnings.add(source + "：id \"" + id + "\" 只能用小写字母 / 数字 / 下划线"
                    + "（它会被写进 modifier id 与存档，发布后不能改）");
            return null;
        }

        String baseId = str(root, "base", "");
        ItemBase base = ItemBase.byId(baseId);
        if (base == null) {
            warnings.add(source + "：" + id + " 的 base \"" + baseId + "\" 不存在（可用：" + ItemBase.allIds() + "）");
            return null;
        }

        int durability = (int) num(root, "durability", base.defaultDurability(), warnings, source);
        if (durability < 0) {
            warnings.add(source + "：" + id + " 的 durability 为负，按 0（不可损坏）处理");
            durability = 0;
        }

        String rarity = str(root, "rarity", "common").toLowerCase(Locale.ROOT);
        if (!List.of("common", "uncommon", "rare", "epic").contains(rarity)) {
            warnings.add(source + "：" + id + " 的 rarity \"" + rarity + "\" 不认识，按 common 处理");
            rarity = "common";
        }

        Map<String, Double> stats = new LinkedHashMap<>();
        JsonElement statsEl = root.get("stats");
        if (statsEl != null && statsEl.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : statsEl.getAsJsonObject().entrySet()) {
                AttributeSpec spec = AttributeSpecs.resolve(e.getKey());
                if (spec == null) {
                    warnings.add(source + "：" + id + " 的属性 \"" + e.getKey()
                            + "\" 不在 80 条表里（属性是启动期注册表，加一条要换 jar）—— 已跳过");
                    continue;
                }
                if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) {
                    warnings.add(source + "：" + id + " 的属性 " + spec.id() + " 不是数字 —— 已跳过");
                    continue;
                }
                double v = e.getValue().getAsDouble();
                if (!Double.isFinite(v)) {
                    warnings.add(source + "：" + id + " 的属性 " + spec.id() + " 不是有限数 —— 已跳过");
                    continue;
                }
                stats.put(spec.id(), v);
            }
        }

        int rev = (int) num(root, "rev", 0, warnings, source);
        if (rev < 0) {
            warnings.add(source + "：" + id + " 的 rev 为负，按 0 处理");
            rev = 0;
        }

        ConsumableSpec.Spec consumable = ConsumableSpec.parse(root, source, warnings);
        RepairSpec.Spec repair = RepairSpec.parse(root, source, warnings);
        if (base.isArmor() && (consumable != null || repair != null)) {
            warnings.add(source + "：" + id + " 的基底 " + base.id()
                    + " 是护甲（右键 = 穿戴），use/repair 段已忽略 —— 消耗品与修理品请用非护甲基底（如 amulet）");
            consumable = null;
            repair = null;
        }

        // M3：孔位与宝石（纯解析，失败只告警不中断；socket 结算在 Socketer 里）
        SocketSpec.Layout sockets = SocketSpec.parseLayout(root, source, warnings);
        SocketSpec.Gem gem = SocketSpec.parseGem(root, source, warnings);

        // M3 套装归属：只认字符串，空白按「不属于任何套装」；套装定义本身是否存在由
        // /redtale validate 检查（解析期看不到 sets/，两份配置是并行的）
        String setId = str(root, "set_id", "").trim();

        // M5-B：绑定技能（只解析形态，不校验技能存在 —— R28-d）
        List<String> abilities = parseAbilities(root, source, warnings);

        return new ItemContent(id, base, str(root, "name", id), durability, rarity,
                str(root, "weapon_attributes", ""), stats,
                bool(root, "strip_vanilla_effects", false), rev, source, consumable, repair,
                sockets, gem, setId, abilities);
    }

    /**
     * {@code abilities} 段：数组，每项一个技能 id（带命名空间）。
     * 坏项只跳过自己并告警（R1-a 分级失败），不影响这件物品的其它字段。
     */
    private static List<String> parseAbilities(JsonObject root, String source, List<String> warnings) {
        JsonElement el = root.get("abilities");
        if (el == null || el.isJsonNull()) {
            return List.of();
        }
        if (!el.isJsonArray()) {
            warnings.add(source + "：abilities 不是数组 —— 已忽略（应写 [\"redtale_project:技能id\"]）");
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                warnings.add(source + "：abilities 里有非字符串项 —— 已跳过");
                continue;
            }
            String skillId = item.getAsString().trim();
            if (skillId.isEmpty()) {
                warnings.add(source + "：abilities 里有空串 —— 已跳过");
                continue;
            }
            if (!skillId.contains(":")) {
                warnings.add(source + "：abilities 的 \"" + skillId
                        + "\" 缺命名空间（应写 redtale_project:" + skillId + "）—— 已跳过");
                continue;
            }
            if (out.contains(skillId)) {
                warnings.add(source + "：abilities 里 " + skillId + " 重复 —— 已跳过重复项");
                continue;
            }
            out.add(skillId);
        }
        return List.copyOf(out);
    }

    /** 是不是修理物品（{@code repair} 段解析成功）。 */
    public boolean isRepairItem() {
        return repair != null;
    }

    /** 是不是消耗品（{@code use} 段解析成功）。 */
    public boolean isConsumable() {
        return consumable != null;
    }

    /** 是不是宝石（{@code gem} 段解析成功）。宝石是**另一件内容物品**，不是新注册表项（§5.1）。 */
    public boolean isGem() {
        return gem != null;
    }

    /** 有没有孔（定义里有 {@code sockets.count > 0}）。 */
    public boolean hasSockets() {
        return sockets.count() > 0;
    }

    /** 供日志 / {@code /redtale items} 的一行摘要。 */
    public String describe() {
        return id + " · " + base.id() + " · " + stats.size() + " 条属性"
                + (durability > 0 ? " · 耐久 " + durability : " · 不可损坏")
                + (weaponAttributes.isEmpty() ? "" : " · " + weaponAttributes)
                + (consumable != null ? " · 消耗品" : "")
                + (repair != null ? " · 修理品" : "")
                + (sockets.count() > 0 ? " · 孔位 " + sockets.count() : "")
                + (gem != null ? " · 宝石 " + gem.type() + " T" + gem.tier() : "")
                + (setId.isEmpty() ? "" : " · 套装 " + setId)
                + (abilities.isEmpty() ? "" : " · 技能 " + abilities.size())
                + (rev > 0 ? " · rev " + rev : "");
    }

    private static String str(JsonObject root, String key, String fallback) {
        JsonElement el = root.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        return el.getAsString();
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        JsonElement el = root.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()
                || !el.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return el.getAsBoolean();
    }

    private static double num(JsonObject root, String key, double fallback, List<String> warnings, String source) {
        JsonElement el = root.get(key);
        if (el == null || el.isJsonNull()) {
            return fallback;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            warnings.add(source + "：" + key + " 不是数字，按 " + fallback + " 处理");
            return fallback;
        }
        return el.getAsDouble();
    }
}
