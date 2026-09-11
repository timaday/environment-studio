import { useRef, useState } from "react";
import type { HostedApi } from "../api/hosted";
import { Definitions } from "./Definitions";
import { useV3Definitions } from "./useV3Definitions";
import { V3Definitions } from "./V3Definitions";

export function VersionedDefinitions({
  api,
  enabled,
  changed,
}: {
  api: HostedApi;
  enabled: boolean;
  changed: () => void;
}) {
  const [version, setVersion] = useState("2");
  const [startedV3, setStartedV3] = useState(false);
  const [v2Locked, setV2Locked] = useState(false);
  const state = useV3Definitions(api, enabled && startedV3);
  const locked = v2Locked || state.busy || state.pending;
  const versionId = useRef(crypto.randomUUID());
  const selector = (
    <label htmlFor={versionId.current}>
      Model version
      <select
        id={versionId.current}
        value={version}
        disabled={!enabled || locked}
        onChange={(event) => {
          if (locked) return;
          state.cancelFileRead();
          setVersion(event.target.value);
          if (event.target.value === "3") setStartedV3(true);
        }}
      >
        <option value="2">Native v2</option>
        <option value="3">Native v3</option>
      </select>
    </label>
  );
  return (
    <>
      {version === "2" && <div className="hosted-panel definition-version">{selector}</div>}
      <div hidden={version !== "2"}>
        <Definitions api={api} enabled={enabled} changed={changed} onLocked={setV2Locked} />
      </div>
      {startedV3 && (
        <div hidden={version !== "3"}>
          <V3Definitions
            state={state}
            enabled={enabled}
            versionSelector={version === "3" ? selector : null}
          />
        </div>
      )}
    </>
  );
}
