# Contracts index

| Contract | Source |
| --- | --- |
| Definitions and profiles | [definitions-and-profiles.md](definitions-and-profiles.md), ../../schemas |
| First definition compiler boundary | [definition-compilation.md](definition-compilation.md) |
| Observation, operations and revision authority | [planning.md](planning.md) |
| XML fidelity and guarded SQL | [xml-and-sql.md](xml-and-sql.md) |
| Credentials, data lifetime and hosted access | [security-and-state.md](security-and-state.md) |
| Hosted session implementation boundary | [hosted-session.md](hosted-session.md) |
| HTTP | [api.md](api.md), [openapi.yaml](openapi.yaml) |
| Deployment | [../../deploy/README.md](../../deploy/README.md) |

Schemas are versioned contract drafts with executable shape checks. Passing a
JSON Schema is necessary, not sufficient for semantic validity. Runtime Java
compilation, mapping conformance and DB qualification are separate gates.
