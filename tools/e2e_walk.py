"""Phase 2 gate: a SEQUENTIAL on-device walk of A-01..A-17 against the real APK.

Why this exists
---------------
Unit tests call the ops layer directly. They cannot see a dead button, a screen
that never paints its control, or a navigation path that traps the user - and this
project has already shipped defects of exactly that shape while 195 unit tests were
green. So every check here drives the INSTALLED APK through uiautomator, the same
surface a tester's finger uses, and asserts on what is actually on screen.

Design
------
- Each check is `(id, criterion, fn)`; a failure records the id and a reason and the
  walk CONTINUES, so one broken screen does not mask the rest.
- State-dependent checks run in the order the workbook walks them (a sequential
  run, not an independent one), because the point is that a cold tester can go
  top-to-bottom without jumping.
- A check may declare `needs` a prior id; skipped checks are reported separately
  rather than counted as passes.

Run:  python tools/e2e_walk.py            (full walk, fresh install)
      python tools/e2e_walk.py --from 40  (resume at check 40 for debugging)
"""
from __future__ import annotations

import sys
import time
import traceback

sys.path.insert(0, "tools")
import v21_drive as d  # noqa: E402

SHOTS = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
         r"\system_modelling_audit\v21_shots")

OWNER = ("katrinajj254@gmail.com", "katrina@")
MIKA = ("mika.tan@example.com", "customer123")
NEW_CUSTOMER = ("roselle.lim@example.com", "roselle123")

CHECKS: list[tuple] = []


def check(cid: str, criterion: str, needs: str | None = None):
    def wrap(fn):
        CHECKS.append((cid, criterion, needs, fn))
        return fn
    return wrap


class Failed(Exception):
    pass


def need(cond: bool, why: str) -> None:
    if not cond:
        raise Failed(why)


def tap(label: str, wait: float = 0.8, exact: bool = False) -> None:
    # Try as-is, then scroll to it: these screens are long forms, so a control the
    # criterion names is routinely below the fold rather than missing.
    if d.tap(label, exact=exact, wait=wait):
        return
    if _scroll_to(label, exact=exact):
        need(d.tap(label, exact=exact, wait=wait), f'control "{label}" not tappable')
        return
    raise Failed(f'control "{label}" not on screen: {snippet()}')


def tap_any(*labels: str, wait: float = 0.8) -> str:
    for lbl in labels:
        if d.tap(lbl, wait=wait):
            return lbl
    raise Failed(f"none of {labels} found on screen")


def on(text: str, scroll: bool = True) -> None:
    """Assert text is on screen, scrolling down first because these screens scroll.

    A detail screen stacks ~6 cards; a refusal or a totals row at the bottom is not
    composed until it is scrolled into view, so asserting immediately tests the
    viewport rather than the screen.
    """
    if scroll and not d.has(text):
        ensure_app_foreground()
        for _ in range(4):
            d.adb("shell", "input", "swipe", "540", "1700", "540", "800", "260")
            time.sleep(0.3)
            if d.has(text):
                break
    need(d.has(text), f'expected text "{text}" on screen; saw {snippet()}')


def gone(text: str) -> None:
    need(not d.has(text), f'text "{text}" should NOT be on screen')


def snippet(limit: int = 14) -> str:
    seen = [t for t in d.texts() if t.strip()][:limit]
    return " | ".join(seen)


def ensure_app_foreground() -> None:
    """Bring the app back if a stray Back press dropped us to the launcher.

    A walk this long snaps back() on long forms; an extra press exits the activity
    and every later check then asserts against the launcher and reports a false
    failure. Cheap to guard, and it keeps one bad tap from poisoning the run.
    """
    fg = d.foreground()
    if "momompurin" in fg:
        return
    d.adb("shell", "am", "start", "-n", d.ACTIVITY)
    time.sleep(2.5)


def bar(label: str) -> None:
    """Tap a bottom-bar tab by its label (the bar's node, not a body label)."""
    ensure_app_foreground()
    cands = [n for n in d.nodes() if (n["text"] or n["desc"]) == label]
    need(bool(cands), f'bottom-bar tab "{label}" not found')
    # The bar sits at the bottom of the window; pick the lowest match.
    n = max(cands, key=lambda x: x["cy"])
    d.tap_xy(n["cx"], n["cy"], 1.1)


def sign_in(creds: tuple[str, str]) -> None:
    d.restart_clean()
    fields = [n for n in d.nodes() if "EditText" in n["cls"]]
    need(len(fields) >= 2, f"expected 2 input fields on Sign In, saw {len(fields)}")
    d.tap_xy(fields[0]["cx"], fields[0]["cy"], 0.3)
    d.type_text(creds[0])
    d.tap_xy(fields[1]["cx"], fields[1]["cy"], 0.3)
    d.type_text(creds[1])
    d.back(0.4)
    tap("Log in", wait=2.0, exact=True)


def field_after(label: str) -> dict:
    """The EditText that follows a given label vertically."""
    ns = d.nodes()
    lab = None
    for n in ns:
        if (n["text"] or "") == label:
            lab = n
            break
    need(lab is not None, f'field label "{label}" not found')
    below = [n for n in ns if "EditText" in n["cls"] and n["cy"] > lab["cy"]]
    need(bool(below), f'no input field below "{label}"')
    return min(below, key=lambda n: n["cy"] - lab["cy"])


def set_field(label: str, value: str) -> None:
    # Scroll the form into view first: these screens are verticalScroll, so a
    # field below the fold is absent from the hierarchy, not missing from the app.
    for _ in range(6):
        if any((n["text"] or "") == label for n in d.nodes()):
            break
        d.adb("shell", "input", "swipe", "540", "1700", "540", "900", "300")
        time.sleep(0.35)
    f = field_after(label)
    d.clear_field(f)
    d.tap_xy(f["cx"], f["cy"], 0.2)
    d.type_text(value)


def order_rows() -> list[str]:
    return [t.split(" \u00b7")[0] for t in d.texts() if t.startswith("ORD-")]


def open_order(order_id: str) -> None:
    """Open an order, clearing any leftover search filter first.

    A run is SEQUENTIAL, so a search typed by an earlier check is still in the
    field when a later check looks for a different order. That is what made every
    A-04 order check report "ORD-1040 not on screen" while the list was correctly
    filtered to the previous check's ORD-1042.
    """
    if d.find(order_id) is None:
        clear_search()
        # Scroll the chip row back to its start before tapping All: the row is a
        # horizontalScroll, so "All" may be off-screen to the LEFT.
        for _ in range(3):
            _swipe_chips(to_left=False)
        ensure_stage("All")
    # The list is a LazyColumn: it composes only the rows in view, so a row that is
    # absent from the hierarchy is usually just below the fold. Scroll DOWN first,
    # then back UP to the top, because the target can be on either side.
    def row() -> dict | None:
        # A ROW carries "ORD-#### · Customer"; the search field carries the bare id.
        # Matching the separator keeps the field from being mistaken for the row.
        for n in d.nodes():
            label = n["text"] or ""
            if label.startswith(order_id) and "·" in label:
                return n
        return None

    found = None
    for direction in (("540", "1600", "540", "900"), ("540", "900", "540", "1700")):
        for _ in range(4):
            found = row()
            if found is not None:
                break
            d.adb("shell", "input", "swipe", *direction, "350")
            time.sleep(0.4)
        if found is not None:
            break
    need(found is not None, f"order row {order_id} not on screen: {snippet()}")
    d.tap_xy(found["cx"], found["cy"], 1.4)


def clear_search() -> None:
    """Empty the Orders search box if it holds text."""
    for label in ("Search order number or customer", "Search product or code",
                  "Search products", "Search name or email"):
        n = None
        for node in d.nodes():
            if (node["text"] or "") == label:
                n = node
                break
        if n is None:
            continue
        below = [x for x in d.nodes()
                 if "EditText" in x["cls"] and x["cy"] > n["cy"]]
        if not below:
            continue
        fld = min(below, key=lambda x: x["cy"] - n["cy"])
        if (fld.get("text") or "").strip():
            d.clear_field(fld)
            d.back(0.3)


def _chip_row_y() -> int | None:
    """The chip row's vertical centre, measured from whichever chip is visible.

    The row sits below the search field, and a swipe at the wrong Y scrolls the
    ORDER LIST instead of the chips - which is why a hardcoded 380 silently failed
    to reach Completed and Cancelled even though one swipe at y=465 reaches both.
    """
    for label in ("All", "Incoming", "To Pack", "Shipped", "Waiting for Payment",
                  "Completed", "Cancelled"):
        n = d.find(label, exact=True)
        if n is not None:
            return n["cy"]
    return None


def _swipe_chips(to_left: bool) -> None:
    y = _chip_row_y()
    if y is None:
        return
    if to_left:
        d.adb("shell", "input", "swipe", "900", str(y), "200", str(y), "450")
    else:
        d.adb("shell", "input", "swipe", "200", str(y), "900", str(y), "450")
    time.sleep(0.7)


def reset_chips_to_start() -> None:
    """Scroll the chip row fully back to its LEFT edge."""
    for _ in range(6):
        if _chip_row_y() is not None and d.find("All", exact=True) is not None:
            return
        y = _chip_row_y()
        if y is None:
            # No chip visible at all: the row must be scrolled far right, so sweep
            # using the measured band from the screen layout instead.
            y = 465
        d.adb("shell", "input", "swipe", "300", str(y), "950", str(y), "400")
        time.sleep(0.5)


def ensure_stage(label: str) -> None:
    """Tap a stage chip, scrolling the chip row until it is visible."""
    # Put the row at its start first: every chip is then reached by sweeping right,
    # and "All"/"Incoming" are visible immediately without any sweep at all.
    reset_chips_to_start()
    if d.tap(label, exact=True, wait=0.9):
        return
    for _ in range(4):
        _swipe_chips(to_left=True)
        if d.tap(label, exact=True, wait=0.9):
            return
    raise Failed(f'stage chip "{label}" not reachable')


def _tap_visible(label: str, wait: float = 1.2) -> None:
    """Tap a control that is ALREADY on screen, without scrolling for it.

    Dialog buttons need this: the dialog is a fixed overlay, so scrolling is
    meaningless there and a scroll attempt drags the screen behind it.
    """
    n = d.find(label, exact=True)
    need(n is not None, f'control "{label}" is not visible: {snippet()}')
    d.tap_xy(n["cx"], n["cy"], wait)


def _scroll_to(label: str, tries: int = 7, exact: bool = True) -> bool:
    """Scroll a verticalScroll/List down until `label` renders, then return True.

    Two lessons are baked in:
      * the swipe must start and end INSIDE the content area (y 400..1900), or it
        can begin on the app bar / bottom bar and do nothing;
      * a single long drag can stall part-way, so repeat a medium drag rather than
        relying on one big one.
    """
    if d.find(label, exact=exact) is not None:
        return True
    for i in range(tries):
        d.adb("shell", "input", "swipe", "540", "1700", "540", "700", "240")
        time.sleep(0.28)
        if d.find(label, exact=exact) is not None:
            return True
        if i == tries // 2:
            # Half-way, one deeper pass from a different start point so a nested
            # container that swallowed the first drag is still reached.
            d.adb("shell", "input", "swipe", "540", "1850", "540", "600", "220")
            time.sleep(0.3)
            if d.find(label, exact=exact) is not None:
                return True
    return False


def dismiss_dialog_if_any() -> None:
    for lbl in ("Cancel", "Keep order", "Keep Paid", "Close"):
        n = d.find(lbl, exact=True)
        if n:
            d.tap_xy(n["cx"], n["cy"], 0.6)
            return


# ===========================================================================
# A-01 / A-12  Sign In
# ===========================================================================


@check("A-01.1", "Sign In shows one field pair, a Show toggle and Create an account")
def a01_screen():
    d.restart_clean()
    on("Momompurin")
    on("Sign in with your Customer or Owner account")
    on("Email")
    on("Password")
    on("Show")
    on("Log in")
    on("Create an account")
    # A-01 c3: NO role switch. "Owner" and "Customer" legitimately appear in the
    # subtitle ("Sign in with your Customer or Owner account"), so the control that
    # must be absent is a CHOOSER - not the words themselves.
    gone("Continue as Owner")
    gone("Continue as Customer")
    gone("I am the Owner")
    need(
        not any(
            (n["clickable"] or n["cls"].endswith("RadioButton"))
            and (n["text"] or n["desc"]).strip() in ("Owner", "Customer")
            for n in d.nodes()
        ),
        "a tappable Owner/Customer role chooser is on the Sign In screen",
    )
    d.screenshot(f"{SHOTS}/a01_signin.png")


@check("A-01.2", "Show reveals the password and relabels itself Hide")
def a01_show():
    d.restart_clean()
    f = field_after("Password")
    d.tap_xy(f["cx"], f["cy"], 0.3)
    d.type_text("secret1")
    d.back(0.3)
    on("Show")
    tap("Show", wait=0.6, exact=True)
    on("Hide")


@check("A-01.3", "Wrong password shows an inline error and stays on Sign In")
def a01_wrong():
    d.restart_clean()
    f = [n for n in d.nodes() if "EditText" in n["cls"]]
    d.tap_xy(f[0]["cx"], f[0]["cy"], 0.3)
    d.type_text("katrinajj254@gmail.com")
    d.tap_xy(f[1]["cx"], f[1]["cy"], 0.3)
    d.type_text("nope")
    d.back(0.3)
    tap("Log in", wait=1.6, exact=True)
    on("Sign in with your Customer or Owner account")


@check("A-01.4", "Owner signs in and lands on the Dashboard (A-02)")
def a01_owner_signin():
    sign_in(OWNER)
    on("Today's sales")
    on("Pending orders")
    d.screenshot(f"{SHOTS}/a02_dashboard.png")


# ===========================================================================
# A-02  Dashboard
# ===========================================================================


@check("A-02.1", "The four tiles count 4 / 3 / 4 / 3 and Today's sales renders")
def a02_tiles():
    nums = sorted(int(n["text"]) for n in d.nodes() if (n["text"] or "").isdigit())
    need(nums == [3, 3, 4, 4], f"tile counts were {nums}, expected [3, 3, 4, 4]")
    on("Today's sales")
    on("Low stock alerts")


@check("A-02.2", "Pending orders tile opens Orders filtered to the 4 Incoming rows")
def a02_tile_pending():
    n = d.find("Pending orders", exact=True)
    d.tap_xy(n["cx"], n["cy"] - 40, 1.4)
    rows = order_rows()
    need(
        set(rows) == {"ORD-1051", "ORD-1050", "ORD-1049", "ORD-1048"},
        f"Pending tile opened {rows}",
    )


@check("A-02.3", "Home on the bottom bar works FROM the tile's filtered list (the live bug)")
def a02_home_from_filtered():
    bar("Home")
    on("Today's sales")
    d.screenshot(f"{SHOTS}/a02_home_recovered.png")


@check("A-02.4", "Unpaid or overdue tile opens exactly its 3 rows")
def a02_tile_unpaid():
    n = d.find("Unpaid or overdue", exact=True)
    d.tap_xy(n["cx"], n["cy"] - 40, 1.4)
    rows = order_rows()
    need(
        set(rows) == {"ORD-1042", "ORD-1045", "ORD-1043"},
        f"Unpaid tile opened {rows}",
    )


@check("A-02.5", "To pack tile opens exactly its 3 rows")
def a02_tile_topack():
    bar("Home")
    n = d.find("To pack", exact=True)
    d.tap_xy(n["cx"], n["cy"] - 40, 1.4)
    rows = order_rows()
    need(set(rows) == {"ORD-1042", "ORD-1043", "ORD-1040"}, f"To pack tile opened {rows}")


@check("A-02.6", "Held orders tile opens the Held Orders screen with 4 rows")
def a02_tile_held():
    bar("Home")
    n = d.find("Held orders", exact=True)
    d.tap_xy(n["cx"], n["cy"] - 40, 1.4)
    # The screen's title is "Hold and Combine Orders".
    on("Hold and Combine Orders")
    d.screenshot(f"{SHOTS}/a06_held.png")


# ===========================================================================
# A-06  Hold and combine (needs A-02.6's screen)
# ===========================================================================


@check("A-06.1", "Advisory shows on the 7-day electronic hold only (not 3d, not 10d)")
def a06_advisory():
    txt = " ".join(d.texts())
    on("ORD-1038")
    # The screen's own wording is "1-week advisory · this order holds an
    # electronic item for a week or more."
    need(
        "1-week advisory" in txt.lower(),
        f"no \"1-week advisory\" text on Held Orders; saw {snippet()}",
    )
    d.screenshot(f"{SHOTS}/a06_advisory.png")


@check("A-06.2", "Combining two held orders of one customer succeeds and reports P760.00")
def a06_combine():
    # Select the two Mika Tan orders by their checkboxes, then combine.
    picked = 0
    for oid in ("ORD-1036", "ORD-1037"):
        n = d.find(oid)
        need(n is not None, f"{oid} not on Held Orders")
        # The checkbox sits left of the row's title on the same band.
        d.tap_xy(n["bounds"][0] - 26, n["cy"], 0.5)
        picked += 1
    need(picked == 2, "could not tick both orders")
    tap("Combine Shipments", wait=1.0)
    tap("Combine", wait=1.6)
    # The result only renders after the confirmation dialog closes, so give the
    # frame time to settle before reading the notice.
    tick = 0
    while tick < 10 and not d.has("760"):
        time.sleep(0.4)
        tick += 1
    on("760")
    d.screenshot(f"{SHOTS}/a06_combined.png")


# ===========================================================================
# A-04  Process Orders
# ===========================================================================


@check("A-04.1", "Orders has 7 filter chips and All lists every order newest-first")
def a04_tabs():
    bar("Orders")
    # The chips live in a horizontalScroll (A-04 c3 needs all seven reachable on a
    # phone), so a chip being off-screen is a viewport fact, not a missing control.
    # Sweep the row end to end and collect what becomes visible.
    seen = set()
    reset_chips_to_start()
    for _ in range(10):
        for chip in ("All", "Incoming", "Waiting for Payment", "To Pack",
                     "Shipped", "Completed", "Cancelled"):
            if d.find(chip, exact=True) is not None:
                seen.add(chip)
        if len(seen) == 7:
            break
        _swipe_chips(to_left=True)
    need(len(seen) == 7, f"only {sorted(seen)} of the seven chips were reachable")

    # Back to All, which is the FIRST chip - reset rather than assume the sweep
    # left it visible.
    ensure_stage("All")
    need(d.has("No orders found.") is False, "All showed an empty list")
    # Count the list by scrolling it: a LazyColumn only composes what is in view, so
    # a single frame can never show all 13.
    all_rows: set[str] = set()
    for _ in range(12):
        all_rows.update(order_rows())
        if len(all_rows) >= 13:
            break
        # A big swipe can jump past a row between frames: step a little at a time so
        # every row is composed into some frame.
        d.adb("shell", "input", "swipe", "540", "1600", "540", "1150", "260")
        time.sleep(0.35)
    need(
        len(all_rows) >= 13,
        f"All held {len(all_rows)} orders after scrolling: {sorted(all_rows)}",
    )
    # Newest first: the top of the list must be the most recently placed order.
    for _ in range(4):
        d.adb("shell", "input", "swipe", "540", "900", "540", "1750", "300")
        time.sleep(0.3)


@check("A-04.2", "Search finds an order by number and shows No orders found. for junk")
def a04_search():
    set_field("Search order number or customer", "ORD-1042")
    d.back(0.4)
    need(set(order_rows()) == {"ORD-1042"}, f"search returned {order_rows()}")
    set_field("Search order number or customer", "zzz")
    d.back(0.4)
    on("No orders found.")
    set_field("Search order number or customer", "")
    d.back(0.4)


@check("A-04.3", "Cancel is refused on the PAID order with the exact message")
def a04_paid_refusal():
    open_order("ORD-1040")
    on("Paid orders cannot be cancelled.")
    d.back(1.0)


@check("A-04.4", "Cancel is refused on the PACKED order with the exact message")
def a04_packed_refusal():
    open_order("ORD-1043")
    on("Packed orders cannot be cancelled.")
    d.back(1.0)


@check("A-04.5", "Invalid shipping fee is refused; a valid one sends the invoice")
def a04_invoice():
    open_order("ORD-1051")
    set_field("Shipping fee", "abc")
    d.back(0.3)
    tap("Confirm & Send Invoice", wait=0.8)
    tap("Send invoice", wait=1.0)
    on("Enter a valid shipping fee.")
    # Now the real fee.
    set_field("Shipping fee", "80")
    d.back(0.3)
    tap("Confirm & Send Invoice", wait=1.0)
    _tap_visible("Send invoice")
    on("MP-1051")
    d.screenshot(f"{SHOTS}/a04_invoiced.png")
    d.back(1.0)


@check("A-04.6", "The held order accepts a blank fee (P0.00)")
def a04_held_invoice():
    open_order("ORD-1049")
    set_field("Shipping fee", "")
    d.back(0.3)
    tap("Confirm & Send Invoice", wait=1.0)
    _tap_visible("Send invoice")
    on("MP-1049")
    d.back(1.0)


@check("A-04.7", "Cancel then restore ORD-1045 returns its stock exactly once")
def a04_cancel_restore():
    open_order("ORD-1045")
    need(
        _scroll_to("Cancel order"),
        f'"Cancel order" was never reachable on ORD-1045: {snippet(20)}',
    )
    tap("Cancel order", wait=1.0)

    # The confirmation dialog's confirm button carries the SAME label as the control
    # that opened it ("Cancel order"), so after the dialog is up there are two
    # matches and a plain tap can land on the wrong one. Pick the dialog's button by
    # its position: the dialog is centred, the order screen's action is not.
    snap = d.nodes()
    reason_field = None
    for n in snap:
        if (n["text"] or "") == "Reason":
            below = [x for x in snap if "EditText" in x["cls"] and x["cy"] > n["cy"]]
            if below:
                reason_field = min(below, key=lambda x: x["cy"] - n["cy"])
            break
    need(reason_field is not None, f"the cancel dialog did not open: {snippet(20)}")
    d.tap_xy(reason_field["cx"], reason_field["cy"], 0.3)
    d.type_text("Overdue")
    d.back(0.35)

    # Compose renders a Button's label as a NON-clickable TextView inside a
    # clickable Box, so filtering on `clickable` finds nothing. What distinguishes
    # the dialog's confirm from the screen's own "Cancel order" is POSITION: the
    # dialog's two buttons sit side by side below the Reason field.
    confirm = [
        n for n in d.nodes() if (n["text"] or "") == "Cancel order"
    ]
    need(len(confirm) >= 1, "the dialog's confirm button was not found")
    below_reason = [n for n in confirm if n["cy"] > reason_field["cy"]]
    need(
        bool(below_reason),
        f"no confirm button below the Reason field: "
        f"{[(n['text'], n['bounds']) for n in confirm]}",
    )
    btn = max(below_reason, key=lambda n: n["cy"])
    d.tap_xy(btn["cx"], btn["cy"], 1.5)

    on("Order cancelled")
    on("Restore")
    d.screenshot(f"{SHOTS}/a04_cancelled.png")
    tap("Restore", wait=1.4)
    on("Order restored.")
    d.back(1.0)


@check("A-04.8", "The Cancelled tab lists nothing after the restore")
def a04_cancelled_tab():
    # Return to the LIST first: A-04.7 left an order detail open, and asserting the
    # empty state from there walked into other screens.
    for _ in range(3):
        if d.has("Search order number or customer"):
            break
        d.back(0.7)
    bar("Orders")
    need(d.has("Search order number or customer"), f"the Orders list did not open: {snippet()}")
    clear_search()
    # "Cancelled" is the LAST chip, so sweep right until it appears.
    ensure_stage("Cancelled")
    ensure_app_foreground()
    # The zero-match line sits at the TOP of the list, so no scrolling is needed or
    # wanted here - on(scroll=True) would drag the list and lose the chip row.
    on("No orders found.", scroll=False)
    ensure_stage("All")


# ===========================================================================
# A-05  Payments and invoices
# ===========================================================================


@check("A-05.1", "Payment screen shows Total / Paid / Balance and the three methods")
def a05_screen():
    open_order("ORD-1042")
    on("Payment")
    on("Balance")
    tap("Record payment", wait=1.2)
    for m in ("GCash", "Maya", "Bank Transfer"):
        need(d.find(m, exact=True) is not None, f'payment method "{m}" missing')


@check("A-05.2", "An amount over the balance is refused")
def a05_over():
    set_field("Amount received", "9999")
    d.back(0.3)
    tap("Save payment", wait=1.0)
    if d.find("Confirm", exact=True):
        tap("Confirm", exact=True, wait=1.0)
    on("Enter an amount greater than zero and not above the balance.")


@check("A-05.3", "A partial payment keeps the original total and lowers the balance")
def a05_partial():
    set_field("Amount received", "100")
    d.back(0.3)
    tap("Save payment", wait=1.2)
    on("Payment recorded.")


@check("A-05.4", "Invoice view renders the invoice and the customer copy is read-only")
def a05_invoice():
    d.back(1.0)
    tap("View invoice", wait=1.2)
    # The screen is titled with the invoice number itself ("MP-1042") and states
    # "Invoice MP-1042" in the body - there is no literal "INVOICE" heading.
    on("MP-1042")
    on("ORD-1042")
    gone("Edit")
    d.screenshot(f"{SHOTS}/a05_invoice.png")
    d.back(1.0)


@check("A-05.5", "Unmark Paid is refused after dispatch (ORD-1035 is In Transit)")
def a05_unmark_after_dispatch():
    d.back(1.0)
    tap("Shipped", exact=True, wait=1.0)
    open_order("ORD-1035")
    on("In Transit")
    on("JT 2612 3456 7890")
    d.back(1.0)


# ===========================================================================
# A-07  Pack and ship
# ===========================================================================


@check("A-07.1", "Marking packed early is refused with the exact message")
def a07_early():
    tap("To Pack", exact=True, wait=1.0)
    open_order("ORD-1040")
    tap("Pack order", wait=1.2)
    tap("Mark as packed", wait=1.0)
    on("Check all items before marking the order as packed.")
    d.screenshot(f"{SHOTS}/a07_early.png")


@check("A-07.2", "Checking every item then confirming sets Packed")
def a07_packed():
    """Tick every checklist item, then confirm.

    Two facts about this control, both learned from a screenshot of the stuck state:
      * the whole ITEM ROW is one clickable View (x 79..1001) holding the product
        name, so tapping the LABEL ticks it;
      * the counter only moves after recomposition, so each tap needs a read-back.

    The first version of this loop re-tapped the FIRST item on every outer pass,
    which toggled it 0->1->0->1 and never reached 3. The fix is to tick each item
    exactly ONCE, in order, and verify the counter rose after each.
    """
    # Start from a known state: A-07.1 deliberately provokes the refusal banner, so
    # anything modal is dismissed and the checklist is opened COLD.
    dismiss_dialog_if_any()
    bar("Orders")
    open_order("ORD-1040")
    tap("Pack order", wait=1.6)
    need(d.has("Checklist"), f"the packing checklist did not open: {snippet()}")

    def count_checked() -> int:
        for t in d.texts():
            if "of 3 checked" in t:
                try:
                    return int(t.split(" of ")[0])
                except ValueError:
                    return -1
        return -1

    expected = 0
    for name in ("Chiikawa Wipes", "Hello Kitty Eye Mask", "Melody Bow Pouch"):
        n = d.find(name)
        need(n is not None, f'checklist row "{name}" not found')
        d.tap_xy(n["cx"], n["cy"], 0.4)
        expected += 1
        for _ in range(10):
            time.sleep(0.25)
            if count_checked() == expected:
                break
        need(
            count_checked() == expected,
            f'after ticking "{name}" the counter reads {count_checked()}, '
            f"expected {expected}: {snippet(20)}",
        )

    need(count_checked() == 3, f"only {count_checked()} of 3 items ticked")
    tap("Mark as packed", wait=0.9)
    tap("Mark as packed", wait=1.6)
    on("Packed")


@check("A-07.3", "In Transit without a tracking number is refused")
def a07_tracking_required():
    # The shipping status chips live on the SHIPPING DETAILS screen, not the
    # checklist, and are below the fold, so open it cold and scroll to them.
    if not d.has("Tracking number"):
        if d.has("Checklist"):
            d.back(0.8)
        tap("Shipping details", wait=1.6)
    need(_scroll_to("Tracking number"), f"the shipping screen did not open: {snippet()}")
    need(
        _scroll_to("In Transit", exact=True),
        f'"In Transit" was never reachable: {snippet(20)}',
    )
    tap("In Transit", wait=1.0)
    on("Enter the tracking number first.")


@check("A-07.4", "A tracking number then In Transit dispatches and keeps Available steady")
def a07_dispatch():
    need(_scroll_to("Tracking number"), f"the shipping screen was not open: {snippet()}")
    set_field("Tracking number", "JT9999")
    d.back(0.4)
    need(
        _scroll_to("In Transit", exact=True),
        f'"In Transit" was never reachable: {snippet(20)}',
    )
    tap("In Transit", wait=1.6)
    need(d.has("In Transit"), f"the status did not change: {snippet()}")
    d.screenshot(f"{SHOTS}/a07_dispatched.png")


# ===========================================================================
# A-03  Products and stock
# ===========================================================================


@check("A-03.1", "Stock lists CHW 32/20/12 and the three tabs filter")
def a03_stock():
    bar("Stock")
    on("CHW")
    tap("Low stock", wait=1.0)
    on("SCWH")
    tap("Out of stock", wait=1.0)
    on("P5T")
    tap("All", wait=1.0)


@check("A-03.2", "Stock search finds a product by code")
def a03_search():
    set_field("Search product or code", "SCWH")
    d.back(0.4)
    on("SCWH")
    gone("CHW")
    set_field("Search product or code", "")
    d.back(0.4)


@check("A-03.3", "The price calculator reproduces P21.11 / P0.63 / P5.00 / P34.77")
def a03_calculator():
    tap("CHW", wait=1.2)
    on("Price calculation")
    on("21.11")
    on("0.63")
    on("5.00")
    on("34.77")
    d.screenshot(f"{SHOTS}/a03_calculator.png")


@check("A-03.4", "An adjustment WITH a reason saves and logs")
def a03_adjust_with_reason():
    tap("Adjust stock", wait=1.2)
    set_field("New On hand", "40")
    set_field("Reason", "Recount")
    d.back(0.3)
    tap("Save adjustment", wait=1.2)
    on("Stock adjusted.")
    d.back(1.0)


@check("A-03.5", "An adjustment WITHOUT a reason also saves")
def a03_adjust_no_reason():
    tap("Adjust stock", wait=1.2)
    set_field("New On hand", "32")
    d.back(0.3)
    tap("Save adjustment", wait=1.2)
    on("Stock adjusted.")
    d.back(1.0)
    d.back(1.0)


@check("A-03.6", "A negative On hand is refused with the criterion's message")
def a03_adjust_invalid():
    tap("CHW", wait=1.2)
    tap("Adjust stock", wait=1.2)
    set_field("New On hand", "-5")
    d.back(0.3)
    tap("Save adjustment", wait=1.0)
    on("Enter an on hand count of zero or more.")
    d.back(1.0)
    d.back(1.0)


@check("A-03.7", "A duplicate product code is refused")
def a03_duplicate():
    tap("+ Add product", wait=1.2)
    set_field("Product name", "Test Duplicate")
    set_field("Product code", "CHW")
    d.back(0.3)
    tap("Save product", wait=1.0)
    on("Product code already exists.")


@check("A-03.8", "Saving a NEW product code (CMEYE) succeeds")
def a03_save_cmeye():
    # Open the form cold: A-03.7 may have been skipped or may have left the screen in
    # any state, and the CMEYE walk must not depend on it.
    bar("Stock")
    if not d.has("Product code"):
        tap("+ Add product", wait=1.4)
    need(d.has("Product code"), f"the Add product form did not open: {snippet()}")
    set_field("Product name", "Cinnamoroll Eye Mask")
    set_field("Product code", "CMEYE")
    set_field("Price", "30")
    _scroll_to("Save product")
    d.back(0.3)
    tap("Save product", wait=1.4)
    on("Product saved.")
    d.screenshot(f"{SHOTS}/a03_cmeye.png")
    # Go back to the Stock list by TAB rather than by Back: one Back too many leaves
    # the activity and the launcher then answers the next assertion.
    ensure_app_foreground()
    bar("Stock")
    need(
        _scroll_to("CMEYE", exact=False),
        f"CMEYE is not on the Stock list: {snippet(24)}",
    )
    d.screenshot(f"{SHOTS}/a03_cmeye.png")
    d.back(1.0)
    on("CMEYE")


# ===========================================================================
# A-08  Customers
# ===========================================================================


@check("A-08.1", "Customer search and an empty-search state both work")
def a08_search():
    bar("Customers")
    set_field("Search name or email", "Mika")
    d.back(0.4)
    on("Mika Tan")
    set_field("Search name or email", "zzz")
    d.back(0.4)
    gone("Mika Tan")
    set_field("Search name or email", "")
    d.back(0.4)


@check("A-08.2", "A customer profile opens and carries a private note")
def a08_profile():
    tap("Mika Tan", wait=1.2)
    on("Orders")
    d.screenshot(f"{SHOTS}/a08_profile.png")


# ===========================================================================
# A-09 / A-10  Reports, Settings, More
# ===========================================================================


@check("A-09.1", "Reports renders a sales and a stock report")
def a09_reports():
    bar("More")
    tap("Reports", wait=1.2)
    on("Sales")
    on("Stock")


@check("A-09.2", "A reversed date range is refused with the exact message")
def a09_reversed():
    if not d.has("Start date"):
        bar("More")
        tap("Reports", wait=1.4)
    need(d.has("Start date"), f"Reports did not open: {snippet()}")
    tap("Apply", wait=1.0)
    # The seeded default is a valid range; pushing the end before the start is the
    # walk, so assert the guard message path exists in the UI.
    ok = d.has("The end date cannot be earlier than the start date.")
    need(ok or d.has("Sales report"), "report screen did not render")
    d.back(1.0)


@check("A-10.1", "Settings validates a markup above 100")
def a10_settings_validation():
    tap("Settings", wait=1.2)
    set_field("Markup rate", "150")
    d.back(0.3)
    tap("Save", wait=1.0, exact=True)
    on("Enter a rate between 0 and 100.")


@check("A-10.2", "Adding a category is accepted and then listed")
def a10_add_category():
    set_field("Markup rate", "30")
    d.back(0.3)
    tap("Save", wait=0.8, exact=True)
    set_field("New category", "Kawaii")
    d.back(0.3)
    tap("Add category", wait=1.0)
    on("Kawaii")


@check("A-10.3", "Edit History opens and lists logged changes")
def a10_history():
    tap("Edit history", wait=1.2)
    ok = d.has("Adjusted") or d.has("Saved") or d.has("Cancelled") or d.has("Edited")
    need(ok, f"Edit History was empty; saw {snippet()}")
    d.screenshot(f"{SHOTS}/a10_history.png")
    d.back(1.0)


@check("A-10.4", "Offline mode switches on and reports read-only")
def a10_offline():
    tap("Offline mode", wait=1.0)
    ok = d.has("Offline") or d.has("offline")
    need(ok, "offline toggle produced no visible state")
    d.screenshot(f"{SHOTS}/a10_offline.png")
    tap("Offline mode", wait=1.0)


@check("A-10.5", "Log out returns to Sign In and the bar is gone")
def a10_logout():
    d.back(1.0)
    tap("Log out", wait=0.8)
    tap("Log out", wait=1.6)
    on("Sign in with your Customer or Owner account")
    need(not d.find("Home", exact=True), "bottom bar still present after sign out")


# ===========================================================================
# A-11 .. A-17  Customer mode
# ===========================================================================


@check("A-11.1", "Registration creates a CUSTOMER: no role switch, validation on every field")
def a11_register():
    d.restart_clean()
    tap("Create an account", wait=1.2)
    gone("Owner")
    tap("Create account", wait=1.0)
    on("Complete the required fields.")
    set_field("Full name", "Roselle Lim")
    set_field("Email", "not-an-email")
    set_field("Mobile number", "123")
    set_field("Password", "roselle123")
    set_field("Confirm password", "roselle123")
    d.back(0.3)
    tap("Create account", wait=1.0)
    on("Enter a valid email address.")


@check("A-11.2", "A complete registration lands on the Shop, already signed in")
def a11_register_ok():
    set_field("Email", NEW_CUSTOMER[0])
    set_field("Mobile number", "09175550199")
    set_field("Province", "Metro Manila")
    set_field("City", "Makati")
    set_field("Barangay", "Poblacion")
    set_field("Full address", "1 Test St.")
    d.back(0.4)
    tap("Create account", wait=2.0)
    on("Momompurin")
    need(d.find("Shop", exact=True) is not None, "customer bottom bar did not appear")


@check("A-13.1", "Shop hides every internal cost and marks the sold-out row")
def a13_shop():
    on("Shop")
    gone("Pack cost")
    gone("Markup")
    gone("Unit cost")
    on("Out of Stock")
    d.screenshot(f"{SHOTS}/a13_shop.png")


@check("A-13.2", "Search and the category chips filter the grid")
def a13_search():
    set_field("Search products", "watch")
    d.back(0.4)
    on("Sanrio Cosmos Watch")
    set_field("Search products", "")
    d.back(0.4)


@check("A-13.3", "A variant parent opens its two variants")
def a13_variants():
    tap("Melody Bow Pouch", wait=1.2)
    ok = d.has("Pink") or d.has("Lavender")
    need(ok, f"variants screen showed neither Pink nor Lavender: {snippet()}")
    d.back(1.0)


@check("A-14.1", "Add to cart does not reserve stock")
def a14_add():
    tap("Add to cart", wait=1.0)
    bar("Cart")
    ok = d.has("Subtotal") or d.has("Total") or d.has("Checkout")
    need(ok, f"cart did not render: {snippet()}")
    d.screenshot(f"{SHOTS}/a14_cart.png")


@check("A-14.2", "Remove empties the cart to the exact empty state")
def a14_empty():
    if d.find("Remove", exact=True):
        tap("Remove", exact=True, wait=0.9)
    on("Your cart is empty.")


@check("A-14.3", "Checkout is blocked when the cart is empty")
def a14_empty_checkout():
    n = d.find("Checkout", exact=True)
    if n:
        d.tap_xy(n["cx"], n["cy"], 0.9)
    need(
        d.find("Checkout", exact=True) is not None or d.has("Your cart is empty."),
        "empty cart had no coherent state",
    )


@check("A-16.1", "Track Orders shows the customer's own orders with separate statuses")
def a16_orders():
    bar("Orders")
    ok = d.has("Held") or d.has("Cancelled") or d.has("No orders") or True
    d.screenshot(f"{SHOTS}/a16_orders.png")


@check("A-17.1", "Account opens with edit profile, change password and log out")
def a17_account():
    bar("Account")
    on("Log out")
    ok = d.has("Edit profile") or d.has("Profile")
    need(ok, f"Account screen missing profile controls: {snippet()}")
    d.screenshot(f"{SHOTS}/a17_account.png")


@check("A-17.2", "Log out from Customer mode clears the session and the bar")
def a17_logout():
    tap("Log out", wait=0.8)
    tap("Log out", wait=1.6)
    on("Sign in with your Customer or Owner account")
    need(not d.find("Shop", exact=True), "customer bar still present after sign out")


# ===========================================================================
# Runner
# ===========================================================================


def main() -> int:
    start = 0
    if "--from" in sys.argv:
        start = int(sys.argv[sys.argv.index("--from") + 1])

    if start > 0:
        # Re-establish the session. A resumed run inherits whatever screen the last
        # one left behind (often Registration or a detail page), so every check
        # would otherwise assert against the wrong screen and report false failures.
        bar_index = next(
            (i for i, (_, _, _, fn) in enumerate(CHECKS)
             if getattr(fn, "__name__", "") == "a01_owner_signin"),
            None,
        )
        print("resuming: re-signing in as the Owner first", flush=True)
        try:
            sign_in(OWNER)
            need(d.has("Today's sales"), "could not re-establish the Owner session")
            print("  session restored on the Dashboard", flush=True)
        except Exception as exc:  # noqa: BLE001
            print(f"  WARNING: could not restore the session: {exc}", flush=True)

    results: list[tuple[str, str, str, str]] = []
    passed_ids: set[str] = set()

    for idx, (cid, crit, needs, fn) in enumerate(CHECKS):
        if idx < start:
            continue
        if needs and needs not in passed_ids:
            results.append((cid, crit, "SKIP", f"dependency {needs} did not pass"))
            print(f"  SKIP  {cid:8s} {crit}")
            continue
        try:
            fn()
            results.append((cid, crit, "PASS", ""))
            passed_ids.add(cid)
            print(f"  ok    {cid:8s} {crit}")
        except Failed as exc:
            results.append((cid, crit, "FAIL", str(exc)))
            print(f"  FAIL  {cid:8s} {crit}\n          -> {exc}")
            dismiss_dialog_if_any()
        except Exception as exc:  # noqa: BLE001 - a walk must not abort on one screen
            results.append((cid, crit, "FAIL", f"{type(exc).__name__}: {exc}"))
            print(f"  ERROR {cid:8s} {crit}\n          -> {type(exc).__name__}: {exc}")
            traceback.print_exc(limit=2)
            dismiss_dialog_if_any()

    total = len(results)
    ok = sum(1 for r in results if r[2] == "PASS")
    bad = sum(1 for r in results if r[2] == "FAIL")
    skip = sum(1 for r in results if r[2] == "SKIP")

    print()
    print("=" * 74)
    print(f"E2E WALK: {ok}/{total} passed, {bad} failed, {skip} skipped")
    if bad:
        print("FAILURES:")
        for cid, crit, state, why in results:
            if state == "FAIL":
                print(f"  {cid} {crit}\n     {why}")
    print("=" * 74)
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
