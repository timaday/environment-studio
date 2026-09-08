import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from check_repository_content import assess, read_index


def mock_files():
    manifest = {"schemaVersion": 1, "purpose": "MOCK_DATABASE_TESTING",
                "origin": "INVENTED_FROM_SCRATCH", "artifacts": ["input.xml", "definition.json"]}
    return {"fixtures/example/provenance.json": json.dumps(manifest).encode(),
            "fixtures/example/input.xml": b'<root xmlns="urn:environment-studio:mock"/>',
            "fixtures/example/definition.json": b'{"schemaVersion":"1","entityTypes":[],"documents":[]}'}


class RepositoryContentTest(unittest.TestCase):
    def test_registered_independent_mock_artifacts_and_tool_contracts_are_allowed(self):
        files = mock_files()
        files["backend/pom.xml"] = b"<project/>"
        files["schemas/definition.schema.json"] = b'{"type":"object","properties":{"entityTypes":{}}}'
        self.assertEqual([], assess(files))

    def test_unregistered_database_artifacts_are_rejected(self):
        for suffix in ["xml", "xsd", "sql", "ddl", "csv", "dump", "sqlite"]:
            with self.subTest(suffix=suffix):
                self.assertTrue(assess({f"docs/unregistered.{suffix}": b"invented test payload"}))

    def test_a_fixture_path_alone_does_not_establish_mock_provenance(self):
        self.assertTrue(assess({"fixtures/example/input.xml": b"<root/>"}))
        files = mock_files()
        manifest = json.loads(files["fixtures/example/provenance.json"])
        manifest["origin"] = "RENAMED_REAL_SOURCE"
        files["fixtures/example/provenance.json"] = json.dumps(manifest).encode()
        self.assertTrue(assess(files))

    def test_new_fixture_payload_requires_manifest_registration(self):
        files = mock_files()
        files["fixtures/example/extra.json"] = b"{}"
        self.assertTrue(assess(files))

    def test_manifest_cannot_allow_files_outside_its_family_or_missing_files(self):
        for path in ["../../docs/anything.xml", "/tmp/example.xml", "missing.xml"]:
            with self.subTest(path=path):
                files = mock_files()
                manifest = json.loads(files["fixtures/example/provenance.json"])
                manifest["artifacts"].append(path)
                files["fixtures/example/provenance.json"] = json.dumps(manifest).encode()
                self.assertTrue(assess(files))

    def test_external_application_config_is_rejected_even_as_json_or_yaml(self):
        payloads = {
            "docs/model.json": b'{"schemaVersion":"1","entityTypes":[],"documents":[]}',
            "docs/profile.json": b'{"definitionDigest":"test-only","entities":[],"relations":[]}',
            "docs/model.yaml": b'schemaVersion: 1\nentityTypes: []\ndocuments: []\n',
        }
        for name, value in payloads.items():
            with self.subTest(name=name):
                self.assertTrue(assess({name: value}))

    def test_external_workspace_paths_and_links_are_refused(self):
        self.assertTrue(assess({"private-fixtures/anything.txt": b"synthetic canary"}))
        self.assertTrue(assess(mock_files(), {"fixtures/example/input.xml": "120000"}))

    def test_index_check_uses_staged_content_and_respects_staged_deletions(self):
        with tempfile.TemporaryDirectory(prefix="studio-index-test-") as folder:
            root = Path(folder)
            def git(*args):
                subprocess.run(["git", "-C", folder, *args], check=True,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            git("init", "-q")
            (root / "README.md").write_text("Independent mock test repository.\n")
            payload = root / "example.xml"
            payload.write_bytes(b"<independently-invented-mock/>")
            git("add", "README.md", "example.xml")
            payload.write_bytes(b"working tree changed after staging")
            files, modes = read_index(root)
            self.assertEqual(b"<independently-invented-mock/>", files["example.xml"])
            self.assertTrue(assess(files, modes))
            git("rm", "--cached", "-f", "example.xml")
            files, modes = read_index(root)
            self.assertNotIn("example.xml", files)
            self.assertEqual([], assess(files, modes))


if __name__ == "__main__":
    unittest.main()
