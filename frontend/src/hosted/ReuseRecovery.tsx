import { ReusePlacementSummary } from "./ReusePlacementSummary";
import type { ReuseCommand, useV3ProfileReuse } from "./useV3ProfileReuse";
import type { ReuseInventoryItem } from "./useV3ReuseInventory";

type Preview = NonNullable<ReturnType<typeof useV3ProfileReuse>["preview"]>;

/** Presentation of the retained original command; the hook owns exact retry. */
export function ReuseRecovery({
  preview,
  decisions,
  inventory,
  busy,
  retry,
  back,
}: {
  preview: Preview;
  decisions: ReuseCommand["decisions"];
  inventory: readonly ReuseInventoryItem[];
  busy: boolean;
  retry: () => void;
  back: () => void;
}) {
  return (
    <>
      <section className="reuse-panel reuse-recovery-selection">
        <h2>Selected parts</h2>
        <p>
          {preview.pins.selectedRoots.length} selected · {preview.dependencies.length} required{" "}
          {preview.dependencies.length === 1 ? "dependency" : "dependencies"} ·{" "}
          {preview.included.length} included
        </p>
        <ul className="reuse-retained-items">
          {preview.included.map((item) => (
            <li key={item.slotId}>
              <strong>{item.label}</strong>
              <p>
                Reusable identifier: {item.slotId} · Type: {item.typeId}
              </p>
              <p>
                Required inputs:{" "}
                {item.requiredInputs.length ? item.requiredInputs.join(", ") : "None declared"}
              </p>
            </li>
          ))}
        </ul>
      </section>
      <section className="reuse-panel" aria-label="Preserved placement">
        <h2>Review placement</h2>
        <ReusePlacementSummary preview={preview} decisions={decisions} inventory={inventory} />
        <button type="button" className="primary" disabled={busy} onClick={retry}>
          Retry apply
        </button>
        <button type="button" onClick={back}>
          Back to plan
        </button>
      </section>
    </>
  );
}
