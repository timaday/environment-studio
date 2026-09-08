#!/usr/bin/env python3
"""Exercise private offline initialization in the exact protected runtime image."""
import sqlite3
import subprocess
import sys
import uuid


def run(args, *, expected=0, timeout=30):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if expected is not None and result.returncode != expected:
        # No raw JVM/driver output: future failures must not disclose runtime data.
        raise AssertionError(f"Workspace smoke command refused: exit {result.returncode}")
    return result


def main(image):
    volume = "environment-studio-workspace-check-" + uuid.uuid4().hex
    container = volume + "-process"
    run(["docker", "volume", "create", volume])
    try:
        mount = ["--mount", f"type=volume,source={volume},target=/workspace"]
        run(["docker", "run", "--rm", "--name", container, "--user", "0:0", "--read-only",
             "--entrypoint", "sh", *mount, image, "-ec",
             "chown 10001:10001 /workspace && chmod 0700 /workspace"])
        protected = ["docker", "run", "--rm", "--name", container, "--read-only", "--cap-drop", "ALL",
                     "--security-opt", "no-new-privileges", "--user", "10001:10001",
                     "--memory", "1g", "--tmpfs", "/tmp:rw,noexec,nosuid,size=128m", *mount]
        initialize = [*protected, image, "--initialize-workspace=/workspace"]
        first = run(initialize)
        assert not first.stdout and not first.stderr, "Initializer emitted unexpected output"

        def inspect():
            result = run([*protected, "--entrypoint", "sh", image, "-ec",
                          "stat -c '%a:%u:%g' /workspace /workspace/studio-workspace.db "
                          "&& sha256sum /workspace/studio-workspace.db"])
            lines = result.stdout.splitlines()
            assert lines[:2] == ["700:10001:10001", "600:10001:10001"], "Unsafe workspace ownership/mode"
            assert len(lines) == 3, "Unexpected workspace inspection output"
            return lines[2].split()[0]

        original = inspect()
        second = run(initialize, expected=None)
        assert second.returncode != 0, "Initializer overwrote existing storage"
        assert "WORKSPACE_UNAVAILABLE" in second.stderr, "Initializer lacked safe refusal code"
        assert inspect() == original, "Refused initialization changed existing storage"
        print("Private workspace initialization, permissions and overwrite refusal passed")
        # This empty SQLite store is independently created by this exact image smoke.
        # Transfer only its bytes in memory; no credentials or application model is involved.
        read_database = [*protected, "--entrypoint", "sh", image, "-ec",
                         "cat /workspace/studio-workspace.db"]
        database = subprocess.run(read_database, capture_output=True, timeout=30)
        assert database.returncode == 0, "Mock database readback failed"
        assert int.from_bytes(database.stdout[60:64], "big") == 2, "Initializer did not create schema 2"
        with sqlite3.connect(":memory:") as legacy:
            legacy.deserialize(database.stdout)
            legacy.execute("DROP TABLE native_replays")
            legacy.execute("DROP TABLE native_revisions")
            legacy.execute("DROP TABLE artifact_types")
            legacy.execute("PRAGMA user_version=1")
            legacy.commit()
            old_bytes = legacy.serialize()
        write_database = [*protected, "--interactive", "--entrypoint", "sh", image, "-ec",
                          "cat > /workspace/studio-workspace.db"]
        restored = subprocess.run(write_database, input=old_bytes, capture_output=True, timeout=30)
        assert restored.returncode == 0, "Independent legacy mock setup failed"
        upgrade = [*protected, image, "--upgrade-workspace=/workspace"]
        result = run(upgrade)
        assert not result.stdout and not result.stderr, "Offline upgrade emitted unexpected output"
        upgraded = inspect()
        repeated = run(upgrade, expected=None)
        assert repeated.returncode != 0, "Already upgraded store was accepted"
        assert inspect() == upgraded, "Refused upgrade changed storage"
        extra = run([*upgrade, "--server.port=0"], expected=None)
        assert extra.returncode != 0, "Offline upgrade accepted web arguments"
        assert inspect() == upgraded, "Invalid upgrade arguments changed storage"
        print("Schema 2 initializer and explicit offline legacy upgrade/refusal checks passed")
    finally:
        # This unique volume is created and owned solely by this smoke invocation.
        # A timed-out docker client can leave its container running; stop that exact one.
        run(["docker", "rm", "-f", container], expected=None)
        run(["docker", "volume", "rm", volume])


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: workspace_smoke.py IMAGE")
    main(sys.argv[1])
