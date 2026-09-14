import { useRef, useState } from "react";
import type { HostedApi } from "../api/hosted";
import { Definitions } from "./Definitions";
import { useV3Definitions } from "./useV3Definitions";
import { V3Definitions } from "./V3Definitions";

export function VersionedDefinitions({
  api,
  enabled,
  active,
  compatibilityMode,
  setCompatibilityMode,
  changed,
}: {
  api: HostedApi;
  enabled: boolean;
  active: boolean;
  compatibilityMode: "2" | "3";
  setCompatibilityMode: (version: "2" | "3") => void;
  changed: () => void;
}) {
  const [startedV3, setStartedV3] = useState(compatibilityMode === "3");
  const [v2Locked, setV2Locked] = useState(false);
  const state = useV3Definitions(api, enabled && active && startedV3);
  const locked = v2Locked || state.busy || state.pending;
  const versionId = useRef(crypto.randomUUID());
  const selector = (
    <label htmlFor={versionId.current}>
      Compatibility mode
      <select
        id={versionId.current}
        value={compatibilityMode}
        disabled={!enabled || locked}
        onChange={(event) => {
          if (locked) return;
          state.cancelFileRead();
          const next = event.target.value === "2" ? "2" : "3";
          setCompatibilityMode(next);
          if (next === "3") setStartedV3(true);
        }}
      >
        <option value="3">PostgreSQL pilot (Native v3)</option>
        <option value="2">Legacy Native v2</option>
      </select>
    </label>
  );
  return (
    <>
      {compatibilityMode === "2" && (
        <div className="hosted-panel definition-version">{selector}</div>
      )}
      {compatibilityMode === "2" && (
        <Definitions
          api={api}
          enabled={enabled && active}
          changed={changed}
          onLocked={setV2Locked}
        />
      )}
      {startedV3 && (
        <div hidden={compatibilityMode !== "3"}>
          <V3Definitions
            state={state}
            enabled={enabled && active}
            versionSelector={compatibilityMode === "3" ? selector : null}
            changed={changed}
          />
        </div>
      )}
    </>
  );
}
