package com.redtale.project.items;

import com.redtale.project.RedtaleProject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 14 个基底物品的注册（{@code redtale_project:sword} … {@code redtale_project:amulet}）。
 *
 * <p>三条刻意的选择：
 * <ol>
 *   <li><b>护甲用 {@code ArmorItem}，武器用普通 {@code Item}</b>。前者提供「右键穿上 / 装备槽 /
 *       附魔台可附」这些**行为**，后者我们不需要任何类级行为（伤害全走属性）。
 *       数值一律不进类，进 {@code ContentData} 算出来的 {@code ATTRIBUTE_MODIFIERS} 组件（D21）。</li>
 *   <li><b>护甲材质统一 {@code LEATHER}</b>：材质只决定「外观染色 / 修复材料 / 默认数值」，
 *       而默认数值会被白板化整段跳过。用皮革而不是钻石，是为了万一白板化失效时
 *       退化成「很弱的甲」而不是「凭空多 8 点护甲」——失效方向要朝安全那边歪。</li>
 *   <li><b>注册期不设 durability</b>：耐久是**每件内容**不同的，写在 {@code Item.Properties}
 *       就等于「一个基底一种耐久」。改成建栈时写 {@code MAX_DAMAGE} 组件，一件一个值。</li>
 * </ol>
 */
public final class RedtaleItems {

    public static final DeferredRegister<Item> REGISTRY =
            DeferredRegister.create(Registries.ITEM, RedtaleProject.MOD_ID);

    private static final Map<ItemBase, DeferredHolder<Item, Item>> BY_BASE;

    static {
        Map<ItemBase, DeferredHolder<Item, Item>> map = new LinkedHashMap<>();
        for (ItemBase base : ItemBase.values()) {
            map.put(base, REGISTRY.register(base.id(), () -> create(base)));
        }
        BY_BASE = Collections.unmodifiableMap(map);
    }

    private RedtaleItems() {
    }

    private static Item create(ItemBase base) {
        Item.Properties props = new Item.Properties().stacksTo(1);
        if (base.isArmor()) {
            return new ArmorItem(ArmorMaterials.LEATHER, base.armorType(), props);
        }
        // 非护甲基底一律用 ContentItem：只有它带「右键 = 用消耗品」这一个行为（D23）
        return new ContentItem(props);
    }

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }

    /** 基底对应的已注册物品；注册表未就绪时返回 {@code null}（reload 早期调用要判空）。 */
    public static Item item(ItemBase base) {
        DeferredHolder<Item, Item> holder = BY_BASE.get(base);
        return holder == null ? null : holder.get();
    }

    public static int count() {
        return BY_BASE.size();
    }
}
