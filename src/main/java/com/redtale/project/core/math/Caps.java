package com.redtale.project.core.math;

/**
 * 平衡闸门（D16）。**默认全 0 = 关闭**，代码里不写死任何平衡值。
 *
 * <p>语义：{@code 0} 表示「不限制」。负值视同关闭（配置写错不该把伤害打成 0）。
 *
 * <p>攻速的 {@code softCap} **不在这里** —— 它归 {@code attack_speed.json5}（D18），
 * 因为攻速曲线与来源表必须放在同一份文件里，否则「谁改的」说不清。
 *
 * @param percentCap 百分比池相加后的上限，0 = 不限
 * @param condCap    单个条件乘区的上限，0 = 不限
 * @param condCount  条件乘区最多生效几个，0 = 不限
 * @param damageCap  单次伤害绝对天花板，0 = 不限
 */
public record Caps(double percentCap, double condCap, int condCount, double damageCap) {

    /** 默认：全关。 */
    public static final Caps OFF = new Caps(0, 0, 0, 0);

    public boolean percentLimited() {
        return percentCap > 0;
    }

    public boolean condLimited() {
        return condCap > 0;
    }

    public boolean countLimited() {
        return condCount > 0;
    }

    public boolean damageLimited() {
        return damageCap > 0;
    }
}
