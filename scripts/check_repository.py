#!/usr/bin/env python3
"""Small repository integrity checks; never claims product qualification."""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    errors = []
    required = ["AGENTS.md", "README.md", "Dockerfile", "frontend/package-lock.json",
                "docs/delivery/build-plan.md", "docs/quality/release-evidence.json",
                "deploy/README.md", ".github/workflows/ci.yml",
                "fixtures/demo/target/nodes.xml", "fixtures/demo/target/workloads.xml", "fixtures/demo/target/endpoints.xml"]
    for name in required:
        if not (ROOT / name).is_file():
            errors.append(f"Missing {name}")
    for folder in ["schemas", "fixtures", "docs/quality"]:
        for path in (ROOT / folder).rglob("*.json"):
            try:
                json.loads(path.read_text())
            except (ValueError, OSError) as error:
                errors.append(f"Invalid JSON {path.relative_to(ROOT)}: {type(error).__name__}")
    for path in [*(ROOT / "backend").rglob("pom.xml"), *(ROOT / "fixtures").rglob("*.xml")]:
        try:
            ET.parse(path)
        except ET.ParseError:
            errors.append(f"Invalid XML {path.relative_to(ROOT)}")
    for path in [ROOT / "README.md", *(ROOT / "docs").rglob("*.md"), ROOT / "deploy/README.md"]:
        for target in re.findall(r"\]\(([^\s)]+)\)", path.read_text()):
            if re.match(r"[a-z]+:", target) or target.startswith("#"):
                continue
            if not (path.parent / target.split("#")[0]).exists():
                errors.append(f"Broken link in {path.relative_to(ROOT)}: {target}")
    definition = json.loads((ROOT / "fixtures/demo/definition.json").read_text())
    if definition["status"] != "draft":
        errors.append("Synthetic incomplete definition must remain a draft")
    for error in errors:
        print(error, file=sys.stderr)
    print(f"Repository integrity: {'FAIL' if errors else 'PASS'}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
