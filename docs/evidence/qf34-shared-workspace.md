# QF-0003/0004 — versioned shared workspace adapter

The internal adapter now joins the shared lifecycle's explicit v2/v3 publication
ports. Original owner, reference, publication, checked model and policy pins pass
to the correct delegate. V3 profile lookup reconstructs the selected definition
from all retained pins before fresh qualification. It cannot replace selected
metadata with a newer lookup or cache earlier publication authority.

V2 objects and bytes remain unchanged. Wrong versions, null/wrong-reference
results and original qualification failures refuse without fallback or writes.
The actual v3 compiler still returns MECHANISM_UNQUALIFIED. This adapter does not
register hosted runtime availability or promote stored history into current
publication authority.

## Fixed review and actual checks

Author four-file manifest SHA
`3c243f2bb40069a35d4c79b288cbe74c9ebd7ff51f950ad570ac7428e818820d`;
base `eedcd11` plus the corrected profile composition files. Independent report SHA
`65f617aaa9d2579a996fbef2c464d88a91151a49d51b9ffb70f3cd82fb2964bf`.
Added independent test SHA
`dac1f12e8ea1580ee15e6fbd74a75d92393c3fe9e682c2cdd71fbefdbaee5cd2`.

Actual Maven3.9.16/JDK21.0.12 in isolated candidates:

- Initial RED:1 assertion,0 errors at the default unsupported v3 definition port;
  15 controls pass. First green16; extended compatibility/adverse suite20 pass.
  Test-only legacy profile setup was refined to actual separately compiled and
  captured v2 content; no production change or setup failure was hidden.
- Author full verification:1,236 pass (293 core,7 parser,672 server,264 supervisor),
  0 failures/errors/skips; assembly/hostile distribution pass,23:23:39BST.
- Independent18 pass (5 core,1 parser,12 server), including actual owned invented
  SQLite history and all nine original v3 lookup controls. Forged selected binding
  digests refuse. A previously qualified selection refuses subsequent reads after
  the same compiler port again returns actual MECHANISM_UNQUALIFIED. Repeated
  refusals leave revision/replay counts unchanged.
- Three author mutations remove selected publication/policy/reference checks;
  one independent mutation substitutes fresh metadata for the selected definition.
  All compile and fail assertions with0 errors; restored suites pass. No review
  setup failure occurred.

The combined twenty-two-file source manifest SHA
`d62ca60122c24c7fbf212f66560fe583155a72485d9fecb0f68e2781bc443912`
includes reviewed profile reuse, native launch ownership and all three independent
review additions. Actual `mvn -B -ntp -f backend/pom.xml verify`:1,260 pass
(293 core,7 parser,675 server,285 supervisor),0 failures/errors/skips;
assembly/hostile distribution pass,23:36:32BST. This five-file addition integrates
on `48b2f9e`, whose seventeen source files match the same verified candidate.

All fixtures are independently invented. Business: exact published selection is
preserved. Engineering/security: historical persistence and current qualification
remain separate; the adapter adds no authority cache. QA challenges substitution,
authority loss, wrong versions and unchanged v2 delegation. Runtime wiring, complete
operator paths, combined resources and release/deployment evidence remain open.
