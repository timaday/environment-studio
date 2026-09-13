import { useState } from "react";
import { DefinitionPreview } from "./components/DefinitionPreview";
import { XmlComparison } from "./components/XmlComparison";
import { documents } from "./demo";

export default function DemoWorkspace() {
  const [view, setView] = useState<"Comparison" | "Definitions">("Comparison");
  const [selectedId, setSelectedId] = useState(documents[0].id);
  const selected = documents.find((document) => document.id === selectedId);
  if (!selected) throw new Error("UNKNOWN_DEMO_DOCUMENT");
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to workspace
      </a>
      <header className="app-header">
        <img
          className="wordmark"
          src="/brand/Environment_Studio_Logo.svg"
          alt="Environment Studio"
        />
        <span className="badge">Development preview</span>
        <a href="https://github.com/timaday/environment-studio/blob/main/docs/delivery/build-plan.md">
          Build plan ↗
        </a>
      </header>
      <nav className="workspace-navigation segmented" aria-label="Workspace views">
        {(["Comparison", "Definitions"] as const).map((item) => (
          <button
            type="button"
            key={item}
            aria-pressed={view === item}
            onClick={() => setView(item)}
          >
            {item}
          </button>
        ))}
      </nav>
      <main id="main">
        {view === "Definitions" ? (
          <DefinitionPreview />
        ) : (
          <>
            <div className="page-heading">
              <div>
                <p className="eyebrow">PLAN WORKSPACE / COMPARISON</p>
                <h1>Shape the next environment</h1>
                <p>Understand every change, across every configuration document.</p>
              </div>
              <button type="button" className="primary" disabled aria-describedby="export-blocker">
                Export SQL
              </button>
            </div>
            <div className="demo-notice">
              <strong>Synthetic example</strong>
              <span>1 node → 2 nodes · 2 workloads · 1 external endpoint</span>
            </div>
            <p id="export-blocker" className="implementation-note">
              This synthetic demo does not connect to a database or authorize SQL export. It uses
              reviewed example files only.
            </p>
            <div className="workbench">
              <aside className="document-list" aria-label="Configuration documents">
                <div className="list-heading">
                  <h2>Documents</h2>
                  <p>3 documents · 3 changed</p>
                </div>
                <ul>
                  {documents.map((document) => (
                    <li key={document.id}>
                      <button
                        type="button"
                        aria-pressed={selected.id === document.id}
                        onClick={() => setSelectedId(document.id)}
                      >
                        <span className="file-name">{document.name}</span>
                        <span>{document.summary}</span>
                      </button>
                    </li>
                  ))}
                </ul>
                <div className="scope-note">
                  <span className="eyebrow">SCOPE</span>
                  <p>
                    A single logical change can affect multiple CLOBs. Review the full document set.
                  </p>
                </div>
              </aside>
              <XmlComparison document={selected} />
            </div>
            <footer className="workspace-footer">
              <span>Demo definition v1 · No database connected</span>
              <span>Export unavailable · Qualification required</span>
            </footer>
          </>
        )}
      </main>
    </>
  );
}
