"""Tick the three packing boxes and confirm completion (G-08 c2/c5)."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402

# The three checkbox squares sit left of each item title; find each title's
# bounds and tap ~55px to its left, at the title's centre height.
titles = ["Melo Ku Charm Band", "Sanrio Cosmos Watch", "Freebie Sticker"]
for t in titles:
    xml = d.dump()
    m = None
    for mm in d.dump().split("\n"):
        pass
    pos = d.bounds_of(t)
    if pos is None:
        print(f"!! {t} not visible")
        continue
    x, y = pos
    # The checkbox column is at the left edge of the card, x≈90 on a 1080px screen.
    d.tap_xy(x - 200, y, wait=1.0)
time.sleep(0.8)
d.shot("t6-06-ticked.png")
ts = d.texts()
print("  progress:", [t for t in ts if "packed" in t.lower()][:3])
# Scroll to the button and tap it.
pos = d.bounds_of("Complete packing")
if pos:
    d.tap_xy(pos[0], pos[1], wait=2.4)
d.shot("t6-07-complete.png")
ts = d.texts()
j = " | ".join(ts)
print("  final:", [t for t in ts if "PACKED" in t or "packed" in t.lower()][:5])
print("  blocked message:", [t for t in ts if "unpacked" in t][:2])
