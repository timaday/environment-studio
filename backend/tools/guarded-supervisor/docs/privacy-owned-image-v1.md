# Root-owned executable inspection prerequisite

Extend the existing single es_launch owner with the reviewed trusted-file,
executable association, hash and structural ELF mechanisms. This is an internal
direct dynamic ELF root prerequisite. It does not reduce the advertised client
matrix or qualify scripts, mapped bytes, loader closure, privacy or execution.
There is no memory descriptor, CHALLENGE, production JNI or new coordinator.

## Private native surface and authority

Add to privacy-launch.h:

```c
typedef struct {
 unsigned char path[4096];
 uint32_t path_length;
 unsigned char expected_sha256[32];
} es_launch_image_record;
es_launch_result es_launch_image(es_launch *,
 const es_launch_image_record *,es_elf_layout *out);
```

The record is immutable native installation metadata from a trusted caller,
never a PREPARE field, peer pathname, Java option or caller-supplied runtime
capability. A record alone does not establish installation admission. Accept
path_length1..4095, no embedded NUL and a NUL at path[path_length]. Existing
es_file path/trust rules remain authoritative. Do not inspect bytes beyond the
declared path when deciding its identity. Copy bounded record data into call-owned
scratch before filesystem work and wipe it afterward. Output contains only the
existing structural layout; no descriptor or persistent pathname is returned.

Validate null, length and owner/input/output overlap before mutation or output
wiping. Reject all overlaps without corrupting any owner/input. Wipe distinct
safe output on every refusal. Valid pointer storage/lifetime remains the native
caller's responsibility. Invalid preflight may return INVALID without latching a
fresh or unentered operation, consistent with existing private entry points.

Only the bound receiver may call once after CORRELATED and successful launcher
disarm. Check existing original owner guard; claim users and the existing active
primitive slot under the mutex. Wrong receiver/phase, duplicate entry or concurrent
primitive refuses PROTOCOL and latches failure. All file/crypto/proc work runs
outside the bookkeeping mutex. No owner or initialized subordinate may be copied.

## One hash owner and original controls

Keep one lazily initialized es_hash in stable es_launch storage. Open it only for
this stage using the existing connection.peer's original cancellation descriptor
and absolute startup deadline, equal to owner.startup_deadline. Do not substitute
the longer owner.operation_deadline. The peer and owner must still agree with their
original scope. Never renew a deadline, reset counters, create a second hash owner,
or allow this operation to restore a failed owner. Initialized failed hash state
must still be closed conclusively by original launch settlement.

Open a call-owned es_file under those same controls and the copied installation
record. Then es_image_check must prove its actual captured executable association
against the expected digest. Only its success permits es_elf_check on that same
stable file owner/hash budget/digest. The existing ELF primitive proves structural
metadata only and also accepts static ELF. After its success, this direct dynamic
root stage additionally requires exactly one PT_INTERP and exactly one PT_DYNAMIC
program header, each with nonzero filesz in that already bounded layout. Missing,
duplicate or empty headers refuse PROTOCOL. This is an owner-stage precondition;
it does not change the ELF primitive or inspect interpreter bytes/dynamic tags.
No substitute parser, executable path or weaker trust fallback is allowed. These
headers alone do not qualify interpreter identity, loader closure or runtime.

Successful image association charges3 full hash occurrences; structural ELF
charges2 more. A complete stage charges5, retaining existing512 occurrences,
2GiB aggregate bytes and512MiB per-file caps. These limits all apply together;
neither file size alone nor an unused occurrence grants extra byte capacity.
Partial failures retain actual charged work. No test setup may renew real startup
time to make maximum counts pass. Earlier raw hash tests do not establish this
composition's allocation/thread/cleanup qualification.

Always close the call-owned file once before returning, including partial open
failure. Preserve an earlier operational result while separately recording any
file/primitive/hash cleanup uncertainty in owner.inconclusive. If no prior failure
exists, an inconclusive file close returns CLEANUP. Existing primitive result
semantics are unchanged: a primitive that already reports CLEANUP retains it.
Map FILE_TRUST, IMAGE_IDENTITY and ELF_IDENTITY to launch IDENTITY; ELF_FORMAT to
PROTOCOL; hash FILE to IDENTITY, hash CRYPTO to IO; matching platform/resource/
cancel/deadline/invalid/IO/cleanup names map directly. Latch the first operational
failure through the existing owner. Never clear uncertainty because another
cleanup later succeeds.

Original launch settlement closes initialized hash resources once, before closing
its cancellation descriptor, even if another subordinate cleanup was inconclusive.
File cleanup remains inside the active image call. Concurrent launch close waits
against the original shortened cleanup deadline and cannot close hash/connection/
eventfd while inspection/file-close is active. All holders retain storage until
joined; synchronous I/O/crypto cleanup may outlive its caller budget. Late output
after cancellation, close, expiry or another latched failure is wiped and refused.
Final publication uses the original owner guard under mutex and leaves phase
CORRELATED on success. Image evidence and separately sampled maps are non-atomic.

## Acceptance and limits

Use independently invented owned FORK roots and exact trusted mock installation
metadata. Meaningful RED reaches the image-stage scaffold after actual root
correlation. Use an actual dynamic ELF held before its main marker; assert exact
ELF metadata independently and five charged passes, no challenge and unchanged
inherited output. Test a test-only JNI/PrivacyLaunchOwner path as well as the
bounded C fixtures before claiming that composition is covered.

Same bytes/different inode, wrong digest, writable/symlink/substituted installation,
unsupported/static ELF, dead/changed peer and wrong scope/receiver refuse. Test
507 prior hash occurrences plus five complete passes versus508 plus this stage;
the latter must stop at the existing bound with no reset or late success. Such
counter setup is explicitly a synthetic boundary control, not proof that512 actual
large objects fit the original deadline. Test null/overlaps before allocation,
once-only entry, partial crypto initialization, cancellation/expiry/death across
primitive boundaries and final file close, held-call concurrent/shortened close,
sticky independent cleanup, exact descriptor-number reuse and hash cleanup on a
different allowed settlement thread. Preserve old launch/maps/FORK/JNI test oracles.

No raw process paths, layouts, addresses, mappings or configuration appear in test
output; only fixed assertion identifiers/capability outcomes. All installed mock
files/children stay in owned external0700 directories. No new dependency choice,
privilege, ptrace/dumpability fallback or global configuration change is authorized
by this contract. Use the already selected OpenSSL binding for these primitives.

Trusted installation, constructor/IFUNC/thread/exec/dlopen behavior, one mm/exec
generation, mapped-byte/relocation/RELRO/vDSO/JIT policy, production JNI/token
lifetime, parent suppression and whole-runtime memory/client qualification remain
required. A future retained memory descriptor must close conclusively before
CHALLENGE; suppression is not revocation. Do not promote this evidence to runtime
admission or an atomic combined image/maps snapshot.
