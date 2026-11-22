"""T7 finish: type tracking number, confirm dispatch, verify FOR PICKUP."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def swipe_up(dist=800):
    d.run("shell", "input", "swipe", "540", "1900", "540", str(1900 - dist), "400")
    time.sleep(1.0)


# State: shipping record, tracking field visible.
d.tap_below("Tracking number", dy=70, wait=1.0)
d.run("shell", "input", "text", "JT261234567890")
time.sleep(0.8)
d.run("shell", "input", "keyevent", "111")  # dismiss IME
time.sleep(1.2)
d.shot("t7-05-tracking-typed.png")
ts = d.texts()
print("  tracking field:", [t for t in ts if "2612" in t or "JT" in t][:3])
# Confirm dispatch is at the bottom.
for _ in range(5):
    if d.bounds_of("Confirm dispatch"):
        break
    swipe_up(700)
d.tap_text("Confirm dispatch", wait=3.0)
time.sleep(1.4)
d.shot("t7-06-dispatched.png")
ts = d.texts()
j = " | ".join(ts)
print("  FOR PICKUP:", "FOR PICKUP" in j)
print("  saved msg:", [t for t in ts if "saved" in t.lower()][:3])
print("  tracking shown:", "2612" in j)
