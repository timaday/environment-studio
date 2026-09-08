# HTTP contract strategy

`openapi.yaml` indexes capability, health, hosted-session, workspace and plan
contracts. Contract presence does not advertise deployment qualification.
PUT `/api/v1/definitions/{objectId}` and the owned list/current/history GET routes
follow [definition workspace](definition-workspace.md). They require a configured
private hosted workspace.
The implemented [native workspace extension](native-workspace-v2.md) has a separate
[closed OpenAPI contract](openapi-workspace-v2.json) for v2 definition/profile
publication and immutable history. It preserves v1 draft history.

The nine initial [hosted plan HTTP routes](hosted-plan-http-v1.md) are implemented:
destination listing, plan creation/current/status, inspection reservation,
one-shot credential submission, operation polling/cancellation and typed commands.
[Hosted plan views](hosted-plan-views-v1.md) specify the subsequent eleven
revision-bound POST routes for materialization, inspection, profile capture/
preview and validation; their implementation evidence is tracked separately in
[delivery progress](../delivery/progress.md). Their exact request/response shapes
are in [plan OpenAPI](openapi-plans-v1.json). They supersede older proposed GET
comparison routes and asynchronous validation polling: validation returns its
bounded result directly. Profile capture returns a value-free portable source;
saving or publishing it is a separate explicit workspace action.

The remaining operations below are planned. Add closed contracts, examples and
backend tests before implementation. The capabilities response remains the source
of advertised availability; installed routes alone do not enable inspection or
export in the browser.

| Planned operation | Contract |
| --- | --- |
| POST /api/v1/plans/{id}/review | Bind acknowledgement to exact validated input/artifact intent |
| POST /api/v1/plans/{id}/artifacts | Backend rechecks complete authority; returns immutable package or typed refusal |
| POST /api/v1/plans/{id}/verifications | New credentials and complete read; compares exact target manifest |

Malformed/unknown fields → 400; unauthenticated → 401; unauthorized → 403;
stale revision/request identity collision → 409; valid request with business
refusal → 422; exhausted bounded service capacity → 429/503 with safe code.
Never expose raw exception/SQL/credential payloads. All large identifiers and
opaque numeric DB keys cross JSON as strings. Count pagination is not permission
to validate only visible documents. No execute, shell or bean-evaluation endpoint.
