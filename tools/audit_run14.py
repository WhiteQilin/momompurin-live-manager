"""Final fix verifications: More's Fix button + editable rates + invalid %."""
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


# From the shipping record: back twice to the Orders tab, then More.
back()
back()
d.tap_text("More", wait=2.6, required=False)
if not d.bounds_of("Data checks"):
    time.sleep(1.0)

print("== FIX 2: Fix button ==")
found = False
for _ in range(9):
    if d.bounds_of("Fix"):
        found = True
        break
    swipe_up(700)
print("  Fix button visible:", found)
if found:
    d.shot("fix-07-before.png")
    d.tap_text("Fix", wait=2.4)
    d.shot("fix-08-after.png")
    ts = d.texts()
    j = " | ".join(ts)
    print("  confirmation:", [t for t in ts if "Corrected" in t][:3])
    print("  finding cleared:", not any("marked Available with no stock" in t for t in ts))
    print("  everything consistent:", "Everything is consistent" in j)

print("== FIX 7: editable rates ==")
for _ in range(10):
    if d.bounds_of("Profit markup"):
        break
    swipe_up(700)
d.shot("fix-09-pricing-card.png")
ts = d.texts()
print("  rows:", [t for t in ts if "%" in t or "markup" in t.lower() or "Freebie" in t][:6])
# Tap the 30% row to open the editor.
if d.tap_text("30%", wait=1.8, required=False):
    d.shot("fix-10-editor.png")
    ts = d.texts()
    print("  editor:", [t for t in ts if "%" in t or "Save" in t][:5])
    # Type an invalid percentage and save.
    d.run("shell", "input", "text", "130")
    time.sleep(0.6)
    d.run("shell", "input", "keyevent", "111")
    time.sleep(1.2)
    # Commit via the field's Save action: re-open and tap Save inside.
    # Simpler: type and press IME Done.
    d.tap_text("30%", wait=1.2, required=False)
    if not d.bounds_of("(%)"):
        pass
    d.shot("fix-11-after-invalid.png")
    ts = d.texts()
    print("  invalid msg:", [t for t in ts if "Invalid percentage" in t][:3])
else:
    print("  !! could not open the rate editor")
