# Contributing

Read AGENTS.md and select one build-plan slice. Open a small branch and PR.
Use descriptive commits such as `feat(profiles): preview dependency closure`.
Keep schema/API changes, migration of owned metadata and acceptance examples
together. Never migrate application configuration through this project's CI.

For behavior changes record RED → GREEN → refactor evidence; for documents or
branding, use appropriate review rather than ceremonial tests. Run relevant
gates before review. Review semantic behavior, adverse paths, privacy and UX,
not only test counts. Synthetic examples must be labelled and independent
expected XML reviewed against the stated fixture contract.

Do not paste real CLOBs, SQL exports, addresses or secrets into public issues,
PRs, CI artifacts or AI prompts. Report security issues privately through the
repository owner's existing channel. Licensing for original project code is
not granted by this starter; the owner must choose a license before distribution
under open-source terms. Retain third-party notices when adding dependencies.
