package studio.environment.core.workspace;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Native framing of closed command/publication identities; never JSON serialization. */
public final class NativeWorkspaceDigests {
    private NativeWorkspaceDigests() { }
    public static Map<String,Object> command(NativeCommand command) {
        var fields = new TreeMap<String,Object>();
        fields.put("expectedRevision",command.expectedRevision()); fields.put("requestId",command.requestId());
        switch(command) {
            case NativeCommand.SaveDefinition c -> { fields.put("kind","save-definition");fields.put("format",c.format().name());fields.put("source",c.source()); }
            case NativeCommand.SaveProfile c -> { fields.put("kind","save-profile");fields.put("format",c.format().name());fields.put("source",c.source());fields.put("definition",reference(c.definition())); }
            case NativeCommand.PublishDefinition c -> { fields.put("kind","publish-definition");fields.put("exportPolicies",policies(c.exportPolicies())); }
            case NativeCommand.PublishProfile c -> fields.put("kind","publish-profile");
        }
        return Collections.unmodifiableMap(fields);
    }
    public static String commandDigest(NativeCommand command) { return hash("ES-WORKSPACE-COMMAND-2",command(command)); }
    public static Map<String,String> reference(NativeCommand.Reference reference) { return Map.of("objectId",reference.objectId(),"workspaceRevision",reference.workspaceRevision()); }
    public static List<Map<String,String>> policies(List<NativeCommand.Policy> policies) {
        return NativeCommand.sorted(policies).stream().map(p -> Map.of("bindingId",p.bindingId(),"documentId",p.documentId(),"content",p.content())).toList();
    }
    public static String publication(NativeRevision revision, String sourceRevision, List<NativeCommand.Policy> policies) {
        var fields = new TreeMap<String,Object>();
        fields.put("objectId",revision.objectId());fields.put("workspaceRevision",revision.workspaceRevision());fields.put("sourceRevision",sourceRevision);
        fields.put("sourceDigest",revision.sourceDigest());fields.put("compilerVersion",revision.compilerVersion());fields.put("schemaVersion",revision.schemaVersion());
        switch(revision.content()) {
            case NativeRevision.Definition d -> {
                fields.put("logicalDigest",d.checked().logicalDigest());fields.put("bindingDigests",d.checked().bindingDigests());
                var versions = new TreeMap<String,String>();d.checked().mechanisms().forEach((k,v)->versions.put(k,v.toString()));
                fields.put("mechanisms",versions);fields.put("exportPolicies",policies(policies));
            }
            case NativeRevision.Profile p -> { fields.put("contentDigest",p.checked().contentDigest());fields.put("definition",reference(p.definition())); }
        }
        return hash(revision.profile() ? "ES-PROFILE-PUBLICATION-2" : "ES-DEFINITION-PUBLICATION-2",fields);
    }
    public static String source(String source) { return bytes(source.getBytes(StandardCharsets.UTF_8)); }
    private static String bytes(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    public static String hash(String domain,Object value) {
        var hash=digest();hash.update(domain.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);frame(hash,value);return HexFormat.of().formatHex(hash.digest());
    }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch(NoSuchAlgorithmException failure) { throw new IllegalStateException("Required digest unavailable."); } }
    private static void frame(MessageDigest hash,Object value) {
        if(value instanceof String text) { byte[] bytes=text.getBytes(StandardCharsets.UTF_8);ascii(hash,"S"+bytes.length+":");hash.update(bytes); }
        else if(value instanceof BigInteger integer) ascii(hash,"I"+integer+";");
        else if(value instanceof Boolean flag) ascii(hash,flag?"T":"F");
        else if(value instanceof List<?> list) { ascii(hash,"A"+list.size()+":");list.forEach(v->frame(hash,v)); }
        else if(value instanceof Map<?,?> map) {
            ascii(hash,"O"+map.size()+":");map.entrySet().stream().sorted((a,b)->Arrays.compareUnsigned(((String)a.getKey()).getBytes(StandardCharsets.UTF_8),((String)b.getKey()).getBytes(StandardCharsets.UTF_8)))
                .forEach(e->{frame(hash,e.getKey());frame(hash,e.getValue());});
        } else throw new IllegalArgumentException("Unsupported frame value.");
    }
    private static void ascii(MessageDigest digest,String text) { digest.update(text.getBytes(StandardCharsets.US_ASCII)); }
}
