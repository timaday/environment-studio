package studio.environment.server.profile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.core.profile.Profile;
import studio.environment.core.profile.ProfileResult;
import studio.environment.server.definition.BoundedDocumentParser;

/** Shared closed wire mechanics; a versioned facade supplies its semantic validator. */
final class PortableProfileCodec {
    private final BoundedDocumentParser parser = new BoundedDocumentParser();
    private final ProfileWireEncoding.Version version;
    private final Schema schema;
    PortableProfileCodec(ProfileWireEncoding.Version version) {
        this.version = version;
        try (InputStream source = PortableProfileCodec.class.getResourceAsStream("/schemas/profile-v" + version.wire + ".schema.json")) {
            if (source == null) throw new IllegalStateException("Packaged profile schema unavailable.");
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,
                    builder -> builder.schemaLoader(loader -> loader.fetchRemoteResources(false).block(iri -> true)))
                    .getSchema(JsonMapper.builder().build().readTree(source));
        } catch (IOException exception) { throw new IllegalStateException("Packaged profile schema unreadable."); }
    }
    ProfileResult read(byte[] source, BoundedDocumentParser.Format format, Function<Profile, ProfileResult> validator) {
        if (source != null && source.length > 1_048_576) return ProfileResult.rejected("BYTE_LIMIT", "");
        var parsed = parser.parse(source, format);
        if (parsed instanceof BoundedDocumentParser.Result.Rejected refusal) return ProfileResult.rejected(refusal.code().name(), "");
        return portable(validateTree(((BoundedDocumentParser.Result.Parsed)parsed).tree(), validator), validator);
    }
    private ProfileResult validateTree(JsonNode tree, Function<Profile, ProfileResult> validator) {
        if (!schema.validate(tree).isEmpty()) return ProfileResult.rejected("SCHEMA_VIOLATION", "");
        var entities = new ArrayList<Profile.Entity>();
        for (var node : tree.get("entities")) {
            var fields = new ArrayList<String>(); node.get("requiredInputs").forEach(f -> fields.add(f.asString()));
            entities.add(new Profile.Entity(node.get("id").asString(), node.get("type").asString(), node.get("label").asString(), fields));
        }
        var relations = new ArrayList<Profile.Relation>();
        for (var node : tree.get("relations")) relations.add(new Profile.Relation(node.get("type").asString(), node.get("from").asString(), node.get("to").asString()));
        return validator.apply(new Profile(tree.get("id").asString(), tree.get("revision").bigIntegerValue(), tree.get("logicalDefinitionDigest").asString(), entities, relations));
    }
    ProfileResult portable(ProfileResult structural, Function<Profile, ProfileResult> validator) {
        if (structural instanceof ProfileResult.Rejected) return structural;
        var encoded = encode(((ProfileResult.StructurallyValid)structural).checked(), validator);
        if (encoded instanceof Rejected refused) return new ProfileResult.Rejected(refused.diagnostics());
        return structural;
    }
    sealed interface Encoding permits Encoded, Rejected { }
    record Encoded(byte[] bytes) implements Encoding {
        Encoded { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public String toString() { return "EncodedProfile[redacted]"; }
    }
    record Rejected(List<ProfileResult.Diagnostic> diagnostics) implements Encoding {
        Rejected { diagnostics = List.copyOf(diagnostics); }
    }
    Encoding write(ProfileResult.Checked profile, Function<Profile, ProfileResult> validator) {
        try { ProfileWireEncoding.checkProfileNodes(profile.profile()); }
        catch (ProfileWireEncoding.Limit refused) { return rejected(refused.code().name()); }
        var validated = validator.apply(profile.profile());
        if (validated instanceof ProfileResult.Rejected refused) return new Rejected(refused.diagnostics());
        if (!((ProfileResult.StructurallyValid)validated).checked().equals(profile)) return rejected("STALE_PROFILE");
        return encode(profile, validator);
    }
    private Encoding encode(ProfileResult.Checked checked, Function<Profile, ProfileResult> validator) {
        final byte[] bytes;
        try { bytes = ProfileWireEncoding.encode(checked.profile(), version); }
        catch (ProfileWireEncoding.Limit refused) { return rejected(refused.code().name()); }
        var parsed = parser.parse(bytes, BoundedDocumentParser.Format.JSON);
        if (parsed instanceof BoundedDocumentParser.Result.Rejected refused) return rejected(refused.code().name());
        var roundTrip = validateTree(((BoundedDocumentParser.Result.Parsed)parsed).tree(), validator);
        if (!(roundTrip instanceof ProfileResult.StructurallyValid valid) || !valid.checked().equals(checked)) return rejected("WIRE_OUTCOME_MISMATCH");
        return new Encoded(bytes);
    }
    private static Rejected rejected(String code) { return new Rejected(List.of(new ProfileResult.Diagnostic(code, ""))); }
}
