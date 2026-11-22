"""Small adb/uiautomator driver for the v2.1 live UI verification.

The app is Compose, so uiautomator sees real nodes with text and bounds. Every
helper here finds a node by its visible text (or content-desc) and taps its
centre, which is the same thing a tester's finger does - no test tags, no
back-door state writes.

Usage from a shell:  python tools/v21_drive.py <command> [args]
or import the helpers from a test module.
"""
from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "ph.edu.umak.momompurin"
ACTIVITY = f"{PKG}/.MainActivity"


def _adb_path() -> str:
    """Resolve adb without relying on PATH: Git-Bash's PATH has no platform-tools."""
    import os
    import shutil

    found = shutil.which("adb")
    if found:
        return found
    sdk = os.environ.get("LOCALAPPDATA", "")
    for cand in (
        os.path.join(sdk, "Android", "Sdk", "platform-tools", "adb.exe"),
        os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb.exe"),
    ):
        if os.path.isfile(cand):
            return cand
    raise RuntimeError("adb not found")


ADB = _adb_path()


def adb(*args: str, timeout: int = 60) -> str:
    out = subprocess.run(
        [ADB, *args], capture_output=True, text=True, timeout=timeout,
        encoding="utf-8", errors="replace",
    )
    return out.stdout


def dump() -> ET.Element:
    """The current view hierarchy.

    uiautomator serialises the live window, so it fails while the app is
    animating or immediately after a relaunch. Retrying with a rising delay is
    what makes a long sequential walk reliable: a fixed short sleep passes alone
    but fails on the fifth screen in a row.
    """
    last = ""
    for attempt in range(14):
        raw = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        m = re.search(r"<\?xml.*?</hierarchy>", raw, re.S)
        if m:
            try:
                return ET.fromstring(m.group(0))
            except ET.ParseError as exc:
                last = str(exc)
        else:
            last = raw.strip()[:80]
        time.sleep(0.35 + attempt * 0.25)

    # One last try through a file, which avoids the tty contention entirely.
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("shell", "cat", "/sdcard/ui.xml")
    m = re.search(r"<\?xml.*?</hierarchy>", raw, re.S)
    if m:
        try:
            return ET.fromstring(m.group(0))
        except ET.ParseError:
            pass
    raise RuntimeError(f"uiautomator dump failed ({last})")


# A uiautomator dump costs ~2.1s, and a caller typically reads the SAME frame
# several times (find, then has, then a bounds read). A short-lived cache makes
# those reads free while never serving a stale screen: every input action calls
# [invalidate], so a tap is always followed by a fresh dump.
_CACHE: dict = {"nodes": None, "at": 0.0}


def invalidate() -> None:
    _CACHE["nodes"] = None
    _CACHE["at"] = 0.0


def _parse(root: ET.Element) -> list[dict]:
    out = []
    for n in root.iter("node"):
        txt = (n.get("text") or "").strip()
        desc = (n.get("content-desc") or "").strip()
        b = n.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        out.append({
            "text": txt, "desc": desc, "cls": n.get("class") or "",
            "clickable": n.get("clickable") == "true",
            "enabled": n.get("enabled") == "true",
            "bounds": (x1, y1, x2, y2),
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            "w": x2 - x1, "h": y2 - y1,
        })
    return out


def nodes(root: ET.Element | None = None, *, fresh: bool = False) -> list[dict]:
    if root is not None:
        return _parse(root)
    if not fresh and _CACHE["nodes"] is not None and (time.time() - _CACHE["at"]) < 0.9:
        return _CACHE["nodes"]
    parsed = _parse(dump())
    _CACHE["nodes"] = parsed
    _CACHE["at"] = time.time()
    return parsed


def texts() -> list[str]:
    return [n["text"] or n["desc"] for n in nodes() if (n["text"] or n["desc"])]


def has(text: str, root: ET.Element | None = None) -> bool:
    return any(text in (n["text"] or n["desc"]) for n in nodes(root))


def find(text: str, exact: bool = False, root: ET.Element | None = None) -> dict | None:
    """The smallest node carrying `text` - a leaf beats its container."""
    cands = []
    for n in nodes(root):
        label = n["text"] or n["desc"]
        if not label:
            continue
        if (label == text) if exact else (text in label):
            cands.append(n)
    if not cands:
        return None
    return min(cands, key=lambda n: (n["w"] * n["h"], n["cy"]))


def tap(text: str, exact: bool = False, wait: float = 0.6) -> bool:
    n = find(text, exact)
    if not n:
        return False
    adb("shell", "input", "tap", str(n["cx"]), str(n["cy"]))
    time.sleep(wait)
    return True


def tap_xy(x: int, y: int, wait: float = 0.6) -> None:
    # Drop any cached frame BEFORE the tap and again AFTER it settles. The first
    # clear stops a pre-tap read; the second stops the pre-recomposition frame from
    # being served to the assertion that follows, because a tap changes the screen
    # asynchronously and an immediate dump can still show the old one.
    invalidate()
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)
    invalidate()


def type_text(text: str, wait: float = 0.4) -> None:
    """`input text` mangles spaces and shell metacharacters, so each char is sent.

    mobile_type_keys dropped characters elsewhere in this project; sending them
    one at a time with an explicit space escape is the reliable form.
    """
    safe = text.replace(" ", "%s").replace("'", "\\'").replace("&", "\\&")
    invalidate()
    adb("shell", "input", "text", safe)
    time.sleep(wait)
    invalidate()


def clear_field(text_node: dict) -> None:
    """Tap the field, select all, delete."""
    tap_xy(text_node["cx"], text_node["cy"], 0.3)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(60):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    time.sleep(0.2)
    invalidate()


def back(wait: float = 0.5) -> None:
    invalidate()
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    invalidate()
    time.sleep(wait)


def screenshot(path: str) -> None:
    with open(path, "wb") as f:
        f.write(subprocess.run(
            [ADB, "exec-out", "screencap", "-p"], capture_output=True
        ).stdout)


def restart_clean() -> None:
    adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(3.0)
    invalidate()


def foreground() -> str:
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"mResumedActivity.*?([\w.]+/[\w.$]+)", out)
    return m.group(1) if m else "?"


def bottom_bar_tabs() -> list[dict]:
    """The bottom bar's tappable slots, bottom-most row of the hierarchy."""
    ns = [n for n in nodes() if n["clickable"] and n["h"] > 40]
    if not ns:
        return []
    max_bottom = max(n["bounds"][3] for n in ns)
    bar = [n for n in ns if n["bounds"][3] > max_bottom - 200]
    return sorted(bar, key=lambda n: n["cx"])


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "texts"
    if cmd == "texts":
        for t in texts():
            print(t)
    elif cmd == "tap":
        print("tapped" if tap(sys.argv[2]) else "NOT FOUND")
    elif cmd == "shot":
        screenshot(sys.argv[2])
        print("saved", sys.argv[2])
    elif cmd == "fg":
        print(foreground())
    elif cmd == "bar":
        for t in bottom_bar_tabs():
            print(t["bounds"], t["text"] or t["desc"])
