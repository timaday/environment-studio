# Invented v3 compatibility fixture

This family extends the independently invented `native-v2` and `profile-v2`
families solely for generic contract tests. No real model, mapping, topology,
profile, database or private input was consulted or transformed. The optional
`finish` field, computed types, derivations and co-occurrence were invented here.
One binding uses a child property; the other retains direct attributes.

`definition.json` is a v3 source-shape example, not a published definition.
`profile.json` contains explicit neutral physical slots only. `digest-oracle.py`
uses the frozen Python framing oracle and the new documented v3 normalized
objects/domains, independently of Java. `expected-digests.json` records its output
for future runtime comparisons. Run `python3 fixtures/native-v3/digest-oracle.py`.
Its controls check ordering/label/revision invariance, semantic sensitivity,
selected-binding dependency changes, malformed Unicode and original v2 goldens.
No XML/DB observation, capture, publication, target computation or export has
been executed by these fixtures. The Java compiler and adapters still need
their own behavior tests against these independent expected values.
