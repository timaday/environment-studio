"""Independent native-framing oracle for the invented preview token fixture."""
import hashlib
import json
from pathlib import Path


def frame(value):
    if isinstance(value, str):
        raw = value.encode("utf-8")
        return b"S" + str(len(raw)).encode("ascii") + b":" + raw
    if isinstance(value, list):
        return b"A" + str(len(value)).encode("ascii") + b":" + b"".join(map(frame, value))
    if isinstance(value, dict):
        keys = sorted(value, key=lambda key: key.encode("utf-8"))
        return b"O" + str(len(keys)).encode("ascii") + b":" + b"".join(frame(key) + frame(value[key]) for key in keys)
    raise ValueError("unsupported independent fixture type")


def digest(domain, value):
    return hashlib.sha256(domain.encode("ascii") + b"\0" + frame(value)).hexdigest()


if __name__ == "__main__":
    fixture = json.loads(Path(__file__).with_name("preview-digest.json").read_text())
    pins = fixture["pins"]
    assert digest("ES-PLAN-ROOTS-1", pins["selectedRoots"]) == pins["rootsDigest"]
    assert digest("ES-PLAN-CLOSURE-1", fixture["closure"]) == pins["closureDigest"]
    assert digest("ES-PLAN-COMPOSITION-PREVIEW-1", pins) == fixture["previewDigest"]
    print("Independent preview framing: PASS " + fixture["previewDigest"])
