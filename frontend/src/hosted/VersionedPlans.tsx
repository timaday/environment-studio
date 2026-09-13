import { useState } from "react";
import type { HostedApi } from "../api/hosted";
import { Plans } from "./Plans";
import { useV3PlanInspection } from "./useV3PlanInspection";
import { useV3ProfileCapture } from "./useV3ProfileCapture";
import { V3PlanInspection } from "./V3PlanInspection";
import { V3ProfileCapture } from "./V3ProfileCapture";
import { V3ProfileReuse } from "./V3ProfileReuse";
export function VersionedPlans({
  api,
  inspectionUiEnabled,
  active,
  definitionVersion,
  openDefinitions,
  versionChanged,
  captureChanged,
}: {
  api: HostedApi;
  inspectionUiEnabled: boolean;
  active: boolean;
  definitionVersion: number;
  openDefinitions: () => void;
  versionChanged: (version: string) => void;
  captureChanged?: (open: boolean) => void;
}) {
  const [version, setVersion] = useState("2");
  const state = useV3PlanInspection(api, active && version === "3");
  const [journey, setJourney] = useState<"capture" | "reuse" | null>(null);
  const captureOpen = journey === "capture";
  const reuseOpen = journey === "reuse";
  const capture = useV3ProfileCapture(api, state.plan, active && version === "3" && captureOpen);
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
        <div hidden={journey !== null}>
          <V3PlanInspection
            state={state}
            versionSelector={selector}
            openDefinitions={openDefinitions}
            reuseProfile={() => {
              setJourney("reuse");
              captureChanged?.(true);
            }}
            captureProfile={() => {
              setJourney("capture");
              captureChanged?.(true);
            }}
          />
        </div>
      )}
      <div hidden={!reuseOpen || version !== "3"}>
        <V3ProfileReuse
          api={api}
          refreshPlan={state.refresh}
          plan={state.plan}
          active={active && version === "3" && reuseOpen}
          back={() => {
            setJourney(null);
            captureChanged?.(false);
            void state.refresh();
          }}
        />
      </div>
      <div hidden={!captureOpen || version !== "3"}>
        <V3ProfileCapture
          state={capture}
          plan={state.plan}
          active={active && version === "3" && captureOpen}
          back={() => {
            setJourney(null);
            captureChanged?.(false);
          }}
        />
      </div>
    </>
  );
}
