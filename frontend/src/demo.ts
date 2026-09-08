import currentEndpoints from "../../fixtures/demo/current/endpoints.xml?raw";
import currentNodes from "../../fixtures/demo/current/nodes.xml?raw";
import currentWorkloads from "../../fixtures/demo/current/workloads.xml?raw";
import targetEndpoints from "../../fixtures/demo/target/endpoints.xml?raw";
import targetNodes from "../../fixtures/demo/target/nodes.xml?raw";
import targetWorkloads from "../../fixtures/demo/target/workloads.xml?raw";

export interface Binding {
  field: string;
  current: string;
  target: string;
}
export interface DemoDocument {
  id: string;
  name: string;
  summary: string;
  current: string;
  target: string;
  currentPlaceholders: string;
  targetPlaceholders: string;
  bindings: Binding[];
}

// Pre-authored synthetic projections. This is not the application's XML mapping engine.
export const documents: DemoDocument[] = [
  {
    id: "nodes",
    name: "nodes.xml",
    summary: "1 node → 2 nodes",
    current: currentNodes,
    target: targetNodes,
    currentPlaceholders:
      '<nodes xmlns="urn:environment-studio:demo">\n  <node id="${node-a.server-id}" role="combined" />\n</nodes>',
    targetPlaceholders:
      '<nodes xmlns="urn:environment-studio:demo">\n  <node id="${node-a.server-id}" role="interactive" />\n  <node id="${node-b.server-id}" role="background" />\n</nodes>',
    bindings: [
      { field: "node-a.server-id", current: "old-01", target: "qa-01" },
      { field: "node-b.server-id", current: "Not present", target: "qa-02" },
    ],
  },
  {
    id: "workloads",
    name: "workloads.xml",
    summary: "2 placements updated",
    current: currentWorkloads,
    target: targetWorkloads,
    currentPlaceholders:
      '<workloads xmlns="urn:environment-studio:demo">\n  <workload key="interactive" node="${node-a.server-id}" application="${work-a.application-name}" />\n  <workload key="background" node="${node-a.server-id}" application="${work-b.application-name}" />\n</workloads>',
    targetPlaceholders:
      '<workloads xmlns="urn:environment-studio:demo">\n  <workload key="interactive" node="${node-a.server-id}" application="${work-a.application-name}" />\n  <workload key="background" node="${node-b.server-id}" application="${work-b.application-name}" />\n</workloads>',
    bindings: [
      { field: "work-a.application-name", current: "OldPortal", target: "QaPortal" },
      { field: "work-b.application-name", current: "OldWorker", target: "QaWorker" },
      { field: "node-a.server-id", current: "old-01", target: "qa-01" },
      { field: "node-b.server-id", current: "Not present", target: "qa-02" },
    ],
  },
  {
    id: "endpoints",
    name: "endpoints.xml",
    summary: "1 external reference updated",
    current: currentEndpoints,
    target: targetEndpoints,
    currentPlaceholders:
      '<endpoints xmlns="urn:environment-studio:demo">\n  <endpoint key="public" url="${public.address}" />\n</endpoints>',
    targetPlaceholders:
      '<endpoints xmlns="urn:environment-studio:demo">\n  <endpoint key="public" url="${public.address}" />\n</endpoints>',
    bindings: [
      {
        field: "public.address",
        current: "https://old.example.invalid/api",
        target: "https://qa.example.invalid/api",
      },
    ],
  },
];
