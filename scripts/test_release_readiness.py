import json
import tempfile
import unittest
from pathlib import Path
from release_readiness import REQUIRED, assess


class ReleaseEvidenceTest(unittest.TestCase):
    def test_empty_and_unknown_evidence_never_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertTrue(assess({}, "a", root))
            gates = [{"id": gate, "status": "UNKNOWN"} for gate in sorted(REQUIRED)]
            self.assertTrue(assess({"sourceFingerprint": "a", "gates": gates}, "a", root))

    def test_stale_and_missing_files_block_even_with_pass_labels(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            gates = [{"id": gate, "status": "PASS", "evidence": f"{gate}.json"} for gate in sorted(REQUIRED)]
            manifest = {"sourceFingerprint": "a", "gates": gates}
            self.assertTrue(assess(manifest, "a", root))
            for gate in REQUIRED:
                (root / f"{gate}.json").write_text(json.dumps({"gate": gate, "sourceFingerprint": "a", "result": "PASS", "commands": ["synthetic-test"], "observations": ["test-only"]}))
            self.assertEqual([], assess(manifest, "a", root))
            self.assertTrue(assess(manifest, "new-source", root))
            manifest["gates"].append(gates[0])
            self.assertTrue(assess(manifest, "a", root))
