# PostgreSQL 16.11 candidate template

User-selected pilot destination version: PostgreSQL16.11. This is the only
Monday pilot export-client tuple. Existing PostgreSQL18.6 and Oracle behavior and
evidence remain preserved for later delivery but do not make those combinations
available in this pilot.

The closed guarded manifest accepts the additional exact tuple PostgreSQL/text,
server16.11, psql16.11, linux-amd64 and template `postgresql16-text-v1`.
Do not mix server/client/template versions or accept other patch releases by
range. The shared supervisor format remains1; accepting candidate metadata does
not register an installed client as qualified.

Reuse the existing original bytes, destination, bounded locking, row-count and
post-state guards and the existing hex transport. Complete membership is evaluated
over the eligible row set where the XML text column is non-null and non-empty, so
null/empty rows are preserved outside the guarded package scope. The generated
block requires `server_version_num=160011`. PostgreSQL16's constraint
catalog supports the same conservative eligible primary/unique key subset but
has no `conenforced` column and does not represent NOT NULL as constraint rows.
For16 only, permit primary/unique constraints with immediate, validated semantics;
retain NOT NULL column checks. Do not query nonexistent catalog columns, catch
missing-column failures as success or weaken18's enforced-constraint check.
The [PostgreSQL16 catalog contract](https://www.postgresql.org/docs/16/catalog-pg-constraint.html)
is the reference; exact disposable execution remains required.

Acceptance uses actual generated SQL against independently invented mock rows in
an isolated exact16.11 container. Check successful target bytes with unchanged
sibling content, explicit transaction rollback, independent committed readback,
stale original/complete-membership/destination refusal,
and SQL-error transaction abort without partial changes. Challenge relevant
guard removal with independent expected-state witnesses. Record identity, client,
image digest and exact candidate; do not reuse18.6 results as16.11 qualification.

The application never executes these statements. Only the disposable test
workflow executes candidate SQL. Production execution remains through the
separately qualified supervisor. Pilot readiness also requires live definition
publication, plan workflow, PostgreSQL16.11 observation, downloaded archive
inspection, client privacy checks and exact-image deployment evidence.
