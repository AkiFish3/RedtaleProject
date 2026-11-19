package com.redtale.project.core.progression;

import com.redtale.project.RedtaleProject;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.RedtaleAttributes;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 专业（D24）的**服务层**（L3 发放 + L4 下发），{@code PROFESSION.md} §4.4 / §5。
 *
 * <p>分层：L1 {@link ProfessionSpec} 解析 + {@code resolve} 纯函数、L2 {@link ProfessionRegistry} 快照、
 * L3 {@link ProgressStore} 的 {@code professions} 桶 + 三类事件（由 {@code ProfessionHooks} 调本类）、
 * 本类只做「选哪个专业给多少 → 写桶 → 按等级下发」。
 *
 * <p>三条口径：
 * <ul>
 *   <li><b>一次动作只发一条</b>：精确 id 命中即止，否则取命中的 tag 里最大的那条，否则 {@code default}（§4.2，
 *       顺序无关）；同一方块命中多个 tag 也只取一个数；</li>
 *   <li><b>等级是派生值</b>：只写 {@code total_exp}，等级每次现算（与主等级/职业同一条纪律）；</li>
 *   <li><b>属性先清后加</b>：按 {@code redtale_project:profession/<专业>/} 前缀扫 {@code AttributeMap}，
 *       不按当前声明清单（改配置换属性键后旧 modifier 会残留 —— R53-b 的幽灵 modifier）。</li>
 * </ul>
 *
 * <p>纯函数（{@link #amountFor} / {@link #plan}）零 MC 依赖，命令探针与以后的离线断言直接用。
 * 线程：只在服务端主线程调用。
 */
public final class ProfessionService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("RedtaleProject");

    /** 三个经验源（{@code PROFESSION.md} §4.1：只认这三个，{@code kill} 属主等级与职业）。 */
    public static final String SRC_MINE = "mine";
    public static final String SRC_CRAFT = "craft";
    public static final String SRC_SMELT = "smelt";

    /** modifier id 前缀（{@code PROFESSION.md} §5）：{@code profession/<专业 path>/}。 */
    public static final String ID_ROOT = "profession/";

    private ProfessionService() {
    }

    // ---- 纯：一次动作给多少 ----

    /** 单专业一次动作的经验（{@code 0} = 不发；不发也不告警，§4.2 第 4 档）。 */
    public static long amountFor(ProfessionSpec.Definition def, String source, String id, Set<String> tags) {
        if (def == null) {
            return 0L;
        }
        return ProfessionSpec.resolve(ProfessionSpec.grantsOf(def, source), bare(id), bareAll(tags));
    }

    /**
     * 全部专业一次动作各得多少：只收 {@code > 0} 的，保配置顺序。
     * {@code id} / {@code tags} 里的命名空间原样（tag 传不带的 {@code ns:path}，与 {@code ProfessionSpec.resolve} 对齐）。
     */
    public static Map<String, Long> plan(Map<String, ProfessionSpec.Definition> all, String source,
                                         String id, Set<String> tags) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (all == null || all.isEmpty() || source == null) {
            return out;
        }
        for (ProfessionSpec.Definition def : all.values()) {
            long n = amountFor(def, source, id, tags);
            if (n > 0L) {
                out.put(def.id(), n);
            }
        }
        return out;
    }

    // ---- 写：发放 ----

    /**
     * 按一次动作发放：{@link #plan} 里每个专业各加自己那一份，写完对在线玩家重下发属性。
     *
     * @param id   被破坏的方块 / 合成的结果物 / 熔炼的产出物 id（{@code minecraft:stone}）
     * @param tags 该 id 命中的 tag，**不带** {@code #}（{@code minecraft:logs}）；可为 {@code null}
     * @return {@code 专业 id → 实发经验}（没命中的专业不在表里）
     */
    public static Map<String, Long> award(MinecraftServer server, UUID playerId, String source,
                                          String id, Set<String> tags) {
        if (server == null || playerId == null) {
            return Map.of();
        }
        ProfessionRegistry reg = RedtaleCore.get().professions();
        Map<String, Long> plan = plan(reg.all(), source, id, tags);
        if (plan.isEmpty()) {
            return plan;
        }
        ProgressStore store = ProgressStore.get(server);
        for (Map.Entry<String, Long> e : plan.entrySet()) {
            store.addProfExp(playerId, e.getKey(), e.getValue());
        }
        return plan;
    }

    /** 某专业当前等级（{@code total <= 0} → 1，到顶夹住；曲线自己管上限，同 {@code ProgressService.levelOf} 口径）。 */
    public static int levelOf(ProfessionSpec.Definition def, long total) {
        if (def == null) {
            return 1;
        }
        return Math.max(1, Math.min(def.curve().maxLevel(), def.curve().levelFor(total)));
    }

    /** 某玩家某专业的累计经验。 */
    public static long expOf(MinecraftServer server, UUID id, String profId) {
        return ProgressStore.get(server).profExp(id, profId);
    }

    // ---- 写：属性下发 ----

    /** 一条该下发的东西：modifier id（无命名空间）+ 属性全名 + 数值。 */
    public record Planned(String id, String attribute, double amount) {
    }

    /** {@code profession/<专业 path>/<属性 path>}；两个 path 都去命名空间、属性里的 {@code .} 保留。 */
    public static String idOf(ProfessionSpec.Definition def, String attribute) {
        String attr = attribute == null ? "" : attribute;
        int colon = attr.indexOf(':');
        if (colon >= 0) {
            attr = attr.substring(colon + 1);
        }
        return ID_ROOT + pathOf(def == null ? "" : def.id()) + "/" + attr;
    }

    /** 前缀（带命名空间，用于扫 {@code AttributeMap}）：{@code redtale_project:profession/mining/}。 */
    public static String prefixOf(String profId) {
        return RedtaleProject.MOD_ID + ":" + ID_ROOT + pathOf(profId) + "/";
    }

    /** 定义里声明过的全部 id（不论等级）——「先移除再加」在 {@code level < 2} 时也要靠它清场。 */
    public static List<String> declaredIds(ProfessionSpec.Definition def) {
        List<String> out = new ArrayList<>();
        if (def == null) {
            return out;
        }
        for (String attribute : def.attributes().keySet()) {
            out.add(idOf(def, attribute));
        }
        return out;
    }

    /** 本级该下发什么：{@code per_level × (level - 1)}，四舍五入 6 位；{@code level < 2} → 空表。 */
    public static List<Planned> planGrants(ProfessionSpec.Definition def, int level) {
        List<Planned> out = new ArrayList<>();
        if (def == null) {
            return out;
        }
        int capped = Math.max(1, Math.min(def.curve().maxLevel(), level));
        if (capped < 2) {
            return out;
        }
        for (Map.Entry<String, Double> e : def.attributes().entrySet()) {
            double perLevel = e.getValue() == null ? 0.0 : e.getValue();
            double amount = Math.round(perLevel * (capped - 1) * 1_000_000.0) / 1_000_000.0;
            out.add(new Planned(idOf(def, e.getKey()), e.getKey(), amount));
        }
        return out;
    }

    /**
     * 全部专业按当前等级重下发：**先按前缀清场，再按 {@link #planGrants} 加**。
     * 返回挂上的 modifier 条数（{@code level < 2} 时是 0，但清场照做）。
     */
    public static int applyAttributes(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        ProfessionRegistry reg = RedtaleCore.get().professions();
        ProgressStore store = ProgressStore.get(player.server);
        int added = 0;
        for (ProfessionSpec.Definition def : reg.all().values()) {
            removeByPrefix(player, prefixOf(def.id()));
            int level = levelOf(def, store.profExp(player.getUUID(), def.id()));
            for (Planned m : planGrants(def, level)) {
                if (addOne(player, m)) {
                    added++;
                }
            }
        }
        return added;
    }

    /** 清掉某人身上全部专业 modifier（删号 / 调试用）；返回移除条数。 */
    public static int removeAll(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return removeByPrefix(player, RedtaleProject.MOD_ID + ":" + ID_ROOT);
    }

    /** 当前挂在玩家身上的专业 modifier（{@code modifier id → 数值}），供命令与探针观测。 */
    public static Map<String, Double> snapshot(ServerPlayer player) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (player == null) {
            return out;
        }
        String prefix = RedtaleProject.MOD_ID + ":" + ID_ROOT;
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            if (instance == null) {
                continue;
            }
            for (AttributeModifier m : instance.getModifiers()) {
                if (m.id() != null && m.id().toString().startsWith(prefix)) {
                    out.put(m.id().toString(), m.amount());
                }
            }
        }
        return out;
    }

    // ---- 生命周期 ----

    /** 登入：按当前等级重下发（改过曲线 / 配置后重登即按新值，§6）。 */
    public static int onLogin(ServerPlayer player) {
        int n = applyAttributes(player);
        if (n > 0) {
            LOG.debug("专业属性重下发：{} · modifier {} 条", player.getGameProfile().getName(), n);
        }
        return n;
    }

    /** {@code /redtale reload} 后：全部在线玩家按新曲线重算重下发。 */
    public static int applyAll(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int players = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            applyAttributes(p);
            players++;
        }
        return players;
    }

    // ---- 内部 ----

    private static boolean addOne(ServerPlayer player, Planned m) {
        if (m == null || m.attribute() == null) {
            return false;
        }
        AttributeSpec spec = AttributeSpecs.resolve(m.attribute());
        if (spec == null) {
            return false;
        }
        Holder<Attribute> holder = RedtaleAttributes.holder(spec);
        if (holder == null) {
            return false;
        }
        AttributeInstance instance = player.getAttribute(holder);
        if (instance == null) {
            return false;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, m.id()),
                m.amount(), AttributeModifier.Operation.ADD_VALUE));
        return true;
    }

    /** 按 {@code redtale_project:...} 前缀扫全部可同步属性；不按声明清单（R53-b）。 */
    private static int removeByPrefix(ServerPlayer player, String prefix) {
        int removed = 0;
        Collection<AttributeInstance> all = player.getAttributes().getSyncableAttributes();
        for (AttributeInstance instance : all) {
            if (instance == null) {
                continue;
            }
            List<ResourceLocation> hit = new ArrayList<>();
            for (AttributeModifier m : instance.getModifiers()) {
                if (m.id() != null && m.id().toString().startsWith(prefix)) {
                    hit.add(m.id());
                }
            }
            for (ResourceLocation id : hit) {
                instance.removeModifier(id);
                removed++;
            }
        }
        return removed;
    }

    /** 去命名空间：{@code redtale_project:mining} → {@code mining}；无冒号原样（trim）。 */
    private static String pathOf(String id) {
        if (id == null) {
            return "";
        }
        String s = id.trim();
        int i = s.indexOf(':');
        return i < 0 ? s : s.substring(i + 1);
    }

    /** tag 集合统一成 {@code ProfessionSpec.resolve} 要的形态（不带 {@code #}）。 */
    private static Set<String> bareAll(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String t : tags) {
            out.add(bare(t));
        }
        return out;
    }

    private static String bare(String tag) {
        if (tag == null) {
            return "";
        }
        String s = tag.trim();
        return s.startsWith("#") ? s.substring(1) : s;
    }
}
