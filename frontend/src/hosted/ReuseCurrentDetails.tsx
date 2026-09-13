import type { ReuseInventoryItem } from "./useV3ReuseInventory";

export function ReuseCurrentDetails({ item }: { item: ReuseInventoryItem }) {
  return (
    <details>
      <summary>Current details</summary>
      <dl>
        <dt>Exact identifier</dt>
        <dd>{item.entity.kind === "existing" ? item.entity.handle : item.entity.slotId}</dd>
        {item.fields.map((field) => (
          <div key={field.fieldId}>
            <dt>{field.fieldId}</dt>
            <dd>
              {field.masked
                ? "Masked"
                : !field.present
                  ? "Not present"
                  : field.value === ""
                    ? "Empty text"
                    : field.value}
            </dd>
          </div>
        ))}
      </dl>
    </details>
  );
}
