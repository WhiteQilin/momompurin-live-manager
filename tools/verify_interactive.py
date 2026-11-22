"""Live verification of the four interactive fixes on the emulator."""
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = r"C:\Users\Gabri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path(__file__).resolve().parent.parent / "docs" / "visual_qa" / "audit"
OUT.mkdir(parents=True, exist_ok=True)
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
    for attr in ("text", "content-desc"):
        m = re.search(rf'{attr}="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
        if m:
            a, b, c, d = map(int, m.groups())
            return (a + c) // 2, (b + d) // 2
    return None


def tap(label, wait=1.6, required=True):
    p = bounds(label)
    if p is None:
        if required:
            print(f"  !! not found {label!r}; saw {texts()[:10]}")
        return False
    run("shell", "input", "tap", str(p[0]), str(p[1]))
    time.sleep(wait)
    return True


def tap_below(label, dy=70, wait=1.2):
    x = dump()
    m = re.search(rf'text="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if not m:
        print(f"  !! label missing for tap_below: {label!r}")
        return False
    a, b, c, d = map(int, m.groups())
    run("shell", "input", "tap", str((a + c) // 2), str(d + dy))
    time.sleep(wait)
    return True


def shot(name):
    (OUT / name).write_bytes(
        subprocess.run([ADB, "exec-out", "screencap", "-p"],
                       capture_output=True, timeout=40).stdout)
    print(f"  [shot] {name}")


def wait_for(needle, tries=12, delay=1.0):
    for _ in range(tries):
        if any(needle in t for t in texts()):
            return True
        time.sleep(delay)
    print(f"  !! wait_for missed {needle!r}; saw {texts()[:10]}")
    return False


print("== FIX 1a: placeholders are neutral ==")
tx = texts()
print("  empty-field placeholders:", [t for t in tx if "Enter" in t])
leak = any("katrinajj254@gmail.com" == t for t in tx)
print(f"  real email visible as placeholder: {leak}  (must be False)")

print("\n== FIX 1b: Show reveals the password ==")
tap_below("Password", 70, 0.8)
run("shell", "input", "text", "katrina@")
time.sleep(1.0)
run("shell", "input", "keyevent", "111")  # close IME
time.sleep(1.2)
masked = texts()
print("  before Show:", [t for t in masked if "•" in t or "katrina" in t])
shot("fix2-01-password-masked.png")

tap("Show", wait=1.6)
time.sleep(1.0)
after = texts()
revealed = [t for t in after if "katrina@" in t]
still_dots = [t for t in after if "•" in t]
print("  after Show -> plain text present:", bool(revealed), revealed[:2])
print("  after Show -> dots still present:", bool(still_dots), still_dots[:2])
print("  toggle now reads Hide:", any(t == "Hide" for t in after))
shot("fix2-02-password-revealed.png")

# Hide again to confirm it is a real two-way toggle.
tap("Hide", wait=1.4, required=False)
time.sleep(0.8)
back = texts()
print("  after Hide -> dots again:", any("•" in t for t in back))

print("\n== sign in ==")
tap_below("Email or username", 70, 0.8)
run("shell", "input", "text", "katrinajj254@gmail.com")
run("shell", "input", "keyevent", "66")
wait_for("Open a Live", 14)
time.sleep(1.2)
