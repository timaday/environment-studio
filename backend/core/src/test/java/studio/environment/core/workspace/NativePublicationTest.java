package studio.environment.core.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.session.Owner;

class NativePublicationTest {
    final String id = "00000000-0000-4000-8000-000000000091";
    final Owner owner = new Owner("https://invented.invalid", "invented-owner");
    NativeRevision draft() {
        var binding = new Binding("mock", Engine.POSTGRESQL, Storage.TEXT,"mock","mock","key","xml",KeyType.TEXT,
            List.of(new NativeDefinition.Document("one","one",List.of()),new NativeDefinition.Document("two","two",List.of())));
        var definition = new NativeDefinition("invented",BigInteger.ONE,new Logical(List.of(),List.of(),List.of(),List.of()),List.of(binding));
        var checked = new NativeCompilationResult.Checked(definition,"1".repeat(64),Map.of("mock","2".repeat(64)),
            Map.of("native-compiler-v2",BigInteger.ONE,"xml-path-v1",BigInteger.ONE,"xml-span-v1",BigInteger.ONE,"generic-graph-v1",BigInteger.ONE));
        return new NativeRevision(id,"1",DraftCommand.Format.JSON,"independent mock source","3".repeat(64),"native-compiler-v2","2",new NativeRevision.Definition(checked,List.of()),Optional.empty());
    }
    NativeCommand.PublishDefinition publish(List<NativeCommand.Policy> policies) { return new NativeCommand.PublishDefinition(id,"1",UUID.randomUUID().toString(),policies); }
    @Test void publishesNextRevisionWithExactSourceAndSortedExplicitPolicies() {
        var current = draft(); var calls = new AtomicInteger();
        var store = store(current,calls);
        var service = new NativeWorkspace(store,null, ignored -> true);
        var result = service.mutate(owner,publish(List.of(new NativeCommand.Policy("mock","two","deny"),new NativeCommand.Policy("mock","one","protected-self-contained"))));
        assertEquals("43a73feed4d8d4bd5d3588a38fee3ee6d33c495569515164173952ed82647866",result.publication().orElseThrow().digest());
        assertEquals("2",result.workspaceRevision()); assertEquals("published",result.state());
        assertEquals(current.content(),result.content()); assertEquals(current.source(),result.source());
        assertEquals("one",result.publication().orElseThrow().exportPolicies().getFirst().documentId());
    }
    @Test void independentUtf8CommandDigestOracle() {
        var command=new NativeCommand.SaveDefinition(id,"0","00000000-0000-4000-8000-000000000092",DraftCommand.Format.JSON,"Independent Ω\nsource");
        assertEquals("5585a03f4392a1bd60257f3bf5a050adc8e1190ae28fe9b06e3ac91df9d13c96",NativeWorkspaceDigests.commandDigest(command));
    }
    @Test void incompleteAndUnknownMechanismsCannotPublish() {
        var original=draft();var def=(NativeRevision.Definition)original.content();
        var checked=new NativeCompilationResult.Checked(def.checked().definition(),def.checked().logicalDigest(),def.checked().bindingDigests(),Map.of("native-compiler-v2",BigInteger.TWO));
        var unsupported=new NativeRevision(original.objectId(),"1",original.format(),original.source(),original.sourceDigest(),original.compilerVersion(),original.schemaVersion(),new NativeRevision.Definition(checked,List.of()),Optional.empty());
        var command=publish(List.of(new NativeCommand.Policy("mock","one","deny"),new NativeCommand.Policy("mock","two","deny")));
        assertThrows(WorkspaceRejection.class,()->new NativeWorkspace(store(unsupported,new AtomicInteger()),null,o->true).mutate(owner,command));
        var blocked=new NativeRevision.Definition(def.checked(),List.of(new studio.environment.core.definition.DefinitionDiagnostic(studio.environment.core.definition.DefinitionDiagnostic.Phase.PUBLICATION,"MISSING","","Missing required mechanism.")));
        assertThrows(WorkspaceRejection.class,()->NativeWorkspace.eligibleDefinition(blocked));
    }
    @Test void exactReplayNeverCallsCurrentCompilerOrReferenceResolver() {
        var saved=draft();
        var store=new NativeStore() {
            public Optional<NativeRevision> replay(Owner owner,NativeCommand command){return Optional.of(saved);}
            public NativeRevision append(Owner owner,NativeCommand command,NativeRevision result){throw new AssertionError("Replay attempted mutation");}
            public NativeRevision read(Owner owner,String id,Optional<String> revision,boolean profile){throw new AssertionError("Replay attempted current reference resolution");}
            public List<NativeRevision> list(Owner owner,boolean profile){return List.of();}
        };
        var compiler=new NativeWorkspace.Compiler() {
            public NativeRevision.Definition definition(NativeCommand.SaveDefinition command){throw new AssertionError("Replay attempted compilation");}
            public NativeRevision.Profile profile(NativeCommand.SaveProfile command,NativeCompilationResult.ReadyToPublish definition){throw new AssertionError("Replay attempted profile compilation");}
        };
        assertEquals(saved,new NativeWorkspace(store,compiler,o->true).mutate(owner,new NativeCommand.SaveDefinition(id,"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,"different current compiler input")));
    }
    @Test void independentProfilePublicationDigestOracle() {
        var profile=new studio.environment.core.profile.Profile("mock",BigInteger.ONE,"1".repeat(64),List.of(new studio.environment.core.profile.Profile.Entity("one","mock","Neutral",List.of())),List.of());
        var content=new NativeRevision.Profile(new studio.environment.core.profile.ProfileResult.Checked(profile,"5".repeat(64)),new NativeCommand.Reference("00000000-0000-4000-8000-000000000093","2"));
        var revision=new NativeRevision(id,"2",DraftCommand.Format.JSON,"independent source","4".repeat(64),"profile-compiler-v2","2",content,Optional.empty());
        assertEquals("83fcf370acb42924478d03ad0ef2d412b2b622f075ad2642e69e7d7707726a5a",NativeWorkspaceDigests.publication(revision,"1",List.of()));
    }
    @Test void missingPolicyCannotPublish() {
        var service = new NativeWorkspace(store(draft(),new AtomicInteger()),null,ignored -> true);
        assertThrows(WorkspaceRejection.class,()->service.mutate(owner,publish(List.of(new NativeCommand.Policy("mock","one","deny")))));
    }
    @Test void revokedPublisherIsCheckedBeforeReplay() {
        var calls = new AtomicInteger();
        var service = new NativeWorkspace(store(draft(),calls),null,ignored -> false);
        assertEquals("WORKSPACE_FORBIDDEN",assertThrows(RuntimeException.class,()->service.mutate(owner,publish(List.of()))).getMessage());
        assertEquals(0,calls.get());
    }
    NativeStore store(NativeRevision current,AtomicInteger calls) {
        return new NativeStore() {
            public Optional<NativeRevision> replay(Owner o,NativeCommand c) { calls.incrementAndGet();return Optional.empty(); }
            public NativeRevision append(Owner o,NativeCommand c,NativeRevision r) { return r; }
            public NativeRevision read(Owner o,String id,Optional<String> r,boolean p) { return current; }
            public List<NativeRevision> list(Owner o,boolean p) { return List.of(current); }
        };
    }
}
