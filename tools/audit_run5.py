"""T5-T8 via the detail action buttons: Invoice / Export / Pack / Ship / Shared view."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def kb():
    d.run("shell", "input", "keyevent", "111")
    time.sleep(1.0)


def swipe_up(dist=1400):
    d.run("shell", "input", "swipe", "540", "1900", "540", str(1900 - dist), "400")
    time.sleep(1.0)


def back():
    d.run("shell", "input", "keyevent", "4")
    time.sleep(1.4)


def open_ord1042():
    d.tap_text("Orders", wait=2.4)
    d.wait_for("ORD-1050", tries=8)
    for _ in range(8):
        if d.bounds_of("ORD-1042"):
            break
        d.run("shell", "input", "swipe", "540", "1900", "540", "500", "400")
        time.sleep(0.9)
    d.tap_text("ORD-1042", wait=2.4)
    time.sleep(1.0)


print("== T5: invoice -> export ==")
open_ord1042()
# Action buttons sit at the bottom of the detail; scroll to them.
for _ in range(6):
    if d.bounds_of("Invoice"):
        break
    swipe_up()
d.shot("t5-00-action-row.png")
d.tap_text("Invoice", wait=2.4)
time.sleep(1.0)
d.shot("t5-01-invoice.png")
ts = d.texts()
j = " | ".join(ts)
for needle in ("MP-1042", "MCKP", "SCWH", "₱160.00", "₱600.00", "₱880.00", "PAID WITH BALANCE"):
    print(f"  invoice has {needle!r}:", needle in j)
back()

print("== T5b: Export screen ==")
for _ in range(6):
    if d.bounds_of("Export"):
        break
    swipe_up()
d.tap_text("Export", wait=2.4)
time.sleep(1.2)
d.shot("t5-02-export.png")
ts = d.texts()
print("  export screen:", ts[:14])
d.tap_text("Export image", wait=2.6, required=False)
d.shot("t5-03-image.png")
ts = d.texts()
j = " | ".join(ts)
for needle in ("Pia Santos", "ORD-1042", "₱880.00", "PAID WITH BALANCE"):
    print(f"  image preview has {needle!r}:", needle in j)
back()
back()

print("== T6: packing checklist ==")
# We are on the detail again (or its parent); navigate back to the detail.
for _ in range(6):
    if d.bounds_of("Pack"):
        break
    swipe_up()
if not d.bounds_of("Pack"):
    open_ord1042()
    for _ in range(6):
        if d.bounds_of("Pack"):
            break
        swipe_up()
d.tap_text("Pack", wait=2.4)
time.sleep(1.2)
d.shot("t6-01-packing.png")
ts = d.texts()
j = " | ".join(ts)
for needle in ("MCKP", "SCWH", "Freebie sticker", "of 3 packed", "TO PREP"):
    print(f"  packing has {needle!r}:", needle in j)
d.tap_text("Confirm packing complete", wait=2.2, required=False)
d.shot("t6-02-packed.png")
ts = d.texts()
j = " | ".join(ts)
print("  after complete:", [t for t in ts if "packed" in t.lower() or "PACKED" in t][:4])
back()

print("== T7: shipping record ==")
for _ in range(6):
    if d.bounds_of("Ship"):
        break
    swipe_up()
d.tap_text("Ship", wait=2.6)
time.sleep(1.4)
d.shot("t7-01-shipping.png")
ts = d.texts()
j = " | ".join(ts)
for needle in ("J&T Express", "Pia Santos", "0917 555 0142", "Sampaguita", "₱120.00", "Copy all fields"):
    print(f"  shipping has {needle!r}:", needle in j)
d.tap_text("Copy all fields", wait=1.8, required=False)
d.shot("t7-02-copy-fields.png")
ts = d.texts()
print("  after copy:", ts[:14])
d.tap_text("Confirm dispatch", wait=2.4, required=False)
d.shot("t7-03-dispatch.png")
ts = d.texts()
j = " | ".join(ts)
for needle in ("JT 2612 3456 7890", "FOR PICKUP"):
    print(f"  dispatch has {needle!r}:", needle in j)
