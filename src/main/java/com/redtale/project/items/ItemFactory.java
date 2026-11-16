package com.redtale.project.items;

import com.redtale.project.RedtaleProject;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.RedtaleAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把一份 {@link ItemContent} 变成一件**可发放的物品**（M2 的核心，D21 白板化的落点）。
 *
 * <p>白板化的机制在这里体现为一行：{@code stack.set(ATTRIBUTE_MODIFIERS, 非空列表)}。
 * 1.21.1 的 {@code ItemStack#forEachModifier} 在列表**非空**时只用组件、跳过
 * {@code item.getDefaultAttributeModifiers()}，所以皮革甲的 3 点护甲被整段跳过，
 * 写 20 就是 20（证据见 {@code docs/ITEM-MODEL.md} §1.2，本轮再实测一次）。
 *
 * <p>坑（同文档 §1.2 的警告）：判据是「列表非空」不是「组件存在」。
 * 所以 0 属性的装备必须塞一个 {@code +0} 占位，否则回落到材质数值。
 */
public final class ItemFactory {

    /**
     * BetterCombat 的「武器动作预设」组件 id（**运行期按字符串查**，不 import 它，见 D15）。
     *
     * <p>证据（`bettercombat-neoforge-2.4.0+1.21.1.jar` 字节码实读）：
     * `BetterCombatDataComponents` 静态块里 `ResourceLocation.fromNamespaceAndPath("bettercombat","preset_id")`
     * 注册了一个 `DataComponentType<ResourceLocation>`；`WeaponRegistry#getAttributes(ItemStack)`
     * 第一步就是 `stack.get(WEAPON_PRESET_ID)`，拿不到才回落到按物品注册 id 查
     * —— 所以「默认武器动作」拆成两件事：数据包给定义，组件给引用。只做前者等于没做。
     */
    public static final ResourceLocation PRESET_ID_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("bettercombat", "preset_id");

    private ItemFactory() {
    }

    /**
     * 把内容里的 {@code weapon_attributes} 挂成物品组件（R7-1）。
     *
     * <p>**任何命名空间都挂**：`bettercombat:claymore` 这类是别人 jar 里已经存在的预设，
     * 数据包那份文件归别人管（生成器跳过它），但物品要引用它就只能靠这个组件
     * —— 组件值本身就是那个 id，引用不等于覆盖。
     *
     * <p>BetterCombat 不在线时 `BuiltInRegistries` 里没有这个组件 → 静默返回 false，不报错（D15「不作前置」）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean applyWeaponPreset(ItemStack stack, String ref) {
        if (ref == null || ref.isEmpty()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(ref);
        if (id == null) {
            return false;
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(PRESET_ID_COMPONENT);
        if (type == null) {
            return false;
        }
        stack.set((DataComponentType) type, id);
        return true;
    }

    /** 读回预设组件（验收与 tooltip 用）；没装 BetterCombat 或没设过 → null。 */
    public static ResourceLocation weaponPresetOf(ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(PRESET_ID_COMPONENT);
        if (type == null) {
            return null;
        }
        Object value = stack.get(type);
        return value instanceof ResourceLocation id ? id : null;
    }

    /** 建一件满耐久、按内容定义白板化的物品（词条现摇，见 {@link #build(ItemContent, java.util.Random)}）。 */
    public static ItemStack build(ItemContent content) {
        return build(content, new java.util.Random());
    }

    /**
     * 物品等级。**物品等级系统尚未接**（M3 等级/经验那批），先固定 1：
     * 词条的 {@code per_item_level} 在等级 1 上贡献一次，行为可预测，接上之后只改这一个常量。
     */
    public static final int ITEM_LEVEL = 1;

    /**
     * 建栈 + **摇一次词条**（D22 第二层，R11）。
     *
     * <p>roll 的落点是「生成那一刻」，所以同一份定义 `/redtale give` 两次数值可以不同 ——
     * 这是设计（{@code ITEM-MODEL.md} §2「生成时机 = 掉落时」），不是 bug。
     * 结果三样一起写进 {@code ContentData}：数值并进 {@code rolledStats}、
     * 键记进 {@code rolledKeys}（D33 迁移不重摇）、id 记进 {@code affixes}（tooltip 与重铸读它）。
     *
     * @param random 随机源；{@code null} = 不摇词条（验收 / 定值场景用）
     */
    public static ItemStack build(ItemContent content, java.util.Random random) {
        Item item = RedtaleItems.item(content.base());
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        Map<String, Double> stats = new java.util.LinkedHashMap<>(content.stats());
        AffixRoller.Result rolled = rollAffixes(content, random);
        for (Map.Entry<String, Double> e : rolled.stats().entrySet()) {
            stats.merge(e.getKey(), e.getValue(), Double::sum);
        }
        stack.set(ContentComponents.CONTENT.get(), new ContentData(
                content.id(), content.rev(), ContentData.CURRENT_SCHEMA,
                // M3 套装归属：定义型字段，随 rev 一起写（改 set_id 必须 rev+1，否则存量物品不跟）
                content.setId(), 0, stats, List.copyOf(rolled.stats().keySet()), rolled.affixIds(), List.of(),
                content.weaponAttributes(),
                // M3（§5.5 第 4 步）：按定义布局填空孔 —— 孔数是**定义型**的，
                // 新发放就必须与定义对齐，否则玩家拿到一件「有孔位但孔不存在」的物品。
                blankSockets(content.sockets().count()), Map.of()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(content.name()));
        stack.set(DataComponents.RARITY, rarity(content.rarity()));
        if (content.durability() > 0) {
            stack.set(DataComponents.MAX_DAMAGE, content.durability());
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers(content, stats));
        applyWeaponPreset(stack, content.weaponAttributes());
        return stack;
    }

    /** 按基底挑池并摇词条；没配词条 / 没有随机源 → 空结果（不告警）。 */
    public static AffixRoller.Result rollAffixes(ItemContent content, java.util.Random random) {
        if (content == null || random == null) {
            return AffixRoller.Result.EMPTY;
        }
        var core = com.redtale.project.core.RedtaleCore.get();
        AffixPools.Pool pool = AffixRoller.poolFor(core.affixPools().values(), content.base().id());
        return AffixRoller.roll(pool, core.affixDefs(), ITEM_LEVEL, random);
    }

    /**
     * 内容数值 → {@code ATTRIBUTE_MODIFIERS} 组件。
     *
     * <p>所有条目都是 {@code ADD_VALUE}（见 {@link StatFormat} 的说明：自建属性基值 0，
     * 乘算永远是 0）。唯一例外是 {@code attack_speed}：内容里写的是**目标次/秒**，
     * 这里换算成「相对原版 4.0 的差值」，与原版剑的 {@code -2.4} 同一种形态。
     */
    public static ItemAttributeModifiers modifiers(ItemContent content) {
        return modifiers(content, content.stats());
    }

    /**
     * 同 {@link #modifiers(ItemContent)}，但数值取 {@code stats} 而不是定义里的。
     *
     * <p>给物品更新系统用（D33）：重算一件老物品时，属性集合以**新定义**为准、
     * 数值以**这件实例的 {@code rolled_stats}** 为准，两者合并后传进来。
     * 加这个重载而不是让调用方自己拼 {@code ItemContent}，是因为拼 record 会漏字段。
     */
    public static ItemAttributeModifiers modifiers(ItemContent content, Map<String, Double> stats) {
        return modifiers(content, stats, Map.of());
    }

    /**
     * 面板 = 内容数值 + **宝石数值**（M3）。两张表分开存（{@code ContentData.gemStats}），
     * 但**必须并进同一个 {@code ATTRIBUTE_MODIFIERS} 组件**，否则宝石不产生任何效果。
     *
     * <p>关键的坑：两部分的 modifier id **必须不同**（{@code content/<属性>} vs {@code gem/<属性>}）。
     * 同一个 id 在 {@code AttributeMap} 里是「后加覆盖先加」，词条与宝石会互相顶掉一个 ——
     * 数值凭空少一块，且只在「同一属性既有词条又有宝石」时出现，极难复现。
     */
    public static ItemAttributeModifiers modifiers(ItemContent content, Map<String, Double> stats,
                                                   Map<String, Double> gemStats) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        addAll(builder, content, stats, "content/");
        addAll(builder, content, gemStats, "gem/");
        ItemAttributeModifiers built = builder.build();
        if (!built.modifiers().isEmpty()) {
            return built;
        }
        // 0 属性装备：显式 +0 占位，否则会回落到底材（D21 §1.2 的坑）
        Holder<Attribute> placeholder = content.base().isArmor() ? Attributes.ARMOR : Attributes.ATTACK_DAMAGE;
        return ItemAttributeModifiers.builder()
                .add(placeholder, new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, "content/placeholder"),
                        0.0, AttributeModifier.Operation.ADD_VALUE), content.base().slot())
                .build();
    }

    /** 把一张「属性 → 平加值」表按给定 id 前缀灌进 builder（内容用 {@code content/}，宝石用 {@code gem/}）。 */
    private static void addAll(ItemAttributeModifiers.Builder builder, ItemContent content,
                               Map<String, Double> table, String idPrefix) {
        for (Map.Entry<String, Double> e : table.entrySet()) {
            AttributeSpec spec = AttributeSpecs.resolve(e.getKey());
            Holder<Attribute> holder = RedtaleAttributes.holder(spec);
            if (holder == null) {
                continue;
            }
            double amount = e.getValue();
            if (isAttackSpeed(spec)) {
                amount = e.getValue() - holder.value().getDefaultValue();
            }
            builder.add(holder, new AttributeModifier(
                            ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, idPrefix + spec.path()),
                            amount, AttributeModifier.Operation.ADD_VALUE),
                    content.base().slot());
        }
    }

    /** {@code count} 个空孔（{@code ""} = 空）；孔序长度恒等于定义里的孔数。 */
    public static List<String> blankSockets(int count) {
        if (count <= 0) {
            return List.of();
        }
        String[] slots = new String[count];
        java.util.Arrays.fill(slots, "");
        return List.copyOf(java.util.Arrays.asList(slots));
    }

    /**
     * 把孔序与宝石数值写回物品，并按「内容数值 + 宝石数值」重算面板（M3 唯一的宝石写回点）。
     *
     * <p>只碰两个东西：{@code ContentData}（孔序 + gemStats）与 {@code ATTRIBUTE_MODIFIERS}。
     * 名称 / 稀有度 / 耐久 / 预设一个都不动 —— 那属于 D33 的 {@link #applyDefinition}，
     * 镶一颗宝石不该顺手把玩家看到的其它东西重写一遍。
     */
    public static void applySockets(net.minecraft.world.item.ItemStack stack, ItemContent content,
                                    ContentData data) {
        stack.set(ContentComponents.CONTENT.get(), data);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                modifiers(content, data.rolledStats(), data.gemStats()));
    }

    public static boolean isAttackSpeed(AttributeSpec spec) {
        return spec != null && "minecraft:generic.attack_speed".equals(spec.id());
    }

    /**
     * 把「定义型字段」整套重写到一件**已有**物品上（D33 物品更新系统的唯一实现点）。
     *
     * <p>只碰这五样：`CUSTOM_NAME` / `RARITY` / `MAX_DAMAGE` / `ATTRIBUTE_MODIFIERS` / `preset_id`。
     * <b>不碰 {@code ContentData} 本身</b>——`rolled_stats` / `affixes` / `abilities` / `upgrade_level`
     * 是玩家资产，由调用方决定怎么合并；这里只负责「按给定数值把面板重算一遍」。
     *
     * <p>{@code effectiveStats} 一般是「新定义的属性集合 ∩ 这件实例保留的数值」，由
     * {@code ItemUpgrader} 算好传进来。耐久上限下调时会把当前损伤夹到合法区间，不炸物品。
     *
     * @return 是否真的写了东西（空栈返回 {@code false}）
     */
    public static boolean applyDefinition(ItemStack stack, ItemContent content,
                                          Map<String, Double> effectiveStats, List<String> warnings) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(content.name()));
        stack.set(DataComponents.RARITY, rarity(content.rarity()));
        if (content.durability() > 0) {
            stack.set(DataComponents.MAX_DAMAGE, content.durability());
            int damage = stack.getDamageValue();
            if (damage >= content.durability()) {
                stack.setDamageValue(content.durability() - 1);
                warnings.add("物品更新：" + content.id() + " 耐久上限下调，损伤已夹到 "
                        + (content.durability() - 1));
            }
        } else {
            // 定义改成「不可损坏」时要移除组件，否则旧物品仍按老上限磨损
            stack.remove(DataComponents.MAX_DAMAGE);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers(content, effectiveStats));
        applyWeaponPreset(stack, content.weaponAttributes());
        return true;
    }

    /** modifier id 固定：{@code redtale_project:content/<属性路径>}，同一件物品不随 reload 变化。 */
    public static ResourceLocation modifierId(AttributeSpec spec) {
        return ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, "content/" + spec.path());
    }

    public static Rarity rarity(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }
}
