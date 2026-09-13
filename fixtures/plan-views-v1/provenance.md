# Independent planned view shapes

All identifiers, labels, field names, UUIDs, XML text and digest strings in
`shapes.json` were independently invented for D06b3 wire-schema tests. None was
derived from an actual database/application model, private input or credential.

These are decoded JSON shape fixtures, not observations, published profiles,
matched current/target evidence, complete graph qualification or export authority.
The capture `source` string is explicitly a shape-only placeholder; schema tests
cannot establish portable profile acceptance inside that string. Production
capture must use the existing server-owned accepted profile adapter.

The ten validation categories come from the public generic RequiredCheck contract.
Repeated fixed hex digest values are placeholders, never claimed calculated
identity proofs. The large decimal revision checks lossless wire representation.
Masked fields deliberately contain null. Negative mutation canaries are generated
in memory by tests and carry no secrets.
