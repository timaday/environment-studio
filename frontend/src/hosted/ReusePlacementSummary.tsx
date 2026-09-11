import { ReuseCurrentDetails } from "./ReuseCurrentDetails";
import type { ReuseCommand, useV3ProfileReuse } from "./useV3ProfileReuse";
import type { ReuseInventoryItem } from "./useV3ReuseInventory";

type Preview = NonNullable<ReturnType<typeof useV3ProfileReuse>["preview"]>;

/** Read-only original placement, retained independently of the current editor. */
export function ReusePlacementSummary({
  preview,
  decisions,
  inventory,
}: {
  preview: Preview;
  decisions: ReuseCommand["decisions"];
  inventory: readonly ReuseInventoryItem[];
}) {
  const choices = new Map(decisions.map((decision) => [decision.slotId, decision]));
  const labels = new Map(preview.included.map((item) => [item.slotId, item.label]));
  const dependencies = new Map<string, Preview["dependencies"][number][]>();
  for (const dependency of preview.dependencies) {
    const rows = dependencies.get(dependency.slotId) ?? [];
    rows.push(dependency);
    dependencies.set(dependency.slotId, rows);
  }
  const current = new Map(inventory.map((row, index) => [JSON.stringify(row.entity), index + 1]));
  return (
    <div className="reuse-receipt-rows">
      {preview.included.map((item) => {
        const decision = choices.get(item.slotId);
        const ordinal =
          decision?.kind === "use-existing"
            ? current.get(JSON.stringify(decision.target))
            : undefined;
        return (
          <section key={item.slotId}>
            <div>
              <strong>{item.label}</strong>
              <p>Reusable identifier: {item.slotId}</p>
              <p>Type: {item.typeId}</p>
              <p>
                Required inputs:{" "}
                {item.requiredInputs.length ? item.requiredInputs.join(", ") : "None declared"}
              </p>
            </div>
            <div>
              {dependencies.get(item.slotId)?.map((dependency) => (
                <p key={`${dependency.causedBy}:${dependency.relationId}:${dependency.reason}`}>
                  Required by {labels.get(dependency.causedBy) ?? dependency.causedBy} ·{" "}
                  {dependency.relationId}
                </p>
              ))}
              <strong>
                {decision?.kind === "create"
                  ? "Add configuration item"
                  : decision?.kind === "cancel"
                    ? "Cancel item"
                    : decision?.kind === "use-existing"
                      ? "Reuse existing"
                      : "Placement unavailable"}
              </strong>
              <p>
                {decision?.kind === "create"
                  ? decision.targetSlotId
                  : ordinal
                    ? `Inspected item ${ordinal}`
                    : ""}
              </p>
              {ordinal && inventory[ordinal - 1] && (
                <ReuseCurrentDetails item={inventory[ordinal - 1]} />
              )}
            </div>
          </section>
        );
      })}
    </div>
  );
}
