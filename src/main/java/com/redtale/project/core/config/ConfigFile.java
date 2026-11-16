package com.redtale.project.core.config;

import com.google.gson.JsonObject;

/**
 * 一份已解析的配置文件。
 *
 * @param path 相对 config/redtale_project 的路径，用 / 分隔
 * @param root 根对象
 */
public record ConfigFile(String path, JsonObject root) {
}
