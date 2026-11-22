"""T7 final: fix tracking, confirm dispatch."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def swipe_up(dist=800):
    d.run("shell", "input", "swipe", "540", "1900", "540", str(1900 - dist), "400")
    time.sleep(1.0)


# Clear the stray character.
d.run("shell", "input", "keyevent", "67")  # one DEL
time.sleep(0.6)
d.run("shell", "input", "keyevent", "111")  # dismiss IME
time.sleep(1.4)
d.shot("t7-07-tracking-fixed.png")
ts = d.texts()
print("  tracking:", [t for t in ts if "2612" in t][:3])
for _ in range(5):
    if d.bounds_of("Confirm dispatch"):
        break
    swipe_up(700)
d.tap_text("Confirm dispatch", wait=3.2)
time.sleep(1.6)
d.shot("t7-08-dispatched.png")
ts = d.texts()
j = " | ".join(ts)
print("  FOR PICKUP:", "FOR PICKUP" in j)
print("  any saved msg:", [t for t in ts if "saved" in t.lower() or "Shipment" in t][:4])
