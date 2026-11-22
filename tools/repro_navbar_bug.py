"""Reproduce the confirmed live navbar deadlock on v2.0, as evidence.

Tap path: Sign in as Owner -> Dashboard -> tap the "Pending orders" tile ->
lands on Orders -> tap "Home" on the bottom bar -> assert we are back on the
Dashboard AND that the bottom bar still responds afterwards.
"""
import sys
import time

sys.path.insert(0, "tools")
import v21_drive as d  # noqa: E402

SHOTS = r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)\system_modelling_audit\v21_shots"


def sign_in_owner() -> None:
    d.restart_clean()
    fields = [n for n in d.nodes() if "EditText" in n["cls"]]
    assert len(fields) >= 2, f"expected 2 fields, got {len(fields)}"
    d.tap_xy(fields[0]["cx"], fields[0]["cy"], 0.3)
    d.type_text("katrinajj254@gmail.com")
    d.tap_xy(fields[1]["cx"], fields[1]["cy"], 0.3)
    d.type_text("katrina@")
    d.back(0.4)  # dismiss the IME
    assert d.tap("Log in", exact=True, wait=1.8), "Log in button not found"


def tap_bar(label: str, wait: float = 1.2) -> bool:
    n = d.find(label, exact=True)
    if not n:
        return False
    d.tap_xy(n["cx"], n["cy"], wait)
    return True


def main() -> int:
    sign_in_owner()
    d.screenshot(f"{SHOTS}/01_dashboard.png")
    print("on dashboard:", d.has("Today's sales"), "| tile:", d.has("Pending orders"))

    # --- the reported path: tile -> Orders -> Home ---
    assert d.tap("Pending orders", wait=1.5), "Pending orders tile not found"
    on_orders = d.has("Search order number or customer")
    d.screenshot(f"{SHOTS}/02_orders_from_tile.png")
    print("tile opened Orders:", on_orders)

    home_tapped = tap_bar("Home", 1.5)
    back_on_dash = d.has("Today's sales")
    d.screenshot(f"{SHOTS}/03_after_home_tap.png")
    print("Home found in bar:", home_tapped, "| back on Dashboard:", back_on_dash)

    # --- does the bar still work at all after that? ---
    stock_ok = tap_bar("Stock", 1.5)
    on_stock = d.has("On hand") or d.has("Low stock")
    d.screenshot(f"{SHOTS}/04_stock_after.png")
    print("Stock tapped:", stock_ok, "| on Stock:", on_stock)

    print()
    print("RESULT navbar:", "PASS" if (on_orders and back_on_dash and on_stock) else "FAIL")
    return 0 if (on_orders and back_on_dash and on_stock) else 1


if __name__ == "__main__":
    raise SystemExit(main())
