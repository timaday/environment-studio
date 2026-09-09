import { useCallback, useEffect, useRef, useState } from "react";
import {
  type Ack,
  ApiFailure,
  checkedAck,
  type Definition,
  type DefinitionList,
  type Destination,
  definitiveRefusal,
  failureMessage,
  type HostedApi,
  type Plan,
} from "../api/hosted";
import { DocumentComparison } from "./Documents";
import { Inspection } from "./Inspection";

type CreateCommand = Readonly<{
  expectedRevision: "0";
  requestId: string;
  definition: { objectId: string; workspaceRevision: string };
  bindingId: string;
  destinationId: string;
}>;
export function Plans({
  api,
  inspectionEnabled,
  active = true,
  definitionVersion = 0,
}: {
  api: HostedApi;
  inspectionEnabled: boolean;
  active?: boolean;
  definitionVersion?: number;
}) {
  const [definitions, setDefinitions] = useState<DefinitionList>({
    definitions: [],
    canPublish: false,
  });
  const [definition, setDefinition] = useState<Definition | null>(null);
  const [destinations, setDestinations] = useState<Destination[]>([]);
  const [binding, setBinding] = useState("");
  const [destination, setDestination] = useState("");
  const [plan, setPlan] = useState<Plan | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState<CreateCommand | null>(null);
  const mutating = useRef(false);
  const blocked = useRef(false);
  blocked.current = busy || pending !== null;
  const epoch = useRef(0);
  const publishedVersion = useRef(definitionVersion);
  publishedVersion.current = definitionVersion;
  const refresh = useCallback(async () => {
    if (blocked.current) return;
    const token = ++epoch.current;
    try {
      const result = await api.get<Plan>("/api/v1/plans/current");
      if (token === epoch.current) setPlan(result);
    } catch (e) {
      if (token === epoch.current) {
        if (e instanceof ApiFailure && e.status === 404) setPlan(null);
        else setError(failureMessage(e));
      }
    }
  }, [api]);
  useEffect(() => {
    void refresh();
    return () => {
      epoch.current++;
    };
  }, [refresh]);
  useEffect(() => {
    let active = true;
    api
      .get<DefinitionList>("/api/v2/definitions")
      .then((result) => {
        if (active && publishedVersion.current === definitionVersion) setDefinitions(result);
      })
      .catch((e) => {
        if (active) setError(failureMessage(e));
      });
    api
      .get<{ destinations: Destination[] }>("/api/v1/destinations")
      .then((result) => {
        if (active) setDestinations(result.destinations);
      })
      .catch((e) => {
        if (active) setError(failureMessage(e));
      });
    return () => {
      active = false;
    };
  }, [api, definitionVersion]);
  async function choose(id: string) {
    if (blocked.current) return;
    const token = ++epoch.current;
    setDefinition(null);
    setBinding("");
    setDestination("");
    if (!id) return;
    setBusy(true);
    try {
      const result = await api.get<Definition>(`/api/v2/definitions/${id}`);
      if (token === epoch.current) setDefinition(result);
    } catch (e) {
      if (token === epoch.current) setError(failureMessage(e));
    } finally {
      if (token === epoch.current) setBusy(false);
    }
  }
  async function create(command: CreateCommand) {
    if (mutating.current) return;
    mutating.current = true;
    const token = ++epoch.current;
    setBusy(true);
    setError("");
    setPending(command);
    let acknowledged = false;
    try {
      const ack = checkedAck(await api.post<Ack>("/api/v1/plans", command));
      acknowledged = true;
      if (token !== epoch.current) return;
      const result = await api.get<Plan>(`/api/v1/plans/${ack.planId}`);
      if (token === epoch.current) {
        setPlan(result);
        setPending(null);
      }
    } catch (e) {
      if (token === epoch.current) {
        setError(failureMessage(e));
        if (!acknowledged && definitiveRefusal(e)) setPending(null);
      }
    } finally {
      mutating.current = false;
      if (token === epoch.current) setBusy(false);
    }
  }
  const engine = definition?.projection.model.bindings.find((item) => item.id === binding)?.engine;
  return (
    <>
      <section className="hosted-panel">
        <h1>Plans</h1>
        <p>
          Plans and observations are session-only. A server restart, logout or session expiry
          removes them. Saved workspace definition revisions remain separate.
        </p>
        <button type="button" disabled={busy || pending !== null} onClick={refresh}>
          Resume current plan / refresh
        </button>
        {!plan && (
          <>
            <h2>Create plan</h2>
            <label htmlFor="published-definition">Published definition</label>
            <select
              id="published-definition"
              disabled={busy || pending !== null}
              onChange={(event) => void choose(event.target.value)}
              value={definition?.objectId ?? ""}
            >
              <option value="">Choose exact owned publication</option>
              {definitions.definitions
                .filter((item) => item.state === "published")
                .map((item) => (
                  <option key={item.objectId} value={item.objectId}>
                    {item.nativeId} · workspace {item.workspaceRevision}
                  </option>
                ))}
            </select>
            {definition && (
              <p>
                Published object <code>{definition.objectId}</code> · workspace{" "}
                {definition.workspaceRevision} · publication {definition.publication?.digest}
              </p>
            )}
            <label htmlFor="plan-binding">Binding</label>
            <select
              id="plan-binding"
              disabled={busy || pending !== null}
              value={binding}
              onChange={(event) => {
                setBinding(event.target.value);
                setDestination("");
              }}
            >
              <option value="">Choose declared binding</option>
              {definition?.projection.model.bindings.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.id} · {item.engine}
                </option>
              ))}
            </select>
            <label htmlFor="plan-destination">Configured destination</label>
            <select
              id="plan-destination"
              disabled={busy || pending !== null}
              value={destination}
              onChange={(event) => setDestination(event.target.value)}
            >
              <option value="">Choose allowlisted destination</option>
              {destinations
                .filter((item) => item.engine === engine)
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.id} · {item.engine} · {item.host}:{item.port}/{item.database}
                  </option>
                ))}
            </select>
            <button
              type="button"
              disabled={
                busy ||
                pending !== null ||
                definition?.state !== "published" ||
                !binding ||
                !destination
              }
              onClick={() => {
                if (!definition || blocked.current) return;
                void create({
                  expectedRevision: "0",
                  requestId: crypto.randomUUID(),
                  definition: {
                    objectId: definition.objectId,
                    workspaceRevision: definition.workspaceRevision,
                  },
                  bindingId: binding,
                  destinationId: destination,
                });
              }}
            >
              Create plan
            </button>
            {!definitions.definitions.some((item) => item.state === "published") && (
              <p>
                No current published definition is available. Use Definitions to save and explicitly
                publish an owned revision.
              </p>
            )}
          </>
        )}
        {pending && !busy && (
          <>
            <p>
              The plan may have been created. Retry its original command to recover the acknowledged
              plan before making another change.
            </p>
            <button type="button" onClick={() => void create(pending)}>
              Retry original plan command
            </button>
          </>
        )}
        {error && <p role="alert">{error}</p>}
      </section>
      {plan && (
        <>
          <section className="plan-context" aria-label="Persistent plan context">
            <h2>Plan {plan.planId}</h2>
            <p>
              Revision {plan.revision} · definition {plan.definition.objectId} /{" "}
              {plan.definition.workspaceRevision} · binding {plan.bindingId}
            </p>
            <p>
              Configured destination: {plan.destinationId}. Independently observed database
              identity: unavailable in this response.
            </p>
            <p>Plan labels and observed destination details are not yet available.</p>
            <p>
              Observation {plan.inspectionValid ? "valid" : "missing or expired"} · Target{" "}
              {plan.targetComplete ? "complete" : "unavailable"}
            </p>
            <p>
              Current: {plan.currentCounts.documents} documents · {plan.currentCounts.entities}{" "}
              entities · {plan.currentCounts.relations} relations. Target:{" "}
              {plan.targetCounts.documents} documents · {plan.targetCounts.entities} entities ·{" "}
              {plan.targetCounts.relations} relations.
            </p>
            <h3>{plan.blockers.length} global blockers</h3>
            <ul>
              {plan.blockers.map((blocker) => (
                <li key={blocker}>{blocker}</li>
              ))}
            </ul>
            <button type="button" disabled>
              Export unavailable
            </button>
            <p>Export is not available in this development build.</p>
          </section>
          <Inspection
            key={plan.planId}
            api={api}
            plan={plan}
            enabled={inspectionEnabled}
            visible={active}
            refresh={refresh}
          />
          {active && plan.currentCounts.documents > 0 && (
            <DocumentComparison api={api} plan={plan} />
          )}
        </>
      )}
    </>
  );
}
