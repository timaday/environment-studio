import type { captureSummary } from "./captureSummary";

/** Read-only projection of the returned value-free profile, never donor inventory. */
export function CapturedProfileDetails({
  model,
  narrow,
  saved,
}: {
  model: ReturnType<typeof captureSummary>;
  narrow: boolean;
  saved: boolean;
}) {
  return (
    <details className={`capture-details${saved ? " saved" : ""}`} open={!narrow || !saved}>
      <summary className="capture-details-trigger">Captured items and relationships</summary>
      {narrow ? (
        <ul className="capture-items">
          {model.entities.map((item) => (
            <li key={item.id}>
              <h3>{item.label}</h3>
              <p>
                Identifier: {item.id} · Type: {item.type}
              </p>
              <p>
                Required inputs:{" "}
                {item.requiredInputs.length ? item.requiredInputs.join(", ") : "None declared"}
              </p>
            </li>
          ))}
        </ul>
      ) : (
        <table className="capture-review-table">
          <caption className="visually-hidden">Captured profile items</caption>
          <thead>
            <tr>
              <th scope="col">Label</th>
              <th scope="col">Reusable identifier</th>
              <th scope="col">Declared type</th>
              <th scope="col">Required input</th>
            </tr>
          </thead>
          <tbody>
            {model.entities.map((item) => (
              <tr key={item.id}>
                <td>{item.label}</td>
                <td>{item.id}</td>
                <td>{item.type}</td>
                <td>
                  {item.requiredInputs.length ? item.requiredInputs.join(", ") : "None declared"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <h3 className="capture-relationships-title">Relationships</h3>
      {model.relations.length ? (
        narrow ? (
          <ul className="capture-relations">
            {model.relations.map((rel) => (
              <li key={`${rel.type}/${rel.from}/${rel.to}`}>
                {rel.from} to {rel.to} · {rel.type}
              </li>
            ))}
          </ul>
        ) : (
          <table className="capture-review-table">
            <caption className="visually-hidden">Captured profile relationships</caption>
            <thead>
              <tr>
                <th scope="col">From</th>
                <th scope="col">To</th>
                <th scope="col">Type</th>
              </tr>
            </thead>
            <tbody>
              {model.relations.map((rel) => (
                <tr key={`${rel.type}/${rel.from}/${rel.to}`}>
                  <td>{rel.from}</td>
                  <td>{rel.to}</td>
                  <td>{rel.type}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      ) : (
        <p>No relationships declared.</p>
      )}
    </details>
  );
}
