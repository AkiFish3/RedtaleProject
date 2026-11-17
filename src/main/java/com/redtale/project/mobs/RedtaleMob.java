package com.redtale.project.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 8 个模板实体共用的类骨架（M6 第三步，PLAN §5）。
 *
 * <p>为什么先共用一个类：{@link EntityType} 的**注册项**是启动期冻结的（D17 基石约束 2），
 * 但「哪个类」只是代码。先把 8 个 id 占住，各行为了各自 AI 的差异再拆子类，id 不用动。
 *
 * <p>v1 行为骨架 = 原版 {@link Monster} 的默认一套（近战追击 + 目标选择），**不写自定义 AI**：
 * 技能、装备、属性、体积全部由 {@code mobs/*.json5} 在生成时套（同 PLAN §5 的可配项表）。
 * 内容侧的属性覆盖走 {@code AttributeMap}，不走 {@code createAttributes()}，因此 8 个原型
 * 共用<b>同一份</b>出厂属性表，避免「改一个原型顺手改了另一个」。
 */
public class RedtaleMob extends Monster {

    public RedtaleMob(EntityType<? extends RedtaleMob> type, Level level) {
        super(type, level);
    }

    /**
     * 出厂属性（**唯一一份**，8 个 EntityType 共用）。
     *
     * <p>数值只是「没有配置时的兜底」，真实值由 {@code MobSpec.attributes} 在生成时写进
     * {@code AttributeMap}（规则同装备：自建属性基值 0，内容只 {@code ADD_VALUE}）。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    /**
     * v1 不覆盖 {@code getMobType()}：1.21.1 的 {@code server-*-srg.jar} 里**没有** {@code MobType} 类
     * （编译期实证，javap 与 unzip 都查不到），而我们的亡灵判定本来也不吃它 ——
     * {@code TargetClass} 走的是 {@code #minecraft:undead} 实体标签（R6 口径），
     * 所以 8 个原型要算亡灵，得由 datapack 往该标签**追加** id（`replace:false`，同 R23 手法）。
     */

    /** v1 不注册任何自定义 goal：原版 {@link Monster} 的追击/目标选择就够了。 */
    @Override
    protected void registerGoals() {
        super.registerGoals();
    }
}
