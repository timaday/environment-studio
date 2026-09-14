import type { ReactNode } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { useV3PlanInspection } from "./useV3PlanInspection";
import { V3DocumentNavigator } from "./V3DocumentNavigator";
import "./V3DocumentComparison.css";

type ComparisonState = Pick<
  ReturnType<typeof useV3PlanInspection>,
  | "plan"
  | "inventory"
  | "selected"
  | "mode"
  | "consent"
  | "reading"
  | "current"
  | "target"
  | "bindingRail"
  | "select"
  | "setMode"
  | "setConsent"
  | "load"
>;
type Side = "current" | "target";
type BindingRailItem = ComparisonState["bindingRail"][number];
type TextHit = Readonly<{ side: Side; start: number; end: number }>;
type LocationHit = Readonly<{
  side: Side;
  index: number;
  start: number;
  end: number;
  label: string;
  role: string;
  attribute: string;
  elementIndex: string;
  declarationId: string;
}>;

type Highlight = Readonly<{
  start: number;
  end: number;
  kind: "search" | "active-search" | "location";
}>;
const highlightPriority: Readonly<Record<Highlight["kind"], number>> = {
  location: 3,
  "active-search": 2,
  search: 1,
};

function matches(text: string, query: string): readonly { start: number; end: number }[] {
  const needle = query.trim();
  if (!needle) return [];
  const haystack = text.toLocaleLowerCase();
  const lower = needle.toLocaleLowerCase();
  const found: { start: number; end: number }[] = [];
  for (
    let index = haystack.indexOf(lower);
    index >= 0;
    index = haystack.indexOf(lower, index + Math.max(1, lower.length))
  ) {
    found.push({ start: index, end: index + lower.length });
    if (found.length >= 100) break;
  }
  return found;
}

function renderText(text: string, highlights: readonly Highlight[]) {
  const ranges = [...highlights]
    .filter((range) => range.end > range.start && range.start >= 0 && range.end <= text.length)
    .sort(
      (a, b) =>
        a.start - b.start || highlightPriority[b.kind] - highlightPriority[a.kind] || b.end - a.end,
    )
    .reduce<Highlight[]>((accepted, range) => {
      const previous = accepted.at(-1);
      if (previous && range.start < previous.end) return accepted;
      accepted.push(range);
      return accepted;
    }, []);
  const parts: ReactNode[] = [];
  let cursor = 0;
  ranges.forEach((range) => {
    if (range.start > cursor) parts.push(text.slice(cursor, range.start));
    parts.push(
      <mark
        key={`${range.kind}-${range.start}-${range.end}`}
        className={`v3-xml-mark v3-xml-mark-${range.kind}`}
        data-active-location={range.kind === "location" ? "true" : undefined}
      >
        {text.slice(range.start, range.end)}
      </mark>,
    );
    cursor = range.end;
  });
  if (cursor < text.length) parts.push(text.slice(cursor));
  return parts;
}

type LineRange = Readonly<{ text: string; start: number; end: number }>;
type LineDiff = Readonly<{ current: ReadonlySet<number>; target: ReadonlySet<number> }>;
const emptyLineDiff: LineDiff = Object.freeze({
  current: new Set<number>(),
  target: new Set<number>(),
});
const maxExactDiffCells = 250_000;

function lineRanges(text: string): readonly LineRange[] {
  if (!text.length) return [{ text: "", start: 0, end: 0 }];
  const ranges: LineRange[] = [];
  let start = 0;
  for (let index = 0; index < text.length; index += 1) {
    if (text[index] === "\n") {
      ranges.push({ text: text.slice(start, index + 1), start, end: index + 1 });
      start = index + 1;
    }
  }
  if (start < text.length) ranges.push({ text: text.slice(start), start, end: text.length });
  return ranges;
}

function positionalLineDiff(current: readonly string[], target: readonly string[]): LineDiff {
  const currentChanged = new Set<number>();
  const targetChanged = new Set<number>();
  const length = Math.max(current.length, target.length);
  for (let index = 0; index < length; index += 1) {
    if (current[index] !== target[index]) {
      if (index < current.length) currentChanged.add(index);
      if (index < target.length) targetChanged.add(index);
    }
  }
  return { current: currentChanged, target: targetChanged };
}

function computeLineDiff(currentText?: string, targetText?: string): LineDiff {
  if (currentText === undefined || targetText === undefined) return emptyLineDiff;
  const current = lineRanges(currentText).map((line) => line.text);
  const target = lineRanges(targetText).map((line) => line.text);
  if (current.length * target.length > maxExactDiffCells)
    return positionalLineDiff(current, target);
  const matrix: number[][] = Array.from({ length: current.length + 1 }, () =>
    Array.from({ length: target.length + 1 }, () => 0),
  );
  for (let i = current.length - 1; i >= 0; i -= 1) {
    for (let j = target.length - 1; j >= 0; j -= 1) {
      matrix[i][j] =
        current[i] === target[j]
          ? matrix[i + 1][j + 1] + 1
          : Math.max(matrix[i + 1][j], matrix[i][j + 1]);
    }
  }
  const currentChanged = new Set<number>();
  const targetChanged = new Set<number>();
  let i = 0;
  let j = 0;
  while (i < current.length && j < target.length) {
    if (current[i] === target[j]) {
      i += 1;
      j += 1;
    } else if (matrix[i + 1][j] >= matrix[i][j + 1]) {
      currentChanged.add(i);
      i += 1;
    } else {
      targetChanged.add(j);
      j += 1;
    }
  }
  while (i < current.length) currentChanged.add(i++);
  while (j < target.length) targetChanged.add(j++);
  return { current: currentChanged, target: targetChanged };
}

function lineIndexesForSpan(ranges: readonly LineRange[], start: number, end: number): number[] {
  if (end <= start) return [];
  const indexes: number[] = [];
  ranges.forEach((line, index) => {
    if (start < line.end && end > line.start) indexes.push(index);
  });
  return indexes;
}

function lineIndexesForToken(text: string, token: string): number[] {
  if (!token) return [];
  const ranges = lineRanges(text);
  const indexes = new Set<number>();
  for (
    let index = text.indexOf(token);
    index >= 0;
    index = text.indexOf(token, index + Math.max(1, token.length))
  ) {
    lineIndexesForSpan(ranges, index, index + token.length).forEach((line) => {
      indexes.add(line);
    });
    if (indexes.size >= 100) break;
  }
  return [...indexes];
}

function changedBindingLineIndexes(
  text: string,
  mode: ComparisonState["mode"],
  side: Side,
  bindingRail: readonly BindingRailItem[],
): ReadonlySet<number> {
  if (mode !== "raw" && mode !== "placeholders") return new Set<number>();
  const ranges = lineRanges(text);
  const indexes = new Set<number>();
  bindingRail
    .filter((item) => item.change !== "unchanged")
    .forEach((item) => {
      if (mode === "placeholders") {
        lineIndexesForToken(text, item.token).forEach((line) => {
          indexes.add(line);
        });
        return;
      }
      const locations =
        side === "current" ? item.currentDocumentLocations : item.targetDocumentLocations;
      locations.forEach((location) => {
        lineIndexesForSpan(ranges, location.span.start, location.span.end).forEach((line) => {
          indexes.add(line);
        });
      });
    });
  return indexes;
}

function combinedLineIndexes(
  first: ReadonlySet<number>,
  second: ReadonlySet<number>,
): ReadonlySet<number> {
  if (!first.size) return second;
  if (!second.size) return first;
  return new Set([...first, ...second]);
}

function renderLines(
  text: string,
  highlights: readonly Highlight[],
  changedLines: ReadonlySet<number>,
  side: Side,
) {
  return lineRanges(text).map((line, index) => {
    const lineHighlights = highlights
      .map((highlight) => ({
        start: Math.max(highlight.start, line.start) - line.start,
        end: Math.min(highlight.end, line.end) - line.start,
        kind: highlight.kind,
      }))
      .filter((highlight) => highlight.end > highlight.start);
    const changed = changedLines.has(index);
    return (
      <span
        key={`${side}-${line.start}-${line.end}-${line.text.length}`}
        className={`v3-xml-line${changed ? ` v3-xml-line-${side}-diff` : ""}`}
        data-diff-side={changed ? side : undefined}
      >
        <span className="v3-xml-line-number" aria-hidden="true">
          {index + 1}
        </span>
        <span className="v3-xml-line-text">{renderText(line.text, lineHighlights)}</span>
      </span>
    );
  });
}

export function V3DocumentComparison({ state }: { state: ComparisonState }) {
  const { plan } = state;
  const documents = state.inventory?.documents;
  const [mapping, setMapping] = useState("");
  const [textQuery, setTextQuery] = useState("");
  const [activeTextHit, setActiveTextHit] = useState(0);
  const [activeLocation, setActiveLocation] = useState(0);
  const currentPre = useRef<HTMLElement | null>(null);
  const targetPre = useRef<HTMLElement | null>(null);
  const syncing = useRef(false);
  const selectedMapping =
    state.bindingRail.find(
      (item) => `${JSON.stringify(item.entity)}:${item.fieldId}` === mapping,
    ) ?? state.bindingRail[0];
  const canRead = Boolean(state.selected && state.consent && !state.reading);
  const diffLines = useMemo(
    () => computeLineDiff(state.current?.text, state.target?.text),
    [state.current?.text, state.target?.text],
  );
  const bindingDiffLines = useMemo(
    () => ({
      current: state.current
        ? changedBindingLineIndexes(state.current.text, state.mode, "current", state.bindingRail)
        : new Set<number>(),
      target: state.target
        ? changedBindingLineIndexes(state.target.text, state.mode, "target", state.bindingRail)
        : new Set<number>(),
    }),
    [state.bindingRail, state.current, state.mode, state.target],
  );
  const textHits = useMemo<TextHit[]>(() => {
    const hits: TextHit[] = [];
    if (state.current)
      hits.push(
        ...matches(state.current.text, textQuery).map((hit) => ({
          side: "current" as const,
          ...hit,
        })),
      );
    if (state.target)
      hits.push(
        ...matches(state.target.text, textQuery).map((hit) => ({
          side: "target" as const,
          ...hit,
        })),
      );
    return hits;
  }, [state.current, state.target, textQuery]);
  const locationHits = useMemo<LocationHit[]>(() => {
    if (!selectedMapping) return [];
    return [
      ...selectedMapping.currentDocumentLocations.map((location, index) => ({
        side: "current" as const,
        index,
        start: location.span.start,
        end: location.span.end,
        label: `Current ${index + 1}`,
        role: location.role,
        attribute: location.attribute.localName,
        elementIndex: location.elementIndex,
        declarationId: location.declarationId,
      })),
      ...selectedMapping.targetDocumentLocations.map((location, index) => ({
        side: "target" as const,
        index,
        start: location.span.start,
        end: location.span.end,
        label: `Target ${index + 1}`,
        role: location.role,
        attribute: location.attribute.localName,
        elementIndex: location.elementIndex,
        declarationId: location.declarationId,
      })),
    ];
  }, [selectedMapping]);
  const currentTextHit = textHits[Math.min(activeTextHit, Math.max(0, textHits.length - 1))];
  const currentLocationHit =
    locationHits[Math.min(activeLocation, Math.max(0, locationHits.length - 1))];
  const textResetKey = `${textQuery}\u0000${state.current?.documentId ?? ""}\u0000${state.target?.documentId ?? ""}\u0000${state.current?.mode ?? ""}\u0000${state.target?.mode ?? ""}`;
  const selectedMappingKey = selectedMapping
    ? `${JSON.stringify(selectedMapping.entity)}:${selectedMapping.fieldId}`
    : "";
  useEffect(() => {
    if (textResetKey.length >= 0) setActiveTextHit(0);
  }, [textResetKey]);
  useEffect(() => {
    if (selectedMappingKey.length >= 0) setActiveLocation(0);
  }, [selectedMappingKey]);
  useEffect(() => {
    const side = currentLocationHit?.side ?? currentTextHit?.side;
    const pane =
      side === "current" ? currentPre.current : side === "target" ? targetPre.current : null;
    const mark = pane?.querySelector<HTMLElement>(
      currentLocationHit ? ".v3-xml-mark-location" : ".v3-xml-mark-active-search",
    );
    if (typeof mark?.scrollIntoView === "function") {
      mark.scrollIntoView({ block: "center", inline: "nearest" });
    }
  }, [currentTextHit, currentLocationHit]);
  function sync(side: Side) {
    if (syncing.current) return;
    const source = side === "current" ? currentPre.current : targetPre.current;
    const target = side === "current" ? targetPre.current : currentPre.current;
    if (!source || !target) return;
    syncing.current = true;
    const yRange = Math.max(1, source.scrollHeight - source.clientHeight);
    const xRange = Math.max(1, source.scrollWidth - source.clientWidth);
    target.scrollTop =
      (source.scrollTop / yRange) * Math.max(1, target.scrollHeight - target.clientHeight);
    target.scrollLeft =
      (source.scrollLeft / xRange) * Math.max(1, target.scrollWidth - target.clientWidth);
    window.setTimeout(() => {
      syncing.current = false;
    }, 0);
  }
  function paneHighlights(side: Side, text: string): readonly Highlight[] {
    const ranges: Highlight[] = [];
    matches(text, textQuery).forEach((hit) => {
      ranges.push({ ...hit, kind: "search" });
    });
    if (currentTextHit?.side === side) ranges.push({ ...currentTextHit, kind: "active-search" });
    const document = side === "current" ? state.current : state.target;
    if (state.mode === "raw" && document?.exact && currentLocationHit?.side === side)
      ranges.push({
        start: currentLocationHit.start,
        end: currentLocationHit.end,
        kind: "location",
      });
    return ranges;
  }
  function moveTextHit(direction: -1 | 1) {
    if (!textHits.length) return;
    setActiveTextHit((index) => (index + direction + textHits.length) % textHits.length);
  }
  function moveLocation(direction: -1 | 1) {
    if (!locationHits.length) return;
    setActiveLocation((index) => (index + direction + locationHits.length) % locationHits.length);
  }
  return (
    <section className="v3-plan-documents" aria-labelledby="v3-documents-heading">
      <div className="v3-comparison-heading">
        <div>
          <h2 id="v3-documents-heading">Document comparison</h2>
          <p>
            Review XML / CLOB records across the plan. Filtering does not change validation scope.
          </p>
        </div>
        {documents && (
          <p>
            {documents.length} documents · {documents.filter((d) => d.changed === true).length}{" "}
            changed · {documents.filter((d) => d.changed === null).length} unknown
          </p>
        )}
      </div>
      <div className="v3-comparison-workbench">
        <V3DocumentNavigator
          documents={documents}
          selected={state.selected}
          select={state.select}
        />
        <div className="v3-comparison-detail">
          <div className="v3-comparison-toolbar">
            <div className="v3-comparison-identity">
              <h3>{state.selected || "No document selected"}</h3>
              <p>
                {plan
                  ? `Plan revision ${plan.revision} · read-only comparison`
                  : "No observation loaded"}
              </p>
            </div>
            <fieldset>
              <legend className="sr-only">View</legend>
              <div className="v3-plan-modes">
                {(["raw", "placeholders", "formatted"] as const).map((mode) => (
                  <button
                    type="button"
                    key={mode}
                    disabled={!state.selected}
                    aria-pressed={state.mode === mode}
                    onClick={() => state.setMode(mode)}
                  >
                    {mode === "raw"
                      ? "Raw"
                      : mode === "placeholders"
                        ? "Placeholders"
                        : "Formatted"}
                  </button>
                ))}
              </div>
            </fieldset>
          </div>
          <p className="v3-comparison-mode-note">
            {state.mode === "raw"
              ? "Raw preserves exact characters. Location highlights use verified raw spans."
              : state.mode === "placeholders"
                ? "Mapping projection only. Concrete current and target values remain visible in the binding rail."
                : "Formatted is a display projection only. Exported XML is unchanged by this view."}
          </p>
          <div className="v3-comparison-disclosure">
            <label className="v3-plan-consent">
              <input
                type="checkbox"
                checked={state.consent}
                disabled={!state.selected}
                onChange={(e) => state.setConsent(e.target.checked)}
              />
              I understand complete documents may include unmapped or sensitive values.
            </label>
            <button type="button" disabled={!canRead} onClick={() => void state.load()}>
              Load document comparison
            </button>
          </div>
          {(state.current || state.target) && (
            <section className="v3-text-find" aria-label="Find in loaded XML">
              <label>
                Find in loaded XML
                <input
                  type="search"
                  value={textQuery}
                  onChange={(event) => setTextQuery(event.target.value)}
                  placeholder="Search loaded current and target text…"
                />
              </label>
              <div className="v3-text-find-actions">
                <button type="button" disabled={!textHits.length} onClick={() => moveTextHit(-1)}>
                  Previous match
                </button>
                <button type="button" disabled={!textHits.length} onClick={() => moveTextHit(1)}>
                  Next match
                </button>
              </div>
              <p role="status">
                {textQuery.trim()
                  ? `${textHits.length ? Math.min(activeTextHit + 1, textHits.length) : 0} of ${textHits.length} visible text matches`
                  : "Search runs only inside the loaded document text."}
              </p>
            </section>
          )}
          {(!plan || !documents) && (
            <p>Resume a plan with an observation before loading documents.</p>
          )}
          {state.reading && (
            <p role="status">
              {state.current || state.target
                ? "Updating the loaded comparison and checking plan context…"
                : "Loading the selected revision and checking plan context…"}
            </p>
          )}
          <div className="v3-plan-panes">
            {(
              [
                ["Current", "current", state.current, currentPre],
                ["Target", "target", state.target, targetPre],
              ] as const
            ).map(([label, side, value, paneRef]) => (
              <section
                key={label}
                aria-label={`${label} XML`}
                aria-busy={state.reading ? "true" : undefined}
              >
                <h3>{label}</h3>
                {value ? (
                  <>
                    <p>
                      {value.documentId} · revision {value.revision} · {value.mode}
                    </p>
                    <p>
                      {value.exact ? "Exact characters" : "Display projection only"} ·{" "}
                      {value.redacted ? "Redacted" : "Concrete document"}
                    </p>
                    {value.unmappedConcreteMayRemain && <p>Unmapped concrete values may remain.</p>}
                    {value.omissions.length > 0 && <p>Omissions: {value.omissions.join(", ")}</p>}
                    <section
                      ref={paneRef}
                      className="v3-xml-scroll-region"
                      aria-label={`${label} XML text`}
                      data-side={side}
                      onScroll={() => sync(side)}
                    >
                      <pre>
                        {renderLines(
                          value.text,
                          paneHighlights(side, value.text),
                          combinedLineIndexes(diffLines[side], bindingDiffLines[side]),
                          side,
                        )}
                      </pre>
                    </section>
                  </>
                ) : (
                  <div className="v3-plan-empty-document">
                    <svg
                      aria-hidden="true"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.5"
                    >
                      <path d="M5 2h10l5 5v15H5zM15 2v6h5M8 12h9m-9 4h9" />
                    </svg>
                    <p>
                      {!plan
                        ? label === "Current"
                          ? "No observed document available."
                          : "No target document available."
                        : label === "Target" && !plan.targetComplete
                          ? "Target unavailable · not evidence of unchanged content."
                          : "Select a document, confirm disclosure and load."}
                    </p>
                  </div>
                )}
              </section>
            ))}
          </div>
          {(state.mode === "placeholders" || state.mode === "raw") && state.current && (
            <section className="v3-binding-rail" aria-labelledby="v3-binding-rail-heading">
              <h3 id="v3-binding-rail-heading">Binding rail</h3>
              <p>
                {state.mode === "raw"
                  ? "Mapped locations use verified raw spans. Current and target values remain visible here."
                  : "Placeholder tokens are labels only. Current and target values remain visible here for the selected document."}
              </p>
              {state.bindingRail.length > 0 && (
                <label>
                  Selected mapping
                  <select
                    value={
                      selectedMapping
                        ? `${JSON.stringify(selectedMapping.entity)}:${selectedMapping.fieldId}`
                        : ""
                    }
                    onChange={(event) => setMapping(event.target.value)}
                  >
                    {state.bindingRail.map((item) => (
                      <option
                        key={`${JSON.stringify(item.entity)}:${item.fieldId}`}
                        value={`${JSON.stringify(item.entity)}:${item.fieldId}`}
                      >
                        {item.token} · {item.change}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {state.bindingRail.length === 0 ? (
                <p>No mapped placeholder locations were returned for this document.</p>
              ) : (
                <div className="v3-binding-rail-list">
                  {(selectedMapping ? [selectedMapping] : []).map((item) => (
                    <article key={`${JSON.stringify(item.entity)}:${item.fieldId}`}>
                      <div>
                        <strong>{item.fieldId}</strong>
                        <span>{item.typeId}</span>
                      </div>
                      <code>{item.token}</code>
                      <dl>
                        <div>
                          <dt>Current value</dt>
                          <dd>{item.current}</dd>
                        </div>
                        <div>
                          <dt>Target value</dt>
                          <dd>{item.target}</dd>
                        </div>
                        <div>
                          <dt>Status</dt>
                          <dd>{item.change}</dd>
                        </div>
                        <div>
                          <dt>Mapped locations</dt>
                          <dd>
                            This document: Current {item.currentLocations} · Target{" "}
                            {item.targetLocations}. Whole plan: Current {item.currentTotalLocations}{" "}
                            · Target {item.targetTotalLocations}.
                          </dd>
                        </div>
                      </dl>
                      <fieldset className="v3-location-navigation">
                        <legend>Selected mapping locations</legend>
                        <button
                          type="button"
                          disabled={!locationHits.length}
                          onClick={() => moveLocation(-1)}
                        >
                          Previous mapped location
                        </button>
                        <button
                          type="button"
                          disabled={!locationHits.length}
                          onClick={() => moveLocation(1)}
                        >
                          Next mapped location
                        </button>
                        <p role="status">
                          {locationHits.length
                            ? `${Math.min(activeLocation + 1, locationHits.length)} of ${locationHits.length} mapped locations in this document`
                            : "No mapped locations in this document."}
                        </p>
                      </fieldset>
                      {currentLocationHit && (
                        <dl className="v3-location-detail" aria-label="Active mapped location">
                          <div>
                            <dt>Side</dt>
                            <dd>{currentLocationHit.label}</dd>
                          </div>
                          <div>
                            <dt>Role</dt>
                            <dd>{currentLocationHit.role}</dd>
                          </div>
                          <div>
                            <dt>Element</dt>
                            <dd>{currentLocationHit.elementIndex}</dd>
                          </div>
                          <div>
                            <dt>Attribute</dt>
                            <dd>{currentLocationHit.attribute}</dd>
                          </div>
                          <div>
                            <dt>Span</dt>
                            <dd>
                              {currentLocationHit.start}–{currentLocationHit.end}
                            </dd>
                          </div>
                          <div>
                            <dt>Declaration</dt>
                            <dd>{currentLocationHit.declarationId}</dd>
                          </div>
                        </dl>
                      )}
                      {state.mode !== "raw" && locationHits.length > 0 && (
                        <p className="v3-location-note">
                          Switch to Raw and reload this document to highlight exact source spans.
                        </p>
                      )}
                    </article>
                  ))}
                </div>
              )}
            </section>
          )}
        </div>
      </div>
    </section>
  );
}
