package studio.environment.server.export;

import java.nio.charset.StandardCharsets;
import java.util.List;

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
            public List<Block> blocks() { return blocks; }
            public String sha256() { return PackageJson.sha256(bytes); }
            public boolean qualified() { return false; }
            @Override public String toString() { return "TransactionCandidate[redacted,unqualified]"; }
        }
    }
    public Result generate(PackageAdmission.Result.Accepted input) {
        try {
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
            return pg?PostgresTransaction.generate(input):OracleTransaction.generate(input);
        }catch(PackageJson.Refusal failure){return new Result.Rejected(failure.code);}
    }
    static String id(String value) { return "\""+value.replace("\"","\"\"")+"\""; }
    static String hex(String value) { return java.util.HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8)); }
    static String pgText(String value) { return "pg_catalog.convert_from(pg_catalog.decode('"+hex(value)+"','hex'),'UTF8')"; }
    static final class Source {
        private final StringBuilder value=new StringBuilder();
        void add(String text) { if(text.length()>PackageJson.LARGE-value.length())PackageJson.fail("RESOURCE_LIMIT");for(int i=0;i<text.length();i++)if(text.charAt(i)>127)PackageJson.fail("SQL_SOURCE_ENCODING");value.append(text); }
        void line(String text) { add(text);add("\n"); }
        byte[] bytes() { return value.toString().getBytes(StandardCharsets.US_ASCII); }
    }
}
