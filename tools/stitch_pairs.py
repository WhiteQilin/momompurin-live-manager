#!/usr/bin/env python3
"""Stitch [Figma frame PNG | Android emulator PNG] pairs for visual QA.

Usage: python tools/stitch_pairs.py [route ...]

Both sides are scaled to a common height and separated by a labelled gutter, so a
defect (mis-centred text, a colliding border, an unformatted currency glyph) is
visible by direct comparison rather than inferred from an XML dump.
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

FIGMA = os.path.join("docs", "visual_qa", "figma")
ANDROID = os.path.join("docs", "visual_qa", "android")
PAIRS = os.path.join("docs", "visual_qa", "pairs")

# route -> (figma node file stem, human label)
ROUTES = {
    "signin":       ("3422-431",  "A1.0 Sign In"),
    "home":         ("3452-656",  "A2.0 Dashboard"),
    "more":         ("3461-1273", "A2.6 More"),
    "notifications":("3752-3869", "N8.0 Notifications"),
    "claim":        ("3495-1488", "B1.0 Claim Form"),
    "search":       ("3512-1581", "B2.0 Item Search"),
    "session":      ("3515-1605", "B3.0 Session Claims"),
    "orders":       ("3560-1698", "D2.0 Orders/Payment"),
    "payment":      ("3560-1698", "D2.0 Payment"),
    "orderdetail":  ("3558-1843", "D1.0 Order Detail"),
    "invoice":      ("3689-2779", "G4.0 Invoice"),
    "export":       ("3692-2859", "G6.0 Export"),
    "pack":         ("3693-2918", "G8.0 Packing"),
    "shipping":     ("3742-3159", "I1.0 Shipping"),
    "sharedinvoice":("3765-3615", "C1.0 Shared Invoice"),
    "catalog":      ("3766-3652", "C2.0 Shared Catalog"),
    "keep":         ("3610-2073", "E3.0 Keep"),
    "stock":        ("3750-3557", "J2.0 Stock"),
    "product":      ("3640-2328", "F1.0 Product Form"),
    "productedit":  ("3640-2328", "F1.0 Product Edit"),
    "pos":          ("3649-2512", "F3.0 POS"),
    "adjust":       ("3650-2578", "F4.0 Stock Adjust"),
    "media":        ("3713-3171", "H2.0 Media"),
}

TARGET_H = 1400
GUTTER = 28
LABEL_H = 34


def load(path, height):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    scale = height / h
    return im.resize((max(1, int(w * scale)), height), Image.LANCZOS)


def font(size=18):
    for candidate in (
        r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arial.ttf",
    ):
        if os.path.exists(candidate):
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def stitch(route):
    stem, label = ROUTES[route]
    fig_path = os.path.join(FIGMA, f"{stem}.png")
    and_path = os.path.join(ANDROID, f"{route}.png")
    missing = [p for p in (fig_path, and_path) if not os.path.exists(p)]
    if missing:
        print(f"  SKIP {route}: missing {', '.join(os.path.basename(m) for m in missing)}")
        return False

    left = load(fig_path, TARGET_H)
    right = load(and_path, TARGET_H)
    width = left.width + GUTTER + right.width
    canvas = Image.new("RGB", (width, TARGET_H + LABEL_H), (245, 240, 235))
    canvas.paste(left, (0, LABEL_H))
    canvas.paste(right, (left.width + GUTTER, LABEL_H))

    draw = ImageDraw.Draw(canvas)
    f = font(18)
    draw.text((6, 8), f"FIGMA  {label}", fill=(70, 18, 4), font=f)
    draw.text((left.width + GUTTER + 6, 8), f"ANDROID  /{route}", fill=(158, 20, 88), font=f)
    # The gutter marks the seam so the eye can compare edges directly.
    draw.rectangle(
        [left.width, LABEL_H, left.width + GUTTER - 1, TARGET_H + LABEL_H],
        fill=(200, 190, 180),
    )

    os.makedirs(PAIRS, exist_ok=True)
    out = os.path.join(PAIRS, f"{route}.png")
    canvas.save(out)
    print(f"  stitched {out}  ({canvas.width}x{canvas.height})")
    return True


def main():
    which = sys.argv[1:] or list(ROUTES)
    ok = sum(1 for r in which if stitch(r))
    print(f"\n{ok}/{len(which)} pairs stitched")


if __name__ == "__main__":
    main()
