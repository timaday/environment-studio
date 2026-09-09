package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.plan.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;
import studio.environment.server.definition.*;
import studio.environment.server.workspace.PlanWorkspaceBridge;

class PlanWorkspaceBridgeTest {
    final Owner owner=new Owner("https://invented.invalid","mock-owner");
    final NativeCommand.Reference reference=new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2");
    NativeRevision stored(boolean published,String compiler) throws Exception {
        var ready=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionBytesCompiler().compile(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")),DefinitionBytesCompiler.Format.JSON));
        var policies=new ArrayList<NativeCommand.Policy>();
        for(var binding:ready.checked().definition().bindings()) for(var document:binding.documents()) policies.add(new NativeCommand.Policy(binding.id(),document.id(),"protected-self-contained"));
        return new NativeRevision(reference.objectId(),"2",DraftCommand.Format.JSON,"never-recompiled-source","source-digest",compiler,"2",new NativeRevision.Definition(ready.checked(),List.of()),published?Optional.of(new NativeRevision.Publication("publication-digest","1",policies)):Optional.empty());
    }
    PlanWorkspaceBridge bridge(NativeRevision revision) {
        return new PlanWorkspaceBridge(new NativeStore() {
            public Optional<NativeRevision> replay(Owner owner,NativeCommand command) { throw new AssertionError("NO_REPLAY_LOOKUP"); }
            public NativeRevision append(Owner owner,NativeCommand command,NativeRevision value) { throw new AssertionError("NO_WRITE"); }
            public NativeRevision read(Owner actual,String id,Optional<String> number,boolean profile) {
                assertEquals(owner,actual); assertEquals(reference.objectId(),id); assertEquals(Optional.of("2"),number); assertFalse(profile); return revision;
            }
            public List<NativeRevision> list(Owner owner,boolean profile) { throw new AssertionError("NO_CURRENT_LOOKUP"); }
        });
    }
    @Test void readsOwnedExactImmutablePublicationWithoutCompilingStoredSourceOrWriting() throws Exception {
        var revision=stored(true,"native-compiler-v2");
        var result=bridge(revision).definition(owner,reference);
        assertEquals("publication-digest",result.publicationDigest()); assertEquals(reference,result.reference());
        assertFalse(result.toString().contains("never-recompiled"));
    }
    @Test void compilerReadinessAndStoredHistoryCannotStandInForCurrentPublicationEligibility() throws Exception {
        assertEquals(PlanRefusal.Code.PUBLICATION_REQUIRED,assertThrows(PlanRefusal.class,()->bridge(stored(false,"native-compiler-v2")).definition(owner,reference)).code());
        assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->bridge(stored(true,"future-compiler")).definition(owner,reference)).code());
    }
    @Test void historicalPublishedCompilerCannotCreateNewPlan() throws Exception {
        var current = stored(true, "native-compiler-v2"); var checked = ((NativeRevision.Definition) current.content()).checked();
        var mechanisms = new TreeMap<>(checked.mechanisms()); mechanisms.put("native-compiler-v2", java.math.BigInteger.ONE);
        var old = new NativeRevision(current.objectId(), current.workspaceRevision(), current.format(), current.source(), current.sourceDigest(),
            current.compilerVersion(), current.schemaVersion(), new NativeRevision.Definition(new NativeCompilationResult.Checked(
                checked.definition(), checked.logicalDigest(), checked.bindingDigests(), mechanisms), List.of()), current.publication());
        assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION, assertThrows(PlanRefusal.class, () -> bridge(old).definition(owner, reference)).code());
    }
}
