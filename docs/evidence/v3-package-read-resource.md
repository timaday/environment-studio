# Package admission read memory correction

Status: local correction accepted within stated resource scope. Lead owns
implementation, checks and self-review. No independent acceptance, production
publication, operational export or release claim is made.

Base: `848e8ab7354a182cb27b051faaeb37d39e7440b2`.

## Behavior and scope

Package admission must reject invalid UTF-8 before JSON syntax errors, enforce
the existing byte, token, depth, string and node limits, preserve JSON/BOM refusal
behavior, and avoid holding a second full decoded UTF-16 copy of the package
payload during readback.

`PackageJson.parse` now validates UTF-8 through an 8 KiB character buffer and then
parses from a fresh strict UTF-8 reader. The existing `PackageJson.utf8` helper is
unchanged for bounded member reads that need a `String`. No schema, SQL template,
XML edit, publication, export-authority or package-membership behavior changes.

Acceptance examples:

- supplementary characters decode correctly across the validation buffer boundary;
- invalid UTF-8 at the end of an otherwise syntactically invalid document returns
  `INVALID_UTF8`, preserving whole-input UTF-8 precedence;
- truncated supplementary UTF-8 at EOF returns `INVALID_UTF8`;
- a UTF-8 BOM before an otherwise empty object still returns `INVALID_JSON`;
- existing package admission, assembly, payload and PostgreSQL transaction
  template checks remain green;
- the four-owner admission resource probe reads back the large generated package
  under the same 1 GiB container envelope that previously failed.

## Actual RED and focused results

The prior external admission probe starts from exact image
`sha256:c3f8fb2931005f1b4a81c54e34b52583c1620cb1493fb868c1cba072c6e82f67`
and source `200ad5d391467de32909018f5a309b42a3f9662a`. It creates four owners,
verifies each plan, prepares one package payload, independently checks every
canonical byte, reads the package through actual admission, then rechecks the four
plans and cleanup. It uses reviewed mock observation/publication ports and no real
or transformed application material.

Small control passes in 2.084 seconds. The large run fails with Java heap
exhaustion after 93.109 seconds during package admission:
`PACKAGE_ADMISSION_BEGIN` is the last stage. Docker reports no kernel OOM kill;
the container is removed. Source and results:
`/home/tim/.tmp/es-v3-admission-capacity-20260911/`. Large log SHA256:
`85dd5e6d81ee763598c076d2557ecc23b47d39bf19d4c0b3baf60ffc1307d990`.

The added literal compatibility test passes on the prior implementation as a
control, not a claimed RED. The focused corrected run passes 53 selected Java
tests: 3 core, 1 parser and 49 server tests, including package JSON encoding,
package admission, package assembly, payload and PostgreSQL transaction template
checks. Log:
`/home/tim/.tmp/es-package-read-focused-20260911.log`; SHA256:
`1ebdcfedc6ee853ee7d61eeb363568148234d36818ac6e386c674dad528bd8dc`.

## Resource result

The same admission probe with only the corrected `PackageJson` class first on the
classpath passes:

- small: 2.077 seconds, exit 0, no OOM, container removed, log SHA256
  `11e76f20d14d2ce10d77711e9d0ec6ca1907e7c7965d580bed2d08d9a4023203`;
- large: 158.104 seconds, exit 0, no OOM, container removed, log SHA256
  `6949bdf57e78c7a256ba944dba7ecce1631f296044cc332049eeb0e36dc821dd`.

The large run reaches `PACKAGE_ADMISSION_VERIFIED`, reports
`PAYLOAD_BYTES=67121255 qualified=false exactCanonical=true`, rechecks the four
plans, and completes final cleanup. Result:
`/home/tim/.tmp/es-v3-admission-capacity-streaming-read-20260911/result.json`;
bundle hashes are unchanged.

## Exact candidate result

Source `551ddc2aca9431af00b79f4d62e21c8bc530f5f6` was built from Git archive
with no untracked inputs. Full OCI runtime build passes, including the Docker
Maven run, container smoke, supervisor artifact build and 29 supervisor checksum
verification. Local image:
`sha256:011646bb41ac6b3572d39e02859755bbd7031e4b26b7d94e8d4e8dc9590e72f9`.
Result: `/home/tim/.tmp/es-package-read-oci-20260912/results.json`; SHA256
`1a05681957b26ecfa0eabb7f21f7ecc611ec9c86281b1b392b1c622dcc777b5d`.
Build log SHA256:
`a2d96974d22797693247514609b0f43c7730e2a91e9474fcc0f13d8cb2dbd97f`.

The exact packaged image, with no classpath overlay, passes the same admission
resource probe:

- small: 2.091 seconds, exit 0, no OOM, container removed, log SHA256
  `e76e2fb6cb10da0c1e48dd6ae228b6519997f1856424390ea522942ea729a8d4`;
- large: 158.112 seconds, exit 0, no OOM, container removed, log SHA256
  `084a71e726584909580ff6d25ebb6da7e6b5f331926be26bd5a438e7d6e0db24`.

The exact-image large run reaches `PACKAGE_ADMISSION_VERIFIED`, reports
`PAYLOAD_BYTES=67121255 qualified=false exactCanonical=true`, rechecks all four
plans and completes final cleanup. Result:
`/home/tim/.tmp/es-package-read-image-admission2-20260912/result.json`; SHA256
`5bed23e118a7ed339b788653fc3efa7c9d9c02332feb7a186f4412ce86eb0432`.

An earlier exact-image probe artifact failed before product behavior because its
harness omitted `/probe/execution-template.json`; that preserved setup failure is
`/home/tim/.tmp/es-package-read-image-admission-20260912/result.json`.

## Qualification limits

The exact image and overlay probes use 1 GiB total memory, one CPU, 256 PIDs, the
image's 65% heap, no swap, no network and the unchanged 300-second deadline.
Observation/publication/policies are mock ports. Native client behavior, HTTP
wire buffers, distinct source storage, full package assembly admission and
operational export remain separate open qualifications.
