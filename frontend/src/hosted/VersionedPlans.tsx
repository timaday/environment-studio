import { useState } from "react";
import type { HostedApi } from "../api/hosted";
import { Plans } from "./Plans";
import { useV3PlanInspection } from "./useV3PlanInspection";
import { V3PlanInspection } from "./V3PlanInspection";
export function VersionedPlans({
  api,
  inspectionUiEnabled,
  active,
  definitionVersion,
  openDefinitions,
  versionChanged,
}: {
  api: HostedApi;
  inspectionUiEnabled: boolean;
  active: boolean;
  definitionVersion: number;
  openDefinitions: () => void;
  versionChanged: (version: string) => void;
}) {
  const [version, setVersion] = useState("2");
  const state = useV3PlanInspection(api, active && version === "3");
  const selector = (
    <label>
      Model version
      <select
        value={version}
        onChange={(e) => {
          setVersion(e.target.value);
          versionChanged(e.target.value);
        }}
      >
        <option value="2">Native v2</option>
        <option value="3">Native v3</option>
      </select>
    </label>
  );
  return (
    <>
      {version === "2" && <div className="hosted-panel">{selector}</div>}
      <div hidden={version !== "2"}>
        <Plans
          api={api}
          inspectionUiEnabled={inspectionUiEnabled}
          active={active && version === "2"}
          definitionVersion={definitionVersion}
        />
      </div>
      {version === "3" && (
        <V3PlanInspection
          state={state}
          versionSelector={selector}
          openDefinitions={openDefinitions}
        />
      )}
    </>
  );
}
