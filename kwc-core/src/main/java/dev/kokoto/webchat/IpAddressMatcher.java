package dev.kokoto.webchat;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

public final class IpAddressMatcher {
    private IpAddressMatcher() {}

    public static boolean matchesAny(String ip, List<String> patterns) {
        if (ip == null || patterns == null || patterns.isEmpty()) return false;
        for (String pattern : patterns) {
            if (matches(ip, pattern)) return true;
        }
        return false;
    }

    public static boolean matches(String ip, String pattern) {
        String target = normalizeIpLiteral(ip);
        String rule = normalizeIpLiteral(pattern);
        if (target.isBlank() || rule.isBlank()) return false;
        if ("*".equals(rule)) return true;

        int slash = rule.indexOf('/');
        if (slash < 0) {
            if (target.equals(rule)) return true;
            try {
                return Arrays.equals(addressBytes(target), addressBytes(rule));
            } catch (Exception ignored) {
                return false;
            }
        }

        String base = rule.substring(0, slash).trim();
        String prefixText = rule.substring(slash + 1).trim();
        try {
            byte[] targetBytes = addressBytes(target);
            byte[] baseBytes = addressBytes(base);
            if (targetBytes.length != baseBytes.length) return false;

            int totalBits = baseBytes.length * 8;
            int prefix = Integer.parseInt(prefixText);
            if (prefix < 0 || prefix > totalBits) return false;
            return prefixMatches(targetBytes, baseBytes, prefix);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isValidAddress(String value) {
        try {
            String normalized = normalizeIpLiteral(value);
            return !normalized.isBlank() && addressBytes(normalized).length > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String normalizeIpLiteral(String value) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("[") && s.contains("]")) {
            return s.substring(1, s.indexOf(']')).trim();
        }
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.indexOf(':') == colon && colon < s.length() - 1) {
            String maybePort = s.substring(colon + 1);
            boolean digits = true;
            for (int i = 0; i < maybePort.length(); i++) {
                if (!Character.isDigit(maybePort.charAt(i))) {
                    digits = false;
                    break;
                }
            }
            if (digits) s = s.substring(0, colon);
        }
        return s.trim();
    }

    private static boolean prefixMatches(byte[] target, byte[] base, int prefixBits) {
        int fullBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (target[i] != base[i]) return false;
        }
        if (remainingBits == 0) return true;

        int mask = 0xFF << (8 - remainingBits);
        return (target[fullBytes] & mask) == (base[fullBytes] & mask);
    }

    private static byte[] addressBytes(String value) throws Exception {
        return comparableBytes(InetAddress.getByName(normalizeIpLiteral(value)));
    }

    private static byte[] comparableBytes(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length == 16 && isIpv4Mapped(b)) {
            return Arrays.copyOfRange(b, 12, 16);
        }
        return b;
    }

    private static boolean isIpv4Mapped(byte[] b) {
        if (b.length != 16) return false;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return b[10] == (byte)0xff && b[11] == (byte)0xff;
    }
}
