package com.redtale.project.core.hook;

import com.redtale.project.core.progression.ProfessionService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 专业（D24）的三个经验源接线，{@code PROFESSION.md} §4.4 的三行表格逐条对应：
 *
 * <ul>
 *   <li>{@code mine} ← {@code BlockEvent.BreakEvent}（按**被破坏的方块 id** 查一次）；</li>
 *   <li>{@code craft} ← {@code PlayerEvent.ItemCraftedEvent}（按**结果物品 id**，<b>不乘产出件数</b>）；</li>
 *   <li>{@code smelt} ← {@code PlayerEvent.ItemSmeltedEvent}（按**产出物品 id**，每件触发一次）。</li>
 * </ul>
 *
 * <p>本类只做「MC 对象 → (id, tag 集)」的翻译，判定与写入全在 {@link ProfessionService}：
 * 这样控制台探针可以不经事件、直接喂字符串验同一套逻辑（同 R43 的「事件不是唯一入口」）。
 *
 * <p>tag 一律转成 {@code ns:path}（不带 {@code #}），与 {@code ProfessionSpec.resolve} 的入参形态一致。
 */
public final class ProfessionHooks {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        // 被取消的破坏不算（保护 / 领地插件拦下的）；创造与旁观也不给经验
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        BlockState state = event.getState();
        if (state == null) {
            return;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        award(player, ProfessionService.SRC_MINE, id, blockTags(state));
    }

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getCrafting();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // §4.4：按结果物品查一次，**不乘 stack.getCount()**（否则「原木 → 4 木板」就是刷经验入口）
        award(player, ProfessionService.SRC_CRAFT, itemId(stack), itemTags(stack));
    }

    @SubscribeEvent
    public void onSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getSmelting();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        award(player, ProfessionService.SRC_SMELT, itemId(stack), itemTags(stack));
    }

    // ---- 内部 ----

    private static void award(ServerPlayer player, String source, String id, Set<String> tags) {
        Map<String, Long> got = ProfessionService.award(player.server, player.getUUID(), source, id, tags);
        if (got.isEmpty()) {
            return;
        }
        // 等级可能因这次经验变化 → 按新等级重下发（只在真有经验时做，空手挖土不碰属性）
        ProfessionService.applyAttributes(player);
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static Set<String> blockTags(BlockState state) {
        Set<String> out = new LinkedHashSet<>();
        for (TagKey<net.minecraft.world.level.block.Block> tag : state.getTags().toList()) {
            out.add(tag.location().toString());
        }
        return out;
    }

    private static Set<String> itemTags(ItemStack stack) {
        Set<String> out = new LinkedHashSet<>();
        for (TagKey<net.minecraft.world.item.Item> tag : stack.getTags().toList()) {
            out.add(tag.location().toString());
        }
        return out;
    }
}
