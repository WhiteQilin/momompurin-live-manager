#!/usr/bin/env python3
"""Read the emulator screen as text. Usage: ui.py [--shot name]"""
import re, subprocess, sys, os, io

SDK = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb.exe")
OUT = r"C:\Users\Gabri\AppData\Local\Temp\ui.xml"
SHOT = r"C:\Users\Gabri\AppData\Local\Temp\shot.png"

def sh(*args, check=False):
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    return subprocess.run([ADB, "-e", *args], capture_output=True, text=True,
                          env=env, check=check)

def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/w.xml")
    sh("pull", "/sdcard/w.xml", OUT)
    return io.open(OUT, encoding="utf-8", errors="replace").read()

def main():
    xml = dump()
    if "--shot" in sys.argv:
        sh("shell", "screencap", "-p", "/sdcard/s.png")
        sh("pull", "/sdcard/s.png", SHOT)
        print(f"[screenshot] {SHOT}")
    # Collect text + bounds, keeping document order.
    pat = re.compile(
        r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    rows = []
    for m in pat.finditer(xml):
        t = m.group(1)
        if not t.strip():
            continue
        x1, y1, x2, y2 = (int(m.group(i)) for i in range(2, 6))
        rows.append((y1, x1, t, (x1 + x2) // 2, (y1 + y2) // 2))
    rows.sort()
    out = io.StringIO()
    for y1, x1, t, cx, cy in rows:
        out.write(f"y{y1:4d} x{x1:4d} tap({cx},{cy})  {t}\n")
    sys.stdout.write(out.getvalue())
    # Also list clickable nodes that carry no text (icon buttons).
    click_pat = re.compile(
        r'<node[^>]*?clickable="true"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    bare = []
    for m in click_pat.finditer(xml):
        seg = xml[m.start():m.start() + 400]
        tm = re.search(r'text="([^"]*)"', seg)
        label = (tm.group(1) if tm else "") or ""
        x1, y1, x2, y2 = (int(m.group(i)) for i in range(1, 5))
        if not label.strip() and (x2 - x1) > 20 and (y2 - y1) > 20:
            bare.append(((x1 + x2) // 2, (y1 + y2) // 2, x2 - x1, y2 - y1))
    if bare:
        sys.stdout.write("\n[clickable, no text]\n")
        for cx, cy, w, h in bare:
            sys.stdout.write(f"  tap({cx},{cy})  {w}x{h}\n")

if __name__ == "__main__":
    main()
