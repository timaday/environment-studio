import { useEffect, useRef, useState } from "react";
import {
  type Ack,
  ApiFailure,
  checkedAck,
  definitiveRefusal,
  failureMessage,
  type HostedApi,
  type Operation,
  type Plan,
} from "../api/hosted";

type Reservation = Readonly<{
  expectedRevision: string;
  requestId: string;
  discardDraftOnSuccess: true;
}>;
const terminal = (operation: Operation | null) =>
  operation !== null && ["succeeded", "refused", "cancelled", "expired"].includes(operation.phase);
const settled = (operation: Operation | null) =>
  terminal(operation) && operation?.cleanup === "complete";
export function Inspection({
  api,
  plan,
  enabled,
  refresh,
  visible = true,
}: {
  api: HostedApi;
  plan: Plan;
  enabled: boolean;
  refresh: () => Promise<void> | void;
  visible?: boolean;
}) {
  const [operation, setOperation] = useState<Operation | null>(null);
  const [reserved, setReserved] = useState<string | null>(plan.activeOperationId ?? null);
  const [sent, setSent] = useState(Boolean(plan.activeOperationId));
  const [busy, setBusy] = useState(false);
  const [discardConfirmed, setDiscardConfirmed] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState<Reservation | null>(null);
  const [pollVersion, setPollVersion] = useState(0);
  const [checking, setChecking] = useState(false);
  const alive = useRef(true);
  const sending = useRef(false);
  const reserving = useRef(false);
  const activeOperation = useRef<string | null>(plan.activeOperationId ?? null);
  const pendingReservation = useRef(false);
  pendingReservation.current = pending !== null;
  const lastPollVersion = useRef(0);
  useEffect(() => {
    if (
      plan.activeOperationId &&
      plan.activeOperationId !== activeOperation.current &&
      !pendingReservation.current &&
      !reserving.current
    ) {
      activeOperation.current = plan.activeOperationId;
      setReserved(plan.activeOperationId);
      setOperation(null);
      setSent(true);
      sending.current = true;
    }
  }, [plan]);
  useEffect(() => {
    if (!visible) setDiscardConfirmed(false);
  }, [visible]);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    if (!reserved || !sent || settled(operation)) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      let finished = false;
      if (active) setChecking(true);
      try {
        const status = await api.get<Operation>(`/api/v1/operations/${reserved}`);
        if (!active || activeOperation.current !== reserved) return;
        if (
          !status ||
          status.operationId !== reserved ||
          status.planId !== plan.planId ||
          !["reserved", "running", "succeeded", "refused", "cancelled", "expired"].includes(
            status.phase,
          )
        )
          throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
        setOperation((previous) => (settled(previous) && !settled(status) ? previous : status));
        setError("");
        finished = settled(status);
        if (terminal(status)) await refresh();
      } catch (failure) {
        if (active) setError(failureMessage(failure));
      } finally {
        if (active) {
          setChecking(false);
          if (!finished) timer = setTimeout(() => void poll(), 1000);
        }
      }
    };
    const requested = pollVersion !== lastPollVersion.current;
    lastPollVersion.current = pollVersion;
    timer = setTimeout(() => void poll(), requested ? 0 : 1000);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [api, reserved, sent, operation, refresh, plan.planId, pollVersion]);
  async function reserve(command: Reservation) {
    if (reserving.current || !enabled) return;
    reserving.current = true;
    setBusy(true);
    setError("");
    setPending(command);
    try {
      const ack = checkedAck(
        await api.post<Ack>(`/api/v1/plans/${plan.planId}/inspections`, command),
        true,
      );
      if (ack.planId !== plan.planId) throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
      if (alive.current) {
        activeOperation.current = ack.operationId ?? null;
        setReserved(ack.operationId ?? null);
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
    const request = api.credentials(reserved, username, password);
    try {
      const status = await request;
      if (alive.current && activeOperation.current === reserved) {
        if (status.operationId !== reserved || status.planId !== plan.planId)
          throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
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
      const status = await api.post<Operation>(`/api/v1/operations/${reserved}/cancel`, {});
      if (alive.current && activeOperation.current === reserved) {
        if (status.operationId !== reserved || status.planId !== plan.planId)
          throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
        setOperation((previous) => (settled(previous) && !settled(status) ? previous : status));
        setSent(true);
        await refresh();
      }
    } catch (failure) {
      if (alive.current && activeOperation.current === reserved) setError(failureMessage(failure));
    }
  }
  return (
    <section aria-labelledby="inspection-heading" className="hosted-panel">
      <h2 id="inspection-heading">Inspect configuration</h2>
      <p>
        Read-only, one-shot database observation. Credentials remain transient and are never saved.
        Polling does not extend the session idle deadline.
      </p>
      {!enabled && <p id="inspection-disabled">Inspection is not enabled for this deployment.</p>}
      {!reserved && (
        <label>
          <input
            type="checkbox"
            checked={discardConfirmed}
            disabled={!enabled || busy || pending !== null}
            onChange={(event) => setDiscardConfirmed(event.target.checked)}
          />
          Successful inspection may replace current configuration and discard target changes.
        </label>
      )}
      {!reserved && (
        <button
          type="button"
          disabled={!enabled || busy || pending !== null || !discardConfirmed}
          aria-describedby={!enabled ? "inspection-disabled" : undefined}
          onClick={() => {
            if (busy || pending || !discardConfirmed) return;
            void reserve({
              expectedRevision: plan.revision,
              requestId: crypto.randomUUID(),
              discardDraftOnSuccess: true,
            });
          }}
        >
          Reserve inspection
        </button>
      )}
      {pending && !busy && (
        <>
          <p>
            The reservation may have completed. Recover its original operation before entering
            credentials.
          </p>
          <button type="button" disabled={!enabled} onClick={() => void reserve(pending)}>
            Retry original reservation
          </button>
        </>
      )}
      {reserved && (
        <p>
          Operation <code>{reserved}</code>
        </p>
      )}
      {visible && reserved && !sent && (
        <form
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
          <button type="submit">Send credentials once</button>
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
          onClick={() => setPollVersion((value) => value + 1)}
        >
          Check operation status
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
        <p role="status">
          {operation.phase} · {operation.code} · cleanup {operation.cleanup}
        </p>
      )}
      {operation?.cleanup === "inconclusive" && (
        <p>
          Cleanup is inconclusive. Capacity remains quarantined until server cleanup is confirmed.
        </p>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
