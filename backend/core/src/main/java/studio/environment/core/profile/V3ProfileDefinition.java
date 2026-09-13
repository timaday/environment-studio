package studio.environment.core.profile;

import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.definitionv2.NativeDefinition.Logical;

/** Internal checked metadata only, never publication readiness. */
final class V3ProfileDefinition {
    private V3ProfileDefinition() { }
    static boolean eligible(Checked definition) {
        if (definition == null) return false;
        var result = new NativeDefinitionCompiler().compile(definition.definition());
        return result.isCompatibleWith(definition);
    }
    static Logical physical(Checked definition) {
        var logical = definition.definition().logical();
        return new Logical(logical.entityTypes(), logical.relations(), logical.rules(), logical.operationCapabilities());
    }
    static int scalar(String left, String right) {
        int a = 0, b = 0;
        while (a < left.length() && b < right.length()) {
            int x = left.codePointAt(a), y = right.codePointAt(b);
            if (x != y) return Integer.compare(x, y);
            a += Character.charCount(x); b += Character.charCount(y);
        }
        return Integer.compare(left.length() - a, right.length() - b);
    }
    static boolean identity(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i); i += Character.charCount(cp);
            if (!(cp == 9 || cp == 10 || cp == 13 || cp >= 32 && cp <= 0xd7ff
                    || cp >= 0xe000 && cp <= 0xfffd || cp >= 0x10000 && cp <= 0x10ffff)) return false;
        }
        return true;
    }
}
