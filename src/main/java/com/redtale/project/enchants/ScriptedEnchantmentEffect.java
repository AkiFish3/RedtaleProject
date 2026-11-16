package com.redtale.project.enchants;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.redtale.project.core.script.ScriptContext;
import com.redtale.project.core.script.ScriptEngine;
import com.redtale.project.core.RedtaleCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D32 的 L2 层：**唯一**自定义附魔实体效果 {@code redtale_project:scripted}。
 *
 * <p>为什么只有一个：{@code EnchantmentEntityEffect} 的每种实现都是一个注册表项，加字段要换 jar（F5）。
 * 所以这里不描述「做什么」，只做「什么时候把参数递给脚本层」——触发时机全交给原版 30 个组件
 * （{@code minecraft:post_attack} / {@code minecraft:hit_block} …），执行体全交给 L3 脚本层
 * （{@code scripts/*.json5}）。新增第 N 个脚本化附魔 = 写一份 JSON5，不编译。
 *
 * <p>JSON 形态（写在附魔 datapack 的 {@code effects} 里）：
 * <pre>{@code
 * { "type": "redtale_project:scripted",
 *   "script": "enchants/soul_reap",
 *   "args": { "heal_ratio": 0.08 } }
 * }</pre>
 *
 * <p>失败策略：脚本找不到只 WARN 一次（按 id 去重），不崩、不刷屏、不影响同级的原版 effect。
 * 这与 M0 脚本层的「单条失败不牵连其它」同口径。
 *
 * <p><b>{@code script} 是脚本 id 字符串，不是 {@code ResourceLocation}</b>（R22 实测修正）：
 * 写成 {@code ResourceLocation.CODEC} 时，裸 id {@code enchants/soul_reap} 会被补成
 * {@code minecraft:enchants/soul_reap}，和 {@code scripts/*.json5} 里的 {@code id} 永远对不上，
 * 表现是「附魔生效但执行体静默不跑」。这里用 {@code Codec.STRING} 原样透传。
 */
public record ScriptedEnchantmentEffect(String script, CompoundTag args)
        implements EnchantmentEntityEffect {

    /** 本组件在 {@link Registries#ENCHANTMENT_ENTITY_EFFECT_TYPE} 里的 id（固定，不热改）。 */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("redtale_project", "scripted");

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject");
    /** 已经报过「脚本不存在」的 id：只报一次，避免每次命中刷屏。 */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    public static final MapCodec<ScriptedEnchantmentEffect> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("script").forGetter(ScriptedEnchantmentEffect::script),
                    CompoundTag.CODEC.optionalFieldOf("args", new CompoundTag())
                            .forGetter(ScriptedEnchantmentEffect::args)
            ).apply(instance, ScriptedEnchantmentEffect::new));

    @Override
    public MapCodec<? extends EnchantmentEntityEffect> codec() {
        return CODEC;
    }

    @Override
    public void apply(ServerLevel level, int enchLevel, EnchantedItemInUse item,
                      Entity affected, Vec3 pos) {
        ScriptEngine engine = RedtaleCore.get().engine();
        if (engine == null) {
            return;
        }
        // R22-c：第 4 参是**受害者**（post_attack），施法者在 EnchantedItemInUse#owner()（LivingEntity）。
        // R24：两个都交给脚本层——affected 走 ctx.target()，施法者走 player()（是玩家时）或 vars.attacker。
        Entity caster = item.owner() != null ? item.owner() : affected;
        ScriptContext ctx = ScriptContext.ofEntity(caster, affected);
        ctx.vars().put("attacker", item.owner());
        ctx.vars().put("affected", affected);
        // args 走 NBT 而不是字符串拼接：脚本读到的就是数值，能直接参与表达式
        if (!args.isEmpty()) {
            for (String key : args.getAllKeys()) {
                Tag value = args.get(key);
                if (value != null) {
                    ctx.vars().put(key, nbtToJava(value));
                }
            }
        }
        ctx.vars().put("enchant_level", enchLevel);
        ctx.vars().put("enchant_item", item.itemStack());
        ctx.vars().put("enchant_pos", pos);
        try {
            if (!engine.fireById(script, ctx)) {
                if (REPORTED.add(script)) {
                    LOG.warn("附魔脚本 {} 不存在（附魔仍生效，只是这次触发没有执行体）", script);
                }
            }
        } catch (Exception e) {
            // 脚本抛异常不该打断原版伤害/事件链
            LOG.error("附魔脚本 {} 执行失败", script, e);
        }
    }

    /** NBT 标量 → Java 值；复合/列表原样留在 ctx 里，脚本层要自己认。 */
    private static Object nbtToJava(Tag tag) {
        return switch (tag.getId()) {
            case Tag.TAG_BYTE -> ((net.minecraft.nbt.NumericTag) tag).getAsByte();
            case Tag.TAG_SHORT -> ((net.minecraft.nbt.NumericTag) tag).getAsShort();
            case Tag.TAG_INT -> ((net.minecraft.nbt.NumericTag) tag).getAsInt();
            case Tag.TAG_LONG -> ((net.minecraft.nbt.NumericTag) tag).getAsLong();
            case Tag.TAG_FLOAT -> ((net.minecraft.nbt.NumericTag) tag).getAsFloat();
            case Tag.TAG_DOUBLE -> ((net.minecraft.nbt.NumericTag) tag).getAsDouble();
            case Tag.TAG_STRING -> tag.getAsString();
            default -> tag;
        };
    }

    /**
     * 注册进 {@link Registries#ENCHANTMENT_ENTITY_EFFECT_TYPE}。
     *
     * <p>1.21.1 的这张表是**内置表**（`BuiltInRegistries`），不是 `DeferredRegister` 的常规目标，
     * 所以走 {@link RegisterEvent}：注册发生在注册表加载期，晚于此就冻结。
     */
    public static void register(IEventBus modBus) {
        modBus.addListener(ScriptedEnchantmentEffect::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, ID, () -> CODEC);
    }
}
