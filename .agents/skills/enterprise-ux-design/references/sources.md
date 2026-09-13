# Evidence and research starting points

These sources were checked on 2026-09-08. They support specific design and verification decisions; they do not certify a product or replace task research. Verify current APIs, browser behavior, and version-specific requirements when implementing.

| Source | Useful application | Limit |
| --- | --- | --- |
| [W3C WCAG 2.2 quick reference](https://www.w3.org/WAI/WCAG22/quickref/) | Select applicable accessibility criteria, including keyboard, focus, reflow, contrast, and target size. | Requires criterion-specific evidence; a screenshot or scan is not a conformance assessment. |
| [W3C ARIA Authoring Practices: read me first](https://www.w3.org/WAI/ARIA/apg/practices/read-me-first/) | Decide when native semantics suffice and what a custom widget must support. | Pattern examples still need testing in the product's supported environments. |
| [IBM Carbon: data table usage](https://carbondesignsystem.com/components/data-table/usage/) | Evaluate collection density, sorting, selection, and batch-action patterns. | Use when the operator's task warrants a table; do not copy an entire product or assume its domain. |
| [React: Thinking in React](https://react.dev/learn/thinking-in-react) | Decompose components, minimize state, and identify its owner in a React application. | Framework guidance does not establish domain contracts or require React in other projects. |
| [Playwright: visual comparisons](https://playwright.dev/docs/test-snapshots) | Establish reproducible browser captures and maintain rendered regression baselines. | Passing configured comparison thresholds does not prove literal identity or agreement with an external design image. |
| [Nielsen Norman Group: ten usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) | Structure a heuristic review of feedback, consistency, prevention, recovery, and learning effort. | Heuristic evaluation is not empirical user testing or proof that a specific layout will work. |
| [Satisfice: Rapid Software Testing Methodology](https://www.satisfice.com/rapid-testing-methodology) | Guide contextual investigation, explicit reasoning, and adaptable testing heuristics. | The UX charters in this skill are tailored applications, not a prescribed RST checklist or certification. |

Research the unresolved task, then document the implication for this product. Prefer primary sources and official documentation for technical claims. Competitor patterns can reveal alternatives; they are not permission to copy assets, import domain assumptions, fabricate populated screens, or expand scope.
