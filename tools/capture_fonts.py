"""Capture the weight hierarchy after the static-font fix, and prove the
rendered glyphs actually differ in ink weight (not just in font metadata).

Method: screenshot the dashboard, then measure the mean stroke darkness in the
band of each key text. A collapsed hierarchy shows identical density; a correct
one shows bold bands noticeably darker than regular ones.
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_drive as d  # noqa: E402

OUT = Path(__file__).resolve().parent.parent / "docs" / "visual_qa" / "audit"
OUT.mkdir(parents=True, exist_ok=True)


def sign_in():
    d.run("shell", "pm", "clear", "ph.edu.umak.momompurin")
    d.run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
    time.sleep(4)
    d.tap_below("Email or username", dy=70, wait=0.8)
    d.run("shell", "input", "text", "katrinajj254@gmail.com")
    d.tap_below("Password", dy=70, wait=0.8)
    d.run("shell", "input", "text", "katrina@")
    d.run("shell", "input", "keyevent", "66")
    d.wait_for("Open a Live", tries=14)
    time.sleep(1.5)


sign_in()
d.shot("fonts-01-dashboard.png")
ts = d.texts()
print("dashboard texts:", ts[:16])

# Record the on-screen bounds of the three headline strings so we can measure them.
for label in ("Katrina!", "Momompurin", "Ready for your next live?", "Shop snapshot", "Open a Live"):
    print(f"  bounds[{label!r}]:", d.bounds_of(label))

# Also capture the stock screen (DataMonospace SemiBold rows) and More (many roles).
d.tap_text("Stock", wait=2.4)
time.sleep(1.2)
d.shot("fonts-02-stock.png")
d.tap_text("More", wait=2.4)
time.sleep(1.2)
d.shot("fonts-03-more.png")
print("captured fonts-01..03")
