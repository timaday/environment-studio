# Frontend guidance

Read docs/ux/design-system.md. Build typed components with accessible names,
keyboard equivalents, visible loading/error/empty states and shared tokens.
React derives views from authoritative server revisions; never authorize export
from client state. Do not store credentials/raw XML in browser storage.

Use component behavior tests. Preserve Current/Target labels, document scope,
concrete binding visibility in placeholder mode and disabled-action explanations.
Synthetic demos must remain labelled. Do not claim a click saved a profile or
validated a plan without an implemented backend response.

Do not embed the real model in components, TypeScript data, labels, screenshots,
snapshots or assets. Runtime views are driven by externally supplied definitions;
committed examples and test cases must be independently invented for mock databases.
Even a value-free real profile or renamed XML shape stays outside this repository.
