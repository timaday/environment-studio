#!/usr/bin/env python3
"""Staged repository boundary guard; semantic provenance still requires review."""

import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

DATA_SUFFIXES = {".xml", ".xsd", ".wsdl", ".sql", ".ddl", ".csv", ".tsv",
                 ".dmp", ".dump", ".db", ".sqlite", ".sqlite3", ".bak"}
TOOL_XML = {"backend/pom.xml", "backend/core/pom.xml", "backend/server/pom.xml",
            "backend/qualified-xml-parser/pom.xml", "backend/tools/guarded-supervisor/pom.xml"}
EXTERNAL_ROOTS = {"private-fixtures", "workspace-data", "exports", "external-config"}
REGULAR_MODES = {"100644", "100755"}
MANIFEST_FIELDS = {"schemaVersion", "purpose", "origin", "artifacts"}


def native_model_shape(name, content):
    """Recognize known native config shapes, not arbitrary private information."""
    suffix = PurePosixPath(name).suffix.lower()
    if suffix not in {".json", ".yaml", ".yml"}:
        return False
    try:
        decoded = content.decode("utf-8")
        if suffix == ".json":
            value = json.loads(decoded)
            keys = set(value) if isinstance(value, dict) else set()
        else:
            # A deliberately limited signal, not a YAML parser or semantic validator.
            keys = set(re.findall(r"^['\"]?([A-Za-z][A-Za-z0-9]*)['\"]?\s*:",
                                  decoded, re.MULTILINE))
    except (ValueError, UnicodeError):
        return False
    return ({"schemaVersion", "entityTypes", "documents"} <= keys
            or {"definitionDigest", "entities", "relations"} <= keys)


def assess(files, modes=None):
    """Return stable error codes/paths without echoing artifact contents."""
    modes = modes or {}
    errors = set()
    registered = set()
    for name in sorted(files):
        path = PurePosixPath(name)
        if path.parts[0] in EXTERNAL_ROOTS:
            errors.add(f"EXTERNAL_WORKSPACE: {name}")
        if modes.get(name, "100644") not in REGULAR_MODES:
            errors.add(f"NON_REGULAR_FILE: {name}")
        if len(path.parts) != 3 or path.parts[0] != "fixtures" or path.name != "provenance.json":
            continue
        try:
            manifest = json.loads(files[name])
        except (ValueError, UnicodeError):
            errors.add(f"INVALID_MOCK_MANIFEST: {name}")
            continue
        if (not isinstance(manifest, dict) or set(manifest) != MANIFEST_FIELDS
                or type(manifest.get("schemaVersion")) is not int
                or manifest["schemaVersion"] != 1
                or manifest.get("purpose") != "MOCK_DATABASE_TESTING"
                or manifest.get("origin") != "INVENTED_FROM_SCRATCH"
                or not isinstance(manifest.get("artifacts"), list)
                or not manifest["artifacts"]):
            errors.add(f"INVALID_MOCK_MANIFEST: {name}")
            continue
        members = set()
        valid = modes.get(name, "100644") in REGULAR_MODES
        for item in manifest["artifacts"]:
            if not isinstance(item, str) or not item or "\\" in item:
                valid = False
                continue
            relative = PurePosixPath(item)
            member = str(path.parent / relative)
            if (relative.is_absolute() or ".." in relative.parts
                    or str(relative) != item or relative.name in {"README.md", "provenance.json"}
                    or member not in files or member in members
                    or modes.get(member, "100644") not in REGULAR_MODES):
                valid = False
            members.add(member)
        if valid:
            registered.update(members)
        else:
            errors.add(f"INVALID_MOCK_MEMBERS: {name}")

    for name, content in sorted(files.items()):
        path = PurePosixPath(name)
        if path.parts[0] == "fixtures":
            if name not in registered and path.name not in {"README.md", "provenance.json"}:
                errors.add(f"UNREGISTERED_MOCK: {name}")
            if path.name == "provenance.json" and len(path.parts) != 3:
                errors.add(f"INVALID_MOCK_MANIFEST_PATH: {name}")
        if path.suffix.lower() in DATA_SUFFIXES and name not in registered and name not in TOOL_XML:
            errors.add(f"UNREGISTERED_DATA_ARTIFACT: {name}")
        if name not in registered and native_model_shape(name, content):
            errors.add(f"EXTERNAL_MODEL_SHAPE: {name}")
    return sorted(errors)


def read_index(root):
    """Read exactly the staged blobs, including staged deletions and additions."""
    def git(*args):
        return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.PIPE)

    files, modes = {}, {}
    for entry in git("ls-files", "--stage", "-z").split(b"\0"):
        if not entry:
            continue
        metadata, encoded_name = entry.split(b"\t", 1)
        mode, object_id, stage = metadata.decode("ascii").split()
        if stage != "0":
            raise ValueError("UNMERGED_INDEX")
        name = encoded_name.decode("utf-8")
        modes[name] = mode
        files[name] = git("cat-file", "blob", object_id) if mode in REGULAR_MODES else b""
    if not files:
        raise ValueError("EMPTY_INDEX")
    return files, modes


def main():
    try:
        files, modes = read_index(Path(__file__).resolve().parents[1])
        errors = assess(files, modes)
    except (OSError, ValueError, subprocess.CalledProcessError):
        print("Repository content: BLOCKED (cannot inspect complete staged index)", file=sys.stderr)
        return 1
    for error in errors:
        print(error, file=sys.stderr)
    print(f"Repository content: {'FAIL' if errors else 'PASS'} (known patterns only; provenance review required)")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
