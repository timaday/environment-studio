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

/** Scan valid JSON text before duplicate names disappear into its object value. */
function uniqueMembers(source: string): boolean {
  const objects: Set<string>[] = [];
  const colon = /[ \t\r\n]*:/y;
  // JSON.parse has already checked grammar. Strings hide punctuation in values;
  // only a string followed by a colon is a decoded member name in this object.
  for (const token of source.matchAll(/"(?:[^"\\]|\\[\s\S])*"|[{}]/g)) {
    const text = token[0];
    if (text === "{") objects.push(new Set());
    else if (text === "}") objects.pop();
    else {
      colon.lastIndex = token.index + text.length;
      if (!colon.test(source)) continue;
      const members = objects.at(-1);
      const name: string = JSON.parse(text);
      if (!members || members.has(name)) return false;
      members.add(name);
    }
  }
  return true;
}

/** Only an unambiguous complete raw save refusal establishes no draft commit. */
export function workspaceRejection(source: string): Diagnostic[] | null {
  let value: unknown;
  try {
    value = JSON.parse(source);
  } catch {
    return null;
  }
  if (!uniqueMembers(source)) return null;
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
