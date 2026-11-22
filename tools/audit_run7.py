"""Final evidence captures: packing checklist + dispatch + fixes on More/EditHistory."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def swipe_up(dist=1400):
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


def open_ord1042():
    d.tap_text("Orders", wait=2.6, required=False)
    if not d.wait_for("ORD-1050", tries=6):
        go_home()
        d.tap_text("Orders", wait=2.6)
    for _ in range(10):
        if d.bounds_of("ORD-1042"):
            break
        swipe_up(1200)
    if not d.bounds_of("ORD-1042"):
        # The row may be grouped further down; use Order History filter (E-05 c6).
        d.tap_text("Order History", wait=2.2, required=False)
        time.sleep(1.2)
        for _ in range(8):
            if d.bounds_of("ORD-1042"):
                break
            swipe_up(1200)
    d.tap_text("ORD-1042", wait=2.8)
    time.sleep(1.2)
    for _ in range(8):
        if d.bounds_of("Pack") or d.bounds_of("Ship"):
            break
        swipe_up()


print("== T6: packing (via Order History if needed) ==")
open_ord1042()
if d.tap_text("Pack", wait=2.8):
    time.sleep(1.4)
    d.shot("t6-01-packing.png")
    ts = d.texts()
    j = " | ".join(ts)
    for needle in ("MCKP", "SCWH", "Freebie", "of 3 packed", "TO PREP", "Confirm packing complete"):
        print(f"  packing has {needle!r}:", needle in j)
    d.tap_text("Confirm packing complete", wait=2.8, required=False)
    d.shot("t6-02-packed.png")
    ts = d.texts()
    print("  packed:", [t for t in ts if "PACKED" in t][:4])
    back()
else:
    print("  !! Pack not reachable")

print("== T7 finish: dispatch ==")
for _ in range(7):
    if d.bounds_of("Ship"):
        break
    swipe_up()
if d.tap_text("Ship", wait=3.0):
    time.sleep(1.6)
    # Scroll the shipping record to reach Copy all fields / Confirm dispatch.
    for _ in range(4):
        if d.bounds_of("Confirm dispatch"):
            break
        swipe_up(1000)
    d.shot("t7-01-shipping.png")
    ts = d.texts()
    j = " | ".join(ts)
    print("  dispatch visible:", "Confirm dispatch" in j)
    print("  tracking seeded:", "2612 3456 7890" in j)
    d.tap_text("Confirm dispatch", wait=2.8, required=False)
    d.shot("t7-02-dispatch.png")
    ts = d.texts()
    j = " | ".join(ts)
    print("  after dispatch FOR PICKUP:", "FOR PICKUP" in j)

print("== FIX verification: Edit history export (J-06 c8) ==")
back()
back()
d.tap_text("More", wait=2.4, required=False)
if not d.bounds_of("Edit history"):
    go_home()
    d.tap_text("More", wait=2.4)
d.tap_text("Edit history", wait=2.4)
time.sleep(1.2)
d.shot("fix-01-edit-history.png")
ts = d.texts()
print("  history screen:", ts[:10])
d.tap_text("Export", wait=2.2)
time.sleep(1.4)
d.shot("fix-02-export-confirm.png")
ts = d.texts()
print("  export msg:", [t for t in ts if "Exported" in t or ".csv" in t][:3])

print("== FIX verification: data checks Fix button (J-03 c4) ==")
back()
time.sleep(1.0)
# Scroll More to the Data checks card.
for _ in range(6):
    if d.bounds_of("Data checks"):
        break
    swipe_up(1000)
d.shot("fix-03-data-checks.png")
ts = d.texts()
j = " | ".join(ts)
print("  findings shown:", [t for t in ts if "marked Available" in t][:3])
if d.tap_text("Fix", wait=2.2, required=False):
    d.shot("fix-04-fixed.png")
    ts = d.texts()
    print("  fix msg:", [t for t in ts if "Corrected" in t][:3])
else:
    print("  (no Fix button visible - no findings on seed)")

print("== FIX verification: editable rates (G-02 c3/c6) ==")
for _ in range(8):
    if d.bounds_of("Profit markup"):
        break
    swipe_up(1000)
d.shot("fix-05-pricing.png")
d.tap_text("30%", wait=1.6, required=False)
time.sleep(1.0)
d.shot("fix-06-rate-editor.png")
