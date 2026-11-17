package com.redtale.project.mobs;

import com.redtale.project.RedtaleProject;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.RedtaleAttributes;
import com.redtale.project.core.math.StatValue;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 怪物生成器（M6 第三步第 2 小步）：把一条 {@link MobSpec.Definition} 落到世界里。
 *
 * <p>唯一职责 = 「配置 → 实体」，四件事按固定顺序：
 * 属性（临时 modifier，id 固定 {@code mob/<怪id>/<属性path>}，可重复生成不叠加）→
 * 体积（{@code generic.scale} 基值）→ 六槽装备 → 名称 / 标签 / 持久化 → 落地。
 *
 * <p>为什么不 {@code setBaseValue}：基值是出厂值，改了就看不出「谁给的」（D18 的可拆解性）。
 * 内容数值一律走 {@code ADD_VALUE} 临时 modifier，{@code /redtale attr} 能按 id 分组显示；
 * 重生成同一只怪只会覆盖同一 id，不会翻倍。
 *
 * <p>本类不做：技能释放（M6 第 4 步）、刷怪替换（M6 第 5 步）、AI 差异（{@link RedtaleMob} v1 共用原版）。
 */
public final class MobSpawner {

    /** 生成结果；{@code skips} 是「配置里有、但落不下去」的项（未知属性 / 实体没有该属性 / 未知物品 id）。 */
    public record Result(boolean ok, String message, Mob mob, List<String> skips) {}

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SLOT_NAMES = {"mainhand", "offhand", "head", "chest", "legs", "feet"};
    /** 标签前缀：{@code redtale_mob/<怪id>}，scoreboard tag 不含空格即可。 */
    public static final String TAG_PREFIX = "redtale_mob/";

    private MobSpawner() {
    }

    /** 按定义在世界坐标 {@code (x,y,z)} 生成一只；失败返回 {@code ok=false} + 原因，不抛异常。 */
    public static Result spawn(ServerLevel level, double x, double y, double z, MobSpec.Definition def) {
        if (level == null || def == null) {
            return new Result(false, "缺少世界或怪物定义", null, List.of());
        }
        EntityType<RedtaleMob> type = RedtaleEntities.byBase(def.base());
        if (type == null) {
            return new Result(false, "原型 " + def.base() + " 没有注册的实体类型", null, List.of());
        }
        Mob mob = type.create(level);
        if (mob == null) {
            return new Result(false, "实体类型 " + def.base() + " 创建失败（create 返回 null）", null, List.of());
        }
        mob.moveTo(x, y, z, mob.getYRot(), mob.getXRot());

        List<String> skips = new ArrayList<>();
        int attrs = applyAttributes(mob, def, skips);
        double scale = applyScale(mob, def.scale());
        int gear = applyEquipment(mob, def.equipment(), skips);

        mob.setCustomName(Component.literal(def.name()));
        mob.setCustomNameVisible(true);
        mob.addTag(TAG_PREFIX + def.id());
        mob.setPersistenceRequired();

        if (!level.addFreshEntity(mob)) {
            return new Result(false, "生成被世界拒绝（addFreshEntity 返回 false）：" + def.id(), null, skips);
        }
        // 血量最后补：max_health 的 modifier 是上面加的，这里才能读到新上限
        mob.setHealth(mob.getMaxHealth());

        String msg = "已生成 " + def.name() + " " + def.id() + "（原型 " + def.base()
                + " · 等级 " + def.level() + " · 属性 " + attrs + " 条 · 装备 " + gear + " 件"
                + " · 体积 ×" + String.format("%.2f", scale) + " · " + (int) mob.getHealth()
                + "/" + (int) mob.getMaxHealth() + " 血）";
        return new Result(true, msg, mob, List.copyOf(skips));
    }

    /** 属性逐条挂临时 modifier；未知键 / 实体没有该属性 → 记 skip 不崩。返回成功条数。 */
    private static int applyAttributes(Mob mob, MobSpec.Definition def, List<String> skips) {
        int applied = 0;
        for (Map.Entry<String, StatValue> e : def.attributes().entrySet()) {
            AttributeSpec spec = AttributeSpecs.resolve(e.getKey());
            if (spec == null) {
                skips.add("未知属性 " + e.getKey());
                continue;
            }
            Holder<Attribute> holder = RedtaleAttributes.holder(spec);
            if (holder == null) {
                skips.add("属性 " + spec.path() + " 没有注册");
                continue;
            }
            AttributeInstance instance = mob.getAttribute(holder);
            if (instance == null) {
                skips.add("该实体没有属性 " + spec.path());
                continue;
            }
            double amount = e.getValue().valueAt(def.level());
            AttributeModifier modifier = new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID,
                            "mob/" + def.id() + "/" + spec.path()),
                    amount, AttributeModifier.Operation.ADD_VALUE);
            instance.addOrUpdateTransientModifier(modifier);
            applied++;
        }
        return applied;
    }

    /**
     * 体积走 {@code minecraft:generic.scale} 的**基值**（这是 D4 指定的唯一手段，不是内容数值）。
     * 值由 {@link MobSpec} 解析期夹到 {@code [0.25,4.0]}，这里不再夹；{@code 1.0} 不写，保持出厂干净。
     */
    private static double applyScale(Mob mob, double scale) {
        AttributeInstance instance = mob.getAttribute(Attributes.SCALE);
        if (instance == null || Math.abs(scale - 1.0D) < 1.0E-9D) {
            return 1.0D;
        }
        instance.setBaseValue(scale);
        return scale;
    }

    /** 六槽装备；物品 id 必须是注册表里的**物品 id**（内容 id 不是物品 id）。返回穿上件数。 */
    private static int applyEquipment(Mob mob, MobSpec.Equipment equipment, List<String> skips) {
        if (equipment == null) {
            return 0;
        }
        int worn = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            String id = equipment.slot(SLOT_NAMES[i]);
            if (id.isEmpty()) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Item item = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == Items.AIR) {
                skips.add("装备 " + SLOT_NAMES[i] + " 的物品 id 未知：" + id);
                continue;
            }
            mob.setItemSlot(SLOTS[i], new ItemStack(item));
            worn++;
        }
        return worn;
    }
}
