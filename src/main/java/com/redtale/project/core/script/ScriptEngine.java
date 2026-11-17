package com.redtale.project.core.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本层执行器：把 scripts/*.json5 解析成 {@link ScriptAction}，并按 trigger 分发。
 *
 * <p>设计底线（PLAN §9）：脚本内容全是数据，改配置 + /redtale reload 即可生效；
 * 只有 effect / trigger / condition **原语**才需要换 jar。
 *
 * <p>加载采用「先校验、后换入」：任一条配置有错，整体不生效，旧脚本集继续跑。
 * 这样热重载写错一个字符不会把线上脚本清空。
 */
public final class ScriptEngine {

    private final ScriptRegistries registries;
    private final Logger log;

    private volatile List<ScriptAction> actions = List.of();
    private volatile Map<String, List<ScriptAction>> byTrigger = Map.of();
    private volatile Map<String, ScriptAction> byId = Map.of();

    /** 冷却时间戳：actionId + 玩家名 -> 上次执行的 tick。 */
    private final Map<String, Long> cooldownStamp = new HashMap<>();

    public ScriptEngine(ScriptRegistries registries, Logger log) {
        this.registries = registries;
        this.log = log;
    }

    // ------------------------------------------------------------ 加载

    /**
     * 解析并校验全部脚本文件。
     *
     * <p>**部分成功**策略：单条脚本有错只丢弃它，其余照常生效，错误一次性全报。
     * 理由：一个文件写错不该让线上整套脚本停摆，但也不能静默 —— 调用方必须把
     * {@link Outcome#errors()} 原样打给操作者。
     */
    public Outcome load(List<ConfigFile> files) {
        List<String> errors = new ArrayList<>();
        Map<String, ScriptAction> nextById = new LinkedHashMap<>();
        Map<String, List<ScriptAction>> nextByTrigger = new LinkedHashMap<>();

        for (ConfigFile file : files) {
            List<JsonElement> rawScripts = extract(file, errors);
            for (JsonElement raw : rawScripts) {
                if (!raw.isJsonObject()) {
                    errors.add(file.path() + "：scripts 数组里必须是对象");
                    continue;
                }
                ScriptAction action = parseOne(file.path(), raw.getAsJsonObject(), errors);
                if (action == null) {
                    continue;
                }
                ScriptAction previous = nextById.putIfAbsent(action.id(), action);
                if (previous != null) {
                    errors.add(file.path() + "：脚本 id 重复 '" + action.id()
                            + "'（另一处在 " + previous.source() + "）");
                    continue;
                }
                nextByTrigger.computeIfAbsent(action.trigger(), k -> new ArrayList<>()).add(action);
            }
        }

        this.byId = Map.copyOf(nextById);
        this.byTrigger = Map.copyOf(nextByTrigger);
        this.actions = List.copyOf(nextById.values());
        this.cooldownStamp.clear();
        return new Outcome(this.actions.size(), List.copyOf(errors));
    }

    private List<JsonElement> extract(ConfigFile file, List<String> errors) {
        JsonObject root = file.root();
        if (root.has("scripts")) {
            JsonElement scripts = root.get("scripts");
            if (!scripts.isJsonArray()) {
                errors.add(file.path() + "：顶层的 scripts 必须是数组");
                return List.of();
            }
            JsonArray arr = scripts.getAsJsonArray();
            List<JsonElement> out = new ArrayList<>(arr.size());
            arr.forEach(out::add);
            return out;
        }
        if (root.has("id") || root.has("trigger")) {
            return List.of(root);
        }
        errors.add(file.path() + "：既没有 scripts 数组，也不像单条脚本（缺 id / trigger）");
        return List.of();
    }

    private ScriptAction parseOne(String path, JsonObject obj, List<String> errors) {
        int before = errors.size();

        String id = str(obj, "id", path, errors);
        String triggerId = str(obj, "trigger", path, errors);
        int cooldown = optInt(obj, "cooldown_ticks", 0, path, errors);

        if (triggerId != null && registries.trigger(triggerId) == null) {
            errors.add(path + "：未知 trigger '" + triggerId + "'，当前可用："
                    + registries.triggers().stream().map(TriggerType::id).toList());
        }

        List<ScriptCall> conditions = calls(obj, "conditions", path, errors, true);
        List<ScriptCall> effects = calls(obj, "effects", path, errors, false);

        if (errors.size() != before) {
            return null;
        }
        return new ScriptAction(id, triggerId, conditions, effects, cooldown, path);
    }

    private List<ScriptCall> calls(JsonObject obj, String key, String path,
                                   List<String> errors, boolean isCondition) {
        List<ScriptCall> out = new ArrayList<>();
        if (!obj.has(key)) {
            return out;
        }
        JsonElement raw = obj.get(key);
        if (!raw.isJsonArray()) {
            errors.add(path + "：" + key + " 必须是数组");
            return out;
        }
        for (JsonElement el : raw.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                errors.add(path + "：" + key + " 的每一项都必须是对象");
                continue;
            }
            JsonObject callObj = el.getAsJsonObject();
            if (!callObj.has("type") || !callObj.get("type").isJsonPrimitive()) {
                errors.add(path + "：" + key + " 里有一项缺少 type");
                continue;
            }
            String type = callObj.get("type").getAsString();
            boolean known = isCondition
                    ? registries.condition(type) != null
                    : registries.effect(type) != null;
            if (!known) {
                errors.add(path + "：未知 " + (isCondition ? "condition" : "effect") + " '" + type + "'");
                continue;
            }
            JsonObject args = new JsonObject();
            if (callObj.has("args")) {
                if (callObj.get("args").isJsonObject()) {
                    args = callObj.getAsJsonObject("args");
                } else {
                    errors.add(path + "：" + key + " 的 args 必须是对象");
                    continue;
                }
            }
            out.add(new ScriptCall(type, args));
        }
        if (!isCondition && out.isEmpty()) {
            errors.add(path + "：脚本 " + obj.get("id") + " 没有可执行的 effect");
        }
        return out;
    }

    // ------------------------------------------------------------ 执行

    /** 按 trigger 分发。单个 effect 抛异常只记日志，不影响同级其它脚本。 */
    public void fire(String triggerId, ScriptContext ctx) {
        List<ScriptAction> list = byTrigger.get(triggerId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (ScriptAction action : list) {
            execute(action, ctx);
        }
    }

    /** /redtale fire 用：按 id 直接跑一条，忽略 trigger。 */
    public boolean fireById(String id, ScriptContext ctx) {
        ScriptAction action = byId.get(id);
        if (action == null) {
            return false;
        }
        execute(action, ctx);
        return true;
    }

    /**
     * 直接跑一组原语调动（不经 trigger / condition / 脚本冷却）。
     *
     * <p>给「物品自带的脚本」（D23 消耗品的 {@code use.script}）用：那段脚本属于**这件物品**，
     * 不进 {@code scripts/} 注册表，所以没有 action id 可查。返回成功执行的条数；
     * 单条失败只记日志，不影响后面的条 —— 与 {@link #execute} 同口径。
     */
    public int runCalls(List<ScriptCall> calls, ScriptContext ctx) {
        return runCalls(calls, ctx, "物品脚本");
    }

    /**
     * 同上，但日志带上调用方标签（M5-A 技能引擎用：{@code "技能 redtale_project:gale_slash"}）。
     * 标签只进日志文案，不影响执行语义 —— 少一个重载，技能报错就会被写成「物品脚本的 effect」。
     */
    public int runCalls(List<ScriptCall> calls, ScriptContext ctx, String owner) {
        String who = owner == null || owner.isBlank() ? "匿名调用" : owner;
        int done = 0;
        for (ScriptCall call : calls) {
            EffectType effect = registries.effect(call.type());
            if (effect == null) {
                log.warn("{} 的 effect {} 未注册（已跳过）", who, call.type());
                continue;
            }
            try {
                effect.run(ctx, call.argsOrEmpty());
                done++;
            } catch (Exception e) {
                log.error("{} 的 effect {} 执行失败", who, call.type(), e);
            }
        }
        return done;
    }

    private void execute(ScriptAction action, ScriptContext ctx) {
        if (!passConditions(action, ctx)) {
            return;
        }
        if (onCooldown(action, ctx)) {
            return;
        }
        for (ScriptCall call : action.effects()) {
            EffectType effect = registries.effect(call.type());
            if (effect == null) {
                continue;
            }
            try {
                effect.run(ctx, call.argsOrEmpty());
            } catch (Exception e) {
                log.error("脚本 {} 的 effect {} 执行失败", action.id(), call.type(), e);
            }
        }
    }

    private boolean passConditions(ScriptAction action, ScriptContext ctx) {
        for (ScriptCall call : action.conditions()) {
            ConditionType condition = registries.condition(call.type());
            if (condition == null) {
                continue;
            }
            try {
                if (!condition.test(ctx, call.argsOrEmpty())) {
                    return false;
                }
            } catch (Exception e) {
                log.error("脚本 {} 的 condition {} 判定失败，按不通过处理", action.id(), call.type(), e);
                return false;
            }
        }
        return true;
    }

    private boolean onCooldown(ScriptAction action, ScriptContext ctx) {
        if (action.cooldownTicks() <= 0) {
            return false;
        }
        // R24：模型/实体触发的上下文可能没有 server（ofEntity 兜底失败时），冷却按「不拦」处理
        if (ctx.server() == null) {
            return false;
        }
        String key = action.id() + '\u0000'
                + (ctx.hasPlayer() ? ctx.player().getGameProfile().getName() : "server");
        long now = ctx.server().overworld().getGameTime();
        Long last = cooldownStamp.get(key);
        if (last != null && now - last < action.cooldownTicks()) {
            return true;
        }
        cooldownStamp.put(key, now);
        return false;
    }

    // ------------------------------------------------------------ 查询

    public int size() {
        return actions.size();
    }

    public List<ScriptAction> actions() {
        return actions;
    }

    // ------------------------------------------------------------ 小工具

    private static String str(JsonObject obj, String key, String path, List<String> errors) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            errors.add(path + "：缺少字符串字段 " + key);
            return null;
        }
        return obj.get(key).getAsString();
    }

    private static int optInt(JsonObject obj, String key, int def, String path, List<String> errors) {
        if (!obj.has(key)) {
            return def;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            errors.add(path + "：" + key + " 必须是整数");
            return def;
        }
    }

    /** 加载结果：成功生效的条数 + 全部错误（部分成功也会返回 errors）。 */
    public record Outcome(int loaded, List<String> errors) {

        public boolean clean() {
            return errors.isEmpty();
        }
    }
}
