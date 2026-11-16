package com.redtale.project.core.config;

/**
 * JSON5 解析失败。带行列号，方便直接在日志里定位配置里的手误。
 */
public final class Json5Exception extends RuntimeException {

    public Json5Exception(String message, int line, int column) {
        super(message + "（第 " + line + " 行，第 " + column + " 列）");
    }

    public Json5Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
