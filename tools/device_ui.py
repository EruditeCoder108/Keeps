"""Small ADB helper for the explicitly authorized Android device smoke test.

Always supplies a device serial and reads the current UI before tapping a label.
No lock-screen credentials, broad media access, or phone-setting changes.
"""
import argparse
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

p = argparse.ArgumentParser()
p.add_argument("--serial", required=True)
p.add_argument("action", choices=["inspect", "tap", "hold", "text", "back", "swipe", "capture", "check-first"])
p.add_argument("values", nargs="*")
a = p.parse_args()
adb = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"
out = Path(__file__).resolve().parents[1] / ".work/device"
out.mkdir(parents=True, exist_ok=True)

def run(*args):
    return subprocess.run([str(adb), "-s", a.serial, *args], check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=40).stdout.decode("utf-8", errors="replace")

def ui():
    run("shell", "uiautomator", "dump", "/data/local/tmp/keeps-ui.xml")
    run("pull", "/data/local/tmp/keeps-ui.xml", str(out / "current.xml"))
    return ET.parse(out / "current.xml")

if a.action in ("tap", "hold", "inspect", "check-first"):
    tree = ui()
    if a.action == "inspect":
        for node in tree.iter("node"):
            label = node.get("text") or node.get("content-desc")
            if label:
                print(label, node.get("bounds"))
    elif a.action == "check-first":
        if not any(n.get("text") == "SEARCH RESULTS" for n in tree.iter("node")):
            raise SystemExit("Expected the search-results screen")
        files = [n.get("text") for n in tree.iter("node") if (n.get("text") or "").startswith("fixture-")]
        expected = " ".join(a.values)
        if not files or files[0] != expected:
            raise SystemExit(f"Expected first visible result {expected!r}, found {files[:1]}")
        print("PASS: first visible result is", expected)
    else:
        label = " ".join(a.values)
        matches = [n for n in tree.iter("node") if label == n.get("text")]
        if not matches:
            matches = [n for n in tree.iter("node") if label == n.get("content-desc")]
        if len(matches) != 1:
            raise SystemExit(f"Expected one visible label {label!r}, found {len(matches)}")
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", matches[0].get("bounds")))
        x, y = str((x1 + x2) // 2), str((y1 + y2) // 2)
        if a.action == "hold":
            run("shell", "input", "swipe", x, y, x, y, "700")
        else:
            run("shell", "input", "tap", x, y)
        print(a.action, label)
elif a.action == "text":
    text = " ".join(a.values)
    if not re.fullmatch(r"[A-Za-z0-9 ._-]+", text):
        raise SystemExit("Use plain test text only")
    run("shell", "input", "text", text.replace(" ", "%s"))
elif a.action == "back":
    run("shell", "input", "keyevent", "KEYCODE_BACK")
elif a.action == "swipe":
    if len(a.values) != 4 or not all(v.isdigit() for v in a.values):
        raise SystemExit("Supply four observed screen coordinates")
    run("shell", "input", "swipe", *a.values, "400")
elif a.action == "capture":
    label = a.values[0] if a.values else "screen"
    if not re.fullmatch(r"[A-Za-z0-9_-]+", label):
        raise SystemExit("Use a simple capture name")
    ui()
    (out / f"{label}.xml").write_bytes((out / "current.xml").read_bytes())
    run("shell", "screencap", "-p", "/data/local/tmp/keeps-screen.png")
    run("pull", "/data/local/tmp/keeps-screen.png", str(out / f"{label}.png"))
    print(out / f"{label}.png")
