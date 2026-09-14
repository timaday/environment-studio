import { useState } from "react";
import type { HostedApi } from "../api/hosted";
import { Plans } from "./Plans";
import { useV3PlanInspection } from "./useV3PlanInspection";
import { useV3ProfileCapture } from "./useV3ProfileCapture";
import { V3ExportJourney } from "./V3ExportJourney";
import { V3PlanInspection } from "./V3PlanInspection";
import { V3ProfileCapture } from "./V3ProfileCapture";
import { V3ProfileReuse } from "./V3ProfileReuse";
import { V3ValuesValidation } from "./V3ValuesValidation";
export function VersionedPlans({
  api,
  inspectionUiEnabled,
  active,
  definitionVersion,
  openDefinitions,
  compatibilityMode,
  setCompatibilityMode,
  captureChanged,
}: {
  api: HostedApi;
  inspectionUiEnabled: boolean;
  active: boolean;
  definitionVersion: number;
  openDefinitions: () => void;
  compatibilityMode: "2" | "3";
  setCompatibilityMode: (version: "2" | "3") => void;
  captureChanged?: (open: boolean) => void;
}) {
  const state = useV3PlanInspection(api, active && compatibilityMode === "3");
  const [journey, setJourney] = useState<
    "capture" | "reuse" | "values" | "validation" | "export" | null
  >(null);
  const captureOpen = journey === "capture";
  const reuseOpen = journey === "reuse";
  const valuesOpen = journey === "values";
  const validationOpen = journey === "validation";
  const exportOpen = journey === "export";
  const capture = useV3ProfileCapture(
    api,
    state.plan,
    active && compatibilityMode === "3" && captureOpen,
  );
  const selector = (
    <label>
      Compatibility mode
      <select
        value={compatibilityMode}
        onChange={(e) => {
          setCompatibilityMode(e.target.value === "2" ? "2" : "3");
        }}
      >
        <option value="3">PostgreSQL pilot (Native v3)</option>
        <option value="2">Legacy Native v2</option>
      </select>
    </label>
  );
  return (
    <>
      {compatibilityMode === "2" && <div className="hosted-panel">{selector}</div>}
      {compatibilityMode === "2" && (
        <Plans
          api={api}
          inspectionUiEnabled={inspectionUiEnabled}
          active={active}
          definitionVersion={definitionVersion}
        />
      )}
      {compatibilityMode === "3" && (
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
            editValues={() => {
              setJourney("values");
              captureChanged?.(true);
            }}
            validatePlan={() => {
              setJourney("validation");
              captureChanged?.(true);
            }}
            exportPlan={() => {
              setJourney("export");
              captureChanged?.(true);
            }}
          />
        </div>
      )}
      <div hidden={!reuseOpen || compatibilityMode !== "3"}>
        <V3ProfileReuse
          api={api}
          refreshPlan={state.refresh}
          plan={state.plan}
          active={active && compatibilityMode === "3" && reuseOpen}
          back={() => {
            setJourney(null);
            captureChanged?.(false);
            void state.refresh();
          }}
        />
      </div>
      <div hidden={!valuesOpen || compatibilityMode !== "3"}>
        <V3ValuesValidation
          api={api}
          plan={state.plan}
          active={active && compatibilityMode === "3" && valuesOpen}
          view="values"
          refreshPlan={state.refresh}
          back={() => {
            setJourney(null);
            captureChanged?.(false);
            void state.refresh();
          }}
        />
      </div>
      <div hidden={!validationOpen || compatibilityMode !== "3"}>
        <V3ValuesValidation
          api={api}
          plan={state.plan}
          active={active && compatibilityMode === "3" && validationOpen}
          view="validation"
          refreshPlan={state.refresh}
          back={() => {
            setJourney(null);
            captureChanged?.(false);
            void state.refresh();
          }}
        />
      </div>
      <div hidden={!exportOpen || compatibilityMode !== "3"}>
        <V3ExportJourney
          api={api}
          plan={state.plan}
          active={active && compatibilityMode === "3" && exportOpen}
          back={() => {
            setJourney(null);
            captureChanged?.(false);
            void state.refresh();
          }}
          reviewDocuments={() => {
            setJourney(null);
            captureChanged?.(false);
          }}
        />
      </div>
      <div hidden={!captureOpen || compatibilityMode !== "3"}>
        <V3ProfileCapture
          state={capture}
          plan={state.plan}
          active={active && compatibilityMode === "3" && captureOpen}
          back={() => {
            setJourney(null);
            captureChanged?.(false);
          }}
        />
      </div>
    </>
  );
}
