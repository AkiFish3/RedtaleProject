package com.redtale.project.core;

import com.redtale.project.core.attr.AttackSpeedRules;
import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.config.ConfigStore;
import com.redtale.project.core.damage.DamageTrace;
import com.redtale.project.core.math.DamageRules;
import com.redtale.project.core.script.ScriptContext;
import com.redtale.project.core.script.ScriptEngine;
import com.redtale.project.core.script.ScriptRegistries;
import com.redtale.project.core.script.builtin.Builtins;
import com.redtale.project.items.WeaponAttributesGenerator;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行期单例：持有三张原语注册表、配置目录与脚本执行器。
 *
 * <p>原语注册表在构造期一次性建好（启动期冻结），之后 reload 只换**数据**
 * （脚本列表），不动注册表 —— 这正是 PLAN §9 的热更新边界。
 */
public final class RedtaleCore {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject");

    private static final RedtaleCore INSTANCE = new RedtaleCore();

    private final ScriptRegistries registries = new ScriptRegistries();
    private final ConfigStore config;
    private final ScriptEngine engine;

    private boolean active;
    private String lastError = "";
    private List<String> lastErrorLines = List.of();

    /** 攻速来源表（D18）。volatile：mixin 在游戏线程读，reload 在命令线程写。 */
    private volatile AttackSpeedRules attackSpeedRules = AttackSpeedRules.DEFAULT;
    /** 伤害闸门 + 异常保护（D16）。 */
    private volatile DamageRules damageRules = DamageRules.DEFAULT;
    /** 近战暴击入口（M2）。 */
    private volatile com.redtale.project.core.damage.CritRules critRules =
            com.redtale.project.core.damage.CritRules.DEFAULT;
    /** 全部内容定义（M2：装备）。不可变快照，reload 整份换掉。 */
    private volatile com.redtale.project.items.ItemContentRegistry contentItems =
            com.redtale.project.items.ItemContentRegistry.EMPTY;
    /** 词条定义（D22，R11）：{@code affixes/*.json5} 的解析结果。 */
    private volatile java.util.Map<String, com.redtale.project.items.AffixPools.Def> affixDefs = java.util.Map.of();
    /** 词条池（D22，R11）：{@code affix_pools/*.json5} 的解析结果。 */
    private volatile java.util.Map<String, com.redtale.project.items.AffixPools.Pool> affixPools = java.util.Map.of();
    /** 套装定义（M3，{@code sets/*.json5}）。与内容分开存：套装是「玩家穿了一组」的派生态，不是物品属性。 */
    private volatile java.util.Map<String, com.redtale.project.items.SetSpec.Definition> sets = java.util.Map.of();
    /** 技能定义（M5-A，{@code skills/*.json5}）。快照整份换掉；执行在 SkillEngine（冷却表 T-0022 落地后接）。 */
    private volatile com.redtale.project.core.skill.SkillRegistry skills = com.redtale.project.core.skill.SkillRegistry.EMPTY;
    /** 技能执行器（M5-A）：持有冷却表。**跨 reload 存活**（reload 清冷却 = 免费刷技能）。 */
    private final com.redtale.project.core.skill.SkillEngine skillEngine = new com.redtale.project.core.skill.SkillEngine();
    /** 最近一次 reload 的非致命告警（配置写歪了但被默认值兜住）。 */
    private volatile List<String> configWarnings = List.of();

    private RedtaleCore() {
        Builtins.registerAll(registries);
        this.config = new ConfigStore(FMLPaths.CONFIGDIR.get());
        this.engine = new ScriptEngine(registries, LOG);
    }

    public static RedtaleCore get() {
        return INSTANCE;
    }

    public ScriptRegistries registries() {
        return registries;
    }

    public ConfigStore config() {
        return config;
    }

    public ScriptEngine engine() {
        return engine;
    }

    /** 攻速来源表（D18）。mixin 与 {@code /redtale attr} 读同一份。 */
    public AttackSpeedRules attackSpeedRules() {
        return attackSpeedRules;
    }

    /** 伤害闸门 + 异常保护（D16）。 */
    public DamageRules damageRules() {
        return damageRules;
    }

    /** 近战暴击入口（M2）：{@code crit.json5} 的解析结果。 */
    public com.redtale.project.core.damage.CritRules critRules() {
        return critRules;
    }

    /** 全部内容定义（M2 装备）。 */
    public com.redtale.project.items.ItemContentRegistry contentItems() {
        return contentItems;
    }

    /** 词条定义表（D22）。为空 = 没配词条，装备不带词条。 */
    public java.util.Map<String, com.redtale.project.items.AffixPools.Def> affixDefs() {
        return affixDefs;
    }

    /** 词条池（D22）。{@code applies_to} 命中基底才会被 roll。 */
    /** 套装定义（M3）。reload 整份换掉；件数结算见 {@code items.SetBonus}。 */
    public java.util.Map<String, com.redtale.project.items.SetSpec.Definition> sets() {
        return sets;
    }

    public java.util.Map<String, com.redtale.project.items.AffixPools.Pool> affixPools() {
        return affixPools;
    }

    /** 技能执行器（M5-A）：{@code /redtale skill cast} 与 M6 怪物 AI 共用同一个冷却表。 */
    public com.redtale.project.core.skill.SkillEngine skillEngine() {
        return skillEngine;
    }

    /** 技能定义（M5-A）。为空 = 没有技能内容，命令与怪物 AI 都拿不到东西。 */
    public com.redtale.project.core.skill.SkillRegistry skills() {
        return skills;
    }

    /** 最近一次 reload 的非致命告警。 */
    public List<String> configWarnings() {
        return configWarnings;
    }

    /** 供事件钩子调用：按 trigger 分发脚本。 */
    public void fire(String triggerId, ScriptContext ctx) {
        if (!active) {
            return;
        }
        engine.fire(triggerId, ctx);
    }

    /**
     * 重载全部配置。
     *
     * <p>两种失败分开处理：
     * <ul>
     *   <li><b>文件语法错误</b> —— 读取阶段就中断，整个 reload 不生效，上一份配置继续跑。</li>
     *   <li><b>单条脚本语义错误</b>（未知 trigger/effect、缺字段…）—— 只跳过那一条，
     *       其余照常生效，错误一次性全报。</li>
     * </ul>
     * 线上改配置写漏一个逗号不该让整套脚本停摆，也不该静默。
     */
    public ReloadResult reload() {
        try {
            config.ensureDefaults();
            List<ConfigFile> all = config.loadAll();

            // 数值规则先解析（D16/D18）：语法错误已在 loadAll 阶段整体回滚，
            // 这里只处理「字段写歪」这类可由默认值兜住的问题 —— 记告警，不中断。
            List<String> warnings = new ArrayList<>();
            attackSpeedRules = AttackSpeedRules.parse(find(all, "attack_speed.json5"), warnings);
            damageRules = DamageRules.parse(find(all, "damage.json5"), warnings);
            critRules = com.redtale.project.core.damage.CritRules.parse(find(all, "crit.json5"), warnings);
            contentItems = com.redtale.project.items.ItemContentRegistry.load(all, warnings);
            // 词条（D22）：定义与池都走同一份配置列表，解析失败只告警不中断（R1-a）
            affixDefs = com.redtale.project.items.AffixPools.parseDefs(all, warnings);
            affixPools = com.redtale.project.items.AffixPools.parsePools(all, warnings);
            // 套装（M3）：同样是纯解析，失败只告警；下发在 SetBonus（件数变化时重算）
            sets = com.redtale.project.items.SetSpec.parseAll(all, warnings);
            // 技能（M5-A）：解析层不校验原语是否存在（那需要启动期注册表），交给执行期与 /redtale validate
            skills = com.redtale.project.core.skill.SkillRegistry.load(all, warnings);
            net.minecraft.server.MinecraftServer srv = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (srv != null) { WeaponAttributesGenerator.generateAll(srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("redtale_project"), all, contentItems.all(), warnings); } else { LOG.warn("weapon_attributes 生成跳过：当前没有服务端实例"); }
            // M4 / D32 的 L1→L0：把 enchantments/*.json5 写成真·原版 Enchantment JSON。
            // 与 weapon_attributes 同一个数据包根，靠 PackEnabler 一次选中一次重载。
            if (srv != null) { com.redtale.project.enchants.EnchantmentGenerator.generateAll(srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("redtale_project"), all, warnings); } else { LOG.warn("enchantments 生成跳过：当前没有服务端实例"); }
            // R23：14 基底不在原版 #minecraft:enchantable/* 里 → /enchant 报 cannot support（R22 实测）。
            // 这里往原版 tag 里**追加**（replace:false），并给缺失的自家 exclusive_set tag 补空文件。
            if (srv != null) { com.redtale.project.enchants.EnchantableTags.generateAll(srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("redtale_project"), all, warnings); } else { LOG.warn("enchantable tag 生成跳过：当前没有服务端实例"); }
            // R27 / T-0020：不写这三条原版可获取性 tag，附魔台刷不出、村民不卖、战利品不掉（V4 卡在这）
            if (srv != null) { com.redtale.project.enchants.EnchantObtainTags.generateAll(srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("redtale_project"), all, warnings); } else { LOG.warn("obtain tag 生成跳过：当前没有服务端实例"); }
            // R8-1：写出来的包必须被选中 + 重载才会进注册表，否则要等重启（R7 实测）
            if (srv != null) { LOG.info("生成数据包状态：{}", com.redtale.project.core.datapack.PackEnabler.ensureEnabled(srv, "redtale_project", warnings)); }
            configWarnings = List.copyOf(warnings);
            // 观测数据跟着配置一起换代：上一份规则的命中率不该算进这一份
            DamageTrace.reset();
            com.redtale.project.core.damage.CritLayer.reset();
            for (String w : warnings) {
                LOG.warn("配置告警：{}", w);
            }

            // 套装跟着配置换代：在线玩家的件数签名要作废并重算（否则改 sets/ 后不生效）
            if (srv != null) {
                for (net.minecraft.server.level.ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    com.redtale.project.items.SetBonus.clear(p);
                    com.redtale.project.items.SetBonus.apply(p);
                }
            }

            ScriptEngine.Outcome outcome = engine.load(ConfigStore.ofDirectory(all, "scripts"));
            config.writeReadme(registries);
            active = true;

            String tail = warnings.isEmpty() ? "" : "，" + warnings.size() + " 条告警";
            if (outcome.clean()) {
                lastError = "";
                lastErrorLines = List.of();
                LOG.info("配置重载完成：{} 条脚本，{} 个文件，{} 件装备内容，{}。攻速：{}",
                        outcome.loaded(), all.size(), contentItems.size(), skills.describe(),
                        attackSpeedRules.describe());
                return new ReloadResult(true,
                        "已载入 " + outcome.loaded() + " 条脚本 + " + contentItems.size()
                                + " 件装备内容 + " + skills.size() + " 条技能（" + all.size() + " 份配置文件）" + tail,
                        List.of());
            }

            lastErrorLines = outcome.errors();
            lastError = "有 " + outcome.errors().size() + " 处错误被跳过";
            LOG.error("脚本配置有 {} 处错误，已跳过（仍生效 {} 条）：\n{}",
                    outcome.errors().size(), outcome.loaded(), String.join("\n", outcome.errors()));
            return new ReloadResult(false,
                    "已载入 " + outcome.loaded() + " 条脚本，另有 " + outcome.errors().size()
                            + " 处错误被跳过" + tail,
                    lastErrorLines);
        } catch (ConfigStore.ConfigException e) {
            lastError = String.valueOf(e.getMessage());
            lastErrorLines = List.of(lastError);
            LOG.error("配置文件解析失败（当前仍在用上一份配置）：{}", lastError);
            return new ReloadResult(false, "配置文件解析失败" + (active ? "（仍在使用上一份配置）" : "（没有可用配置）"),
                    lastErrorLines);
        } catch (Exception e) {
            lastError = String.valueOf(e.getMessage());
            lastErrorLines = List.of(lastError);
            LOG.error("配置重载失败", e);
            return new ReloadResult(false, "配置重载失败：" + lastError, lastErrorLines);
        }
    }

    public boolean ready() {
        return active;
    }

    /** 按相对路径取一份配置（{@code attack_speed.json5} 这类单文件配置用）；没有则返回 null。 */
    private static com.google.gson.JsonObject find(List<ConfigFile> all, String path) {
        for (ConfigFile file : all) {
            if (file.path().equalsIgnoreCase(path)) {
                return file.root();
            }
        }
        return null;
    }

    public String lastError() {
        return lastError;
    }

    public List<String> lastErrorLines() {
        return lastErrorLines;
    }

    public record ReloadResult(boolean ok, String summary, List<String> errors) {
    }
}
