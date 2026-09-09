package studio.environment.core.workspace;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Native framing of closed command/publication identities; never JSON serialization. */
public final class V3NativeWorkspaceDigests {
    private V3NativeWorkspaceDigests() { }
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
    public static String commandDigest(NativeCommand command) { return hash("ES-WORKSPACE-COMMAND-3",command(command)); }
    public static Map<String,String> reference(NativeCommand.Reference reference) { return Map.of("objectId",reference.objectId(),"workspaceRevision",reference.workspaceRevision()); }
    public static List<Map<String,String>> policies(List<NativeCommand.Policy> policies) {
        return NativeCommand.sorted(policies).stream().map(p -> Map.of("bindingId",p.bindingId(),"documentId",p.documentId(),"content",p.content())).toList();
    }
    public static String publication(V3NativeRevision revision, String sourceRevision, List<NativeCommand.Policy> policies) {
        var fields = new TreeMap<String,Object>();
        fields.put("objectId",revision.objectId());fields.put("workspaceRevision",revision.workspaceRevision());fields.put("sourceRevision",sourceRevision);
        fields.put("sourceDigest",revision.sourceDigest());fields.put("compilerVersion",revision.compilerVersion());fields.put("schemaVersion",revision.schemaVersion());
        switch(revision.content()) {
            case V3NativeRevision.Definition d -> {
                fields.put("logicalDigest",d.checked().logicalDigest());fields.put("bindingDigests",d.checked().bindingDigests());
                var versions = new TreeMap<String,String>();d.checked().mechanisms().forEach((k,v)->versions.put(k,v.toString()));
                fields.put("mechanisms",versions);fields.put("exportPolicies",policies(policies));
            }
            case V3NativeRevision.Profile p -> { fields.put("contentDigest",p.checked().contentDigest());fields.put("definition",reference(p.definition())); }
        }
        return hash(revision.profile() ? "ES-PROFILE-PUBLICATION-3" : "ES-DEFINITION-PUBLICATION-3",fields);
    }
    public static String source(String source) { return bytes(utf8(source)); }
    private static String bytes(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    public static String hash(String domain,Object value) {
        var hash=digest();hash.update(domain.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);frame(hash,value);return HexFormat.of().formatHex(hash.digest());
    }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch(NoSuchAlgorithmException failure) { throw new IllegalStateException("Required digest unavailable."); } }
    private static void frame(MessageDigest hash,Object value) {
        if(value instanceof String text) { byte[] bytes=utf8(text);ascii(hash,"S"+bytes.length+":");hash.update(bytes); }
        else if(value instanceof BigInteger integer) ascii(hash,"I"+integer+";");
        else if(value instanceof Boolean flag) ascii(hash,flag?"T":"F");
        else if(value instanceof List<?> list) { ascii(hash,"A"+list.size()+":");list.forEach(v->frame(hash,v)); }
        else if(value instanceof Map<?,?> map) {
            ascii(hash,"O"+map.size()+":");map.entrySet().stream().sorted((a,b)->Arrays.compareUnsigned(utf8((String)a.getKey()),utf8((String)b.getKey())))
                .forEach(e->{frame(hash,e.getKey());frame(hash,e.getValue());});
        } else throw new IllegalArgumentException("Unsupported frame value.");
    }
    private static byte[] utf8(String value) {
        try {
            var buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); return bytes;
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST);
        }
    }
    private static void ascii(MessageDigest digest,String text) { digest.update(text.getBytes(StandardCharsets.US_ASCII)); }
}
