# Package capacity probe evidence-harness correction

Status: local evidence-harness correction. This settles reviewer findings
TEST-QA-010 and TEST-QA-011 as test-oracle defects. No product runtime behavior,
SQL template, package bytes, OCI image or release-readiness claim changes.

Base: `be945e06db43ee930c552f7aa63120585ab92764` in the PostgreSQL 16.11 package
route worktree.

## Findings and disposition

- TEST-QA-010 accepted: the exact-image admission and assembly probes could record
  `timedOut=true` and still satisfy their final success assertion if the container
  later exited with code 0.
- TEST-QA-011 accepted: failed `docker inspect` left `containerExit`, `oomKilled`
  and `running` absent; the prior assertion treated absent `oomKilled` as success.
- ASM-QA-C01 accepted as contract reconciliation: `guarded-package-v1.md` now states
  the actual immutable admitted-input boundary used by `GuardedPackageAssembler`.
  No runtime output defect was demonstrated by the review.

## Correction

Both `docs/evidence/probes/package-capacity/run_exact_image_admission.py` and
`run_exact_image_assembly.py` now initialize `timedOut=false`, record Docker inspect
exit status, and require every run to have:

- process exit 0;
- container exit 0;
- Docker inspect exit 0;
- `OOMKilled == false`;
- `Running == false`;
- successful container removal;
- `timedOut == false`.

`docs/contracts/guarded-package-v1.md` now describes assembler behavior as accepting
only mechanically admitted immutable inputs, computing canonical payload metrics,
rebinding the accepted input to the exact emitted payload digest, and generating SQL
from that rebound execution context.

## Verification

A new Python regression control executes both probe scripts under mocked Docker and
clock behavior:

```sh
python3 -m unittest scripts/test_package_capacity_probe_scripts.py
```

Result: PASS. Three tests cover two scripts: normal controls still pass, timeout
with process exit 0 fails, and missing Docker inspect state fails. Finished
2026-09-12 14:43 Europe/London.

Repository-content checks remain required before committing this correction.
