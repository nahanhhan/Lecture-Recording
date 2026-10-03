import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock
import urllib.error

spec = importlib.util.spec_from_file_location("publisher", Path(__file__).parents[1] / "publish_release.py")
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublisherTest(unittest.TestCase):
    def test_create_release_uses_exact_tag_commit_and_prerelease_flag(self):
        github = publisher.GitHub("owner/repo", "fake")
        not_found = urllib.error.HTTPError("https://api.github.com", 404, "missing", {}, None)
        github.request = Mock(side_effect=[not_found, not_found, {"id": 1}])
        result = github.release("v0.1.5-alpha", "a" * 40, "release notes")
        self.assertEqual(1, result["id"])
        payload = github.request.call_args.args[2]
        self.assertEqual("a" * 40, payload["target_commitish"])
        self.assertTrue(payload["prerelease"])
    def test_existing_tag_for_another_commit_blocks_publication(self):
        github = publisher.GitHub("owner/repo", "fake")
        github.request = Mock(side_effect=[{"id": 1}, {"object": {"type": "commit", "sha": "b" * 40}}])
        with self.assertRaises(RuntimeError):
            github.release("v0.1.5-alpha", "a" * 40, "notes")
        self.assertTrue(all(call.args[0] == "GET" for call in github.request.call_args_list))

    def test_existing_identical_apk_is_preserved_without_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            apk.write_bytes(b"fixture")
            digest = "sha256:" + hashlib.sha256(apk.read_bytes()).hexdigest()
            github = publisher.GitHub("owner/repo", "fake")
            github.request = Mock()
            github.upload({"assets": [{"name": "recnote-0.1.5-alpha.apk", "state": "uploaded", "digest": digest}]}, apk, "v0.1.5-alpha")
            github.request.assert_not_called()

    def test_existing_different_apk_is_never_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            apk.write_bytes(b"fixture")
            github = publisher.GitHub("owner/repo", "fake")
            github.request = Mock()
            with self.assertRaises(RuntimeError):
                github.upload({"assets": [{"name": "recnote-0.1.5-alpha.apk", "state": "uploaded", "digest": "different"}]}, apk, "v0.1.5-alpha")
            github.request.assert_not_called()

    def test_upload_checks_reported_size_and_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            apk.write_bytes(b"fixture")
            github = publisher.GitHub("owner/repo", "fake")
            github.request = Mock(return_value={"state": "uploaded", "size": 1})
            with self.assertRaises(RuntimeError):
                github.upload({"assets": [], "upload_url": "https://uploads.github.com/repos/owner/repo/releases/1/assets{?name}"}, apk, "v0.1.5-alpha")

    def test_token_is_never_sent_to_another_host(self):
        github = publisher.GitHub("owner/repo", "fake")
        with self.assertRaises(ValueError):
            github.request("POST", "https://attacker.example/upload", b"fixture")


if __name__ == "__main__":
    unittest.main()
