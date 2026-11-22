"""T3 retry: scroll the orders list to ORD-1042, verify detail + history.
Then T5-T8: invoice, export, packing, shipping, shared views."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def kb():
    d.run("shell", "input", "keyevent", "111")
    time.sleep(1.0)


def swipe_up():
    d.run("shell", "input", "swipe", "540", "1900", "540", "500", "400")
    time.sleep(1.0)


# State: coming back from product detail -> we are on the stock screen or orders.
d.tap_text("Orders", wait=2.4, required=False)
if not d.wait_for("Order History", tries=8):
    d.run("shell", "input", "keyevent", "4")
    time.sleep(1.2)
    d.tap_text("Orders", wait=2.4)
d.wait_for("ORD-1050", tries=8)
time.sleep(0.6)

print("== T3: scroll to ORD-1042 ==")
found = False
for i in range(8):
    if d.bounds_of("ORD-1042"):
        found = True
        break
    swipe_up()
d.shot("t3-01-orders-scrolled.png")
print("  ORD-1042 visible:", found)
if found:
    d.tap_text("ORD-1042", wait=2.4)
    time.sleep(1.0)
    d.shot("t3-02-detail-top.png")
    ts = d.texts()
    joined = " | ".join(ts)
    for needle in ("880", "280", "CLAIMED", "PAID WITH BALANCE", "MP-1042"):
        print(f"  has {needle!r}:", needle in joined)
    swipe_up()
    d.shot("t3-03-detail-status.png")
    ts = d.texts()
    joined = " | ".join(ts)
    for needle in ("TO PREP", "RESERVED", "NOT DISPATCHED", "View status history"):
        print(f"  status has {needle!r}:", needle in joined)
    d.tap_text("View status history", wait=2.2, required=False)
    d.shot("t3-04-history.png")
    ts = d.texts()
    print("  history:", ts[:18])
    d.run("shell", "input", "keyevent", "4")
    time.sleep(1.2)

print("== T5: export + share from ORD-1042 ==")
d.tap_text("Invoice", wait=2.0, required=False)
time.sleep(1.0)
d.shot("t5-01-invoice.png")
ts = d.texts()
joined = " | ".join(ts)
for needle in ("MP-1042", "MCKP", "SCWH", "160", "600", "880", "PAID WITH BALANCE"):
    print(f"  invoice has {needle!r}:", needle in joined)
d.tap_text("Review & issue", wait=2.0, required=False)
d.shot("t5-02-review.png")
ts = d.texts()
print("  review:", ts[:14])
d.tap_text("Confirm & issue", wait=2.2, required=False)
d.shot("t5-03-issued.png")
ts = d.texts()
joined = " | ".join(ts)
for needle in ("880", "600", "280", "PAID WITH BALANCE"):
    print(f"  issued has {needle!r}:", needle in joined)
d.tap_text("Export invoice", wait=2.2, required=False)
d.shot("t5-04-export.png")
ts = d.texts()
print("  export screen:", ts[:12])
d.tap_text("Export image", wait=2.4, required=False)
d.shot("t5-05-image.png")
ts = d.texts()
print("  image preview:", ts[:12])
