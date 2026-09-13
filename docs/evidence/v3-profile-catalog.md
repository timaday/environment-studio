# Explicit profile catalogue reads

Base e4a41e5bd4648d6a70e3ef94a1ccedb281263608. Lead-only bounded nonvisual
dependency for the reuse picker: explicit owned list and exact historical revision
selection; no render, automatic selection, publication, preview or plan command.
Drafts are inspectable; stored publication never grants runtime authority.

TDD: initial missing-module run is setup evidence, not a behavior assertion.
After the catalogue load existed, the selection acceptance failed because selected
remained null instead of the returned immutable model. Added exact selection and
closed metadata checks. The first full run had six expected-message mismatches:
existing failureMessage returns explanatory CONFLICT text, not just the code.
Assertions now retain the code without replacing existing error semantics.

Final focused Vitest: **12 PASS**. Checks explicit reads/no writes, empty versus
failed catalogue, explicit retry, foreign/unloaded selection without transport,
six metadata mismatch dimensions, retirement of an in-flight selection on reload,
inactive/reentry clearing and session-required retirement. TypeScript/Biome check
passes after correcting a test cleanup callback lint error; no config change.
Actual commands/logs remain external under es-profile-catalog-{red,green,check}.
Independent fixed source review found no confirmed defect (execution NONE),
manifest4a71b9b3a55ef0510824814fa61c0bdef71b242a387d31ea1069b387b3b3c580;
external report es-profile-catalog-source-review1-20260911.md. Host session expiry
must propagate through the established unmount/disabled/API-owner lifecycle.
This status update changes no reviewed code. Integrated frontend/full required
gates remain pending.
This is mocked client behavior evidence, not HTTP/browser/publication qualification.

Repository material is independently invented wire data only. Catalogue source
and model are read only on explicit actions; no browser or disk persistence added.
