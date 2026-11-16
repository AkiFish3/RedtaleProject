package com.redtale.project.items;

import com.redtale.project.core.RedtaleCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 修理结算（D23 第二半）：磨刀石这类「作用于别的物品」的内容唯一结算点。
 *
 * <p>与 {@link ConsumableSpec} / {@link RepairSpec} 的关系：那两份是<b>解析</b>，本类是<b>结算</b>。
 * 解析结果不落进 {@link ContentData}（R15-a 同口径），所以改 {@code repair} 段 + {@code /redtale reload}
 * 对存量物品立刻生效、不用 `rev`+1。
 *
 * <p>三条口径：
 * <ol>
 *   <li><b>只改耐久</b>：{@code setDamageValue} 是唯一写操作。白板化（D21）不碰
 *       {@code MAX_DAMAGE} 以外的耐久逻辑，所以这里也不需要管属性。</li>
 *   <li><b>先判目标再算数</b>：不可损坏 / 不满耐久 / 目标不匹配三种情况在写之前就返回，
 *       保证「失败 = 物品逐字未变」，玩家不会因为点错一下白丢一块磨刀石。</li>
 *   <li><b>不扣件</b>：扣不扣工具由调用方按 {@code consume_item} 决定 —— 命令路径要能「只看不扣」。</li>
 * </ol>
 */
public final class Repairer {

    /** 结算结果。{@code repaired} 是真实修好的耐久点数（0 = 没修）。 */
    public enum Status {
        /** 修好了。 */
        APPLIED,
        /** 目标物品不可损坏（没有耐久条）。 */
        NOT_REPAIRABLE,
        /** 目标耐久是满的。 */
        ALREADY_FULL,
        /** {@code targets} 里没有一项匹配该目标。 */
        TARGET_MISMATCH,
        /** 目标为空栈。 */
        NO_TARGET
    }

    public record Result(Status status, int repaired, int before, int after) {
        public boolean applied() {
            return status == Status.APPLIED;
        }
    }

    private Repairer() {
    }

    /**
     * 按定义修一件目标物品。纯结算：不读配置、不扣件、不写日志。
     *
     * @param spec   {@code repair} 段的解析结果（非 null）
     * @param target 要被修的那件物品（原地改）
     */
    public static Result apply(RepairSpec.Spec spec, ItemStack target) {
        if (target == null || target.isEmpty()) {
            return new Result(Status.NO_TARGET, 0, 0, 0);
        }
        if (!target.isDamageableItem()) {
            return new Result(Status.NOT_REPAIRABLE, 0, 0, 0);
        }
        if (!matches(spec, target)) {
            return new Result(Status.TARGET_MISMATCH, 0, target.getDamageValue(), target.getDamageValue());
        }
        int before = target.getDamageValue();
        if (before <= 0) {
            return new Result(Status.ALREADY_FULL, 0, 0, 0);
        }
        int wanted = "percent".equals(spec.mode())
                ? (int) Math.ceil(target.getMaxDamage() * spec.percent())
                : spec.amount();
        if (wanted <= 0) {
            // amount 模式写 0 / percent 模式写 0：定义合法但修不动，按「已满」处理而不是静默 APPLIED
            return new Result(Status.ALREADY_FULL, 0, before, before);
        }
        int repaired = Math.min(wanted, before);
        target.setDamageValue(before - repaired);
        return new Result(Status.APPLIED, repaired, before, before - repaired);
    }

    /**
     * 目标匹配。{@code targets} 为空 = 不限制（任意可损坏物品）。
     *
     * <p>一个条目三种写法，按顺序试：
     * <ol>
     *   <li>{@code #ns:tag} → 原版物品标签（如 {@code #minecraft:swords}）</li>
     *   <li>{@code ns:path} → 物品注册 id（如 {@code redtale_project:sword}）</li>
     *   <li>裸串 → 内容 id（如 {@code demo_longsword}），按物品上 {@code ContentData.contentId} 比对</li>
     * </ol>
     * 第 3 条是给运营用的：我们的装备 14 件共用一个物品 id，「按内容 id 指定」比按基底指定更好写。
     */
    public static boolean matches(RepairSpec.Spec spec, ItemStack target) {
        if (spec.targets().isEmpty()) {
            return true;
        }
        ContentData data = target.get(ContentComponents.CONTENT.get());
        String contentId = data == null ? null : data.contentId();
        for (String entry : spec.targets()) {
            if (entry.equals(contentId)) {
                return true;
            }
            if (entry.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
                if (tagId != null && target.is(TagKey.create(Registries.ITEM, tagId))) {
                    return true;
                }
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(entry);
            if (itemId != null) {
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null && item != net.minecraft.world.item.Items.AIR && target.is(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 按 {@code contentId} 取定义里的 {@code repair} 段；任何一环缺失返回 {@code null}。 */
    public static RepairSpec.Spec specOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ContentData data = stack.get(ContentComponents.CONTENT.get());
        if (data == null) {
            return null;
        }
        ItemContent definition = RedtaleCore.get().contentItems().get(data.contentId());
        return definition == null ? null : definition.repair();
    }

    /**
     * 选目标：**副手优先，否则修背包里损伤最重的那件**。
     *
     * <p>为什么副手优先：右键结果必须可预期 —— 把要修的东西放副手，点一下就是修它。
     * 副手空着时才退化成「修最破的那件」，这是玩家不想切手的顺路便利，且永远不会
     * 修到工具自己（按对象身份排除）。
     *
     * <p>实现细节：副手读 {@code inventory.offhand.get(0)} 而**不是**
     * {@code player.getItemInHand(OFF_HAND)} —— 实测 {@code FakePlayer} 上前者能拿到、
     * 后者恒返回 {@code air}（{@code setItemInHand} 已写进列表，{@code getItemBySlot} 读不到），
     * 两者对真玩家等价，读列表只影响「探测能不能跑通」。
     */
    public static Target pickTarget(net.minecraft.world.entity.player.Player player,
                                    RepairSpec.Spec spec, ItemStack tool) {
        ItemStack offhand = player.getInventory().offhand.get(0);
        if (usable(offhand, spec) && !same(offhand, tool)) {
            return new Target(offhand, 40);
        }
        var inventory = player.getInventory();
        ItemStack best = ItemStack.EMPTY;
        int bestSlot = -1;
        int bestDamage = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!usable(candidate, spec) || same(candidate, tool)) {
                continue;
            }
            if (candidate.getDamageValue() > bestDamage) {
                best = candidate;
                bestSlot = slot;
                bestDamage = candidate.getDamageValue();
            }
        }
        return bestSlot < 0 ? null : new Target(best, bestSlot);
    }

    /** 目标候选：{@code slot} 只用于回显（40 = 副手）。 */
    public record Target(ItemStack stack, int slot) {}

    /** 能不能被这件工具修：不空、可损坏、还没修满、且匹配 {@code targets}。 */
    private static boolean usable(ItemStack stack, RepairSpec.Spec spec) {
        return stack != null && !stack.isEmpty() && stack.isDamageableItem()
                && stack.getDamageValue() > 0 && matches(spec, stack);
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a == b;
    }

    /** 状态的中文短句（命令回显用，避免每个调用点各写一份）。 */
    public static String describe(Status status) {
        return switch (status) {
            case APPLIED -> "已修好";
            case NOT_REPAIRABLE -> "目标不可损坏";
            case ALREADY_FULL -> "目标耐久是满的（或定义修不动）";
            case TARGET_MISMATCH -> "目标不在 targets 里";
            case NO_TARGET -> "没有目标";
        };
    }
}
