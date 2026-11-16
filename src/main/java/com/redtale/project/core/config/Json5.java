package com.redtale.project.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * 最小 JSON5 解析器，输出 Gson 树，零第三方依赖（Gson 由 Minecraft 自带）。
 *
 * 支持：单行斜杠注释、块注释、未加引号的对象键、单引号字符串、尾逗号、
 * 十六进制数字、正号前缀。
 *
 * 不支持（直接报错，不静默降级）：多行字符串、Infinity / NaN。
 * 数值一律保留为 long 或 double，避免 "1.0" 变成整型后精度语义漂移。
 */
public final class Json5 {

    private final String src;
    private int pos;

    private Json5(String src) {
        this.src = src;
    }

    public static JsonElement parse(String text) {
        Json5 p = new Json5(text);
        p.skipTrivia();
        JsonElement value = p.readValue();
        p.skipTrivia();
        if (p.pos < p.src.length()) {
            throw p.error("解析结束后仍有残留内容");
        }
        return value;
    }

    // ---------------------------------------------------------------- 值

    private JsonElement readValue() {
        skipTrivia();
        if (pos >= src.length()) {
            throw error("内容意外结束");
        }
        char c = src.charAt(pos);
        if (c == '{') {
            return readObject();
        }
        if (c == '[') {
            return readArray();
        }
        if (c == '"' || c == '\'') {
            return new JsonPrimitive(readString(c));
        }
        if (c == '+' || c == '-' || c == '.' || (c >= '0' && c <= '9')) {
            return readNumber();
        }
        String ident = readIdentifier();
        if (ident.equals("true")) {
            return new JsonPrimitive(Boolean.TRUE);
        }
        if (ident.equals("false")) {
            return new JsonPrimitive(Boolean.FALSE);
        }
        if (ident.equals("null")) {
            return JsonNull.INSTANCE;
        }
        if (ident.isEmpty()) {
            throw error("无法识别的字符 '" + c + "'");
        }
        throw error("无法识别的字面量 '" + ident + "'（字符串必须加引号）");
    }

    private JsonObject readObject() {
        JsonObject obj = new JsonObject();
        pos++; // 跳过 '{'
        skipTrivia();
        if (peek() == '}') {
            pos++;
            return obj;
        }
        while (true) {
            skipTrivia();
            String key = readKey();
            skipTrivia();
            expect(':');
            JsonElement value = readValue();
            obj.add(key, value);
            skipTrivia();
            char c = next("对象缺少 ',' 或 '}'");
            if (c == '}') {
                return obj;
            }
            if (c != ',') {
                throw error("对象里缺少 ',' 或 '}'");
            }
            skipTrivia();
            if (peek() == '}') {
                pos++;
                return obj; // 尾逗号
            }
        }
    }

    private JsonArray readArray() {
        JsonArray arr = new JsonArray();
        pos++; // 跳过 '['
        skipTrivia();
        if (peek() == ']') {
            pos++;
            return arr;
        }
        while (true) {
            arr.add(readValue());
            skipTrivia();
            char c = next("数组缺少 ',' 或 ']'");
            if (c == ']') {
                return arr;
            }
            if (c != ',') {
                throw error("数组里缺少 ',' 或 ']'");
            }
            skipTrivia();
            if (peek() == ']') {
                pos++;
                return arr; // 尾逗号
            }
        }
    }

    private String readKey() {
        char c = peek();
        if (c == '"' || c == '\'') {
            return readString(c);
        }
        String ident = readIdentifier();
        if (ident.isEmpty()) {
            throw error("对象键必须是字符串或标识符");
        }
        return ident;
    }

    private String readString(char quote) {
        pos++; // 跳过起始引号
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw error("字符串没有闭合");
            }
            char c = src.charAt(pos++);
            if (c == quote) {
                return sb.toString();
            }
            if (c == '\n') {
                throw error("字符串内出现换行（不支持多行字符串）");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= src.length()) {
                throw error("转义符后内容缺失");
            }
            char esc = src.charAt(pos++);
            switch (esc) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case '0' -> sb.append('\0');
                case '"' -> sb.append('"');
                case '\'' -> sb.append('\'');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'u' -> {
                    if (pos + 4 > src.length()) {
                        throw error("\\u 转义不完整");
                    }
                    String hex = src.substring(pos, pos + 4);
                    pos += 4;
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw error("\\u 转义不是合法十六进制：" + hex);
                    }
                }
                default -> throw error("不支持的转义 '\\" + esc + "'");
            }
        }
    }

    private JsonPrimitive readNumber() {
        int start = pos;
        if (peek() == '+' || peek() == '-') {
            pos++;
        }
        boolean hex = false;
        if (pos + 1 < src.length() && src.charAt(pos) == '0'
                && (src.charAt(pos + 1) == 'x' || src.charAt(pos + 1) == 'X')) {
            hex = true;
            pos += 2;
            while (pos < src.length() && isHexDigit(src.charAt(pos))) {
                pos++;
            }
        } else {
            while (pos < src.length() && isDigit(src.charAt(pos))) {
                pos++;
            }
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                while (pos < src.length() && isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < src.length() && isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
        }
        String raw = src.substring(start, pos);
        String unsigned = raw.startsWith("-") || raw.startsWith("+") ? raw.substring(1) : raw;
        if (unsigned.isEmpty() || unsigned.equals(".")) {
            throw error("数字不合法：" + raw);
        }
        try {
            if (hex) {
                long value = Long.parseLong(unsigned.substring(2), 16);
                return new JsonPrimitive(raw.startsWith("-") ? Long.valueOf(-value) : Long.valueOf(value));
            }
            boolean fractional = unsigned.indexOf('.') >= 0
                    || unsigned.indexOf('e') >= 0
                    || unsigned.indexOf('E') >= 0;
            if (fractional) {
                return new JsonPrimitive(Double.valueOf(raw));
            }
            return new JsonPrimitive(Long.valueOf(Long.parseLong(raw)));
        } catch (NumberFormatException e) {
            throw error("数字不合法：" + raw);
        }
    }

    private String readIdentifier() {
        int start = pos;
        while (pos < src.length() && isIdentChar(src.charAt(pos))) {
            pos++;
        }
        return src.substring(start, pos);
    }

    // ---------------------------------------------------------------- 底层

    private void skipTrivia() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\uFEFF') {
                pos++;
                continue;
            }
            if (c == '/' && pos + 1 < src.length()) {
                char n = src.charAt(pos + 1);
                if (n == '/') {
                    pos += 2;
                    while (pos < src.length() && src.charAt(pos) != '\n') {
                        pos++;
                    }
                    continue;
                }
                if (n == '*') {
                    pos += 2;
                    while (pos + 1 < src.length()
                            && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
                        pos++;
                    }
                    if (pos + 1 >= src.length()) {
                        throw error("块注释没有闭合");
                    }
                    pos += 2;
                    continue;
                }
            }
            return;
        }
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private char next(String message) {
        if (pos >= src.length()) {
            throw error(message);
        }
        return src.charAt(pos++);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("期望 '" + c + "'，实际是 '" + peek() + "'");
        }
        pos++;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isIdentChar(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    private Json5Exception error(String message) {
        int line = 1;
        int column = 1;
        int limit = Math.min(pos, src.length());
        for (int i = 0; i < limit; i++) {
            if (src.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new Json5Exception(message, line, column);
    }
}
