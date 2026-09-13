# Guarded package and hosted plan contract review — 8 September 2026

These are planned interfaces, not implemented SQL writers, hosted plans or
client qualification. The package schemas and independent framing fixture were
reviewed by a non-authoring agent. The deliberately incomplete mock manifest
cannot authorize execution: its SQL/instruction member digests have no matching
files. All fixture names, XML and identities were independently invented.

One concrete transport admission gap was found: the original schema accepted a
remote host labelled disposable-loopback, and the external supervisor contract
did not explicitly carry forward D04's verified transport requirement. An added
adverse schema assertion failed before the correction. The schema now limits that
label to qualified literal loopback hosts; semantic admission also checks resolved
addresses and independent external configuration. Ordinary execution/hosted export
requires qualified TLS chain/hostname verification before network authentication.
Local forced password prompting is explicitly distinguished from that network
handshake. The reviewer independently verified the correction and closed it.

Pinned Node 24 `npm run test:contracts --prefix frontend` passed all 14 tests.
Three tests exercise package shapes/adverse cases; a fourth new test addresses
readable historical workspace mechanism versions. That historical test first
failed against constant version 1, then passed with canonical positive version
strings while actual new publication remains restricted to supported versions.
The package review independently reran the full schema suite; the workspace
reviewer independently checked the six historical-version assertions.

`python3 fixtures/guarded-package-v1/execution-digest-oracle.py` independently
confirmed program digest
`5b521d695676f345a9e8ec7eceeb09ea5c99cf83b8134179aa89af4b11b21fcc`.
Schema acceptance intentionally leaves cross-field counts, duplicate decoded
identities, hex parity/UTF-8, signed-64-bit range and exact template regeneration
to mandatory semantic validation. No such runtime validation is claimed here.
Final package-review manifest SHA-256:
`9ba730bb3896799a0544475bf102f868c204b9b2aa0153209be7369448903fe7`.

A separate non-authoring review of hosted-plans-v1 identified six contract
clarifications: atomic lease revocation, full entered-value capacity, semantic
revision versus lifecycle state, Existing/Fresh provenance during repeated
composition, physical reservation before credentials and invalidation after a
failed inspection. All six were clarified and independently closed before
implementation assignment. HTTP wire contracts are still required before routes.
Repository integrity, staged content guard and 10 Python script tests passed.
These local checks do not qualify actual application data, TLS or deployment.
