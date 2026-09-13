package studio.environment.core.definitionv2;

/** RFC 3986 generic URI grammar, absolute scheme required; never performs scheme-specific lookup. */
final class AsciiUriSyntax {
    private AsciiUriSyntax() { }
    static boolean valid(String value) {
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) <= 32 || value.charAt(i) >= 127) return false;
        int colon = value.indexOf(':');
        if (colon < 1 || !alpha(value.charAt(0))) return false;
        for (int i = 1; i < colon; i++) {
            char c = value.charAt(i);
            if (!alpha(c) && !digit(c) && c != '+' && c != '-' && c != '.') return false;
        }
        String rest = value.substring(colon + 1);
        int fragment = rest.indexOf('#');
        if (fragment >= 0) {
            if (!component(rest.substring(fragment + 1), ":@/?")) return false;
            rest = rest.substring(0, fragment);
        }
        int query = rest.indexOf('?');
        if (query >= 0) {
            if (!component(rest.substring(query + 1), ":@/?")) return false;
            rest = rest.substring(0, query);
        }
        if (rest.startsWith("//")) {
            int slash = rest.indexOf('/', 2);
            String authority = slash < 0 ? rest.substring(2) : rest.substring(2, slash);
            return authority(authority) && (slash < 0 || component(rest.substring(slash), ":@/"));
        }
        return component(rest, ":@/");
    }
    private static boolean authority(String value) {
        int at = value.indexOf('@');
        if (at >= 0) {
            if (!component(value.substring(0, at), ":")) return false;
            value = value.substring(at + 1);
        }
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0 || !ipLiteral(value.substring(1, close))) return false;
            String suffix = value.substring(close + 1);
            return suffix.isEmpty() || suffix.startsWith(":") && digits(suffix.substring(1));
        }
        int colon = value.indexOf(':');
        return colon < 0 ? component(value, "") : component(value.substring(0, colon), "") && digits(value.substring(colon + 1));
    }
    private static boolean ipLiteral(String value) {
        if (value.startsWith("v") || value.startsWith("V")) {
            int dot = value.indexOf('.');
            if (dot < 2 || dot == value.length() - 1) return false;
            for (int i = 1; i < dot; i++) if (!hex(value.charAt(i))) return false;
            String address = value.substring(dot + 1);
            return address.indexOf('%') < 0 && component(address, ":");
        }
        int compressed = value.indexOf("::");
        if (compressed >= 0 && value.indexOf("::", compressed + 2) >= 0) return false;
        if (compressed < 0 && (value.startsWith(":") || value.endsWith(":"))) return false;
        String left = compressed < 0 ? value : value.substring(0, compressed);
        String right = compressed < 0 ? "" : value.substring(compressed + 2);
        int leftCount = groups(left, compressed < 0);
        int rightCount = groups(right, true);
        if (leftCount < 0 || rightCount < 0) return false;
        return compressed < 0 ? leftCount == 8 : leftCount + rightCount < 8;
    }
    private static int groups(String value, boolean allowIpv4Tail) {
        if (value.isEmpty()) return 0;
        String[] groups = value.split(":", -1);
        int count = 0;
        for (int i = 0; i < groups.length; i++) {
            String group = groups[i];
            if (group.contains(".")) {
                if (!allowIpv4Tail || i != groups.length - 1 || !ipv4(group)) return -1;
                count += 2;
            } else {
                if (group.isEmpty() || group.length() > 4) return -1;
                for (int j = 0; j < group.length(); j++) if (!hex(group.charAt(j))) return -1;
                count++;
            }
        }
        return count;
    }
    private static boolean ipv4(String value) {
        String[] pieces = value.split("\\.", -1);
        if (pieces.length != 4) return false;
        for (String piece : pieces) {
            if (piece.isEmpty() || piece.length() > 3 || piece.length() > 1 && piece.startsWith("0") || !digits(piece)) return false;
            if (Integer.parseInt(piece) > 255) return false;
        }
        return true;
    }
    private static boolean component(String value, String extra) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length() || !hex(value.charAt(i + 1)) || !hex(value.charAt(i + 2))) return false;
                i += 2;
            } else if (!alpha(c) && !digit(c) && "-._~!$&'()*+,;=".indexOf(c) < 0 && extra.indexOf(c) < 0) return false;
        }
        return true;
    }
    private static boolean digits(String value) { for (int i = 0; i < value.length(); i++) if (!digit(value.charAt(i))) return false; return true; }
    private static boolean alpha(char c) { return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'; }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private static boolean hex(char c) { return digit(c) || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f'; }
}
