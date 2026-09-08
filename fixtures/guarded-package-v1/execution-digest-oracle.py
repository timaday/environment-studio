#!/usr/bin/env python3
"""Independent native-framing oracle for the invented, non-executable shape fixture."""
from pathlib import Path
import hashlib
import json


def frame(value):
    if isinstance(value, str):
        encoded = value.encode("utf-8", errors="strict")
        return b"S" + str(len(encoded)).encode("ascii") + b":" + encoded
    if type(value) is bool:
        return b"T" if value else b"F"
    if type(value) is int:
        return b"I" + str(value).encode("ascii") + b";"
    if isinstance(value, list):
        return b"A" + str(len(value)).encode("ascii") + b":" + b"".join(map(frame, value))
    if isinstance(value, dict):
        keys = sorted(value, key=lambda key: key.encode("utf-8"))
        return b"O" + str(len(keys)).encode("ascii") + b":" + b"".join(
            frame(key) + frame(value[key]) for key in keys
        )
    raise ValueError("No null or unsupported framing value")


root = Path(__file__).resolve().parent
manifest = json.loads((root / "manifest.json").read_text())
payload_digest = hashlib.sha256((root / "payload.json").read_bytes()).hexdigest()
program_digest = hashlib.sha256(b"ES-EXECUTION-1\0" + frame({
    "execution": manifest["execution"], "payloadDigest": payload_digest,
})).hexdigest()
assert {"payloadDigest": payload_digest, "programDigest": program_digest} == json.loads(
    (root / "expected-digest.json").read_text()
)
assert manifest["programDigest"] == program_digest
assert manifest["members"]["payload.json"]["sha256"] == payload_digest
changed = json.loads(json.dumps(manifest["execution"]))
changed["destination"]["expectedPhysicalIdentity"]["databaseOid"] = "16385"
assert hashlib.sha256(b"ES-EXECUTION-1\0" + frame({
    "execution": changed, "payloadDigest": payload_digest,
})).hexdigest() != program_digest
print(program_digest)
