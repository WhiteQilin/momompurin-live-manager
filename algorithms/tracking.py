"""Courier tracking-number validation.

Before an order is marked In Transit, the typed or scanned tracking number is
validated against the courier's format. The rule comes from the client's own
selling cycle (v2.4): J&T Express is the primary courier, Lalamove and Grab
handle same-day local delivery, TikTok Shop orders carry platform tracking,
and Customer Pickup needs no tracking number at all.

A wrong digit on a waybill means a parcel the owner cannot trace, so the check
runs at entry time — before the parcel is handed over, not after.

Customer Pickup is handled by the caller, not here: the selling cycle states it
needs no courier booking or tracking number, so the app skips validation for
that status instead of rejecting the order.
"""

from __future__ import annotations

import re

# Formats are the real public ones for Philippine couriers.
COURIER_PATTERNS: dict[str, str] = {
    # J&T domestic: 10-13 digit numeric, optionally prefixed with JT
    "J&T Express": r"^(?:JT)?\d{10,13}$",
    # Lalamove: alphanumeric booking code with a letter prefix
    "Lalamove": r"^(?:LMB|LM)[-A-Z0-9]{7,14}$",
    # Grab Express: alphanumeric with GRAB or GRAB-PH prefix
    "Grab Express": r"^GRAB(?:-PH)?[-A-Z0-9]{5,13}$",
    # TikTok Shop: SPH + digits, or plain numeric 10-20
    "TikTok Shop": r"^SPH\d{10,18}$|^\d{10,20}$",
}

FIXTURES: list[tuple[str, str, bool]] = [
    ("J&T Express", "JT0012345678", True),
    ("J&T Express", "76123456789", True),
    ("J&T Express", "JT123", False),           # too short
    ("J&T Express", "JT00123456789O", False),  # letter O inside the digit run
    ("Lalamove", "LMB-8H2K4M", True),
    ("Lalamove", "LM123", False),              # too short
    ("Grab Express", "GRAB-PH-123456", True),
    ("Grab Express", "GR", False),
    ("TikTok Shop", "SPH2406031234567", True),
    ("TikTok Shop", "24060312345", True),
    ("TikTok Shop", "SPH12", False),
]


def validate_tracking(courier: str, number: str) -> tuple[bool, str]:
    """Return (ok, reason) for a (courier, tracking number) pair."""
    n = number.strip().upper()
    pat = COURIER_PATTERNS.get(courier)
    if pat is None:
        return False, "unknown courier"
    if re.fullmatch(pat, n):
        return True, "format ok"
    return False, "does not match courier format"


def self_test() -> None:
    """Run the fixtures; raise AssertionError on any disagreement."""
    for courier, number, want in FIXTURES:
        ok, _ = validate_tracking(courier, number)
        assert ok == want, f"{courier} {number!r}: got {ok}, want {want}"


if __name__ == "__main__":
    self_test()
    print(f"{len(FIXTURES)}/{len(FIXTURES)} tracking fixtures pass")
