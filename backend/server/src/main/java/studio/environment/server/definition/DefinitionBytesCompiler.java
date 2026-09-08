package studio.environment.server.definition;

import java.util.List;
import java.util.Objects;
import studio.environment.core.definition.DefinitionCompiler;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definition.DefinitionResult;

/** Local adapter only. It does not expose an HTTP route or persist supplied source. */
public final class DefinitionBytesCompiler {
    public enum Format { JSON, YAML }
    private final BoundedDefinitionParser parser = new BoundedDefinitionParser();
    private final DefinitionShapeValidator shape = new DefinitionShapeValidator();
    private final DefinitionCompiler compiler = new DefinitionCompiler();

    public DefinitionResult compile(byte[] source, Format format) {
        Objects.requireNonNull(source); Objects.requireNonNull(format);
        try {
            var tree = parser.parse(source, format);
            var diagnostics = shape.validate(tree);
            if (!diagnostics.isEmpty()) return new DefinitionResult.Rejected(diagnostics);
            return compiler.compile(DefinitionDraftReader.read(tree));
        } catch (BoundedDefinitionParser.Refusal refusal) {
            String message = switch (refusal.code()) {
                case "RESOURCE_LIMIT" -> "Reduce the definition to the documented input resource limits.";
                case "INVALID_UTF8" -> "Encode the definition as valid UTF-8.";
                default -> "Supply one valid definition object in the selected format.";
            };
            return new DefinitionResult.Rejected(List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PARSE,
                    refusal.code(), "", message)));
        }
    }
}
