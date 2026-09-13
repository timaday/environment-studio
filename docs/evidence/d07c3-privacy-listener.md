# D07c3 private listener prerequisite

9 September 2026. Independent invented local processes and paths only. This
five-file candidate is based on `909c458575cd4c07e3993b51e28ea644c4a99784`.
It implements native socket ownership, not process identity, image admission,
ancestry, privacy establishment or a Java process coordinator. Runtime admission,
the launcher and the empty qualified registry remain unchanged.

The caller supplies a previously admitted, stable parent directory descriptor and
canonical absolute UTF-8 path. The primitive repeats owner/mode, CLOEXEC,
component no-follow and final inode/device checks. Native entropy creates a fresh
0700 child and 0600 `control.sock`; the complete socket path is at most 103 bytes.
Pinned `fchmodat2(AT_EMPTY_PATH)` corrects restrictive inherited umasks without
changing the process umask or following a substituted leaf. Missing support
refuses without a pathname chmod fallback.

A crucial caller precondition remains unqualified: a trusted admitted namespace
owner must guarantee exclusive mutation throughout the listener lifetime. UID and
0700 permissions do not establish this against hostile same-UID processes. Linux
has no conditional unlink of a pinned inode. Identity checks preserve detected
substitutions and latch inconclusive cleanup, but are not an atomic defense
against a malicious namespace owner. This primitive does not resolve that runtime
qualification gate. Linux 6.8 primary source supports the distinction:
[fchmodat2](https://github.com/torvalds/linux/blob/v6.8/fs/open.c) supports
`AT_EMPTY_PATH`; [unlinkat](https://github.com/torvalds/linux/blob/v6.8/fs/namei.c)
accepts only its removal flag, without an expected-inode argument.

The serialized native owner retains accepted nonblocking CLOEXEC sockets.
Generation/ordinal tokens permit borrowing only within native C; they do not
transfer close ownership or become Java descriptors. Width 1–16 limits live
accepted owners and requests the listen backlog separately. Neither a backlog nor
this primitive certifies the combined queued-plus-accepted protocol pending
bound. A launch retains at most 64 accept tombstones. Peer/wire interpretation is
absent. Only signalling the borrowed cancellation eventfd may be concurrent.

Startup uses the original absolute monotonic deadline with at most ten seconds
remaining. Cleanup uses its own already-running deadline; invalid/expired cleanup
still closes owned descriptors and remains inconclusive. Cancellation does not
prevent close attempts. Descriptor slots are consumed once, repeated cleanup
preserves tombstones, and uncertain close is never retried against reused numbers.

## Actual checks

Pinned Maven 3.9.16, Java 21 and system GCC 13.3.0 were used in the isolated
archive. Tests compile into private 0700 `/tmp` directories; no binaries or
credentials are added to the checkout.

- Initial refusing implementation: **2 assertion failures, 0 errors** in the two
  listener tests (`es-privacy-listener-red-20260909.log`).
- First implementation compile attempt failed on three misleading-indentation
  warnings. This was setup failure, not behavior RED. After correction, the nine
  selected reactor cases passed.
- Expanded adverse run: **1 assertion failure, 0 errors** among eight listener
  tests. A rejected path read incorrectly reopened completed cleanup state, so a
  later invalid budget erased COMPLETE. Preserving the closed state fixed it;
  all 15 selected reactor cases passed.
- Final focused run: **19 passed**: 12 listener, 4 core, 1 parser, 2 server.
- Full `mvn -B -ntp -f backend/pom.xml verify`: **655 passed**, comprising 161 core,
  7 parser, 365 server and 122 supervisor, with zero failures/errors/skips.
  Assembly and hostile-launch checks passed. Log:
  `/home/tim/.tmp/es-privacy-listener-full-20260909.log`.
- `python3 scripts/check_repository.py`: PASS.

The twelve listener tests include actual AF_UNIX child connection and exact
invented bytes, owner/mode/descriptor flags, full 103-byte path, excessive path and
width refusal, symlink parent refusal, restrictive umask, unavailable pinned chmod,
borrowed cancellation, substituted socket preservation, real nonblocking queue
saturation, live accepted-owner bound, 64 sequential accept tombstones, stale token
refusal, expired startup/cleanup, completed-close preservation and simulated
close uncertainty with descriptor-number reuse. Test-only teardown can remove
known invented fixture leaves after runtime cleanup reports inconclusive; this
is not a runtime cleanup retry or a claim of confirmed cleanup.

An external credential-free filesystem control additionally demonstrated pinned
chmod on a renamed socket while leaving the replacement regular file unchanged,
and actual rejection of empty-path unlink. Its owned source is outside the repo
at `/tmp/es-listener-fs-brhvbns3/probe.py`.

Six final isolated mutations were killed by assertions: erase the completed-close
tombstone, wrong pinned directory mode, omit live accepted capacity, change the
cancellation result, omit namespace checks, and erase sticky close uncertainty.
No compile failure counted. An earlier cancellation-ignoring mutation timed out
and was killed by the external runner; it is explicitly not assertion-killed.
Results: `/home/tim/.tmp/es-privacy-listener-mutants-20260909.log`.

## Remaining scope

These local development results include the independent fixed-candidate review
and integration below. No hostile same-UID namespace admission, remote/network
filesystem behavior, combined queue coordinator, JNI descriptor bridge, registered
Java Process ownership, executable/loader closure, inherited-FD audit across the
full launcher, or credential-bearing native client was qualified. No database
authentication or global runtime/kernel setting was changed.

## Independent review and integration

The lead reviewed the fixed four source/test files and five-file candidate.
Source manifest SHA-256:
`151797374f77a1d0ca6938caa5a3645b6f838ad5a41c61983bf15c42af63fb59`;
candidate manifest SHA-256:
`c030e40e51342a169f9dc4fe4366f980b65c87776e7039ad79938b8bfd236bbb`.
No blocking finding was identified within this private prerequisite's explicit
namespace and caller assumptions.

Three independent, unwrapped native controls passed: cross-object token isolation,
borrowed cancellation and descriptor reuse; changed parent permissions with
preserved namespace and sticky inconclusive cleanup; malformed UTF-8 refusal
before owned allocation. Source and results remain outside the checkout at
`/home/tim/.tmp/es-listener-independent-20260909.c` and its `.log` sibling.
Compilation used system GCC with C17, warnings-as-errors, PIE, full RELRO,
stack protection and FORTIFY3. Only owned invented temporary paths were used.

The fixed candidate was integrated into a fresh archive of
`212fb2fea812190a088533d86d28e145d467c4b4`. Independent Maven verify passed
**655 tests: core161/parser7/server365/supervisor122**, plus assembly and hostile
launch checks. Log: `/home/tim/.tmp/es-listener-integrated-full-20260909.log`.
These results do not include the isolated labels or output-transfer candidates.
Runtime/native admission remains disabled and the namespace/coordinator gaps
above remain open.
