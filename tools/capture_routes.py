#!/usr/bin/env python3
"""Drive the emulator through the built routes and capture a PNG each.

Usage: python tools/capture_routes.py [route ...]
With no args it captures the full set. Screenshots land in docs/visual_qa/android/.
"""
import os
import subprocess
import sys
import time

SDK = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb.exe")
OUT = os.path.join("docs", "visual_qa", "android")
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*args, check=False):
    return subprocess.run([ADB, "-e", *args], capture_output=True, text=True,
                          env=ENV, check=check)


def tap(x, y, wait=1.6):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=400, wait=1.0):
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))
    time.sleep(wait)


def key(code, wait=1.2):
    adb("shell", "input", "keyevent", str(code))
    time.sleep(wait)


def shot(name):
    os.makedirs(OUT, exist_ok=True)
    adb("shell", "screencap", "-p", "/sdcard/vq.png")
    path = os.path.join(OUT, f"{name}.png")
    adb("pull", "/sdcard/vq.png", os.path.abspath(path))
    print(f"  captured {path}")


def restart():
    adb("shell", "am", "force-stop", "ph.edu.umak.momompurin")
    time.sleep(0.5)
    adb("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
    time.sleep(7)
    # A restored bottom-bar state can drop us on a detail screen; walk back to a
    # known root so every capture starts from the same place. BACK is bounded.
    for _ in range(3):
        if find_row_y("Good Morning,") is not None or find_row_y("Good Afternoon,") is not None                 or find_row_y("Good Evening,") is not None:
            return
        adb("shell", "input", "keyevent", "4")
        time.sleep(1.2)


# Tab positions come from the laid-out bar; these are the 1080x2400 coordinates
# the Phase 4.6 bar produces (5 equal slots inside a 399dp pill).
TAB_Y = 2266
TABS = {"home": 122, "live": 331, "orders": 541, "stock": 749, "more": 958}


def main():
    which = sys.argv[1:] or [
        "home", "more", "notifications", "claim", "search", "session",
        "orders", "orderdetail", "payment", "invoice", "export", "pack",
        "shipping", "sharedinvoice", "catalog", "keep", "stock", "product",
        "productedit", "pos", "adjust", "media", "signin",
    ]
    print("capturing:", ", ".join(which))

    if "signin" in which:
        # Deterministic pre-auth: clear app data (the store reseeds from the same
        # seed, so nothing is lost) and relaunch - the session is gone, so the app
        # MUST open on /sign-in. UI sign-out paths are flaky here because a system
        # dialog or a list-scroll race leaves the button invisible to the dump.
        adb("shell", "pm", "clear", "ph.edu.umak.momompurin")
        adb("shell", "am", "start", "-n", "ph.edu.umak.momompurin/.MainActivity")
        time.sleep(9)
        shot("signin")

    restart()
    for name in which:
        if name == "signin":
            continue
        if name == "home":
            shot("home")
        elif name == "more":
            tap(TABS["more"], TAB_Y)
            shot("more")
        elif name == "notifications":
            restart()
            # The bell sits at the header's right; on the freshly scrolled dashboard
            # it is the second circular button.
            tap(1013, 170, wait=2.2)
            shot("notifications")
        elif name == "claim":
            tap(TABS["live"], TAB_Y)
            shot("claim")
        elif name == "search":
            tap(TABS["live"], TAB_Y)
            # "Open item search" sits just under the Quantity field at y~1196 on the
            # UNSCROLLED claim form (the 4.6 rebuild moved it there). The old step
            # scrolled first, which pushed the entry off-screen - do not scroll.
            y = find_row_y("Open item search")
            if y:
                tap(540, y, wait=2.4)
                shot("search")
            else:
                print("  'Open item search' not visible; capture skipped")
        elif name == "session":
            tap(TABS["live"], TAB_Y)
            swipe(540, 1700, 540, 800)
            swipe(540, 1700, 540, 800)
            tap(531, 1805, wait=2.0)
            shot("session")
        elif name == "orders":
            tap(TABS["orders"], TAB_Y)
            shot("orders")
        elif name == "orderdetail":
            # Same restart-first discipline as the other order-chain steps: a fresh
            # process guarantees the canonical ORD-1042 seed state.
            restart()
            open_order_detail()
            shot("orderdetail")
        elif name in ("payment", "invoice", "export", "pack", "shipping", "sharedinvoice", "catalog"):
            # Every order-chain step starts from a FRESH process: the bottom bar's
            # restoreState would otherwise land the Orders tab on whatever screen it
            # last showed instead of the list, and the store reseeds on restart so
            # ORD-1042 is always in its canonical state for the flow being captured.
            restart()
            open_order_detail()
            if name == "payment":
                tap_button("Record payment", wait=2.2)
                shot("payment")
            elif name == "invoice":
                tap_button("Invoice", wait=2.4)
                shot("invoice")
            elif name == "export":
                # Invoice -> Confirm & issue (lands on Export & share) -> Export.
                tap_button("Invoice", wait=2.4)
                tap_button("Confirm & issue", wait=2.2)
                tap_button("Export & share", wait=2.4)
                shot("export")
            elif name == "pack":
                tap_button("Pack", wait=2.4)
                shot("pack")
            elif name == "shipping":
                tap_button("Ship", wait=2.4)
                shot("shipping")
            elif name == "sharedinvoice":
                tap_button("Shared view", wait=2.4)
                shot("sharedinvoice")
            elif name == "catalog":
                tap_button("Shared view", wait=2.4)
                tap(310, 70, wait=2.4)     # shop name -> catalog
                shot("catalog")
        elif name == "keep":
            tap(TABS["orders"], TAB_Y)
            swipe(540, 1800, 540, 900, 600)
            tap(967, 158, wait=2.2)   # Keep action on the Paid-and-kept filter
            shot("keep")
        elif name == "stock":
            tap(TABS["stock"], TAB_Y)
            shot("stock")
        elif name == "product":
            # F1.0 is the product FORM, so open a product row and tap its Edit
            # action; the detail screen is not what this pair compares against.
            tap(TABS["stock"], TAB_Y)
            tap(132, 767, wait=2.2)   # first product row
            tap_button("Edit", wait=2.4)
            shot("product")
        elif name == "productedit":
            tap(TABS["stock"], TAB_Y)
            tap(132, 767, wait=2.2)
            swipe(540, 1800, 540, 900, 600)
            # the edit action sits below the pricing block
            tap(272, 1220, wait=2.2)
            shot("productedit")
        elif name == "pos":
            tap(815, 823, wait=2.2)   # hero "Regular POS"
            shot("pos")
        elif name == "adjust":
            tap(TABS["stock"], TAB_Y)
            swipe(540, 1800, 540, 900, 600)
            tap(226, 2031, wait=2.2)  # "Adjust stock" on the product detail
            shot("adjust")
        elif name == "media":
            tap(TABS["stock"], TAB_Y)
            tap(1029, 158, wait=2.2)  # "Media" action in the app bar
            shot("media")
        else:
            print(f"  unknown route {name}")


def tap_button(text, wait=2.2):
    """Find a button by its visible text in the current dump and tap its centre."""
    pos = find_row_pos(text)
    if pos:
        adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
        time.sleep(wait)
        return True
    print(f"  button '{text}' not visible; skipped")
    return False


def find_row_pos(text):
    """(x, y) centre of the node carrying `text` in the current UI dump."""
    import io, re
    xml_path = os.path.join(os.environ.get("TEMP", "/tmp"), "find.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/f.xml")
    adb("pull", "/sdcard/f.xml", xml_path)
    try:
        xml = io.open(xml_path, encoding="utf-8", errors="replace").read()
    except OSError:
        return None
    for m in re.finditer(
        r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml,
    ):
        # Same unescape as find_row_y: the dump writes "&" as "&amp;".
        if text in m.group(1).replace("&amp;", "&"):
            x1, y1, x2, y2 = (int(m.group(i)) for i in range(2, 6))
            return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None


def find_row_y(text):
    """Find the tap y of the row carrying `text` from the current UI dump."""
    import io, re
    xml_path = os.path.join(os.environ.get("TEMP", "/tmp"), "find.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/f.xml")
    adb("pull", "/sdcard/f.xml", xml_path)
    try:
        xml = io.open(xml_path, encoding="utf-8", errors="replace").read()
    except OSError:
        return None
    for m in re.finditer(
        r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml,
    ):
        # The dump XML-escapes "&" as "&amp;"; unescape so "Confirm & issue" matches.
        if text in m.group(1).replace("&amp;", "&"):
            y1, y2 = int(m.group(3)), int(m.group(5))
            return (y1 + y2) // 2
    return None


def open_order_detail():
    """From anywhere signed-in: Orders tab -> scroll to ORD-1042 -> open it."""
    tap(TABS["orders"], TAB_Y)
    time.sleep(1.4)
    # Scroll until the row is visible (the list is newest-claim-first, so ORD-1042
    # sits mid-list); bounded so a failed dump cannot spin forever.
    y = None
    for _ in range(6):
        y = find_row_y("ORD-1042")
        if y:
            break
        swipe(540, 1900, 540, 500, 400)
        time.sleep(1.2)
    if not y:
        print("  ORD-1042 not found in the Orders list")
        return False
    # Tap and CONFIRM the detail opened; a tap that lands while the list is still
    # settling would otherwise leave us on the list and every later step would
    # silently capture the wrong screen.
    for attempt in range(3):
        tap(540, y, wait=2.4)
        if find_row_y("Order state") is not None:
            return True
        # Re-find: the list may have scrolled under the tap.
        y = find_row_y("ORD-1042")
        if not y:
            for _ in range(4):
                swipe(540, 1900, 540, 500, 400)
                time.sleep(1.2)
                y = find_row_y("ORD-1042")
                if y:
                    break
        if not y:
            return False
    print("  ORD-1042 detail did not open")
    return False


if __name__ == "__main__":
    main()
