"""Capture the remaining screens for the geometry report (dashboard onwards).

Standalone: re-implements the three small helpers rather than importing
geometry_check.py, whose module level drives the whole sign-in flow.
"""
import json
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = r"C:\Users\Gabri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path(__file__).resolve().parent.parent / "docs" / "visual_qa" / "audit"
PARITY_PX = 6
CLEARANCE_PX = 6
sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def run(*a, t=40):
    return subprocess.run([ADB, *a], capture_output=True, text=True,
                          timeout=t, encoding="utf8", errors="replace").stdout


def dump():
    run("shell", "uiautomator", "dump", "/sdcard/ui.xml", t=25)
    x = run("shell", "cat", "/sdcard/ui.xml", t=25)
    return (x.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&quot;", '"').replace("&apos;", "'"))


def texts():
    return [t for t in re.findall(r'text="([^"]+)"', dump()) if t.strip()]


def tap(label, wait=2.2):
    x = dump()
    m = re.search(rf'(?:text|content-desc)="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if not m:
        print(f"  !! not found {label!r}; saw {texts()[:8]}")
        return False
    a, b, c, d = map(int, m.groups())
    run("shell", "input", "tap", str((a + c) // 2), str((b + d) // 2))
    time.sleep(wait)
    return True


def shot(name):
    (OUT / name).write_bytes(
        subprocess.run([ADB, "exec-out", "screencap", "-p"],
                       capture_output=True, timeout=40).stdout)
    print(f"  [shot] {name}")


def check_screen(xml):
    nodes = [n for n in
             (lambda x: [
                 {"text": (re.search(r'text="([^"]*)"', t) or [None, ""])[1],
                  "class": (re.search(r'class="([^"]*)"', t) or [None, ""])[1],
                  **{k: int(v) for k, v in zip(
                      ("l", "t", "r", "b"),
                      re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', t).groups())}}
                 for t in re.findall(r"<node[^>]*/?>", x)
                 if 'bounds="' in t])(xml)
             if n["text"]]
    violations = []
    for i in range(len(nodes)):
        for j in range(i + 1, len(nodes)):
            a, b = nodes[i], nodes[j]
            if a["l"] >= b["r"] or b["l"] >= a["r"]:
                continue
            if min(a["b"], b["b"]) - max(a["t"], b["t"]) < 4:
                continue
            ca, cb = (a["t"] + a["b"]) / 2, (b["t"] + b["b"]) / 2
            # CentreY of each must fall INSIDE the sibling's vertical band, or the
            # pair is stacked blocks (caption over chip), not one-line siblings.
            if not (a["t"] <= cb <= a["b"] and b["t"] <= ca <= b["b"]):
                continue
            if abs(ca - cb) > PARITY_PX and \
               abs((a["b"] - a["t"]) - (b["b"] - b["t"])) >= 8:
                violations.append({
                    "check": "parity",
                    "a": {"text": a["text"][:28], "cy": round(ca)},
                    "b": {"text": b["text"][:28], "cy": round(cb)},
                    "delta": round(abs(ca - cb), 1),
                })
    for box in [n for n in nodes if n["class"].endswith("EditText")]:
        for c in nodes:
            if c is box or not c["text"] or c["class"].endswith("EditText"):
                continue
            if box["l"] <= c["l"] and c["r"] <= box["r"] and \
               box["t"] <= c["t"] and c["b"] <= c["b"] and c["b"] <= box["b"]:
                gaps = (c["t"] - box["t"], box["b"] - c["b"])
                if min(gaps) < CLEARANCE_PX:
                    violations.append({
                        "check": "clearance", "node": c["text"][:28],
                        "top_gap": gaps[0], "bottom_gap": gaps[1],
                    })
    return violations


report = {}

for name in ("live", "orders", "stock", "more"):
    if not tap(name.capitalize()):
        continue
    time.sleep(0.6)
    xml = dump()
    v = check_screen(xml)
    shot(f"geo-{name}.png")
    report[name] = v
    print(f"  {name:8s} violations: {len(v)}")
    for it in v[:4]:
        print("    ", json.dumps(it, ensure_ascii=False)[:160])

# Dashboard was captured by the first script but under the failed nav run —
# recapture now that we know the app is signed in.
if tap("Home"):
    time.sleep(0.8)
    xml = dump()
    v = check_screen(xml)
    shot("geo-dashboard.png")
    report["dashboard"] = v
    print(f"  dashboard violations: {len(v)}")

(OUT / "geometry_report.json").write_text(
    json.dumps(report, indent=1, ensure_ascii=False), encoding="utf8")
total = sum(len(v) for v in report.values() if isinstance(v, list))
print(f"\nTOTAL violations on captured screens: {total}")
