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

/** Result compatibility: only the explicitly qualified PostgreSQL text subset is current-ready. */
class NativeV3CompilationResultTest {
    private final NativeDefinitionCompiler compiler = new NativeDefinitionCompiler();
    private ReadyToPublish current() {
        return assertInstanceOf(ReadyToPublish.class, compiler.compile(NativeV3CompilerTest.fixture()));
    }
    private Incomplete unavailableOracle() {
        var d = NativeV3CompilerTest.fixture(); var b = d.bindings().getFirst();
        var oracle = new NativeDefinition(d.id(), d.revision(), d.logical(), List.of(new Binding(b.id(),
                studio.environment.core.definitionv2.NativeDefinition.Engine.ORACLE,
                studio.environment.core.definitionv2.NativeDefinition.Storage.CLOB,
                b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(), b.documents())));
        return assertInstanceOf(Incomplete.class, compiler.compile(oracle));
    }
    @Test void equivalentCheckedDataWorksOnlyForItsAllowedCurrentState() {
        var ready = current(); var checked = ready.checked();
        var copied = new Checked(checked.definition(), checked.logicalDigest(),
                new TreeMap<>(checked.bindingDigests()), new TreeMap<>(checked.mechanisms()));
        assertNotSame(checked, copied);
        assertTrue(ready.isCompatibleWith(copied));
        var mechanism = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PUBLICATION, "MECHANISM_UNQUALIFIED", "", "Unavailable.");
        assertFalse(new Incomplete(checked, List.of(mechanism)).isCompatibleWith(copied));
        var unavailable = unavailableOracle();
        assertTrue(unavailable.isCompatibleWith(unavailable.checked()));
    }
    @Test void actualCompilerEmitsReadinessOnlyForQualifiedPostgresqlText() {
        var actual = current();
        assertTrue(actual.diagnostics().isEmpty());
        assertEquals(actual, compiler.compile(actual.checked().definition()));
        var unavailable = unavailableOracle();
        assertEquals(List.of("MECHANISM_UNQUALIFIED"), unavailable.diagnostics().stream().map(DefinitionDiagnostic::code).toList());
        assertEquals(unavailable, compiler.compile(unavailable.checked().definition()));
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
        assertFalse(actual.isCompatibleWith(null));
        for (var changed : changes) assertFalse(actual.isCompatibleWith(changed));
        // A constructed result cannot replace actual recompilation of a supplied forged model.
        for (var changed : changes.subList(1, changes.size()))
            assertFalse(compiler.compile(changed.definition()).isCompatibleWith(changed));
        // Source revision is excluded from compatibility hashes; a freshly compiled
        // legitimate new revision remains usable, while it is not the original Checked.
        assertTrue(compiler.compile(changes.getFirst().definition()).isCompatibleWith(changes.getFirst()));
    }
    @Test void anyPhysicalBlockerOnQualifiedPostgresqlRefusesWithoutMechanismDowngrade() {
        var d = NativeV3CompilerTest.fixture(); var b = d.bindings().getFirst();
        var doc = b.documents().getFirst(); var p = doc.entities().getFirst();
        var missing = new Projection(p.id(), p.type(), p.path(), List.of(p.fields().getFirst()), p.references());
        var changed = new NativeDefinition(d.id(), d.revision(), d.logical(), List.of(new Binding(b.id(), b.engine(), b.storage(),
                b.schema(), b.table(), b.keyColumn(), b.xmlColumn(), b.keyType(), List.of(new Document(doc.id(), doc.key(), List.of(missing))))));
        var incomplete = assertInstanceOf(Incomplete.class, compiler.compile(changed));
        var physical = incomplete.diagnostics().stream().filter(x -> !x.code().equals("MECHANISM_UNQUALIFIED")).toList();
        assertFalse(physical.isEmpty());
        assertTrue(incomplete.diagnostics().stream().noneMatch(x -> x.code().equals("MECHANISM_UNQUALIFIED")));
        assertFalse(incomplete.isCompatibleWith(incomplete.checked()));
        assertFalse(new Incomplete(incomplete.checked(), physical).isCompatibleWith(incomplete.checked()));
        var future = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PUBLICATION, "MOCK_UNAVAILABLE", "", "Mock blocker.");
        assertFalse(new Incomplete(current().checked(), List.of(future)).isCompatibleWith(current().checked()));
    }
    @Test void statesKeepTheirClosedConstructionAndValueFreeRendering() {
        var c = current().checked(); var ready = new ReadyToPublish(c); var unavailable = unavailableOracle();
        assertTrue(ready.diagnostics().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> unavailable.diagnostics().add(unavailable.diagnostics().getFirst()));
        assertEquals("ReadyToPublishV3[redacted]", ready.toString());
        assertThrows(NullPointerException.class, () -> new ReadyToPublish(null));
        assertThrows(IllegalArgumentException.class, () -> new Incomplete(c, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Rejected(List.of()));
        var syntax = new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PARSE, "MOCK_PARSE", "", "Mock refusal.");
        assertThrows(IllegalArgumentException.class, () -> new Incomplete(c, List.of(syntax)));
        assertFalse(new Rejected(List.of(syntax)).isCompatibleWith(c));
    }
}
