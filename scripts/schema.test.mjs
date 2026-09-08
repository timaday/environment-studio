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
