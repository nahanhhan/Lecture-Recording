"""Publish immutable, versioned APKs to GitHub Releases without exposing credentials."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import zipfile


class GitHub:
    def __init__(self, repo: str, token: str):
        self.repo = repo
        self.token = token

    def request(self, method: str, url: str, body: bytes | dict | None = None):
        parsed = urllib.parse.urlparse(url)
        if parsed.scheme != "https" or parsed.hostname not in ("api.github.com", "uploads.github.com"):
            raise ValueError("Unexpected GitHub endpoint")
        content_type = "application/vnd.android.package-archive"
        if isinstance(body, dict):
            body = json.dumps(body, ensure_ascii=False).encode()
            content_type = "application/json"
        request = urllib.request.Request(url, data=body, method=method, headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
            "User-Agent": "RecNote-release-publisher", "Content-Type": content_type,
            "X-GitHub-Api-Version": "2022-11-28",
        })
        with urllib.request.urlopen(request, timeout=180) as response:
            return json.load(response)

    def release(self, tag: str, commit: str, notes: str):
        url = f"https://api.github.com/repos/{self.repo}/releases"
        existing = None
        try:
            existing = self.request("GET", url + "/tags/" + urllib.parse.quote(tag, safe=""))
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
        try:
            ref = self.request("GET", f"https://api.github.com/repos/{self.repo}/git/ref/tags/" + urllib.parse.quote(tag, safe=""))
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            ref = None
        if ref is not None:
            obj = ref["object"]
            for _ in range(5):
                if obj["type"] == "commit":
                    break
                if obj["type"] != "tag":
                    raise RuntimeError("Release tag does not identify a commit")
                obj = self.request("GET", f"https://api.github.com/repos/{self.repo}/git/tags/{obj['sha']}")["object"]
            if obj["type"] != "commit" or obj["sha"] != commit:
                raise RuntimeError("Existing release tag points to another commit; refusing to publish")
        if existing is not None:
            return existing
        return self.request("POST", url, {
            "tag_name": tag, "target_commitish": commit, "name": f"RecNote {tag.removeprefix('v')}",
            "body": notes, "draft": False, "prerelease": "-" in tag,
        })

    def upload(self, release: dict, apk: Path, tag: str):
        data = apk.read_bytes()
        digest = "sha256:" + hashlib.sha256(data).hexdigest()
        name = f"recnote-{tag.removeprefix('v')}.apk"
        for asset in release.get("assets", []):
            if asset["name"] != name:
                continue
            if asset.get("digest") not in (None, digest):
                raise RuntimeError("Release already has a different APK; refusing to replace it")
            if asset.get("state") != "uploaded":
                raise RuntimeError("Release has an incomplete asset; inspect it before retrying")
            print(f"Kept existing release APK: {name}")
            return
        upload_url = release["upload_url"].split("{", 1)[0]
        asset = self.request("POST", upload_url + "?" + urllib.parse.urlencode({"name": name}), data)
        if asset.get("state") != "uploaded" or asset.get("size") != len(data):
            raise RuntimeError("GitHub did not confirm a complete APK upload")
        if asset.get("digest") not in (None, digest):
            raise RuntimeError("Uploaded APK digest differs from the local file")
        print(f"Published {asset['browser_download_url']} ({len(data)} bytes)")


def git_token(repo: str) -> str:
    result = subprocess.run(["git", "credential", "fill"], input=f"url=https://github.com/{repo}.git\n\n",
                            text=True, capture_output=True, check=True,
                            env={**os.environ, "GIT_TERMINAL_PROMPT": "0", "GCM_INTERACTIVE": "never"})
    values = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
    return values.get("password", "")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--notes", type=Path)
    parser.add_argument("--use-git-credentials", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo):
        parser.error("Invalid repository")
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", args.tag):
        parser.error("Invalid version tag")
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        parser.error("Use an exact commit SHA")
    with zipfile.ZipFile(args.apk) as apk:
        if "AndroidManifest.xml" not in apk.namelist():
            parser.error("File is not an APK")
    if args.metadata:
        metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
        if metadata["applicationId"] != "io.github.nahanhhan.lecturerecording":
            parser.error("Refusing to publish a test application")
        if "v" + metadata["elements"][0]["versionName"] != args.tag:
            parser.error("APK version does not match release tag")
    notes_path = args.notes or Path("docs/releases") / (args.tag.removeprefix("v") + ".md")
    notes = notes_path.read_text(encoding="utf-8") if notes_path.exists() else "历史版本安装包。建议日常使用最新版本；历史包保留当时的签名。"
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token and args.use_git_credentials:
        token = git_token(args.repo)
    if not token:
        parser.error("A GitHub token or authorized Git credential is required")
    github = GitHub(args.repo, token)
    release = github.release(args.tag, args.commit, notes)
    github.upload(release, args.apk, args.tag)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        raise SystemExit(f"GitHub request failed: HTTP {error.code}") from None
