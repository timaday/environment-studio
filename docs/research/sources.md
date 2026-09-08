# Research basis and applicability

Reviewed 8 September 2026. These primary sources support design choices; none
qualifies this application's XML family, database scripts or HiveForge setup.

| Source | Applied decision / limit |
| --- | --- |
| [Cockburn: Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture) | Isolate application from UI/DB adapters; proportionate modular monolith |
| [React: Thinking in React](https://react.dev/learn/thinking-in-react) | Component hierarchy, minimal state, derived projections |
| [ArchUnit guide](https://www.archunit.org/userguide/html/000_Index.html) | Executable dependency boundaries |
| [Playwright best practices](https://playwright.dev/docs/best-practices) | User-visible behavior, isolated browser checks, resilient locators |
| [PIT Maven guide](https://pitest.org/quickstart/maven/) | Mutation investigation for high-risk authority/patch/guard code |
| [Satisfice HTSM](https://www.satisfice.com/download/heuristic-test-strategy-model) | RST charters consider product factors, risks, techniques and context |
| [OWASP XXE prevention](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html) | Qualified parser hardening, no remote resolution |
| [W3C XML Infoset](https://www.w3.org/TR/2004/REC-xml-infoset-20040204/) | Semantic parse does not preserve every lexical detail; qualify a lossless writer |
| [W3C XSD 1.1](https://www.w3.org/TR/xmlschema11-1/) | Import supported schema constraints; XML shape does not supply missing app semantics |
| [JSON Schema annotations](https://json-schema.org/understanding-json-schema/reference/annotations) | Default annotation does not authorize target-value insertion |
| [Eclipse Emfatic](https://eclipse.dev/emfatic/) | Fixed modelling vocabulary with user-defined domain entities |
| [JSON Forms](https://jsonforms.io/docs/) | Separate data constraints and presentation; forms do not replace the planner |
| [WCAG 2.2](https://www.w3.org/TR/WCAG22/) | Keyboard, focus, errors and contrast targets need task-specific validation |
| [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html) | 4.1.1 supports Java 21; compatibility still tested in CI |
| [Maven release history](https://maven.apache.org/docs/history.html) | Pin maintained 3.9.16 baseline |
| [Vite guide](https://vite.dev/guide/) | Pin Node/dependencies compatible with selected Vite release |
| [GitHub Docker publication](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images) | GITHUB_TOKEN GHCR publication and least permissions |
| [Docker attestations](https://docs.docker.com/build/ci/github-actions/attestations/) | Attach SBOM/provenance; never pass secrets as build arguments |

Competitor/metamodel patterns from the prior schema research: Atlassian Assets
user-defined types/references, Eclipse Ecore and JSON Forms. Borrow explicit
schema navigation and progressive disclosure, not their built-in domain rules.
OpenAPI is an upload/documentation analogy, not the configuration semantic model.

A public search did not identify the user's HiveForge deployment contract;
several unrelated projects share that name. No undocumented manifest, webhook
or API was invented. OCI compatibility and actual platform deployment remain
separate evidence items.
