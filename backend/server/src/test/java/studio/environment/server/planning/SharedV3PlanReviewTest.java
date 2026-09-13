package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.environment.core.Outcome;
import studio.environment.core.RequiredCheck;
import studio.environment.core.plan.*;
import studio.environment.core.workspace.NativeCommand;

/** Actual invented XML; existing explicitly labelled publication and observation witnesses. */
class SharedV3PlanReviewTest {
    static SharedV3PlanXmlTest allowed() {
        var fixture=new SharedV3PlanXmlTest();
        fixture.definitionLookup=p->new PlanPorts.PublishedDefinition(p.reference(),p.publicationDigest(),p.model(),
                List.of(new NativeCommand.Policy("mock-pg","sheet","protected-self-contained")));
        return fixture;
    }
    static HostedPlanService.ReviewCommand command(String revision,String fingerprint) {
        return new HostedPlanService.ReviewCommand(new HostedPlanService.Mutation(revision,UUID.randomUUID().toString()),
                fingerprint,"destination",HostedPlanService.ArtifactIntent.PROTECTED_SELF_CONTAINED);
    }
    static Outcome outcome(HostedPlanService.V3Validation result,RequiredCheck check) {
        return result.checks().stream().filter(c->c.check()==check).findFirst().orElseThrow().outcome();
    }
    @Test void exactReviewKeepsTheInputFingerprintAndCannotCreateExportAuthority() {
        var fixture=allowed();var service=fixture.service();String plan=fixture.inspected(service);
        assertTrue(service.materialize(fixture.lease,plan,"2").complete());
        var before=service.validateV3(fixture.lease,plan,"2");
        assertEquals(Outcome.UNKNOWN,outcome(before,RequiredCheck.REVIEW));
        var ack=service.reviewV3(fixture.lease,plan,command("2",before.inputFingerprint()));
        assertEquals(new HostedPlanService.Ack(plan,"2",java.util.Optional.empty()),ack);
        var after=service.validateV3(fixture.lease,plan,"2");
        assertEquals(before.inputFingerprint(),after.inputFingerprint());
        assertEquals(Outcome.PASS,outcome(after,RequiredCheck.REVIEW));
        assertEquals(Outcome.PASS,outcome(after,RequiredCheck.CONTENT_POLICY));
        assertEquals(Outcome.UNKNOWN,outcome(after,RequiredCheck.CLIENT_CAPABILITY));
        assertFalse(after.exportAvailable());
        assertEquals(PlanRefusal.Code.EXPORT_UNAVAILABLE,assertThrows(PlanRefusal.class,()->service.requestExport(fixture.lease,plan,"2")).code());
    }
    @Test void immutablePoliciesCannotBeSuppliedByAnotherBindingOrChangedByReview() {
        var alternatives=List.of(
                List.<NativeCommand.Policy>of(),
                List.of(new NativeCommand.Policy("mock-pg","sheet","deny")),
                List.of(new NativeCommand.Policy("other-binding","sheet","protected-self-contained")),
                List.of(new NativeCommand.Policy("mock-pg","sheet","protected-self-contained"),new NativeCommand.Policy("mock-pg","sheet","protected-self-contained")),
                List.of(new NativeCommand.Policy("mock-pg","sheet","protected-self-contained"),new NativeCommand.Policy("mock-pg","extra","deny")));
        var expected=List.of(Outcome.UNKNOWN,Outcome.FAIL,Outcome.UNKNOWN,Outcome.ERROR,Outcome.ERROR);
        for(int i=0;i<alternatives.size();i++) {
            var fixture=new SharedV3PlanXmlTest();var policies=alternatives.get(i);
            fixture.definitionLookup=p->new PlanPorts.PublishedDefinition(p.reference(),p.publicationDigest(),p.model(),policies);
            var service=fixture.service();String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
            var before=service.validateV3(fixture.lease,plan,"2");service.reviewV3(fixture.lease,plan,command("2",before.inputFingerprint()));
            var after=service.validateV3(fixture.lease,plan,"2");assertEquals(Outcome.PASS,outcome(after,RequiredCheck.REVIEW));
            assertEquals(expected.get(i),outcome(after,RequiredCheck.CONTENT_POLICY));assertEquals(before.inputFingerprint(),after.inputFingerprint());assertFalse(after.exportAvailable());
        }
    }
    @Test void deniedUnchangedUnmappedSiblingRemainsInTheWholeArtifactPolicy() {
        var fixture=allowed();var original=fixture.model.checked().definition();var binding=original.bindings().getFirst();
        var documents=new java.util.ArrayList<>(binding.documents());
        documents.add(new studio.environment.core.definitionv2.NativeDefinition.Document("unmapped-sheet","2",List.of()));
        var changed=new studio.environment.core.definitionv2.NativeDefinition.Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),binding.table(),binding.keyColumn(),binding.xmlColumn(),binding.keyType(),documents);
        var definition=new studio.environment.core.definitionv3.NativeDefinition(original.id(),original.revision(),original.logical(),List.of(changed));
        var checked=assertInstanceOf(studio.environment.core.definitionv3.NativeCompilationResult.Incomplete.class,
                new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(definition)).checked();
        fixture.model=new PlanDefinition.V3(checked);fixture.additionalXml=java.util.Map.of("unmapped-sheet","<mock-private-field>invented-canary</mock-private-field>");
        fixture.definitionLookup=p->new PlanPorts.PublishedDefinition(p.reference(),p.publicationDigest(),p.model(),List.of(
                new NativeCommand.Policy("mock-pg","sheet","protected-self-contained"),new NativeCommand.Policy("mock-pg","unmapped-sheet","deny")));
        var service=fixture.service();String plan=fixture.inspected(service);assertTrue(service.materialize(fixture.lease,plan,"2").complete());
        var snapshot=fixture.snapshot(service,plan,"2");assertEquals(snapshot.current().orElseThrow().sources(),snapshot.target().orElseThrow().sources());
        var before=service.validateV3(fixture.lease,plan,"2");service.reviewV3(fixture.lease,plan,command("2",before.inputFingerprint()));
        var after=service.validateV3(fixture.lease,plan,"2");assertEquals(Outcome.FAIL,outcome(after,RequiredCheck.CONTENT_POLICY));assertFalse(after.exportAvailable());
        assertFalse(after.toString().contains("invented-canary"));
    }
    @Test void staleInputWrongDestinationAndMissingTargetCannotInstallReview() {
        var fixture=allowed();var service=fixture.service();String plan=fixture.inspected(service);
        var current=service.validateV3(fixture.lease,plan,"2");
        refused(PlanRefusal.Code.INCOMPLETE_TARGET,()->service.reviewV3(fixture.lease,plan,command("2",current.inputFingerprint())));
        service.materialize(fixture.lease,plan,"2");var before=service.validateV3(fixture.lease,plan,"2");
        refused(PlanRefusal.Code.CONFLICT,()->service.reviewV3(fixture.lease,plan,command("2",current.inputFingerprint())));
        refused(PlanRefusal.Code.CONFLICT,()->service.reviewV3(fixture.lease,plan,command("1",before.inputFingerprint())));
        var wrong=new HostedPlanService.ReviewCommand(command("2",before.inputFingerprint()).mutation(),before.inputFingerprint(),"different-destination",HostedPlanService.ArtifactIntent.PROTECTED_SELF_CONTAINED);
        refused(PlanRefusal.Code.CONFLICT,()->service.reviewV3(fixture.lease,plan,wrong));
        assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
    }
    @Test void unchangedMaterializationPreservesReviewButFailedFreshLookupPermanentlyInvalidatesIt() {
        var fixture=allowed();var service=fixture.service();String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
        var before=service.validateV3(fixture.lease,plan,"2");var request=command("2",before.inputFingerprint());
        var ack=service.reviewV3(fixture.lease,plan,request);service.materialize(fixture.lease,plan,"2");
        assertEquals(Outcome.PASS,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
        var lookup=fixture.definitionLookup;fixture.definitionLookup=p->{throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);};
        refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION,()->service.validateV3(fixture.lease,plan,"2"));fixture.definitionLookup=lookup;
        assertEquals(before.inputFingerprint(),service.validateV3(fixture.lease,plan,"2").inputFingerprint());
        assertEquals(ack,service.reviewV3(fixture.lease,plan,request));
        assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
        service.reviewV3(fixture.lease,plan,command("2",before.inputFingerprint()));
        assertEquals(Outcome.PASS,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
    }
    @Test void replaySurvivesEditsAndRetirementWithoutReinstallingOldAuthority() {
        var fixture=allowed();var service=fixture.service();String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
        var before=service.validateV3(fixture.lease,plan,"2");var request=command("2",before.inputFingerprint());var ack=service.reviewV3(fixture.lease,plan,request);
        refused(PlanRefusal.Code.CONFLICT,()->service.discard(fixture.lease,plan,request.mutation()));
        var changed=new HostedPlanService.ReviewCommand(request.mutation(),"f".repeat(64),"destination",request.artifactIntent());
        refused(PlanRefusal.Code.CONFLICT,()->service.reviewV3(fixture.lease,plan,changed));
        service.replaceDraft(fixture.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),PlanPorts.Draft.empty());
        assertEquals(ack,assertDoesNotThrow(()->service.reviewV3(fixture.lease,plan,request)));
        assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(fixture.lease,plan,"3"),RequiredCheck.REVIEW));
        service.discard(fixture.lease,plan,new HostedPlanService.Mutation("3",UUID.randomUUID().toString()));
        assertEquals(ack,assertDoesNotThrow(()->service.reviewV3(fixture.lease,plan,request)));
    }
    @Test void reviewSharesTheExisting256ReceiptBoundWithCreationAndInspection() {
        var fixture=allowed();var service=fixture.service();String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
        String fingerprint=service.validateV3(fixture.lease,plan,"2").inputFingerprint();
        var first=command("2",fingerprint);var ack=service.reviewV3(fixture.lease,plan,first);
        // Two earlier successful commands plus254 review commands exactly fill the one256 ledger.
        for(int i=1;i<254;i++)assertEquals(ack,service.reviewV3(fixture.lease,plan,command("2",fingerprint)));
        refused(PlanRefusal.Code.CAPACITY,()->service.reviewV3(fixture.lease,plan,command("2",fingerprint)));
        refused(PlanRefusal.Code.CAPACITY,()->service.discard(fixture.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString())));
        assertEquals(ack,service.reviewV3(fixture.lease,plan,first));assertEquals("2",service.summary(fixture.lease,plan).revision());
    }
    private static void refused(PlanRefusal.Code expected,org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected,assertThrows(PlanRefusal.class,action).code());
    }

}
