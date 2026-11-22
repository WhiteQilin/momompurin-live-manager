"""Live verification part 1 (redo): Show toggle on a NON-EMPTY password field.

The previous run typed the password into the email field (tap landed on the email
box because password-first ordering put the tap before the layout settled), so the
"reveal" was never actually exercised on a filled field. This script:
  1. types the EMAIL first,
  2. re-dumps and taps the box under the PASSWORD label,
  3. types the password,
  4. asserts DOTS are on screen  -> screenshot,
  5. taps Show, asserts the plain text is on screen and the toggle reads Hide,
  6. taps Hide, asserts dots return.
"""
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = r"C:\Users\Gabri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path(__file__).resolve().parent.parent / "docs" / "visual_qa" / "audit"
sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def run(*a, t=40):
    return subprocess.run([ADB, *a], capture_output=True, text=True,
                          timeout=t, encoding="utf8", errors="replace").stdout


def dump():
    run("shell", "uiautomator", "dump", "/sdcard/ui.xml", t=25)
    x = run("shell", "cat", "/sdcard/ui.xml", t=25)
    return (x.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&quot;", '"').replace("&apos;", "'"))


def texts():
    return [t for t in re.findall(r'text="([^"]+)"', dump()) if t.strip()]


def bounds(label):
    x = dump()
    m = re.search(rf'text="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        a, b, c, d = map(int, m.groups())
        return (a + c) // 2, (b + d) // 2
    return None


def tap_xy(x, y, wait=1.2):
    run("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def tap(label, wait=1.4):
    p = bounds(label)
    if p is None:
        print(f"  !! not found {label!r}")
        return False
    tap_xy(p[0], p[1], wait)
    return True


def shot(name):
    (OUT / name).write_bytes(
        subprocess.run([ADB, "exec-out", "screencap", "-p"],
                       capture_output=True, timeout=40).stdout)
    print(f"  [shot] {name}")


run("shell", "pm", "clear", "ph.edu.umak.momompurin")
run("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
time.sleep(5)

print("== step 1: email first ==")
p = bounds("Email or username")
assert p, "email label missing"
tap_xy(p[0], p[1] + 70, 0.8)
run("shell", "input", "text", "katrinajj254@gmail.com")
time.sleep(0.6)
print("  fields now:", [t for t in texts() if "katrina" in t.lower() or "Enter" in t])

print("\n== step 2: password field (anchored on the EMAIL BOX bottom) ==")
# The layout shifts while the IME is up, so a fixed +70 offset from the Password
# label landed inside the email box. Anchor on the email VALUE node instead: the
# password box starts about 90px below where the email text ends.
x = dump()
m = re.search(
    r'text="katrinajj254@gmail\.com"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
assert m, "email value node not found"
a, b, c, d = map(int, m.groups())
tap_xy((a + c) // 2, d + 150, 1.0)
run("shell", "input", "text", "katrina@")
time.sleep(1.0)
run("shell", "input", "keyevent", "111")  # dismiss IME only
time.sleep(1.4)

tx = texts()
dots = [t for t in tx if "•" in t]
print("  email field holds:", [t for t in tx if "gmail.com" in t])
print("  masked field shows dots:", bool(dots), dots[:2])
print("  plain password on screen:", any("katrina@" in t for t in tx), "(must be False)")
shot("fix2-03-masked-before-show.png")
assert dots, "password must be masked before Show"

print("\n== step 3: tap Show ==")
assert tap("Show", 1.6), "Show not found"
time.sleep(0.8)
tx = texts()
plain = [t for t in tx if "katrina@" in t]
dots_after = [t for t in tx if "•" in t]
print("  plain text revealed:", bool(plain), plain[:2])
print("  dots remaining:", bool(dots_after))
print("  toggle reads Hide:", any(t == "Hide" for t in tx))
shot("fix2-04-revealed-after-show.png")
assert plain and not dots_after, "Show must reveal the typed text"
assert any(t == "Hide" for t in tx), "toggle must read Hide after revealing"

print("\n== step 4: tap Hide ==")
assert tap("Hide", 1.6), "Hide not found"
time.sleep(0.8)
tx = texts()
print("  dots back:", any("•" in t for t in tx), "(must be True)")
print("  toggle reads Show:", any(t == "Show" for t in tx))
shot("fix2-05-masked-again.png")
assert any("•" in t for t in tx), "Hide must re-mask"

print("\nRESULT: Show/Hide toggle works both ways on a filled field")
