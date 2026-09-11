package studio.environment.server.export;

import java.nio.charset.StandardCharsets;
import java.util.*;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.plan.HostedPlanService.ViewSnapshot;
import studio.environment.core.plan.PlanDefinition;
import studio.environment.core.plan.PlanPorts;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.server.plan.PlanContentAdapter;
import tools.jackson.databind.node.JsonNodeFactory;
import static studio.environment.server.export.PackageJson.fail;

/** Internal unqualified payload candidate; never export authority. */
public final class V3PlanPackagePayload {
    public sealed interface Result {
        record Rejected(String code) implements Result { }
        final class Candidate implements Result {
            private final byte[] bytes;
            private Candidate(byte[] bytes) { this.bytes = bytes; }
            public byte[] bytes() { return bytes.clone(); }
            public boolean qualified() { return false; }
            @Override public String toString() { return "PlanPayloadCandidate[redacted,unqualified]"; }
        }
    }
    public Result prepare(ViewSnapshot snapshot, Cancellation cancellation) {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(cancellation);
        try {
            live(cancellation);
            if (!(snapshot.definition().model() instanceof PlanDefinition.V3 model)) fail("UNSUPPORTED_DEFINITION");
            new PlanContentAdapter().verifyV3(snapshot, true, cancellation);
            var binding = snapshot.definition().model().bindings().stream().filter(b -> b.id().equals(snapshot.binding()))
                    .findFirst().orElseThrow(() -> new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED));
            var policies = new HashMap<String, String>();
            for (var policy : snapshot.definition().policies()) {
                live(cancellation);
                if (policy.bindingId().equals(binding.id()) && policies.putIfAbsent(policy.documentId(), policy.content()) != null)
                    fail("POLICY_INVENTORY_MISMATCH");
            }
            var documentIds = new HashSet<String>();
            for (var document : binding.documents()) {
                if (!documentIds.add(document.id()) || !"protected-self-contained".equals(policies.get(document.id())))
                    fail("POLICY_INVENTORY_MISMATCH");
            }
            if (!policies.keySet().equals(documentIds)) fail("POLICY_INVENTORY_MISMATCH");
            if (documentIds.isEmpty() || documentIds.size() > 128) fail("RESOURCE_LIMIT");
            var original = sources(snapshot.selected(false)); var target = sources(snapshot.selected(true));
            if (!original.keySet().equals(documentIds) || !target.keySet().equals(documentIds)) fail("INVENTORY_MISMATCH");
            var root = JsonNodeFactory.instance.objectNode();
            root.put("schemaVersion", "1"); root.put("bindingId", binding.id());
            root.put("engine", binding.engine().name().toLowerCase(Locale.ROOT));
            root.put("storage", binding.storage().name().toLowerCase(Locale.ROOT));
            var table = root.putObject("table");
            table.put("schema", binding.schema()); table.put("name", binding.table());
            table.put("keyColumn", binding.keyColumn()); table.put("xmlColumn", binding.xmlColumn());
            table.put("keyType", binding.keyType().name().toLowerCase(Locale.ROOT));
            var records = root.putArray("records"); long originalBytes = 0, targetBytes = 0;
            for (var document : binding.documents().stream().sorted(Comparator.comparing(d -> d.id(), PackageJson.UTF8)).toList()) {
                live(cancellation);
                var before = bytes(original.get(document.id())); var after = bytes(target.get(document.id()));
                originalBytes += before.length; targetBytes += after.length;
                if (originalBytes > 16L * 1024 * 1024 || targetBytes > 16L * 1024 * 1024) fail("RESOURCE_LIMIT");
                var record = records.addObject(); record.put("documentId", document.id());
                var key = record.putObject("key"); key.put("type", binding.keyType().name().toLowerCase(Locale.ROOT)); key.put("value", document.key());
                record.put("originalHex", HexFormat.of().formatHex(before)); record.put("targetHex", HexFormat.of().formatHex(after));
            }
            live(cancellation);
            var encoded = PackageJson.canonical(root, PackageJson.LARGE);
            live(cancellation);
            return new Result.Candidate(encoded);
        } catch (PlanRefusal refused) { return new Result.Rejected(refused.code().name()); }
        catch (PackageJson.Refusal refused) { return new Result.Rejected(refused.code); }
    }
    private static Map<String, String> sources(PlanPorts.Content content) {
        var sources = new HashMap<String, String>();
        for (var source : content.sources()) if (sources.putIfAbsent(source.documentId(), source.xml()) != null) fail("INVENTORY_MISMATCH");
        return sources;
    }
    private static byte[] bytes(String source) {
        if (source.length() > 1_048_576) fail("RESOURCE_LIMIT");
        PackageJson.unicode(source);
        var bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 3_145_728) fail("RESOURCE_LIMIT");
        return bytes;
    }
    private static void live(Cancellation cancellation) { if (cancellation.cancelled()) fail("CANCELLED"); }
}
