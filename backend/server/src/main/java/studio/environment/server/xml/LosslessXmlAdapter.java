package studio.environment.server.xml;

import java.util.List;
import java.util.Objects;

/** Generic internal mechanism; no HTTP, persistence, application meaning or export authority. */
public final class LosslessXmlAdapter {
    private final HardenedXmlProjection projection = new HardenedXmlProjection();
    public XmlResult project(String source) {
        Objects.requireNonNull(source);
        try { return new XmlResult.Accepted(projection.project(source)); }
        catch (XmlRefusal refusal) { return rejected(refusal.code()); }
    }
    public XmlResult apply(XmlDocument document, String expectedDigest, List<XmlEdit> edits) {
        Objects.requireNonNull(document); Objects.requireNonNull(expectedDigest); Objects.requireNonNull(edits);
        try {
            if (!document.digest().equals(expectedDigest)) throw new XmlRefusal("STALE_SOURCE");
            if (edits.size() > XmlLexicalScanner.MAX_TOKENS) throw new XmlRefusal("RESOURCE_LIMIT");
            return new XmlResult.Accepted(new XmlPatchBatch(projection).apply(document, List.copyOf(edits)));
        } catch (XmlRefusal refusal) { return rejected(refusal.code()); }
    }
    private static XmlResult rejected(String code) {
        String message = switch (code) {
            case "RESOURCE_LIMIT" -> "Reduce the document or edits to the documented resource limits.";
            case "UNSUPPORTED_XML" -> "Supply XML 1.0 without DTDs or unsupported inclusion, signature or encryption constructs.";
            case "STALE_SOURCE" -> "Bind the edit batch to the exact current source digest.";
            case "STALE_REFERENCE" -> "Select references from the current source revision.";
            case "EXPECTED_VALUE_MISMATCH" -> "Review the current attribute value before applying the replacement.";
            case "NAMESPACE_ATTRIBUTE" -> "Namespace declaration editing is not supported.";
            case "ROOT_REMOVAL" -> "Select a non-root element for removal.";
            case "INVALID_ANCHOR" -> "Select an existing direct child of the insertion parent.";
            case "CONFLICTING_EDITS" -> "Resolve overlapping or conflicting edits before applying the batch.";
            case "INVALID_INSERTION" -> "Supply exactly one self-contained XML element for insertion.";
            case "INSERTION_NAMESPACE_MISMATCH" -> "Declare insertion namespaces explicitly to preserve their standalone meaning.";
            case "OUTCOME_MISMATCH" -> "Review the edit batch because the complete target did not match its intended outcomes.";
            default -> "Supply supported well-formed XML with valid Unicode characters.";
        };
        return new XmlResult.Rejected(List.of(new XmlResult.Diagnostic(code, message)));
    }
}
