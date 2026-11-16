package com.redtale.project.core.script;

/**
 * trigger 原语：只描述「什么时机能触发脚本」，真正的游戏事件钩子在
 * {@code core.hook} 里注册（M0 只有手动与玩家登录两个）。
 *
 * <p>与 effect 一样是启动期冻结的注册表项。
 */
public interface TriggerType {

    /** 配置里写的 id，全小写下划线。 */
    String id();

    /** 一句话说明，会写进 README。 */
    String description();

    /** 该触发是否一定带玩家上下文。false 时脚本里的 effect 必须自己容错。 */
    boolean requiresPlayer();
}
