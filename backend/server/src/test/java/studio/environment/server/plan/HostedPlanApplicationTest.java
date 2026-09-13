package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.profile.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.definition.BoundedDocumentParser;
import studio.environment.server.profile.ProfileBytesAdapter;

class HostedPlanApplicationTest {
    private static ObservationResult.Observation qualifiedMock(ObservationResult.Observation source) {
        var evidence=new HashMap<>(PlanHttpTestConfiguration.mockObservationEvidence());
        var destination=new HashMap<String,Object>();((Map<?,?>)evidence.get("destination")).forEach((key,value)->destination.put((String)key,value));
        destination.put("id","configured");evidence.put("destination",destination);
        return new ObservationResult.Observation("a".repeat(64),source.logicalDigest(),source.bindingDigest(),source.documents(),evidence);
    }
    @Test void completeObservationToTwoDocumentTargetThenPartialReusePreservesPriorFreshIdentityAndUnselectedSibling() throws Exception {
        var fixtures=new PlanContentAdapterTest(); var definition=fixtures.definition(); var observed=qualifiedMock(fixtures.observation(definition));
        var profileAdapter=new ProfileBytesAdapter();
        var profile=assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class,profileAdapter.read(definition.compiled(),Files.readAllBytes(Path.of("../../fixtures/profile-v2/profile.json")),BoundedDocumentParser.Format.JSON));
        var profileRef=new NativeCommand.Reference("00000000-0000-4000-8000-000000000003","2");
        Workspace workspace=new Workspace() {
            public PublishedDefinition definition(Owner owner,NativeCommand.Reference reference) { return definition; }
            public PublishedProfile profile(Owner owner,NativeCommand.Reference reference,PublishedDefinition definition) {
                assertEquals(profileRef,reference); return new PublishedProfile(reference,"invented-profile-publication",profile.checked());
            }
        };
        ObservationPort observations=new ObservationPort() {
            public ObservationResult observe(Selection selection,TransientCredentials credentials,Cancellation cancellation) { throw new AssertionError("RESERVATION_REQUIRED"); }
            public Reservation reserve(Selection selection) {
                return new Reservation.Admitted(new Permit() {
                    public ObservationResult observe(TransientCredentials credentials,Cancellation cancel) {
                        assertTrue(Arrays.equals("MockReader".toCharArray(),credentials.copyUser()),"Reader identity must preserve exact case");
                        assertTrue(Arrays.equals("MiXeD-Password-Canary".toCharArray(),credentials.copyPassword()),"Authentication value must preserve exact case");
                        credentials.close(); return new ObservationResult.Complete(observed);
                    }
                    public void close() { }
                });
            }
        };
        var authority=new SessionLedger(Clock.systemUTC(),ignored->{});
        var lease=((SessionLedger.Accepted)authority.admit("invented-session",new Owner("https://invented.invalid","reader-owner"))).lease();
        var service=new HostedPlanService(authority::guard,workspace,Map.of("configured",new Destination("configured",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,observations)),new PlanContentAdapter(),System::nanoTime);
        var plan=service.create(lease,UUID.randomUUID().toString(),definition.reference(),"mock-pg","configured");
        var reservation=service.reserve(lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        service.submit(lease,reservation.operationId().orElseThrow(),(user,password)->{
            "MockReader".getChars(0,10,user,0); "MiXeD-Password-Canary".getChars(0,21,password,0); return new CredentialLengths(10,21);
        });
        var fresh=new TargetIntent.Ref.Fresh("new-palette","palette"); var alpha=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        String parentDigest=observed.documents().stream().filter(document->document.documentId().equals("palette-sheet")).findFirst().orElseThrow().sourceDigest();
        var draft=new Draft(new TargetIntent(List.of(
                new TargetIntent.EntityDecision.Retain(alpha,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.KeepObserved()),Map.of("uses",new TargetIntent.ReferenceValue.To(fresh))),
                new TargetIntent.EntityDecision.Create(fresh,Map.of("tag",new TargetIntent.FieldValue.Entered("second-palette"),"shade",new TargetIntent.FieldValue.Entered("cool & \t𐀀")),Map.of())),List.of()),
                List.of(new Placement(fresh,"palette-sheet","palettes",new Parent.Existing("palette-sheet",parentDigest,0))));
        assertEquals("3",service.replaceDraft(lease,plan.planId(),new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),draft).revision());
        var preview=service.previewProfile(lease,plan.planId(),"3",profileRef,List.of("first"));
        assertEquals(List.of("dependency","first"),preview.dependencies().included().stream().map(Profile.Entity::id).sorted().toList());
        String alphaHandle=service.entities(lease,plan.planId(),"3",false,0,100).entities().stream()
            .filter(e->e.type().equals("glyph") && e.fields().stream().anyMatch(f->f.field().equals("tag") && f.value().orElse("").equals("alpha")))
            .findFirst().orElseThrow().handle();
        var mutation=new HostedPlanService.Mutation("3",UUID.randomUUID().toString());
        var action=new PlanCommand.Action.Compose(profileRef,HostedPlanService.compositionPreviewDigest(preview),List.of("first"),List.of(
            new PlanCommand.ProfileDecision.UseExisting("first",new PlanCommand.Ref.Existing(alphaHandle)),
            new PlanCommand.ProfileDecision.UseExisting("dependency",new PlanCommand.Ref.Fresh("new-palette","palette"))));
        var command=new PlanCommand(mutation,action);
        var stale=new PlanCommand(new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),new PlanCommand.Action.Compose(profileRef,"0".repeat(64),action.selectedRoots(),action.decisions()));
        assertEquals(PlanRefusal.Code.STALE_PREVIEW,assertThrows(PlanRefusal.class,()->service.command(lease,plan.planId(),stale)).code());
        assertEquals("3",service.summary(lease,plan.planId()).revision());
        assertEquals("4",service.command(lease,plan.planId(),command).revision());
        assertTrue(service.summary(lease,plan.planId()).targetComplete());
        assertEquals(Files.readString(Path.of("../../fixtures/structural-target/expected-glyphs.xml")),service.comparison(lease,plan.planId(),"4",true,"glyph-sheet",ViewMode.RAW,true).text());
        assertEquals(Files.readString(Path.of("../../fixtures/structural-target/expected-palettes.xml")),service.comparison(lease,plan.planId(),"4",true,"palette-sheet",ViewMode.RAW,true).text());
        assertEquals("4",service.command(lease,plan.planId(),command).revision());
        assertFalse(service.validate(lease,plan.planId(),"4").exportAvailable());
        var whole=service.previewProfile(lease,plan.planId(),"4",profileRef,List.of());
        assertEquals(3,whole.dependencies().included().size());
        assertFalse(whole.toString().contains("second-palette"));
        service.discard(lease,plan.planId(),new HostedPlanService.Mutation("4",UUID.randomUUID().toString()));
        assertEquals("4",service.command(lease,plan.planId(),command).revision());
        assertThrows(PlanRefusal.class,()->service.summary(lease,plan.planId()));
    }
    @Test void profileReuseRequiresExplicitValuesBeforeExactNoopMaterialization() throws Exception {
        var fixtures=new PlanContentAdapterTest(); var definition=fixtures.definition(); var observed=qualifiedMock(fixtures.observation(definition));
        var portable=assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class,new ProfileBytesAdapter().read(definition.compiled(),Files.readAllBytes(Path.of("../../fixtures/profile-v2/profile.json")),BoundedDocumentParser.Format.JSON));
        var reference=new NativeCommand.Reference("00000000-0000-4000-8000-000000000003","2");
        Workspace workspace=new Workspace() {
            public PublishedDefinition definition(Owner owner,NativeCommand.Reference ref) { return definition; }
            public PublishedProfile profile(Owner owner,NativeCommand.Reference ref,PublishedDefinition def) { return new PublishedProfile(reference,"mock-profile-publication",portable.checked()); }
        };
        ObservationPort observations=new ObservationPort() {
            public ObservationResult observe(Selection selection,TransientCredentials credentials,Cancellation cancel) { throw new AssertionError("RESERVATION_REQUIRED"); }
            public Reservation reserve(Selection selection) { return new Reservation.Admitted(new Permit() {
                public ObservationResult observe(TransientCredentials credentials,Cancellation cancel) { credentials.close(); return new ObservationResult.Complete(observed); }
                public void close() { }
            }); }
        };
        var authority=new SessionLedger(Clock.systemUTC(),ignored->{});
        var lease=((SessionLedger.Accepted)authority.admit("mock-lease",new Owner("https://invented.invalid","owner"))).lease();
        var service=new HostedPlanService(authority::guard,workspace,Map.of("configured",new Destination("configured",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,observations)),new PlanContentAdapter(),System::nanoTime);
        var plan=service.create(lease,UUID.randomUUID().toString(),definition.reference(),"mock-pg","configured");
        var operation=service.reserve(lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        service.submit(lease,operation.operationId().orElseThrow(),(user,password)->{ user[0]='u'; password[0]='p'; return new CredentialLengths(1,1); });
        var preview=service.previewProfile(lease,plan.planId(),"2",reference,List.of("first"));
        service.composeProfile(lease,plan.planId(),new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),preview,List.of(
                new ProfileComposer.Decision.UseExisting("first",new ObservedGraph.Key("glyph","alpha")),
                new ProfileComposer.Decision.UseExisting("dependency",new ObservedGraph.Key("palette","shared"))));
        assertFalse(service.summary(lease,plan.planId()).targetComplete(),"Selecting original entities must not infer KeepObserved values");
        assertEquals(List.of("FIELD_NOT_EDITABLE"),service.materialize(lease,plan.planId(),"3").diagnostics());
        var alpha=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var palette=new TargetIntent.Ref.Existing(new ObservedGraph.Key("palette","shared"));
        var explicit=new Draft(new TargetIntent(List.of(
                new TargetIntent.EntityDecision.Retain(alpha,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.KeepObserved()),Map.of("uses",new TargetIntent.ReferenceValue.To(palette))),
                new TargetIntent.EntityDecision.Retain(palette,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"shade",new TargetIntent.FieldValue.KeepObserved()),Map.of())),List.of()),List.of());
        service.replaceDraft(lease,plan.planId(),new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),explicit);
        assertTrue(service.summary(lease,plan.planId()).targetComplete());
        for(var document:observed.documents()) assertEquals(document.xml(),service.comparison(lease,plan.planId(),"4",true,document.documentId(),ViewMode.RAW,true).text());
    }

}
