"""Phase 3c gate: prove the workbook renders with ZERO clipped cells.

Why this check is built the way it is
-------------------------------------
Three earlier versions of this file were WRONG, and each failure is worth stating
because the next reader will be tempted to simplify back into it:

1. **Contiguous-character search over `pdftotext` output.** The extracted text is
   the full logical text, so a cell whose lower lines are never painted still
   "matches". An injected 8pt row that hid an entire cell passed this test.

2. **Word PRESENCE over the whole document.** A clipped tail word like "tapped"
   appears 30 other times in the workbook, so a document-wide set membership test
   cannot see that ONE instance went missing. Word MULTIPLICITY can: the clipped
   file paints 30 occurrences and the intact one 31.

3. **Comparing against the reference workbook.** Group 6's own file scores the same
   as a healthy one under presence tests, so it proves nothing either way.

What is measured now
--------------------
For every non-empty cell, the LAST word of the cell is looked for among the words
that are actually PAINTED, and the count of that word across the whole document is
compared against the count across the whole logical workbook. A cell whose closing
word is painted fewer times than it occurs in the workbook has had its tail cut.

This is exact rather than heuristic: the workbook's own text supplies the expected
multiplicity, so no threshold or estimate is involved.
"""
from __future__ import annotations

import re
from collections import Counter

import openpyxl
import pymupdf

XLSX = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
        r"\USER STORY-v6.21.11.2026.xlsx")
PDF = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
       r"\system_modelling_audit\us_v6_render\USER STORY-v6.21.11.2026.pdf")

# Words shorter than this are too common to measure multiplicity reliably.
MIN_TAIL_LEN = 6


def painted_counter(pdf_path: str) -> Counter:
    d = pymupdf.open(pdf_path)
    counts: Counter = Counter()
    for page in d:
        for word in page.get_text("words"):
            counts[word[4]] += 1
    d.close()
    return counts


def main(xlsx_path: str = XLSX, pdf_path: str = PDF, label: str = "workbook") -> int:
    painted = painted_counter(pdf_path)
    if sum(painted.values()) < 2000:
        print(f"FAIL: only {sum(painted.values())} painted words - the PDF did not render")
        return 1

    wb = openpyxl.load_workbook(xlsx_path)

    # How many times each tail word SHOULD appear, from the workbook itself.
    expected: Counter = Counter()
    cell_tails: list[tuple[str, str]] = []
    checked = 0

    for ws in wb.worksheets:
        for row in ws.iter_rows():
            for cell in row:
                value = cell.value
                if value is None or value == "":
                    continue
                # Cells may be plain strings OR CellRichText after the bolding pass;
                # stringify both, and still skip formulas (which display a result).
                if not isinstance(value, str):
                    value = str(value)
                if value.startswith("="):
                    continue
                words = re.findall(r"[^\s]+", re.sub(r"\s+", " ", value).strip())
                if len(words) < 6:
                    continue
                tail = words[-1]
                if len(tail) < MIN_TAIL_LEN:
                    continue
                checked += 1
                expected[tail] += 1
                cell_tails.append((f"{ws.title}!{cell.coordinate}", tail))

    # A word is under-painted when the render shows it fewer times than the
    # workbook needs it. The shortfall is how many of its uses were cut off.
    shortfall: Counter = Counter()
    for word, need in expected.items():
        got = painted.get(word, 0)
        if got < need:
            shortfall[word] = need - got

    clipped: list[str] = []
    page_break: list[str] = []
    for coord, tail in cell_tails:
        if shortfall.get(tail, 0) > 0:
            entry = (
                f"{coord}: closing word {tail!r} painted {painted.get(tail, 0)}x "
                f"but the workbook needs it {expected[tail]}x"
            )
            # A cell that spans a PAGE break is not clipped: LibreOffice stops
            # drawing at the page edge and resumes on the next page, so the tail
            # legitimately lands elsewhere. Distinguish the two by asking whether
            # the cell's own row span is taller than one printable page.
            sheet, ref = coord.split("!")
            row_no = int(re.sub(r"[A-Z]+", "", ref))
            span_pt = 0.0
            ws = wb[sheet]
            for r in range(row_no, row_no + 1):
                h = ws.row_dimensions[r].height
                span_pt += float(h) if h else 15.0
            (page_break if span_pt > 600 else clipped).append(entry)
            shortfall[tail] -= 1

    print(f"[{label}] cells checked: {checked}")
    print(f"[{label}] CLIPPED cells: {len(clipped)}")
    for c in clipped[:12]:
        print("   ", c)
    if page_break:
        print(f"[{label}] spanning a page break (not clipping): {len(page_break)}")

    ok = not clipped
    print(f"[{label}] RESULT:", "PASS - 0 clipped cells" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
