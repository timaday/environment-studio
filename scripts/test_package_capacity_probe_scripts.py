import contextlib
import hashlib
import io
import json
import runpy
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
PROBES = [
    ROOT / "docs/evidence/probes/package-capacity/run_exact_image_admission.py",
    ROOT / "docs/evidence/probes/package-capacity/run_exact_image_assembly.py",
]


class FakeProcess:
    def __init__(self, timeout=False):
        self.timeout = timeout
        self.stopped = False
    def poll(self):
        if self.timeout and not self.stopped:
            return None
        return 0
    def wait(self, timeout=None):
        self.stopped = True
        return 0


class PackageCapacityProbeScriptsTest(unittest.TestCase):
    def run_probe(self, source, *, timeout_run=False, inspect_exit=0):
        with tempfile.TemporaryDirectory(prefix="es-probe-script-") as folder:
            root = Path(folder)
            script = root / source.name
            shutil.copy(source, script)
            bundle = root / "bundle"
            bundle.mkdir()
            payload = bundle / "payload.bin"
            payload.write_bytes(b"independent mock payload")
            manifest = {
                "image": "mock-image",
                "sourceRevision": "mock-source",
                "bundleHashes": {"payload.bin": hashlib.sha256(payload.read_bytes()).hexdigest()},
            }
            (root / "manifest.json").write_text(json.dumps(manifest))
            processes = []
            def popen(*args, **kwargs):
                process = FakeProcess(timeout=timeout_run and not processes)
                processes.append(process)
                return process
            def run(command, **kwargs):
                if command[:2] == ["docker", "stop"]:
                    processes[-1].stopped = True
                    return subprocess.CompletedProcess(command, 0, "", "")
                if command[:2] == ["docker", "inspect"]:
                    return subprocess.CompletedProcess(command, inspect_exit,
                            json.dumps({"ExitCode": 0, "OOMKilled": False, "Running": False}) if inspect_exit == 0 else "", "")
                if command[:2] == ["docker", "rm"]:
                    return subprocess.CompletedProcess(command, 0, "", "")
                raise AssertionError(command)
            clock = iter([0, 301, 302, 303, 304, 305] if timeout_run else [0, 1, 2, 3, 4, 5])
            with mock.patch("subprocess.Popen", popen), mock.patch("subprocess.run", run), \
                    mock.patch("time.monotonic", lambda: next(clock)), mock.patch("time.sleep", lambda _: None), \
                    contextlib.redirect_stdout(io.StringIO()):
                runpy.run_path(str(script), run_name="__main__")
    def test_normal_controls_still_pass(self):
        for probe in PROBES:
            with self.subTest(probe=probe.name):
                self.run_probe(probe)
    def test_timeout_exit_zero_is_not_a_passing_result(self):
        for probe in PROBES:
            with self.subTest(probe=probe.name):
                with self.assertRaises(AssertionError):
                    self.run_probe(probe, timeout_run=True)
    def test_missing_docker_state_is_not_a_passing_result(self):
        for probe in PROBES:
            with self.subTest(probe=probe.name):
                with self.assertRaises(AssertionError):
                    self.run_probe(probe, inspect_exit=1)


if __name__ == "__main__":
    unittest.main()
