# ADR 0001 — proportionate modular monolith

Status: accepted for the starter, 8 September 2026.

Context: a short delivery window, deterministic cross-document transformations,
two DB engines, React visibility and independent external SQL execution.

Decision: Java 21, Maven modules `core` and `server`, Spring Boot 4.1.1 at the
boundary, React/TypeScript/Vite, one OCI image. Keep core algorithms independent
of external frameworks. Use exact npm lockfile and pinned Java dependencies.
Current compatibility still requires CI evidence; versions are not a test result.

Consequences: low deployment complexity, reusable core tests, independent DB
capability qualification. Initial hosting has one replica and memory-bound
observations. Authenticated multi-user ownership precedes real database access.
No microservices, runtime LLM, Kafka, graph DB or general solver in the MVP.

Alternatives rejected for the first slice: untyped string substitution (cannot
preserve meaning/closure); full EMF/constraint-solver workbench (unnecessary
initial complexity); Flyway-owned environment data (different ownership);
client-side authoritative transformation (bypasses backend gates).
