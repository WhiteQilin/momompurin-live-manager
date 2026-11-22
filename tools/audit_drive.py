"""Audit-capture driver: walks the walkthrough guide + the fix verification paths.

Screenshots land in docs/visual_qa/audit/. Each step re-dumps the UI tree so taps
resolve against the CURRENT screen (never a stale one).
"""
import re
import subprocess
import sys
import time
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ADB = r"C:\Users\Gabri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path(__file__).resolve().parent.parent / "docs" / "visual_qa" / "audit"
OUT.mkdir(parents=True, exist_ok=True)

PKG = "ph.edu.umak.momompurin"


def run(*args, timeout=30):
    return subprocess.run(
        [ADB, *args], capture_output=True, text=True, timeout=timeout, encoding="utf8", errors="replace"
    ).stdout


def dump():
    run("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=20)
    xml = run("shell", "cat", "/sdcard/ui.xml", timeout=20)
    xml = xml.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"').replace("&apos;", "'")
    return xml


def texts():
    """All non-empty text attributes on screen."""
    xml = dump()
    return [t for t in re.findall(r'text="([^"]+)"', xml) if t.strip()]


def bounds_of(node_text):
    """Center (x, y) of the first node whose text matches."""
    xml = dump()
    m = re.search(
        r'text="' + re.escape(node_text) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    )
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    # content-desc fallback
    m = re.search(
        r'content-desc="' + re.escape(node_text) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    )
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap_below(label, dy=90, wait=1.2):
    """Tap the input box under a label-above field (the label node itself is
    not the editable box)."""
    xml = dump()
    m = re.search(
        r'text="' + re.escape(label) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    )
    if not m:
        print(f"  !! label not found for tap_below: {label!r}")
        return False
    x1, y1, x2, y2 = map(int, m.groups())
    x = (x1 + x2) // 2
    y = y2 + dy
    run("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)
    return True


def clear_field(label, dy=90):
    """Focus the box under the label and clear it."""
    if tap_below(label, wait=0.6):
        run("shell", "input", "keyevent", "123")  # move to end
        for _ in range(40):
            run("shell", "input", "keyevent", "67")  # DEL
        return True
    return False


def tap_text(label, wait=1.6, required=True):
    pos = bounds_of(label)
    if pos is None:
        if required:
            print(f"  !! NOT FOUND: {label!r}; on-screen: {texts()[:12]}")
        return False
    run("shell", "input", "tap", str(pos[0]), str(pos[1]))
    time.sleep(wait)
    return True


def tap_xy(x, y, wait=1.6):
    run("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def back(wait=1.6):
    run("shell", "input", "keyevent", "4")
    time.sleep(wait)


def shot(name):
    (OUT / name).write_bytes(
        subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, timeout=30).stdout
    )
    print(f"  [shot] {name}")


def wait_for(needle, tries=10, delay=1.0):
    for _ in range(tries):
        ts = texts()
        if any(needle in t for t in ts):
            return True
        time.sleep(delay)
    print(f"  !! wait_for missed: {needle!r}; on-screen: {ts[:12]}")
    return False
