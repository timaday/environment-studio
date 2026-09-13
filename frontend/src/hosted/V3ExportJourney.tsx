import { useEffect, useMemo, useState } from "react";
import { failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Api } from "../api/hostedV3";
import type { PlanSummary, ValidationSummary } from "../api/hostedV3Types";
import { useV3Validation } from "./useV3Validation";
import { WorkflowPlanContext } from "./WorkflowPlanContext";
import "./V3ExportJourney.css";

type DownloadReceipt = Readonly<{ filename: string; qualified: false }>;

function checkLabel(value: string): string {
  return value.replaceAll("_", " ");
}
function ready(summary: ValidationSummary | null): boolean {
  return Boolean(
    summary?.targetComplete && summary.checks.every((check) => check.outcome === "PASS"),
  );
}
function blocker(summary: ValidationSummary | null): string {
  if (!summary) return "Check readiness before download.";
  const blocked = summary.checks.filter((check) => check.outcome !== "PASS");
  if (blocked.length === 0) return "Validation passed for this exact revision.";
  return `${blocked.length} required check${blocked.length === 1 ? "" : "s"} block package generation.`;
}
function download(bytes: ArrayBuffer, filename: string) {
  const url = URL.createObjectURL(new Blob([bytes], { type: "application/zip" }));
  try {
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.rel = "noopener";
    document.body.append(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}

export function V3ExportJourney({
  api,
  plan,
  active,
  back,
  reviewDocuments,
}: {
  api: HostedApi;
  plan: PlanSummary | null;
  active: boolean;
  back: () => void;
  reviewDocuments: () => void;
}) {
  const validation = useV3Validation(api, plan, active);
  const client = useMemo(() => new HostedV3Api(api), [api]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [receipt, setReceipt] = useState<DownloadReceipt | null>(null);
  const eligible = Boolean(plan?.inspectionValid && plan.observedDestination?.evidenceValid);
  const canGenerate = eligible && ready(validation.summary) && !busy && !validation.busy;
  const context = JSON.stringify(plan);
  useEffect(() => {
    void context;
    void active;
    setBusy(false);
    setError("");
    setReceipt(null);
  }, [context, active]);
  async function generate() {
    if (!plan || !validation.summary || !canGenerate) return;
    setBusy(true);
    setError("");
    setReceipt(null);
    try {
      const result = await client.guardedPackageCandidate(plan.planId, {
        revision: validation.summary.revision,
        inputFingerprint: validation.summary.inputFingerprint,
      });
      download(result.bytes, result.filename);
      setReceipt({ filename: result.filename, qualified: result.qualified });
    } catch (failure) {
      setError(failureMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="capture-workspace export-workspace" aria-label="Export workspace">
      <WorkflowPlanContext plan={plan} label="Export plan context" />
      <header className="export-header">
        <div>
          <button type="button" onClick={back}>
            Back to plan
          </button>
          <h1>Export guarded package</h1>
          <p>Generate a guarded ZIP candidate for external review.</p>
        </div>
        <button type="button" onClick={reviewDocuments}>
          Review documents
        </button>
      </header>
      <section className="export-status-grid" aria-label="Export readiness">
        <article className={plan?.targetComplete ? "ok" : "warn"}>
          <strong>Target {plan?.targetComplete ? "complete" : "incomplete"}</strong>
          <span>{plan ? "Plan summary" : "No active plan"}</span>
        </article>
        <article className={ready(validation.summary) ? "ok" : "warn"}>
          <strong>
            {ready(validation.summary) ? "Validation passed" : "Validation unresolved"}
          </strong>
          <span>{validation.summary ? "Latest check" : "Not checked"}</span>
        </article>
        <article className={receipt ? "ok" : "neutral"}>
          <strong>{receipt ? "Package received" : "Package not generated"}</strong>
          <span>{receipt ? "Browser download started." : "No candidate yet."}</span>
        </article>
      </section>
      {!eligible && (
        <section className="export-panel" role="status">
          <h2>Inspection required</h2>
          <p>Resume the plan and complete target observation before export.</p>
        </section>
      )}
      <section className="export-panel export-candidate" aria-label="Package candidate">
        <h2>Package candidate</h2>
        <p>Server checks revision, fingerprint, destination and policy before streaming bytes.</p>
        <div className="export-actions">
          <button
            type="button"
            className="primary"
            onClick={() => void validation.validate()}
            disabled={!eligible || validation.busy || busy}
          >
            Check readiness
          </button>
          <button type="button" onClick={() => void generate()} disabled={!canGenerate}>
            Download package candidate
          </button>
        </div>
        <p>{busy ? "Requesting package candidate…" : blocker(validation.summary)}</p>
        {error && <p role="alert">{error}</p>}
        {receipt && (
          <p role="status">
            Downloaded {receipt.filename}. Unqualified candidate; review and run it outside
            Environment Studio.
          </p>
        )}
      </section>
      {validation.summary && (
        <section className="export-panel" aria-label="Validation checks">
          <h2>Required checks</h2>
          <div className="export-checks">
            {validation.summary.checks.map((check) => (
              <article key={check.check} className={check.outcome === "PASS" ? "ok" : "warn"}>
                <strong>{checkLabel(check.check)}</strong>
                <span>{check.outcome}</span>
              </article>
            ))}
          </div>
        </section>
      )}
      <details className="export-panel">
        <summary>External execution requirements</summary>
        <p>Use the verified guarded supervisor after package review.</p>
        <code>
          environment-studio-guarded apply --package ABSOLUTE_PATH --sha256 REVIEWED_ARCHIVE_SHA256
          --configuration APPROVED_CLIENT_JSON --destination APPROVED_DESTINATION_ID
        </code>
      </details>
    </section>
  );
}
