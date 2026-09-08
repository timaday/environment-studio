# Repository content boundary

**No real database or application configuration model belongs in this repo.**
This applies even when all credentials and environment values have been removed.
It includes Git history, branches, issues/PRs, docs, generated examples, snapshots,
CI logs/artifacts, Docker layers and attachments. Do not place private inputs in
an ignored directory inside the checkout or build context.

| Content | Repository policy |
| --- | --- |
| Generic engine, UI, ports and tool-owned API/meta-schema contracts | Allowed; no real application concepts or mappings |
| Independently invented mock database/configuration fixtures | Allowed with explicit provenance and review |
| Real XML/CLOBs, XSDs, DDL, ERDs or database metadata | External only; never commit, quote or embed |
| Real table/column/key mappings, application definitions, profiles or topology | External only, including value-free versions |
| Real values, observations, SQL exports, logs and screenshots | External only; redaction does not authorize inclusion |
| Renamed, masked, encoded, encrypted or structurally faithful copies | Prohibited; still derived from the real model |
| Generic Amazon Q feedback with an independent mock reproduction | Draft requires Tim's information-boundary review before transfer |
| Separate application configuration versioning | External owner/process; creating or managing it is outside this repo's scope |

The product may still load external definitions at runtime and support immutable
profile/definition revisions in its separately governed workspace. These
features do not turn source control into a home for actual application config.
Do not add real mapping bundles to the application image or create a config sync
pipeline. Generic database engine adapters must consume external declarations;
they must not encode a particular real database's tables, hierarchy or rules.

The current `fixtures/demo` family was invented from scratch for mock database
testing and a read-only UI preview. It has no actual database provenance; no mock
DB provisioning is implemented yet. Its manifest records this limited purpose.
New fixture families need the same independent origin and content review. A
manifest declaration does not itself prove that a fixture is synthetic.

Use the [Amazon Q feedback workflow](../agents/amazon-q-feedback.md) for private
review. Keep real evidence in the separately authorized review workspace. Only
generic findings and independently reproducible mock behavior enter this project.

## Practical enforcement and limits

Run `python3 scripts/check_repository_content.py` against staged files before
committing or sending a change to GitHub. The same check runs in CI. It rejects
unregistered database/configuration artifact paths and non-mock fixture metadata;
it does not determine whether arbitrary prose, names, code or images reveal a
real model. Human review of provenance and the whole diff remains necessary.
CI runs after upload and therefore cannot undo a disclosure already committed.

This policy supersedes earlier references to supplying "sanitized application
examples" to the repository. Masked real structures are not mock fixtures. Real
application qualification can occur externally; public results must use generic
capability outcomes and mock evidence without exposing the real model.
