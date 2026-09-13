# Journeys and functionality

Use these records to connect the product brief, screen design, component design, and implementation. Keep them proportionate to the change. An existing product may already have suitable records; extend those rather than creating a parallel specification.

## Journey inventory

| Journey and role | Goal and entry/prerequisites | Steps and decisions | Observable completion | Failure/interruption and recovery | Views and responsive equivalent | Support evidence |
| --- | --- | --- | --- | --- | --- | --- |

Cover every journey within the agreed scope. Record a reason when a state is not applicable. Prioritize images by task importance, frequency, risk, ambiguity, and distinct layout; this prioritization does not remove less frequent required capabilities.

Consider the states that actually apply:

- First visit and return visits; create, inspect, edit, compare, review, finish, and revisit a result.
- Initial loading, refresh, empty results, absent configuration, denied access, and unavailable dependencies. These states have different meanings.
- Invalid input, incomplete work, partial success, stale data, conflicting changes, timeout, and an outcome that is not yet known.
- Back, cancel, retry, resume, session expiry, refresh, and unsaved changes. Define what survives and what is deliberately discarded.
- Relevant roles, ownership, permission changes, destructive actions, and bulk operations.
- Large collections, long or localized text, nested content, narrow screens, and content that expands after loading.
- Keyboard, touch, zoom, assistive technology, and reduced motion.

Use ordered steps when a real prerequisite exists. Use independent navigation when tasks can be completed independently. Preserve progress and context when users inspect detail or correct a problem.

## Value and action contracts

| Element and view | User purpose | Source or computation contract | Support status | Trigger, validation, and authority | Pending/failure/unknown behavior | Acceptance evidence |
| --- | --- | --- | --- | --- | --- | --- |

Support status is **implemented**, **planned with an explicit contract**, **unknown**, or **unavailable**. The record belongs in design/engineering documentation; do not crowd product screens with implementation metadata unless it helps an operator make a decision.

For each displayed value, identify its meaning, source, freshness, permissions, and scope. For each action, identify inputs, validation, side effects, persistence, cancellation/retry behavior, and the authority that establishes completion. Expand only the fields relevant to the action.

- A count of loaded rows is not automatically a count of all matching records.
- A request being accepted is not automatically a completed operation.
- A stopped spinner is not evidence of success.
- Missing data is not zero, an empty collection, or permission to invent activity.
- A client-side selection or expansion is legitimate local behavior. A local draft is legitimate if its persistence scope is clear. Neither should imply a server-side save.
- A planned service may have a reviewed contract and an honest setup or unavailable state. It remains an implementation dependency until connected and verified.

Never introduce invented product data to make an approval image look populated. Use permitted actual content or the correct honest state. Keep domain-neutral layout annotations outside the product frame. Test fixtures remain isolated from approval images and shipped product data paths.

Resolve missing functionality rather than replacing a required feature with a decorative control or a permanent "coming soon" panel. Record dependencies and blockers in the review packet; do not silently lower the scope.

## Component contracts

| Component and variants | Inputs and source | Outputs/events | State owner | Semantics and focus | Responsive behavior | Behavioral and visual checks |
| --- | --- | --- | --- | --- | --- | --- |

Derive components from repeated responsibilities and interaction patterns. Separate primitives, composed controls, domain views, and page orchestration when the application warrants it. Share real variants; avoid an all-purpose component with unrelated flags or a new configuration framework for one screen.

Keep state minimal, locate it with the responsible owner, and derive values where possible. Distinguish editing state from authoritative saved state. Do not let presentation components decide permissions or domain validity independently of the application contract.

## Information and interaction choices

- Use hierarchy, meaningful labels, consistent placement, and visible feedback to reduce learning effort. Keep essential decisions understandable without prior knowledge of implementation details.
- Use tables when comparing records, trees when hierarchy matters, and graphs when relationships are the task. A canvas, a card grid, or a dashboard is not automatically the best enterprise interface.
- Offer search, filters, sorting, and bulk actions when collection size and user tasks justify them. Make the scope of selection and processing clear. Virtualization does not prove that a server-side operation covers the full collection.
- Use overview and detail to manage density. Keep primary actions easy to find and make advanced controls discoverable. Do not hide errors or required inputs in collapsed sections.
- In comparison views, label both contexts, align comparable content, keep selection and differences understandable, and expose underlying detail when the task requires it. Formatting or placeholder views must not conceal meaningful changes. On narrow screens, stack or switch contexts while preserving labels, differences, and actions.
- Write errors that explain the problem and available recovery without exposing private internals. Do not choose domain defaults that have not been defined by the product contract.
