package com.redtale.project.mobs;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.skill.SkillEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 怪物技能释放（M6 第三步第 3 小步）：把 {@code MobSpec.skills} 的 {@code chance} / {@code cooldown_ticks}
 * 变成真的施法。
 *
 * <p><b>不新建触发器体系</b>（SKILLS §2）：这里只做两件事 —— ① 找到「谁是我的目标」，
 * ② 按配置掷骰后调 {@link SkillEngine#castMob}。条件、冷却、effects 全在引擎里，
 * 所以玩家技能与怪物技能是**同一条执行链**（改了原语两边同时生效，不会出现第二份真相）。
 *
 * <p><b>状态为零</b>：冷却记在 {@code SkillEngine.cooldowns()}（键 = 怪物 UUID + 技能 id），
 * 掷骰用 {@code mob.getRandom()}。因此本类没有跨 tick 的成员状态、reload 不需要清理、
 * 重启也不会残留半截施法 —— 与 R37-b「AI 不跑时物理照跑」的观察方式无关。
 *
 * <p><b>节拍 {@value #INTERVAL_TICKS} tick</b>：每 tick 遍历全服实体会让 271 模组的整合包多一份
 * 常驻开销；怪物 AI 的决策粒度本来就在秒级，10 tick（0.5s）足够，且让「掷骰失败」不是每 tick 重试。
 *
 * <p>只在服务端主线程调用，不加锁。
 */
public final class MobSkillCaster {

    private static final Logger LOG = LoggerFactory.getLogger("redtale/mobskill");

    /** 节拍：每多少 tick 掷一次骰。 */
    public static final int INTERVAL_TICKS = 10;

    private MobSkillCaster() {
    }

    /** 一次施法尝试的结果；{@code skillId} 为空表示「一个技能都没试着放」。 */
    public record Result(boolean cast, String skillId, SkillEngine.Status status, int effects, String detail) {

        static Result none(String detail) {
            return new Result(false, null, null, 0, detail);
        }
    }

    /**
     * 周期入口（{@code GameHooks#onServerTickPost} 每 tick 调，内部自己判节拍）。
     * 返回本轮真的放出去的技能条数（日志/验收用；正常运行期不读返回值）。
     */
    public static int tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % INTERVAL_TICKS != 0) {
            return 0;
        }
        RedtaleCore core = RedtaleCore.get();
        if (core == null || core.mobs().size() == 0) {
            return 0;
        }
        int cast = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob)) {
                    continue;
                }
                String contentId = contentIdOf(mob);
                if (contentId == null) {
                    continue;
                }
                MobSpec.Definition def = core.mobs().get(contentId);
                if (def == null || def.skills().isEmpty()) {
                    continue;
                }
                // 目标由原版 AI 给（getTarget）；没有目标就不掷骰 —— 否则冷却被「空放」吃掉
                LivingEntity target = mob.getTarget();
                if (target == null || !target.isAlive()) {
                    continue;
                }
                if (tryCast(mob, def, target).cast()) {
                    cast++;
                }
            }
        }
        return cast;
    }

    /**
     * 按配置顺序掷骰：每个技能独立掷一次 {@code chance}，第一个真的放出去的胜出（一轮最多一个）。
     *
     * <p>掷中了但**门槛没过**（冷却中 / 条件不满足）继续试下一个技能 —— 这不是 bug：
     * 「这只怪现在怒气不够但能放另一个」是配置写出来的组合，硬停会让整套技能只剩第一个可用。
     */
    public static Result tryCast(Mob mob, MobSpec.Definition def, LivingEntity target) {
        if (mob == null || def == null || target == null) {
            return Result.none("缺少施法者或目标");
        }
        SkillEngine engine = RedtaleCore.get().skillEngine();
        List<String> tried = new ArrayList<>();
        for (MobSpec.SkillRef ref : def.skills()) {
            if (ref.chance() <= 0.0D) {
                tried.add(ref.skillId() + "(chance 0)");
                continue;
            }
            if (ref.chance() < 1.0D && mob.getRandom().nextDouble() >= ref.chance()) {
                tried.add(ref.skillId() + "(未掷中)");
                continue;
            }
            SkillEngine.Result result = engine.castMob(mob, target, ref.skillId(), ref.cooldownTicks());
            if (result.status() == SkillEngine.Status.CAST) {
                LOG.info("[mob] {} 对 {} 施放 {}（{} 条 effect）", def.id(), target.getName().getString(),
                        ref.skillId(), result.effects());
                return new Result(true, ref.skillId(), result.status(), result.effects(), null);
            }
            tried.add(ref.skillId() + "(" + result.status()
                    + (result.remainingTicks() > 0 ? " " + result.remainingTicks() + "t" : "") + ")");
        }
        // 掷不中与「掷中了但门槛没过」是两件事，输出里分开列（否则调 chance 时看不出是冷却在挡）
        return Result.none("没有技能通过掷骰或门槛：" + String.join("、", tried));
    }

    /**
     * 不管 {@code chance}，按顺序试到放出去为止（命令验收用）。
     *
     * <p>存在的理由：验收要能复现地打一发，而 {@code chance: 0.25} 的怪十次里七次半不出手 ——
     * 把「掷骰」和「执行」分开验，才不会把配置概率当成链路故障。
     */
    public static Result forceCast(Mob mob, MobSpec.Definition def, LivingEntity target) {
        if (mob == null || def == null || target == null) {
            return Result.none("缺少施法者或目标");
        }
        SkillEngine engine = RedtaleCore.get().skillEngine();
        List<String> tried = new ArrayList<>();
        for (MobSpec.SkillRef ref : def.skills()) {
            // 记下每条的真实去向（冷却中/条件不过/没这个技能），否则「全失败」看不出是冷却还是故障
            tried.add(ref.skillId());
            SkillEngine.Result result = engine.castMob(mob, target, ref.skillId(), ref.cooldownTicks());
            tried.set(tried.size() - 1, ref.skillId() + "(" + result.status()
                    + (result.remainingTicks() > 0 ? " " + result.remainingTicks() + "t" : "") + ")");
            if (result.status() == SkillEngine.Status.CAST) {
                LOG.info("[mob] {} 对 {} 强制施放 {}（{} 条 effect）", def.id(),
                        target.getName().getString(), ref.skillId(), result.effects());
                return new Result(true, ref.skillId(), result.status(), result.effects(), null);
            }
        }
        return Result.none("全部失败：" + String.join("、", tried));
    }

    /** 从 scoreboard tag 取内容 id（{@code redtale_mob/<怪id>}）；手动 {@code /summon} 的怪没有 tag → null。 */
    static String contentIdOf(Mob mob) {
        for (String tag : mob.getTags()) {
            if (tag.startsWith(MobSpawner.TAG_PREFIX)) {
                String id = tag.substring(MobSpawner.TAG_PREFIX.length());
                if (!id.isEmpty()) {
                    return id;
                }
            }
        }
        return null;
    }
}
