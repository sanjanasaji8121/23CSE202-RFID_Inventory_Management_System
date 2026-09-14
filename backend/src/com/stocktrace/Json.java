package com.stocktrace;

import java.util.*;

/**
 * Small hand-rolled JSON writer + parser so the project doesn't need an extra
 * dependency (Jackson/Gson) on top of the SQLite driver. Handles exactly what
 * this API needs: flat-ish objects/arrays of strings, numbers, and booleans.
 */
public class Json {

    // ---------- Writing ----------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, sb);
        } else if (value instanceof List) {
            writeArray((List<Object>) value, sb);
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(e.getKey(), sb);
            sb.append(':');
            writeValue(e.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object o : list) {
            if (!first) sb.append(',');
            first = false;
            writeValue(o, sb);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------- Parsing ----------

    /** Parses a JSON object into a Map<String,Object> (String/Double/Boolean/null values). */
    public static Map<String, Object> parseObject(String json) {
        Parser p = new Parser(json);
        p.skipWs();
        Object result = p.parseValue();
        if (!(result instanceof Map)) throw new RuntimeException("Expected JSON object at top level");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        return map;
    }

    private static class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) { this.s = s; }

        void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        Object parseValue() {
            skipWs();
            char c = s.charAt(i);
            if (c == '{') return parseObj();
            if (c == '[') return parseArr();
            if (c == '"') return parseStr();
            if (c == 't') { i += 4; return Boolean.TRUE; }
            if (c == 'f') { i += 5; return Boolean.FALSE; }
            if (c == 'n') { i += 4; return null; }
            return parseNum();
        }

        Map<String, Object> parseObj() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (s.charAt(i) == '}') { i++; return map; }
            while (true) {
                skipWs();
                String key = parseStr();
                skipWs();
                i++; // :
                Object value = parseValue();
                map.put(key, value);
                skipWs();
                if (s.charAt(i) == ',') { i++; continue; }
                if (s.charAt(i) == '}') { i++; break; }
            }
            return map;
        }

        List<Object> parseArr() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (s.charAt(i) == ']') { i++; return list; }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWs();
                if (s.charAt(i) == ',') { i++; continue; }
                if (s.charAt(i) == ']') { i++; break; }
            }
            return list;
        }

        String parseStr() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (s.charAt(i) != '"') {
                char c = s.charAt(i);
                if (c == '\\') {
                    i++;
                    char esc = s.charAt(i);
                    switch (esc) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            String hex = s.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
                i++;
            }
            i++; // closing quote
            return sb.toString();
        }

        Double parseNum() {
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || "+-.eE".indexOf(s.charAt(i)) >= 0)) i++;
            return Double.parseDouble(s.substring(start, i));
        }
    }

    // ---------- Small helpers for pulling typed values out of a parsed map ----------

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    public static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0;
        return (int) Math.round(((Number) v).doubleValue());
    }

    public static double dblVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0.0;
        return ((Number) v).doubleValue();
    }

    public static boolean boolVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null && (Boolean) v;
    }
}
