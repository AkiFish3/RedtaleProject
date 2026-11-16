package com.redtale.project.core.script;

import java.util.List;

/**
 * 一条已解析并校验通过的脚本。
 *
 * @param id            全工程唯一的脚本 id（配置里写，重复会报错）
 * @param trigger       trigger 原语 id
 * @param conditions    全部为 true 才执行 effects
 * @param effects       顺序执行
 * @param cooldownTicks 同一玩家两次执行的最小间隔，0 = 无冷却
 * @param source        来源文件（相对 config/redtale_project 的路径），报错定位用
 */
public record ScriptAction(
        String id,
        String trigger,
        List<ScriptCall> conditions,
        List<ScriptCall> effects,
        int cooldownTicks,
        String source
) {
}
