package studio.environment.server.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HexFormat;
import java.util.zip.CRC32;

/** Deterministic candidate SQL only; no execution or export authority. */
public final class TransactionTemplates {
    public record Block(int offset, int length) { }
    public sealed interface Result {
        record Rejected(String code) implements Result { }
        final class Candidate implements Result {
            private final byte[] bytes;
            private final List<Block> blocks;
            Candidate(byte[] bytes, List<Block> blocks) { this.bytes=bytes;this.blocks=List.copyOf(blocks); }
            public byte[] bytes() { return bytes.clone(); }
            byte[] rawBytes() { return bytes; }
            public List<Block> blocks() { return blocks; }
            public String sha256() { return PackageJson.sha256(bytes); }
            public boolean qualified() { return false; }
            @Override public String toString() { return "TransactionCandidate[redacted,unqualified]"; }
        }
    }
    public Result generate(PackageAdmission.Result.Accepted input) {
        try {
            return route(input)==PackageData.Engine.POSTGRESQL?PostgresTransaction.generate(input):OracleTransaction.generate(input);
        }catch(PackageJson.Refusal failure){return new Result.Rejected(failure.code);}
    }
    static PackageData.Engine route(PackageAdmission.Result.Accepted input) {
        if(input==null)PackageJson.fail("INVALID_INPUT");
        var e=input.execution();
        if(e.exportPolicies().stream().anyMatch(p->!p.content().equals("protected-self-contained")))PackageJson.fail("CONTENT_POLICY_DENIED");
        boolean pg=e.engine()==PackageData.Engine.POSTGRESQL;
        boolean tuple = pg
                ? (e.client().version().equals("18.6") && e.versions().template().equals("postgresql-text-v1"))
                    || (e.client().version().equals("16.11") && e.versions().template().equals("postgresql16-text-v1"))
                : e.client().version().equals("23.26.3.0.0") && e.versions().template().equals("oracle-clob-v1");
        if(!e.client().platform().equals("linux-amd64") || !e.client().family().equals(pg?"psql":"sqlplus")
                || !e.versions().server().equals(e.client().version()) || !tuple)PackageJson.fail("TEMPLATE_VERSION_UNSUPPORTED");
        return e.engine();
    }
    static String id(String value) { return "\""+value.replace("\"","\"\"")+"\""; }
    static String hex(String value) { return java.util.HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8)); }
    static String pgText(String value) { return "pg_catalog.convert_from(pg_catalog.decode('"+hex(value)+"','hex'),'UTF8')"; }
    record Metrics(long bytes, long crc32, String sha256) { }
    static final class Source {
        private final StringBuilder value; private byte[] output; private OutputStream stream;
        private MessageDigest digest; private CRC32 crc; private byte[] scratch; private int size;
        Source() { this(true); }
        private Source(boolean stored) { value=stored?new StringBuilder():null; }
        static Source counter() { return new Source(false); }
        static Source metricsSource() { var source=new Source(false); source.digest=digest(); source.crc=new CRC32(); source.scratch=new byte[8192]; return source; }
        static Source stream(OutputStream stream) { var source=new Source(false); source.stream=stream; source.scratch=new byte[8192]; return source; }
        Source allocate() { if(value!=null)PackageJson.fail("INVALID_INPUT"); var fill=new Source(false); fill.output=new byte[size]; return fill; }
        void add(String text) {
            if((long)size+text.length()>PackageJson.LARGE)PackageJson.fail("RESOURCE_LIMIT");
            for(int i=0;i<text.length();) {
                if(stream!=null || digest!=null || crc!=null) {
                    int count=Math.min(scratch.length,text.length()-i);
                    for(int j=0;j<count;j++) { char c=text.charAt(i+j); if(c>127)PackageJson.fail("SQL_SOURCE_ENCODING"); scratch[j]=(byte)c; }
                    if(digest!=null)digest.update(scratch,0,count); if(crc!=null)crc.update(scratch,0,count);
                    if(stream!=null)try{stream.write(scratch,0,count);}catch(IOException failure){throw new UncheckedIOException(failure);}
                    i+=count;
                } else {
                    char c=text.charAt(i); if(c>127)PackageJson.fail("SQL_SOURCE_ENCODING");
                    if(output!=null)output[size+i]=(byte)c; i++;
                }
            }
            if(value!=null)value.append(text); size+=text.length();
        }
        void line(String text) { add(text);add("\n"); }
        byte[] bytes() {
            if(output!=null) { if(size!=output.length)PackageJson.fail("INVALID_INPUT"); return output; }
            return value.toString().getBytes(StandardCharsets.US_ASCII);
        }
        Metrics snapshotMetrics() { if(digest==null || crc==null)PackageJson.fail("INVALID_INPUT"); return new Metrics(size,crc.getValue(),HexFormat.of().formatHex(digest.digest())); }
        private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA256_UNAVAILABLE"); } }
    }
}
