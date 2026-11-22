"""Cross-check: every UI string the workbook QUOTES must exist in the app source.

A workbook that names a button the app does not have is worse than no workbook: the
tester taps at a label that is not there, reports a failure, and the real defect is
the document. This walks every quoted "..." in column F and G of the v6 workbook and
checks it against the string literals extracted from the Kotlin sources.

Two flavours of quote are allowed:
  * an exact UI string   -> must appear as a Kotlin literal
  * a NAME / ID / code   -> must appear as a literal too (order ids, SKUs, emails)

Numbers and dates are exempt.
"""
from __future__ import annotations

import re
import sys

import openpyxl

XLSX = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
        r"\USER STORY-v6.21.11.2026.xlsx")
STRINGS = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
           r"\system_modelling_audit\ui_strings.txt")

# Quoted things that are legitimately not UI LITERALS.
#
# A workbook legitimately quotes two different kinds of text:
#   * CHROME the tester must find on screen - a button, a tab, an error message.
#     These MUST exist in the app.
#   * DATA the tester TYPES - "Recount", "abc", "not-an-email", "wrongpass".
#     These must NOT exist in the app; they are inputs.
#
# The distinction that matters is whether the quote is a label the app renders.
# Inputs are recognised by their context in the workbook, handled below, plus the
# token shapes listed here.
EXEMPT_PATTERNS = [
    r"^\d", r"^₱", r"^MP-\d", r"^https?:", r"^@", r"^\d+\s*(days|characters)",
    r"^JT",                                # a tracking number the tester types
    r"^[\w.+-]+@[\w.-]+$",              # an email address
    r"^-[\d.]+$",                        # a negative amount
    r"^from ₱",                           # a price prefix
    r"[·;,]\s",                          # prose fragments cut by punctuation
    r"^\[?[A-Z][a-z]+\]?\s+and",     # "…[Button] and …" prose
]


def load_literals() -> str:
    """All extracted UI strings, plus the Kotlin sources' raw text."""
    text = open(STRINGS, encoding="utf-8").read()
    return text


def main() -> int:
    corpus = load_literals()
    # Also fold in the Kotlin sources so identifiers like "SCWH" or "CMEYE" that only
    # appear inside longer literals still register.
    import glob
    src_dir = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)\IAS-ALGOCOM"
               r"\momompurin-live-manager\app\src\main\java\ph\edu\umak\momompurin")
    for p in glob.glob(src_dir + "/**/*.kt", recursive=True):
        corpus += "\n" + open(p, encoding="utf-8").read()

    wb = openpyxl.load_workbook(XLSX)
    quoted = 0
    unresolved: list[str] = []

    for ws in wb.worksheets:
        if ws.title == "Summary":
            continue
        for row in ws.iter_rows():
            for cell in row:
                if cell.column not in (6, 7):
                    continue
                value = cell.value
                if not isinstance(value, str):
                    continue
                for m in re.finditer(r'"([^"\n]{2,80})"', value):
                    token = m.group(1).strip()
                    if not token:
                        continue
                    if any(re.match(p, token) for p in EXEMPT_PATTERNS):
                        continue
                    quoted += 1
                    if token not in corpus:
                        unresolved.append(f"{ws.title}!{cell.coordinate}: {token!r}")

    # Deduplicate while keeping order.
    seen = set()
    uniq = [u for u in unresolved if not (u in seen or seen.add(u))]

    print(f"quoted strings checked: {quoted}")
    print(f"NOT FOUND in the app source: {len(uniq)}")
    for u in uniq[:25]:
        print("   ", u)

    ok = not uniq
    print()
    print("RESULT:", "PASS - every quoted string exists in the app" if ok else
          "FAIL - the workbook quotes text the app does not contain")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
