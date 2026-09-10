import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { test } from "node:test";

const require = createRequire(new URL("../frontend/package.json", import.meta.url));
const Ajv = require("ajv");
const read = (path) => JSON.parse(readFileSync(new URL(path, import.meta.url), "utf8"));
const ajv = new Ajv({ allErrors: true, strict: true });
const definition = ajv.compile(read("../schemas/definition.schema.json"));
const profile = ajv.compile(read("../schemas/profile.schema.json"));

test("draft definition example has a closed valid shape, not publication authority", () => {
  assert.equal(definition(read("../fixtures/demo/definition.json")), true, JSON.stringify(definition.errors));
});
test("value-free draft profile has a closed valid shape", () => {
  assert.equal(profile(read("../fixtures/demo/profile.json")), true, JSON.stringify(profile.errors));
});
test("a profile cannot carry donor field values or credential metadata", () => {
  for (const injected of ["values", "password", "rawXml", "metadata"]) {
    const candidate = read("../fixtures/demo/profile.json");
    candidate[injected] = "synthetic-canary";
    assert.equal(profile(candidate), false, injected);
  }
  const candidate = read("../fixtures/demo/profile.json");
  candidate.entities[0].serverId = "donor-id";
  assert.equal(profile(candidate), false);
});
test("unknown type semantics and missing requiredness cannot be silently accepted", () => {
  const unknown = read("../fixtures/demo/definition.json");
  unknown.entityTypes[0].fields[0].valueType = "guess-from-name";
  assert.equal(definition(unknown), false);
  const incomplete = read("../fixtures/demo/definition.json");
  delete incomplete.entityTypes[0].fields[0].required;
  assert.equal(definition(incomplete), false);
});

const nativeV2 = ajv.compile(read("../schemas/definition-v2.schema.json"));
const nativeV3 = ajv.compile(read("../schemas/definition-v3.schema.json"));
const inspectionV3 = ajv.compile(read("../schemas/definition-inspection-v3.schema.json"));
const decimalInspection = (value) => JSON.parse(JSON.stringify(value, (key, item) => typeof item === "number" ? String(item) : item));
test("v3 explicitly separates physical bindings from closed derived declarations", () => {
  const candidate = read("../fixtures/native-v3/definition.json");
  assert.equal(nativeV3(candidate), true, JSON.stringify(nativeV3.errors));
  assert.equal(inspectionV3(decimalInspection(candidate)), true, JSON.stringify(inspectionV3.errors));
  assert.equal(nativeV2(candidate), false);
  assert.equal(nativeV3(read("../fixtures/native-v2/definition.json")), false);
  candidate.schemaVersion = "2";
  assert.equal(nativeV2(candidate), false, "version relabelling cannot add v3 declarations to v2");
});
test("v3 requires explicit derived arrays and refuses uploaded computation or authority", () => {
  const seed = read("../fixtures/native-v3/definition.json");
  assert.equal(nativeV3(seed), true);
  for (const mutate of [
    c => { delete c.logical.derivations; },
    c => { delete c.logical.computedTypes; },
    c => { delete c.logical.cooccurrences; },
    c => { delete c.logical.computedRules; },
    c => { c.logical.derivedSemantics = { maxTotalNodes: 999999 }; },
    c => { c.mechanisms = { "derived-graph-v1": 1 }; },
    c => { c.logical.computedTypes[0].identity = { field: "tone" }; },
    c => { c.logical.derivations[0].expression = "invented-expression"; },
    c => { c.logical.derivations[0].normalization = "trim"; },
    c => { delete c.logical.derivations[0].membershipRelation; },
    c => { c.logical.cooccurrences[0].includeTargetOnReuse = true; },
    c => { delete c.logical.cooccurrences[0].maximum; },
    c => { c.logical.computedRules[0].kind = "inferred-count"; },
  ]) {
    const bad = structuredClone(seed); mutate(bad);
    assert.equal(nativeV3(bad), false);
    assert.equal(inspectionV3(decimalInspection(bad)), false);
  }
  for (const key of ["computedTypes", "derivations", "cooccurrences", "computedRules"]) seed.logical[key] = [];
  assert.equal(nativeV3(seed), true, "explicitly empty arrays have defined v3 semantics");
});
test("v3 bounds declaration arrays at 32 independently of semantic uniqueness", () => {
  for (const key of ["computedTypes", "derivations", "cooccurrences"]) {
    const seed = read("../fixtures/native-v3/definition.json");
    seed.logical[key] = Array.from({ length: 32 }, (_, i) => ({ ...seed.logical[key][0], id: `invented-${i}` }));
    assert.equal(nativeV3(seed), true, key);
    assert.equal(inspectionV3(decimalInspection(seed)), true, key);
    seed.logical[key].push({ ...seed.logical[key][0], id: "invented-over" });
    assert.equal(nativeV3(seed), false, key);
    assert.equal(inspectionV3(decimalInspection(seed)), false, key);
  }
});
test("v3 inspection uses canonical bounded decimals without changing source integer semantics", () => {
  const source = read("../fixtures/native-v3/definition.json");
  assert.equal(nativeV3(source), true);
  const shown = decimalInspection(source);
  shown.revision = "9".repeat(1024);
  shown.logical.cooccurrences[0].maximum = "9".repeat(1024);
  assert.equal(inspectionV3(shown), true, JSON.stringify(inspectionV3.errors));
  for (const bad of ["01", "-1", "1\n", "1.0", "9".repeat(1025), 1]) {
    const candidate = structuredClone(shown); candidate.logical.cooccurrences[0].maximum = bad;
    assert.equal(inspectionV3(candidate), false);
  }
  source.logical.cooccurrences[0].minimum = -1;
  assert.equal(nativeV3(source), false);
});
test("v3 shape acceptance leaves field eligibility and physical partition references to Java", () => {
  const source = read("../fixtures/native-v3/definition.json");
  source.logical.entityTypes[0].fields[1].sensitivity = "secret";
  source.logical.cooccurrences[0].toDerivation = "not-declared";
  assert.equal(nativeV3(source), true, "shape checks do not grant semantic acceptance");
});
test("v3 portable profiles refuse computed data at every level and keep v2 separate", () => {
  const validate = ajv.compile(read("../schemas/profile-v3.schema.json"));
  const inspection = ajv.compile(read("../schemas/profile-inspection-v3.schema.json"));
  const seed = read("../fixtures/native-v3/profile.json");
  assert.equal(validate(seed), true);
  assert.equal(inspection(decimalInspection(seed)), true);
  assert.equal(profileV2(seed), false);
  assert.equal(validate(read("../fixtures/profile-v2/profile.json")), false);
  for (const location of ["root", "entity", "relation"]) for (const key of ["computedTypes", "derivations", "constraints", "contributors", "identity", "values", "locator"]) {
    const bad = structuredClone(seed);
    const target = location === "root" ? bad : location === "entity" ? bad.entities[0] : bad.relations[0];
    target[key] = "invented-donor-canary";
    assert.equal(validate(bad), false, `${location}.${key}`);
    assert.equal(inspection(decimalInspection(bad)), false, `${location}.${key}`);
  }
  seed.entities[0].type = "tone-group";
  assert.equal(validate(seed), true, "Java must reject computed slot references against the pinned definition");
});
test("child field declarations and historical inspection use the same closed mutually exclusive forms", () => {
  const inspection = ajv.compile(read("../schemas/definition-inspection-v2.schema.json"));
  const candidate = read("../fixtures/native-v2/definition.json");
  const mapping = candidate.bindings[0].documents[0].entities[0].fields[1];
  delete mapping.attribute;
  mapping.childProperty = {
    element: { namespaceUri: "urn:mock:properties", localName: "entry" },
    discriminatorAttribute: { namespaceUri: "urn:mock:keys", localName: "key" },
    discriminatorValue: "é𐀀\t",
    valueAttribute: { namespaceUri: "", localName: "value" },
  };
  for (const validate of [nativeV2, inspection]) {
    const input = validate === inspection
      ? JSON.parse(JSON.stringify(candidate, (key, value) => typeof value === "number" ? String(value) : value))
      : structuredClone(candidate);
    assert.equal(validate(input), true, JSON.stringify(validate.errors));
    for (const mutate of [
      m => { m.attribute = { namespaceUri: "", localName: "tone" }; },
      m => { delete m.childProperty; },
      m => { m.childProperty.descendants = true; },
      m => { delete m.childProperty.discriminatorValue; },
      m => { m.childProperty.valueAttribute.prefix = "p"; },
    ]) {
      const bad = structuredClone(input); mutate(bad.bindings[0].documents[0].entities[0].fields[1]);
      assert.equal(validate(bad), false);
    }
  }
});
test("invented native v2 bindings have a closed shape with distinct engine storage declarations", () => {
  const candidate = read("../fixtures/native-v2/definition.json");
  assert.equal(nativeV2(candidate), true, JSON.stringify(nativeV2.errors));
  assert.deepEqual(candidate.bindings.map((binding) => binding.engine), ["postgresql", "oracle"]);
});
test("native v2 rejects unknown authority fields, unsupported vocabulary and omitted decisions", () => {
  for (const mutate of [
    (candidate) => { candidate.status = "published"; },
    (candidate) => { candidate.mechanisms = { "xml-span-v1": 999 }; },
    (candidate) => { delete candidate.logical.entityTypes[0].fields[0].editable; },
    (candidate) => { candidate.logical.entityTypes[0].identity.normalization = "trim"; },
    (candidate) => { candidate.logical.rules[0].kind = "uploaded-code"; },
    (candidate) => { candidate.logical.operationCapabilities.push("execute-sql"); },
    (candidate) => { candidate.bindings[0].table = "mock;expression"; },
    (candidate) => { candidate.bindings[0].documents[0].entities[0].path[0].localName = "has:prefix"; },
  ]) {
    const candidate = read("../fixtures/native-v2/definition.json");
    mutate(candidate);
    assert.equal(nativeV2(candidate), false);
  }
});
test("native v2 missing mapping mechanisms remain shape-valid for explicit incomplete diagnostics", () => {
  const candidate = read("../fixtures/native-v2/definition.json");
  candidate.bindings[0].documents[0].entities[0].fields = [];
  candidate.bindings[0].documents[0].entities[0].references = [];
  assert.equal(nativeV2(candidate), true, JSON.stringify(nativeV2.errors));
});
test("native v2 XML names count Unicode code points and reject trailing newline", () => {
  const candidate = read("../fixtures/native-v2/definition.json");
  candidate.bindings[0].documents[0].entities[0].path[0].localName = "𐀀glyph";
  assert.equal(nativeV2(candidate), true, JSON.stringify(nativeV2.errors));
  candidate.bindings[0].documents[0].entities[0].path[0].localName = "glyph\n";
  assert.equal(nativeV2(candidate), false);
});

const profileV2 = ajv.compile(read("../schemas/profile-v2.schema.json"));
test("native profile v2 admits only closed value-free structure", () => {
  assert.equal(profileV2(read("../fixtures/profile-v2/profile.json")), true, JSON.stringify(profileV2.errors));
  for (const location of ["root", "entity", "relation"]) for (const key of ["values", "identity", "rawXml", "locator", "metadata", "credential"]) {
    const candidate = read("../fixtures/profile-v2/profile.json");
    const target = location === "root" ? candidate : location === "entity" ? candidate.entities[0] : candidate.relations[0];
    target[key] = { canary: "invented-donor" };
    assert.equal(profileV2(candidate), false, `${location}.${key}`);
  }
});
test("native profile v2 rejects duplicate required inputs, wrong versions and invalid neutral IDs", () => {
  for (const mutate of [
    (p) => { p.schemaVersion = "1"; },
    (p) => { p.entities[0].requiredInputs.push("tag"); },
    (p) => { p.entities[0].id = "first\n"; },
    (p) => { p.entities[0].label = ""; },
    (p) => { p.definitionDigest = p.logicalDefinitionDigest; },
    (p) => { p.revision = 0; },
  ]) { const candidate = read("../fixtures/profile-v2/profile.json"); mutate(candidate); assert.equal(profileV2(candidate), false); }
});

const Ajv2020 = require("ajv/dist/2020");
test("workspace history admits the optional child dependency without accepting uploaded authority", () => {
  const validate = new Ajv2020({ allErrors: true, strict: true }).compile(read("../docs/contracts/openapi-workspace-v2.json").components.schemas.Mechanisms);
  const base = { "native-compiler-v2": "2", "xml-path-v1": "1", "xml-span-v1": "1", "generic-graph-v1": "1" };
  assert.equal(validate(base), true);
  assert.equal(validate({ ...base, "xml-child-property-v1": "1" }), true, JSON.stringify(validate.errors));
  assert.equal(validate({ ...base, "xml-child-property-v1": "2" }), true);
  assert.equal(validate({ ...base, "xml-child-property-v1": "0" }), false);
  assert.equal(validate({ ...base, "xml-child-property-v2": "1" }), false);
});
test("workspace historical mechanism versions remain readable without granting new publication", () => {
  const schema = read("../docs/contracts/openapi-workspace-v2.json").components.schemas.Mechanisms;
  const validate = new Ajv2020({ allErrors: true, strict: true }).compile(schema);
  const historical = Object.fromEntries(Object.keys(schema.properties).map((name) => [name, "2"]));
  assert.equal(validate(historical), true, JSON.stringify(validate.errors));
  for (const invalid of ["0", "01", "-1", "1\n", "1".repeat(1025)]) {
    historical["xml-span-v1"] = invalid;
    assert.equal(validate(historical), false, invalid.slice(0, 20));
  }
});
const packageAjv = new Ajv2020({ allErrors: true, strict: true });
const packageManifest = packageAjv.compile(read("../schemas/guarded-manifest-v1.schema.json"));
const packagePayload = packageAjv.compile(read("../schemas/guarded-payload-v1.schema.json"));
test("invented guarded package shapes support both engine contexts without granting execution authority", () => {
  const manifest = read("../fixtures/guarded-package-v1/manifest.json");
  const payload = read("../fixtures/guarded-package-v1/payload.json");
  assert.equal(packageManifest(manifest), true, JSON.stringify(packageManifest.errors));
  assert.equal(packagePayload(payload), true, JSON.stringify(packagePayload.errors));
  manifest.execution.engine = "oracle";
  manifest.execution.storage = "clob";
  manifest.execution.serverVersion = "23.26.3.0.0";
  manifest.execution.templateVersion = "oracle-clob-v1";
  manifest.execution.client = { family: "sqlplus", version: "23.26.3.0.0", platform: "linux-amd64" };
  manifest.execution.destination.expectedPhysicalIdentity = {
    dbid: "42", dbUniqueName: "MOCKDB", conId: "3", conUid: "43", conName: "MOCKPDB", pdbGuid: "a".repeat(32),
  };
  payload.engine = "oracle";
  payload.storage = "clob";
  assert.equal(packageManifest(manifest), true, JSON.stringify(packageManifest.errors));
  assert.equal(packagePayload(payload), true, JSON.stringify(packagePayload.errors));
});
test("guarded manifest refuses foreign commands, credentials and incompatible execution pins", () => {
  for (const mutate of [
    (candidate) => { candidate.approved = true; },
    (candidate) => { candidate.execution.password = "invented-canary"; },
    (candidate) => { candidate.execution.destination.url = "invented-input"; },
    (candidate) => { candidate.execution.destination.transport = "disposable-loopback"; candidate.execution.destination.host = "invented-remote.invalid"; },
    (candidate) => { candidate.execution.storage = "clob"; },
    (candidate) => { candidate.execution.client.family = "sqlplus"; },
    (candidate) => { candidate.execution.templateVersion = "uploaded-template"; },
    (candidate) => { candidate.execution.mechanisms["xml-span-v1"] = "2"; },
    (candidate) => { candidate.execution.mechanisms["native-compiler-v2"] = "1"; },
    (candidate) => { candidate.execution.exportPolicies[0].content = "deny"; },
    (candidate) => { candidate.execution.bindingDigest += "\n"; },
    (candidate) => { candidate.members["tail.sql"] = candidate.members["transaction.sql"]; },
    (candidate) => { delete candidate.execution.destination; },
  ]) {
    const candidate = read("../fixtures/guarded-package-v1/manifest.json");
    mutate(candidate);
    assert.equal(packageManifest(candidate), false);
  }
});
test("guarded payload refuses SQL-shaped identifiers, unknown fields and malformed wire values", () => {
  for (const mutate of [
    (candidate) => { candidate.table.name = 'rows";COMMIT;'; },
    (candidate) => { candidate.table.xmlColumn += "\n"; },
    (candidate) => { candidate.bindingId += "\n"; },
    (candidate) => { candidate.sql = "invented-command"; },
    (candidate) => { candidate.records[0].predicate = "invented-expression"; },
    (candidate) => { candidate.records[0].originalHex = ""; },
    (candidate) => { candidate.records[0].targetHex = "00\n"; },
    (candidate) => { candidate.records[0].targetHex = "00xz"; },
    (candidate) => { candidate.records[0].key = { type: "int64", value: "01" }; },
    (candidate) => { candidate.records[0].key.value = 1; },
    (candidate) => { candidate.storage = "clob"; },
    (candidate) => { candidate.records = Array.from({ length: 129 }, () => candidate.records[0]); },
  ]) {
    const candidate = read("../fixtures/guarded-package-v1/payload.json");
    mutate(candidate);
    assert.equal(packagePayload(candidate), false);
  }
});

const planCommands = new Ajv2020({ allErrors: true, strict: true }).compile(read("../schemas/plan-command-v1.schema.json"));
test("bounded batch upserts carry explicit decisions and placements without implicit replacement", () => {
  const single = read("../fixtures/plan-http-v1/command.json");
  const batch = { kind: "batch-upsert", expectedRevision: single.expectedRevision, requestId: single.requestId,
    changes: [{ decision: single.decision, placements: [] }], containment: [] };
  assert.equal(planCommands(batch), true, JSON.stringify(planCommands.errors));
  for (const mutate of [
    (b) => { delete b.changes[0].placements; },
    (b) => { b.changes[0].mergeMissingFields = true; },
    (b) => { delete b.changes[0].decision.fields; },
    (b) => { b.clearUnselected = true; },
    (b) => { b.changes = []; },
  ]) {
    const bad = structuredClone(batch); mutate(bad); assert.equal(planCommands(bad), false);
  }
});
test("plan commands preserve explicit unresolved values without accepting caller authority", () => {
  const candidate = read("../fixtures/plan-http-v1/command.json");
  assert.equal(planCommands(candidate), true, JSON.stringify(planCommands.errors));
  for (const mutate of [
    (c) => { c.owner = "invented-owner"; },
    (c) => { c.validation = "PASS"; },
    (c) => { c.expectedRevision = "02"; },
    (c) => { c.requestId += "\n"; },
    (c) => { c.decision.entity.identity = "invented-02"; },
    (c) => { c.decision.fields.shade = { kind: "unresolved", text: "guessed" }; },
    (c) => { c.decision.fields.tag.kind = "infer"; },
    (c) => { c.decision.references.link = { kind: "to", target: { kind: "existing", key: "invented" } }; },
    (c) => { delete c.decision.references; },
  ]) {
    const bad = structuredClone(candidate);
    mutate(bad);
    assert.equal(planCommands(bad), false);
  }
});

test("planned plan reservation and credential shapes exclude authority and replay metadata", () => {
  const components = read("../docs/contracts/openapi-plans-v1.json").components.schemas;
  const defs = JSON.parse(JSON.stringify(components).replaceAll("#/components/schemas/", "#/$defs/").replaceAll("../../schemas/plan-command-v1.schema.json", "https://environment.studio/schemas/plan-command-v1"));
  const validator = new Ajv2020({ allErrors: true, strict: true }).addSchema(read("../schemas/plan-command-v1.schema.json"));
  const creation = validator.compile({ $defs: defs, $ref: "#/$defs/CreatePlan" });
  const reserve = validator.compile({ $defs: defs, $ref: "#/$defs/ReserveInspection" });
  const candidate = { expectedRevision: "0", requestId: "00000000-0000-4000-8000-000000000031", definition: { objectId: "00000000-0000-4000-8000-000000000032", workspaceRevision: "2" }, bindingId: "invented.pg", destinationId: "invented.destination" };
  assert.equal(creation(candidate), true, JSON.stringify(creation.errors));
  assert.equal(creation({ ...candidate, owner: "invented-owner" }), false);
  assert.equal(creation({ ...candidate, observedXml: "invented-input" }), false);
  assert.equal(reserve({ expectedRevision: "1", requestId: candidate.requestId, discardDraftOnSuccess: true }), true);
  assert.equal(reserve({ expectedRevision: "1", requestId: candidate.requestId }), false);
  const credential = validator.compile({ $defs: defs, $ref: "#/$defs/Credentials" });
  const invented = { username: "independent-user", password: ["independent", "canary"].join("-") };
  assert.equal(credential(invented), true);
  assert.equal(credential({ ...invented, requestId: candidate.requestId }), false);
  assert.equal(credential({ ...invented, owner: "invented-owner" }), false);
  assert.equal(credential({ ...invented, password: "" }), false);
  assert.equal(credential({ ...invented, username: "bad\u0000name" }), false);
});

const planViewSchema = read("../schemas/plan-view-v1.schema.json");
test("observed destination summary has closed engine identities and no configured evidence passthrough", () => {
  const defs = JSON.parse(JSON.stringify(read("../docs/contracts/openapi-plans-v1.json").components.schemas).replaceAll("#/components/schemas/", "#/$defs/"));
  const validate = new Ajv2020({ allErrors: true, strict: true }).compile({ $defs: defs, $ref: "#/$defs/ObservedDestination" });
  assert.equal(validate(null), true);
  const pg = { engine: "postgresql", identity: { systemIdentifier: "731", databaseOid: "19", databaseName: "invented_db" }, observationFingerprint: "b".repeat(64), evidenceValid: true };
  const oracle = { engine: "oracle", identity: { dbid: "711", dbUniqueName: "invented_cdb", conId: "3", conUid: "812", conName: "invented_pdb", pdbGuid: "e".repeat(32) }, observationFingerprint: "f".repeat(64), evidenceValid: false };
  for (const candidate of [pg, oracle]) {
    assert.equal(validate(candidate), true, JSON.stringify(validate.errors));
    for (const key of Object.keys(candidate)) { const missing = structuredClone(candidate); delete missing[key]; assert.equal(validate(missing), false); }
    for (const key of Object.keys(candidate.identity)) { const missing = structuredClone(candidate); delete missing.identity[key]; assert.equal(validate(missing), false); }
    assert.equal(validate({ ...candidate, expectedPhysicalIdentity: candidate.identity }), false);
    assert.equal(validate({ ...candidate, observationFingerprint: "F".repeat(64) }), false);
    assert.equal(validate({ ...candidate, evidenceValid: "true" }), false);
    assert.equal(validate({ ...candidate, identity: { ...candidate.identity, transportIdentity: "c".repeat(64) } }), false);
  }
  assert.equal(validate({ ...pg, identity: oracle.identity }), false);
  for (const databaseName of ["", " ", "\u2003", "bad\nname", "\ud800", "x".repeat(129)]) assert.equal(validate({ ...pg, identity: { ...pg.identity, databaseName } }), false);
  for (const databaseName of ["𐀀".repeat(128), "\u00a0"]) assert.equal(validate({ ...pg, identity: { ...pg.identity, databaseName } }), true, JSON.stringify(validate.errors));
});
const planViewAjv = new Ajv2020({ allErrors: true, strict: true }).addSchema(read("../schemas/plan-command-v1.schema.json")).addSchema(planViewSchema);
const planView = (name) => planViewAjv.compile({ $ref: `${planViewSchema.$id}#/$defs/${name}` });
const viewShapes = read("../fixtures/plan-views-v1/shapes.json");
test("planned view fixtures cover every closed request and response family without granting authority", () => {
  for (const [name, shape] of Object.entries({ ...viewShapes.requests, ...viewShapes.responses })) {
    const validate = planView(name);
    assert.equal(validate(shape), true, `${name}: ${JSON.stringify(validate.errors)}`);
    assert.equal(validate({ ...shape, callerApproved: true }), false, name);
  }
});
test("view masking, consent, absent target and preview sections cannot claim unsafe shapes", () => {
  for (const [name, mutate] of [
    ["entitiesResponse", s => { s.items[0].fields[1].value = "independent-hidden-canary"; }],
    ["entitiesResponse", s => { s.items[0].fields[0].present = false; }],
    ["draftResponse", s => { s.items[0].fields[0].value = "independent-hidden-canary"; }],
    ["draftResponse", s => { s.items[0].references[0].target = s.items[0].entity; }],
    ["draftResponse", s => { s.items[0].disposition = "remove"; }],
    ["documentsResponse", s => { s.documents[0].changed = false; }],
    ["previewIncludedResponse", s => { s.section = "conflicts"; }],
    ["validationResponse", s => { s.checks.pop(); }],
    ["validationResponse", s => { s.checks.reverse(); }],
    ["validationResponse", s => { s.exportAvailable = true; }],
    ["documentRequest", s => { s.completeDocumentDisclosure = false; }],
    ["captureRequest", s => { s.mappings[0].entity = { kind: "fresh", slotId: "new-tone", typeId: "tone" }; }],
    ["previewRequest", s => { s.selection = { kind: "selected", roots: ["one", "one"] }; }],
    ["entitiesRequest", s => { s.limit = 101; }],
    ["entitiesRequest", s => { s.revision = "01"; }],
  ]) {
    const shape = structuredClone(viewShapes.responses[name] ?? viewShapes.requests[name]);
    mutate(shape); assert.equal(planView(name)(shape), false, name);
  }
});
test("thirteen contracted view routes share closed schemas, authentication and no-store responses", () => {
  const api = read("../docs/contracts/openapi-plans-v1.json");
  const suffixes = ["materializations", "views/documents", "views/entities", "views/relations", "views/draft", "views/containment", "views/placements", "views/document", "views/bindings", "views/binding-locations", "profile-captures", "profile-previews", "validations"];
  const aggregate = readFileSync(new URL("../docs/contracts/openapi.yaml", import.meta.url), "utf8");
  for (const suffix of suffixes) {
    const path = `/api/v1/plans/{planId}/${suffix}`;
    const operation = api.paths[path];
    assert.deepEqual(Object.keys(operation), ["post"]);
    assert.deepEqual(operation.post.security, [{ sessionCookie: [] }]);
    assert.ok(operation.post.parameters.some(p => p.name === "X-CSRF-TOKEN" && p.required));
    assert.equal(operation.post.responses["200"].headers["Cache-Control"].schema.const, "no-store");
    for (const schema of [operation.post.requestBody.content["application/json"].schema, operation.post.responses["200"].content["application/json"].schema]) {
      const name = schema.$ref.split("#/$defs/")[1];
      assert.ok(planViewSchema.$defs[name], name);
    }
    assert.ok(aggregate.includes(`./openapi-plans-v1.json#/paths/${path.replaceAll("/", "~1")}`));
  }
});
test("preview conflicts retain nullable coordinates and never disguise a rule as a relation", () => {
  const validate = planView("previewConflictsResponse");
  const page = structuredClone(viewShapes.responses.previewConflictsResponse);
  for (const item of [
    { code: "CONTAINMENT_CYCLE", slotId: null, relationId: null, ruleId: null },
    { code: "ENTITY_COUNT", slotId: null, relationId: null, ruleId: "invented-rule" },
    { code: "CONTAINMENT_PARENT_MISSING", slotId: null, relationId: "contains", ruleId: null },
  ]) { page.items = [item]; assert.equal(validate(page), true, JSON.stringify(validate.errors)); }
  for (const item of [
    { code: "ENTITY_COUNT", slotId: null, relationId: "invented-rule", ruleId: null },
    { code: "CONTAINMENT_CYCLE", slotId: "", relationId: "", ruleId: null },
    { code: "FUTURE_UNKNOWN", slotId: null, relationId: null, ruleId: null },
    { code: "RELATION_CARDINALITY", slotId: "shape-one", relationId: "uses" },
  ]) { page.items = [item]; assert.equal(validate(page), false); }
});

const supervisorConfig = ajv.compile(read('../schemas/guarded-supervisor-config-v1.schema.json'));
test('supervisor configuration is closed shape only, never runtime qualification', () => {
  const fixture = read('../fixtures/guarded-supervisor-v1/configuration.json');
  assert.equal(supervisorConfig(fixture), true);
  for (const mutate of [x => x.password = 'invented', x => x.clients[0].arguments = [], x => x.clients[0].orapkiSha256 = 'a'.repeat(64), x => x.destinations[0].trustMaterial.key = 'invented', x => x.destinations[0].expectedPhysicalIdentity.dbid = '1', x => x.destinations[0].port = 1.5]) {
    const value = structuredClone(fixture); mutate(value); assert.equal(supervisorConfig(value), false);
  }
});

test("binding values and full location pages use closed distinct states and bounded coordinates", () => {
  for (const [name, mutate] of [
    ["bindingsRequest", s => { s.offset = 257; }],
    ["bindingLocationsRequest", s => { s.offset = 2147483648; }],
    ["bindingLocationsRequest", s => { delete s.completeDocumentDisclosure; }],
    ["bindingLocationsRequest", s => { s.completeDocumentDisclosure = false; }],
    ["bindingLocationsRequest", s => { s.completeDocumentDisclosure = "true"; }],
    ["bindingsResponse", s => { s.items[0].current = {state: "masked", text: "invented-secret"}; }],
    ["bindingsResponse", s => { s.items[0].target = {state: "unresolved", text: ""}; }],
    ["bindingsResponse", s => { s.items[0].currentLocations = {state: "complete"}; }],
    ["bindingsResponse", s => { s.items[0].targetLocations = {state: "unavailable", total: 0}; }],
    ["bindingsResponse", s => { s.items[0].token += "\n"; }],
    ["bindingLocationsResponse", s => { s.items[0].elementIndex = "01"; }],
    ["bindingLocationsResponse", s => { s.items[0].span.start = -1; }],
    ["bindingLocationsResponse", s => { s.items[0].role = "containment"; }],
  ]) {
    const instance = structuredClone(viewShapes.requests[name] ?? viewShapes.responses[name]);
    mutate(instance); assert.equal(planView(name)(instance), false, name);
  }
  for (const state of ["masked", "absent", "unresolved", "unavailable"]) {
    const instance = structuredClone(viewShapes.responses.bindingsResponse);
    instance.items[0].current = {state}; assert.equal(planView("bindingsResponse")(instance), true);
  }
});

const workspaceV3 = read("../docs/contracts/openapi-workspace-v3.json");
const workspaceV3Defs = JSON.parse(JSON.stringify(workspaceV3.components.schemas)
  .replaceAll("#/components/schemas/", "#/$defs/")
  .replaceAll("../../schemas/definition-inspection-v3.schema.json", "https://environment.studio/schemas/definition-inspection-v3")
  .replaceAll("../../schemas/profile-inspection-v3.schema.json", "https://environment.studio/schemas/profile-inspection-v3"));
// UUID syntax is constrained by the schema's explicit pattern.
const workspaceV3Ajv = new Ajv({ allErrors: true, strict: true, formats: { uuid: true } })
  .addSchema(read("../schemas/definition-inspection-v3.schema.json"))
  .addSchema(read("../schemas/profile-inspection-v3.schema.json"));
const workspaceV3Shape = (name) => workspaceV3Ajv.compile({ $defs: workspaceV3Defs, $ref: `#/$defs/${name}` });
const workspaceV3Mechanisms = { "native-compiler-v3": "1", "xml-path-v1": "1", "xml-span-v1": "1", "generic-graph-v1": "1", "derived-graph-v1": "1" };
test("v3 workspace exposes only ten closed draft/history/publication operations", () => {
  assert.deepEqual(Object.keys(workspaceV3.paths).sort(), ["/api/v3/definitions", "/api/v3/definitions/{objectId}", "/api/v3/definitions/{objectId}/publish", "/api/v3/definitions/{objectId}/revisions/{revision}", "/api/v3/profiles", "/api/v3/profiles/{objectId}", "/api/v3/profiles/{objectId}/publish", "/api/v3/profiles/{objectId}/revisions/{revision}"]);
  assert.deepEqual(Object.entries(workspaceV3.paths).flatMap(([path, item]) => Object.keys(item).filter(key => key !== "parameters").map(method => `${method} ${path}`)).sort(), [
    "get /api/v3/definitions", "get /api/v3/definitions/{objectId}", "get /api/v3/definitions/{objectId}/revisions/{revision}",
    "get /api/v3/profiles", "get /api/v3/profiles/{objectId}", "get /api/v3/profiles/{objectId}/revisions/{revision}", "post /api/v3/definitions/{objectId}/publish", "post /api/v3/profiles/{objectId}/publish", "put /api/v3/definitions/{objectId}", "put /api/v3/profiles/{objectId}",
  ]);
  for (const item of Object.values(workspaceV3.paths)) for (const method of ["get", "put"]) if (item[method]) {
    for (const status of ["403", "413", "429", "503"]) assert.ok(item[method].responses[status]);
  }
  const validate = workspaceV3Shape("SaveDefinition");
  const command = { expectedRevision: "0", requestId: "00000000-0000-4000-8000-000000000031", format: "YAML", source: "invented source é 😀\r\n" };
  assert.equal(validate(command), true, JSON.stringify(validate.errors));
  for (const key of ["owner", "publication", "definition", "projection"]) assert.equal(validate({ ...command, [key]: "invented" }), false);
});
test("v3 workspace historical projections never advertise current ready-to-publish", () => {
  const validate = workspaceV3Shape("DefinitionProjection");
  const model = decimalInspection(read("../fixtures/native-v3/definition.json"));
  const projection = { kind: "incomplete", model, logicalDigest: "a".repeat(64), bindingDigests: Object.fromEntries(model.bindings.map(b => [b.id, "b".repeat(64)])), mechanisms: workspaceV3Mechanisms,
    diagnostics: [{ phase: "publication", code: "MECHANISM_UNQUALIFIED", pointer: "", message: "Unqualified." }] };
  assert.equal(validate(projection), true, JSON.stringify(validate.errors));
  assert.equal(validate({ ...projection, kind: "historical-ready", diagnostics: [] }), true);
  assert.equal(validate({ ...projection, kind: "ready-to-publish", diagnostics: [] }), false);
  assert.equal(validate({ ...projection, kind: "historical-ready" }), false);
  assert.equal(validate({ ...projection, diagnostics: [] }), false);
  assert.equal(validate({ ...projection, diagnostics: [{ ...projection.diagnostics[0], phase: "semantic" }] }), false);
  assert.equal(validate({ ...projection, diagnostics: [{ ...projection.diagnostics[0], code: "unsafe-code" }] }), false);
  assert.equal(validate({ ...projection, model: decimalInspection(read("../fixtures/native-v2/definition.json")) }), false);
});
test("v3 workspace history requires its distinct complete mechanism vector", () => {
  const validate = workspaceV3Shape("Mechanisms");
  assert.equal(validate(workspaceV3Mechanisms), true);
  assert.equal(validate({ ...workspaceV3Mechanisms, "native-compiler-v3": "9".repeat(1024), "xml-child-property-v1": "9" }), true);
  const missing = { ...workspaceV3Mechanisms }; delete missing["derived-graph-v1"];
  assert.equal(validate(missing), false);
  assert.equal(validate({ ...workspaceV3Mechanisms, "native-compiler-v2": "1" }), false);
  for (const bad of ["0", "01", "1\n", "9".repeat(1025), 1]) assert.equal(validate({ ...workspaceV3Mechanisms, "derived-graph-v1": bad }), false);
});
test("v3 workspace metadata is bounded and excludes source or publication authority", () => {
  const validate = workspaceV3Shape("DefinitionList");
  const item = { objectId: "00000000-0000-4000-8000-000000000032", workspaceRevision: "1", nativeId: "invented", nativeRevision: "1", sourceDigest: "c".repeat(64), state: "draft", compilationKind: "incomplete", logicalDigest: "d".repeat(64) };
  assert.equal(validate({ definitions: Array.from({ length: 100 }, () => ({ ...item })) }), true, JSON.stringify(validate.errors));
  assert.equal(validate({ definitions: Array.from({ length: 101 }, () => ({ ...item })) }), false);
  assert.equal(validate({ definitions: [item], canPublish: true }), false);
  assert.equal(validate({ definitions: [{ ...item, source: "invented" }] }), false);
  assert.equal(validate({ definitions: [{ ...item, compilationKind: "ready-to-publish" }] }), false);
});


test("v3 profile drafts require an exact closed definition reference", () => {
  const validate = workspaceV3Shape("SaveProfile");
  const command = { expectedRevision: "0", requestId: "00000000-0000-4000-8000-000000000041", format: "JSON", source: "invented", definition: { objectId: "00000000-0000-4000-8000-000000000042", workspaceRevision: "2" } };
  assert.equal(validate(command), true, JSON.stringify(validate.errors));
  for (const key of ["values", "model", "publication", "owner"]) assert.equal(validate({ ...command, [key]: "invented" }), false);
  for (const definition of [{ ...command.definition, workspaceRevision: "0" }, { ...command.definition, owner: "invented" }, { objectId: command.definition.objectId }]) assert.equal(validate({ ...command, definition }), false);
});
test("v3 profile projections describe physical structure without publication eligibility", () => {
  const validate = workspaceV3Shape("ProfileProjection");
  const projection = { kind: "structurally-valid", model: decimalInspection(read("../fixtures/native-v3/profile.json")), contentDigest: "a".repeat(64), diagnostics: [] };
  assert.equal(validate(projection), true, JSON.stringify(validate.errors));
  for (const kind of ["ready-to-publish", "historical-ready", "incomplete"]) assert.equal(validate({ ...projection, kind }), false);
  assert.equal(validate({ ...projection, model: { ...projection.model, schemaVersion: "2" } }), false);
  assert.equal(validate({ ...projection, contributors: [] }), false);
});
test("v3 profile history preserves publication presence and excludes document policy", () => {
  const validate = workspaceV3Shape("ProfileRevision");
  const value = { objectId: "00000000-0000-4000-8000-000000000043", workspaceRevision: "1", sourceDigest: "b".repeat(64), format: "YAML", source: "invented", compilerVersion: "profile-compiler-v3", schemaVersion: "3", state: "draft", definition: { objectId: "00000000-0000-4000-8000-000000000042", workspaceRevision: "2" }, projection: { kind: "structurally-valid", model: decimalInspection(read("../fixtures/native-v3/profile.json")), contentDigest: "a".repeat(64), diagnostics: [] } };
  assert.equal(validate(value), true, JSON.stringify(validate.errors));
  const publication = { digest: "c".repeat(64), sourceRevision: "1" };
  assert.equal(validate({ ...value, workspaceRevision: "2", state: "published", publication }), true);
  assert.equal(validate({ ...value, publication }), false);
  assert.equal(validate({ ...value, state: "published" }), false);
  assert.equal(validate({ ...value, state: "published", publication: { ...publication, exportPolicies: [] } }), false);
});
test("v3 profile lists are bounded metadata without source or authority", () => {
  const validate = workspaceV3Shape("ProfileList");
  const item = { objectId: "00000000-0000-4000-8000-000000000043", workspaceRevision: "1", nativeId: "invented", nativeRevision: "1", sourceDigest: "b".repeat(64), state: "draft", contentDigest: "a".repeat(64), definition: { objectId: "00000000-0000-4000-8000-000000000042", workspaceRevision: "2" } };
  assert.equal(validate({ profiles: Array.from({ length: 100 }, () => ({ ...item })) }), true);
  assert.equal(validate({ profiles: Array.from({ length: 101 }, () => ({ ...item })) }), false);
  assert.equal(validate({ profiles: [{ ...item, source: "invented" }] }), false);
  assert.equal(validate({ profiles: [item], canPublish: true }), false);
});

test("v3 publication commands are closed and preserve policy duplicates for semantic checking", () => {
  const profile = { expectedRevision: "1", requestId: "00000000-0000-4000-8000-000000000061" };
  const policy = { bindingId: "mock-pg", documentId: "sheet", content: "deny" };
  const definition = { ...profile, exportPolicies: [policy, policy] };
  const checkDefinition = workspaceV3Shape("PublishDefinition"), checkProfile = workspaceV3Shape("PublishProfile");
  assert.equal(checkDefinition(definition), true, JSON.stringify(checkDefinition.errors));
  assert.equal(checkProfile(profile), true, JSON.stringify(checkProfile.errors));
  assert.equal(checkProfile(definition), false);
  assert.equal(checkDefinition(profile), false);
  for (const key of ["model", "source", "format", "owner", "definition", "ready", "computed"]) {
    assert.equal(checkDefinition({ ...definition, [key]: "invented" }), false);
    assert.equal(checkProfile({ ...profile, [key]: "invented" }), false);
  }
  for (const value of ["01", "1\n", "9".repeat(1025), 1]) assert.equal(checkProfile({ ...profile, expectedRevision: value }), false);
  assert.equal(checkDefinition({ ...definition, exportPolicies: Array.from({ length: 20000 }, () => policy) }), true);
  assert.equal(checkDefinition({ ...definition, exportPolicies: Array.from({ length: 20001 }, () => policy) }), false);
  for (const bad of [{ ...policy, extra: true }, { ...policy, content: "allow" }, { ...policy, documentId: "sheet\n" }])
    assert.equal(checkDefinition({ ...definition, exportPolicies: [bad] }), false);
});
test("v3 publication has exactly two POST routes and historical response shapes with all owned failure statuses", () => {
  const posts = Object.entries(workspaceV3.paths).filter(([, methods]) => methods.post).map(([path]) => path).sort();
  assert.deepEqual(posts, ["/api/v3/definitions/{objectId}/publish", "/api/v3/profiles/{objectId}/publish"]);
  for (const [kind, request, response] of [["definitions", "PublishDefinition", "DefinitionRevision"], ["profiles", "PublishProfile", "ProfileRevision"]]) {
    const route = workspaceV3.paths[`/api/v3/${kind}/{objectId}/publish`];
    assert.deepEqual(Object.keys(route).filter((key) => key !== "parameters"), ["post"]);
    assert.equal(route.post.requestBody.content["application/json"].schema.$ref, `#/components/schemas/${request}`);
    assert.equal(route.post.responses["200"].content["application/json"].schema.$ref, `#/components/schemas/${response}`);
    for (const status of ["400", "401", "403", "404", "409", "413", "422", "429", "503"]) assert.ok(route.post.responses[status]);
  }
});

const planV3Api = read("../docs/contracts/openapi-plans-v3.json");
const planV3Defs = JSON.parse(JSON.stringify(planV3Api.components.schemas).replaceAll("#/components/schemas/", "#/$defs/").replaceAll("../../schemas/plan-command-v1.schema.json", "https://environment.studio/schemas/plan-command-v1"));
const planV3Schema = (name) => new Ajv2020({ allErrors: true, strict: true }).addSchema(read("../schemas/plan-command-v1.schema.json")).compile({ $defs: planV3Defs, $ref: `#/$defs/${name}` });
const emptyV3Summary = () => ({
  planId: "00000000-0000-4000-8000-000000000041", revision: "1",
  definition: { objectId: "00000000-0000-4000-8000-000000000042", workspaceRevision: "2" },
  bindingId: "invented.binding", destinationId: "invented.destination",
  currentCounts: { documents: 0, entities: 0, relations: 0 }, targetCounts: { documents: 0, entities: 0, relations: 0 },
  observedDestination: null, inspectionValid: false, targetComplete: false, exportAvailable: false,
  blockers: ["INSPECTION_REQUIRED", "EXPORT_UNAVAILABLE"], currentComputedCounts: null, targetComputedCounts: null,
});
test("v3 summary requires separate bounded computed counts and keeps absent distinct from empty", () => {
  const validate = planV3Schema("PlanSummary");
  const empty = emptyV3Summary();
  assert.equal(validate(empty), true, JSON.stringify(validate.errors));
  const zero = structuredClone(empty); zero.currentComputedCounts = { nodes: 0, memberships: 0, cooccurrences: 0 };
  assert.equal(validate(zero), true, JSON.stringify(validate.errors));
  assert.notDeepEqual(empty, zero);
  for (const field of ["currentComputedCounts", "targetComputedCounts"]) {
    const missing = structuredClone(empty); delete missing[field]; assert.equal(validate(missing), false);
    for (const bad of [{ nodes: -1, memberships: 0, cooccurrences: 0 }, { nodes: 20001, memberships: 0, cooccurrences: 0 },
      { nodes: 0, memberships: 50001, cooccurrences: 0 }, { nodes: 0, memberships: 0, cooccurrences: 50001 },
      { nodes: 0, memberships: 0, cooccurrences: "0" }, { nodes: 0, memberships: 0, cooccurrences: 0, contributors: [] }]) {
      assert.equal(validate({ ...empty, [field]: bad }), false);
    }
  }
  assert.equal(validate({ ...empty, exportAvailable: true }), false);
  assert.equal(validate({ ...empty, values: {} }), false);
});
test("v3 small replies preserve common v1 revision and optional-field wire contracts", () => {
  const legacy = read("../docs/contracts/openapi-plans-v1.json").components.schemas;
  for (const name of ["Uuid", "Id", "Revision", "PublicationRef", "CreatePlan", "ReserveInspection", "Credentials", "Ack", "Operation", "Counts", "ObservedDestination"]) {
    assert.deepEqual(planV3Api.components.schemas[name], legacy[name], name);
  }
  const ack = planV3Schema("Ack");
  const candidate = { planId: emptyV3Summary().planId, revision: "9007199254740993" };
  assert.equal(ack(candidate), true); assert.equal(ack({ ...candidate, revision: 9007199254740992 }), false);
  assert.equal(ack({ ...candidate, operationId: null }), false);
  const operation = planV3Schema("Operation");
  const status = { operationId: "00000000-0000-4000-8000-000000000043", planId: candidate.planId, phase: "cancelled", code: "CANCELLED", cleanup: "in-progress" };
  assert.equal(operation(status), true); assert.equal(operation({ ...status, installedRevision: candidate.revision }), true);
  assert.equal(operation({ ...status, installedRevision: null }), false);
  assert.equal(operation({ ...status, cleanup: "complete-anyway" }), false);
});
test("v3 exposes exactly eleven authenticated routes and documents early empty controller refusals", () => {
  const paths = ["/api/v3/plans", "/api/v3/plans/current", "/api/v3/plans/{planId}", "/api/v3/plans/{planId}/inspections",
    "/api/v3/operations/{operationId}/credentials", "/api/v3/operations/{operationId}", "/api/v3/operations/{operationId}/cancel", "/api/v3/plans/{planId}/commands", "/api/v3/plans/{planId}/materializations", "/api/v3/plans/{planId}/views/documents", "/api/v3/plans/{planId}/views/entities"];
  assert.deepEqual(Object.keys(planV3Api.paths).sort(), paths.sort());
  for (const item of Object.values(planV3Api.paths)) for (const [method, route] of Object.entries(item)) {
    assert.deepEqual(route.security, [{ sessionCookie: [] }]);
    if (method === "post") assert.ok(route.parameters.some((value) => value.name === "X-CSRF-TOKEN" && value.required));
    assert.deepEqual(route.responses["500"], { $ref: "#/components/responses/Error500" });
  }
  for (const response of Object.values(planV3Api.components.responses)) {
    const lengthSchema = response.headers["Content-Length"].schema;
    const length = new Ajv2020({ strict: true }).compile(lengthSchema);
    assert.equal(length(0), true);
    assert.equal(length(Buffer.byteLength('{"code":"MALFORMED_BODY"}')), true, "owned JSON has a nonzero Content-Length");
    assert.equal(length(-1), false); assert.equal(length(1.5), false);
    assert.deepEqual(lengthSchema, { type: "integer", minimum: 0 });
    assert.deepEqual(response.headers["X-Environment-Studio-Code"].schema, { $ref: "#/components/schemas/ControllerErrorCode" });
    assert.match(response.description, /no.body/i);
  }
  const aggregate = readFileSync(new URL("../docs/contracts/openapi.yaml", import.meta.url), "utf8");
  for (const path of paths) assert.ok(aggregate.includes(`./openapi-plans-v3.json#/paths/${path.replaceAll("/", "~1")}`));
});
test("v3 create and one-shot credentials refuse caller-supplied model or admission authority", () => {
  const creation = planV3Schema("CreatePlan");
  const request = { expectedRevision: "0", requestId: "00000000-0000-4000-8000-000000000044", definition: emptyV3Summary().definition, bindingId: "invented.binding", destinationId: "invented.destination" };
  assert.equal(creation(request), true);
  for (const field of ["modelVersion", "owner", "driver", "exportAvailable", "observedXml"]) assert.equal(creation({ ...request, [field]: "invented" }), false);
  const credentials = planV3Schema("Credentials");
  const body = { username: "invented-reader", password: "invented-password" };
  assert.equal(credentials(body), true);
  for (const field of ["requestId", "owner", "modelVersion", "operationId"]) assert.equal(credentials({ ...body, [field]: "invented" }), false);
});


test("v3 semantic commands preserve the existing closed command protocol and owned acknowledgement", () => {
  const path = "/api/v3/plans/{planId}/commands";
  assert.ok(planV3Api.paths[path], "semantic command route is documented");
  const route = planV3Api.paths[path].post;
  const v1 = read("../docs/contracts/openapi-plans-v1.json");
  assert.deepEqual(Object.keys(planV3Api.paths[path]), ["post"]);
  assert.deepEqual(route.requestBody, v1.paths["/api/v1/plans/{planId}/commands"].post.requestBody);
  assert.deepEqual(planV3Api.components.schemas.PlanCommand, v1.components.schemas.PlanCommand);
  assert.equal(route.responses["200"].content["application/json"].schema.$ref, "#/components/schemas/Ack");
  const validate = planCommands;
  const body = { kind: "discard", expectedRevision: "2", requestId: "00000000-0000-4000-8000-000000000051" };
  assert.equal(validate(body), true, JSON.stringify(validate.errors));
  for (const field of ["modelVersion", "owner", "computed", "targetXml", "validation", "exportAvailable"])
    assert.equal(validate({ ...body, [field]: "caller-cannot-select" }), false);
});

test("v3 materialization preserves complete, incomplete and refused states with complete diagnostic references", () => {
  assert.ok(planV3Api.components.schemas.Materialization, "v3 materialization result is declared");
  const validate = planV3Schema("Materialization");
  const complete = { revision: "2", state: "COMPLETE", complete: true, diagnostics: [] };
  const incomplete = { revision: "2", state: "INCOMPLETE", complete: false, diagnostics: ["by-tone", "item/finish"] };
  const refused = { revision: "2", state: "REFUSED", complete: false, diagnostics: ["RESOURCE_LIMIT"] };
  for (const value of [complete, incomplete, refused]) assert.equal(validate(value), true, JSON.stringify(validate.errors));
  for (const value of [ { ...complete, complete: false }, { ...incomplete, complete: true }, { ...refused, complete: true },
    { ...complete, state: "READY" }, { ...complete, revision: 2 }, { ...complete, revision: "02" },
    { ...complete, targetXml: "caller" }, { ...complete, exportAvailable: true }, { ...complete, diagnostics: ["RESOURCE_LIMIT"] } ])
    assert.equal(validate(value), false, JSON.stringify(value));
  const references = Array.from({ length: 256 }, (_, index) => "a".repeat(64)+"/"+("field"+index).padEnd(64,"x"));
  assert.equal(validate({ ...incomplete, revision: "9".repeat(1024), diagnostics: references }), true);
  assert.ok(Buffer.byteLength(JSON.stringify({ ...incomplete, revision: "9".repeat(1024), diagnostics: references })) > 32768);
  assert.equal(validate({ ...incomplete, diagnostics: [...references, "overflow"] }), false);
  assert.equal(validate({ ...incomplete, diagnostics: ["x".repeat(130)] }), false);
});

test("v3 materialization is one authenticated revision-only POST without replay or result authority", () => {
  const path = "/api/v3/plans/{planId}/materializations";
  assert.ok(planV3Api.paths[path], "materialization route is documented");
  assert.deepEqual(Object.keys(planV3Api.paths[path]), ["post"]);
  const route = planV3Api.paths[path].post;
  const v1 = read("../docs/contracts/openapi-plans-v1.json").paths["/api/v1/plans/{planId}/materializations"].post;
  assert.deepEqual(route.requestBody, v1.requestBody);
  assert.deepEqual(route.parameters, v1.parameters);
  assert.equal(route.responses["200"].content["application/json"].schema.$ref, "#/components/schemas/Materialization");
  const schema = read("../schemas/plan-view-v1.schema.json");
  const validate = new Ajv2020({ allErrors: true, strict: true }).addSchema(read("../schemas/plan-command-v1.schema.json")).compile({ ...schema, $ref: "#/$defs/revisionRequest" });
  assert.equal(validate({ revision: "2" }), true);
  for (const field of ["requestId", "modelVersion", "owner", "targetXml", "state", "complete", "diagnostics", "exportAvailable"])
    assert.equal(validate({ revision: "2", [field]: "caller" }), false);
});

test("v3 document and physical entity routes preserve exact v1 request and result shapes", () => {
  const v1 = read("../docs/contracts/openapi-plans-v1.json");
  for (const name of ["documents", "entities"]) {
    const path = `/api/v3/plans/{planId}/views/${name}`;
    assert.ok(planV3Api.paths[path], "physical view route is documented");
    assert.deepEqual(Object.keys(planV3Api.paths[path]), ["post"]);
    const route = planV3Api.paths[path].post;
    const old = v1.paths[`/api/v1/plans/{planId}/views/${name}`].post;
    assert.deepEqual(route.requestBody, old.requestBody);
    assert.deepEqual(route.parameters, old.parameters);
    assert.deepEqual(route.responses["200"], old.responses["200"]);
  }
});

test("physical view schemas keep unavailable inventory, declared masking and closed paging distinct", () => {
  const schema = read("../schemas/plan-view-v1.schema.json");
  const validate = name => new Ajv2020({ allErrors: true, strict: true }).addSchema(read("../schemas/plan-command-v1.schema.json")).compile({ ...schema, $ref: `#/$defs/${name}` });
  const documents = validate("documentsResponse");
  const row = { documentId: "sheet", currentDigest: "a".repeat(64), targetDigest: null, changed: null };
  assert.equal(documents({ revision: "2", documents: [row] }), true);
  assert.equal(documents({ revision: "2", documents: [{ ...row, changed: false }] }), false);
  assert.equal(documents({ revision: "2", documents: [{ ...row, targetDigest: "a".repeat(64), changed: false }] }), true);
  const entities = validate("entitiesResponse");
  const fields = [ { fieldId: "visible", present: true, masked: false, value: "" },
    { fieldId: "hidden", present: true, masked: true, value: null }, { fieldId: "missing", present: false, masked: false, value: null } ];
  const item = { entity: { kind: "existing", handle: "00000000-0000-4000-8000-000000000091" }, typeId: "item", fields };
  const page = { revision: "2", total: 3, offset: 0, nextOffset: 1, items: [item] };
  assert.equal(entities(page), true, JSON.stringify(entities.errors));
  assert.equal(entities({ ...page, offset: 50000, nextOffset: null, items: [] }), true);
  assert.equal(entities({ ...page, items: [{ ...item, entity: { kind: "fresh", slotId: "replacement", typeId: "item" } }] }), true);
  for (const field of [ { ...fields[1], value: "must-not-appear" }, { ...fields[2], value: "must-not-appear" }, { ...fields[0], value: null } ])
    assert.equal(entities({ ...page, items: [{ ...item, fields: [field] }] }), false);
  assert.equal(entities({ ...page, items: [{ ...item, computed: true }] }), false);
  const request = validate("entitiesRequest"); const body = { revision: "2", side: "target", offset: 0, limit: 100 };
  assert.equal(request(body), true);
  for (const value of [{ ...body, side: "computed" }, { ...body, offset: 50001 }, { ...body, limit: 101 },
    { ...body, revealSecrets: true }, { ...body, modelVersion: 3 }, { ...body, completeDocumentDisclosure: true }, { ...body, filter: "caller" }])
    assert.equal(request(value), false);
});
