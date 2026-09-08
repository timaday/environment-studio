import { type DefinitionInspectionState, DefinitionInspector } from "./DefinitionInspector";

// Invented here for a display-only mock; no private model or database provenance.
// This is not a compiler response and must not be treated as one.
const source = `{
  "schemaVersion": "1", "id": "glimmer-demo", "revision": 1, "status": "draft",
  "entityTypes": [{"id": "glimmer", "label": "Glimmer", "fields": [
    {"id": "shade", "valueType": "text", "required": true,
     "classification": "structural", "sensitivity": "unknown"}
  ]}],
  "relations": [],
  "documents": [{"id": "sample", "logicalStore": "mock-store",
    "recordKey": "sample-key", "namespaces": {}, "mappings": []}],
  "requiredRules": ["mock-rule"], "operationCapabilities": []
}`;
const preview: DefinitionInspectionState = {
  kind: "incomplete",
  revision: "1",
  source,
  model: {
    entityTypes: [{ id: "glimmer", fields: [{ id: "shade", valueType: "text" }] }],
    relations: [],
  },
  diagnostics: [
    {
      phase: "publication",
      code: "PREVIEW_IDENTITY",
      pointer: "",
      message: "Identity and inventory semantics are unresolved.",
    },
    {
      phase: "publication",
      code: "PREVIEW_QUALIFICATION",
      pointer: "",
      message: "Selector, writer and operation qualification is unavailable.",
    },
    {
      phase: "publication",
      code: "PREVIEW_SENSITIVITY",
      pointer: "/entityTypes/0/fields/0/sensitivity",
      message: "Declare field sensitivity.",
    },
    {
      phase: "publication",
      code: "PREVIEW_RULE_BINDING",
      pointer: "/requiredRules",
      message: "Rule implementation binding is unavailable.",
    },
  ],
};

export function DefinitionPreview() {
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">SETTINGS / DEFINITIONS</p>
          <h1>Inspect a definition</h1>
          <p>Review declared vocabulary, source and unresolved semantics together.</p>
        </div>
      </div>
      <div className="demo-notice">
        <strong>Synthetic definition preview</strong>
      </div>
      <p className="implementation-note">
        Independently invented display data. No live compilation API is connected; upload, save and
        publication are unavailable. These illustrative diagnostics are not a compiler response.
      </p>
      <DefinitionInspector state={preview} />
    </>
  );
}
