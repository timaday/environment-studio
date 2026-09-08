package studio.environment.server.definition;

import java.util.Objects;
import tools.jackson.databind.JsonNode;

/** Server-only reuse of the existing strict document parser and all its budgets. */
public final class BoundedDocumentParser {
    public enum Format { JSON, YAML }
    public enum RefusalCode { RESOURCE_LIMIT, INVALID_UTF8, INVALID_SYNTAX }
    public sealed interface Result {
        record Parsed(JsonNode tree) implements Result {
            @Override public String toString() { return "ParsedDocument[redacted]"; }
        }
        record Rejected(RefusalCode code) implements Result { }
    }
    public Result parse(byte[] source, Format format) {
        Objects.requireNonNull(source); Objects.requireNonNull(format);
        try {
            return new Result.Parsed(new BoundedDefinitionParser().parse(source,
                    format == Format.JSON ? DefinitionBytesCompiler.Format.JSON : DefinitionBytesCompiler.Format.YAML));
        } catch (BoundedDefinitionParser.Refusal refused) {
            return new Result.Rejected(RefusalCode.valueOf(refused.code()));
        }
    }
}
