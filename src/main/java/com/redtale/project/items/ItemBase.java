package com.redtale.project.items;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ArmorItem;

/**
 * 14 个基底物品类型（HOT-UPDATE §2 的「基底物品类型」容器）。
 *
 * <p>这 14 个是 {@code ITEM} 注册表项，启动期冻结 → **加第 15 种要换 jar**。
 * 之所以敢定 14 个：内容身份在 {@code ContentData} 里，基底只决定「外观属性 / 耐久上限口径 /
 * 能挂到哪个装备槽」。剑与匕首的区别不在类，在配置数值与 {@code weapon_attributes}。
 *
 * <p>槽位（{@code EquipmentSlotGroup}）决定属性 modifier 在哪个槽生效 —— 这是白板化之后
 * 「穿上才有」的唯一来源，所以它必须跟着基底走，不能由内容乱填。
 */
public enum ItemBase {

    SWORD("sword", Kind.WEAPON, EquipmentSlotGroup.MAINHAND, null, 1200),
    DAGGER("dagger", Kind.WEAPON, EquipmentSlotGroup.MAINHAND, null, 800),
    AXE("axe", Kind.WEAPON, EquipmentSlotGroup.MAINHAND, null, 1100),
    HAMMER("hammer", Kind.WEAPON, EquipmentSlotGroup.MAINHAND, null, 1600),
    SPEAR("spear", Kind.WEAPON, EquipmentSlotGroup.MAINHAND, null, 1000),
    STAFF("staff", Kind.WEAPON, EquipmentSlotGroup.MAINHAND, null, 900),
    BOW("bow", Kind.WEAPON, EquipmentSlotGroup.MAINHAND, null, 700),
    SHIELD("shield", Kind.WEAPON, EquipmentSlotGroup.OFFHAND, null, 1500),

    HELMET("helmet", Kind.ARMOR, EquipmentSlotGroup.HEAD, ArmorItem.Type.HELMET, 400),
    CHESTPLATE("chestplate", Kind.ARMOR, EquipmentSlotGroup.CHEST, ArmorItem.Type.CHESTPLATE, 600),
    LEGGINGS("leggings", Kind.ARMOR, EquipmentSlotGroup.LEGS, ArmorItem.Type.LEGGINGS, 550),
    BOOTS("boots", Kind.ARMOR, EquipmentSlotGroup.FEET, ArmorItem.Type.BOOTS, 450),

    /**
     * 饰品：M3 接 Curios 前只在主手生效 —— 故意不用 {@code ANY}，
     * 否则「放背包就给属性」会变成一个必须回收的数值事故（D29 的 Curios 槽位是后面的事）。
     */
    RING("ring", Kind.ACCESSORY, EquipmentSlotGroup.MAINHAND, null, 0),
    AMULET("amulet", Kind.ACCESSORY, EquipmentSlotGroup.MAINHAND, null, 0);

    public enum Kind {
        WEAPON, ARMOR, ACCESSORY
    }

    private final String id;
    private final Kind kind;
    private final EquipmentSlotGroup slot;
    private final ArmorItem.Type armorType;
    private final int defaultDurability;

    ItemBase(String id, Kind kind, EquipmentSlotGroup slot, ArmorItem.Type armorType, int defaultDurability) {
        this.id = id;
        this.kind = kind;
        this.slot = slot;
        this.armorType = armorType;
        this.defaultDurability = defaultDurability;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public EquipmentSlotGroup slot() {
        return slot;
    }

    /** 仅护甲基底非 null；决定用 {@code ArmorItem} 还是普通 {@code Item} 注册。 */
    public ArmorItem.Type armorType() {
        return armorType;
    }

    public boolean isArmor() {
        return armorType != null;
    }

    /** 内容没写 {@code durability} 时的默认值（0 = 不可损坏，例如饰品）。 */
    public int defaultDurability() {
        return defaultDurability;
    }

    public static ItemBase byId(String id) {
        for (ItemBase base : values()) {
            if (base.id.equalsIgnoreCase(id)) {
                return base;
            }
        }
        return null;
    }

    public static String allIds() {
        StringBuilder sb = new StringBuilder();
        for (ItemBase base : values()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(base.id);
        }
        return sb.toString();
    }
}
