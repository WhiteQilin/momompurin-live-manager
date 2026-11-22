"""Focused on-device verification of the four flows the long walk kept losing.

The full sequential walk is the right gate for "can a cold tester go top to bottom",
but a long walk also accumulates screen state, and debugging one check inside it
costs a 10-minute rerun. These four flows are therefore verified in isolation, each
from a fresh install, which is both faster and a stronger statement: nothing here
depends on a previous step.

Flows:
  1. A-04 c5  the invoice dialog opens, refuses a bad fee, then accepts a real one
  2. A-04 c8  a PACKED unpaid order refuses cancellation with the exact message
  3. A-04 c7  cancel then restore the overdue order, and the stock round trip
  4. A-05 c1  Total / Paid / Balance and the three payment methods
"""
from __future__ import annotations

import sys
import time

sys.path.insert(0, "tools")
import v21_drive as d  # noqa: E402

OWNER = ("katrinajj254@gmail.com", "katrina@")
SHOTS = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
         r"\system_modelling_audit\v21_shots")

results: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    results.append((name, ok, detail))
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"\n         {detail}" if detail else ""))


def sign_in_owner() -> None:
    d.restart_clean()
    f = [n for n in d.nodes() if "EditText" in n["cls"]]
    d.tap_xy(f[0]["cx"], f[0]["cy"], 0.3)
    d.type_text(OWNER[0])
    d.tap_xy(f[1]["cx"], f[1]["cy"], 0.3)
    d.type_text(OWNER[1])
    d.back(0.4)
    d.tap("Log in", exact=True, wait=2.2)


def found_label_exact(label: str) -> dict | None:
    return d.find(label, exact=True)


def open_order_row(order_id: str) -> bool:
    """Open an order from the Orders list, matching the ROW (which holds '·')."""
    d.tap("Orders", exact=True, wait=1.4)
    for _ in range(8):
        for n in d.nodes():
            t = n["text"] or ""
            if t.startswith(order_id) and "\u00b7" in t:
                d.tap_xy(n["cx"], n["cy"], 1.5)
                return True
        d.adb("shell", "input", "swipe", "540", "1600", "540", "1100", "260")
        time.sleep(0.35)
    return False


def tap_visible(label: str, wait: float = 1.2) -> bool:
    n = found_label_exact(label)
    if n is None:
        return False
    d.tap_xy(n["cx"], n["cy"], wait)
    return True


# ---------------------------------------------------------------------------
# 1. A-04 c5: the invoice dialog
# ---------------------------------------------------------------------------
def flow_invoice() -> None:
    print("\n1) A-04 c5  Confirm & Send Invoice")
    sign_in_owner()
    if not open_order_row("ORD-1051"):
        record("opens ORD-1051", False, "row not found")
        return
    record("opens ORD-1051", True)

    # A bad fee must be refused AFTER the dialog is confirmed.
    fld = None
    for n in d.nodes():
        if (n["text"] or "") == "Shipping fee":
            below = [x for x in d.nodes() if "EditText" in x["cls"] and x["cy"] > n["cy"]]
            if below:
                fld = min(below, key=lambda x: x["cy"] - n["cy"])
            break
    if fld is None:
        record("finds the Shipping fee field", False)
        return
    d.clear_field(fld)
    d.tap_xy(fld["cx"], fld["cy"], 0.2)
    d.type_text("abc")
    d.back(0.4)

    opened = tap_visible("Confirm & Send Invoice", 1.4)
    dialog = found_label_exact("Send invoice") is not None
    record("the confirmation DIALOG opens", dialog,
           "" if dialog else f"after tapping the button, saw: {d.texts()[:12]}")
    if not dialog:
        return
    d.screenshot(f"{SHOTS}/a04_invoice_dialog.png")

    tap_visible("Send invoice", 1.3)
    # The notice renders below the card stack, so scroll it into view before
    # asserting; an immediate read tests the viewport, not the screen.
    refused = False
    for _ in range(6):
        if any("Enter a valid shipping fee." in t for t in d.texts()):
            refused = True
            break
        d.adb("shell", "input", "swipe", "540", "1650", "540", "900", "260")
        time.sleep(0.35)
    record('refuses a bad fee with "Enter a valid shipping fee."', refused,
           "" if refused else f"saw: {d.texts()[:16]}")

    # Now a real fee.
    for n in d.nodes():
        if (n["text"] or "") == "Shipping fee":
            below = [x for x in d.nodes() if "EditText" in x["cls"] and x["cy"] > n["cy"]]
            if below:
                fld = min(below, key=lambda x: x["cy"] - n["cy"])
            break
    d.clear_field(fld)
    d.tap_xy(fld["cx"], fld["cy"], 0.2)
    d.type_text("80")
    d.back(0.4)
    tap_visible("Confirm & Send Invoice", 1.3)
    tap_visible("Send invoice", 1.5)
    sent = any("MP-1051" in t for t in d.texts())
    record("sends the invoice and assigns MP-1051", sent,
           "" if sent else f"saw: {d.texts()[:14]}")
    d.screenshot(f"{SHOTS}/a04_invoiced_live.png")


# ---------------------------------------------------------------------------
# 2. A-04 c8: a packed order cannot be cancelled
# ---------------------------------------------------------------------------
def flow_packed_refusal() -> None:
    print("\n2) A-04 c8  a PACKED order refuses cancellation")
    sign_in_owner()
    if not open_order_row("ORD-1043"):
        record("opens ORD-1043", False, "row not found")
        return
    record("opens ORD-1043", True)
    time.sleep(0.6)
    # The refusal line sits at the bottom of the card stack.
    for _ in range(6):
        if any("Packed orders cannot be cancelled." in t for t in d.texts()):
            break
        d.adb("shell", "input", "swipe", "540", "1650", "540", "950", "280")
        time.sleep(0.4)
    ok = any("Packed orders cannot be cancelled." in t for t in d.texts())
    record('states "Packed orders cannot be cancelled."', ok,
           "" if ok else f"saw: {d.texts()[:16]}")
    d.screenshot(f"{SHOTS}/a04_packed_refusal.png")


# ---------------------------------------------------------------------------
# 3. A-04 c7/c9: cancel then restore, with the stock round trip
# ---------------------------------------------------------------------------
def flow_cancel_restore() -> None:
    print("\n3) A-04 c7-c9  cancel then restore the overdue order")
    sign_in_owner()

    # Baseline stock from the Stock detail card.
    d.tap("Stock", exact=True, wait=1.4)
    for _ in range(6):
        if found_label_exact("CHW") is not None:
            break
        d.adb("shell", "input", "swipe", "540", "1500", "540", "1000", "260")
        time.sleep(0.35)
    d.tap("CHW", exact=True, wait=1.5)
    before_ok = any("32 \u2212 20 = 12" in t for t in d.texts())
    record("CHW reads 32 - 20 = 12 before cancelling", before_ok,
           "" if before_ok else f"saw: {d.texts()[:18]}")

    if not open_order_row("ORD-1045"):
        record("opens ORD-1045", False, "row not found")
        return
    record("opens ORD-1045", True)

    if not tap_visible("Cancel order", 1.0):
        for _ in range(8):
            d.adb("shell", "input", "swipe", "540", "1700", "540", "800", "260")
            time.sleep(0.3)
            if tap_visible("Cancel order", 1.0):
                break
    dialog_reason = None
    for n in d.nodes():
        if (n["text"] or "") == "Reason":
            below = [x for x in d.nodes() if "EditText" in x["cls"] and x["cy"] > n["cy"]]
            if below:
                dialog_reason = min(below, key=lambda x: x["cy"] - n["cy"])
            break
    record("the cancel dialog opens and asks for a Reason", dialog_reason is not None)
    if dialog_reason is None:
        return
    d.tap_xy(dialog_reason["cx"], dialog_reason["cy"], 0.2)
    d.type_text("Overdue")
    d.back(0.4)

    # The dialog's confirm carries the same label, so pick the one BELOW the field.
    cands = [n for n in d.nodes()
             if (n["text"] or "") == "Cancel order" and n["cy"] > dialog_reason["cy"]]
    if not cands:
        record("finds the dialog's confirm button", False, f"saw: {d.texts()[:14]}")
        return
    btn = max(cands, key=lambda n: n["cy"])
    d.tap_xy(btn["cx"], btn["cy"], 1.6)
    # The definitive evidence is the STAGE in the app bar, which flips to
    # "Cancelled" immediately; the confirmation sentence renders further down.
    cancelled = any(t == "Cancelled" for t in d.texts())
    if not cancelled:
        for _ in range(6):
            if any(t == "Cancelled" for t in d.texts()):
                cancelled = True
                break
            d.adb("shell", "input", "swipe", "540", "1650", "540", "900", "260")
            time.sleep(0.35)
    record("cancels the order (stage becomes Cancelled)", cancelled,
           "" if cancelled else f"saw: {d.texts()[:16]}")

    # Stock must have gone UP by exactly ORD-1045's 8 CHW.
    d.tap("Stock", exact=True, wait=1.4)
    for _ in range(6):
        if found_label_exact("CHW") is not None:
            break
        d.adb("shell", "input", "swipe", "540", "1500", "540", "1000", "260")
        time.sleep(0.35)
    d.tap("CHW", exact=True, wait=1.5)
    after_cancel = any("32 \u2212 12 = 20" in t for t in d.texts())
    record("CHW becomes 32 - 12 = 20 after the cancel (8 released, exact)", after_cancel,
           "" if after_cancel else f"saw: {d.texts()[:18]}")
    d.screenshot(f"{SHOTS}/a04_after_cancel_stock.png")

    # Restore, and check the figure returns exactly.
    if not open_order_row("ORD-1045"):
        record("re-opens ORD-1045", False)
        return
    if not tap_visible("Restore", 1.5):
        for _ in range(8):
            d.adb("shell", "input", "swipe", "540", "1700", "540", "800", "260")
            time.sleep(0.3)
            if tap_visible("Restore", 1.5):
                break
    restored = any(t == "Order Placed" for t in d.texts())
    if not restored:
        for _ in range(6):
            if any(t == "Order Placed" for t in d.texts()):
                restored = True
                break
            d.adb("shell", "input", "swipe", "540", "1650", "540", "900", "260")
            time.sleep(0.35)
    record("restores the order (stage returns to Order Placed)", restored,
           "" if restored else f"saw: {d.texts()[:16]}")

    d.tap("Stock", exact=True, wait=1.4)
    for _ in range(6):
        if found_label_exact("CHW") is not None:
            break
        d.adb("shell", "input", "swipe", "540", "1500", "540", "1000", "260")
        time.sleep(0.35)
    d.tap("CHW", exact=True, wait=1.5)
    after_restore = any("32 \u2212 20 = 12" in t for t in d.texts())
    record("CHW is back to 32 - 20 = 12 (lossless round trip)", after_restore,
           "" if after_restore else f"saw: {d.texts()[:18]}")
    d.screenshot(f"{SHOTS}/a04_after_restore_stock.png")


# ---------------------------------------------------------------------------
# 4. A-05 c1-c2: the payment screen
# ---------------------------------------------------------------------------
def flow_payment() -> None:
    print("\n4) A-05 c1-c2  Total / Paid / Balance and the payment methods")
    sign_in_owner()
    if not open_order_row("ORD-1042"):
        record("opens ORD-1042", False, "row not found")
        return
    record("opens ORD-1042", True)
    txt = " ".join(d.texts())
    record("shows Total 880.00, Paid 600.00, Balance 280.00",
           "880.00" in txt and "600.00" in txt and "280.00" in txt,
           "" if ("880.00" in txt) else f"saw: {d.texts()[:18]}")

    if not tap_visible("Record payment", 1.4):
        for _ in range(8):
            d.adb("shell", "input", "swipe", "540", "1700", "540", "800", "260")
            time.sleep(0.3)
            if tap_visible("Record payment", 1.4):
                break
    txt2 = " ".join(d.texts())
    methods = [m for m in ("GCash", "Maya", "Bank Transfer") if m in txt2]
    record("offers GCash, Maya and Bank Transfer", len(methods) == 3,
           f"found {methods}")
    d.screenshot(f"{SHOTS}/a05_payment_live.png")


def main() -> int:
    for fn in (flow_invoice, flow_packed_refusal, flow_cancel_restore, flow_payment):
        try:
            fn()
        except Exception as exc:  # noqa: BLE001 - keep going to the next flow
            record(fn.__name__ + " crashed", False, f"{type(exc).__name__}: {exc}")

    ok = sum(1 for _, o, _ in results if o)
    print()
    print("=" * 70)
    print(f"FOCUSED FLOWS: {ok}/{len(results)} passed")
    for name, o, detail in results:
        if not o:
            print(f"  FAILED: {name}\n          {detail}")
    print("=" * 70)
    return 0 if ok == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
