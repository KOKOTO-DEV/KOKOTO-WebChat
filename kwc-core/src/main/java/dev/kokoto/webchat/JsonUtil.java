package dev.kokoto.webchat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class JsonUtil {
    public static final int DEFAULT_BODY_LIMIT_BYTES = 1024 * 1024;

    private JsonUtil() {}

    public static final class BodyTooLargeException extends IOException {
        public final long maxBytes;
        public BodyTooLargeException(long maxBytes) {
            super("request_body_too_large");
            this.maxBytes = maxBytes;
        }
    }

    public static String readBody(InputStream in) throws IOException {
        return readBody(in, DEFAULT_BODY_LIMIT_BYTES);
    }

    public static String readBody(InputStream in, long maxBytes) throws IOException {
        long limit = maxBytes <= 0 ? DEFAULT_BODY_LIMIT_BYTES : maxBytes;
        ByteArrayOutputStream out = new ByteArrayOutputStream((int)Math.min(8192L, limit));
        byte[] buf = new byte[4096];
        long total = 0L;
        int read;
        while ((read = in.read(buf)) != -1) {
            total += read;
            if (total > limit) throw new BodyTooLargeException(limit);
            out.write(buf, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String obj(Map<String, ?> values) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> e : values.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(e.getKey())).append(':').append(value(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    public static String arr(Collection<?> values) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Object v : values) {
            if (!first) sb.append(',');
            first = false;
            sb.append(value(v));
        }
        sb.append(']');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String value(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return quote((String) v);
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        if (v instanceof Map) return obj((Map<String, ?>) v);
        if (v instanceof Collection) return arr((Collection<?>) v);
        return quote(String.valueOf(v));
    }

    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null) return map;
        Parser p = new Parser(json);
        p.skipWs();
        if (!p.consume('{')) return map;
        while (true) {
            p.skipWs();
            if (p.consume('}')) break;
            String key = p.readString();
            if (key == null) break;
            p.skipWs();
            if (!p.consume(':')) break;
            p.skipWs();
            String value;
            if (p.peek() == '"') value = p.readString();
            else value = p.readLiteral();
            map.put(key, value == null ? "" : value);
            p.skipWs();
            if (p.consume('}')) break;
            p.consume(',');
        }
        return map;
    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                map.put(urlDecode(part), "");
            } else {
                map.put(urlDecode(part.substring(0, eq)), urlDecode(part.substring(eq + 1)));
            }
        }
        return map;
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        char peek() {
            return i >= s.length() ? 0 : s.charAt(i);
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        boolean consume(char c) {
            if (peek() == c) {
                i++;
                return true;
            }
            return false;
        }

        String readString() {
            if (!consume('"')) return null;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char ch = s.charAt(i++);
                if (ch == '"') break;
                if (ch == '\\' && i < s.length()) {
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (i + 4 <= s.length()) {
                                String hex = s.substring(i, i + 4);
                                i += 4;
                                try {
                                    sb.append((char) Integer.parseInt(hex, 16));
                                } catch (NumberFormatException ignored) {}
                            }
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }

        String readLiteral() {
            int start = i;
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) break;
                i++;
            }
            return s.substring(start, i);
        }
    }
}
