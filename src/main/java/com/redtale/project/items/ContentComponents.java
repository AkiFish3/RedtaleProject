package com.redtale.project.items;

import com.redtale.project.RedtaleProject;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 数据组件注册表。**整个模组只有这一个自定义组件类型**（D17）。
 *
 * <p>加第二个组件类型 = 换 jar。真需要新字段时先往 {@link ContentData} 里加，
 * 旧物品缺字段用默认值补齐，比注册新组件便宜得多。
 */
public final class ContentComponents {

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RedtaleProject.MOD_ID);

    /** {@code redtale_project:content} —— 所有内容的身份与数值都挂在这里。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContentData>> CONTENT =
            REGISTRY.register("content", () -> DataComponentType.<ContentData>builder()
                    .persistent(ContentData.CODEC)
                    .networkSynchronized(ContentData.STREAM_CODEC)
                    .build());

    private ContentComponents() {
    }

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
