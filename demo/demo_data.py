"""Demo catalogue for the algorithm modules.

This is a SYNTHETIC catalogue in the shape of the client's real one (SKU
conventions like POM-MUG-01, brand shorthands like "HK mini wipes"), NOT the
client's 767-row workbook. Real customer-linked data stays out of this
repository; the ALGOCOM PBL report documents the real benchmark, which ran
against the client workbook locally with the same seed and methodology.
"""

from __future__ import annotations

BRANDS = [
    ("hello kitty", "HK"),
    ("cinnamoroll", "CINNA"),
    ("my melody", "MELODY"),
    ("pompompurin", "POM"),
    ("kuromi", "KRMI"),
    ("hangyodon", "HANGYO"),
    ("keroppi", "KERO"),
    ("tuxedosam", "TUX"),
]

ITEM_TYPES = [
    "mini wipes", "plush keychain", "mug", "sticker sheet", "plush 8 inch",
    "pouch", "notebook", "pen", "lanyard", "bag charm", "tumbler",
    "photo card set",
]

COLORS = ["pink", "blue", "purple", "white", "yellow", "red", "mint", "black"]

VARIANTS = ["", " plain", " glitter", " ribbon", " pastel", " dark"]


def catalogue() -> list[tuple[str, str]]:
    """(SKU, display name) pairs, e.g. ('POM-MUG-01', 'Pompompurin mug')."""
    items: list[tuple[str, str]] = []
    n = 1
    for brand, code in BRANDS:
        for item in ITEM_TYPES:
            for color in COLORS:
                if color == "white" and "plush" in item:
                    continue  # keep the catalogue under 200 rows for the demo
                sku = f"{code}-{item.split()[0].upper()[:4]}-{n:02d}"
                name = f"{brand.capitalize()} {item} {color}".strip()
                items.append((sku, name))
                n += 1
    return items


# Messy live-chat queries a buyer actually types, with the SKU family they
# mean. Queries are typed variants of full catalogue names (chat noise, dropped
# words, typos) — the same distribution the ALGOCOM benchmark documents.
TYPED_COMMENTS: list[tuple[str, str]] = [
    ("claim po hello kitty mug blue", "HK-MUG-"),
    ("mine my melody pouch purple", "MELODY-POU"),
    ("sakin cinnamoroll plush keychain pink", "CINNA-PLU"),
    ("ako po kuromi notebook", "KRMI-NOT"),
    ("claim pompompurin tumbler yelow", "POM-TUM"),
    ("hello kitty lanyard black mine", "HK-LAN"),
    ("cinnamoroll bag charm mint po", "CINNA-BAG"),
    ("my melody photo card st pink claim", "MELODY-PH"),
]
