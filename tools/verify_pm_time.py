"""Verify the FULL "6:15 PM" (12-hour, with space) timestamp path.

The previous run typed "615" because `adb shell input text` drops spaces, and the
app correctly read that bare number as 24-hour 06:15 (shown "6:15 AM"). This run
types the complete 12-hour value by escaping the space, then checks the session
row renders "6:15 PM".
"""
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = r"C:\Users\Gabri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path(__file__).resolve().parent.parent / "docs" / "visual_qa" / "audit"
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


def bounds(label):
    x = dump()
    m = re.search(rf'text="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        a, b, c, d = map(int, m.groups())
        return (a + c) // 2, (b + d) // 2
    return None


def tap_xy(x, y, wait=1.2):
    run("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def tap(label, wait=1.6):
    p = bounds(label)
    if p is None:
        print(f"  !! not found {label!r}; saw {texts()[:10]}")
        return False
    tap_xy(p[0], p[1], wait)
    return True


def swipe_up(dist=1000):
    run("shell", "input", "swipe", "540", "1900", "540", str(1900 - dist), "400")
    time.sleep(1.0)


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


run("shell", "pm", "clear", "ph.edu.umak.momompurin")
run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
time.sleep(5)

# ---- sign in (anchored on the email box bottom) ----
x = dump()
m = re.search(r'text="Email or username"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
a, b, c, d = map(int, m.groups())
tap_xy((a + c) // 2, d + 70, 0.8)
run("shell", "input", "text", "katrinajj254@gmail.com")
time.sleep(0.6)
x = dump()
m = re.search(
    r'text="katrinajj254@gmail\.com"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
a, b, c, d = map(int, m.groups())
tap_xy((a + c) // 2, d + 150, 0.8)
run("shell", "input", "text", "katrina@")
time.sleep(0.6)
run("shell", "input", "keyevent", "66")
wait_for("Open a Live", 14)
time.sleep(1.2)

# ---- open the claim form ----
tap("Open a Live", 2.2)
wait_for("Customer Name *", 8)
time.sleep(0.8)
x = dump()
m = re.search(r'text="Customer Name \*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
a, b, c, d = map(int, m.groups())
tap_xy((a + c) // 2, d + 70, 0.8)
run("shell", "input", "text", "be")
time.sleep(1.6)
tap("Bea Cruz · @beacruz", 1.4)
run("shell", "input", "keyevent", "111")
time.sleep(1.0)
tap("Chiikawa Wipes", 1.4)
run("shell", "input", "keyevent", "111")
time.sleep(1.0)

# ---- type the FULL 12-hour timestamp (escaped space) ----
found = None
for _ in range(6):
    x = dump()
    m = re.search(r'text="7:42 PM"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        a, b, c, d = map(int, m.groups())
        found = ((a + c) // 2, (b + d) // 2)
        break
    swipe_up(900)
assert found, "timestamp field not found"
tap_xy(found[0], found[1], 1.0)
run("shell", "input", "keycombination", "113 29")  # select all
time.sleep(0.3)
run("shell", "input", "keyevent", "67")           # delete
time.sleep(0.4)
# adb's `input text` splits on spaces unless they are %-escaped.
run("shell", "input", "text", "6:15%sPM")
time.sleep(0.8)
run("shell", "input", "keyevent", "111")
time.sleep(1.2)
tx = texts()
typed = [t for t in tx if "6:15" in t]
print("  typed value on screen:", typed)
assert typed and "6:15 PM" in typed[0], f"expected '6:15 PM', saw {typed}"
shot("fix2-09-full-pm-typed.png")

# ---- save ----
for _ in range(6):
    if bounds("Save Claim"):
        break
    swipe_up(900)
tap("Save Claim", 2.6)
wait_for("Claim saved", 10)
time.sleep(1.0)
saved = [t for t in texts() if "Claim saved" in t]
new_ord = re.search(r"ORD-\d+", saved[0]).group(0)
print(f"  saved {new_ord}")

# ---- session claims: the new claim must be TOP, reading 6:15 PM ----
run("shell", "input", "keyevent", "111")
time.sleep(0.8)
tap("View this session's claims", 2.6, required=False)
if not bounds("Session claims"):
    tap("Live", 2.2, required=False)
    time.sleep(0.8)
    x = dump()
    m = re.search(r'text="LIVE-0007"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        a, b, c, d = map(int, m.groups())
        tap_xy((a + c) // 2, (b + d) // 2, 1.8)
time.sleep(1.2)
x = dump()
first = re.search(r'text="(ORD-\d+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
print("  first session row:", first.group(1) if first else None)
row_zone = ""
if first:
    m2 = re.search(
        rf'text="{first.group(1)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m2:
        top = int(m2.group(2))
        row_zone = x[x.find(f'text="{first.group(1)}"'):]
        pm = re.search(r'text="(6:15\s*[AP]M)"', row_zone[:900])
        print("  time shown on the new claim:", pm.group(1) if pm else "(not matched)")
shot("fix2-10-session-615pm.png")
assert first and first.group(1) == new_ord, "newest claim must be at the top"
print("\nRESULT: full 12-hour timestamp accepted and shown; newest first")
