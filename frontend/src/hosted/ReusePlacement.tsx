import { useId } from "react";
import { ReuseCurrentDetails } from "./ReuseCurrentDetails";
import type { ReuseInventoryItem } from "./useV3ReuseInventory";
export type PlacementChoice = { kind: "" | "create" | "use-existing" | "cancel"; value: string };
export function ReusePlacement({
  item,
  inventory,
  value,
  busy,
  onChange,
}: {
  item: { slotId: string; typeId: string; label: string };
  inventory: readonly ReuseInventoryItem[];
  value: PlacementChoice;
  busy: boolean;
  onChange: (value: PlacementChoice) => void;
}) {
  const id = useId();
  const selected = inventory.find(
    (row) => row.entity.kind === "existing" && row.entity.handle === value.value,
  );
  return (
    <section className="reuse-placement">
      <div>
        <h3>{item.label}</h3>
        <small>
          Reusable identifier: {item.slotId} · Type: {item.typeId}
        </small>
      </div>
      <div className="reuse-placement-fields">
        <label htmlFor={`${id}-action`}>
          Action<span className="visually-hidden"> for {item.label}</span>
        </label>
        <select
          id={`${id}-action`}
          value={value.kind}
          disabled={busy}
          onChange={(e) => onChange({ kind: e.target.value as PlacementChoice["kind"], value: "" })}
        >
          <option value="">Choose action</option>
          <option value="use-existing">Reuse existing</option>
          <option value="create">Add configuration item</option>
          <option value="cancel">Cancel item</option>
        </select>
        {value.kind === "use-existing" && (
          <>
            <label htmlFor={`${id}-current`}>
              Current item<span className="visually-hidden"> for {item.label}</span>
            </label>
            <select
              id={`${id}-current`}
              value={value.value}
              disabled={busy}
              onChange={(e) => onChange({ ...value, value: e.target.value })}
            >
              <option value="">Choose current item</option>
              {inventory.map((row, index) =>
                row.entity.kind === "existing" && row.typeId === item.typeId ? (
                  <option key={row.entity.handle} value={row.entity.handle}>
                    Inspected item {index + 1}
                  </option>
                ) : null,
              )}
            </select>
            {selected && <ReuseCurrentDetails item={selected} />}
          </>
        )}
        {value.kind === "create" && (
          <>
            <label htmlFor={`${id}-new`}>
              Target identifier<span className="visually-hidden"> for {item.label}</span>
            </label>
            <input
              id={`${id}-new`}
              value={value.value}
              disabled={busy}
              maxLength={64}
              autoComplete="off"
              onChange={(e) => onChange({ ...value, value: e.target.value })}
            />
            <small>
              Identifier for the new configuration item. Start with a lowercase letter; use
              lowercase letters, digits, dots or hyphens. This does not provision a host.
            </small>
          </>
        )}
      </div>
    </section>
  );
}
