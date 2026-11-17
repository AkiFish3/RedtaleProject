package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;

/**
 * {@code pull} 原语：把目标**拉向**施法者（SKILLS §4 第 6 条，M5-C）。
 *
 * <p>与 {@link KnockbackEffect} 唯一的差别是方向取反 + <b>缺省半径 6</b>（拉扯的语义就是「把一圈人拽过来」，
 * 单体拉扯用 {@code radius: 0} 显式写）。位移计算、抗性打折、{@code hurtMarked} 同步全部复用
 * {@link KnockbackEffect#shove(ScriptContext, JsonObject, double, boolean)} 一份实现（R32-d 口径）。
 *
 * <p>参数：{@code strength}（缺省 {@code 0.4}，{@code <=0} 跳过）· {@code radius}（缺省 {@code 6}，上限 32）·
 * {@code radius: 0} = 只拉受害者。
 *
 * <p>玩家侧不需要额外判断「no_knockback」：原版 {@link net.minecraft.world.entity.LivingEntity#knockback}
 * 内部按 {@code Attributes.KNOCKBACK_RESISTANCE} 打折，抗性 1.0 的目标速度<b>逐位不变</b>（日志里记 {@code 推动 0/n}）。
 */
public final class PullEffect implements EffectType {

    @Override
    public String id() {
        return "pull";
    }

    @Override
    public String description() {
        return "args.strength：拉扯强度（缺省 0.4，<=0 跳过）；"
                + "args.radius：半径（缺省 6 = 把圈心内活体拽向施法者，上限 32；0 = 只拉受害者）；"
                + "方向 = 目标 → 施法者，走原版 knockback（吃击退抗性）";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        KnockbackEffect.shove(ctx, args, 6.0D, true);
    }
}
