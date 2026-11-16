package com.redtale.project.core.damage;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 目标分类（M2 条件乘区的输入）。
 *
 * <p>条件乘区按「打的是谁」取不同属性：{@code pve_damage} / {@code pvp_damage} /
 * {@code undead_damage}。分类必须只有一处实现，否则 tooltip、结算、{@code /redtale attr}
 * 三处会各算各的（D20 的同源要求）。
 *
 * <p><b>归类口径（2026-11-15 定，写在这里就是契约）</b>：
 * <ul>
 *   <li>玩家 → 只有 {@code pvp}。玩家不进 {@code #minecraft:undead}，但语义上「打人」一律走 PvP 项。</li>
 *   <li>亡灵生物（{@code #minecraft:undead}）→ {@code pve} <b>与</b> {@code undead} 都算。
 *       「对怪物伤害」覆盖所有非玩家目标，「对亡灵伤害」是它的子集加成 —— 与上游语义一致，
 *       也保证同一件装备换目标时只有增减、不会突然整段失效。</li>
 *   <li>其它非玩家生物 → 只有 {@code pve}。</li>
 * </ul>
 *
 * <p>{@link #keysFor(boolean, boolean)} 是纯函数（{@code gradle selftest} 直接测），
 * {@link #of(LivingEntity)} 只做类型与 tag 的取值，不含判断 —— 这样分类逻辑可以离线验证。
 */
public final class TargetClass {

    public static final String PVP = "pvp";
    public static final String PVE = "pve";
    public static final String UNDEAD = "undead";

    /** 已知键，解析配置时用来挡错别字（写错键 = 静默不生效，比报错更难查）。 */
    public static final List<String> KEYS = List.of(PVP, PVE, UNDEAD);

    private TargetClass() {
    }

    /**
     * 纯分类：给「是不是玩家」「是不是亡灵」两个事实，返回命中的条件键。
     *
     * @return 一或两个键；顺序固定（先 pve 后 undead），便于日志对账
     */
    public static List<String> keysFor(boolean isPlayer, boolean isUndead) {
        if (isPlayer) {
            return List.of(PVP);
        }
        return isUndead ? List.of(PVE, UNDEAD) : List.of(PVE);
    }

    /** 目标为 {@code null}（理论上不会）时返回空表 = 条件乘区不生效，绝不 NPE。 */
    public static List<String> of(LivingEntity target) {
        if (target == null) {
            return List.of();
        }
        return keysFor(target instanceof Player, target.getType().is(EntityTypeTags.UNDEAD));
    }
}
