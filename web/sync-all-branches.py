#!/usr/bin/env python3
"""
Syncs all branches from PVI-FTC/ftc-robot-framework, simulates every Autonomous
OpMode in each branch using the FTC Test Harness, and writes the resulting
recordings + manifest.json into web/vi_arena/recordings/.

Usage:
    python3 web/sync-all-branches.py [--force]
"""

import argparse
import datetime
import json
import os
import platform
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

FRAMEWORK_REPO = os.environ.get(
    "FRAMEWORK_REPO", "https://github.com/PVI-FTC/ftc-robot-framework.git"
)
DEFAULT_BRANCH = "feature/team-3-setup"
REPO_ROOT = Path(__file__).resolve().parent.parent
TH_SRC = REPO_ROOT / "th_src"
RECORDINGS_OUT = REPO_ROOT / "web" / "vi_arena" / "recordings"
MANIFEST_PATH = RECORDINGS_OUT / "manifest.json"
BUILD_RECORDINGS = TH_SRC / "build" / "recordings"


def sanitize_branch(branch: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "__", branch)


def label_from_filename(filename: str) -> str:
    name = filename
    if name.startswith("auto-"):
        name = name[len("auto-"):]
    if name.endswith(".json"):
        name = name[:-len(".json")]
    return name


def detect_team_id(branch: str) -> str:
    lower = branch.lower()
    if "team2" in lower or "team-2" in lower or "team_2" in lower:
        return "team2"
    if "team4" in lower or "team-4" in lower or "team_4" in lower:
        return "team4"
    if "teama" in lower or "team-a" in lower or "team_a" in lower or lower == "master":
        return "teamA"
    return "team3"


def list_remote_branches() -> list[tuple[str, str]]:
    res = subprocess.run(
        ["git", "ls-remote", "--heads", FRAMEWORK_REPO],
        check=True,
        capture_output=True,
        text=True,
    )
    branches = []
    for line in res.stdout.splitlines():
        if not line.strip():
            continue
        sha, ref = line.split("\t", 1)
        if ref.startswith("refs/heads/"):
            branch = ref[len("refs/heads/"):]
            if branch.startswith("revert-"):
                continue
            branches.append((branch, sha[:7]))

    def sort_key(item):
        b = item[0]
        if b == DEFAULT_BRANCH:
            return (0, b)
        if b == "master":
            return (1, b)
        return (2, b.lower())

    branches.sort(key=sort_key)
    return branches


def load_existing_manifest() -> dict:
    if MANIFEST_PATH.exists():
        try:
            return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        except Exception:
            pass
    return {"branches": {}}


def gradle_cmd() -> list[str]:
    if platform.system() == "Windows":
        return [str(TH_SRC / "gradlew.bat")]
    gradlew = TH_SRC / "gradlew"
    gradlew.chmod(gradlew.stat().st_mode | 0o111)
    return [str(gradlew)]


def build_branch(branch: str, short_sha: str, folder_name: str) -> list[dict] | None:
    print(f"\n=== Syncing branch '{branch}' ({short_sha}) ===")
    tmp_dir = Path(tempfile.mkdtemp(prefix="ftc-branch-"))
    try:
        subprocess.run(
            ["git", "clone", "--depth", "1", "--branch", branch, FRAMEWORK_REPO, str(tmp_dir)],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
        )

        src_teamcode = tmp_dir / "TeamCode" / "src"
        src_deps = tmp_dir / "build.dependencies.gradle"
        if not src_teamcode.exists():
            print(f"  [skip] Branch '{branch}' has no TeamCode/src")
            return None

        dst_teamcode = REPO_ROOT / "TeamCode" / "src"
        if dst_teamcode.exists():
            shutil.rmtree(dst_teamcode)
        shutil.copytree(src_teamcode, dst_teamcode)

        if src_deps.exists():
            shutil.copy2(src_deps, REPO_ROOT / "build.dependencies.gradle")

        # Activate matching team profile if available
        team_id = detect_team_id(branch)
        profile_path = REPO_ROOT / "configs" / "teams" / f"{team_id}.properties"
        if profile_path.exists():
            shutil.copy2(profile_path, TH_SRC / "harness.properties")

        # Clear previous auto-*.json so recordings from other branches don't leak
        if BUILD_RECORDINGS.exists():
            for old in BUILD_RECORDINGS.glob("auto-*.json"):
                old.unlink()

        cmd = gradle_cmd() + [
            "test",
            "--tests",
            "pvi.harness.GenerateTeam3AutonomousRecordingsTest",
            "--rerun-tasks",
            "--no-daemon",
        ]
        proc = subprocess.run(
            cmd,
            cwd=str(TH_SRC),
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0:
            print(f"  [warn] Branch '{branch}' failed to build/simulate (exit {proc.returncode}).")
            tail = "\n".join(proc.stderr.splitlines()[-15:])
            if tail:
                print(tail)
            return None

        generated = sorted(BUILD_RECORDINGS.glob("auto-*.json")) if BUILD_RECORDINGS.exists() else []
        if not generated:
            print(f"  [warn] Branch '{branch}' produced 0 autonomous recordings.")
            return None

        out_dir = RECORDINGS_OUT / folder_name
        if out_dir.exists():
            shutil.rmtree(out_dir)
        out_dir.mkdir(parents=True, exist_ok=True)

        recordings = []
        for f in generated:
            shutil.copy2(f, out_dir / f.name)
            recordings.append({
                "file": f.name,
                "label": label_from_filename(f.name),
            })

        print(f"  [ok] Generated {len(recordings)} recording(s) for '{branch}'.")
        return recordings
    except subprocess.CalledProcessError as e:
        print(f"  [warn] Git error for '{branch}': {e.stderr or e}")
        return None
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="Rebuild all branches even if commit SHA unchanged")
    args = parser.parse_args()

    RECORDINGS_OUT.mkdir(parents=True, exist_ok=True)
    existing = load_existing_manifest()
    old_branches = existing.get("branches", {})

    remote_branches = list_remote_branches()
    print(f"Found {len(remote_branches)} branch(es) in {FRAMEWORK_REPO}")

    new_branches = {}
    for branch, short_sha in remote_branches:
        folder_name = sanitize_branch(branch)
        prev = old_branches.get(branch)
        if (
            not args.force
            and prev
            and prev.get("commit") == short_sha
            and prev.get("recordings")
        ):
            prev_folder = RECORDINGS_OUT / prev.get("folder", folder_name)
            if prev_folder.exists() and all((prev_folder / r["file"]).exists() for r in prev["recordings"]):
                print(f"[up-to-date] '{branch}' ({short_sha}) — keeping {len(prev['recordings'])} recording(s)")
                new_branches[branch] = prev
                continue

        recs = build_branch(branch, short_sha, folder_name)
        if recs:
            new_branches[branch] = {
                "folder": folder_name,
                "commit": short_sha,
                "recordings": recs,
            }
        elif prev and prev.get("recordings"):
            print(f"  [keep] Keeping previous working recordings for '{branch}'")
            new_branches[branch] = prev

    default_branch = DEFAULT_BRANCH if DEFAULT_BRANCH in new_branches else (
        next(iter(new_branches.keys()), DEFAULT_BRANCH)
    )

    manifest = {
        "repo": "PVI-FTC/ftc-robot-framework",
        "updatedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "defaultBranch": default_branch,
        "branches": new_branches,
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"\nWrote {MANIFEST_PATH} with {len(new_branches)} branch(es).")


if __name__ == "__main__":
    main()
