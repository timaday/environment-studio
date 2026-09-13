import { expect, it } from "vitest";
import { captureSummary } from "./captureSummary";

const source = (revision: string, label = 'A 42 and \\"quoted\\" label') =>
  `{"schemaVersion":"3","id":"mock","revision":${revision},"logicalDefinitionDigest":"${"a".repeat(64)}","entities":[{"id":"one","type":"item","label":"${label}","requiredInputs":[]}],"relations":[]}`;
it("retains the exact declared revision and string content without numeric rounding", () => {
  const revision = "9".repeat(1024);
  expect(captureSummary(source(revision))).toMatchObject({
    revision,
    entities: [{ label: 'A 42 and "quoted" label' }],
  });
});
it.each(["0", "-1", "1.5", "1e3", '"1"'])("rejects noncanonical native revision %s", (revision) => {
  expect(() => captureSummary(source(revision))).toThrow();
});
it("does not normalize a numeric label or another extra numeric field into valid content", () => {
  expect(() =>
    captureSummary(source("1").replace('"label":"A 42 and \\"quoted\\" label"', '"label":123')),
  ).toThrow();
  expect(() =>
    captureSummary(source("1").replace('"relations":[]', '"relations":[],"extra":5')),
  ).toThrow();
});
