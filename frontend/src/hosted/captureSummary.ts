import { invalid } from "../api/hostedV3Decoding";
import { profileModel } from "../api/hostedV3ProfileDecoding";

/** Read-only native JSON projection; keep the original source unchanged for saving. */
export function captureSummary(source: string) {
  const parsed: unknown = JSON.parse(source);
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return invalid();
  if (!("revision" in parsed) || typeof parsed.revision !== "number") return invalid();
  // Native profile v3 has exactly one numeric value: its arbitrary-precision revision.
  // Scan the already syntax-checked source, skipping complete JSON strings. Never recover
  // that revision from a rounded JavaScript number or coerce other model field types.
  const numbers: string[] = [];
  for (const match of source.matchAll(
    /"(?:[^"\\]|\\[\s\S])*"|(-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)/g,
  )) {
    if (match[1] !== undefined) numbers.push(match[1]);
  }
  if (numbers.length !== 1) return invalid();
  return profileModel({ ...parsed, revision: numbers[0] });
}
