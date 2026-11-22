"""Complete Compose alignment & geometry audit.

STEP 2 scan: every Box( and Row( in ui/components and ui/screens, flagged when:
  - a Box that wraps content AND sizes itself (.size/.height/.heightIn/.sizeIn)
    or is clickable, but has NO contentAlignment = ... (Box defaults to TopStart,
    so a taller-than-content box pushes its label to the top edge);
  - a Row(...) whose block contains both a Text and a second child (button/badge/
    icon) but no verticalAlignment = ... (Row defaults to Top, so siblings of
    different heights drift apart vertically).

Findings are printed as file:line with the offending declaration, and a JSON
report is written so the fix pass can work through them.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)\IAS-ALGOCOM\momompurin-live-manager")
UI = ROOT / "app" / "src" / "main" / "java" / "ph" / "edu" / "umak" / "momompurin" / "ui"

BOX_ALIGN = re.compile(r"contentAlignment\s*=")
ROW_ALIGN = re.compile(r"verticalAlignment\s*=")
SIZERS = re.compile(r"\.(size\(|sizeIn\(|height\(|heightIn\(|width\(|fillMaxHeight\()")
ROW_CHILD = re.compile(r"\b(Text|Icon|MomoButton|MiniAction|Badge|StatusBadge)\s*\(")


def blocks(src: str, name: str):
    """Yield (line_no, decl, block) for every Box( / Row( call, INCLUDING its
    trailing lambda — `Row(...) { ... }` puts the content AFTER the parens, so
    stopping at `)` misses every child and reports nothing."""
    for m in re.finditer(r"\b(Box|Row)\s*\(", src):
        start = m.start()
        line = src[:start].count("\n") + 1
        # Walk forward tracking parens; when the outermost paren closes, if the
        # next non-space character is `{`, keep going through that lambda too.
        i = m.end() - 1
        depth = 0
        end = None
        in_str = False
        quote = ""
        while i < len(src):
            ch = src[i]
            if in_str:
                if ch == "\\":
                    i += 2
                    continue
                if ch == quote:
                    in_str = False
                i += 1
                continue
            if ch in "\"'":
                in_str = True
                quote = ch
                i += 1
                continue
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    # paren pair closed; check for a trailing lambda
                    j = i + 1
                    while j < len(src) and src[j] in " \t\r\n":
                        j += 1
                    if j < len(src) and src[j] == "{":
                        # resync: walk the lambda braces from here
                        d2 = 0
                        k = j
                        while k < len(src):
                            c2 = src[k]
                            if c2 == "{":
                                d2 += 1
                            elif c2 == "}":
                                d2 -= 1
                                if d2 == 0:
                                    end = k
                                    break
                            k += 1
                    end = end if end is not None else i
                    break
            i += 1
        if end is None:
            continue
        yield line, m.group(1), src[start:end + 1]


def scan() -> list[dict]:
    findings = []
    for f in sorted(list((UI / "components").glob("*.kt")) +
                    list((UI / "screens").glob("*.kt"))):
        src = f.read_text(encoding="utf8")
        for line, decl, block in blocks(src, f.name):
            body = block
            if decl == "Box":
                if BOX_ALIGN.search(body):
                    continue
                # Only flag boxes that SIZE themselves or take clicks: a bare
                # layout Box that hugs its content is correctly TopStart.
                if SIZERS.search(body) or ".clickable" in body:
                    # ...and only when something is actually placed inside it.
                    if re.search(r"\b(Text|Icon)\s*\(", body.split(")", 1)[-1]):
                        findings.append({
                            "file": str(f.relative_to(UI)),
                            "line": line,
                            "kind": "Box",
                            "why": "sized/clickable Box without contentAlignment "
                                   "(defaults to TopStart - content hugs the top)",
                            "snippet": body[:180],
                        })
            else:  # Row
                if ROW_ALIGN.search(body):
                    continue
                texts = len(re.findall(r"\bText\s*\(", body))
                others = len(ROW_CHILD.findall(body)) - texts
                # Rows that mix a text with an icon/badge/button and nothing
                # aligns them: siblings of different heights drift to Top.
                if texts >= 1 and others >= 1:
                    findings.append({
                        "file": str(f.relative_to(UI)),
                        "line": line,
                        "kind": "Row",
                        "why": "text + button/badge/icon Row without "
                               "verticalAlignment (defaults to Top)",
                        "snippet": body[:180],
                    })
    return findings


def main() -> int:
    findings = scan()
    out = Path(__file__).resolve().parent / "alignment_findings.json"
    out.write_text(json.dumps(findings, indent=1), encoding="utf8")
    print(f"scanned ui/components + ui/screens -> {len(findings)} findings\n")
    by_file: dict[str, list[dict]] = {}
    for f in findings:
        by_file.setdefault(f["file"], []).append(f)
    for file, items in sorted(by_file.items()):
        print(f"{file}  ({len(items)})")
        for it in items[:6]:
            first = " ".join(it["snippet"].split())[:110]
            print(f"   L{it['line']:4d} {it['kind']:3s} {it['why']}")
            print(f"         {first}")
        if len(items) > 6:
            print(f"   ... +{len(items) - 6} more")
    print(f"\nreport: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
