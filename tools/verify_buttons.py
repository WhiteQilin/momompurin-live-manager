"""Verify the button fixes on device: Notifications action rows + the cancel dialog."""
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = r"C:\Users\Gabri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path(__file__).resolve().parent.parent / "docs" / "visual_qa" / "audit"
OUT.mkdir(parents=True, exist_ok=True)
sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def run(*a, timeout=40):
    return subprocess.run([ADB, *a], capture_output=True, text=True,
                          timeout=timeout, encoding="utf8", errors="replace").stdout


def dump():
    run("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=25)
    x = run("shell", "cat", "/sdcard/ui.xml", timeout=25)
    return x.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">") \
            .replace("&quot;", '"').replace("&apos;", "'")


def texts():
    return [t for t in re.findall(r'text="([^"]+)"', dump()) if t.strip()]


def bounds(label):
    x = dump()
    for attr in ("text", "content-desc"):
        m = re.search(rf'{attr}="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
        if m:
            a, b, c, d_ = map(int, m.groups())
            return (a + c) // 2, (b + d_) // 2
    return None


def tap(label, wait=1.6, required=True):
    p = bounds(label)
    if p is None:
        if required:
            print(f"  !! not found: {label!r}; on screen: {texts()[:10]}")
        return False
    run("shell", "input", "tap", str(p[0]), str(p[1]))
    time.sleep(wait)
    return True


def tap_below(label, dy=70, wait=1.2):
    x = dump()
    m = re.search(rf'text="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if not m:
        print(f"  !! label not found for tap_below: {label!r}")
        return False
    a, b, c, d_ = map(int, m.groups())
    run("shell", "input", "tap", str((a + c) // 2), str(d_ + dy))
    time.sleep(wait)
    return True


def shot(name):
    (OUT / name).write_bytes(
        subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, timeout=40).stdout)
    print(f"  [shot] {name}")


def wait_for(needle, tries=12, delay=1.0):
    for _ in range(tries):
        if any(needle in t for t in texts()):
            return True
        time.sleep(delay)
    print(f"  !! wait_for missed {needle!r}; saw {texts()[:10]}")
    return False


# ---- sign in ----
run("shell", "pm", "clear", "ph.edu.umak.momompurin")
run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
time.sleep(5)
tap_below("Email or username", 70, 0.8)
run("shell", "input", "text", "katrinajj254@gmail.com")
tap_below("Password", 70, 0.8)
run("shell", "input", "text", "katrina@")
run("shell", "input", "keyevent", "66")
wait_for("Open a Live", 14)
time.sleep(1.2)

# ---- Notifications ----
print("== Notifications: ORD-1045 / ORD-1042 action rows ==")
if not tap("Notifications", required=False):
    # the bell sits in the dashboard header
    x = dump()
    m = re.search(r'content-desc="Notifications"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        a, b, c, d_ = map(int, m.groups())
        run("shell", "input", "tap", str((a + c) // 2), str((b + d_) // 2))
        time.sleep(2)
wait_for("Notifications", 8)
time.sleep(1.2)
shot("btn-01-notifications.png")

tx = texts()
print("  action labels present:")
for lbl in ("Open order", "Confirm Cancel & Restock", "Extend by", "Set Payment Deadline"):
    print(f"    {lbl!r}:", any(lbl == t or lbl in t for t in tx))

# Measure each action button's bounds: a squished button is narrow and tall.
x = dump()
print("\n  measured action-button boxes (w x h):")
for lbl in ("Open order", "Confirm Cancel & Restock", "Extend by", "Set Payment Deadline"):
    for m in re.finditer(rf'text="{re.escape(lbl)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        a, b, c, d_ = map(int, m.groups())
        w, h = c - a, d_ - b
        flag = "  <-- SQUISHED" if h > w * 1.5 else ""
        print(f"    {lbl:28s} {w:4d} x {h:3d}{flag}")

# ---- Cancel dialog ----
print("\n== Orders: the 'Cancel this order?' dialog ==")
tap("Orders", wait=2.4)
wait_for("ORD-1050", 8)
time.sleep(1)
shot("btn-02-orders.png")
# Scroll to a cancellable order and open its cancel action.
for _ in range(8):
    if bounds("Cancel order"):
        break
    run("shell", "input", "swipe", "540", "1900", "540", "600", "400")
    time.sleep(1)
if tap("Cancel order", wait=2.0):
    wait_for("Cancel this order?", 8)
    time.sleep(1)
    shot("btn-03-cancel-dialog.png")
    tx = texts()
    print("  dialog texts:", [t for t in tx if t in ("Cancel this order?", "Keep order", "Confirm cancel")])
    x = dump()
    print("  dialog button boxes (w x h):")
    for lbl in ("Keep order", "Confirm cancel"):
        for m in re.finditer(rf'text="{re.escape(lbl)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
            a, b, c, d_ = map(int, m.groups())
            print(f"    {lbl:16s} {c-a:4d} x {d_-b:3d}")
else:
    print("  (no cancellable order visible to open the dialog)")
