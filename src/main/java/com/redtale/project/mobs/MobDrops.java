package com.redtale.project.mobs;

import com.redtale.project.core.RedtaleCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 掉落接线（M6 第四层第三步）：自建怪死亡 → {@code MobSpec.drops} 指的掉落表 → {@link DropRoll} 掷一次。
 *
 * <p>三个来源各管一件事，本类只做「翻译」：
 * <ul>
 *   <li>{@code mobs/*.json5} 的 {@code drops: "<裸 id>"} —— 这只怪用哪张表（{@link MobSpec}）</li>
 *   <li>{@code drops/*.json5} —— 表里有什么（{@link DropSpec}）</li>
 *   <li>{@link DropRoll} —— 这次掉几件（纯函数，可离线复现）</li>
 * </ul>
 *
 * <p>认出「这是哪只怪」只靠 scoreboard tag {@code redtale_mob/<id>}（{@link MobSpawner} 打的），
 * 因为 1.21.1 的实体没有可写的自定义 NBT（R34：`getPersistentData()` 已移除）。
 *
 * <p>**不覆盖原版掉落**：本类只 `add` 条目，不 `clear()` —— 自建怪的装备/原版战利品照掉，
 * 「我们的表」是加项不是替换项（与 D15「只乘不覆盖」同一条纪律）。
 *
 * <p>经验为什么也挂在这里（而不是 {@code LivingExperienceDropEvent}，R41 实测）：原版经验球只在
 * **有玩家击杀**时才发（对照实验：`/kill` 一只原版牛 → 0 个经验球），而 `LivingExperienceDropEvent`
 * 是「原版要发经验」那一刻才触发的事件 —— 无玩家击杀的怪上它根本不响，验收里永远看不见。
 * 所以这里用 {@link ExperienceOrb#award} 主动发；玩家击杀时原版那份照旧另发，两边相加 =
 * 「表里的 xp 是在原版之上额外给的」，语义与掉落物一致。
 */
public final class MobDrops {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("RedtaleProject");
    /** 已告警过的坏引用（表不存在 / 物品 id 未知）。去重，免得一次群刷刷满日志。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** 表里的物品落成掉落物（散在脚下 ±0.75 格），表里的 xp 发成经验球。 */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        DropSpec.Definition table = tableOf(entity, "掉落");
        if (table == null) return;
        // DropRoll 吃 java.util.Random（纯函数层不依赖 MC 的 RandomSource）→ 用实体 RNG 取一个种子派生；
        // 这样「同一个世界种子 + 同一只怪」掉落仍可复现，又不给 DropRoll 引入 net.minecraft
        java.util.Random rnd = new java.util.Random(entity.getRandom().nextLong());
        DropRoll.Result rolled = DropRoll.roll(table, rnd);
        RedtaleCore core = RedtaleCore.get();
        for (DropRoll.Rolled item : rolled.items()) {
            ItemStack stack = stackOf(core, item, rnd, table.id());
            if (stack == null) continue;
            double dx = (level.random.nextDouble() - 0.5D) * 1.5D;
            double dz = (level.random.nextDouble() - 0.5D) * 1.5D;
            event.getDrops().add(new ItemEntity(level, entity.getX() + dx, entity.getY() + 0.5D,
                    entity.getZ() + dz, stack));
        }
        if (rolled.xp() > 0) {
            ExperienceOrb.award(level, entity.position(), Math.min(1_000_000, rolled.xp()));
        }
    }

    /**
     * 掉落表里的一行 → 物品栈。**先查内容 id、再查物品注册表**（R41 实测的口径）：
     * 我们的 14 个基底各只有**一个**物品 id（`redtale_project:sword`），
     * `demo_longsword` / `gem_ruby` 这些是**内容 id**（= 基底 id + `redtale_project:content` 组件），
     * `BuiltInRegistries.ITEM.containsKey("redtale_project:gem_ruby")` 恒 false —— 只查注册表就永远掉不出装备。
     * 两条都查不到 → 告警一次并跳过（不抛、不降级成别的物品）。
     */
    private static ItemStack stackOf(RedtaleCore core, DropRoll.Rolled item, java.util.Random rnd, String tableId) {
        String raw = item.item();
        var content = core.contentItems().get(raw);
        // 内容 id 是裸名（`gem_ruby`），运营写全名（`redtale_project:gem_ruby`）也认 —— 免得「写对了名字却掉不出来」
        if (content == null && raw.startsWith("redtale_project:")) {
            content = core.contentItems().get(raw.substring("redtale_project:".length()));
        }
        if (content != null) {
            return com.redtale.project.items.ItemFactory.build(content, rnd);
        }
        ResourceLocation id = ResourceLocation.tryParse(item.item());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            warnOnce("item/" + item.item(), "掉落物品 id 未知：" + item.item()
                    + "（既不是内容 id 也不是物品 id，表 " + tableId + " 的这一项已跳过）");
            return null;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id), item.count());
    }

    /**
     * 实体 → 掉落表：没有 {@code redtale_mob/} 标签 / 定义里没写 {@code drops} / 表不存在 → {@code null}。
     * 三条都是「正常情况」（原版怪、没配掉落的怪），只有第三条值得告警。
     */
    private static DropSpec.Definition tableOf(LivingEntity entity, String what) {
        if (entity == null) return null;
        String mobId = mobIdOf(entity);
        if (mobId == null) return null;
        RedtaleCore core = RedtaleCore.get();
        MobSpec.Definition def = core.mobs().get(mobId);
        if (def == null) return null;
        String tableId = def.drops();
        if (tableId == null || tableId.isEmpty()) return null;
        DropSpec.Definition table = core.drops().get(tableId);
        if (table == null) {
            warnOnce("table/" + tableId, "怪物 " + mobId + " 引用的" + what + "表 " + tableId
                    + " 不存在（掉落按原版处理）");
            return null;
        }
        return table;
    }

    /** 从 scoreboard tag 反查内容 id：`redtale_mob/<id>` → `<id>`。 */
    private static String mobIdOf(LivingEntity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(MobSpawner.TAG_PREFIX)) {
                String id = tag.substring(MobSpawner.TAG_PREFIX.length());
                if (!id.isEmpty()) return id;
            }
        }
        return null;
    }

    /** 同一个坏引用只喊一次（进程内）。配置改好也无所谓：下一条新坏引用照样会喊。 */
    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) LOG.warn("{}", message);
    }
}
