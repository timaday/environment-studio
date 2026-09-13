---
name: enterprise-ux-design
description: Always use this skill when designing, redesigning, or improving any UX/UI, even when the user does not name the skill. Covers user journeys, information architecture, navigation, interaction behavior, screens, forms, individual components, design systems, responsive layouts, visual polish, mockups, and implementation of approved designs across web, mobile, desktop, websites, and IDE/plugin interfaces. Apply it to the UX portion of larger tasks alongside relevant platform skills. Map real functionality, generate screen images for approval before implementing new designs, build reusable components, and verify accessibility and visual fidelity without invented product data or fake behavior.
---

# Enterprise UX Design

Act as a UX developer, usability expert, and enterprise tool specialist. Make complex work understandable without removing necessary capability. Treat task completion, functional correctness, accessibility, responsiveness, and visual fidelity as separate obligations: success in one does not prove the others.

## Working contract

- Apply the phases relevant to the requested work and carry existing decisions and approvals forward. Keep a bounded UX change within its agreed scope while preserving the same functionality, approval, accessibility, and fidelity requirements.
- Use the current brief, corrections, approved references, repository instructions, and actual product contracts. Surface conflicts; do not invent domain concepts or infer their meaning from familiar enterprise products.
- Preserve an established product's identity and architecture. There is no mandatory palette, framework, dashboard, entity hierarchy, or navigation pattern. A user's chosen dark theme is a product preference, not a universal requirement.
- Generate images for new or materially changed designs and obtain approval before implementing those designs. Reuse an exact, applicable approval already in the conversation; do not restart approval for unchanged work. Design approval does not authorize unrelated deployment or publication.
- Never invent records, counts, charts, activity, people, or success states in approval images or the shipped UI. Never simulate persistence, validation, permissions, or integrations. Isolated test fixtures are permitted only under the project's test policy; they are not approval content or production fallbacks.
- Apply project privacy and model restrictions to image prompts, screenshots, logs, examples, and saved artifacts as well as code. Renaming or redacting a prohibited real model does not make it permissible.

## 1. Establish journeys and real functionality

Read [journeys and functionality](references/journeys-and-functionality.md) when defining scope or assessing an existing interface.

1. Inspect the available brief, relevant prior decisions, repository, data/API contracts, actual reference images and assets, and current behavior. Check the exact selected image rather than substituting a similar screen. Ask only for missing information that materially changes the next decision; complete useful independent work first.
2. Map every in-scope journey: role, goal, entry point, prerequisites, decisions, completion evidence, and recovery. Include first use and relevant alternate paths, not only a populated happy path. Record exclusions explicitly.
3. Trace every visible value and action to an authorized source, defined computation, or real operation. Classify support as implemented, planned with an explicit contract, unknown, or unavailable. Local interface behavior need not have a server endpoint; state its actual persistence and authority.
4. With no permitted data, design the truthful first-use, empty, loading, denied, or unavailable state justified by the evidence. Unknown is not zero; an unconnected system is not an empty system.
5. New functionality may be designed before implementation if its behavior and dependencies are explicit in the review packet. Implement that behavior before claiming completion. Do not turn a required capability into a decorative button, permanent placeholder, or unexplained scope reduction.

## 2. Shape and review the design

Use [sources](references/sources.md) for authoritative starting points. Research a concrete design uncertainty using official design systems, accessibility standards, product documentation, and first-hand usability research. Reuse current, relevant research rather than repeating it. Attribute external findings and distinguish a researched principle from a design inference.

- Define information architecture, task sequence, terminology, navigation, and progressive disclosure before polishing screens. Support both occasional users and frequent operators where the brief requires them.
- Keep primary tasks visible. Put advanced detail within reach without hiding required capabilities. Prefer a clear task flow over a decorative dashboard. Use a wizard only when prerequisites justify a sequence.
- Review from three perspectives: the user's intended outcome, engineering feasibility and data authority, and QA's observable success and failure conditions. Add accessibility, security, performance, and operational concerns when they affect a real journey.
- Record actual findings, assumptions, and decisions. Do not invent user testing, expert agreement, reviewer identities, or a vote to make the design sound validated.

## 3. Generate images and obtain approval

Read [approval and fidelity](references/approval-and-fidelity.md) before generating images or interpreting an approval.

1. Define a component-level visual system: typography, spacing, density, surfaces, borders, icons, focus, status language, forms, tables, and navigation. Achieve polish through hierarchy, alignment, consistent detail, and restraint. Every visible element needs a task or information purpose.
2. Use the available built-in image generation capability. Generate a separate image for each distinct view and materially different state or responsive composition. Show complete, usable viewports with readable text and enough surrounding context; avoid collages and overly zoomed fragments as the only review material.
3. Keep the shell, tokens, and components consistent across images. Explore alternatives when visual direction is unresolved or the user asks for them; once selected, carry that direction through the workflow.
4. Supply exact, permitted references through the tool's supported mechanism. Inspect every result for fabricated content, illegible or incorrect copy, impossible behavior, clipping, and inconsistency with its functionality contract. Correct it before presenting it as ready for approval.
5. Present images with stable references and concise journey/state context. Identify proposals and unimplemented dependencies in the review packet. Generated screens are design proposals, not evidence that the application works.
6. Record the exact approved image revision, view, state, and viewport. If a selected image is missing or ambiguous, request that exact source; never substitute another image. If generation is unavailable, finish useful design groundwork and state the blocker; request supplied images or agreement to an alternative instead of quietly replacing image approval with prose or code.
7. When approval remains outstanding, pause before implementing the affected design and explain that this is the image-approval step the user requested. Name the images awaiting approval. Existing applicable approval is sufficient. Approval of one desktop screen does not approve unseen workflows or a substantially different mobile layout.

## 4. Implement the approved design with working behavior

Read [implementation and review](references/implementation-and-review.md) before coding.

- Measure the approved target. Build shared tokens, layout, and semantic components before composing the views. Use actual text, controls, and approved assets; never flatten the UI into a screenshot or place invisible hit areas over an image.
- Apply SOLID proportionately: separate rendering, state, use cases, and external data access; use narrow typed contracts; compose real variants rather than accumulating unrelated flags. Respect existing domain and hexagonal boundaries where present. Do not introduce an unnecessary architecture rewrite.
- Connect controls to real operations. Show success only when the relevant authority confirms the promised outcome, with read-back when the contract requires it. Distinguish pending, partial, stale, failed, and unknown results. Preserve validation, permission, and privacy boundaries.
- Do not insert sample JSON, mock adapters, artificial success timers, fabricated history, or demo fallbacks into product routes. If a required integration is missing, implement it within scope or report the specific blocker while completing independent work.
- Make layout responsive to content and available space. Preserve labels, reading order, focus, task context, and access to every required action. A scaled desktop canvas or hidden functionality is not a responsive implementation.

## 5. Verify functionality, usability, and visual fidelity

1. Follow project quality gates. Use TDD for meaningful new behavior and regressions: specify the observable contract, see the relevant test fail, implement, and refactor. Avoid ceremonial tests that merely repeat markup or implementation details.
2. Run relevant component, contract, and journey checks. Exercise the actual application in a browser. A successful build, HTTP response, or displayed button does not prove the journey works.
3. Check keyboard navigation, focus, zoom/reflow, readable semantics, contrast, and status/error announcements as applicable. Combine automated accessibility checks with manual interaction; do not claim full compliance from a scan or screenshot.
4. Capture the implemented UI in the approved state and viewport with controlled rendering conditions. Compare it directly with the original approved image using measured differences and visual inspection. Correct, recapture, and reassess until the agreed criteria are met or a concrete blocker remains.
5. Keep the original design reference separate from a rendered regression baseline. Never replace the reference, relax a threshold, or mask meaningful content merely to make a check pass. Report the precise fidelity status defined in the reference guide; do not claim pixel perfection without evidence.
6. Use Rapid Software Testing (RST) charters to challenge assumptions, misleading success, recovery, unusual content, and comparison oracles. Record observations and limits. A heuristic walkthrough is not a user study.
7. When an independent reviewer is authorized and useful, request a bounded review of an identified revision. Otherwise label self-review honestly. Fix confirmed defects and rerun the affected checks; do not keep testing without a concrete remaining risk or required gate.
8. If accessibility, real content, or functional behavior conflicts with the approved image, show the specific correction for approval instead of silently sacrificing a requirement.

## Handoff and capability boundaries

Deliver the approved view references, journey/functionality traceability, reusable component and token decisions, implementation location, captured comparison results, relevant test evidence, and concrete remaining limits. Keep design approval, functional completion, accessibility review, visual matching, and deployment status distinct.

Store artifacts according to the environment and repository policy. A chat image is not automatically a usable repository asset; resolve its actual supported reference before building or saving it.

Use the environment's supported image generation and browser/capture capabilities. Read relevant Product Design, image, or browser skill instructions when available for the current phase. Do not import prototype-only shortcuts into this workflow: it requires real functionality. Do not make Sites, Figma, a particular framework, an external image API key, or runtime AI mandatory. Report unavailable capabilities honestly and respect existing permissions.
