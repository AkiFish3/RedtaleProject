package com.redtale.project.core.command;

import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.script.ScriptCall;
import com.redtale.project.core.skill.SkillSpec;
import com.redtale.project.items.AffixPools;
import com.redtale.project.items.ItemBase;
import com.redtale.project.items.ItemContent;
import com.redtale.project.items.SetSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /redtale validate} —— 引用完整性只读检查（M3 配套，T-0015）。
 *
 * <p>扫四类「引用目标不存在」的静默失效：{@code weapon_attributes} 指向没写的定义文件、词条池引用删掉的词条、
 * {@code applies_to} 不是 14 基底之一、权重合计 {@code <= 0} 的池永远摇不出东西。它们都不报错、不崩，
 * 只是不生效。**只读**：不写盘、不改内存状态、不打日志。
 *
 * <p>返回的是「引用问题」条数，不是检查项总数。输出顺序固定（内容按 id 字典序、池按 {@code affixPools()}
 * 迭代序），同一份配置两次运行逐字相同。
 */
public final class ValidateCommand {
    private static final String NS = "redtale_project";
    private static final String WA_DIR = "weapon_attributes/";

    private ValidateCommand() {
    }

    /** 返回「引用问题」条数（不是检查项总数）。 */
    public static int validate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        List<ConfigFile> configs;
        try {
            configs = core.config().loadAll();
        } catch (IOException e) {
            src.sendFailure(Component.literal("[redtale] 校验失败：读取配置失败（" + e.getMessage() + "）"));
            return 0;
        }
        Set<String> paths = new HashSet<>();
        for (ConfigFile file : configs) {
            paths.add(file.path());
        }
        Map<String, AffixPools.Def> defs = core.affixDefs();
        Map<String, AffixPools.Pool> pools = core.affixPools();
        Map<String, SetSpec.Definition> sets = core.sets();
        List<ItemContent> contents = new ArrayList<>(core.contentItems().all());
        contents.sort(Comparator.comparing(ItemContent::id));

        List<String> problems = new ArrayList<>();
        int external = 0;
        for (ItemContent content : contents) {
            String ref = content.weaponAttributes();
            if (ref == null || ref.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(ref);
            if (id == null) {
                problems.add("  " + content.id() + ": weapon_attributes 的 id 非法（" + ref + "）");
                continue;
            }
            // 别人 jar 里的内置项我们无法校验：外部命名空间只计数，不报问题
            if (!NS.equals(id.getNamespace())) {
                external++;
                continue;
            }
            if (!paths.contains(WA_DIR + id.getPath() + ".json5")) {
                problems.add("  " + content.id() + ": weapon_attributes " + ref
                        + " 缺定义文件 " + WA_DIR + id.getPath() + ".json5");
            }
        }
        for (AffixPools.Pool pool : pools.values()) {
            Set<String> reported = new LinkedHashSet<>();
            int weightSum = 0;
            for (AffixPools.Entry entry : pool.entries()) {
                weightSum += entry.weight();
                // 同一池里同一个 id 只报一次（同一槽位可能被 prefix/suffix 各绑一遍）
                if (!defs.containsKey(entry.affixId()) && reported.add(entry.affixId())) {
                    problems.add("  " + pool.id() + ": 引用了未定义的词条 " + entry.affixId());
                }
            }
            for (String raw : pool.appliesTo()) {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) {
                    problems.add("  " + pool.id() + ": applies_to 的 id 非法（" + raw + "）");
                } else if (NS.equals(id.getNamespace()) && ItemBase.byId(id.getPath()) == null) {
                    problems.add("  " + pool.id() + ": applies_to 的 " + raw + " 不是 14 基底之一");
                }
                // R21-e：裸名会被 ResourceLocation 补成 minecraft:<raw>，上一条判定查不出这种笔误
                if (!raw.contains(":")) {
                    problems.add("  " + pool.id() + ": applies_to 的 " + raw + " 没写命名空间（会被当成 minecraft:"
                            + raw + "）");
                }
            }
            if (pool.entries().isEmpty() || weightSum <= 0) {
                problems.add("  " + pool.id() + ": 权重合计 <= 0，永远不会被摇到");
            }
        }
        // 第 5 条（T-0018）：套装引用双向核对。(a) 定义里的 piece 必须存在；(b) 物品的 set_id 必须有定义
        List<String> setIds = new ArrayList<>(sets.keySet());
        setIds.sort(Comparator.naturalOrder());
        for (String setId : setIds) {
            for (String piece : sets.get(setId).pieces()) {
                if (core.contentItems().get(piece) == null) {
                    problems.add("  " + setId + ": piece " + piece + " 不存在");
                }
            }
        }
        for (ItemContent content : contents) {
            String setId = content.setId();
            if (!setId.isEmpty() && !sets.containsKey(setId)) {
                problems.add("  " + content.id() + ": set_id " + setId
                        + " 没有对应套装定义（写错一个字母就静默不生效）");
            }
        }
        // 第 6 条（T-0025）：技能引用的原语必须在启动期冻结的 ScriptRegistries 里存在
        checkSkillPrimitives(core, problems);
        final int k = problems.size();
        final int ext = external;
        final int sc = sets.size();
        final int skc = core.skills().size();
        src.sendSuccess(() -> Component.literal("校验：" + contents.size() + " 件内容 / " + pools.size()
                + " 个词条池 / " + defs.size() + " 条词条定义 / " + sc + " 个套装 · 技能 " + skc + " 条 · 问题 " + k
                + " 条（外部 weapon_attributes 引用 " + ext + " 条未校验）"), false);
        if (k == 0) {
            src.sendSuccess(() -> Component.literal("  （没有引用问题）"), false);
        } else {
            for (String line : problems) {
                src.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return k;
    }

    /** 第 6 条（T-0025）：技能里写的 condition / effect 类型有没有对应原语。同一技能同种同 type 只报一条。 */
    private static void checkSkillPrimitives(RedtaleCore core, List<String> problems) {
        for (Map.Entry<String, SkillSpec.Definition> entry : core.skills().all().entrySet()) {
            Set<String> badConditions = new LinkedHashSet<>();
            Set<String> badEffects = new LinkedHashSet<>();
            for (ScriptCall call : entry.getValue().conditions()) {
                String type = call.type() == null ? "" : call.type();
                if (core.registries().condition(type) == null && badConditions.add(type)) {
                    problems.add("  " + entry.getKey() + ": conditions 引用了未注册的原语 " + type);
                }
            }
            for (ScriptCall call : entry.getValue().effects()) {
                String type = call.type() == null ? "" : call.type();
                if (core.registries().effect(type) == null && badEffects.add(type)) {
                    problems.add("  " + entry.getKey() + ": effects 引用了未注册的原语 " + type);
                }
            }
        }
    }
}
