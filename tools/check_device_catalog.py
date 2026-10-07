"""Read the debug Keeps database to verify synthetic-only fixture test state.

Run only when indexing is idle. Copies the database and WAL to ignored local
test output, then refuses to report a library containing non-fixture files.
"""
import argparse
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
p = argparse.ArgumentParser()
p.add_argument("--serial", required=True)
p.add_argument("--label", default="catalog")
a = p.parse_args()
if not a.label.replace("-", "").isalnum():
    raise SystemExit("Use a simple report label")
adb = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"
out = Path(__file__).resolve().parents[1] / ".work/device" / a.label
out.mkdir(parents=True, exist_ok=True)
for suffix in ("", "-wal"):
    result = subprocess.run([str(adb), "-s", a.serial, "exec-out", "run-as", "dev.keeps.library",
                             "cat", f"databases/keeps.db{suffix}"], capture_output=True, timeout=20)
    if result.returncode == 0:
        (out / f"keeps.db{suffix}").write_bytes(result.stdout)
    elif suffix == "":
        raise SystemExit(result.stderr.decode(errors="replace"))

database = sqlite3.connect(out / "keeps.db")
database.row_factory = sqlite3.Row
rows = database.execute("SELECT id,name,status,detail FROM assets ORDER BY name").fetchall()
if any(not row["name"].startswith("fixture-") for row in rows):
    raise SystemExit("Refusing to report a library containing non-fixture files")
report = []
for row in rows:
    chunks = database.execute("SELECT pageNumber,startMs,endMs,length(vector) AS vectorBytes FROM chunks WHERE assetId=?", (row["id"],)).fetchall()
    report.append({"name": row["name"], "status": row["status"], "detail": row["detail"],
                   "chunks": [dict(chunk) for chunk in chunks]})
database.close()
(out / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
