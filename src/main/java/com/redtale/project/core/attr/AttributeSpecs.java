package com.redtale.project.core.attr;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.redtale.project.core.attr.AttributeGroup.*;

/**
 * 80 条属性总表（{@code docs/ATTRIBUTE-REGISTRY.md} §2 的 A~F 段，顺序即文档顺序）。
 *
 * <p><b>这是唯一事实来源</b>：
 * <ul>
 *   <li>{@link RedtaleAttributes} 按本表注册（只注册 {@code c()} / {@code cb()} 的行）；</li>
 *   <li>{@code tools/gen_attr_lang.py} 解析本文件生成 {@code assets/redtale_project/lang/*.json}；</li>
 *   <li>{@code /redtale attr list} 按本表顺序与分组打印。</li>
 * </ul>
 *
 * <p>改这张表 = 换 jar（属性是启动期冻结的注册表项），所以它有 80 条校验：
 * {@link #ALL} 初始化时就断言数量与文档一致，写漏一条启动即失败，不会静默少注册。
 */
public final class AttributeSpecs {

    private AttributeSpecs() {
    }

    /**
     * 原版属性：只引用，不注册。值来自 {@code net.minecraft.world.entity.ai.attributes.Attributes}。
     */
    private static AttributeSpec v(String id, AttributeGroup group, String zh, String en) {
        return new AttributeSpec(id, 0.0, group, zh, en);
    }

    /** 自建属性，基值 0。 */
    private static AttributeSpec c(String path, AttributeGroup group, String zh, String en) {
        return new AttributeSpec(path, 0.0, group, zh, en);
    }

    /** 自建属性，非零基值（上游有明确的默认基值时才用）。 */
    private static AttributeSpec cb(String path, double base, AttributeGroup group, String zh, String en) {
        return new AttributeSpec(path, base, group, zh, en);
    }

    /** 文档定死的条数。改动它必须同时改 {@code docs/ATTRIBUTE-REGISTRY.md} §2。 */
    public static final int EXPECTED_COUNT = 80;

    private static final Map<String, AttributeSpec> BY_ID;

    public static final List<AttributeSpec> ALL = List.of(

            // ---------------------------------------------------------------- A. 近战与物理（12）
            v("minecraft:generic.attack_damage", MELEE, "攻击伤害", "Attack Damage"),
            v("minecraft:generic.attack_speed", MELEE, "攻击速度", "Attack Speed"),
            c("physical_damage", MELEE, "物理伤害", "Physical Damage"),
            c("weapon_damage", MELEE, "武器伤害", "Weapon Damage"),
            c("projectile_damage", MELEE, "投射物伤害", "Projectile Damage"),
            c("critical_strike_chance", MELEE, "暴击率", "Critical Strike Chance"),
            cb("critical_strike_power", 2.0, MELEE, "暴击倍率", "Critical Strike Power"),
            c("armor_penetration", MELEE, "护甲穿透", "Armor Penetration"),
            c("lifesteal", MELEE, "生命偷取", "Lifesteal"),
            c("pvp_damage", MELEE, "对玩家伤害", "PvP Damage"),
            c("pve_damage", MELEE, "对怪物伤害", "PvE Damage"),
            c("undead_damage", MELEE, "对亡灵伤害", "Undead Damage"),

            // ---------------------------------------------------------------- B. 防御与减伤（18）
            v("minecraft:generic.armor", DEFENSE, "护甲", "Armor"),
            v("minecraft:generic.armor_toughness", DEFENSE, "护甲韧性", "Armor Toughness"),
            v("minecraft:generic.max_health", DEFENSE, "最大生命", "Max Health"),
            v("minecraft:generic.knockback_resistance", DEFENSE, "击退抗性", "Knockback Resistance"),
            c("damage_reduction", DEFENSE, "全局减伤", "Damage Reduction"),
            c("block_rating", DEFENSE, "格挡评级", "Block Rating"),
            cb("block_power", 25.0, DEFENSE, "格挡强度", "Block Power"),
            c("block_cooldown_reduction", DEFENSE, "格挡冷却缩减", "Block Cooldown Reduction"),
            c("dodge_rating", DEFENSE, "闪避评级", "Dodge Rating"),
            c("dodge_cooldown_reduction", DEFENSE, "闪避冷却缩减", "Dodge Cooldown Reduction"),
            c("parry_rating", DEFENSE, "招架评级", "Parry Rating"),
            c("parry_cooldown_reduction", DEFENSE, "招架冷却缩减", "Parry Cooldown Reduction"),
            c("pvp_defense", DEFENSE, "对玩家减伤", "PvP Defense"),
            c("pve_defense", DEFENSE, "对怪物减伤", "PvE Defense"),
            c("magic_resistance", DEFENSE, "法术抗性", "Magic Resistance"),
            c("health_regeneration", DEFENSE, "生命回复", "Health Regeneration"),
            c("max_absorption", DEFENSE, "伤害吸收上限", "Max Absorption"),
            c("reflect_damage", DEFENSE, "反伤", "Reflect Damage"),

            // ---------------------------------------------------------------- C. 法术与资源（18）
            c("magical_damage", SPELL, "法术伤害", "Magical Damage"),
            c("spell_critical_strike_chance", SPELL, "法术暴击率", "Spell Critical Chance"),
            cb("spell_critical_strike_power", 2.0, SPELL, "法术暴击倍率", "Spell Critical Power"),
            c("spell_vampirism", SPELL, "法术吸血", "Spell Vampirism"),
            c("max_mana", SPELL, "法力上限", "Max Mana"),
            c("mana_regeneration", SPELL, "法力回复", "Mana Regeneration"),
            c("cooldown_reduction", SPELL, "冷却缩减", "Cooldown Reduction"),
            c("healing_power", SPELL, "治疗强度", "Healing Power"),
            c("fire_damage", SPELL, "火系强度", "Fire Damage"),
            c("frost_damage", SPELL, "冰系强度", "Frost Damage"),
            c("lightning_damage", SPELL, "雷系强度", "Lightning Damage"),
            c("arcane_damage", SPELL, "奥术强度", "Arcane Damage"),
            c("nature_damage", SPELL, "自然强度", "Nature Damage"),
            c("shadow_damage", SPELL, "暗影强度", "Shadow Damage"),
            c("holy_damage", SPELL, "神圣强度", "Holy Damage"),
            c("water_damage", SPELL, "水系强度", "Water Damage"),
            c("earth_damage", SPELL, "土系强度", "Earth Damage"),
            c("spell_resistance", SPELL, "元素抗性", "Spell Resistance"),

            // ---------------------------------------------------------------- D. 采集与移动（10）
            v("minecraft:generic.movement_speed", GATHER, "移动速度", "Movement Speed"),
            v("minecraft:generic.block_break_speed", GATHER, "挖掘速度", "Block Break Speed"),
            v("minecraft:generic.jump_strength", GATHER, "跳跃力", "Jump Strength"),
            v("minecraft:generic.safe_fall_distance", GATHER, "安全下落距离", "Safe Fall Distance"),
            v("minecraft:generic.step_height", GATHER, "跨步高度", "Step Height"),
            v("minecraft:generic.oxygen_bonus", GATHER, "水下呼吸加成", "Oxygen Bonus"),
            c("sneaking_speed", GATHER, "潜行速度", "Sneaking Speed"),
            c("water_movement_efficiency", GATHER, "水中移动效率", "Water Movement Efficiency"),
            c("mining_efficiency", GATHER, "挖掘效率", "Mining Efficiency"),
            c("fall_damage_multiplier", GATHER, "摔落伤害倍率", "Fall Damage Multiplier"),

            // ---------------------------------------------------------------- E. 通用与进度（6）
            v("minecraft:generic.luck", GENERAL, "幸运", "Luck"),
            c("additional_experience", GENERAL, "经验加成", "Additional Experience"),
            c("skill_damage", GENERAL, "技能伤害", "Skill Damage"),
            c("max_stamina", GENERAL, "耐力上限", "Max Stamina"),
            c("stamina_regeneration", GENERAL, "耐力回复", "Stamina Regeneration"),
            v("minecraft:generic.scale", GENERAL, "体型", "Scale"),

            // ---------------------------------------------------------------- F. 预留 16 条（注册但不使用）
            //
            // 注意 #70 attack_range / #71 sweeping_damage_ratio：原版已有
            // minecraft:generic.entity_interaction_range / sweeping_damage_ratio。
            // 按 ATTRIBUTE-REGISTRY §2F 的表原样注册（表已定，不擅自增删），
            // 语义上作为「便捷别名」——M2 接线时由 settle() 读它再映射到原版行为，
            // 不复用原版那两条是为了让内容侧只面对一套 redtale_project: 名字。
            c("air_damage", RESERVED, "风系强度", "Air Damage"),
            c("soul_damage", RESERVED, "魂系强度", "Soul Damage"),
            c("blood_damage", RESERVED, "血系强度", "Blood Damage"),
            c("unholy_damage", RESERVED, "邪系强度", "Unholy Damage"),
            c("true_damage", RESERVED, "真实伤害", "True Damage"),
            c("attack_range", RESERVED, "攻击距离", "Attack Range"),
            c("sweeping_damage_ratio", RESERVED, "横扫伤害比例", "Sweeping Damage Ratio"),
            c("hunger_efficiency", RESERVED, "饥饿效率", "Hunger Efficiency"),
            c("mana_cost_reduction", RESERVED, "法力消耗降低", "Mana Cost Reduction"),
            c("charge_speed", RESERVED, "蓄力速度", "Charge Speed"),
            c("projectile_velocity", RESERVED, "投射物速度", "Projectile Velocity"),
            c("healing_received", RESERVED, "受治疗加成", "Healing Received"),
            c("armor_durability", RESERVED, "装备耐久", "Armor Durability"),
            c("potion_duration", RESERVED, "药水时长", "Potion Duration"),
            c("knockback_power", RESERVED, "击退强度", "Knockback Power"),
            c("critical_damage_resistance", RESERVED, "暴击伤害减免", "Critical Damage Resistance")
    );

    static {
        if (ALL.size() != EXPECTED_COUNT) {
            throw new IllegalStateException("属性表条数 " + ALL.size() + " != 文档定的 " + EXPECTED_COUNT
                    + "（占位行不算；改表必须同步改 docs/ATTRIBUTE-REGISTRY.md §2）");
        }
        Map<String, AttributeSpec> index = new LinkedHashMap<>();
        for (AttributeSpec spec : ALL) {
            AttributeSpec prev = index.put(spec.id(), spec);
            if (prev != null) {
                throw new IllegalStateException("属性 id 重复：" + spec.id());
            }
        }
        BY_ID = Collections.unmodifiableMap(index);
    }

    /** 按全名查（自建不带前缀，原版带 {@code minecraft:}）。 */
    public static AttributeSpec byId(String id) {
        return BY_ID.get(id);
    }

    /**
     * 宽松解析：接受全名、带前缀名、路径名。
     *
     * <p>例：{@code attack_speed} / {@code generic.attack_speed} /
     * {@code minecraft:generic.attack_speed} / {@code redtale_project:attack_speed} 都指向同一条。
     *
     * <p>另接受把命名空间写成点的形态（{@code minecraft.attack_speed}）——
     * Brigadier 的裸参数不允许冒号，这条是命令行的便利入口。
     */
    public static AttributeSpec resolve(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String t = token.trim();
        AttributeSpec direct = BY_ID.get(t);
        if (direct != null) {
            return direct;
        }
        // 点当冒号：minecraft.generic.attack_speed → minecraft:generic.attack_speed
        if (!t.contains(":")) {
            if (t.startsWith("minecraft.")) {
                AttributeSpec s = BY_ID.get("minecraft:" + t.substring("minecraft.".length()));
                if (s != null) {
                    return s;
                }
            } else if (t.startsWith("redtale_project.")) {
                AttributeSpec s = BY_ID.get(t.substring("redtale_project.".length()));
                if (s != null) {
                    return s;
                }
            }
        }
        if (t.startsWith("redtale_project:")) {
            return BY_ID.get(t.substring("redtale_project:".length()));
        }
        if (t.startsWith("minecraft:")) {
            // 允许省略 generic. 前缀，例 minecraft:attack_damage
            return BY_ID.get("minecraft:generic." + t.substring("minecraft:".length()));
        }
        // 裸路径：generic.* 是原版属性的绝大多数
        return BY_ID.get("minecraft:generic." + t);
    }

    /** 只取自建条目（要注册的那些）。 */
    public static List<AttributeSpec> custom() {
        return ALL.stream().filter(s -> !s.vanilla()).toList();
    }

    /** 只取原版条目（只引用、不注册）。 */
    public static List<AttributeSpec> vanilla() {
        return ALL.stream().filter(AttributeSpec::vanilla).toList();
    }
}
