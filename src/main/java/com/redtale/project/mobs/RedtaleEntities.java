package com.redtale.project.mobs;

import com.redtale.project.RedtaleProject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * 8 个模板 {@link EntityType}（M6 第三步第 1 小步）。
 *
 * <p>id 与 {@link MobSpec#BASES} 一一对应，因此 {@code mobs/*.json5} 的 {@code base} 字段
 * 直接就是实体 id 的末段：{@code redtale_project:melee} … {@code redtale_project:ambient}。
 * 这条对应关系是本类的**唯一契约**，新增原型必须同时改 {@code BASES}（换 jar，D17 记账）。
 *
 * <p>尺寸只是出厂值；真正按怪区分靠 {@code minecraft:generic.scale}（PLAN §5：
 * 1.20.5+ 该属性能缩放碰撞箱），所以这里不做「细高/扁宽」的特例。
 */
public final class RedtaleEntities {

    /** 表里每个原型：id、宽、高、客户端追踪距离（飞行原型要给远一点，否则客户端看不全）。 */
    private record Proto(String id, float width, float height, int tracking) {}

    private static final List<Proto> PROTOS = List.of(
            new Proto("melee", 0.6F, 1.95F, 8),
            new Proto("ranged", 0.6F, 1.95F, 8),
            new Proto("caster", 0.6F, 1.95F, 8),
            new Proto("summoner", 0.6F, 1.95F, 8),
            new Proto("charger", 0.9F, 1.4F, 8),
            new Proto("flyer", 0.7F, 1.2F, 10),
            new Proto("elite", 0.8F, 2.2F, 10),
            new Proto("ambient", 0.5F, 1.0F, 8));

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RedtaleProject.MOD_ID);

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<RedtaleMob>>> ALL =
            register();

    private RedtaleEntities() {}

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<RedtaleMob>>> register() {
        java.util.LinkedHashMap<String, DeferredHolder<EntityType<?>, EntityType<RedtaleMob>>> out =
                new java.util.LinkedHashMap<>();
        for (Proto p : PROTOS) {
            out.put(p.id(), TYPES.register(p.id(), () -> EntityType.Builder
                    .of(RedtaleMob::new, MobCategory.MONSTER)
                    .sized(p.width(), p.height())
                    .clientTrackingRange(p.tracking())
                    .build(RedtaleProject.MOD_ID + ":" + p.id())));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** mod 构造期调用：注册 8 个 EntityType。 */
    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** {@link EntityAttributeCreationEvent} 是 mod bus 事件：每个 EntityType 必须有一份属性表，否则生成即崩。 */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (DeferredHolder<EntityType<?>, EntityType<RedtaleMob>> holder : ALL.values()) {
            event.put(holder.get(), RedtaleMob.createAttributes().build());
        }
    }

    /** 按原型 id 取实体类型（{@code base} 字段用），未知 id 返回 null。 */
    public static EntityType<RedtaleMob> byBase(String base) {
        DeferredHolder<EntityType<?>, EntityType<RedtaleMob>> h = ALL.get(base);
        return h == null ? null : h.get();
    }

    /** 已注册的原型 id（配置顺序），给命令与校验用。 */
    public static List<String> baseIds() {
        return List.copyOf(ALL.keySet());
    }
}
