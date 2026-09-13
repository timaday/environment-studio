# V3 contract input in the container build

The frontend Docker stage now receives `openapi-plans-v3.json`. The file must be
both allowed by `.dockerignore` and named in the stage's COPY list. The existing
schema tests read it; host-only verification had not exercised this build context.
No dependency, runtime resource setting, private-input boundary or application
behavior changes.

At `a1bee1002e093142c0425118febbfa91c24bd758`, actual `docker build --target ui
--progress=plain` fails because `/build/docs/contracts/openapi-plans-v3.json` is
missing. Frontend40 passed before that schema-loader failure. This is a confirmed
packaging defect, not a malformed schema or skipped test success.

After only those two filename additions, the same archive/build target passes
frontend checking,40 component tests,55 schema tests and production build. The
base/npm dependency layers are cached; the changed COPY and full UI check/test/build
step execute afresh. This development UI-stage image is not a runnable release
or a full Java/browser/native/resource qualification.

`es-v3-document-oci-ui-red-20260910.log` SHA`442d98719c3cb781bec2d1ffd40f09dbcfcc7ea6c6b1d0b748899eb7348716f8`.
`es-v3-document-oci-ui-green-20260910.log` SHA`7d54e07a68b2a14811a5f985781e44fb16ea3fb0469f4945c9033b1c9ad96d42`.

The lead reviewed this mechanical two-line input correction and its actual before/
after build. No separate agent review is claimed; agent usage is currently exhausted.
Full runtime image refresh follows on the committed correction. Whole staged
provenance/content guard, Python11, repository and diff checks PASS.
