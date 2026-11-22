"""Full clean run: T1-T3 with pm clear (guaranteed pre-auth seed)."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402


def kb_dismiss():
    d.run("shell", "input", "keyevent", "111")  # ESC closes the IME, keeps the screen
    time.sleep(1.0)


d.run("shell", "pm", "clear", "ph.edu.umak.momompurin")
d.run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
time.sleep(4)

print("== T1: sign in ==")
d.shot("t1-01-signin.png")
d.tap_below("Email or username", dy=70, wait=0.8)
d.run("shell", "input", "text", "katrinajj254@gmail.com")
d.tap_below("Password", dy=70, wait=0.8)
d.run("shell", "input", "text", "katrina@")
d.shot("t1-02-credentials-typed.png")
d.run("shell", "input", "keyevent", "66")
d.wait_for("Open a Live", tries=14)
time.sleep(1.4)
d.shot("t1-03-dashboard.png")
ts = d.texts()
ok1 = all(any(n in t for t in ts) for n in ("Open a Live", "Sign out", "To pack"))
print("  T1 dashboard OK:", ok1)

print("== T2: claim ==")
d.tap_text("Open a Live")
d.wait_for("Customer Name *", tries=8)
time.sleep(0.6)
d.shot("t2-01-claim-form.png")
d.tap_below("Customer Name *", dy=70, wait=0.8)
d.run("shell", "input", "text", "be")
time.sleep(1.6)
d.shot("t2-02-suggest.png")
d.tap_text("Bea Cruz · @beacruz", wait=1.4)
kb_dismiss()
ts = d.texts()
print("  on claim:", ts[:14])
# The claimable card shows the product name; tap it.
d.tap_text("Chiikawa Wipes", wait=1.4, required=False)
kb_dismiss()
d.shot("t2-03-item-added.png")
ts = d.texts()
print("  lines now:", [t for t in ts if "₱" in t][:6])
d.tap_text("Save Claim", wait=2.6)
ok = d.wait_for("Claim saved", tries=10)
time.sleep(0.8)
d.shot("t2-04-claim-saved.png")
ts = d.texts()
print("  T2 saved:", [t for t in ts if "Claim saved" in t][:2])

print("== T3: ORD-1042 history ==")
d.tap_text("Orders", wait=2.2)
d.wait_for("ORD-1042", tries=8)
time.sleep(0.8)
d.shot("t3-01-orders.png")
d.tap_text("ORD-1042", wait=2.2)
d.wait_for("Payment", tries=8)
time.sleep(1.0)
d.shot("t3-02-detail.png")
ts = d.texts()
joined = " | ".join(ts)
for needle in ("880", "280", "CLAIMED", "PAID WITH BALANCE", "MP-1042"):
    print(f"  has {needle!r}:", needle in joined)
d.shot("t3-03-scrolled.png")
