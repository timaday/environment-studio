# Nonvisual paged validation state

Base04a7f940b08c5d096c517002746ac0f03d817091; lead-owned hook, tests and browser
contract supplement. No backend/wire/renderer changes or new design approval.

Explicit validation preserves all ten backend outcomes, physical rules and missing
versus complete-empty computed counts. It does not fetch every computed rule or
authorize export. Summary and page results require matching final full plan
context; changing owner/context/presentation or revalidating retires earlier reads.
Session-required errors retire evidence and retry state. Future rendering must
propagate idle session termination; this hook does not subscribe independently.

One page is retained, default4 rows. Exact requested revision/fingerprint/offset,
page arithmetic and complete count are checked. RESOURCE_LIMIT with actual422
clears prior rows and retains the failed offset/limit for explicit smaller-page
retry down to1. No automatic retry, advance or severity change. Other failures
clear evidence. Invalid coordinates make no IO and leave the previous identified
page; rendering must display that page's request coordinates accurately.

Actual author checks on pinned Node24:
- Six scaffold behavior tests fail (RED1). GREEN1 passes5; missing-target fixture
  incorrectly supplied a complete plan. Corrected fixture supplies matching plan
  context. Actual V3PlanTransport inspection also establishes RESOURCE_LIMIT422,
  not413: corrected wire test RED2 fails1/passes5 before production correction.
- GREEN2 passes25/fails1: unmount assertion incorrectly expected React's last
  rendered test result to update after unmount. Corrected oracle verifies no late
  page and no subsequent IO; no heap erasure is claimed. GREEN3 passes26.
- npm run check passes TypeScript/e2e types and Biome69; npm test passes303frontend
  and59schema; npm run build passes. V8 focused26:83/84lines,61/65branches,
  91/95statements and14/14functions, excluding backend/browser execution.
- Independent fixed three-file source review finds no confirmed defect and
  verifies all source hashes. No independent tests executed.
- External unchanged26-test control compiles/passes. Six distinct compiled faults
  are detected: revision-only final context, ignored generation, missing page-count
  comparison, advanced retry offset, wrong refusal status and retained session
  evidence. One initial count-check mutant fails compilation for an unused import;
  it is not counted. Corrected compiled variant retains an inert import reference
  and fails behavior. All six valid variants testExit1; candidate source unchanged.

Evidence: external es-v3-validation-{red1,red2,green1,green2,green3,check1,tests1,
build1,coverage}-20260911 logs, fixed1 manifest/review and mutation result plus
page-count-unchecked/result-fixed.json. Commands: npm ci/check/test/build and
vitest run src/hosted/useV3Validation.test.ts, including a separate V8 run.

RST probes challenge false empty success, mixed fingerprint/count/offset, unchanged
revision with changed definition, late replies, repeated resource refusal and
session termination. Independently invented wire fixtures include exact large
integer strings and Unicode keys; no real model or source is committed. This
establishes client display state only, not actual XML/backend resource qualification,
atomic snapshot authority, browser rendering, accessibility or visual fidelity.
Remote acceptance, combined gates and release qualification remain required.
