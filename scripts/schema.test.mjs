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
