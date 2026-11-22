"""T3-T9: order history, stock, invoice, export, packing, shipping, shared views."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def kb_dismiss():
    d.run("shell", "input", "keyevent", "111")
    time.sleep(1.0)


def bottom_tab(label, wait=2.2):
    """Tap a bottom-bar tab by its content-desc/text."""
    if d.tap_text(label, wait=wait, required=False):
        return True
    # The bottom bar labels are plain Text nodes; try the desc fallback.
    return False


# App state: on the claim form after T2's save (confirmation shown).
kb_dismiss()
print("== T3: ORD-1042 history ==")
d.tap_text("Orders", wait=2.4)
if not d.wait_for("ORD-1042", tries=8):
    # The claim-confirmation screen may still be up; go back first.
    d.run("shell", "input", "keyevent", "4")
    time.sleep(1.4)
    d.tap_text("Orders", wait=2.4)
d.wait_for("ORD-1042", tries=8)
time.sleep(0.8)
d.shot("t3-01-orders.png")
ts = d.texts()
print("  orders:", [t for t in ts if "ORD-" in t][:8])
d.tap_text("ORD-1042", wait=2.4)
time.sleep(1.0)
d.shot("t3-02-detail-top.png")
ts = d.texts()
joined = " | ".join(ts)
for needle in ("880", "280", "CLAIMED", "MP-1042"):
    print(f"  top has {needle!r}:", needle in joined)
# Scroll through the detail to reach the six status fields + history button.
d.run("shell", "input", "swipe", "540", "1900", "540", "500", "400")
time.sleep(1.0)
d.shot("t3-03-detail-status.png")
ts = d.texts()
joined = " | ".join(ts)
for needle in ("PAID WITH BALANCE", "TO PREP", "RESERVED", "View status history"):
    print(f"  status has {needle!r}:", needle in joined)
d.tap_text("View status history", wait=2.0, required=False)
d.shot("t3-04-history.png")
ts = d.texts()
print("  history rows:", ts[:16])
d.run("shell", "input", "keyevent", "4")
time.sleep(1.2)

print("== T4/T5: stock -> CHW pricing -> invoice ==")
d.tap_text("Stock", wait=2.4)
d.wait_for("Chiikawa", tries=8)
time.sleep(0.8)
d.shot("t4-01-stock.png")
ts = d.texts()
print("  stock rows:", [t for t in ts if "CHW" in t or "on hand" in t][:6])
d.tap_text("CHW · Chiikawa Wipes", wait=2.2, required=False)
time.sleep(1.0)
d.shot("t4-02-product-detail.png")
ts = d.texts()
joined = " | ".join(ts)
for needle in ("21.11", "0.63", "34.77", "This field is calculated automatically"):
    print(f"  pricing has {needle!r}:", needle in joined)
d.run("shell", "input", "keyevent", "4")
time.sleep(1.2)
