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
