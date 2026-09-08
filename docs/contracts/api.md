# HTTP contract strategy

`openapi.yaml` describes implemented capability, health, hosted-session and owned
v1 definition draft routes. PUT `/api/v1/definitions/{objectId}` and the owned
list/current/history GET routes follow [definition workspace](definition-workspace.md).
They require a configured private hosted workspace; no publication is implemented.
Planned
endpoints below are design contracts; they must be added with examples and
backend tests as each slice is implemented. Never advertise a fictional API.
The planned [native workspace extension](native-workspace-v2.md) has a separate
[closed OpenAPI contract](openapi-workspace-v2.json); it does not yet represent
enabled routes or replace v1 draft history.

| Planned operation | Contract |
| --- | --- |
| POST /api/v2/definitions/{objectId}/publish | Current maintainer authorization, expected revision, ready native result and explicit document policies; v1 remains draft-only |
| POST /api/v1/plans | Definition revision, intended environment, destination reference, no credential |
| POST /api/v1/plans/{id}/inspections | One credential-bearing bounded operation; no automatic submission retry |
| GET /api/v1/operations/{id} | Owner-authorized polling without resubmitting credentials |
| POST /api/v1/plans/{id}/commands | Closed tagged command union, expectedRevision and requestId |
| POST /api/v1/profiles | Allowlisted value-free capture; draft/ready distinct |
| POST /api/v1/plans/{id}/composition-preview | Selected profile IDs/roots, closure, conflicts and impact without mutations |
| GET /api/v1/plans/{id}/documents | Paginated projection; complete scope/counts retained server-side |
| GET /api/v1/plans/{id}/documents/{doc}/comparison | Explicit raw/placeholders/formatted view, mode/redaction/omission metadata |
| POST /api/v1/plans/{id}/validations | Freeze inputs, evaluate all required rules; polling by operation ID |
| POST /api/v1/plans/{id}/review | Bind acknowledgement to exact validated input/artifact intent |
| POST /api/v1/plans/{id}/artifacts | Backend rechecks complete authority; returns immutable package or typed refusal |
| POST /api/v1/plans/{id}/verifications | New credentials and complete read; compares exact target manifest |

Malformed/unknown fields → 400; unauthenticated → 401; unauthorized → 403;
stale revision/request identity collision → 409; valid request with business
refusal → 422; exhausted bounded service capacity → 429/503 with safe code.
Never expose raw exception/SQL/credential payloads. All large identifiers and
opaque numeric DB keys cross JSON as strings. Count pagination is not permission
to validate only visible documents. No execute, shell or bean-evaluation endpoint.
