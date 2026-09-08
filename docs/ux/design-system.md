# Midnight UX and component contract

The operator flow is **Plan → Inspect → Target → Values → Validate → Review &
export**. Verify is available for a specific exported artifact. Settings →
Definitions is a separate maintainer task, not a mandatory detour every time.
Profiles can be captured/reused at the point of need and managed independently.

| View | Main question / primary action | Required states and capability |
| --- | --- | --- |
| Plans | What am I working on? / Create plan | Empty, resumable, observation expired, blocked, historic artifact |
| Inspect | What is in the copied DB? / Inspect configuration | Purpose, engine/destination, transient credential field, cancel, complete/incomplete scope |
| Target | What structure do I need? / Reuse profile or selection | Whole/partial selection, dependency closure, mapping conflicts, explicit additions/moves/removals |
| Values | What must differ here? / Validate | Required inputs, explicit Keep observed, sensitivity/session-only, affected-document count |
| Validate | What prevents safe export? / Fix issue and revalidate | PASS/FAIL/UNKNOWN/ERROR; global scope counts; no override |
| Review | What exactly changes? / Generate SQL package | Current/target graph/table and many-document diff; exact revision, destination, artifact classification |
| Export result | What was generated? / Download package | Immutable digest, exact-client instructions, external execution; no Deploy/Run SQL button |
| Verify | Does the DB match this artifact? / Fresh readback | Matches/Differs/Unknown separate from execution claim |
| Definitions | What does this application mean? / Upload | Draft Model/Source/Diagnostics, unresolved decisions, mapping test, immutable publication |
| Profiles | Which structure can I reuse? / Use all or choose part | Value-free preview, revision compatibility, required inputs without values |

## Information design

Keep plan name, intended environment, independently observed DB identity,
revision/definition/profile references and global blocker count persistent.
Connection state differs from captured-observation freshness. Filtering or
collapsing content never reduces scope or blocker totals. Start at whole
environment scale. Provide tree/table navigation before a canvas; the same
commands are keyboard-accessible. Domain labels come from the definition.

The Target view uses current and target columns with synchronized selection and
a concise change summary. Reuse opens a selection panel: Choose revision →
select all/parts → inspect required dependencies → resolve conflicts → preview
impact → apply. A dependency badge explains why an item joined the selection.
Unselected siblings remain unchanged; imported labels/values are never guessed.

A document navigator shows all CLOB/text records, changed/unchanged status,
search and complete/filtered counts. XML comparison has three read-only modes:
Raw (exact characters), Placeholders (mapping projection), Formatted (display
projection only). Raw is not silently pretty-printed. Both panes show row/document
identity and observation/target revision. Large previews state omissions and
provide controlled full access under content policy.

**Placeholder mode keeps a binding rail** showing each logical field's concrete
current and target values, change/unresolved status and all mapped locations.
The same placeholder name on both sides must not conceal changed actual IDs.
Mask sensitive values consistently; redaction is visibly distinct from unresolved.
Raw/placeholder/formatted views never alter exported bytes.

## Component system

| Component | Responsibility | Contract |
| --- | --- | --- |
| AppShell + PlanContext | Navigation and persistent scope | No domain logic; responsive landmarks and skip link |
| StepNavigation | Progress and revisit | Real links/buttons, current step announced, blocked action explains why |
| StatusBadge + DiagnosticList | Evidence state | Text/icon + color, stable code, actionable links, live-region updates |
| EntityTree / ComparisonTable | Full model projection | Stable IDs, filters with global totals, keyboard action menu |
| ProfileSelectionPanel | Selection and dependency proposal | Explicit source revision, selectable roots vs required dependencies |
| FieldEditor + BindingRail | Typed target input and value provenance | Label/help/error, sensitivity, no browser persistence |
| DocumentNavigator + XmlComparison | Multi-CLOB side-by-side visibility | Raw/placeholder/formatted modes, line/record context, controlled omission |
| ExportSummary | Exact immutable artifact intent | Backend readiness; explanation beside disabled action |
| DefinitionInspector | Model/source/diagnostics projections | Explicit unresolved semantics; no pre-populated business vocabulary |

Use minimal React state: selections and view preferences local; server revisions,
commands and diagnostics authoritative. Derive counts/diffs from the same data;
do not maintain duplicate mutable current/target models. Reusable components
receive typed data/actions, not a giant generic configuration bag. Keep domain
work outside rendering. Add network error/loading/empty/expiry/conflict states
before claiming an integrated view complete.

## Tokens and accessibility

Midnight #0B1220; surface #121C2E; raised #18243A; border #33425C; text #EDF3FD;
muted #B0BED2; periwinkle #8798FF; teal #43D6C5. Use 4/8 px spacing steps,
10–14 px control/card radii, restrained borders and subtle elevation. Avoid
neon glow and excessive nested cards. Typography: system sans; code monospace.
Approved SVG logo/icon live in frontend/public/brand; outlined paths need no
web font. Never add a fabricated load balancer as a managed entity.

Target WCAG 2.2 AA with measured contrast, visible focus, semantic landmarks,
labelled controls, error association, keyboard equivalents, reduced motion and
adequate target size. No color-only changes; use +/−/Moved/Unresolved labels.
At narrow widths switch from two columns to accessible sequential sections;
retain explicit Current/Target labels. Test zoom and long names. Automated
accessibility tools supplement operator sessions and keyboard testing.

The starter comparison is synthetic and read-only. The design contract covers
future integrated views; no screenshot or demo data constitutes DB evidence.
