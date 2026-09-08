package studio.environment.server.definition;

import java.util.List;
import java.util.Objects;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.definitionv2.NativeDefinitionCompiler;

/** Explicit v2 entry point. V1 continues through its unchanged public result contract. */
public final class NativeDefinitionBytesCompiler {
    private final BoundedDefinitionParser parser = new BoundedDefinitionParser();
    private final DefinitionShapeValidator shape = new DefinitionShapeValidator(DefinitionShapeValidator.Version.V2);
    private final NativeDefinitionCompiler compiler = new NativeDefinitionCompiler();
    public NativeCompilationResult compile(byte[] source, DefinitionBytesCompiler.Format format) {
        Objects.requireNonNull(source); Objects.requireNonNull(format);
        try {
            var tree = parser.parse(source, format);
            var diagnostics = shape.validate(tree);
            if (!diagnostics.isEmpty()) return new NativeCompilationResult.Rejected(diagnostics);
            return compiler.compile(NativeDefinitionReader.read(tree));
        } catch (BoundedDefinitionParser.Refusal refusal) {
            String message = switch (refusal.code()) {
                case "RESOURCE_LIMIT" -> "Reduce the definition to the documented input resource limits.";
                case "INVALID_UTF8" -> "Encode the definition as valid UTF-8.";
                default -> "Supply one valid definition object in the selected format.";
            };
            return new NativeCompilationResult.Rejected(List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PARSE,
                    refusal.code(), "", message)));
        }
    }
}
