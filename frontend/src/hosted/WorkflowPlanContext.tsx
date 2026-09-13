import type { PlanSummary } from "../api/hostedV3Types";
import { useNarrowLayout } from "./useNarrowLayout";

export function WorkflowPlanContext({ plan, label }: { plan: PlanSummary | null; label: string }) {
  const narrow = useNarrowLayout();
  const observed = plan?.observedDestination;
  const databaseName =
    observed?.engine === "postgresql"
      ? observed.identity.databaseName
      : observed?.engine === "oracle"
        ? observed.identity.conName
        : null;
  return (
    <>
      {plan && (
        <section className="capture-context" aria-label={label}>
          <div>
            <strong>
              {observed?.engine === "postgresql"
                ? "PostgreSQL"
                : observed?.engine === "oracle"
                  ? "Oracle"
                  : "Observed environment unavailable"}
            </strong>
            {databaseName && <span> · {databaseName}</span>}
            <p>
              Plan revision {plan.revision} ·{" "}
              {plan.inspectionValid ? "Inspection valid" : "Inspection required"}
            </p>
          </div>
          {!narrow && (
            <>
              <div>
                <p>Destination: {plan.destinationId}</p>
                <p>Binding: {plan.bindingId}</p>
              </div>
              <div>
                <p>Definition revision {plan.definition.workspaceRevision}</p>
              </div>
            </>
          )}
          <details>
            <summary>Plan details</summary>
            <dl>
              <dt>Plan</dt>
              <dd>{plan.planId}</dd>
              <dt>Definition</dt>
              <dd>
                {plan.definition.objectId} · revision {plan.definition.workspaceRevision}
              </dd>
              <dt>Binding</dt>
              <dd>{plan.bindingId}</dd>
              <dt>Destination</dt>
              <dd>{plan.destinationId}</dd>
            </dl>
          </details>
        </section>
      )}
    </>
  );
}
