#!/usr/bin/env python3
"""Content fingerprint excludes evidence itself and generated/build output."""
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCOPES = ["backend", "frontend", "schemas", "fixtures", "scripts", "deploy", ".github/workflows", "docs/contracts"]
EXCLUDED = {"node_modules", "dist", "coverage", "__pycache__", "test-results", "playwright-report"}


def fingerprint(root=ROOT):
    paths = {root / "Dockerfile", root / ".dockerignore"}
    for scope in SCOPES:
        for path in (root / scope).rglob("*"):
            parts = path.relative_to(root).parts
            is_maven_output = parts[0] == "backend" and "target" in parts
            if path.is_file() and not is_maven_output and not EXCLUDED.intersection(parts):
                paths.add(path)
    digest = hashlib.sha256()
    for path in sorted(paths, key=lambda item: item.relative_to(root).as_posix()):
        name = path.relative_to(root).as_posix().encode()
        data = path.read_bytes()
        digest.update(len(name).to_bytes(8, "big") + name + len(data).to_bytes(8, "big") + data)
    return digest.hexdigest()


if __name__ == "__main__":
    print(fingerprint())
