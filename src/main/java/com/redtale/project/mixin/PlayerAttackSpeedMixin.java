package com.redtale.project.mixin;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttackSpeedRules;
import com.redtale.project.core.math.AttackSpeedCurve;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 攻速的唯一注入点（D15 / D19）：{@code Player#getCurrentItemAttackStrengthDelay()}。
 *
 * <p>为什么只改这一个方法就够：
 * <ul>
 *   <li>原版 {@code getAttackCooldownProgress} / {@code getAttackStrengthScale} 都从它取 delay；</li>
 *   <li>BetterCombat 的 {@code PlayerAttackHelper#getAttackCooldownTicksCapped} 第一句就是调它，
 *       然后才套自己的 {@code attack_interval_cap}（实测字节码），所以它自动继承我们的结果，
 *       <b>不需要第二套代码</b>，也不需要 import 它；</li>
 *   <li>SimplySwords / B&amp;C 之类的「攻速」最终也都是写 {@code ATTACK_SPEED} 属性，
 *       已经在属性值里，本 mixin 只是负责「饱和 + 地板」这层。</li>
 * </ul>
 *
 * <p>三条安全规则：
 * <ol>
 *   <li><b>默认路径零改动</b>：{@code soft_cap = 0} 且原 delay 已经 ≥ 地板时直接 return，
 *       连浮点重算都不做，保证与原版逐位一致（避免 12.5f 变 12.499999f 这种噪声）。</li>
 *   <li>整个方法体包 try/catch：攻速算错最多退回原版，绝不让玩家因为我们的异常掉线。</li>
 *   <li>地板永远存在（≥ {@link AttackSpeedCurve#ABSOLUTE_MIN_DELAY}）：delay = 0 会让
 *       {@code getAttackCooldownProgress} 除零 → 每 tick 出手。这是防 NaN，不是平衡阀（D19）。</li>
 * </ol>
 */
@Mixin(Player.class)
public abstract class PlayerAttackSpeedMixin {

    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"), cancellable = true)
    private void redtale$applyAttackSpeedRules(CallbackInfoReturnable<Float> cir) {
        try {
            AttackSpeedRules rules = RedtaleCore.get().attackSpeedRules();
            float original = cir.getReturnValueF();
            double floor = Math.max(rules.minDelayTicks(), AttackSpeedCurve.ABSOLUTE_MIN_DELAY);

            // 默认路径：不饱和 + 原值已在地板之上 → 原样放行（一次浮点运算都不做）
            if (rules.softCap() <= 0 && original >= floor && Float.isFinite(original)) {
                return;
            }
            double adjusted = AttackSpeedCurve.apply(original, rules.softCap(), rules.minDelayTicks());
            if (adjusted != original) {
                cir.setReturnValue((float) adjusted);
            }
        } catch (Throwable ignored) {
            // 攻速处理失败不该影响游戏：保持原版返回值
        }
    }
}
