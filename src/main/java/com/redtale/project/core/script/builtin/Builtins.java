package com.redtale.project.core.script.builtin;

import com.redtale.project.core.script.ScriptRegistries;
import com.redtale.project.core.script.TriggerType;

/**
 * 内置原语统一登记处。M0 只放最小集，M1 起按 PLAN §7 的 24 条原语清单补齐。
 */
public final class Builtins {

    private Builtins() {
    }

    public static void registerAll(ScriptRegistries registries) {
        // effects
        registries.register(new MessageEffect());
        registries.register(new ConsoleEffect());
        // M4 的 soul_reap 执行体（R24）：附魔 post_attack → scripted → 本原语
        registries.register(new HealEffect());
        // M5-A 第一条直伤原语（R27）：只给白值，倍率由 DamageLayer 乘（SKILLS §5 红线 1）
        registries.register(new DamageEffect());

        // conditions
        registries.register(new AlwaysCondition());
        registries.register(new HealthBelowCondition());
        registries.register(new ChanceCondition());

        // triggers
        registries.register(new ManualTrigger());
        registries.register(new PlayerJoinTrigger());
    }

    /** /redtale fire 与调试用；不接游戏事件。 */
    public record ManualTrigger() implements TriggerType {

        @Override
        public String id() {
            return "manual";
        }

        @Override
        public String description() {
            return "只能由 /redtale fire <脚本id> 触发，用于冒烟测试";
        }

        @Override
        public boolean requiresPlayer() {
            return false;
        }
    }

    /** 玩家登录。M0 的验收触发点。 */
    public record PlayerJoinTrigger() implements TriggerType {

        @Override
        public String id() {
            return "player_join";
        }

        @Override
        public String description() {
            return "玩家进入服务器时触发，带玩家上下文";
        }

        @Override
        public boolean requiresPlayer() {
            return true;
        }
    }
}
