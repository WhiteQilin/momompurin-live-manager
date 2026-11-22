"""Final taps: Fix button in a finding row + packing confirm."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def swipe_up(dist=900):
    d.run("shell", "input", "swipe", "540", "1900", "540", str(1900 - dist), "400")
    time.sleep(1.0)


def back():
    d.run("shell", "input", "keyevent", "4")
    time.sleep(1.4)


def go_home():
    d.run("shell", "input", "keyevent", "3")
    time.sleep(1.0)
    d.run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
    time.sleep(2.6)


# State: More screen after the rate editor opened.
back()  # close the editor if open
time.sleep(0.8)
print("== FIX 2: the Fix button ==")
# The finding row's Fix button sits in the row; scroll within the Data checks card.
found = False
for _ in range(8):
    if d.bounds_of("Fix"):
        found = True
        break
    swipe_up(700)
print("  Fix button visible:", found)
if found:
    d.shot("fix-03b-before-fix.png")
    d.tap_text("Fix", wait=2.2)
    d.shot("fix-04b-after-fix.png")
    ts = d.texts()
    print("  confirmation:", [t for t in ts if "Corrected" in t or "consistent" in t][:3])
    print("  finding gone:", not any("marked Available with no stock" in t for t in ts))

print("== T6 finish: packing confirm ==")
go_home()
d.tap_text("Orders", wait=2.6)
time.sleep(0.8)
# The order detail may already be reachable from history; use the Orders list.
for _ in range(10):
    if d.bounds_of("ORD-1042"):
        break
    swipe_up(1200)
if d.bounds_of("ORD-1042"):
    d.tap_text("ORD-1042", wait=2.8)
    time.sleep(1.2)
    for _ in range(8):
        if d.bounds_of("Pack"):
            break
        swipe_up()
    if d.tap_text("Pack", wait=2.8):
        time.sleep(1.4)
        for _ in range(5):
            if d.bounds_of("Confirm packing complete"):
                break
            swipe_up(700)
        d.shot("t6-03-packing-full.png")
        ts = d.texts()
        j = " | ".join(ts)
        print("  packing TO PREP:", "TO PREP" in j)
        print("  confirm visible:", "Confirm packing complete" in j)
        d.tap_text("Confirm packing complete", wait=2.8, required=False)
        time.sleep(1.0)
        d.shot("t6-04-packed.png")
        ts = d.texts()
        print("  packed state:", [t for t in ts if "PACKED" in t or "packed" in t.lower()][:5])
