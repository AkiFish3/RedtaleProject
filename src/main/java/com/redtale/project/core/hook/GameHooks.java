package com.redtale.project.core.hook;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttackSpeedApplier;
import com.redtale.project.core.command.RedtaleCommand;
import com.redtale.project.core.damage.DamageLayer;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 游戏事件 → 脚本 trigger 的接线处。M0 只接两个点：
 * 开服（加载配置）、玩家登录（跑 player_join 脚本）。
 *
 * <p>M1 起这里还挂数值层的入口：攻速（{@link AttackSpeedApplier}）与伤害乘区
 * （{@link DamageLayer}）。它们不是脚本 trigger，但同样「换行为就要换 jar」，
 * 放在同一个类里方便一眼看全本 mod 到底碰了哪些事件。
 *
 * <p>以后每个 trigger 原语在这里加一个 @SubscribeEvent，这也是「新 trigger 必须换 jar」
 * 的物理原因。
 */
public final class GameHooks {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("RedtaleProject");

    /** L2 乘区（M1）：状态无关，构造一次注册一次即可。 */
    private final DamageLayer damageLayer = new DamageLayer();

    public GameHooks() {
        // 由 @Mod 构造期注册到 NeoForge.EVENT_BUS；DamageLayer 自己带 @SubscribeEvent
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(damageLayer);
        // M2：物品 tooltip（D20）。挂事件而不是覆写 Item#appendHoverText，
        // 因为 14 个基底各写一遍不划算，且新增事件点本身就要换 jar。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                new com.redtale.project.items.ItemTooltipHook());
        // M2：近战暴击（CriticalHitEvent）。与 L2 乘区是两个事件点：暴击在「出手」，乘区在「落地」。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                new com.redtale.project.core.damage.CritLayer());
        // M2：物品更新（D33）的 3 个被动刷新点：登录 / 换维度 / 复活 / 开容器 / 区块加载。
        // 用单例而不是 new：UpgradeSweep（第 ④ 个刷新点）要复用同一个升级计数器。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ItemUpgradeHooks.get());
        // M6 第四层（R41）：自建怪死亡掉落 = mobs/*.json5 的 drops 引用 → drops/*.json5 → DropRoll 掷一次。
        // 只在 LivingDropsEvent / LivingExperienceDropEvent 上「追加」，不 clear 原版战利品（D15 只乘不覆盖）。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                new com.redtale.project.mobs.MobDrops());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        RedtaleCore.get().reload();
        // M4 / D32 的 R1 风险探针：EnchantmentEntityEffect 是内置表，注册晚于加载期就会静默缺席。
        // 开局打一行，谁都能在日志里核对，不用进游戏。
        net.minecraft.resources.ResourceLocation enchantFx =
                com.redtale.project.enchants.ScriptedEnchantmentEffect.ID;
        boolean present = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE
                .containsKey(enchantFx);
        LOG.info("附魔实体效果注册：{} 存在={}（表内共 {} 条）",
                enchantFx, present,
                net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE.size());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RedtaleCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RedtaleCore.get().fire("player_join", ScriptContext.ofPlayer(player));
            // 登录先算一次，避免「刚进服 5 tick 内」读到上一世的残留
            AttackSpeedApplier.apply(player, RedtaleCore.get().attackSpeedRules());
        }
    }

    /**
     * 攻速来源落地（D18）：每 5 tick 把 {@code attack_speed.json5} 声明的加成写成
     * {@code ATTACK_SPEED} 的 transient modifier（固定 id，等级变化先移除再加）。
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttackSpeedApplier.onPlayerTick(player);
            // 法力（M5 收口，R35）：每 tick 同步上限 + 按 mana_regeneration 回蓝。
            // 用 ticks=1 交回池里累积，小数余额不会被取整吃掉（T-0029 行为 5）。
            RedtaleCore.get().skillEngine().tickMana(player);
            // 套装件数（M3）：每 20 tick 复核一次。换装没有可靠的单点事件
            // （背包点击 / 快捷栏切换 / 被怪打掉 / 死亡掉落 都会改装备），而件数签名没变时
            // SetBonus.apply 是「读 6 个格子 + 拼一个字符串」就返回，不碰任何属性。
            if (player.tickCount % 20 == 0) {
                com.redtale.project.items.SetBonus.apply(player);
            }
        }
    }

    /**
     * D33 第 ④ 个刷新点（reload 后的全量刷新）分帧执行：每 tick 只吃固定数量的区块，
     * 扫完自己收尾。空闲时是一次 boolean 判断，不进任何循环。
     */
    @SubscribeEvent
    public void onServerTickPost(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        com.redtale.project.core.hook.UpgradeSweep.get().tick();
        // M6 第三步第 3 小步（R40）：怪物按 MobSpec.skills 掷骰放技能（内部 10 tick 节拍）。
        com.redtale.project.mobs.MobSkillCaster.tick(event.getServer());
        // M5-C 停 AI 定住（R34）：到点恢复。空闲时是一次 isEmpty 判断。
        com.redtale.project.core.script.builtin.StunEffect.tick();
    }

    /** 重启残留兜底：上次关服时还定着的怪，入世即解除（否则永久 NoAI）。 */
    @SubscribeEvent
    public void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        com.redtale.project.core.script.builtin.StunEffect.onEntityJoin(event.getEntity());
    }
}
