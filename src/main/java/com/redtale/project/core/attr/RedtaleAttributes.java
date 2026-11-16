package com.redtale.project.core.attr;

import com.redtale.project.RedtaleProject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 属性注册（M1）。66 条自建属性全部落在 {@code redtale_project:} 下，零 mod 依赖（D12）。
 *
 * <p>三条必须知道的事实：
 * <ol>
 *   <li><b>属性是启动期冻结的注册表项</b>：这份表一旦发版就不能加、不能改名（改名 = 换 ID =
 *       存档不兼容）。所以一次注册 80 条（66 自建 + 14 原版引用 + 16 预留），见
 *       {@code docs/ATTRIBUTE-REGISTRY.md}。</li>
 *   <li><b>注册了不等于实体有</b>：{@link Attribute} 进 registry 只是有了类型；
 *       玩家/怪物身上要出现对应的 {@code AttributeInstance}，必须走
 *       {@link EntityAttributeModificationEvent}。少了这一步，{@code /attribute} 会报
 *       「该实体没有此属性」，{@code getAttribute()} 返回 {@code null}。</li>
 *   <li><b>{@code setSyncable(true)} 必须显式调</b>：1.21.1 的 {@code Attribute.syncable}
 *       字段默认 {@code false}（已用 21.1.257 字节码核对），不调就不会同步到客户端，
 *       tooltip 拿到的是过期值。</li>
 * </ol>
 */
public final class RedtaleAttributes {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Attr");

    public static final DeferredRegister<Attribute> REGISTRY =
            DeferredRegister.create(Registries.ATTRIBUTE, RedtaleProject.MOD_ID);

    /** 自建属性：注册顺序 = 表顺序（{@link LinkedHashMap} 保序，日志与 list 才对得上）。 */
    private static final Map<String, DeferredHolder<Attribute, Attribute>> CUSTOM;

    static {
        Map<String, DeferredHolder<Attribute, Attribute>> map = new LinkedHashMap<>();
        for (AttributeSpec spec : AttributeSpecs.ALL) {
            if (spec.vanilla()) {
                continue;
            }
            String key = spec.translationKey();
            map.put(spec.id(), REGISTRY.register(spec.path(), () -> new RangedAttribute(
                    key, spec.base(), AttributeSpec.MIN, AttributeSpec.MAX).setSyncable(true)));
        }
        CUSTOM = Collections.unmodifiableMap(map);
    }

    private RedtaleAttributes() {
    }

    /** 在 {@code @Mod} 构造器里调一次。 */
    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }

    /** 日志用：本 mod 注册了几条、预留几条。 */
    public static String describe() {
        long reserved = AttributeSpecs.ALL.stream().filter(AttributeSpec::reserved).count();
        return CUSTOM.size() + " 条自建（含预留 " + reserved + "） + "
                + AttributeSpecs.vanilla().size() + " 条原版引用 = " + AttributeSpecs.ALL.size();
    }

    /**
     * 把这些属性挂到所有活体实体上，否则 {@code /attribute} 查不到、{@code getAttribute()} 为 null。
     *
     * <p>为什么给**所有**类型而不是只给玩家：M6 的模板怪物要用同一批属性做数值，
     * 而 {@code EntityType} 的属性表在启动期就冻结了，与其 M6 再补一次，不如现在一次挂全。
     * 代价是每个活体多 66 个空 {@code AttributeInstance}（每个几十字节），可忽略。
     */
    public static void onEntityAttributes(EntityAttributeModificationEvent event) {
        int added = 0;
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            for (DeferredHolder<Attribute, Attribute> holder : CUSTOM.values()) {
                if (!event.has(type, holder)) {
                    event.add(type, holder);
                    added++;
                }
            }
        }
        LOG.info("属性挂载完成：{} 个实体类型 × {} 条自建属性（实际新增 {} 处）",
                event.getTypes().size(), CUSTOM.size(), added);
    }

    /** 自建属性的 Holder；不存在返回 null。 */
    public static Holder<Attribute> custom(String path) {
        DeferredHolder<Attribute, Attribute> holder = CUSTOM.get(path);
        return holder == null ? null : holder;
    }

    /**
     * 按 {@link AttributeSpec} 拿 Holder：自建走注册表，原版走 {@code BuiltInRegistries}。
     *
     * @return 找不到返回 null（原版 ID 写错时会是这样，调用方要判空）
     */
    public static Holder<Attribute> holder(AttributeSpec spec) {
        if (spec == null) {
            return null;
        }
        if (!spec.vanilla()) {
            return CUSTOM.get(spec.id());
        }
        ResourceLocation id = ResourceLocation.tryParse(spec.id());
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
    }

    /** 已注册的自建属性 id 列表（调试用）。 */
    public static List<String> customIds() {
        return List.copyOf(CUSTOM.keySet());
    }
}
