"""T7 dispatch completion: open Ship, save record, confirm dispatch."""
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


# On the order detail (status view). Find the action row.
found_ship = False
for _ in range(8):
    if d.bounds_of("Ship"):
        found_ship = True
        break
    swipe_up(700)
print("Ship visible:", found_ship)
if found_ship:
    d.tap_text("Ship", wait=3.0)
    time.sleep(1.6)
    d.shot("t7-01-record.png")
    ts = d.texts()
    j = " | ".join(ts)
    for needle in ("J&T Express", "Pia Santos", "0917 555 0142", "Save shipment", "Copy all fields"):
        print(f"  record has {needle!r}:", needle in j)
    # Scroll to the Save button.
    for _ in range(6):
        if d.bounds_of("Save shipment") or d.bounds_of("Save"):
            break
        swipe_up(800)
    d.shot("t7-02-record-bottom.png")
    ts = d.texts()
    print("  bottom:", [t for t in ts if "Save" in t or "Dispatch" in t or "Tracking" in t][:8])
    # Save the shipment record first.
    if d.tap_text("Save shipment", wait=2.6, required=False) or d.tap_text("Save", wait=2.6, required=False):
        d.shot("t7-03-saved.png")
        ts = d.texts()
        print("  saved:", [t for t in ts if "saved" in t.lower() or "Shipment" in t][:5])
    # Confirm dispatch.
    for _ in range(6):
        if d.bounds_of("Confirm dispatch"):
            break
        swipe_up(800)
    d.tap_text("Confirm dispatch", wait=3.0, required=False)
    d.shot("t7-04-dispatch.png")
    ts = d.texts()
    j = " | ".join(ts)
    print("  dispatch FOR PICKUP:", "FOR PICKUP" in j)
    print("  tracking verbatim:", "2612 3456 7890" in j)
