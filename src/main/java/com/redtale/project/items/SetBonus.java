package com.redtale.project.items;

import com.redtale.project.RedtaleProject;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.RedtaleAttributes;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 套装件数统计 + 属性下发（M3 第 6 步，{@code ITEM-MODEL.md} §5.4）。
 *
 * <p>三条纪律，与 D18 攻速同源：
 * <ol>
 *   <li><b>固定 modifier id</b>：{@code redtale_project:set/<setId>/<count>}。件数从 2 变 3 时
 *       先移除 {@code .../2} 再加 {@code .../3}，不是「在旧值上加减」。</li>
 *   <li><b>先移除再加</b>：每次重算前把「该套装所有定义过的档位 id」全部移除，再按当前件数逐档加回。
 *       老档位的 id 不留在属性上，属性面板永远等于当前件数该有的样子。</li>
 *   <li><b>件数不变就不碰属性</b>：{@code AttributeInstance} 一改就触发重算与客户端同步，
 *       每 tick 无脑写一遍等于每 tick 同步一次属性（{@link #SIGNATURES} 缓存挡这个）。</li>
 * </ol>
 *
 * <p>件数口径（§5.4）：护甲 4 格 + 主手 + 副手里 {@code set_id} 命中的**不同内容 id** 数。
 * 同一件内容 id 戴两件（比如两个一样的戒指）只算一件 —— 按内容 id 去重，不是按槽位数。
 *
 * <p>这套数值**永不进 {@code ContentData}**：它不是物品属性，是「玩家穿了一组东西」的派生态，
 * 写进物品组件就等于把派生数据当事实存，脱一件还得改另一件的组件。
 */
public final class SetBonus {

    /** modifier id 前缀：{@code redtale_project:set/<setId>/<count>}。 */
    private static final String PREFIX = "set/";

    /** 参与件数统计的 6 个格子（§5.4：护甲 4 + 主手 + 副手）。 */
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    /**
     * 上一次下发的「件数签名」（玩家 UUID → {@code set=a,b} 拼接）。只用于「没变就别动属性」，
     * 丢了最多多做一次重算，不影响正确性 —— 所以允许它在 reload / 退出时不清。
     */
    private static final Map<UUID, String> SIGNATURES = new ConcurrentHashMap<>();

    private SetBonus() {
    }

    /**
     * 基底声明的 {@code EquipmentSlotGroup} → 真正要穿的那一个 {@code EquipmentSlot}。
     *
     * <p>只用于「把装备塞进玩家」的入口（探测 / 以后的一键穿戴）；件数统计**不用它**
     * ——统计读的是玩家身上 6 个格子，谁穿在哪由玩家自己决定。
     * 组都是原版单例，用 {@code ==} 比较即可（1.21.1 没有公开的 group→slot 反查）。
     */
    public static EquipmentSlot slotOf(ItemBase base) {
        net.minecraft.world.entity.EquipmentSlotGroup group =
                base == null ? net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND : base.slot();
        if (group == net.minecraft.world.entity.EquipmentSlotGroup.HEAD) return EquipmentSlot.HEAD;
        if (group == net.minecraft.world.entity.EquipmentSlotGroup.CHEST) return EquipmentSlot.CHEST;
        if (group == net.minecraft.world.entity.EquipmentSlotGroup.LEGS) return EquipmentSlot.LEGS;
        if (group == net.minecraft.world.entity.EquipmentSlotGroup.FEET) return EquipmentSlot.FEET;
        if (group == net.minecraft.world.entity.EquipmentSlotGroup.OFFHAND) return EquipmentSlot.OFFHAND;
        return EquipmentSlot.MAINHAND;
    }

    /** 一名玩家身上 6 个格子的物品（顺序固定，便于探针断言）。 */
    public static List<ItemStack> worn(Player player) {
        List<ItemStack> out = new ArrayList<>(SLOTS.length);
        for (EquipmentSlot slot : SLOTS) {
            out.add(player.getItemBySlot(slot));
        }
        return out;
    }

    /**
     * 纯函数：<b>不同内容 id</b> 的件数统计（{@code set_id} → 件数），保持套装定义里的迭代序。
     *
     * <p>只认带内容组件的物品（{@code ContentData} 为 {@code null} 一律跳过），
     * 因此原版装备、别的 mod 的装备不会被误算进来。
     */
    public static Map<String, Integer> counts(List<ItemStack> items) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (items == null) {
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ContentData data = stack.get(ContentComponents.CONTENT.get());
            if (data == null || data.setId().isEmpty()) {
                continue;
            }
            if (!seen.add(data.setId() + "\u0000" + data.contentId())) {
                continue; // 同一套装里同一件内容戴了两件 → 只算一件
            }
            out.merge(data.setId(), 1, Integer::sum);
        }
        return out;
    }

    /** 玩家当前件数（命令与 tooltip 共用的入口，语义与结算完全一致）。 */
    public static Map<String, Integer> countsOf(Player player) {
        return counts(worn(player));
    }

    /** 一名玩家某套装的件数；没有该套装 → 0。 */
    public static int countOf(Player player, String setId) {
        return countsOf(player).getOrDefault(setId, 0);
    }

    /**
     * 重算并下发这名玩家的全部套装加成。**每 20 tick / 登录 / reload 各调一次**，
     * 件数签名没变就直接返回（零属性写入）。
     *
     * @return 实际改写了多少个 modifier（验收与日志用；0 = 件数没变或没有套装）
     */
    public static int apply(Player player) {
        if (player == null || player.level().isClientSide()) {
            return 0;
        }
        Map<String, SetSpec.Definition> sets = RedtaleCore.get().sets();
        Map<String, Integer> counts = countsOf(player);
        String signature = signature(counts);
        String previous = SIGNATURES.get(player.getUUID());
        if (signature.equals(previous)) {
            return 0;
        }
        int written = 0;
        // 先按「定义里出现过的档位」整段移除：老档位的 id 必须清掉，否则件数从 3 掉到 2 时
        // .../3 那一档会留在属性上（属性只增不减的经典漏）
        for (SetSpec.Definition def : sets.values()) {
            for (int count : declaredCounts(def)) {
                written += remove(player, def.id(), count);
            }
        }
        // 再按当前件数把生效的档加回去（§5.4：所有 count <= 件数 的档全部生效，不是只取最高档）
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            SetSpec.Definition def = sets.get(e.getKey());
            if (def == null) {
                continue;
            }
            for (SetSpec.Bonus bonus : SetSpec.activeBonuses(def, e.getValue())) {
                written += add(player, def.id(), bonus);
            }
        }
        SIGNATURES.put(player.getUUID(), signature);
        return written;
    }

    /** 清掉一名玩家的全部套装 modifier（登出 / reload 关掉套装时用；签名缓存一并清）。 */
    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        for (SetSpec.Definition def : RedtaleCore.get().sets().values()) {
            for (int count : declaredCounts(def)) {
                remove(player, def.id(), count);
            }
        }
        SIGNATURES.remove(player.getUUID());
    }

    /** 全部在线玩家重算一遍（{@code /redtale reload} 与 {@code /redtale set sync} 用）。 */
    public static int applyAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int written = 0;
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            written += apply(player);
        }
        return written;
    }

    /** 玩家身上某套装的生效档（tooltip 与命令用）：按 {@code count} 升序。 */
    public static List<SetSpec.Bonus> activeOf(Player player, String setId) {
        SetSpec.Definition def = RedtaleCore.get().sets().get(setId);
        if (def == null) {
            return List.of();
        }
        return SetSpec.activeBonuses(def, countOf(player, setId));
    }

    /** 定义里出现过的全部档位数（升序去重），用于「先整段移除」。 */
    public static List<Integer> declaredCounts(SetSpec.Definition def) {
        List<Integer> out = new ArrayList<>();
        for (SetSpec.Bonus bonus : def.bonuses()) {
            if (!out.contains(bonus.count())) {
                out.add(bonus.count());
            }
        }
        return out;
    }

    /** modifier id：{@code redtale_project:set/<setId>/<count>}，与 D18 攻速同一套「固定 id」纪律。 */
    public static ResourceLocation modifierId(String setId, int count) {
        return ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, PREFIX + setId + "/" + count);
    }

    /** 一条属性上某个套装档位的值（命令 / tooltip / 探针读用）。 */
    public static double amount(Player player, String setId, int count, AttributeSpec spec) {
        Holder<Attribute> holder = RedtaleAttributes.holder(spec);
        if (holder == null) {
            return 0;
        }
        AttributeInstance instance = player.getAttribute(holder);
        if (instance == null) {
            return 0;
        }
        AttributeModifier modifier = instance.getModifier(modifierId(setId, count));
        return modifier == null ? 0 : modifier.amount();
    }

    // ---- 内部：加一档 / 移除一档 ----

    /**
     * 把一档加成写进属性。数值一律 {@code ADD_VALUE}（与物品面板同口径：自建属性基值 0，
     * 乘算永远乘 0 得 0；百分比只影响显示）。
     *
     * <p>只取 {@code StatValue.base()}：套装档位没有「物品等级」这个概念，
     * {@code per_item_level} 曲线在这里无意义（写了也不参与），数值就是那个平加量。
     */
    private static int add(Player player, String setId, SetSpec.Bonus bonus) {
        int written = 0;
        ResourceLocation id = modifierId(setId, bonus.count());
        for (Map.Entry<String, com.redtale.project.core.math.StatValue> e : bonus.stats().entrySet()) {
            AttributeSpec spec = AttributeSpecs.resolve(e.getKey());
            Holder<Attribute> holder = spec == null ? null : RedtaleAttributes.holder(spec);
            if (holder == null) {
                continue;
            }
            double value = e.getValue().base();
            if (!Double.isFinite(value) || value == 0) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(holder);
            if (instance == null) {
                continue; // 这名玩家没有这条属性（原版实体没有自建属性）→ 跳过，不报错
            }
            AttributeModifier existing = instance.getModifier(id);
            if (existing != null
                    && existing.operation() == AttributeModifier.Operation.ADD_VALUE
                    && Double.compare(existing.amount(), value) == 0) {
                continue; // 已经是这个值，不碰属性（避免无意义的重算与同步）
            }
            instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, value,
                    AttributeModifier.Operation.ADD_VALUE));
            written++;
        }
        return written;
    }

    /** 移除某套装某档位在**它声明过的每条属性**上的 modifier（声明集合来自定义，不猜）。 */
    private static int remove(Player player, String setId, int count) {
        int written = 0;
        ResourceLocation id = modifierId(setId, count);
        SetSpec.Definition def = RedtaleCore.get().sets().get(setId);
        if (def == null) {
            return 0;
        }
        for (SetSpec.Bonus bonus : def.bonuses()) {
            if (bonus.count() != count) {
                continue;
            }
            for (String key : bonus.stats().keySet()) {
                AttributeSpec spec = AttributeSpecs.resolve(key);
                Holder<Attribute> holder = spec == null ? null : RedtaleAttributes.holder(spec);
                if (holder == null) {
                    continue;
                }
                AttributeInstance instance = player.getAttribute(holder);
                if (instance != null && instance.getModifier(id) != null) {
                    instance.removeModifier(id);
                    written++;
                }
            }
        }
        return written;
    }

    private static String signature(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            parts.add(e.getKey() + "=" + e.getValue());
        }
        parts.sort(null);
        return String.join(",", parts);
    }
}
