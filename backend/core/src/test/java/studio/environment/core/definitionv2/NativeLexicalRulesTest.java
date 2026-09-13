package studio.environment.core.definitionv2;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.definition.DefinitionDraft.ValueType.*;
import static studio.environment.core.definitionv2.NativeDefinition.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NativeLexicalRulesTest {
    @Test void scalarCodecsKeepEmptyTextDistinctFromIdentityAndAbsence() {
        assertTrue(NativeLexicalRules.validValue(TEXT, ""));
        assertTrue(NativeLexicalRules.validValue(TEXT, " 01 😀 "));
        assertFalse(NativeLexicalRules.validValue(TEXT, null));
        assertFalse(NativeLexicalRules.validIdentity(""));
        assertTrue(NativeLexicalRules.validIdentity(" 01 "));
        assertTrue(NativeLexicalRules.validValue(INTEGER, "-" + "9".repeat(1024)));
        assertFalse(NativeLexicalRules.validValue(INTEGER, "9".repeat(1025)));
        assertTrue(NativeLexicalRules.validValue(BOOLEAN, "true"));
        assertTrue(NativeLexicalRules.validValue(BOOLEAN, "false"));
        for (String invalid : java.util.List.of("TRUE", "0", "1", " true", "")) assertFalse(NativeLexicalRules.validValue(BOOLEAN, invalid));
        for (String invalid : java.util.List.of("-0", "+1", "01", "1.0", "1e0", " 1", "", "-")) assertFalse(NativeLexicalRules.validValue(INTEGER, invalid));
    }
    @Test void keysHaveExactSigned64BitBoundsWithoutNarrowingOrNormalization() {
        assertTrue(NativeLexicalRules.validKey(KeyType.INT64, "9223372036854775807"));
        assertTrue(NativeLexicalRules.validKey(KeyType.INT64, "-9223372036854775808"));
        for (String key : java.util.List.of("9223372036854775808", "-9223372036854775809", "-0", "+0", "00", " 1"))
            assertFalse(NativeLexicalRules.validKey(KeyType.INT64, key));
        assertTrue(NativeLexicalRules.validKey(KeyType.TEXT, "😀".repeat(256)));
        assertFalse(NativeLexicalRules.validKey(KeyType.TEXT, "😀".repeat(257)));
        assertFalse(NativeLexicalRules.validKey(KeyType.TEXT, ""));
    }
    @Test void ncNamesFollowXml10UnicodeRulesAndReserveNamespaceDeclarations() {
        for (String name : java.util.List.of("_mock", "écho", "𐀀glyph", "glyph-1", "a\u0300"))
            assertTrue(NativeLexicalRules.validName(new ExpandedName("urn:mock", name), false), name);
        for (String name : java.util.List.of("", "a:b", "1a", "a b", "a\n", "\u0300a", "x".repeat(129)))
            assertFalse(NativeLexicalRules.validName(new ExpandedName("urn:mock", name), false), name);
        assertFalse(NativeLexicalRules.validName(new ExpandedName("http://www.w3.org/2000/xmlns/", "x"), false));
        assertFalse(NativeLexicalRules.validName(new ExpandedName("", "xmlns"), true));
        assertTrue(NativeLexicalRules.validName(new ExpandedName("http://www.w3.org/XML/1998/namespace", "lang"), true));
        assertFalse(NativeLexicalRules.validName(new ExpandedName("urn:mock" + (char) 0, "x"), true));
    }
    @ParameterizedTest @ValueSource(strings = {"urn:mock:glyph", "https://mock.invalid/a%20b?q=x#part", "file:///mock", "x:",
            "x://user:pass@mock.invalid:80/a", "x://[::1]/", "x://[2001:db8:0:0:0:0:0:1]", "x://[::ffff:192.0.2.1]/", "x://[v1.mock:host]/"})
    void acceptsAbsoluteAsciiRfc3986SyntaxWithoutResolvingIt(String value) { assertTrue(NativeLexicalRules.validValue(URI, value)); }
    @ParameterizedTest @ValueSource(strings = {"relative/path", "//mock.invalid", "1x:thing", "http://mock.invalid/white space", "urn:mock:é",
            "x:%zz", "x:%0", "x://[::1", "x://[1:2:3:4:5:6:7:8:9]", "x://[1::2::3]", "x://[::ffff:999.1.1.1]", "x://[v1.%20]", "x://a:port", "x:a#b#c", "x:a\\b"})
    void refusesInvalidUriSyntax(String value) { assertFalse(NativeLexicalRules.validValue(URI, value)); }
}
