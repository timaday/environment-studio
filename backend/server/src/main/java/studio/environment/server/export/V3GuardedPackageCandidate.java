package studio.environment.server.export;

import java.io.OutputStream;
import java.util.*;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeMechanisms;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanDefinition;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.workspace.NativeCommand;
import tools.jackson.databind.node.JsonNodeFactory;

/** Server-owned guarded package candidate generation; never execution or export-readiness authority. */
public final class V3GuardedPackageCandidate {
    public record Target(String destinationId, String engine, String host, int port, String database, String transport,
            String transportIdentity, String provisioningPolicyVersion, Map<String, String> expectedPhysicalIdentity,
            String serverVersion, String clientFamily, String clientVersion, String clientPlatform, String templateVersion) {
        public Target {
            expectedPhysicalIdentity = Collections.unmodifiableMap(new TreeMap<>(expectedPhysicalIdentity));
        }
        public String storage() { return templateVersion.equals("oracle-clob-v1") ? "clob" : "text"; }
        @Override public String toString() { return "PackageTarget[redacted]"; }
    }
    public sealed interface Result {
        record Rejected(String code) implements Result { }
        record Prepared(PackageAdmission.Result.Accepted input) implements Result {
            public Prepared { Objects.requireNonNull(input); }
            @Override public String toString() { return "PreparedPackageCandidate[redacted,unqualified]"; }
        }
    }
    public Result prepare(HostedPlanService.ViewAdmission admission, Target target, String inputFingerprint) {
        Objects.requireNonNull(admission); Objects.requireNonNull(target);
        if (inputFingerprint == null || !inputFingerprint.matches("[0-9a-f]{64}")) return new Result.Rejected("INVALID_REQUEST");
        try {
            var validation = admission.validationV3();
            if (!inputFingerprint.equals(validation.inputFingerprint())) return new Result.Rejected("CONFLICT");
            if (!validation.targetComplete()) return new Result.Rejected("INCOMPLETE_TARGET");
            var context = admission.packageContext();
            return admission.read((snapshot, cancellation) -> prepare(snapshot, context, target, inputFingerprint, cancellation));
        } catch (PlanRefusal refused) { return new Result.Rejected(refused.code().name()); }
        catch (PackageJson.Refusal refused) { return new Result.Rejected(refused.code); }
    }
    private Result prepare(HostedPlanService.ViewSnapshot snapshot, HostedPlanService.PackageContext context,
            Target target, String inputFingerprint, Cancellation cancellation) {
        live(cancellation);
        if (!context.destinationId().equals(target.destinationId())) return new Result.Rejected("CONFLICT");
        if (!context.observedDestination().engine().equals(target.engine())) return new Result.Rejected("DESTINATION_MISMATCH");
        if (!context.observedDestination().identity().equals(target.expectedPhysicalIdentity())) return new Result.Rejected("DESTINATION_MISMATCH");
        if (!(snapshot.definition().model() instanceof PlanDefinition.V3 model)) return new Result.Rejected("UNSUPPORTED_DEFINITION");
        var binding = model.checked().definition().bindings().stream().filter(value -> value.id().equals(context.bindingId()))
                .findFirst().orElse(null);
        if (binding == null) return new Result.Rejected("UNKNOWN_BINDING");
        if (!target.engine().equals(binding.engine().name().toLowerCase(Locale.ROOT)) || !target.storage().equals(binding.storage().name().toLowerCase(Locale.ROOT)))
            return new Result.Rejected("DEFINITION_BINDING_MISMATCH");
        if (!supported(target, binding)) return new Result.Rejected("EXPORT_UNAVAILABLE");
        var payload = new V3PlanPackagePayload().prepare(snapshot, cancellation);
        if (!(payload instanceof V3PlanPackagePayload.Result.Candidate candidate))
            return new Result.Rejected(((V3PlanPackagePayload.Result.Rejected) payload).code());
        var execution = JsonNodeFactory.instance.objectNode();
        execution.put("bindingDigest", context.bindingDigest());
        execution.put("bindingId", context.bindingId());
        var client = execution.putObject("client");
        client.put("family", target.clientFamily()); client.put("platform", target.clientPlatform()); client.put("version", target.clientVersion());
        execution.put("definitionPublicationDigest", context.definitionPublicationDigest());
        var destination = execution.putObject("destination");
        destination.put("database", target.database()); destination.put("host", target.host()); destination.put("id", target.destinationId());
        destination.put("port", target.port()); destination.put("provisioningPolicyVersion", target.provisioningPolicyVersion());
        destination.put("transport", target.transport()); destination.put("transportIdentity", target.transportIdentity());
        var identity = destination.putObject("expectedPhysicalIdentity");
        target.expectedPhysicalIdentity().forEach(identity::put);
        execution.put("engine", target.engine());
        var policies = execution.putArray("exportPolicies");
        snapshot.definition().policies().stream().filter(policy -> policy.bindingId().equals(context.bindingId()))
                .sorted(Comparator.comparing(NativeCommand.Policy::documentId, PackageJson.UTF8))
                .forEach(policy -> policies.addObject().put("content", policy.content()).put("documentId", policy.documentId()));
        execution.put("logicalDigest", context.logicalDigest());
        var mechanisms = execution.putObject("mechanisms");
        var required = new TreeMap<String, String>();
        NativeMechanisms.required(binding).forEach((key, version) -> required.put(key, version.toString()));
        required.put("structural-target-v1", "1"); required.put("plan-validation-v3", "1");
        required.forEach(mechanisms::put);
        execution.put("observationFingerprint", context.observedDestination().observationFingerprint());
        execution.put("parserVersion", "7.2.2-es-xml10-fifth-1");
        var profiles = execution.putArray("profilePublicationDigests");
        context.profilePublicationDigests().stream().sorted(PackageJson.UTF8).forEach(profiles::add);
        execution.put("planId", context.planId()); execution.put("planInputFingerprint", inputFingerprint); execution.put("planRevision", context.revision());
        execution.put("serverVersion", target.serverVersion()); execution.put("storage", target.storage());
        execution.put("supervisorVersion", "1"); execution.put("templateVersion", target.templateVersion()); execution.put("writerVersion", "1");
        live(cancellation);
        var admitted = new PackageAdmission().readPinnedV3(new NativeCompilationResult.ReadyToPublish(model.checked()),
                context.bindingId(), PackageJson.canonical(execution, PackageJson.SMALL), candidate.bytes());
        if (!(admitted instanceof PackageAdmission.Result.Accepted accepted)) return new Result.Rejected(((PackageAdmission.Result.Rejected) admitted).code());
        return new Result.Prepared(accepted);
    }
    private static boolean supported(Target target, NativeDefinition.Binding binding) {
        return binding.engine() == NativeDefinition.Engine.POSTGRESQL && binding.storage() == NativeDefinition.Storage.TEXT
                && target.engine().equals("postgresql") && target.serverVersion().equals("16.11")
                && target.clientFamily().equals("psql") && target.clientVersion().equals("16.11")
                && target.clientPlatform().equals("linux-amd64") && target.templateVersion().equals("postgresql16-text-v1");
    }
    public GuardedPackageAssembler.Result write(Result.Prepared prepared, OutputStream output, Cancellation cancellation) {
        return new GuardedPackageAssembler().write(prepared.input(), output, cancellation);
    }
    private static void live(Cancellation cancellation) { if (cancellation.cancelled()) PackageJson.fail("CANCELLED"); }
}
