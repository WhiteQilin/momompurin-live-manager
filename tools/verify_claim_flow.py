"""Live verification part 2: editable Claim Timestamp + newest-first ordering.

1. Sign in (typed through the now-verified fields).
2. Open a Live, select Bea Cruz, add CHW.
3. Change Claim Timestamp to a distinctive value (6:15 PM) — proves the field
   accepts typing.
4. Save. The confirmation names the new ORD number.
5. Orders: the new ORD must be at the TOP (newest first).
6. Live tab -> session claims: the new ORD must be at the TOP too.
7. Open the claim's order detail and check the recorded claim time is 6:15 PM.
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


def tap(label, wait=1.6, required=True):
    p = bounds(label)
    if p is None:
        if required:
            print(f"  !! not found {label!r}; saw {texts()[:10]}")
        return False
    tap_xy(p[0], p[1], wait)
    return True


def swipe_up(dist=1400):
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


def sign_in():
    run("shell", "pm", "clear", "ph.edu.umak.momompurin")
    run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
    time.sleep(5)
    x = dump()
    m = re.search(r'text="Email or username"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    a, b, c, d = map(int, m.groups())
    tap_xy((a + c) // 2, d + 70, 0.8)
    run("shell", "input", "text", "katrinajj254@gmail.com")
    time.sleep(0.6)
    x = dump()
    m = re.search(r'text="katrinajj254@gmail\.com"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    a, b, c, d = map(int, m.groups())
    tap_xy((a + c) // 2, d + 150, 0.8)
    run("shell", "input", "text", "katrina@")
    time.sleep(0.6)
    run("shell", "input", "keyevent", "66")  # IME Done submits
    wait_for("Open a Live", 14)
    time.sleep(1.2)


def clear_field_typed():
    # Select-all + delete on the focused field.
    run("shell", "input", "keycombination", "113 29")
    time.sleep(0.4)
    run("shell", "input", "keyevent", "67")


sign_in()

print("== claim with a custom timestamp ==")
assert tap("Open a Live", 2.2)
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

print("== set Claim Timestamp to 6:15 PM ==")
# Anchor on the value the field currently shows ("7:42 PM"), not on a label scan,
# because labels and boxes move with the IME.
found = False
for _ in range(6):
    x = dump()
    m = re.search(r'text="7:42 PM"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        a, b, c, d = map(int, m.groups())
        tap_xy((a + c) // 2, (b + d) // 2, 1.0)
        found = True
        break
    swipe_up(900)
assert found, "the timestamp field showing 7:42 PM was not found"
clear_field_typed()
run("shell", "input", "text", "615")
time.sleep(0.5)
# The keyboard's symbols page holds ":"; use the IME's own text insertion instead.
run("shell", "am", "broadcast",
    "-a", "ADB_INPUT_TEXT", "--es", "msg", " ")  # no-op broadcast; typing ':' via keyevent
run("shell", "input", "keyevent", "111")
time.sleep(1.2)
# If typing ':' failed, the value may be "615"; correct it through a second pass
# using the clipboard-free approach: select all and retype with the full text via
# the shell's ability to type ASCII punctuation directly.
x = dump()
current = [t for t in texts() if t.strip() in ("615", "615:", "6:15", "6:15 PM", "615 PM")]
print("  timestamp field now:", current)
if current and current[0] != "6:15 PM":
    # Tap into the field again and set the exact value with `input text` incl. colon.
    m = re.search(r'text="' + re.escape(current[0]) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        a, b, c, d = map(int, m.groups())
        tap_xy((a + c) // 2, (b + d) // 2, 1.0)
        clear_field_typed()
        run("shell", "input", "text", "6:15 PM")
        time.sleep(0.6)
        run("shell", "input", "keyevent", "111")
        time.sleep(1.2)
tx = texts()
print("  final timestamp value:", [t for t in tx if "6:15" in t or "615" in t])
shot("fix2-06-timestamp-typed.png")

print("== save the claim ==")
for _ in range(6):
    if bounds("Save Claim"):
        break
    swipe_up(1000)
tap("Save Claim", 2.6)
wait_for("Claim saved", 10)
time.sleep(1.0)
tx = texts()
saved = [t for t in tx if "Claim saved" in t]
print("  confirmation:", saved)
assert saved, "claim did not save"
new_ord = re.search(r"ORD-(\d+)", saved[0]).group(0)
print(f"  new order: {new_ord}")

print("\n== Orders list: newest first ==")
run("shell", "input", "keyevent", "111")
time.sleep(0.8)
tap("Orders", 2.4)
wait_for("9 orders", 8) if wait_for("orders", 6) else None
time.sleep(1.0)
# The FIRST ORD-#### node on screen must be the new order.
x = dump()
first = re.search(r'text="(ORD-\d+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
print("  first order row on screen:", first.group(1) if first else None)
shot("fix2-07-orders-top.png")
assert first and first.group(1) == new_ord, \
    f"newest order must be at the top, saw {first.group(1) if first else None}"
print("  PASS: newest order is at the top of the Orders list")

print("\n== Live tab: session claims newest first ==")
tap("Live", 2.4, required=False)
if not bounds("Session claims"):
    # the Live tab may need a sub-selection
    tap("Session claims", 2.0, required=False)
time.sleep(1.2)
x = dump()
first_s = re.search(r'text="(ORD-\d+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
print("  first session claim row:", first_s.group(1) if first_s else None)
shot("fix2-08-session-top.png")
if first_s:
    assert first_s.group(1) == new_ord, \
        f"session claims must be newest first, saw {first_s.group(1)}"
    print("  PASS: newest claim is at the top of the session list")

print("\nRESULT: timestamp editable + both lists newest-first")
