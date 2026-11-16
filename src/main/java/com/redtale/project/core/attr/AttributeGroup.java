package com.redtale.project.core.attr;

/**
 * 属性分类。只影响展示顺序与分组标题，不影响注册顺序之外的行为。
 *
 * <p>分组与 {@code docs/ATTRIBUTE-REGISTRY.md} §2 的 A~F 段一一对应；
 * {@link #RESERVED} 的条目「注册但不使用」——没有物品/技能会给它们值，
 * {@code /redtale attr list} 不列，只在直接查询时可见。
 */
public enum AttributeGroup {

    /** A 段：近战与物理（12）。 */
    MELEE("近战与物理", "Melee & Physical"),
    /** B 段：防御与减伤（18）。 */
    DEFENSE("防御与减伤", "Defense"),
    /** C 段：法术与资源（18）。 */
    SPELL("法术与资源", "Spell & Resource"),
    /** D 段：采集与移动（10）。 */
    GATHER("采集与移动", "Gather & Movement"),
    /** E 段：通用与进度（6）。 */
    GENERAL("通用与进度", "General"),
    /** F 段：预留 16 条，注册但不使用。 */
    RESERVED("预留（不上面板）", "Reserved");

    private final String zh;
    private final String en;

    AttributeGroup(String zh, String en) {
        this.zh = zh;
        this.en = en;
    }

    public String zh() {
        return zh;
    }

    public String en() {
        return en;
    }
}
