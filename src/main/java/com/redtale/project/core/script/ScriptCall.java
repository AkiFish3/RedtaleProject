package com.redtale.project.core.script;

import com.google.gson.JsonObject;

/**
 * 脚本里的一次「原语调用」：{ type: "message", args: { text: "hi" } }
 */
public record ScriptCall(String type, JsonObject args) {

    public static final JsonObject EMPTY = new JsonObject();

    public JsonObject argsOrEmpty() {
        return args != null ? args : EMPTY;
    }
}
