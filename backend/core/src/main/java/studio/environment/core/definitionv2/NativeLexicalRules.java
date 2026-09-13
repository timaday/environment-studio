package studio.environment.core.definitionv2;

import java.math.BigInteger;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import static studio.environment.core.definitionv2.NativeDefinition.*;

/** Pure lexical rules, without XML parsers, network resolution or value normalization. */
public final class NativeLexicalRules {
    private NativeLexicalRules() { }
    public static boolean validValue(ValueType type, String value) {
        if (value == null) return false;
        return switch (type) {
            case TEXT -> true;
            case INTEGER -> canonicalInteger(value, 1024);
            case BOOLEAN -> value.equals("true") || value.equals("false");
            case URI -> AsciiUriSyntax.valid(value);
        };
    }
    public static boolean validIdentity(String value) { return value != null && !value.isEmpty(); }
    public static boolean validKey(KeyType type, String value) {
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 256) return false;
        if (type == KeyType.TEXT) return true;
        if (!canonicalInteger(value, 19)) return false;
        BigInteger number = new BigInteger(value);
        return number.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0 && number.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
    }
    private static boolean canonicalInteger(String value, int maxDigits) {
        if (value.equals("0")) return true;
        int start = value.startsWith("-") ? 1 : 0;
        if (value.length() <= start || value.length() - start > maxDigits || value.charAt(start) < '1' || value.charAt(start) > '9') return false;
        for (int i = start + 1; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }
    public static boolean validName(ExpandedName name, boolean attribute) {
        String namespace = name.namespaceUri();
        if (namespace.equals("http://www.w3.org/2000/xmlns/") || namespace.codePointCount(0, namespace.length()) > 2048) return false;
        for (int i = 0; i < namespace.length();) {
            int cp = namespace.codePointAt(i); i += Character.charCount(cp);
            if (!(cp == 9 || cp == 10 || cp == 13 || cp >= 32 && cp <= 0xd7ff
                    || cp >= 0xe000 && cp <= 0xfffd || cp >= 0x10000 && cp <= 0x10ffff)) return false;
        }
        String local = name.localName();
        if (local.isEmpty() || local.codePointCount(0, local.length()) > 128 || attribute && namespace.isEmpty() && local.equals("xmlns")) return false;
        int offset = 0;
        while (offset < local.length()) {
            int cp = local.codePointAt(offset);
            if (!nameStart(cp) && (offset == 0 || !(cp == '-' || cp == '.' || cp >= '0' && cp <= '9'
                    || cp == 0xb7 || cp >= 0x300 && cp <= 0x36f || cp >= 0x203f && cp <= 0x2040))) return false;
            offset += Character.charCount(cp);
        }
        return true;
    }
    // XML 1.0 Fifth Edition NameStartChar with ':' excluded for NCName.
    private static boolean nameStart(int cp) {
        return cp >= 'A' && cp <= 'Z' || cp == '_' || cp >= 'a' && cp <= 'z'
                || cp >= 0xc0 && cp <= 0xd6 || cp >= 0xd8 && cp <= 0xf6 || cp >= 0xf8 && cp <= 0x2ff
                || cp >= 0x370 && cp <= 0x37d || cp >= 0x37f && cp <= 0x1fff || cp >= 0x200c && cp <= 0x200d
                || cp >= 0x2070 && cp <= 0x218f || cp >= 0x2c00 && cp <= 0x2fef || cp >= 0x3001 && cp <= 0xd7ff
                || cp >= 0xf900 && cp <= 0xfdcf || cp >= 0xfdf0 && cp <= 0xfffd || cp >= 0x10000 && cp <= 0xeffff;
    }
}
