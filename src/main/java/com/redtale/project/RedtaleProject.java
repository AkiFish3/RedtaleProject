package com.redtale.project;

import com.redtale.project.core.attr.RedtaleAttributes;
import com.redtale.project.core.hook.GameHooks;
import com.redtale.project.items.ContentComponents;
import com.redtale.project.items.RedtaleItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

/**
 * RedtaleProject 入口。
 *
 * <p>M0：配置热载 + 脚本层三张注册表 + /redtale 命令。
 * <p>M1：80 条属性注册（66 自建）+ /redtale attr 来源拆解。
 * <p>装备、附魔、怪物、进度按源码集在 M2 之后逐步加入（PLAN §4）。
 */
@Mod(RedtaleProject.MOD_ID)
public final class RedtaleProject {

    public static final String MOD_ID = "redtale_project";

    public RedtaleProject(IEventBus modBus, ModContainer container) {
        // 注册表项必须在 mod 构造期绑定到 mod bus，晚了就冻结不进 registry
        RedtaleAttributes.register(modBus);
        // M2：唯一的数据组件 + 14 个基底物品（都是启动期冻结的注册表项）
        ContentComponents.register(modBus);
        RedtaleItems.register(modBus);
        // M4 / D32：唯一自定义附魔实体效果（内置表，走 RegisterEvent）
        com.redtale.project.enchants.ScriptedEnchantmentEffect.register(modBus);
        // M6 第三步：8 个模板 EntityType（id 与 MobSpec.BASES 一一对应）
        com.redtale.project.mobs.RedtaleEntities.register(modBus);
        modBus.addListener(com.redtale.project.mobs.RedtaleEntities::onAttributes);
        // 属性注册完还要挂到实体上，否则 /attribute 与 getAttribute() 都看不到它们
        modBus.addListener(RedtaleProject::onEntityAttributes);
        NeoForge.EVENT_BUS.register(new GameHooks());
    }

    /** {@link EntityAttributeModificationEvent} 是 mod bus 事件（实现 IModBusEvent）。 */
    private static void onEntityAttributes(EntityAttributeModificationEvent event) {
        RedtaleAttributes.onEntityAttributes(event);
    }
}
