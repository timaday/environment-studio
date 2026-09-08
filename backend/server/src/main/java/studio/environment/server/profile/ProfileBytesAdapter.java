package studio.environment.server.profile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.profile.*;
import studio.environment.server.definition.BoundedDocumentParser;
import studio.environment.server.projection.ProjectionResult;

/** Strict portable drafts only: no persistence, publication or observation authority. */
public final class ProfileBytesAdapter {
    private final BoundedDocumentParser parser = new BoundedDocumentParser();
    private final ProfileValidator validator = new ProfileValidator();
    private final Schema schema;
    public ProfileBytesAdapter() {
        try (InputStream source = ProfileBytesAdapter.class.getResourceAsStream("/schemas/profile-v2.schema.json")) {
            if (source == null) throw new IllegalStateException("Packaged profile schema unavailable.");
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,
                    builder -> builder.schemaLoader(loader -> loader.fetchRemoteResources(false).block(iri -> true)))
                    .getSchema(JsonMapper.builder().build().readTree(source));
        } catch (IOException exception) { throw new IllegalStateException("Packaged profile schema unreadable."); }
    }
    public Result read(ReadyToPublish definition, byte[] source, BoundedDocumentParser.Format format) {
        var parsed = parser.parse(source, format);
        if (parsed instanceof BoundedDocumentParser.Result.Rejected refusal) return rejected(refusal.code().name());
        return portable(definition, validateTree(definition, ((BoundedDocumentParser.Result.Parsed)parsed).tree()));
    }
    private ProfileResult validateTree(ReadyToPublish definition, JsonNode tree) {
        if (!schema.validate(tree).isEmpty()) return ProfileResult.rejected("SCHEMA_VIOLATION", "");
        var entities = new ArrayList<Profile.Entity>();
        for (var node : tree.get("entities")) {
            var fields = new ArrayList<String>(); node.get("requiredInputs").forEach(f -> fields.add(f.asString()));
            entities.add(new Profile.Entity(node.get("id").asString(), node.get("type").asString(), node.get("label").asString(), fields));
        }
        var relations = new ArrayList<Profile.Relation>();
        for (var node : tree.get("relations")) relations.add(new Profile.Relation(node.get("type").asString(), node.get("from").asString(), node.get("to").asString()));
        return validator.validate(definition, new Profile(tree.get("id").asString(), tree.get("revision").bigIntegerValue(), tree.get("logicalDefinitionDigest").asString(), entities, relations));
    }
    public Result capture(ReadyToPublish definition, ProjectionResult.Accepted observation, ProfileCapture.Command command) {
        if (!definition.checked().logicalDigest().equals(observation.logicalDigest())) return rejected("INCOMPATIBLE_DEFINITION");
        try { ProfileWireEncoding.checkCaptureNodes(definition, observation.graph()); }
        catch (ProfileWireEncoding.Limit refused) { return rejected("RESOURCE_LIMIT"); }
        return portable(definition, new ProfileCapture().capture(definition, new GraphValidationResult.Accepted(observation.graph()), command));
    }
    public ProfileComposer.CompositionResult compose(ReadyToPublish definition, Result.Accepted profile,
            ProfileComposer.Preview preview, ProjectionResult.Accepted currentTarget, java.util.List<ProfileComposer.Decision> decisions) {
        if (!definition.checked().logicalDigest().equals(currentTarget.logicalDigest()))
            return new ProfileComposer.CompositionResult.Rejected(java.util.List.of(new ProfileResult.Diagnostic("INCOMPATIBLE_DEFINITION", "")));
        return new ProfileComposer().compose(definition, profile.checked(), preview, new GraphValidationResult.Accepted(currentTarget.graph()), decisions);
    }
    public sealed interface ExportResult {
        final class Encoded implements ExportResult {
            private final byte[] bytes;
            private Encoded(byte[] bytes) { this.bytes = bytes.clone(); }
            public byte[] bytes() { return bytes.clone(); }
            @Override public String toString() { return "EncodedProfile[redacted]"; }
        }
        record Rejected(java.util.List<ProfileResult.Diagnostic> diagnostics) implements ExportResult {
            public Rejected { if (diagnostics.size() > 256) throw new IllegalArgumentException("Diagnostic limit exceeded."); diagnostics = java.util.List.copyOf(diagnostics); }
        }
    }
    /** Canonical UTF-8 JSON; also valid YAML 1.2. Caller supplies the compatible definition. */
    public ExportResult write(ReadyToPublish definition, ProfileResult.Checked profile) {
        try { ProfileWireEncoding.checkProfileNodes(profile.profile()); }
        catch (ProfileWireEncoding.Limit refused) { return exportRejected("RESOURCE_LIMIT"); }
        var validated = validator.validate(definition, profile.profile());
        if (validated instanceof ProfileResult.Rejected refused) return new ExportResult.Rejected(refused.diagnostics());
        if (!((ProfileResult.StructurallyValid)validated).checked().equals(profile)) return new ExportResult.Rejected(java.util.List.of(new ProfileResult.Diagnostic("STALE_PROFILE", "")));
        return encodePortable(definition, profile);
    }

    /** Accepted is an adapter-created, fully portable draft, not publication authority. */
    public sealed interface Result {
        final class Accepted implements Result {
            private final ProfileResult.Checked checked;
            private Accepted(ProfileResult.Checked checked) { this.checked = checked; }
            public ProfileResult.Checked checked() { return checked; }
            @Override public String toString() { return "PortableProfile[redacted]"; }
        }
        record Rejected(java.util.List<ProfileResult.Diagnostic> diagnostics) implements Result {
            public Rejected { if (diagnostics.size() > 256) throw new IllegalArgumentException("Diagnostic limit exceeded."); diagnostics = java.util.List.copyOf(diagnostics); }
        }
    }
    private Result portable(ReadyToPublish definition, ProfileResult structural) {
        if (structural instanceof ProfileResult.Rejected refused) return new Result.Rejected(refused.diagnostics());
        var checked = ((ProfileResult.StructurallyValid)structural).checked();
        var encoded = encodePortable(definition, checked);
        if (encoded instanceof ExportResult.Rejected refused) return new Result.Rejected(refused.diagnostics());
        return new Result.Accepted(checked);
    }
    private ExportResult encodePortable(ReadyToPublish definition, ProfileResult.Checked checked) {
        final byte[] bytes;
        try { bytes = ProfileWireEncoding.encode(checked.profile()); }
        catch (ProfileWireEncoding.Limit refused) { return exportRejected("RESOURCE_LIMIT"); }
        var parsed = parser.parse(bytes, BoundedDocumentParser.Format.JSON);
        if (parsed instanceof BoundedDocumentParser.Result.Rejected refused) return exportRejected(refused.code().name());
        var roundTrip = validateTree(definition, ((BoundedDocumentParser.Result.Parsed)parsed).tree());
        if (!(roundTrip instanceof ProfileResult.StructurallyValid valid) || !valid.checked().equals(checked)) return exportRejected("WIRE_OUTCOME_MISMATCH");
        return new ExportResult.Encoded(bytes);
    }
    private static Result.Rejected rejected(String code) { return new Result.Rejected(java.util.List.of(new ProfileResult.Diagnostic(code, ""))); }
    private static ExportResult.Rejected exportRejected(String code) { return new ExportResult.Rejected(java.util.List.of(new ProfileResult.Diagnostic(code, ""))); }
}
