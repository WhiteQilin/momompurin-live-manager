"""Generate static weight-instanced TTFs from the variable fonts.

WHY THIS EXISTS
---------------
`res/font/` shipped variable TTFs and relied on Compose's
`FontVariation.Settings(FontVariation.weight(...))` to pick a weight. That works on
API 36 (which maps FontWeight onto the `wght` axis) but NOT on Android 14/15 and
not on OEM renderers (HyperOS/MIUI/OneUI), which load a variable TTF resource at
its DEFAULT `wght` axis and ignore the settings. Fredoka's default is
**wght=300 (Light)** - so on those devices every Fredoka weight rendered LIGHTER
than Regular, collapsing the whole hierarchy.

The fix is to ship one static instance per weight. Each output file has:
  - `fvar` STRIPPED (it is no longer a variable font, so no renderer can pick an
    axis value for it),
  - `OS/2.usWeightClass` set to the real weight (400/500/600/700),
  - a weight-specific family + subfamily name, so the platform's font matcher
    cannot confuse the four files.

Run:  python tools/make_static_fonts.py
"""
from __future__ import annotations

import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "font"
# The variable sources live OUTSIDE res/font on purpose: once the static instances
# exist, a variable TTF in res/font would be a second, silently-different face that
# Android could pick instead. Keeping them here means the build cannot ship them.
SRC = Path(__file__).resolve().parent / "font_sources"

# source variable font -> (output stem, family name for the static file)
SOURCES = {
    "fredoka.ttf": ("fredoka", "Fredoka"),
    "mplus1code.ttf": ("mplus1code", "M PLUS 1 Code"),
    "comfortaa.ttf": ("comfortaa", "Comfortaa"),
}

WEIGHTS = [
    ("regular", 400, "Regular"),
    ("medium", 500, "Medium"),
    ("semibold", 600, "SemiBold"),
    ("bold", 700, "Bold"),
]


def set_name(font: TTFont, family: str, subfamily: str, full: str, ps: str) -> None:
    """Rewrite the name table so the platform sees a distinct static family."""
    name = font["name"]
    # nameID -> value. 1 family, 2 subfamily, 4 full, 6 postscript, 16/17 typographic
    values = {
        1: family,
        2: subfamily,
        4: full,
        6: ps,
        16: family,
        17: subfamily,
    }
    for rec in list(name.names):
        if rec.nameID in values:
            name.setName(
                values[rec.nameID],
                rec.nameID,
                rec.platformID,
                rec.platEncID,
                rec.langID,
            )
    # Some fonts lack the typographic records; add them on the Windows/Unicode pair.
    for nid in (16, 17):
        if not name.getDebugName(nid):
            name.setName(values[nid], nid, 3, 1, 0x409)


def instance(source: Path, out_stem: str, family: str, suffix: str, wght: int,
             style_name: str) -> Path:
    font = TTFont(str(source))

    # 1. Pin the wght axis (and wdth to its default so a width axis cannot drift).
    axes = {}
    fvar = font.get("fvar")
    if fvar:
        for axis in fvar.axes:
            axes[axis.axisTag] = wght if axis.axisTag == "wght" else axis.defaultValue
    if "wght" in axes:
        font = instancer.instantiateVariableFont(font, axes, inplace=False, updateFontNames=False)

    # 2. It must no longer be variable. instantiateVariableFont already drops fvar,
    #    but assert it so a fontTools behaviour change cannot silently regress this.
    if font.get("fvar") is not None:
        del font["fvar"]
    if font.get("STAT") is not None:
        del font["STAT"]
    if font.get("avar") is not None:
        del font["avar"]
    if font.get("gvar") is not None:
        del font["gvar"]

    # 3. The real weight class - this is what Android's matcher reads.
    #
    # fsSelection is rebuilt from scratch rather than patched: the source variable
    # fonts carry stray bits (Fredoka ships 0b11000001, i.e. ITALIC set), and
    # fontTools correctly rejects a file whose REGULAR bit coexists with BOLD.
    # Only these bits are meaningful for a static upright face:
    #   bit 5 (0x20) BOLD, bit 6 (0x40) REGULAR, bit 7 (0x80) USE_TYPO_METRICS.
    os2 = font["OS/2"]
    os2.fsSelection = (os2.fsSelection & 0x80)  # keep USE_TYPO_METRICS only
    if wght == 400:
        os2.fsSelection |= 0x40          # REGULAR
    elif wght >= 700:
        os2.fsSelection |= 0x20          # BOLD
    os2.usWeightClass = wght

    # head.macStyle bit 0 = bold, bit 1 = italic. Neither face is italic.
    font["head"].macStyle = 0b1 if wght >= 700 else 0b0

    # 4. Distinct names per weight so nothing confuses the four files.
    full = f"{family} {style_name}"
    ps = f"{family.replace(' ', '')}-{style_name}"
    set_name(font, family, style_name, full, ps)

    out = RES / f"{out_stem}_{suffix}.ttf"
    font.save(str(out))
    font.close()
    return out


def verify(path: Path, expect_wght: int) -> tuple[bool, str]:
    f = TTFont(str(path), lazy=True)
    problems = []
    if f.get("fvar") is not None:
        problems.append("fvar still present")
    if f.get("gvar") is not None:
        problems.append("gvar still present")
    actual = f["OS/2"].usWeightClass
    if actual != expect_wght:
        problems.append(f"usWeightClass={actual} expected {expect_wght}")
    # The style bits must be self-consistent, or Android's matcher may reject the
    # face and silently fall back to the system font.
    sel = f["OS/2"].fsSelection
    mac = f["head"].macStyle
    if sel & 0x1:
        problems.append("ITALIC bit set on an upright face")
    if mac & 0x2:
        problems.append("macStyle italic set")
    if expect_wght == 400:
        if not (sel & 0x40):
            problems.append("REGULAR bit missing on the 400 face")
        if sel & 0x20:
            problems.append("BOLD bit set on the 400 face")
    elif expect_wght >= 700:
        if not (sel & 0x20):
            problems.append("BOLD bit missing on the 700 face")
        if sel & 0x40:
            problems.append("REGULAR bit set on the 700 face")
        if not (mac & 0x1):
            problems.append("macStyle bold missing on the 700 face")
    glyphs = f["maxp"].numGlyphs
    family = f["name"].getDebugName(1)
    sub = f["name"].getDebugName(2)
    f.close()
    ok = not problems
    detail = (
        f"{path.name}: usWeightClass={actual} family={family!r} style={sub!r} "
        f"glyphs={glyphs} fsSel=0b{sel:08b}"
    )
    if problems:
        detail += "  PROBLEMS: " + "; ".join(problems)
    return ok, detail


def main() -> int:
    if not RES.is_dir():
        print(f"font dir not found: {RES}", file=sys.stderr)
        return 2

    generated = []
    for src_name, (stem, family) in SOURCES.items():
        src = SRC / src_name
        if not src.exists():
            print(f"MISSING SOURCE: {src}", file=sys.stderr)
            return 2
        for suffix, wght, style in WEIGHTS:
            out = instance(src, stem, family, suffix, wght, style)
            generated.append((out, wght))
            print(f"  wrote {out.name}")

    print("\nVerification:")
    all_ok = True
    for path, wght in generated:
        ok, detail = verify(path, wght)
        all_ok &= ok
        print(("  OK   " if ok else "  FAIL ") + detail)

    # The static files must not be variable and must all differ from each other.
    sizes = {}
    for path, _ in generated:
        sizes.setdefault(path.name.split("_")[0], set()).add(path.stat().st_size)
    print("\nPer-family size spread (proves the instances really differ):")
    for fam, ss in sorted(sizes.items()):
        print(f"  {fam}: {len(ss)} distinct sizes {sorted(ss)}")
        if len(ss) != 4:
            all_ok = False
            print(f"    !! {fam} produced identical files - instancing did not work")

    print("\nRESULT:", "ALL OK" if all_ok else "FAILURES PRESENT")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
