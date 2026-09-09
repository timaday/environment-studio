import { useEffect, useState } from "react";
import { type Capabilities, failureMessage, HostedApi, type Session } from "../api/hosted";
import { Definitions } from "./Definitions";
import { Plans } from "./Plans";
export function HostedWorkspace({ capabilities }: { capabilities: Capabilities }) {
  const [session, setSession] = useState<Session | null>(null);
  const [checking, setChecking] = useState(true);
  const [message, setMessage] = useState("");
  const [view, setView] = useState<"Plans" | "Definitions">("Plans");
  const [version, setVersion] = useState(0);
  const [api] = useState(
    () =>
      new HostedApi(fetch, () => {
        setSession(null);
        setMessage(
          "Session ended. Hosted values and credentials were cleared. Sign in again to start a new session.",
        );
      }),
  );
  useEffect(() => {
    let active = true;
    api
      .session()
      .then((result) => {
        if (active) {
          setSession(result);
          setMessage("");
        }
      })
      .catch((error) => {
        if (active) setMessage(failureMessage(error));
      })
      .finally(() => {
        if (active) setChecking(false);
      });
    return () => {
      active = false;
      api.clear();
    };
  }, [api]);
  useEffect(() => {
    if (!session) return;
    const remaining = Date.parse(session.absoluteExpiresAt) - Date.now();
    const timer = setTimeout(
      () => {
        api.clear();
        setSession(null);
        setMessage(
          "Session ended. Hosted values and credentials were cleared. Sign in again to start a new session.",
        );
      },
      Number.isFinite(remaining) ? Math.max(0, remaining) : 0,
    );
    return () => clearTimeout(timer);
  }, [api, session]);
  async function logout() {
    try {
      await api.logout();
    } catch (error) {
      setMessage(
        `Hosted state cleared. Logout cleanup is not confirmed: ${failureMessage(error)}. Server capacity may remain quarantined.`,
      );
    }
  }
  return (
    <>
      <header className="app-header">
        <img
          className="wordmark"
          src="/brand/Environment_Studio_Logo.svg"
          alt="Environment Studio"
        />
        <span className="badge">Hosted workspace</span>
        {session && (
          <button type="button" onClick={logout}>
            Log out
          </button>
        )}
      </header>
      <a className="skip-link" href="#main">
        Skip to workspace
      </a>
      {session && (
        <nav className="workspace-navigation segmented" aria-label="Workspace views">
          {(["Plans", "Definitions"] as const).map((name) => (
            <button
              type="button"
              key={name}
              aria-pressed={view === name}
              onClick={() => setView(name)}
            >
              {name}
            </button>
          ))}
        </nav>
      )}
      <main id="main">
        {checking ? (
          <p role="status">Checking hosted session…</p>
        ) : !session ? (
          <section className="hosted-panel">
            <h1>Sign in to your workspace</h1>
            <p>
              Use the configured identity provider. Definitions belong to your authenticated
              account; plans remain session-only.
            </p>
            <a className="primary" href="/oauth2/authorization/studio">
              Sign in with OIDC
            </a>
          </section>
        ) : (
          <>
            <p className="session-note">
              Session absolute expiry {session.absoluteExpiresAt} · idle timeout{" "}
              {session.idleTimeoutSeconds / 60} minutes. Background operation polls do not extend
              it.
            </p>
            <div hidden={view !== "Definitions"}>
              <Definitions
                api={api}
                enabled={capabilities.definitionWorkspaceEnabled}
                changed={() => setVersion((value) => value + 1)}
              />
            </div>
            <div hidden={view !== "Plans"}>
              <Plans
                api={api}
                inspectionUiEnabled={capabilities.inspectionUiEnabled}
                active={view === "Plans"}
                definitionVersion={version}
              />
            </div>
          </>
        )}
        {message && <p role="status">{message}</p>}
      </main>
    </>
  );
}
