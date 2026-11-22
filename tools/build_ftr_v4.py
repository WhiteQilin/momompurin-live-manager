"""Sync FUNCTIONAL_TEST_RESULT-v4.21.11.2026.docx from USER STORY-v6.

The Word file is generated FROM the workbook, never edited in parallel, so the two
documents cannot disagree about an epic name, a scenario or a criterion.

Layout, per epic:
    heading      A-01  Sign in
    table        one row per scenario: No. | Scenario | Given | When | Then | Result
    footer note  "Passed __ / Failed __ / Total N"

The table uses the same Arial 10 and the same blue header as the workbook, and each
column gets an explicit <w:tblGrid> width (a missing or zero grid is what makes
Google Docs collapse the table when the file is uploaded).
"""
from __future__ import annotations

import sys

import docx
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

import us_v6_content as content

OUT = (r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)"
       r"\FUNCTIONAL_TEST_RESULT-v4.21.11.2026.docx")

# A4 portrait with 2 cm margins leaves ~17 cm of text width.
COL_CM = [1.6, 5.4, 3.4, 3.4, 3.4, 1.6]

HEADER_BG = "3D85C6"


def shade(cell, hex_fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_fill)
    tc_pr.append(shd)


def set_grid(table, widths_cm: list[float]) -> None:
    """An explicit tblGrid. A missing grid is what collapses in Google Docs."""
    tbl = table._tbl
    grid = tbl.find(qn("w:tblGrid"))
    if grid is not None:
        tbl.remove(grid)
    grid = OxmlElement("w:tblGrid")
    for w in widths_cm:
        col = OxmlElement("w:gridCol")
        col.set(qn("w:w"), str(int(w * 567)))     # 1 cm = 567 twips
        grid.append(col)
    tbl.insert(1, grid)


def set_cell(cell, text: str, *, bold=False, white=False, size=9) -> None:
    cell.text = ""
    p = cell.paragraphs[0]
    run = p.add_run(text)
    run.font.name = "Arial"
    run.font.size = Pt(size)
    run.bold = bold
    if white:
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
    p.paragraph_format.space_after = Pt(0)


def rule(par) -> None:
    """A bottom border on a paragraph, so a section reads as a band."""
    p_pr = par._p.get_or_add_pPr()
    borders = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "6")
    bottom.set(qn("w:space"), "1")
    bottom.set(qn("w:color"), "B6D7A8")
    borders.append(bottom)
    p_pr.append(borders)


def main() -> int:
    doc = docx.Document()

    # Page setup: A4 portrait, 2 cm margins.
    section = doc.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    for attr in ("left_margin", "right_margin", "top_margin", "bottom_margin"):
        setattr(section, attr, Cm(2.0))

    normal = doc.styles["Normal"]
    normal.font.name = "Arial"
    normal.font.size = Pt(10)

    title = doc.add_paragraph()
    run = title.add_run("FUNCTIONAL TEST RESULT")
    run.font.name = "Arial"
    run.font.size = Pt(16)
    run.bold = True
    run.font.color.rgb = RGBColor(0x3D, 0x85, 0xC6)
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER

    sub = doc.add_paragraph()
    r = sub.add_run(
        "Momompurin Online Ordering and Inventory Management  |  Version 2.1  |  "
        "21 November 2026"
    )
    r.font.name = "Arial"
    r.font.size = Pt(10)
    sub.alignment = WD_ALIGN_PARAGRAPH.CENTER

    note = doc.add_paragraph()
    n = note.add_run(
        "Generated from USER STORY-v6.21.11.2026.xlsx, which is the source of truth. "
        "Every scenario below is run against the shipped APK "
        "Momompurin-Online-Ordering-v2.1.apk on a cleared install."
    )
    n.font.name = "Arial"
    n.font.size = Pt(9)
    n.italic = True

    total_scenarios = 0
    for module_title, blocks in (
        ("OWNER MODULE — Target User: Owner (phone)", content.EPICS[:10]),
        ("CUSTOMER MODULE — Target User: Customer (phone)", content.EPICS[10:]),
    ):
        doc.add_page_break()
        band = doc.add_paragraph()
        band_run = band.add_run(module_title)
        band_run.font.name = "Arial"
        band_run.font.size = Pt(13)
        band_run.bold = True
        band_run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        shade_par = band._p.get_or_add_pPr()
        shd = OxmlElement("w:shd")
        shd.set(qn("w:val"), "clear")
        shd.set(qn("w:color"), "auto")
        shd.set(qn("w:fill"), HEADER_BG)
        shade_par.append(shd)

        for epic in blocks:
            h = doc.add_paragraph()
            hr = h.add_run(f"{epic['no']}  {epic['title']}")
            hr.font.name = "Arial"
            hr.font.size = Pt(11)
            hr.bold = True
            hr.font.color.rgb = RGBColor(0x1F, 0x30, 0x48)

            persona = doc.add_paragraph()
            pr = persona.add_run(
                f"{epic['persona']}: {epic['want']} {epic['so_that']}."
            )
            pr.font.name = "Arial"
            pr.font.size = Pt(9)
            pr.italic = True
            rule(persona)

            scenarios = epic["scenarios"]
            total_scenarios += len(scenarios)

            table = doc.add_table(rows=1, cols=6)
            table.style = "Table Grid"
            table.alignment = WD_TABLE_ALIGNMENT.CENTER
            set_grid(table, COL_CM)

            heads = ["No.", "Scenario", "Given", "When", "Then", "Result"]
            for i, head in enumerate(heads):
                cell = table.rows[0].cells[i]
                set_cell(cell, head, bold=True, white=True)
                shade(cell, HEADER_BG)

            for idx, (s_title, given, when, then) in enumerate(scenarios, start=1):
                row = table.add_row()
                set_cell(row.cells[0], f"{epic['no'].split('-')[1]}.{idx}")
                set_cell(row.cells[1], s_title)
                set_cell(row.cells[2], given)
                set_cell(row.cells[3], when)
                set_cell(row.cells[4], then)
                set_cell(row.cells[5], "")

            foot = doc.add_paragraph()
            fr = foot.add_run(
                f"Passed ____    Failed ____    Total / {len(scenarios)}"
            )
            fr.font.name = "Arial"
            fr.font.size = Pt(9)
            fr.bold = True

    doc.save(OUT)
    print("wrote", OUT)
    print(f"  epics: {len(content.EPICS)}  scenarios: {total_scenarios}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
