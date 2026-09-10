import type { Diagnostic } from "./hosted";

function fields(value: unknown, names: readonly string[]): value is Record<string, unknown> {
  return (
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    Object.keys(value).length === names.length &&
    names.every((name) => Object.hasOwn(value, name))
  );
}
const scalar = (value: unknown): value is string =>
  typeof value === "string" && !/[\ud800-\udfff]/u.test(value);

/** Only the complete semantic save refusal establishes that no draft was committed. */
export function workspaceRejection(value: unknown): Diagnostic[] | null {
  if (
    !fields(value, ["kind", "diagnostics"]) ||
    value.kind !== "rejected" ||
    !Array.isArray(value.diagnostics) ||
    value.diagnostics.length < 1 ||
    value.diagnostics.length > 256
  )
    return null;
  const result: Diagnostic[] = [];
  for (const diagnostic of value.diagnostics) {
    if (
      !fields(diagnostic, ["phase", "code", "pointer", "message"]) ||
      typeof diagnostic.phase !== "string" ||
      !["parse", "shape", "semantic", "publication"].includes(diagnostic.phase) ||
      typeof diagnostic.code !== "string" ||
      !/^[A-Z][A-Z0-9_]{0,127}(?![\s\S])/u.test(diagnostic.code) ||
      !scalar(diagnostic.pointer) ||
      !scalar(diagnostic.message)
    )
      return null;
    result.push({
      phase: diagnostic.phase,
      code: diagnostic.code,
      pointer: diagnostic.pointer,
      message: diagnostic.message,
    });
  }
  return result;
}
