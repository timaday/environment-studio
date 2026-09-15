import { useCallback, useEffect, useRef, useState } from "react";
import { ApiFailure, definitiveRefusal, failureMessage, type HostedApi } from "../api/hosted";
import {
  ack as decodeAck,
  operation as decodeOperation,
  reserveInspection,
} from "../api/hostedV3Decoding";
import type { Operation, PlanSummary, ReserveInspection } from "../api/hostedV3Types";

type PendingReservation = Readonly<{
  planId: string;
  command: ReserveInspection;
}>;

const terminal = (operation: Operation | null) =>
  operation !== null && ["succeeded", "refused", "cancelled", "expired"].includes(operation.phase);
const settled = (operation: Operation | null) =>
  terminal(operation) && operation?.cleanup === "complete";
const phaseLabel = (phase: string) =>
  phase === "reserved"
    ? "Reserved"
    : phase === "running"
      ? "Reading"
      : phase === "succeeded"
        ? "Inspection complete"
        : phase === "refused"
          ? "Inspection did not complete"
          : phase === "cancelled"
            ? "Inspection cancelled"
            : phase === "expired"
              ? "Inspection expired"
              : "Inspection status";
const outcomeMessage = (operation: Operation) => {
  if (operation.phase === "succeeded")
    return "The current PostgreSQL configuration was captured for this plan revision.";
  if (operation.phase === "cancelled")
    return "The operation was cancelled. Start another inspection when you are ready.";
  if (operation.phase === "expired")
    return "The reserved operation expired before completion. Start another inspection and send fresh credentials.";
  if (operation.code === "INVALID_CREDENTIALS")
    return "The database rejected the supplied credentials. Start another inspection and enter the credentials again.";
  if (operation.code === "DESTINATION_DENIED" || operation.code === "INVALID_DESTINATION")
    return "The destination did not match the allowed PostgreSQL target. Check the connection target before retrying.";
  if (operation.code === "STORAGE_UNSUPPORTED")
    return "The connection reached PostgreSQL, but the selected definition points to a table or XML storage shape outside the PostgreSQL 16.11 text pilot. Check the table, key column, XML text column, unique key and table safety restrictions.";
  if (operation.code === "OBSERVATION_REFUSED")
    return "The read-only observation was refused before current configuration evidence could be captured.";
  if (operation.code === "RESOURCE_LIMIT" || operation.code === "CAPACITY")
    return "The inspection exceeded an application safety limit. Reduce scope or wait for capacity before retrying.";
  return "No current configuration evidence was installed. Review the failure code and start another inspection when ready.";
};
const cleanupMessage = (operation: Operation) =>
  operation.cleanup === "complete"
    ? "Credentials were discarded and operation cleanup is confirmed."
    : operation.cleanup === "in-progress"
      ? "Cleanup is still in progress. Do not start another inspection until the server reports completion."
      : "Cleanup was not confirmed. Inspection capacity remains quarantined until server cleanup is confirmed.";

export function V3Inspection({
  api,
  plan,
  enabled,
  refresh,
}: {
  api: HostedApi;
  plan: PlanSummary;
  enabled: boolean;
  refresh: () => Promise<void> | void;
}) {
  const [operation, setOperation] = useState<Operation | null>(null);
  const [reserved, setReserved] = useState<string | null>(plan.activeOperationId ?? null);
  const [sent, setSent] = useState(Boolean(plan.activeOperationId));
  const [busy, setBusy] = useState(false);
  const [discardConfirmed, setDiscardConfirmed] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState<PendingReservation | null>(null);
  const [pollVersion, setPollVersion] = useState(0);
  const [checking, setChecking] = useState(false);
  const alive = useRef(true);
  const sending = useRef(false);
  const reserving = useRef(false);
  const activeOperation = useRef<string | null>(plan.activeOperationId ?? null);
  const applyStatus = useCallback(
    async (status: Operation, operationId: string) => {
      if (!alive.current || activeOperation.current !== operationId) return;
      if (status.planId !== plan.planId) throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
      setOperation((previous) => (settled(previous) && !settled(status) ? previous : status));
      setError("");
      if (terminal(status)) await refresh();
    },
    [plan.planId, refresh],
  );

  useEffect(() => {
    if (
      plan.activeOperationId &&
      plan.activeOperationId !== activeOperation.current &&
      !reserving.current
    ) {
      activeOperation.current = plan.activeOperationId;
      setReserved(plan.activeOperationId);
      setOperation(null);
      setSent(true);
      sending.current = true;
    }
  }, [plan.activeOperationId]);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    if (!reserved || !sent || terminal(operation)) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      let finished = false;
      if (active) setChecking(true);
      try {
        const status = decodeOperation(
          await api.get<unknown>(`/api/v3/operations/${encodeURIComponent(reserved)}`),
        );
        if (!active || activeOperation.current !== reserved) return;
        await applyStatus(status, reserved);
        finished = terminal(status);
      } catch (failure) {
        if (active) setError(failureMessage(failure));
      } finally {
        if (active) {
          setChecking(false);
          if (!finished) timer = setTimeout(() => void poll(), 1000);
        }
      }
    };
    timer = setTimeout(() => void poll(), pollVersion > 0 ? 0 : 1000);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [api, reserved, sent, operation, pollVersion, applyStatus]);

  async function reserve(command: ReserveInspection) {
    if (reserving.current || !enabled) return;
    reserving.current = true;
    setBusy(true);
    setError("");
    setPending({ planId: plan.planId, command });
    try {
      const acknowledged = decodeAck(
        await api.post<unknown>(
          `/api/v3/plans/${encodeURIComponent(plan.planId)}/inspections`,
          command,
        ),
      );
      if (acknowledged.planId !== plan.planId || !acknowledged.operationId)
        throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
      if (alive.current) {
        activeOperation.current = acknowledged.operationId;
        setReserved(acknowledged.operationId);
        setOperation(null);
        setPending(null);
        setSent(false);
        sending.current = false;
      }
    } catch (failure) {
      if (alive.current) {
        setError(failureMessage(failure));
        if (definitiveRefusal(failure)) setPending(null);
      }
    } finally {
      reserving.current = false;
      if (alive.current) setBusy(false);
    }
  }

  async function submit(form: HTMLFormElement) {
    if (!reserved || sending.current) return;
    sending.current = true;
    const username = (form.elements.namedItem("username") as HTMLInputElement).value;
    const password = (form.elements.namedItem("password") as HTMLInputElement).value;
    form.reset();
    setSent(true);
    setBusy(true);
    setError("");
    try {
      const status = decodeOperation(await api.credentialsV3(reserved, username, password));
      if (alive.current && activeOperation.current === reserved) {
        if (status.planId !== plan.planId) throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
        setOperation((previous) => (settled(previous) && !settled(status) ? previous : status));
        await refresh();
      }
    } catch (failure) {
      if (alive.current && activeOperation.current === reserved)
        setError(
          `${failureMessage(failure)}. Credentials will not be retried. Check operation status or cancel.`,
        );
    } finally {
      if (alive.current && activeOperation.current === reserved) setBusy(false);
    }
  }

  async function cancel() {
    if (!reserved) return;
    sending.current = true;
    setSent(true);
    try {
      const status = decodeOperation(
        await api.post<unknown>(`/api/v3/operations/${encodeURIComponent(reserved)}/cancel`, {}),
      );
      if (alive.current && activeOperation.current === reserved) {
        if (status.planId !== plan.planId) throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
        setOperation((previous) => (settled(previous) && !settled(status) ? previous : status));
        await refresh();
      }
    } catch (failure) {
      if (alive.current && activeOperation.current === reserved) setError(failureMessage(failure));
    }
  }

  async function checkStatusNow() {
    if (!reserved || checking) return;
    setChecking(true);
    try {
      const status = decodeOperation(
        await api.get<unknown>(`/api/v3/operations/${encodeURIComponent(reserved)}`),
      );
      await applyStatus(status, reserved);
    } catch (failure) {
      if (alive.current && activeOperation.current === reserved) setError(failureMessage(failure));
    } finally {
      if (alive.current && activeOperation.current === reserved) setChecking(false);
    }
  }

  return (
    <section className="v3-inspection-panel" aria-labelledby="v3-inspection-heading">
      <div>
        <h2 id="v3-inspection-heading">Inspect current PostgreSQL</h2>
        <p>
          The published definition and selected destination are read together. Enter credentials
          only for this read-only operation; they are cleared immediately after submission.
        </p>
      </div>
      {!enabled && <p id="v3-inspection-disabled">Inspection is unavailable in this deployment.</p>}
      {!reserved && (
        <label className="v3-plan-consent">
          <input
            type="checkbox"
            checked={discardConfirmed}
            disabled={!enabled || busy || pending !== null}
            onChange={(event) => setDiscardConfirmed(event.target.checked)}
          />
          I understand this inspection may replace current observation evidence and discard target
          changes.
        </label>
      )}
      {!reserved && (
        <button
          type="button"
          className="primary"
          disabled={!enabled || busy || pending !== null || !discardConfirmed}
          aria-describedby={!enabled ? "v3-inspection-disabled" : undefined}
          onClick={() => {
            if (busy || pending || !discardConfirmed) return;
            void reserve(
              reserveInspection({
                expectedRevision: plan.revision,
                requestId: crypto.randomUUID(),
                discardDraftOnSuccess: true,
              }),
            );
          }}
        >
          Reserve read-only inspection
        </button>
      )}
      {pending && !busy && (
        <section className="v3-plan-notice" aria-label="Unconfirmed inspection reservation">
          <p>
            The reservation may have completed. Retry the same reservation before entering
            credentials.
          </p>
          <button type="button" disabled={!enabled} onClick={() => void reserve(pending.command)}>
            Retry original reservation
          </button>
        </section>
      )}
      {reserved && (
        <p>
          Operation <code>{reserved}</code>
        </p>
      )}
      {reserved && !sent && (
        <form
          className="v3-inspection-credentials"
          autoComplete="off"
          onSubmit={(event) => {
            event.preventDefault();
            void submit(event.currentTarget);
          }}
        >
          <label>
            Database username
            <input name="username" required maxLength={256} autoComplete="off" />
          </label>
          <label>
            Database password
            <input
              name="password"
              type="password"
              required
              maxLength={2048}
              autoComplete="new-password"
            />
          </label>
          <button type="submit" className="primary">
            Send credentials once
          </button>
        </form>
      )}
      {reserved && !terminal(operation) && (
        <button type="button" onClick={cancel}>
          Cancel operation
        </button>
      )}
      {reserved && sent && !settled(operation) && (
        <button
          type="button"
          disabled={checking}
          onClick={() => {
            if (terminal(operation)) void checkStatusNow();
            else setPollVersion((value) => value + 1);
          }}
        >
          {terminal(operation) ? "Check cleanup status" : "Check operation status"}
        </button>
      )}
      {terminal(operation) && operation?.cleanup === "complete" && !busy && (
        <button
          type="button"
          disabled={!enabled}
          onClick={() => {
            activeOperation.current = null;
            setReserved(null);
            setOperation(null);
            setSent(false);
            sending.current = false;
            setDiscardConfirmed(false);
            setError("");
          }}
        >
          Start another inspection
        </button>
      )}
      {busy && sent && <p role="status">Credentials sent once · awaiting operation status…</p>}
      {operation && (
        <section
          className={`v3-inspection-outcome v3-inspection-outcome-${operation.phase}`}
          aria-label="Inspection outcome"
          role={
            operation.cleanup === "inconclusive" || operation.phase === "refused"
              ? "alert"
              : "status"
          }
        >
          <div>
            <strong className="v3-inspection-outcome-title">{phaseLabel(operation.phase)}</strong>
            <p>{outcomeMessage(operation)}</p>
            <p>{cleanupMessage(operation)}</p>
          </div>
          <dl>
            <div>
              <dt>Status code</dt>
              <dd>{operation.code}</dd>
            </div>
            {operation.installedRevision && (
              <div>
                <dt>Installed revision</dt>
                <dd>{operation.installedRevision}</dd>
              </div>
            )}
          </dl>
        </section>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
