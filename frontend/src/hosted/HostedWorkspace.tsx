import { useEffect, useState } from "react";
import { type Capabilities, failureMessage, HostedApi, type Session } from "../api/hosted";
import { useNarrowLayout } from "./useNarrowLayout";
import { VersionedDefinitions } from "./VersionedDefinitions";
import { VersionedPlans } from "./VersionedPlans";
export function HostedWorkspace({ capabilities }: { capabilities: Capabilities }) {
  const [session, setSession] = useState<Session | null>(null);
  const [checking, setChecking] = useState(true);
  const [message, setMessage] = useState("");
  const [view, setView] = useState<"Plans" | "Definitions">("Plans");
  const [version, setVersion] = useState(0);
  const [planVersion, setPlanVersion] = useState("2");
  const [capturing, setCapturing] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const narrow = useNarrowLayout();
  const captureActive = view === "Plans" && planVersion === "3" && capturing;
  const compactMenu = captureActive && narrow;
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
    <div
      className={`hosted-workspace${captureActive ? " capture-active" : ""}${view === "Definitions" ? " definitions-active" : planVersion === "3" ? " plans-active" : ""}`}
    >
      <header className="app-header">
        <img
          className="wordmark"
          src="/brand/Environment_Studio_Logo.svg"
          alt="Environment Studio"
        />
        <span className="badge">Hosted workspace</span>
        {session && !compactMenu && (
          <button type="button" onClick={logout}>
            Log out
          </button>
        )}
        {session && compactMenu && (
          <button
            type="button"
            className="capture-menu"
            aria-label="Workspace menu"
            aria-expanded={menuOpen}
            aria-controls="workspace-navigation"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
              <path d="M3 5h18M3 12h18M3 19h18" fill="none" stroke="currentColor" strokeWidth="2" />
            </svg>
          </button>
        )}
      </header>
      <a className="skip-link" href="#main">
        Skip to workspace
      </a>
      {session && (
        <nav
          id="workspace-navigation"
          className="workspace-navigation segmented"
          aria-label="Workspace views"
          hidden={compactMenu && !menuOpen}
        >
          {(["Plans", "Definitions"] as const).map((name) => (
            <button
              type="button"
              key={name}
              aria-pressed={view === name}
              onClick={() => {
                setView(name);
                setMenuOpen(false);
              }}
            >
              {name}
            </button>
          ))}
          {compactMenu && (
            <button type="button" onClick={logout}>
              Log out
            </button>
          )}
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
            <div hidden={view !== "Definitions"}>
              <VersionedDefinitions
                api={api}
                enabled={capabilities.definitionWorkspaceEnabled}
                changed={() => setVersion((value) => value + 1)}
              />
            </div>
            <div hidden={view !== "Plans"}>
              <VersionedPlans
                api={api}
                inspectionUiEnabled={capabilities.inspectionUiEnabled}
                active={view === "Plans"}
                definitionVersion={version}
                openDefinitions={() => setView("Definitions")}
                versionChanged={setPlanVersion}
                captureChanged={setCapturing}
              />
            </div>
            {captureActive ? (
              <details className="capture-session">
                <summary className="capture-session-trigger">Session details</summary>
                <p>
                  Session absolute expiry {session.absoluteExpiresAt} · idle timeout{" "}
                  {session.idleTimeoutSeconds / 60} minutes. Background operation polls do not
                  extend it.
                </p>
              </details>
            ) : (
              <p className="session-note">
                Session absolute expiry {session.absoluteExpiresAt} · idle timeout{" "}
                {session.idleTimeoutSeconds / 60} minutes. Background operation polls do not extend
                it.
              </p>
            )}
          </>
        )}
        {message && <p role="status">{message}</p>}
      </main>
    </div>
  );
}
