package com.redtale.project.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.skill.SkillRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /redtale mob check} —— 怪物引用完整性只读校验（M6 配套，T-0032）。
 *
 * <p>只读三处现成快照（怪物 / 技能 / 物品注册表），不读盘、不重新解析：装备槽写了内容 id 或写错、
 * 技能引用了不存在的技能，解析层与快照层都不报错 —— 这里把它们变成一张能对账的清单。
 *
 * <p>输出顺序固定（怪物配置序，每只先装备后技能），不排序、不去重、不聚合；两次运行逐字相同。
 */
public final class MobCheckCommand {

    private static final List<String> SLOTS =
            List.of("mainhand", "offhand", "head", "chest", "legs", "feet");
    private static final int MAX_LISTED = 5;

    private MobCheckCommand() {
    }

    /** 命令节点：{@code /redtale mob check}（挂到已存在的 {@code redtale} 树，Brigadier 按同名合并）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("mob")
                .then(Commands.literal("check").executes(MobCheckCommand::check));
    }

    /** 返回「问题」条数（不是检查项总数，与 {@code SetCheckCommand} 同口径）。 */
    public static int check(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        var mobs = core.mobs();
        if (mobs.size() == 0) {
            src.sendSuccess(() -> Component.literal("[redtale] 没有怪物内容（读 "
                    + core.config().root().resolve("mobs") + "/*.json5）"), false);
            return 0;
        }
        var skills = core.skills();
        var items = BuiltInRegistries.ITEM;
        List<String> problems = new ArrayList<>();
        for (var def : mobs.all().values()) {
            for (String slot : SLOTS) {
                String id = def.equipment().slot(slot);
                if (id.isEmpty()) continue;
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null || !items.containsKey(rl)) {
                    problems.add("  " + def.name() + " " + def.id() + ": equipment." + slot + " "
                            + id + " 不是已知物品 id");
                }
            }
            for (var ref : def.skills()) {
                String skillId = ref.skillId();
                if (!skills.has(skillId)) {
                    problems.add("  " + def.name() + " " + def.id() + ": 技能 " + skillId
                            + " 不存在" + existing(skills));
                }
            }
        }
        int k = problems.size();
        String head = "[redtale] 怪物检查：" + mobs.size() + " 只 / 技能引用 " + mobs.skillCount()
                + " 条" + (k == 0 ? " · 引用完整 ✓" : " / 问题 " + k + " 个");
        src.sendSuccess(() -> Component.literal(head), false);
        for (String line : problems) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return k;
    }

    /** 「（现有 N 条：a, b, c…）」——最多列 5 个；一条都没有时只写条数。 */
    private static String existing(SkillRegistry skills) {
        List<String> ids = skills.sortedIds();
        if (ids.isEmpty()) return "（现有 0 条）";
        String listed = String.join(", ", ids.subList(0, Math.min(MAX_LISTED, ids.size())));
        return "（现有 " + ids.size() + " 条：" + listed
                + (ids.size() > MAX_LISTED ? "…" : "") + "）";
    }
}
