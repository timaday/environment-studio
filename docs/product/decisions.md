# Decision register and source reconciliation

| Decision | Authority | Consequence |
| --- | --- | --- |
| Environment Studio name and Midnight theme | User | Use approved SVG assets and shared tokens |
| XML consistently stored across many records | User | Complete inventory and per-document lossless edits are fundamental |
| Oracle and PostgreSQL | User | Separate adapters and qualification matrices; no silent storage conversion |
| Java + React | User preference adopted | Java 21 core, Spring Boot adapter, TypeScript React UI |
| Definitions do not know application concepts | Later user correction | Supersedes fixed server/service/webapp hierarchy in the Full Specification |
| All or part of profiles reusable | Later user correction | Supersedes earlier full-profile-only MVP boundary |
| Schema upload like OpenAPI | Later user direction | Upload-first native contract with explicit semantics; OpenAPI separately documents HTTP |
| Server IDs vary by environment | User | Value bindings, not portable identity |
| Flyway DML does not touch managed config | User | No ownership clash; no need to rewrite existing migration practice |
| CI publishes GHCR image; HiveForge deploys it | Latest user direction | Supersedes workstation-only packaging; hosted security is a prerequisite to real DB access |
| Single container, one replica initially | Engineering proposal | One Java process serves React; session memory cannot be shared across replicas |
| Runtime demo until DB/auth/writer qualification | Starter safety boundary | Image boot is useful, but does not enable unimplemented operations |

Historical inputs: Environment_Studio_Full_Specification.docx;
Environment_Studio_Mathematical_Modelling_Research.docx;
Environment_Studio_Schema_Upload_Research.docx; and the earlier three-solutions
research. Their conclusions are distilled here; they contain proposals, not
execution evidence. No private original documents are copied into this public
repository. User amendments above win wherever older prose disagrees.

## Unresolved application facts — never guess

Actual table/column/key locators; XML namespaces and element identity; profile
portability policy; legal structural edits/templates; ownership and reference
scope; case/whitespace/empty semantics; embedded secret export classification;
post-clone destination witness; triggers/sequences/reload effects; database and
client versions; maximum dataset; whether A/B need coordinated execution; and
the user's actual HiveForge import format, network and identity integration.

Use `docs/delivery/intake.md` to collect these facts. Missing facts block the
associated operation, not unrelated work on the scaffold or UI.
