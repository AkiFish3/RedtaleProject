package com.redtale.project.core.attr;

/**
 * 一条属性的静态声明。**纯数据，不 import 任何 Minecraft 类** —— 这样表本身可以离线校验，
 * lang 文件也能由 {@code tools/gen_attr_lang.py} 从同一份表生成。
 *
 * @param id   全名：自建写 {@code critical_strike_chance}（前缀由注册器补 {@code redtale_project:}），
 *             原版写 {@code minecraft:generic.attack_damage}
 * @param base 默认基值（上游基值：{@code critical_strike_power} 2.0、{@code block_power} 25）
 * @param group 展示分组，见 {@link AttributeGroup}
 * @param zh   中文名（tooltip / 命令显示）
 * @param en   英文名（lang 文件 en_us）
 */
public record AttributeSpec(String id, double base, AttributeGroup group, String zh, String en) {

    /**
     * 属性值下限。取 {@code -1e9} 而不是 0：D16 已定「不设平衡上限」，
     * 负向词条（诅咒装备 / 减益 debuff）是合法内容，语义钳制留在结算处（{@code settle()}）。
     */
    public static final double MIN = -1.0e9;

    /** 属性值上限。D16 的「1e9 硬顶」——这里是它的物理落点（{@code RangedAttribute.sanitizeValue}）。 */
    public static final double MAX = 1.0e9;

    /** 是否原版属性（{@code minecraft:} 命名空间）。原版属性只引用、不注册。 */
    public boolean vanilla() {
        return id.startsWith("minecraft:");
    }

    /** 去掉命名空间的路径部分，也就是 {@code DeferredRegister} 的注册名。 */
    public String path() {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    /** 原版的 translationKey 由游戏自己给，自建的在这里定死，lang 文件的键必须与它一致。 */
    public String translationKey() {
        return vanilla() ? "attribute." + id.replace(':', '.') : "attribute.redtale_project." + path();
    }

    /** 预留条目：注册但不使用。 */
    public boolean reserved() {
        return group == AttributeGroup.RESERVED;
    }
}
