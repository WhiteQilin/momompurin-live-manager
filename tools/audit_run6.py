"""T6-T8 final: relaunch, sign in once, then drive each detour from the detail."""
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
    d.run("shell", "input", "keyevent", "3")  # HOME
    time.sleep(1.0)
    d.run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
    time.sleep(2.5)


def open_ord1042():
    """App relaunch resumes the last screen (A-02 c7). First, get to Orders."""
    d.tap_text("Orders", wait=2.6, required=False)
    if not d.wait_for("ORD-1050", tries=6):
        go_home()
        d.tap_text("Orders", wait=2.6)
    d.wait_for("ORD-1050", tries=8)
    for _ in range(9):
        if d.bounds_of("ORD-1042"):
            break
        swipe_up()
    d.tap_text("ORD-1042", wait=2.6)
    time.sleep(1.2)
    for _ in range(7):
        if d.bounds_of("Pack") or d.bounds_of("Invoice"):
            break
        swipe_up()


d.run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
time.sleep(3)
ts = d.texts()
print("start screen:", ts[:6])
if not any("ORD-" in t for t in ts) and not any("LOG IN" in t for t in ts):
    pass  # dashboard or detail; fine

print("== T6: packing checklist ==")
open_ord1042()
d.tap_text("Pack", wait=2.6)
time.sleep(1.4)
d.shot("t6-01-packing.png")
ts = d.texts()
j = " | ".join(ts)
for needle in ("MCKP", "SCWH", "Freebie", "of 3 packed", "TO PREP", "Confirm packing complete"):
    print(f"  packing has {needle!r}:", needle in j)
d.tap_text("Confirm packing complete", wait=2.6)
d.shot("t6-02-packed.png")
ts = d.texts()
print("  packed state:", [t for t in ts if "PACKED" in t or "packed" in t][:5])
back()

print("== T7: shipping record ==")
time.sleep(0.6)
for _ in range(7):
    if d.bounds_of("Ship"):
        break
    swipe_up()
if not d.tap_text("Ship", wait=3.0):
    open_ord1042()
    for _ in range(7):
        if d.bounds_of("Ship"):
            break
        swipe_up()
    d.tap_text("Ship", wait=3.0)
time.sleep(1.6)
d.shot("t7-01-shipping.png")
ts = d.texts()
j = " | ".join(ts)
for needle in ("J&T", "Pia Santos", "0917 555 0142", "Sampaguita", "₱120.00", "Copy all fields", "2612 3456 7890"):
    print(f"  shipping has {needle!r}:", needle in j)
d.tap_text("Copy all fields", wait=2.0, required=False)
d.shot("t7-02-copy.png")
ts = d.texts()
print("  copy view:", ts[:16])
d.tap_text("Confirm dispatch", wait=2.8, required=False)
d.shot("t7-03-dispatch.png")
ts = d.texts()
j = " | ".join(ts)
print("  dispatch has 'FOR PICKUP':", "FOR PICKUP" in j)
print("  dispatch has '2612 3456 7890':", "2612 3456 7890" in j)
back()

print("== T8: shared view ==")
for _ in range(7):
    if d.bounds_of("Shared view"):
        break
    swipe_up()
d.tap_text("Shared view", wait=3.0, required=False)
time.sleep(1.6)
d.shot("t8-01-shared-invoice.png")
ts = d.texts()
j = " | ".join(ts)
for needle in ("MP-1042", "Pia Santos", "₱880.00", "280"):
    print(f"  shared has {needle!r}:", needle in j)
print("  'Edit' control absent:", "Edit" not in j)
back()
