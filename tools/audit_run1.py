"""Walkthrough T1-T2: IME Done submits sign-in; scroll for claim fields."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402

d.run("shell", "pm", "clear", "ph.edu.umak.momompurin")
d.run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
time.sleep(4)

print("== T1: sign in and reach the dashboard ==")
d.shot("t1-01-signin.png")
d.tap_below("Email or username", dy=70, wait=0.8)
d.run("shell", "input", "text", "katrinajj254@gmail.com")
d.tap_below("Password", dy=70, wait=0.8)
d.run("shell", "input", "text", "katrina@")
time.sleep(0.5)
d.shot("t1-02-credentials-typed.png")
# The IME Done key (✓) triggers submit() via KeyboardActions(onDone) - the
# walkthrough's human path when the keyboard hides LOG IN.
d.run("shell", "input", "keyevent", "66")  # KEYCODE_ENTER = IME action Done
time.sleep(1.4)
d.shot("t1-03-signing-in.png")
ok = d.wait_for("Open a Live", tries=16)
time.sleep(1.5)
d.shot("t1-04-dashboard.png")
ts = d.texts()
print("  dashboard:", ts[:20])
for needle in ("Open a Live", "To pack", "Payments due", "Low stock", "Sign out"):
    print(f"  has {needle!r}:", any(needle in t for t in ts))

print("== T2: record a live claim ==")
if not d.tap_text("Open a Live"):
    sys.exit("dashboard did not open")
ok = d.wait_for("Save Claim", tries=10)
time.sleep(1.0)
d.shot("t2-01-claim-form.png")
d.tap_below("Customer Name *", dy=70, wait=1.0)
d.run("shell", "input", "text", "be")
time.sleep(1.8)
ts = d.texts()
print("  suggestions:", [t for t in ts if "Bea" in t or "beacruz" in t][:4])
d.shot("t2-02-customer-suggest.png")
d.tap_text("Bea Cruz · @beacruz", wait=1.4, required=False)
# Scroll down so the SKU field and Save are on screen.
d.run("shell", "input", "swipe", "540", "1800", "540", "700", "300")
time.sleep(1.0)
d.tap_below("SKU / Item Code *", dy=70, wait=1.0)
d.run("shell", "input", "text", "CHW")
time.sleep(1.8)
ts = d.texts()
print("  CHW row:", [t for t in ts if "CHW" in t][:4])
d.shot("t2-03-product-match.png")
d.tap_text("CHW · Chiikawa Wipes", wait=1.4, required=False)
time.sleep(0.6)
# dismiss keyboard so Save Claim is visible
d.run("shell", "input", "keyevent", "4")
time.sleep(1.0)
d.run("shell", "input", "swipe", "540", "1800", "540", "600", "300")
time.sleep(0.8)
d.shot("t2-04-before-save.png")
d.tap_text("Save Claim", wait=2.4)
ok = d.wait_for("Claim saved", tries=10)
time.sleep(0.6)
d.shot("t2-05-claim-saved.png")
ts = d.texts()
print("  saved confirmation:", [t for t in ts if "Claim saved" in t or "ORD-" in t][:4])
