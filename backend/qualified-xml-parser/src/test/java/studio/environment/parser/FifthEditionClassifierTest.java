package studio.environment.parser;

import com.ctc.wstx.util.XmlChars;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent table transcription of W3C XML 1.0 Fifth Edition productions [4]/[4a]. */
class FifthEditionClassifierTest {
    private static final int[][] START = {{0x3a,0x3a},{0x41,0x5a},{0x5f,0x5f},{0x61,0x7a},{0xc0,0xd6},{0xd8,0xf6},
            {0xf8,0x2ff},{0x370,0x37d},{0x37f,0x1fff},{0x200c,0x200d},{0x2070,0x218f},{0x2c00,0x2fef},
            {0x3001,0xd7ff},{0xf900,0xfdcf},{0xfdf0,0xfffd},{0x10000,0xeffff}};
    private static final int[][] EXTRA = {{0x2d,0x2e},{0x30,0x39},{0xb7,0xb7},{0x300,0x36f},{0x203f,0x2040}};
    private static boolean in(int cp, int[][] ranges) {
        for (int[] range : ranges) if (cp >= range[0] && cp <= range[1]) return true;
        return false;
    }
    @Test void everyUnicodeScalarAgreesForStartAndContinuation() {
        long checks = 0;
        for (int cp = 0; cp <= 0x10ffff; cp++) {
            if (cp >= 0xd800 && cp <= 0xdfff) continue;
            char[] characters = Character.toChars(cp);
            boolean start = XmlChars.is10NameStartChar(characters[0]); boolean continuation = XmlChars.is10NameChar(characters[0]);
            if (characters.length == 2) { start &= XmlChars.is10NameChar(characters[1]); continuation &= XmlChars.is10NameChar(characters[1]); }
            if (start != in(cp, START) || continuation != (in(cp, START) || in(cp, EXTRA))) fail("Classifier mismatch at scalar " + cp);
            checks += 2;
        }
        assertEquals(2_224_128, checks);
    }
}
