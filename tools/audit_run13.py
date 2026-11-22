"""T7 final-final: clear stray char via select-all, dispatch."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def swipe_up(dist=800):
    d.run("shell", "input", "swipe", "540", "1900", "540", str(1900 - dist), "400")
    time.sleep(1.0)


# Focus the tracking field and select-all + retype.
d.tap_below("Tracking number", dy=70, wait=1.0)
# Select all (Ctrl+A via meta state) then delete.
d.run("shell", "input", "keycombination", "113 29")  # CTRL+A
time.sleep(0.4)
d.run("shell", "input", "keyevent", "67")  # DEL
time.sleep(0.5)
d.run("shell", "input", "text", "JT261234567890")
time.sleep(0.6)
# Close the IME with BACK (closes only the IME when it is open).
d.run("shell", "input", "keyevent", "4")
time.sleep(1.6)
d.shot("t7-09-clean.png")
ts = d.texts()
print("  tracking:", [t for t in ts if "2612" in t][:3])
for _ in range(5):
    if d.bounds_of("Confirm dispatch"):
        break
    swipe_up(700)
d.tap_text("Confirm dispatch", wait=3.4)
time.sleep(1.8)
d.shot("t7-10-dispatched.png")
ts = d.texts()
j = " | ".join(ts)
print("  FOR PICKUP:", "FOR PICKUP" in j)
print("  msgs:", [t for t in ts if "saved" in t.lower() or "dispatch" in t.lower()][:5])
