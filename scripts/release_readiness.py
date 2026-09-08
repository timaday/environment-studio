#!/usr/bin/env python3
"""Release evidence bookkeeping gate. Human review must assess the actual evidence."""
import json
import sys
from pathlib import Path
from source_fingerprint import ROOT, fingerprint

REQUIRED = {"definitions", "profile-reuse", "structural-plan", "xml-fidelity", "oracle-client",
            "postgres-client", "privacy-auth", "ux-rst", "mutation", "readback", "hiveforge"}


def assess(manifest, expected_fingerprint, evidence_root):
    blockers = []
    entries = manifest.get("gates", [])
    ids = [entry.get("id") for entry in entries]
    if len(ids) != len(set(ids)) or set(ids) != REQUIRED:
        blockers.append("Missing, duplicate or unknown required gates")
    if manifest.get("sourceFingerprint") != expected_fingerprint:
        blockers.append("Evidence does not match current source fingerprint")
    for entry in entries:
        gate = entry.get("id", "unknown")
        if entry.get("status") != "PASS":
            blockers.append(f"{gate}: {entry.get('status', 'MISSING')}")
            continue
        name = entry.get("evidence", "")
        path = (evidence_root / name).resolve()
        if not name or not path.is_relative_to(evidence_root.resolve()) or not path.is_file():
            blockers.append(f"{gate}: evidence file missing or outside evidence directory")
            continue
        try:
            evidence = json.loads(path.read_text())
        except (ValueError, OSError):
            blockers.append(f"{gate}: unreadable evidence")
            continue
        if evidence.get("gate") != gate or evidence.get("sourceFingerprint") != expected_fingerprint:
            blockers.append(f"{gate}: stale or unrelated evidence")
        if evidence.get("result") != "PASS" or not evidence.get("commands") or not evidence.get("observations"):
            blockers.append(f"{gate}: incomplete execution evidence")
    return blockers


def main():
    try:
        manifest = json.loads((ROOT / "docs/quality/release-evidence.json").read_text())
        blockers = assess(manifest, fingerprint(), ROOT / "docs/evidence")
    except (ValueError, OSError, TypeError, AttributeError) as error:
        print(f"Release BLOCKED: malformed/missing evidence ({type(error).__name__})", file=sys.stderr)
        return 1
    for blocker in blockers:
        print(f"BLOCKED: {blocker}")
    if not blockers:
        print("Evidence bookkeeping passed. Review actual oracles, outcomes and exact image/matrix before release.")
    return 1 if blockers else 0


if __name__ == "__main__":
    raise SystemExit(main())
