# Implementation and evidence

## Component architecture and real operations

Extend the actual application; do not create a parallel demo that bypasses its contracts. Translate the journey and functionality map into components, state ownership, use cases, and external adapters using the repository's established architecture.

- Keep rendering, interaction state, domain decisions, and I/O responsibilities distinct. Use typed inputs, events, and results. Put authority in the layer responsible for it.
- Use narrow interfaces and composition. Components should not fetch unrelated data, duplicate derived state, or accumulate flags for unrelated behaviors. Apply SOLID to concrete change risks; do not require an abstraction layer for every element.
- Use semantic HTML, real text, native controls where suitable, licensed assets, and responsive layout. A screenshot with invisible hotspots is not an implementation.
- Verify the effect promised by each action: a save persists to the declared store; an export yields a valid artifact; validation evaluates the relevant current inputs; filtering operates over its stated scope. A local draft is valid only when the UI accurately describes its persistence.
- Use actual pending/error/partial/unknown outcomes. Do not ship mock adapters, sample-data fallbacks, success timers, or buttons that merely change their labels while implying a completed operation.
- When a required backend or permission is missing, complete authorized integration work or report the exact dependency. Do not call the feature done because the layout exists.

Apply each SOLID principle to an actual responsibility or change boundary:

| Principle | Practical application |
| --- | --- |
| Single responsibility | Give a component or module a coherent reason to change; separate presentation from unrelated persistence and domain policy. |
| Open/closed | Use composition, slots, or established extension points for proven variations; avoid a new universal framework for hypothetical needs. |
| Liskov substitution | Keep variants and adapters compatible with their stated input, event, result, and behavioral contracts. A replacement must not silently change authority or success semantics. |
| Interface segregation | Give consumers only the props, events, and capabilities they need rather than a broad product-wide interface. |
| Dependency inversion | Depend on application/domain contracts at appropriate boundaries; let external adapters implement them without making domain rules depend on UI or transport details. |

## Responsive and accessible behavior

Choose breakpoints from content and task needs, then verify the supported size range and intermediate widths. Preserve required actions, labels, focus, context, and reading order. Use a bounded, labeled scroll region for genuinely two-dimensional content such as a large comparison table or code pane; do not force ordinary page content into horizontal scrolling.

Follow the existing accessibility target. If none exists, propose WCAG 2.2 AA as the design and implementation target. Use native semantics first; custom widgets need explicit keyboard, focus, and assistive-technology behavior. Verify relevant contrast, zoom/reflow, target size, visible focus, status/error announcements, reduced motion, and theme states. Neither a visual inspection nor an automated scan establishes complete conformance.

Use real operator task sessions when participants and authorization are available. Otherwise perform and label a heuristic review or cognitive walkthrough. Do not fabricate user quotes, completion rates, timings, or research participants.

## Evidence gates

| Area | Evidence appropriate to the change |
| --- | --- |
| Component behavior | Meaningful input, state, event, and accessibility checks for the changed responsibility |
| Contract and authority | Verified integration results, error handling, and persistence/permissions where relevant |
| Journey | Actual browser completion and recovery for the affected tasks |
| Visual fidelity | Original approved reference, raw captures, controlled conditions, differences, and explicit status |
| Accessibility | Relevant automated results plus keyboard/focus and other applicable manual checks |
| Performance | Realistic collection and interaction behavior where density or response time is a concrete risk |
| Privacy and operations | Relevant data exposure, session, logging, and failure behavior under the project's rules |

Use the existing test framework and CI gates. For meaningful new behavior or a regression, write the expected contract as a test, observe the relevant failure, implement it, then refactor. Do not add a parallel stack or tests that only mirror the implementation. Broaden testing only for a concrete remaining risk or required gate.

Independently invented fixtures may be used in isolated tests when the project's policy permits them. Keep them out of approval images, normal product routes, deployment data, logs presented as real activity, and fallback behavior. Passing tests with fixtures does not prove an external integration works against actual data or that operators find the design usable.

## RST review

Rapid Software Testing uses investigation and judgment to find important problems; a checklist is a starting point, not proof of quality. Choose focused exploratory charters based on the change:

- **Lost context:** move between steps, inspect detail, go back, refresh, cancel, or resume. Look for silent loss, changed selection, and an unclear next action.
- **Misleading completion:** interrupt requests, delay responses, deny access, or create partial outcomes. Look for false success, duplicate work, and unsafe retry.
- **Difficult content:** inspect supported extremes in length, count, nesting, locale, empty values, and unexpected ordering without inventing production content.
- **Alternative access:** complete the task with keyboard, zoom, narrow layout, reduced motion, and applicable assistive technology.
- **Authority and concurrency:** change permissions or relevant data during a task. Look for stale conclusions and actions whose preconditions no longer hold.
- **Visual oracle:** change fonts, device pixel ratio, loading state, or reference revision. Check whether the comparison would detect an important error or merely approve a new baseline.

Record mission, build/reference revision, setup, oracle, observations, uncertainty, follow-up, and stopping reason. Distinguish a confirmed defect from a risk, an assumption, or a preference. Never claim tests or user research that were not performed.

Use a bounded independent review when authorized and useful. Give the reviewer an identified revision and concrete question; avoid duplicated edits and fictional agreement. Self-review remains useful but must be described honestly.

## Completion record

Link the affected journeys to approved image revisions, functionality contracts, components, implementation changes, and verification evidence. Report supported viewports/states and concrete limits. Keep these statuses separate:

- Design approved.
- Functionality implemented and verified.
- Accessibility reviewed to the stated scope.
- Visual fidelity verified with the stated result.
- Deployment performed, pending, or outside scope.

Do not report a feature complete while a required control is decorative, a real dependency is replaced by a mock, an important journey is untested, or an unapproved visual difference remains. Continue independent work when one dependency is blocked; make the remaining blocker specific and reviewable.
