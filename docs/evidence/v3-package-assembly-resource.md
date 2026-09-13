# Guarded package assembly resource settlement

Status: local correction accepted within bounded PostgreSQL package assembly scope.
This is not independent review, production publication, operational execution or
release readiness.

Base branch: `implementation/v3-package-read-resource-20260911`.
Prior reviewed head for package-read evidence: `6b5e2237be64c27274f06c7952e675a2deb9634c`.

## Behavior and scope

Guarded package assembly must write the exact self-contained archive members and
manifest digests while staying within the same constrained runtime envelope used
for package admission. PostgreSQL assembly now measures and streams large
`payload.json` and `transaction.sql` members instead of materializing both as full
byte arrays during archive writing. Oracle assembly retains the existing bounded
candidate path.

The accepted package keeps the execution JSON tree for manifest embedding, but
canonical payload output is derived from the decoded admitted payload model. This
removes the retained parsed payload tree after schema and mechanical admission.
Canonical JSON string writing uses bounded ranges so large unescaped strings do
not create full substring copies before UTF-8 output.

No schema, SQL semantics, package member names, package inspection authority,
export authority or generated SQL execution behavior changes.

Acceptance examples:

- existing small package assembly remains deterministic and inspectable;
- invalid inputs, cancellation and output failures still refuse without returning
a candidate;
- the manifest records the same member sizes and SHA-256 digests as the bytes
written to the archive;
- PostgreSQL 16.11 package SQL stays unqualified and transactional;
- the large four-owner package assembly probe writes the package under the same
1 GiB container, 65% heap, 1 CPU, 256 PID and no-network envelope that previously
failed at `PACKAGE_ASSEMBLY_BEGIN`.

## RED evidence

The package-read exact image passed package admission, then failed during full
archive assembly. Probe directory:
`/home/tim/.tmp/es-package-assembly-capacity-20260912/`.

- small: exit 0;
- large: exit 3 after approximately 106 seconds;
- Docker reported no kernel OOM kill and the container was removed;
- last product stage: `PACKAGE_ASSEMBLY_BEGIN`;
- large log SHA256:
  `c28c3dbf1344d2fa1137b7bf381b9290d7a28af509042f8696f5aa8a68f8ff6b`.

Intermediate overlays that streamed only SQL or still retained/copy-created the
canonical payload remained failing at `PACKAGE_ASSEMBLY_BEGIN`; their result files
are preserved under `/home/tim/.tmp/es-package-assembly-capacity-overlay-20260912/`
and `/home/tim/.tmp/es-package-assembly-capacity-overlay4-20260912/`.

## Focused verification

Focused server/package tests passed after the final no-copy canonical payload
change:

```sh
/home/tim/.tmp/es-toolchain-20260909/apache-maven-3.9.16/bin/mvn -B -ntp -f backend/pom.xml -pl server -am '-Dtest=DerivedGraphOracleTest,MinimalRuntimeTest,PackageJsonEncodingTest,*Package*Test,TransactionTemplatesTest,Postgres16TemplateTest' test > /home/tim/.tmp/es-package-assembly-focused9-20260912.log 2>&1
```

Result: PASS. The run includes 1 parser smoke test and 50 selected server export
and planning tests. Log: `/home/tim/.tmp/es-package-assembly-focused9-20260912.log`.

## Resource result

The same bounded package assembly probe with the corrected classes first on the
classpath passes both shapes:

- small: 2.080 seconds, exit 0, no OOM, container removed, log SHA256
  `6f13f154119875f9056314fa1ec247935ab9b28ec1ff240e1de358e7344f3d22`;
- large: 167.113 seconds, exit 0, no OOM, container removed, log SHA256
  `d2d35b00169307325e6e96f9da0aa0702ebc9fae244f8168d16760dc5f5b09d8`.

Result file: `/home/tim/.tmp/es-package-assembly-capacity-overlay4-20260912/result.json`.
The probe uses exact image
`sha256:1622d20b50f6292af05b75aedbaa2467aae91af39c929dcc6bd2ceec36ecd1e1`
with current corrected export classes overlaid. It does not prove full HTTP,
native client, database or production export readiness.

## Reviewable probe bundle

The generic probe sources and exact-image run scripts used for the package-read
and package-assembly resource observations are recorded under
`docs/evidence/probes/package-capacity/`. The bundle contains independently
invented mock definitions, observation/publication ports and command metadata
only; full extracted image bundles remain local build artifacts.

## Remaining checks

Committed candidate `87183a176fbd7c4fa41efb3955a6bad05770b511` was built from Git archive with no untracked inputs. Exact OCI runtime build passes:

- runtime Docker build: 316.458 seconds, log SHA256
  `eb16b759e62c75988d2f6aa7ed17265b705cbc4e922c4f25a32d1d95cbd5b082`;
- container smoke: 13.953 seconds, log SHA256
  `45222001bbf6ae154f65907b4a7ae509fbbea3cb9f92222837a341726644261b`;
- supervisor artifact build: 0.696 seconds, log SHA256
  `369f690de2611aee0c9e515fca0547965bc043e21215131ca16b011d46b3aa6e`;
- supervisor checksum verification: 0.005 seconds, log SHA256
  `10ccaf2afb6ca322c5168d5cbe505c10740f3466878271318ceaba06dafd772f`.

Exact image: `sha256:08e31b2a5d5245b753a024a330c28009b13f3926063b92e5aea331cbc0072952`.
Result file: `/home/tim/.tmp/es-package-assembly-oci-20260912/results.json`.

The exact image, with no classpath overlay, passes the same package assembly
capacity probe:

- small: 2.074 seconds, exit 0, no OOM, container removed, log SHA256
  `c3e9b1256958d5c998855846dd935028546d581cd6b8ccf71b72b04edce8fc96`;
- large: 178.107 seconds, exit 0, no OOM, container removed, log SHA256
  `1c6e2bfb259e70b01eb8181967382f9865644a716b37f29b793fabce1fe12f46`.

Result file: `/home/tim/.tmp/es-package-assembly-image-20260912b/result.json`.

Remaining before integration acceptance: independent review if reviewer tokens are
available, then integration into the operator candidate with affected gates.
