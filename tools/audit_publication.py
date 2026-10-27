"""Read-only publication preflight; report paths/rule IDs, never matched secrets.

Scans tracked + nonignored untracked text files, reachable local Git blobs and
commit metadata. Binary contents require separate visual/provenance review.
No network, Git mutations, phone access or history rewriting.
"""
from pathlib import Path
import argparse
import json
import re
import subprocess
from datetime import date

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----"),
    "github-token": re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b"),
    "google-api-key": re.compile(rb"\bAIza[0-9A-Za-z_-]{30,}\b"),
    "openai-key": re.compile(rb"\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,}\b"),
    "aws-key": re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "slack-token": re.compile(rb"\bxox[baprs]-[0-9A-Za-z-]{20,}\b"),
    "windows-personal-path": re.compile(rb"[A-Za-z]:[\\/](?:Users[\\/][^\s\"<>]+|Projects(?:%20| )[0-9][^\s\"<>]*)", re.I),
}
PRIVATE_EXTENSIONS = {".db", ".sqlite", ".sqlite3", ".jks", ".keystore", ".pem", ".p12", ".pfx", ".litertlm"}
PRIVATE_NAMES = {"local.properties", "google-services.json", "keystore.properties", "signing.properties"}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def scan(data, path, source):
    if b"\x00" in data[:8192]:
        return []
    findings = []
    for rule, pattern in PATTERNS.items():
        for match in pattern.finditer(data):
            findings.append({"source": source, "path": path, "line": data[:match.start()].count(b"\n") + 1, "rule": rule})
    return findings


def private_artifact(name):
    path = Path(name)
    return path.suffix.lower() in PRIVATE_EXTENSIONS or path.name in PRIVATE_NAMES


def audit():
    candidates = sorted(set(filter(None, git("ls-files", "-z", "--cached", "--others", "--exclude-standard").decode().split("\x00"))))
    findings, binary = [], []
    text_count = 0
    for name in candidates:
        path = ROOT / name
        if not path.is_file():
            continue
        data = path.read_bytes()
        if private_artifact(name):
            findings.append({"source": "working-tree", "path": name, "rule": "private-artifact"})
        if b"\x00" in data[:8192]:
            binary.append(name)
        else:
            text_count += 1
            findings.extend(scan(data, name, "working-tree"))
    history_text, history_blobs = 0, 0
    for row in git("rev-list", "--objects", "--all").decode().splitlines():
        parts = row.split(" ", 1)
        if len(parts) != 2:
            continue
        oid, name = parts
        if git("cat-file", "-t", oid).strip() != b"blob":
            continue
        history_blobs += 1
        data = git("cat-file", "blob", oid)
        if b"\x00" not in data[:8192]:
            history_text += 1
            findings.extend(scan(data, name, "git-history"))
        if private_artifact(name):
            findings.append({"source": "git-history", "path": name, "rule": "private-artifact"})
    findings.extend(scan(git("log", "--all", "--format=%an <%ae>%n%cn <%ce>%n%B"), "commit metadata", "git-history"))
    return {
        "date": date.today().isoformat(),
        "scope": "Tracked and nonignored untracked working files; all reachable local Git blobs and commit metadata. No remote refs fetched.",
        "candidateFiles": len(candidates), "textFilesScanned": text_count,
        "historyBlobsScanned": history_blobs, "historyTextFilesScanned": history_text,
        "reachableCommits": int(git("rev-list", "--count", "--all")),
        "licensePresent": (ROOT / "LICENSE").is_file(), "noticePresent": (ROOT / "NOTICE").is_file(),
        "findings": findings, "binaryFilesForManualReview": binary,
        "limitations": ["Pattern coverage is limited; no zero-secrets guarantee.",
                         "Binary media and asset rights need manual review.",
                         "Git author name and no-reply identity remain in existing history.",
                         "Ignored local files are outside the publishable candidate scan.",
                         "No hosting visibility, remote history or dependency license compliance assertion."],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="Optional JSON report within this repository")
    args = parser.parse_args()
    report = audit()
    payload = json.dumps(report, indent=2) + "\n"
    if args.output:
        destination = args.output.resolve()
        if not destination.is_relative_to(ROOT):
            parser.error("Report output must stay inside this repository")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(payload, encoding="utf-8")
    print(payload)
    raise SystemExit(1 if report["findings"] else 0)
