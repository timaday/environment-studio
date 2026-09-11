package studio.environment.core.definitionv3;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definitionv2.NativeDefinition.Binding;
import studio.environment.core.definitionv2.NativeDefinition.Document;
import studio.environment.core.definitionv2.NativeDefinition.Projection;
import static studio.environment.core.definitionv3.NativeCompilationResult.*;

/** Result compatibility only: constructed ReadyToPublish values do not qualify the compiler. */
class NativeV3CompilationResultTest {
    private final NativeDefinitionCompiler compiler = new NativeDefinitionCompiler();
    private Incomplete current() {
        return assertInstanceOf(Incomplete.class, compiler.compile(NativeV3CompilerTest.fixture()));
    }
    @Test void equivalentCheckedDataWorksForBothAllowedInternalStates() {
        var incomplete = current(); var checked = incomplete.checked();
        var copied = new Checked(checked.definition(), checked.logicalDigest(),
                new TreeMap<>(checked.bindingDigests()), new TreeMap<>(checked.mechanisms()));
        assertNotSame(checked, copied);
        assertTrue(incomplete.isCompatibleWith(copied));
        assertTrue(new ReadyToPublish(checked).isCompatibleWith(copied));
    }
    @Test void actualCompilerStillCannotEmitReadiness() {
        var actual = current();
        assertEquals(List.of("MECHANISM_UNQUALIFIED"), actual.diagnostics().stream().map(DefinitionDiagnostic::code).toList());
        assertFalse(actual.diagnostics().isEmpty());
        assertEquals(actual, compiler.compile(actual.checked().definition()));
    }
    @Test void noCheckedComponentCanBeSubstituted() {
        var actual = current(); var c = actual.checked(); var d = c.definition();
        var changes = new ArrayList<Checked>();
        changes.add(new Checked(new NativeDefinition(d.id(), d.revision().add(BigInteger.ONE), d.logical(), d.bindings()),
                c.logicalDigest(), c.bindingDigests(), c.mechanisms()));
        changes.add(new Checked(d, "0".repeat(64), c.bindingDigests(), c.mechanisms()));
        changes.add(new Checked(d, c.logicalDigest(), Map.of("mock-pg", "0".repeat(64)), c.mechanisms()));
        changes.add(new Checked(d, c.logicalDigest(), Map.of(), c.mechanisms()));
        for (String change : List.of("missing", "changed", "extra")) {
            var mechanisms = new TreeMap<>(c.mechanisms());
            if (change.equals("missing")) mechanisms.remove("derived-graph-v1");
            else if (change.equals("changed")) mechanisms.put("derived-graph-v1", BigInteger.TWO);
            else mechanisms.put("mock-extra", BigInteger.ONE);
            changes.add(new Checked(d, c.logicalDigest(), c.bindingDigests(), mechanisms));
        }
        for (var state : List.of(actual, new ReadyToPublish(c))) {
            assertFalse(state.isCompatibleWith(null));
            for (var changed : changes) assertFalse(state.isCompatibleWith(changed));
        }
        // A constructed result cannot replace actual recompilation of a supplied forged model.
        for (var changed : changes.subList(1, changes.size()))
            assertFalse(compiler.compile(changed.definition()).isCompatibleWith(changed));
        // Source revision is excluded from compatibility hashes; a freshly compiled
        // legitimate new revision remains usable, while it is not the original Checked.
        assertTrue(compiler.compile(changes.getFirst().definition()).isCompatibleWith(changes.getFirst()));
    }
    @Test void anyPhysicalBlockerRefusesIncludingMixedDiagnostics() {
        var d = NativeV3CompilerTest.fixture(); var b = d.bindings().getFirst();
        var doc = b.documents().getFirst(); var p = doc.entities().getFirst();
        var missing = new Projection(p.id(), p.type(), p.path(), List.of(p.fields().getFirst()), p.references());
        var changed = new NativeDefinition(d.id(), d.revision(), d.logical(), List.of(new Binding(b.id(), b.engine(), b.storage(),
                b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(), List.of(new Document(doc.id(), doc.key(), List.of(missing))))));
        var incomplete = assertInstanceOf(Incomplete.class, compiler.compile(changed));
        var physical = incomplete.diagnostics().stream().filter(x -> !x.code().equals("MECHANISM_UNQUALIFIED")).toList();
        assertFalse(physical.isEmpty());
        assertTrue(incomplete.diagnostics().stream().anyMatch(x -> x.code().equals("MECHANISM_UNQUALIFIED")));
        assertFalse(incomplete.isCompatibleWith(incomplete.checked()));
        assertFalse(new Incomplete(incomplete.checked(), physical).isCompatibleWith(incomplete.checked()));
        var future = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PUBLICATION, "MOCK_UNAVAILABLE", "", "Mock blocker.");
        assertFalse(new Incomplete(current().checked(), List.of(future)).isCompatibleWith(current().checked()));
    }
    @Test void statesKeepTheirClosedConstructionAndValueFreeRendering() {
        var c = current().checked(); var ready = new ReadyToPublish(c);
        assertTrue(ready.diagnostics().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> ready.diagnostics().add(current().diagnostics().getFirst()));
        assertEquals("ReadyToPublishV3[redacted]", ready.toString());
        assertThrows(NullPointerException.class, () -> new ReadyToPublish(null));
        assertThrows(IllegalArgumentException.class, () -> new Incomplete(c, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Rejected(List.of()));
        var syntax = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PARSE, "MOCK_PARSE", "", "Mock refusal.");
        assertThrows(IllegalArgumentException.class, () -> new Incomplete(c, List.of(syntax)));
        assertFalse(new Rejected(List.of(syntax)).isCompatibleWith(c));
    }
}
