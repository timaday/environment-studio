package studio.environment.server.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.CRC32;
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
            var payloadMetrics = input.canonicalPayloadMetrics();
            // Rebind the program digest to the exact canonical payload that the archive carries.
            var canonical = input.withCanonicalPayloadDigest(payloadMetrics.sha256());
            live(cancellation);
            var engine = TransactionTemplates.route(canonical);
            TransactionTemplates.Metrics sqlMetrics = null; byte[] sql = null; byte[] payload = null;
            if (engine == PackageData.Engine.POSTGRESQL) sqlMetrics = PostgresTransaction.metrics(canonical);
            else {
                payload = input.canonicalPayload();
                var generated = new TransactionTemplates().generate(canonical);
                if (generated instanceof TransactionTemplates.Result.Rejected refused) return new Result.Rejected(refused.code());
                sql = ((TransactionTemplates.Result.Candidate) generated).rawBytes();
            }
            live(cancellation);
            var instructions = PackageInstructions.bytes();
            var manifest = JsonNodeFactory.instance.objectNode();
            manifest.put("format", "es-guarded-package-v1"); manifest.set("execution", canonical.executionTree());
            manifest.put("programDigest", canonical.programDigest());
            var counts = manifest.putObject("counts"); var actual = canonical.counts();
            counts.put("records", actual.records()); counts.put("changedRecords", actual.changedRecords());
            counts.put("originalBytes", actual.originalBytes()); counts.put("targetBytes", actual.targetBytes());
            var members = manifest.putObject("members");
            members.putObject("payload.json")
                    .put("bytes", engine == PackageData.Engine.POSTGRESQL ? payloadMetrics.bytes() : payload.length)
                    .put("sha256", engine == PackageData.Engine.POSTGRESQL ? payloadMetrics.sha256() : PackageJson.sha256(payload));
            members.putObject("transaction.sql").put("bytes", engine == PackageData.Engine.POSTGRESQL ? sqlMetrics.bytes() : sql.length)
                    .put("sha256", engine == PackageData.Engine.POSTGRESQL ? sqlMetrics.sha256() : PackageJson.sha256(sql));
            members.putObject("instructions.txt").put("bytes", instructions.length).put("sha256", PackageJson.sha256(instructions));
            var encoded = PackageJson.canonical(manifest, PackageJson.SMALL);
            new PackageAdmission().manifest(encoded);
            live(cancellation);
            var guarded = new OutputStream() {
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
            };
            var result = engine == PackageData.Engine.POSTGRESQL
                ? writePostgres(encoded, payloadMetrics, sqlMetrics, instructions, guarded, canonical, input, cancellation)
                : new StrictPackageZip().write(List.of(StrictPackageZip.Member.owned("manifest.json", encoded),
                    StrictPackageZip.Member.owned("payload.json", payload), StrictPackageZip.Member.owned("transaction.sql", sql),
                    StrictPackageZip.Member.owned("instructions.txt", instructions)), guarded);
            live(cancellation);
            if (result instanceof StrictPackageZip.WriteResult.Rejected refused) return new Result.Rejected(refused.code());
            var written = (StrictPackageZip.WriteResult.Written) result;
            return new Result.Candidate(written.bytes(), written.sha256());
        } catch (PackageJson.Refusal refused) { return new Result.Rejected(refused.code); }
        catch (UncheckedIOException failure) { return new Result.Rejected("OUTPUT_FAILURE"); }
    }
    private static StrictPackageZip.WriteResult writePostgres(byte[] manifest, PackageJson.Metrics payload,
            TransactionTemplates.Metrics sql, byte[] instructions, OutputStream output,
            PackageAdmission.Result.Accepted canonical, PackageAdmission.Result.Accepted source, Cancellation cancellation) {
        var zip = new StrictPackageZip();
        return zip.writeStreaming(List.of(member("manifest.json", manifest),
                new StrictPackageZip.StreamMember("payload.json", payload.bytes(), payload.crc32(), sink -> {
                    source.writeCanonicalPayload(sink); live(cancellation);
                }),
                new StrictPackageZip.StreamMember("transaction.sql", sql.bytes(), sql.crc32(), sink -> {
                    try { PostgresTransaction.write(canonical, sink); } catch (UncheckedIOException failure) { throw failure.getCause(); }
                    live(cancellation);
                }), member("instructions.txt", instructions)), output);
    }
    private static StrictPackageZip.StreamMember member(String name, byte[] bytes) {
        var crc = new CRC32(); crc.update(bytes);
        return new StrictPackageZip.StreamMember(name, bytes.length, crc.getValue(), sink -> sink.write(bytes));
    }
    private static void live(Cancellation cancellation) { if (cancellation.cancelled()) PackageJson.fail("CANCELLED"); }
}
