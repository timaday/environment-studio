package studio.environment.server.profile;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.profile.ProfileComposer;
import studio.environment.core.profile.ProfileResult;
import studio.environment.core.profile.V3ProfileCapture;
import studio.environment.core.profile.V3ProfileComposer;
import studio.environment.core.profile.V3ProfileValidator;
import studio.environment.server.definition.BoundedDocumentParser;
import studio.environment.server.projection.DerivedGraphProjectionAdapter;

/** Internal portable v3 drafts; no publication or plan authority. */
public final class V3ProfileBytesAdapter {
    private final PortableProfileCodec codec = new PortableProfileCodec(ProfileWireEncoding.Version.V3);
    private final V3ProfileValidator validator = new V3ProfileValidator();
    public sealed interface Result {
        final class Accepted implements Result {
            private final ProfileResult.Checked checked;
            private Accepted(ProfileResult.Checked checked) { this.checked = checked; }
            public ProfileResult.Checked checked() { return checked; }
            @Override public String toString() { return "PortableV3Profile[redacted]"; }
        }
        record Rejected(List<ProfileResult.Diagnostic> diagnostics) implements Result {
            public Rejected { if (diagnostics.size() > 256) throw new IllegalArgumentException("Diagnostic limit exceeded."); diagnostics = List.copyOf(diagnostics); }
        }
    }
    public sealed interface ExportResult {
        final class Encoded implements ExportResult {
            private final byte[] bytes;
            private Encoded(byte[] bytes) { this.bytes = bytes.clone(); }
            public byte[] bytes() { return bytes.clone(); }
            @Override public String toString() { return "EncodedV3Profile[redacted]"; }
        }
        record Rejected(List<ProfileResult.Diagnostic> diagnostics) implements ExportResult {
            public Rejected { if (diagnostics.size() > 256) throw new IllegalArgumentException("Diagnostic limit exceeded."); diagnostics = List.copyOf(diagnostics); }
        }
    }
    public Result read(Checked definition, byte[] source, BoundedDocumentParser.Format format) {
        if (definition == null || source == null || format == null) return rejected("INVALID_INPUT");
        return accepted(codec.read(source, format, profile -> validator.validate(definition, profile)));
    }
    public Result capture(Checked definition, DerivedInput.Pin expected,
            DerivedGraphProjectionAdapter.Snapshot snapshot, ProfileCapture.Command command, BooleanSupplier cancelled) {
        if (definition == null || expected == null || snapshot == null || command == null || cancelled == null) return rejected("INVALID_INPUT");
        var observed = new DerivedGraphProjectionAdapter().project(definition, expected, snapshot, cancelled);
        if (observed instanceof DerivedGraphProjectionAdapter.Refused refused) return rejected(refused.code());
        var graph = ((DerivedGraphProjectionAdapter.Complete)observed).physical();
        try { ProfileWireEncoding.checkCaptureNodes(definition.definition().logical().entityTypes(), graph); }
        catch (ProfileWireEncoding.Limit refused) { return rejected(refused.code().name()); }
        var result = codec.portable(new V3ProfileCapture().capture(definition, graph, command),
                profile -> validator.validate(definition, profile));
        if (cancelled.getAsBoolean()) return rejected("CANCELLED");
        return accepted(result);
    }
    public ExportResult write(Checked definition, ProfileResult.Checked profile) {
        if (definition == null || profile == null || profile.profile() == null)
            return new ExportResult.Rejected(List.of(new ProfileResult.Diagnostic("INVALID_INPUT", "")));
        var encoded = codec.write(profile, value -> validator.validate(definition, value));
        if (encoded instanceof PortableProfileCodec.Rejected refused) return new ExportResult.Rejected(refused.diagnostics());
        return new ExportResult.Encoded(((PortableProfileCodec.Encoded)encoded).bytes());
    }
    public V3ProfileComposer.PreviewResult preview(Checked definition, Result.Accepted profile, Set<String> selected) {
        if (profile == null) return new V3ProfileComposer.PreviewResult.Rejected(List.of(new ProfileResult.Diagnostic("INVALID_INPUT", "")));
        return new V3ProfileComposer().preview(definition, profile.checked(), selected);
    }
    public ProfileComposer.CompositionResult compose(Checked definition, Result.Accepted profile, V3ProfileComposer.Preview preview,
            DerivedInput.Pin expected, DerivedGraphProjectionAdapter.Snapshot snapshot,
            List<ProfileComposer.Decision> decisions, BooleanSupplier cancelled) {
        if (definition == null || profile == null || preview == null || expected == null || snapshot == null || decisions == null || cancelled == null)
            return compositionRejected("INVALID_INPUT");
        var observed = new DerivedGraphProjectionAdapter().project(definition, expected, snapshot, cancelled);
        if (observed instanceof DerivedGraphProjectionAdapter.Refused refused) return compositionRejected(refused.code());
        var result = new V3ProfileComposer().compose(definition, profile.checked(), preview,
                ((DerivedGraphProjectionAdapter.Complete)observed).physical(), decisions);
        if (cancelled.getAsBoolean()) return compositionRejected("CANCELLED");
        return result;
    }
    private static Result accepted(ProfileResult result) {
        if (result instanceof ProfileResult.Rejected refused) return new Result.Rejected(refused.diagnostics());
        return new Result.Accepted(((ProfileResult.StructurallyValid)result).checked());
    }
    private static Result.Rejected rejected(String code) { return new Result.Rejected(List.of(new ProfileResult.Diagnostic(code, ""))); }
    private static ProfileComposer.CompositionResult.Rejected compositionRejected(String code) {
        return new ProfileComposer.CompositionResult.Rejected(List.of(new ProfileResult.Diagnostic(code, "")));
    }
}
