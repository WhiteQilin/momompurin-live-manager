"""Step 3 — Automated on-device XML pixel-bounds geometry check.

Screens verified: SignIn (empty / masked / revealed), Dashboard, Live, Orders,
Stock, More.

Checks:
  1. SIBLING VERTICAL CENTRE PARITY — two text nodes side by side (horizontal
     overlap, vertical bands overlapping) with different heights must share
     |centreY1 - centreY2| <= 6 px.
  2. BORDER CLEARANCE — a text node nested inside an EditText (the field's box)
     must keep >= 6 px of air top and bottom.
  3. THE core parity: the password box and its trailing Show/Hide pill must
     share centreY within 2 px — anchored on the password EDITTEXT node, never
     on "the first Show on screen".
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = r"C:\Users\Gabri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path(__file__).resolve().parent.parent / "docs" / "visual_qa" / "audit"
OUT.mkdir(parents=True, exist_ok=True)
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PARITY_PX = 6
CLEARANCE_PX = 6
SHOW_PX = 2


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


def bounds(label):
    x = dump()
    m = re.search(rf'text="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        a, b, c, d = map(int, m.groups())
        return (a + c) // 2, (b + d) // 2
    return None


def tap(label, wait=1.6, required=True):
    p = bounds(label)
    if p is None:
        if required:
            print(f"  !! not found {label!r}; saw {texts()[:8]}")
        return False
    run("shell", "input", "tap", str(p[0]), str(p[1]))
    time.sleep(wait)
    return True


def tap_below(label, dy=70, wait=1.0):
    x = dump()
    m = re.search(rf'text="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if not m:
        return False
    a, b, c, d = map(int, m.groups())
    run("shell", "input", "tap", str((a + c) // 2), str(d + dy))
    time.sleep(wait)
    return True


def shot(name):
    (OUT / name).write_bytes(
        subprocess.run([ADB, "exec-out", "screencap", "-p"],
                       capture_output=True, timeout=40).stdout)
    print(f"  [shot] {name}")


def wait_for(needle, tries=12, delay=1.0):
    for _ in range(tries):
        if any(needle in t for t in texts()):
            return True
        time.sleep(delay)
    print(f"  !! wait_for missed {needle!r}; saw {texts()[:10]}")
    return False


def parse_nodes(xml: str):
    out = []
    for m in re.finditer(r"<node[^>]*/?>", xml):
        tag = m.group(0)
        tm = re.search(r'text="([^"]*)"', tag)
        bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        cls = re.search(r'class="([^"]*)"', tag)
        if not bm:
            continue
        out.append({
            "text": (tm.group(1) if tm else ""),
            "class": (cls.group(1) if cls else ""),
            "l": int(bm.group(1)), "t": int(bm.group(2)),
            "r": int(bm.group(3)), "b": int(bm.group(4)),
        })
    return out


def check_screen(xml: str, screen: str):
    nodes = [n for n in parse_nodes(xml) if n["text"]]
    violations = []

    # 1. sibling vertical-centre parity.
    # Only pairs that genuinely sit ON ONE LINE: each node's centreY must fall
    # INSIDE the other node's vertical band (a label and its trailing pill).
    # A stacked caption-over-chip layout has overlapping bounds but the caption's
    # centre is far outside the chip's band, which is how the first pass produced
    # a false positive on Orders.
    for i in range(len(nodes)):
        for j in range(i + 1, len(nodes)):
            a, b = nodes[i], nodes[j]
            if a["l"] >= b["r"] or b["l"] >= a["r"]:
                continue
            ca = (a["t"] + a["b"]) / 2
            cb = (b["t"] + b["b"]) / 2
            inside_a = a["t"] <= cb <= a["b"]
            inside_b = b["t"] <= ca <= b["b"]
            if not (inside_a and inside_b):
                continue
            if abs(ca - cb) > PARITY_PX:
                hdiff = abs((a["b"] - a["t"]) - (b["b"] - b["t"]))
                if hdiff >= 8:
                    violations.append({
                        "check": "parity",
                        "a": {"text": a["text"][:28], "cy": round(ca), "h": a["b"] - a["t"]},
                        "b": {"text": b["text"][:28], "cy": round(cb), "h": b["b"] - b["t"]},
                        "delta": round(abs(ca - cb), 1),
                    })

    # 2. border clearance: text nested inside an EditText
    edits = [n for n in nodes if n["class"].endswith("EditText")]
    for box in edits:
        for c in nodes:
            if c is box or not c["text"] or c["class"].endswith("EditText"):
                continue
            if box["l"] <= c["l"] and c["r"] <= box["r"] and \
               box["t"] <= c["t"] and c["b"] <= box["b"]:
                top_gap = c["t"] - box["t"]
                bot_gap = box["b"] - c["b"]
                if min(top_gap, bot_gap) < CLEARANCE_PX:
                    violations.append({
                        "check": "clearance",
                        "node": c["text"][:28],
                        "top_gap": top_gap, "bottom_gap": bot_gap,
                    })
    return violations


def capture(name: str) -> str:
    xml = dump()
    v = check_screen(xml, name)
    shot(f"geo-{name}.png")
    print(f"  {name:22s} violations: {len(v)}")
    for it in v[:4]:
        print("    ", json.dumps(it, ensure_ascii=False)[:160])
    report[name] = v
    return xml


report: dict = {}

run("shell", "pm", "clear", "ph.edu.umak.momompurin")
run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
time.sleep(5)

print("== SignIn: empty state ==")
capture("signin-empty")

print("\n== SignIn: masked ==")
# Fill the password, anchoring the tap below the email VALUE node so it cannot
# land in the email box when the IME shifts the layout.
x = dump()
m = re.search(r'text="Email or username"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
a, b, c, d = map(int, m.groups())
run("shell", "input", "tap", str((a + c) // 2), str(d + 70))
time.sleep(0.8)
run("shell", "input", "text", "katrinajj254@gmail.com")
time.sleep(0.6)
x = dump()
m = re.search(
    r'text="katrinajj254@gmail\.com"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if m:
    a, b, c, d = map(int, m.groups())
    run("shell", "input", "tap", str((a + c) // 2), str(d + 150))
else:
    tap_below("Password", 70, 1.0)
run("shell", "input", "text", "katrina@")
time.sleep(0.8)
run("shell", "input", "keyevent", "111")
time.sleep(1.4)
xml_masked = capture("signin-masked")

# ---- THE core parity check -------------------------------------------------
# Anchor on the password EDITTEXT node; pick the Show node inside its band.
x = xml_masked
pw = None
for nm in re.finditer(r"<node[^>]*/?>", x):
    tag = nm.group(0)
    if 'class="android.widget.EditText"' not in tag:
        continue
    if "Enter password" in tag or "\u2022" in tag or "katrina@" in tag:
        bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if bm:
            pw = tuple(int(bm.group(i)) for i in range(1, 5))
        break
if pw:
    pl, pt, pr, pb = pw
    pcx_l, pcx_r = pl, pr
    band_lo, band_hi = pt - 40, pb + 40
    best = None
    for sm in re.finditer(r'text="Show"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        a3, b3, c3, d3 = map(int, sm.groups())
        if a3 >= pcx_l - 40 and c3 <= pcx_r + 40 and \
           b3 >= band_lo and d3 <= band_hi:
            cy = (b3 + d3) / 2
            if best is None or abs(cy - (pt + pb) / 2) < abs(best - (pt + pb) / 2):
                best = cy
    if best is not None:
        cy_box = (pt + pb) / 2
        delta = abs(cy_box - best)
        print(f"\n  centerY(password box)={cy_box:.0f}  centerY(Show)={best:.0f}  "
              f"delta={delta:.1f}px  (limit {SHOW_PX}px)")
        report["password_show_delta_px"] = round(delta, 1)
    else:
        print("  !! Show not found within the password box band")
        report["password_show_delta_px"] = None
else:
    print("  !! password EditText not found in the dump")
    report["password_show_delta_px"] = None

print("\n== SignIn: revealed ==")
tap("Show", 1.6)
x = dump()
plain = re.search(r'text="katrina@"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
hide = re.search(r'text="Hide"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if plain and hide:
    a4, b4, c4, d4 = map(int, plain.groups())
    a5, b5, c5, d5 = map(int, hide.groups())
    delta2 = abs((b4 + d4) / 2 - (b5 + d5) / 2)
    print(f"  centerY(password)={(b4 + d4) / 2:.0f}  centerY(Hide)={(b5 + d5) / 2:.0f}  "
          f"delta={delta2:.1f}px")
    report["password_hide_delta_px"] = round(delta2, 1)
shot("geo-signin-revealed.png")
report["signin-revealed"] = check_screen(dump(), "signin-revealed")

# ---- remaining screens ----
run("shell", "input", "keyevent", "111")
time.sleep(0.8)
tap("LOG IN", 2.6)
wait_for("Open a Live", 14)
time.sleep(1.2)
capture("dashboard")
tap("Live", 2.2); time.sleep(1.0); capture("live")
tap("Orders", 2.2); time.sleep(1.0); capture("orders")
tap("Stock", 2.2); time.sleep(1.0); capture("stock")
tap("More", 2.2); time.sleep(1.0); capture("more")

(OUT / "geometry_report.json").write_text(
    json.dumps(report, indent=1, ensure_ascii=False), encoding="utf8")
total_v = sum(len(v) for v in report.values() if isinstance(v, list))
print(f"\nTOTAL parity/clearance violations: {total_v}")
print(f"password/Show delta: {report.get('password_show_delta_px')}px  (limit {SHOW_PX}px)")
print(f"password/Hide delta: {report.get('password_hide_delta_px')}px  (limit {SHOW_PX}px)")
