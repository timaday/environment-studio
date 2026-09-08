#!/usr/bin/env python3
"""Run explicitly selected disposable JDBC qualification; never skip a requested engine."""
import argparse
import pathlib
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--engine", choices=("both", "postgresql", "postgresql-lifecycle", "postgresql-privileges", "oracle-driver-lifecycle"), default="both")
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
command = [
    "java",
    "-Dloader.main=studio.environment.server.observation.DisposableObservationQualification",
    "-Dloader.path=" + str(root / "backend/server/target/test-classes"),
    "-cp", str(root / "backend/server/target/environment-studio.jar"),
    "org.springframework.boot.loader.launch.PropertiesLauncher", str(root),
]
if args.engine != "both":
    command.append(args.engine)
raise SystemExit(subprocess.run(command, cwd=root, check=False).returncode)
