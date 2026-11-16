package com.redtale.project.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Map;

/**
 * 唯一的内容数据组件（D17 / HOT-UPDATE §3）：**所有**内容共用这一个组件类型。
 *
 * <p>为什么是一个而不是每个内容一个：{@code DataComponentType} 是启动期冻结的注册表项，
 * 每加一种内容就注册一个组件，等于每加内容都换 jar。用「一个组件 + 任意字段」把这条边界
 * 挪到数据侧 —— 以后加「耐久词条」「附魔槽」，往这里加字段即可，旧物品缺字段取默认值。
 *
 * <p>字段口径：
 * <ul>
 *   <li>{@code contentId} —— 我们自己的内容 id（{@code red_maple_longsword}），不是物品注册 id。
 *       物品注册 id 只有 14 个基底，身份在组件里（HOT-UPDATE §6 的代价，已接受）。</li>
 *   <li>{@code rolledStats} —— **这一件实例**的最终数值（掉落时 roll 好写进来）。
 *       tooltip / 结算 / {@code /redtale attr} 三处读同一份，杜绝「写着 +3 打出 +5」（D22）。</li>
 *   <li>{@code affixes} / {@code abilities} —— 词条 id 与技能 id 列表（M2 后续、M5 接线）。</li>
 *   <li>{@code weaponAttributes} —— BetterCombat 的 datapack registry id（D15）。空串 = 没有。
 *       它只是**字符串**，我们从不 import BetterCombat。</li>
 *   <li>{@code schema} —— **组件结构版本**（D33）。改字段名 / 改语义时 +1，用于 codec 迁移。
 *       与内容数值无关。</li>
 *   <li>{@code contentRev} —— **这件物品是基于哪一版内容定义生成的**（取自 json5 的 {@code rev}）。
 *       它是「物品更新系统」的判据：{@code contentRev != 当前定义 rev} → 重建定义型字段，
 *       实例型字段（rolledStats / affixes / upgradeLevel）保持不动。详见 {@code docs/ITEM-UPDATE.md}。</li>
 *   <li>{@code rolledKeys} —— **哪些键是真 roll 出来的**（M2 词条系统填充，现在恒空）。
 *       这条决定了「改定义后老物品跟不跟」：
 *       <ul>
 *         <li>键<b>不在</b> rolledKeys → 它是定义给的确定值，迁移时**取新定义的新值**（改配置全服生效）</li>
 *         <li>键<b>在</b> rolledKeys → 它是玩家 roll 出来的，迁移时**保留旧值**（不许冲掉玩家资产）</li>
 *       </ul>
 *       没有这个字段，「改配置全服生效」和「不许重 roll」两条会直接矛盾——
 *       见 {@code docs/ITEM-UPDATE.md} §3.3。</li>
 *   <li>{@code sockets} —— **孔序**（M3，{@code ITEM-MODEL.md} §5.1）：长度等于定义里的孔数，
 *       {@code ""} = 空孔、非空 = 那件宝石的内容 id。玩家资产：迁移与定义变更都**不许**冲掉。</li>
 *   <li>{@code gemStats} —— **宝石贡献的数值**，与 {@code rolledStats} 分开存（M3 口径）：
 *       同一属性若被词条与宝石各加过、并进一个键，拆宝石时会把词条那部分一起扣掉（扣不准）。
 *       拆宝石 = 重算这张表，词条一个数都不动。</li>
 * </ul>
 */
public record ContentData(
        String contentId,
        int contentRev,
        int schema,
        String setId,
        int upgradeLevel,
        Map<String, Double> rolledStats,
        List<String> rolledKeys,
        List<String> affixes,
        List<String> abilities,
        String weaponAttributes,
        List<String> sockets,
        Map<String, Double> gemStats) {

    public ContentData {
        rolledStats = Map.copyOf(rolledStats);
        rolledKeys = List.copyOf(rolledKeys);
        affixes = List.copyOf(affixes);
        abilities = List.copyOf(abilities);
        sockets = List.copyOf(sockets);
        gemStats = Map.copyOf(gemStats);
    }

    /**
     * 当前组件结构版本。改字段结构时 +1，并在 {@code ItemUpgrader} 里加一条迁移分支。
     *
     * <p>2（2026-11-16 R18，M3）：新增 {@code sockets} / {@code gemStats} 两个容器。
     * 老物品（schema 1）缺这两个字段会解成空表，但**孔位布局要与定义对齐**，
     * 所以用 schema 版本触发一次全量重建，而不是等同一次 rev 变更。
     */
    public static final int CURRENT_SCHEMA = 2;

    public static final Codec<ContentData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("content_id").forGetter(ContentData::contentId),
            Codec.INT.optionalFieldOf("content_rev", 0).forGetter(ContentData::contentRev),
            Codec.INT.optionalFieldOf("schema", CURRENT_SCHEMA).forGetter(ContentData::schema),
            Codec.STRING.optionalFieldOf("set_id", "").forGetter(ContentData::setId),
            Codec.INT.optionalFieldOf("upgrade_level", 0).forGetter(ContentData::upgradeLevel),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                    .optionalFieldOf("rolled_stats", Map.of()).forGetter(ContentData::rolledStats),
            Codec.STRING.listOf().optionalFieldOf("rolled_keys", List.of()).forGetter(ContentData::rolledKeys),
            Codec.STRING.listOf().optionalFieldOf("affixes", List.of()).forGetter(ContentData::affixes),
            Codec.STRING.listOf().optionalFieldOf("abilities", List.of()).forGetter(ContentData::abilities),
            Codec.STRING.optionalFieldOf("weapon_attributes", "").forGetter(ContentData::weaponAttributes),
            // M3：孔序（"" = 空孔）与宝石数值。两者都可缺省 → 老物品照常解码，不需要 NBT 迁移。
            Codec.STRING.listOf().optionalFieldOf("sockets", List.of()).forGetter(ContentData::sockets),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                    .optionalFieldOf("gem_stats", Map.of()).forGetter(ContentData::gemStats)
    ).apply(inst, ContentData::new));

    /**
     * 网络同步走「NBT 码编解码」，而不是手写 {@code StreamCodec.composite}：
     * 后者最多 6 个字段，这里已经 10 个，再加字段就要重写。代价是约 100 字节/件的额外开销，
     * 换来「加字段不用改同步代码」—— 与 D17 同一个理由。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ContentData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean hasWeaponAttributes() {
        return !weaponAttributes.isEmpty();
    }

    /** 只换数值，其它字段照抄（M2 词条 roll / 重铸用）。 */
    public ContentData withStats(Map<String, Double> stats) {
        return new ContentData(contentId, contentRev, schema, setId, upgradeLevel, stats, rolledKeys, affixes,
                abilities, weaponAttributes, sockets, gemStats);
    }

    /** 只换修订号（物品更新系统写回用）。 */
    public ContentData withRevision(int rev) {
        return new ContentData(contentId, rev, schema, setId, upgradeLevel, rolledStats, rolledKeys, affixes,
                abilities, weaponAttributes, sockets, gemStats);
    }

    /** 迁移用：换数值 + 换修订号，其它照抄。 */
    public ContentData withStatsAndRevision(Map<String, Double> stats, int rev) {
        return new ContentData(contentId, rev, schema, setId, upgradeLevel, stats, rolledKeys, affixes,
                abilities, weaponAttributes, sockets, gemStats);
    }

    /** M3 镶嵌 / 取出用：只换孔序，其它照抄（宝石数值由调用方另算 {@code gemStats}）。 */
    public ContentData withSockets(List<String> newSockets, Map<String, Double> newGemStats) {
        return new ContentData(contentId, contentRev, schema, setId, upgradeLevel, rolledStats, rolledKeys,
                affixes, abilities, weaponAttributes, newSockets, newGemStats);
    }

    /** 这件物品有没有孔（含空孔也算有，因为「有孔」是定义能力，不是有没有镶东西）。 */
    public boolean hasSockets() {
        return !sockets.isEmpty();
    }
}
