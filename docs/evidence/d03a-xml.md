# D03a XML mechanism evidence — 8 September 2026

The local adapter projects exact XML source and applies bounded attribute,
removal and insertion operations with full target validation. This supports
[lossless XML D03a](../contracts/lossless-xml.md), including G04 conformance and
targeted G06 mutations. It is **not** complete D03, application mapping
qualification, a validated environment plan or export authority.

Traceability: ES-01, ES-04, ES-06, ES-08, ES-09 and ES-16. The public XML contract
was frozen before this implementation. Source base:
`cc354d3445cf81fc2762ea4e7f55d3b656ad4e69`; writer worktree `/tmp/es-d03-xml`.
Only `backend/server/src/main/java/studio/environment/server/xml`, its test
package and this record were changed. No dependency, shared manifest, core
abstraction, HTTP route or Git state change was needed.

## Mechanism and independent data

`LosslessXmlAdapter.project` returns a source-bound `XmlDocument` or safe
refusal. `apply` requires that document, its expected digest and ordered typed
operations. `XmlResult` has only accepted/rejected cases; rejected results have
no document. These internal types carry no application or publication authority.

The bounded lexical scanner records exact UTF-16 spans. A separately configured
JDK SAX parser validates well-formedness, expanded names and decoded values.
Required parser features/properties are set and read back; initialization fails
if hardening is unavailable. DTD/entity access, schemas and XInclude processing
are disabled. Unsupported namespace constructs and XML versions are refused.
Source digesting preserves the supplied Unicode sequence without normalization.

The writer uses one bounded assembly of source slices and edits. Existing
attribute quote styles and surrounding characters remain intact. CR/LF/tab
replacement values use numeric references. Whole-element removal and ordered
insertion support non-root removal, explicit child anchors and self-closing
parent delimiter expansion. Full SAX target validation and intended-outcome
checks precede any accepted result. Standalone insertion namespace meanings are
compared with the resulting subtree; inherited default namespaces cannot silently
change them. Attribute namespaces and values of all surviving original elements
are checked as well.

Every XML string and exact expected string in
[LosslessXmlAdapterTest](../../backend/server/src/test/java/studio/environment/server/xml/LosslessXmlAdapterTest.java)
was invented independently for these generic lexical behaviors. No actual XML,
application vocabulary, schema, database locator or private evidence was read,
renamed or transformed. No standalone XML fixture family was added. The test
class also records this provenance.

## Actual commands and observations

Every writer Maven run used this exact command prefix, mounting only the isolated
writer worktree:

```bash
docker run --rm -v /tmp/es-d03-xml:/workspace -w /workspace \
  maven:3.9.16-eclipse-temurin-21@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8 \
  mvn -B -ntp -f backend/pom.xml
```

| Command suffix / observation | Actual result |
| --- | --- |
| Initial `test`, 14:46:59 UTC | Core 12 passed; server 15 tests with 5 assertion failures and 0 errors against a callable refusal stub. Success/no-op, replacement, removal/insertion, empty-parent expansion and guard tests could not obtain a projection. This was behavior RED, not compilation failure. |
| Initial `test`, 14:51:42 UTC | Core 12 and server 15 passed; 0 failures/errors/skips. |
| Expanded boundary `test`, 14:54:00 UTC | Core passed; server 23 with 1 assertion failure. JAXP's default XML-name limit rejected a 1,001-character name allowed within the documented source bound. |
| Corrected `verify`, 14:55:23 UTC | Core 12 and server 23 passed; 0 failures/errors/skips. Explicit `maxXMLNameLimit=1048576` removed the unintended narrower default, with readback assertion. |
| Final `verify`, 14:57:13 UTC | Core 12 and server 23 passed; 0 failures/errors/skips; both JARs packaged. The server includes 20 XML examples (parameterized cases included) and 3 existing HTTP boundary tests. Elapsed Maven time 40.726 seconds. |
| `python3 scripts/check_repository.py` | Repository integrity: PASS. |
| `git diff --check` | Exit 0. |

Local raw logs remain outside the checkout at `/tmp/es-d03-*.log`; only generic
results are recorded here. No host Maven result or governed HiveGate execution
is claimed. Integration with the separate D01a/D02a candidates needs the lead's
combined verification; this isolated branch does not claim their test counts.

## G04 and focused investigation

Exact independent expected outputs cover comments, CDATA, PI, CRLF, astral
characters, repeated equal attribute values, entity spellings, both quote styles,
empty attributes, whitespace around attributes, ordered sibling insertion,
self-closing expansion and two-document structural building blocks. One test
also uses an independently configured DOM parser as a decoded-value oracle;
DOM is never used to write XML. No-op preserves the entire supplied string.

Boundary experiments exercised exactly 1,048,576 UTF-16 units, depth 128,
20,000 elements, 256 attributes and 100,000 lexical tokens, plus rejection above
each boundary. Lexical tokens count markup/text runs, attributes and references;
the scanner bounds them before SAX can construct semantic projections. Invalid
Unicode, undeclared references, multiple roots, malformed attributes, DTDs,
XInclude, signatures/encryption and XML 1.1 return safe refusals. Full-target
size/depth overflow is also refused without a partial result.

Guard experiments cover stale digests, foreign/forged references, wrong values,
wrong parent/anchor, duplicate replacements, ancestor/descendant removals,
removal/replacement conflicts, insertion into a removed element, namespace
attribute edits and a final operation failure. Explicit namespace aliases and
default resets work; unqualified descendants affected by an inherited default
namespace refuse. No supplied selector, code or external entity executes.

The JAXP name-limit finding was the investigation's material surprise. The
bounded parser configuration was corrected and retested rather than silently
narrowing XML support. Batch conflict checks use ancestry sets and outcome
position mapping uses an ordered sweep; neither repeatedly scans every patch
for every element.

These focused experiments support RST-08/RST-09 and lexical non-interference.
They are not a complete RST campaign, formal proof, hostile JVM sandbox,
process-level memory profile or packet-capture audit. A passing parser oracle
can share implementation defects with another JDK parser; exact independent
text oracles provide additional evidence.

## G06 targeted guard mutations

Each mutant was applied independently to `/tmp/es-d03-mutant-proof`, a separate
copy containing only the public backend and schemas. The pinned Docker/Maven
command above was run with that scratch directory mounted at `/workspace` and
suffix `test`. No mutation touched the candidate worktree. All five runs exited
1 with behavioral assertion failures and no compilation errors:

| Removed or weakened mechanism | Detecting observation |
| --- | --- |
| Exact source digest check | Stale-source test received accepted instead of rejected. |
| Expected attribute value check | Wrong-value test received accepted instead of rejected; final-operation refusal code also changed. |
| Source-bound element reference check | Foreign-reference removal received accepted instead of rejected. |
| Post-state semantic outcome validation | Inherited-default namespace tests received accepted instead of rejected. |
| Ampersand escaping | Independent replacement-output tests received rejection instead of the required accepted exact target. |

After every run the scratch Java sources were restored before the next mutation;
a final byte comparison against all original source files returned `True`.
`/tmp/es-d03-mutation-results.json` records exact test failures and restoration;
individual logs are `/tmp/es-d03-mutant-*.log`. No survivors or compile-failure
pseudo-kills occurred. This is targeted manual G06 evidence, not a PIT coverage
percentage or a claim that every possible guard mutation was explored.

## Independent-review correction

The reviewer found that `List.copyOf(edits)` preceded the existing 100,000-edit
budget check. A virtual oversized list could therefore trigger allocation before
refusal. A new regression list reports `Integer.MAX_VALUE` size and throws an
assertion on element access or either array-copy method; it does not allocate
an enormous backing array.

The pinned command with suffix `test` produced behavior RED at 15:03:04 UTC:
core 12 passed, server 24 tests had one assertion failure, zero errors, because
the list's `toArray` was called before refusal. The public adapter now checks
the bound before copying; the internal batch check remains defense in depth.
The pinned command with suffix `verify` then passed at 15:03:53 UTC: core 12 and
server 24 tests, zero failures/errors/skips, elapsed 29.564 seconds.

Only `LosslessXmlAdapter.java`, `LosslessXmlAdapterTest.java` and this evidence
record changed after the original review manifest. The previous five guard
mutation results remain evidence for their unchanged guards; the new list-bound
regression separately observes the missing guard and its correction. Review
rework took approximately two minutes, with no contract blocking or new scope.

## Remaining boundaries and timing

No arbitrary XPath, text-field replacement, namespace editing, binding registry,
read-set orchestration, document-set transaction, DB adapter, SQL writer or
export policy was added. Operations across two documents are tested as separate
building blocks, not an atomic multi-document planner. Scope-level document
count, total bytes/time and execution cancellation need the later orchestrator.
No actual private application or database family was qualified.

Writer effort was approximately 17 minutes including boundary investigation and
manual mutation work. Contract blocking time was zero. The JAXP correction and
batch-scan refactoring/reverification took about two minutes. Lead integration
and independent-review rework are separate; no speed-up claim is supported.
The lead owns staged-content review, final combined gates and any publication.
