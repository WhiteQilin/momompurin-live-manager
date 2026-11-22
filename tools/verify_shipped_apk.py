"""Verify the SHIPPED APK actually contains the v2.1 fixes.

Release evidence is the artifact, not the working tree: this project has already
shipped a binary whose source said one thing and whose dex said another. So this
reads `classes*.dex` out of the APK and looks for the byte-level markers of each
fix.

A marker is chosen to be stable through R8/D8 renaming where possible, and where it
is not (a method name), the check says so rather than pretending.
"""
from __future__ import annotations

import re
import sys
import zipfile

APK = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
       r"\Momompurin-Online-Ordering-v2.1.apk")

# (label, needle, is_signature_of)
CHECKS = [
    # The nav fix introduced a method with this name. Kotlin method names survive
    # R8 in a debug (unminified) build, which is what ships here.
    ("nav fix: switchTab method", "switchTab", "renamed method - unminified build keeps it"),
    # The Change Password fix needs this class referenced from AccountScreen.
    ("password masking", "PasswordVisualTransformation",
     "framework class - survives shrinking"),
    # An offline guard message that did not exist before v2.1.
    ("offline read-only message",
     "Read-only while offline. Turn Offline Mode off to save.", "string literal"),
    # The read-only product-code supporting line.
    ("read-only product code", "The code cannot be changed.", "string literal"),
    # The Add-character control added to Settings.
    ("add character control", "Add character", "string literal"),
    # The A-06 advisory wording the workbook quotes.
    ("advisory wording", "1-week advisory", "string literal"),
    # The backup JSON now writes real rows; this key only exists in the new writer.
    ("real backup JSON", "\"orderLines\": [", "string literal"),
    # DashboardFilter values travel in the route.
    ("dashboard filter route", "owner/orders/filter/", "string literal"),
]


def dex_blobs(path: str) -> bytes:
    out = bytearray()
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                out += z.read(name)
    return bytes(out)


def main() -> int:
    blob = dex_blobs(APK)
    if len(blob) < 1_000_000:
        print(f"FAIL: read only {len(blob)} bytes of dex - the APK did not open")
        return 1

    print(f"dex bytes read: {len(blob):,}")
    misses = []
    for label, needle, kind in CHECKS:
        found = needle.encode("utf-8") in blob
        print(f"  {'OK  ' if found else 'MISS'}  {label:32s} ({kind})")
        if not found:
            misses.append(label)

    print()
    if misses:
        print(f"RESULT: FAIL - {len(misses)} marker(s) absent: {misses}")
        return 1
    print(f"RESULT: PASS - all {len(CHECKS)} v2.1 markers are inside the shipped APK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
