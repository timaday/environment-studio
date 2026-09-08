import { useState } from "react";
import type { DemoDocument } from "../demo";

const modes = ["Raw", "Placeholders", "Formatted"] as const;
type Mode = (typeof modes)[number];

export function XmlComparison({ document }: { document: DemoDocument }) {
  const [mode, setMode] = useState<Mode>("Raw");
  const placeholder = mode === "Placeholders";
  return (
    <section className="comparison" aria-label="Document comparison">
      <div className="panel-heading">
        <div>
          <h2>{document.name}</h2>
          <p>{document.summary}</p>
        </div>
        <fieldset className="segmented">
          <legend className="sr-only">XML view</legend>
          {modes.map((item) => (
            <button
              type="button"
              key={item}
              aria-pressed={mode === item}
              onClick={() => setMode(item)}
            >
              {item}
            </button>
          ))}
        </fieldset>
      </div>
      <p className="mode-note" aria-live="polite">
        {placeholder
          ? "Mapping projection · concrete current and target values remain visible below."
          : mode === "Formatted"
            ? "Display projection · this synthetic fixture is already formatted. No XML is changed."
            : "Exact synthetic fixture characters · read-only comparison."}
      </p>
      <div className="xml-panes">
        <section className="xml-pane" aria-label="Current XML">
          <div className="pane-title">
            <h3>Current</h3>
            <span>Copied configuration</span>
          </div>
          {/* biome-ignore lint/a11y/noNoninteractiveTabindex: Scrollable XML needs keyboard access. */}
          <section className="code-scroll" tabIndex={0} aria-label="Current XML content">
            <pre>
              <code>{placeholder ? document.currentPlaceholders : document.current}</code>
            </pre>
          </section>
        </section>
        <section className="xml-pane target" aria-label="Target XML">
          <div className="pane-title">
            <h3>Target</h3>
            <span>Proposed configuration</span>
          </div>
          {/* biome-ignore lint/a11y/noNoninteractiveTabindex: Scrollable XML needs keyboard access. */}
          <section className="code-scroll" tabIndex={0} aria-label="Target XML content">
            <pre>
              <code>{placeholder ? document.targetPlaceholders : document.target}</code>
            </pre>
          </section>
        </section>
      </div>
      <div className="binding-section">
        <h3>Environment values</h3>
        <p>Logical fields stay reusable. Concrete values belong to this environment.</p>
        <div className="table-scroll">
          <table aria-label="Environment value mapping">
            <thead>
              <tr>
                <th scope="col">Logical field</th>
                <th scope="col">Current</th>
                <th scope="col">Target</th>
                <th scope="col">Change</th>
              </tr>
            </thead>
            <tbody>
              {document.bindings.map((binding) => (
                <tr key={binding.field}>
                  <th scope="row">
                    <code>{binding.field}</code>
                  </th>
                  <td>{binding.current}</td>
                  <td className="target-value">{binding.target}</td>
                  <td>
                    <span className="badge changed">
                      {binding.current === "Not present" ? "Added" : "Changed"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
