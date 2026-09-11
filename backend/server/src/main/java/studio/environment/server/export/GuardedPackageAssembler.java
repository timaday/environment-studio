package studio.environment.server.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import studio.environment.core.observation.ObservationPort.Cancellation;
import tools.jackson.databind.node.JsonNodeFactory;

/** Internal deterministic candidate only; never export or execution authority. */
public final class GuardedPackageAssembler {
    public sealed interface Result {
        record Rejected(String code) implements Result { }
        record Candidate(long bytes, String sha256) implements Result {
            public boolean qualified() { return false; }
            @Override public String toString() { return "PackageCandidate[unqualified]"; }
        }
    }
    public Result write(PackageAdmission.Result.Accepted input, OutputStream output, Cancellation cancellation) {
        if (input == null || output == null || cancellation == null) return new Result.Rejected("INVALID_INPUT");
        try {
            live(cancellation);
            // Rebind the program to the exact canonical payload that the archive carries.
            var admission = new PackageAdmission();
            var read = admission.read(input.canonicalExecution(), input.canonicalPayload());
            if (read instanceof PackageAdmission.Result.Rejected refused) return new Result.Rejected(refused.code());
            var canonical = (PackageAdmission.Result.Accepted) read;
            live(cancellation);
            var generated = new TransactionTemplates().generate(canonical);
            if (generated instanceof TransactionTemplates.Result.Rejected refused) return new Result.Rejected(refused.code());
            var sql = ((TransactionTemplates.Result.Candidate) generated).bytes();
            live(cancellation);
            var payload = canonical.canonicalPayload(); var instructions = PackageInstructions.bytes();
            var manifest = JsonNodeFactory.instance.objectNode();
            manifest.put("format", "es-guarded-package-v1"); manifest.set("execution", canonical.executionTree());
            manifest.put("programDigest", canonical.programDigest());
            var counts = manifest.putObject("counts"); var actual = canonical.counts();
            counts.put("records", actual.records()); counts.put("changedRecords", actual.changedRecords());
            counts.put("originalBytes", actual.originalBytes()); counts.put("targetBytes", actual.targetBytes());
            var members = manifest.putObject("members");
            var data = List.of(new StrictPackageZip.Member("payload.json", payload),
                    new StrictPackageZip.Member("transaction.sql", sql), new StrictPackageZip.Member("instructions.txt", instructions));
            for (var member : data) {
                live(cancellation); var bytes = member.bytes();
                members.putObject(member.name()).put("bytes", bytes.length).put("sha256", PackageJson.sha256(bytes));
            }
            var encoded = PackageJson.canonical(manifest, PackageJson.SMALL);
            admission.manifest(encoded);
            live(cancellation);
            var result = new StrictPackageZip().write(List.of(new StrictPackageZip.Member("manifest.json", encoded),
                    data.get(0), data.get(1), data.get(2)), new OutputStream() {
                @Override public void write(int value) throws IOException {
                    live(cancellation); output.write(value); live(cancellation);
                }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    for (int position = offset; position < offset + length;) {
                        live(cancellation); int count = Math.min(16384, offset + length - position);
                        output.write(bytes, position, count); position += count;
                    }
                    live(cancellation);
                }
            });
            live(cancellation);
            if (result instanceof StrictPackageZip.WriteResult.Rejected refused) return new Result.Rejected(refused.code());
            var written = (StrictPackageZip.WriteResult.Written) result;
            return new Result.Candidate(written.bytes(), written.sha256());
        } catch (PackageJson.Refusal refused) { return new Result.Rejected(refused.code); }
    }
    private static void live(Cancellation cancellation) { if (cancellation.cancelled()) PackageJson.fail("CANCELLED"); }
}
