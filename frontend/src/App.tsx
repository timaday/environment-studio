import { useEffect, useState } from "react";
import { type Capabilities, HostedApi } from "./api/hosted";
import DemoWorkspace from "./DemoWorkspace";
import { HostedWorkspace } from "./hosted/HostedWorkspace";
export default function App() {
  const [capabilities, setCapabilities] = useState<Capabilities | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    new HostedApi()
      .get<Capabilities>("/api/v1/capabilities")
      .then((value) => {
        if (!active) return;
        if (
          (value.mode !== "demo" && value.mode !== "hosted") ||
          typeof value.inspectionEnabled !== "boolean" ||
          typeof value.definitionWorkspaceEnabled !== "boolean" ||
          typeof value.exportEnabled !== "boolean" ||
          !Array.isArray(value.blockers)
        ) {
          setFailed(true);
          return;
        }
        setCapabilities(value);
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => {
      active = false;
    };
  }, []);
  if (failed)
    return (
      <main id="main">
        <h1>Workspace unavailable</h1>
        <p>Server capabilities could not be loaded. Reload to reconnect.</p>
      </main>
    );
  if (!capabilities)
    return (
      <main id="main">
        <p role="status">Loading server capabilities…</p>
      </main>
    );
  return capabilities.mode === "demo" ? (
    <DemoWorkspace />
  ) : (
    <HostedWorkspace capabilities={capabilities} />
  );
}
