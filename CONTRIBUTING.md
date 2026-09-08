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

Follow [the repository content boundary](docs/product/repository-content-policy.md).
No real database/application model belongs in code, docs, prompts, issues, PRs,
CI artifacts or images, even with names or values removed. Fixtures must be
invented independently for mock databases and registered in their provenance
manifest. Keep actual private review inputs outside the checkout/build context.
Stage reviewed eligible changes and run `python3 scripts/check_repository_content.py`
before committing or uploading; inspect the complete diff because this limited
guard cannot judge semantic provenance. Use the [Q feedback handoff](docs/agents/amazon-q-feedback.md)
for generic findings. Separate application configuration versioning is out of scope.

Report security issues privately through the repository owner's existing channel.
Licensing for original project code is
not granted by this starter; the owner must choose a license before distribution
under open-source terms. Retain third-party notices when adding dependencies.
