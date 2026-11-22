"""Builds USER STORY-v6.21.11.2026.xlsx to GROUP 6's visual standard.

Format is transcribed from GROUP6-User_Story_v5.20.11.2026.xlsx:
  - Arial 10 throughout
  - A4:I4 header bar #3D85C6, white bold, thin borders, centred
  - column widths A 9.5 / B 24 / C 16 / D 31 / E 24 / F 50 / G 52 / H 16 / I 13
  - one block per epic: A..I merged down the block's rows, F holds the scenarios
  - footer rows Passed / Failed / Total unmerged, in column G, with H "/ N"
  - freeze panes at A5
  - Summary tab: B = "A-01 Sign in:" then its 1-line summary beneath, C = yes/no
    formula, D = remarks formula, then Passed / Not yet tested / Rate

Row heights are computed from the text so nothing is clipped: the tallest column
in a block decides the height, using a conservative characters-per-line estimate
for the real column widths.
"""
from __future__ import annotations

import re

import openpyxl
from openpyxl.cell.rich_text import CellRichText, TextBlock
from openpyxl.cell.text import InlineFont
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

import us_v6_content as content

OUT = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
       r"\USER STORY-v6.21.11.2026.xlsx")

# ---------------------------------------------------------------------------
# Rich text: the exact run shape GROUP 6 uses
# ---------------------------------------------------------------------------
# Group 6's sharedStrings carries 1824 <r> runs: a BOLD title run in Arial 10,
# then a non-bold run for the body. Mirroring that shape is what makes
# "A. Open the app" bold while "Given …" stays plain.
_RPR_BOLD = InlineFont(rFont="Arial", b=True, sz=10)
_RPR_PLAIN = InlineFont(rFont="Arial", b=False, sz=10)

# Quoted tokens that must be bold inside body text: "katrinajj254@gmail.com",
# "Log in", "Orders", "ORD-1051". A quote of 2+ characters is treated as a
# literal; a quote that opens a bracketed type marker like "[Button] is not.
_QUOTED = re.compile(r'"[^"\n]{2,80}"')


def _runs(text: str) -> list[TextBlock]:
    """Split body text into plain runs and BOLD runs around every quoted token."""
    blocks: list[TextBlock] = []
    pos = 0
    for m in _QUOTED.finditer(text):
        if m.start() > pos:
            blocks.append(TextBlock(_RPR_PLAIN, text[pos:m.start()]))
        blocks.append(TextBlock(_RPR_BOLD, m.group(0)))
        pos = m.end()
    if pos < len(text):
        blocks.append(TextBlock(_RPR_PLAIN, text[pos:]))
    return blocks or [TextBlock(_RPR_PLAIN, text)]


def rich_scenario(title: str, body: str) -> CellRichText:
    """Column F: bold title run, then the Given/When/Then body with bold quotes."""
    blocks = [TextBlock(_RPR_BOLD, title), TextBlock(_RPR_PLAIN, body)]
    return CellRichText(blocks)


def rich_criteria(sample: str, sections: str) -> CellRichText:
    """Column G: bold "Sample Data", bold section headers, bold quotes.

    Section headers are the lettered lines ("A. Sign In Screen"); criterion
    numbers stay plain, and a blank line separates every numbered criterion.
    """
    blocks: list[TextBlock] = []

    def push(label: str, bold: bool) -> None:
        if not label:
            return
        if bold:
            blocks.append(TextBlock(_RPR_BOLD, label))
        else:
            blocks.extend(_runs(label))

    if sample:
        # "Sample Data" and its bullet header are bold; the bullets themselves
        # keep their quoted tokens bold and the rest plain.
        push("Sample Data\n", True)
        for line in sample[len("Sample Data\n"):].split("\n"):
            push(line + "\n" if line else "\n", False)

    # split("\n") drops the separators, so each line is re-terminated before it
    # becomes a run. Without this the header and its first criterion fuse into
    # "A. Sign In Screen1. The system shall …" and every bullet runs together.
    for line in sections.split("\n"):
        terminated = line + "\n"
        is_header = bool(re.match(r"^[A-Z]\.\s", line))
        is_sample_input = line.startswith("Sample input:")
        if is_header:
            push(terminated, True)
        elif is_sample_input:
            # Bold the "Sample input:" label, then the rest with bold quotes.
            push("Sample input:", True)
            push(line[len("Sample input:"):] + "\n", False)
        else:
            push(terminated, False)

    return CellRichText(blocks)


def spaced_criteria(sections: str) -> str:
    """Insert a blank line between every numbered criterion.

    Group 6 separates "1." from "2." with an empty line so a long criteria cell
    does not read as one dense block. The insertion happens BEFORE the rich-text
    split, so the blank lines are part of the plain text and the bold runs are
    unaffected.
    """
    out: list[str] = []
    for line in sections.split("\n"):
        # A numbered criterion that is not the first line in its section gets a
        # blank line above it. Section headers and bullets are left alone.
        if re.match(r"^\d+\.\s", line) and out and out[-1].strip():
            out.append("")
        out.append(line)
    return "\n".join(out)


# ---------------------------------------------------------------------------
# Group 6's palette and geometry
# ---------------------------------------------------------------------------
HEADER_FILL = PatternFill("solid", fgColor="FF3D85C6")
HEADER_FONT = Font(name="Arial", size=10, bold=True, color="FFFFFFFF")
BODY_FONT = Font(name="Arial", size=10)
BOLD_FONT = Font(name="Arial", size=10, bold=True)
PASSED_FILL = PatternFill("solid", fgColor="FF6AA84F")
FAILED_FILL = PatternFill("solid", fgColor="FFFF0000")
TOTAL_FILL = PatternFill("solid", fgColor="FFFFE499")
SECTION_FILL = PatternFill("solid", fgColor="FFB6D7A8")

THIN = Side(style="thin", color="FF000000")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)

WRAP_TOP_LEFT = Alignment(horizontal="left", vertical="top", wrap_text=True)
WRAP_TOP_CENTER = Alignment(horizontal="center", vertical="top", wrap_text=True)
HEADER_ALIGN = Alignment(horizontal="left", vertical="center", wrap_text=True)
HEADER_CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)

WIDTHS = {"A": 9.5, "B": 24.0, "C": 16.0, "D": 31.0, "E": 24.0,
          "F": 50.0, "G": 52.0, "H": 16.0, "I": 13.0}

HEADERS = ["No.", "Epic (System Component)", "Persona (User)", "I want to (action)",
           "So that (benefit or value)", None, "Acceptance Criteria (System Requirement)",
           "Remarks", "Status"]

# Excel column width -> usable characters.
#
# Measured from the render rather than guessed: on page 2 a wrapped line painted
# 81 characters across 448 of the 612 available points, so one width unit (≈7px)
# carries about 0.75 characters of Arial 10 - not the 1.0 an earlier version
# assumed, which is why a 199-character precondition was given a 17pt row and its
# tail silently disappeared. Using the smaller figure errs toward a taller row.
CHARS_PER_UNIT = 0.75
LINE_HEIGHT_PT = 13.5

# A wrapped cell always keeps at least one spare line: LibreOffice decides the
# break points itself, and a row sized to the exact arithmetic loses the last line
# when its wrapping differs by one word. Group 6's own header rows are 36pt for
# ~200 characters, which is this floor in practice.
MIN_WRAPPED_PT = 36.0


def _plain(value) -> str:
    """The text of a cell that may be a plain string or rich text."""
    if value is None:
        return ""
    return str(value)


def _lines(text, width_units: float) -> int:
    """How many wrapped lines `text` needs in a column of that width."""
    text = _plain(text)
    if not text:
        return 1
    usable = max(8.0, width_units * CHARS_PER_UNIT)
    total = 0
    for para in str(text).split("\n"):
        if not para.strip():
            total += 1
            continue
        # Word-wrap, counting a long unbroken token as its own overflow.
        line = 0
        used = 0.0
        for word in para.split(" "):
            w = len(word) + (1 if used else 0)
            if used + w <= usable:
                used += w
            else:
                line += 1
                used = len(word)
                while used > usable:      # a token wider than the column
                    line += 1
                    used -= usable
        total += line + 1
    return max(1, total)


def _height(texts_by_col: dict[str, str]) -> float:
    """Block height from its tallest cell, in points."""
    tallest = 1
    for col, text in texts_by_col.items():
        if text is None:
            continue
        tallest = max(tallest, _lines(text, WIDTHS[col]))
    return min(409.0, max(MIN_WRAPPED_PT, tallest * LINE_HEIGHT_PT + 6.0))


def _style(cell, *, font=BODY_FONT, align=WRAP_TOP_LEFT, fill=None, border=True):
    cell.font = font
    cell.alignment = align
    if fill is not None:
        cell.fill = fill
    if border:
        cell.border = BORDER


def build_module(wb, name: str, precondition: str, postcondition: str, epics: list[dict]):
    ws = wb.create_sheet(title=name)

    for col, width in WIDTHS.items():
        ws.column_dimensions[col].width = width

    # A-01..A-17 span 235 column-units, which does not fit a portrait page. Group 6
    # sets the module tabs to LANDSCAPE for exactly this reason; without it the
    # merged A2:I3 header row is cut off at the right margin ("... Login starts
    # befo") and the precondition's tail never renders.
    ws.page_setup.orientation = "landscape"
    ws.page_setup.paperSize = ws.PAPERSIZE_A4

    # ---- rows 1-3: the module banner, precondition, postcondition ----------
    ws.merge_cells("A1:I1")
    ws["A1"] = name
    _style(ws["A1"], font=BOLD_FONT)
    ws.row_dimensions[1].height = 22.5

    # A2/A3 span A..I, so their usable width is the SUM of all nine columns, not
    # column A's 9.5 units. Measuring them against A alone made each row ~350pt
    # tall for a 200-character sentence.
    full_width = sum(WIDTHS[c] for c in ("A", "B", "C", "D", "E", "F", "G", "H", "I"))

    ws.merge_cells("A2:I2")
    ws["A2"] = precondition
    _style(ws["A2"])
    ws.row_dimensions[2].height = max(
        MIN_WRAPPED_PT, _lines(precondition, full_width) * LINE_HEIGHT_PT + 6,
    )

    ws.merge_cells("A3:I3")
    ws["A3"] = postcondition
    _style(ws["A3"])
    ws.row_dimensions[3].height = max(
        MIN_WRAPPED_PT, _lines(postcondition, full_width) * LINE_HEIGHT_PT + 6,
    )

    # ---- row 4: the column header bar -------------------------------------
    for idx, title in enumerate(HEADERS, start=1):
        cell = ws.cell(row=4, column=idx)
        if title is not None:
            cell.value = title
        _style(
            cell,
            font=HEADER_FONT,
            align=HEADER_CENTER if idx in (1, 8, 9) else HEADER_ALIGN,
            fill=HEADER_FILL,
        )
    ws.row_dimensions[4].height = 30.0

    ws.freeze_panes = "A5"

    # ---- the epic blocks ---------------------------------------------------
    row = 5
    for epic in epics:
        scenario_rows = len(epic["scenarios"])
        footer_rows = 3                      # Passed / Failed / Total
        block_rows = 2 + scenario_rows + footer_rows   # + the "1" row + Given/When/Then

        first = row
        last = row + block_rows - 1
        footer_first = last - footer_rows + 1

        # Column A..E and G..I merge down the block WITHOUT the footer rows:
        # Group 6's A-01 block merges A5:A15 and puts Passed/Failed/Total on
        # rows 16-18, so merging through the footer would swallow the labels.
        merge_last = footer_first - 1
        for col in ("A", "B", "C", "D", "E", "G", "H", "I"):
            ws.merge_cells(f"{col}{first}:{col}{merge_last}")

        criteria = epic["sample"] + "\n\n" + epic["sections"] if epic["sample"] else epic["sections"]

        ws[f"A{first}"] = epic["no"]
        ws[f"B{first}"] = epic["title"]
        ws[f"C{first}"] = epic["persona"]
        ws[f"D{first}"] = epic["want"]
        ws[f"E{first}"] = epic["so_that"]
        ws[f"F{first}"] = "1"
        # Column G as rich text: bold "Sample Data", bold lettered section headers,
        # bold "Sample input:" labels, bold quoted tokens, and a blank line between
        # every numbered criterion - Group 6's exact readability shape.
        ws[f"G{first}"] = rich_criteria(
            epic["sample"], spaced_criteria(epic["sections"]),
        )
        ws[f"I{first}"] = "Not Tested"

        # F: "1", "Given When Then", then one scenario per row.
        ws[f"F{first + 1}"] = "Given When Then"

        for offset, (title, given, when, then) in enumerate(epic["scenarios"]):
            # Bold scenario title run, then the body with every quoted input,
            # button and tab name bold - Group 6's F-column shape.
            body = f"\n\nGiven {given},\nWhen {when},\nThen {then}"
            ws.cell(row=first + 2 + offset, column=6).value = rich_scenario(title, body)

        # Every cell in the block gets the border and the body font so the grid is
        # continuous - Group 6's blocks read as one ruled table, not floating text.
        for r in range(first, last + 1):
            for c in range(1, 10):
                cell = ws.cell(row=r, column=c)
                if cell.font is None or cell.font.name != "Arial":
                    _style(cell)
                else:
                    cell.border = BORDER
                    if cell.alignment.horizontal is None:
                        cell.alignment = WRAP_TOP_LEFT

        # Block height from the tallest of the two big cells.
        #
        # A merged cell's visible height is the SUM of the rows it spans, not the
        # height of its first row. The earlier code put the whole criteria height
        # on row 1, which hit Excel's hard 409pt row cap while the text needed up
        # to 992pt - so a tall block was silently clipped no matter what. (Group 6's
        # own reference file shows the same defect: 204 of its 341 wrapped cells
        # come back partial in a render.)
        #
        # Distributing the requirement evenly across the block's merged rows keeps
        # every row far under the cap and gives the merged G cell exactly the room
        # its text needs.
        span = merge_last - first + 1
        g_height = _height({"G": criteria})
        per_row = min(409.0, max(28.0, g_height / span))

        for r in range(first, footer_first):
            f_text = _plain(ws.cell(row=r, column=6).value)
            f_height = _height({"F": f_text}) if f_text else 0.0
            ws.row_dimensions[r].height = max(per_row, f_height)

        # ---- the unmerged Passed / Failed / Total footer ------------------
        total_label_row = footer_first + 2
        for idx, (label, fill) in enumerate(
            (("Passed", PASSED_FILL), ("Failed", FAILED_FILL), ("Total", TOTAL_FILL))
        ):
            r = footer_first + idx
            cell = ws.cell(row=r, column=7, value=label)
            _style(cell, align=WRAP_TOP_CENTER, fill=fill)
            ws.row_dimensions[r].height = 16.0

        ws.cell(row=footer_first, column=8, value="")            # Passed -> blank
        ws.cell(row=footer_first + 1, column=8, value="")        # Failed -> blank
        # "/ N" is the number of SCENARIOS, not the block's row count: block_rows
        # also holds the "1" row, the "Given When Then" label and the footer, so
        # `block_rows - 2` overstated every epic by three.
        ws.cell(row=total_label_row, column=8, value=f"/ {scenario_rows}")
        ws.cell(row=total_label_row, column=8).font = BOLD_FONT
        for r in (footer_first, footer_first + 1, total_label_row):
            for c in (8, 9):
                _style(ws.cell(row=r, column=c), align=WRAP_TOP_CENTER)

        # The scenario-count check the writer must satisfy: one F row per lettered
        # The count check that actually matters: the visible scenario rows must
        # equal the "/ N" the Total row prints. Criteria groups and scenarios are
        # deliberately NOT 1-to-1 - Group 6's A-01 has 9 scenarios against three
        # lettered criteria groups - so asserting letters == scenarios would
        # reject the reference format itself.
        written = sum(
            1 for r in range(first + 2, footer_first)
            if _plain(ws.cell(row=r, column=6).value)
        )
        if written != scenario_rows:
            raise SystemExit(
                f"{epic['no']}: wrote {written} scenario rows but expected "
                f"{scenario_rows}"
            )

        row = last + 1

    return ws


def build_summary(wb, owner_blocks, customer_blocks, owner_rows, customer_rows):
    """Group 6's Summary tab: name, one-line summary, yes/no, remarks, totals."""
    ws = wb.create_sheet(title="Summary", index=0)
    for col, width in (("A", 12.63), ("B", 61.75), ("C", 12.63), ("D", 32.63)):
        ws.column_dimensions[col].width = width

    ws["C1"] = "yes / no"
    ws["D1"] = "remarks"
    for c in ("C1", "D1"):
        ws[c].font = Font(name="Arial", size=9)
        ws[c].alignment = Alignment(horizontal="left", vertical="top")

    row = 2

    def module(header: str, blocks, sheet: str, rows: list[int]):
        nonlocal row
        cell = ws.cell(row=row, column=2, value=header)
        cell.font = Font(name="Arial", size=9, bold=True)
        cell.alignment = Alignment(horizontal="left", vertical="top")
        cell.fill = SECTION_FILL
        row += 1
        for block, sheet_row in zip(blocks, rows):
            c = ws.cell(row=row, column=2, value=f"{block['no']} {block['title']}:")
            c.font = Font(name="Arial", size=9, bold=True)
            c.alignment = Alignment(horizontal="left", vertical="top")
            # Group 6 drives "yes / no" from the module tab's Status cell (I) and
            # remarks from its Remarks cell (H), so the Summary can never disagree
            # with the epic it summarises.
            ws.cell(
                row=row, column=3,
                value=f"=IF('{sheet}'!I{sheet_row}=\"Tested\",1,0)",
            ).font = Font(name="Arial", size=9)
            ws.cell(
                row=row, column=4,
                value=f"=IF('{sheet}'!H{sheet_row}=\"\",\"\",'{sheet}'!H{sheet_row})",
            ).font = Font(name="Arial", size=9)
            row += 1

            summary = ws.cell(row=row, column=2, value=block["summary"])
            summary.font = Font(name="Arial", size=9)
            summary.alignment = Alignment(horizontal="left", vertical="top", wrap_text=True)
            row += 1

    first_total = row
    module("Owner Module - Target User: Owner (phone)", owner_blocks, "Owner Module", owner_rows)
    module("Customer Module - Target User: Customer (phone)", customer_blocks, "Customer Module",
           customer_rows)

    last_row = row - 1
    for label, formula in (
        ("Passed", f"=IFERROR(SUM(C{first_total}:C{last_row}),0)"),
        ("Not yet tested", f"=IFERROR(COUNT(C{first_total}:C{last_row})-C{row},0)"),
        ("Rate", f"=IFERROR(C{row}/COUNT(C{first_total}:C{last_row})*100,0)"),
    ):
        lbl = ws.cell(row=row, column=2, value=label)
        lbl.font = Font(name="Arial", size=9)
        lbl.alignment = Alignment(horizontal="right", vertical="top")
        val = ws.cell(row=row, column=3, value=formula)
        val.font = Font(name="Arial", size=9)
        val.alignment = Alignment(horizontal="left", vertical="top")
        row += 1
    # The "Not yet tested" formula references the Passed row it just wrote.
    ws.cell(row=row - 2, column=3).value = (
        f"=IFERROR(COUNT(C{first_total}:C{last_row})-C{row - 3},0)"
    )
    ws.cell(row=row - 1, column=3).value = (
        f"=IFERROR(C{row - 3}/COUNT(C{first_total}:C{last_row})*100,0)"
    )
    return ws


def main() -> int:
    owner = content.EPICS[:10]
    customer = content.EPICS[10:]
    assert len(owner) == 10, f"expected A-01..A-10, got {len(owner)}"
    assert len(customer) == 7, f"expected A-11..A-17, got {len(customer)}"

    pre_owner = (
        "Precondition: The Owner has access to Momompurin and an Active Owner account. Login "
        "starts before authentication; every other process requires a signed-in Owner session "
        "and the stated sample records."
    )
    post_owner = (
        "Postcondition: Successful actions save the related records, statuses, balances and "
        "stock once. Rejected entries preserve the existing data and allow correction. Each "
        "separate Given clause establishes its own sample situation, so one scenario never "
        "depends on another having run first."
    )
    pre_cust = (
        "Precondition: The Customer has access to Momompurin. Signing in and creating an account "
        "are public; every other process requires a signed-in Customer session and the stated "
        "sample records."
    )
    post_cust = (
        "Postcondition: Successful actions save the related records, the cart and the stock "
        "once. Rejected entries preserve the existing data and allow correction. Each separate "
        "Given clause establishes its own sample situation."
    )

    wb = openpyxl.Workbook()
    wb.remove(wb.active)

    build_module(wb, "Owner Module", pre_owner, post_owner, owner)
    build_module(wb, "Customer Module", pre_cust, post_cust, customer)

    # Summary needs each block's Type row, which is its block's first row.
    owner_rows = []
    r = 5
    for e in owner:
        owner_rows.append(r)
        r += 2 + len(e["scenarios"]) + 3
    cust_rows = []
    r = 5
    for e in customer:
        cust_rows.append(r)
        r += 2 + len(e["scenarios"]) + 3

    build_summary(wb, owner, customer, owner_rows, cust_rows)

    wb.save(OUT)
    print("wrote", OUT)
    print(f"  epics: {len(content.EPICS)}  scenarios: "
          f"{sum(len(e['scenarios']) for e in content.EPICS)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
