#!/usr/bin/env python3
"""Build `app/src/main/assets/ph_addresses.json` from the public PSGC dataset.

Why this script exists
----------------------
The app's address cascade used to ship a hand-curated 7-province map. That was a
deliberate size trade-off, but it made a real customer outside those provinces fall
into "Other (type manually)" for a barangay the government has published for
decades - and the tester's report asked for the full list, searchable.

This script turns the community PSGC dump into one compact asset:

    { "<Province>": { "<City>": ["<Barangay>", ...], ... }, ... }

It is reproducible: same input, same output, byte-for-byte, because every list is
sorted and every key order is explicit. Re-run it whenever the PSGC source changes:

    python tools/build_ph_addresses_asset.py

Normalization rules (each one is load-bearing)
---------------------------------------------
1. NCR collapse. The PSGC splits the capital into five pseudo-provinces, one of
   which ("NATIONAL CAPITAL REGION - MANILA") holds 14 DISTRICTS whose "barangays"
   are `BARANGAY 287`-style zone numbers. A customer does not pick "Tondo" as a
   city; they pick Manila and then a barangay. So all five are folded into the one
   province key `Metro Manila`, the 14 districts become barangays of `Manila`, and
   the 16 real LGUs stay as cities. Result: exactly the 17 NCR LGUs.
2. Title case, with the words a naive `str.title()` breaks preserved:
   Roman numerals (I..XX), `Pob.`, `Sto.`, `Sta.`, `Bgc`, `Ncr`, `Ii`, `Iii`.
   City prefixes are stripped: `CITY OF MAKATI` -> `Makati`, but `QUEZON CITY`
   keeps its suffix because that is the name people search for.
3. Seed parity. Every (province, city, barangay) triple in `SeedData.kt` is forced
   present. Where the PSGC spelling differs (`BATASAN HILLS` vs the seed's
   `Batasan`, `SAN ROQUE (POB.)` vs `San Roque`), the seed spelling is added as an
   ALIAS rather than replacing the official one, so both search terms work.
4. BGC. `FORT BONIFACIO` is the official barangay; `BGC` is what everyone types,
   so both are present under Taguig.
5. Molino. `BACOOR CITY` carries `MOLINO I`..`MOLINO VII`; the seed uses bare
   `Molino`, which is added as an alias.

The output is UTF-8 with no BOM, one province per line, so a diff of two builds is
readable.
"""

from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path

# --------------------------------------------------------------------------- paths

ROOT = Path(__file__).resolve().parents[1]          # momompurin-live-manager/
SOURCE = ROOT.parents[1] / "_tmp" / "psgc" / "raw_psgc.json"
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "ph_addresses.json"
SOURCE_URL = (
    "https://raw.githubusercontent.com/flores-jacob/"
    "philippine-regions-provinces-cities-municipalities-barangays/master/"
    "philippine_provinces_cities_municipalities_and_barangays_2019v2.json"
)

# ------------------------------------------------------------------ title casing

# Words `str.title()` gets wrong, and the form a Filipino address actually uses.
SPECIAL = {
    "I": "I", "II": "II", "III": "III", "IV": "IV", "V": "V",
    "VI": "VI", "VII": "VII", "VIII": "VIII", "IX": "IX", "X": "X",
    "XI": "XI", "XII": "XII", "XIII": "XIII", "XIV": "XIV",
    "POB": "Pob.", "POB.": "Pob.",
    "STO": "Sto.", "STO.": "Sto.", "STA": "Sta.", "STA.": "Sta.",
    "BGC": "BGC", "NCR": "NCR", "PSGC": "PSGC",
    "DE": "de", "DEL": "del", "LA": "la", "LAS": "Las", "LOS": "Los",
    "NG": "ng",
}

# A word like `MOLINO` is fine; `MOLINO-I` must keep the hyphen and case each part.
WORD_SPLIT = re.compile(r"([ \-/,()]+)")


def title_word(word: str) -> str:
    """Title-case ONE word, honouring the special table and internal apostrophes."""
    stripped = word.strip()
    if not stripped:
        return word
    key = stripped.upper()
    if key in SPECIAL:
        return SPECIAL[key]
    # Preserve a leading `(` or trailing `)` from the split.
    lead = "(" if word.startswith("(") else ""
    trail = ")" if word.endswith(")") else ""
    core = stripped.strip("()")
    if not core:
        return word
    if core.upper() in SPECIAL:
        return lead + SPECIAL[core.upper()] + trail
    # Dotted abbreviations: `POB.` handled above; anything else keeps its dot.
    if core.endswith(".") and len(core) <= 5:
        return lead + core[0].upper() + core[1:].lower() + trail
    # Mc/Mac/o' names are rare in PSGC; keep the simple rule and let the data say.
    if "'" in core:
        head, _, tail = core.partition("'")
        return lead + head.capitalize() + "'" + tail.lower() + trail
    return lead + core.capitalize() + trail


def title_case(name: str) -> str:
    """Title-case a PSGC ALL-CAPS name without mangling numerals or `Pob.`."""
    parts = WORD_SPLIT.split(name)
    out = []
    for part in parts:
        if not part:
            continue
        if WORD_SPLIT.fullmatch(part):
            # Separator run: normalise to a single space except real punctuation.
            out.append(part if part.strip() else part)
        else:
            out.append(title_word(part))
    joined = "".join(out)
    # Collapse the double spaces a stripped prefix can leave behind.
    return re.sub(r"\s{2,}", " ", joined).strip()


CITY_PREFIX = re.compile(r"^CITY OF\s+", re.IGNORECASE)

# The PSGC spells some LGUs `<NAME> CITY` where the natural, searchable name is
# just `<NAME>`. The spec names Bacoor and Antipolo explicitly; the rest of the
# `... CITY` forms (Quezon City, Pasay City, Caloocan City, Las Piñas, Parañaque,
# Muntinlupa, Marikina, Valenzuela, Malabon, Navotas, San Juan) are LEFT ALONE
# because that is what people actually type.
CITY_SUFFIX_STRIP = {
    "BACOOR CITY": "Bacoor",
    "CITY OF BACOOR": "Bacoor",
    "CITY OF ANTIPOLO": "Antipolo",
    "ANTIPOLO CITY": "Antipolo",
}


def city_name(raw: str) -> str:
    """`CITY OF MAKATI` -> `Makati`; `QUEZON CITY` -> `Quezon City`."""
    name = raw.strip()
    upper = re.sub(r"\s+", " ", name.upper())
    if upper in CITY_SUFFIX_STRIP:
        return CITY_SUFFIX_STRIP[upper]
    if CITY_PREFIX.match(name):
        name = CITY_PREFIX.sub("", name)
    return title_case(name)


PROVINCE_FIXUPS = {
    # The PSGC writes these with a space; the conventional forms are hyphenated.
    "Davao De Oro": "Davao de Oro",
    "Davao Del Norte": "Davao del Norte",
    "Davao Del Sur": "Davao del Sur",
    "Davao Del Oro": "Davao de Oro",
    "Davao Occidental": "Davao Occidental",
    "Davao Oriental": "Davao Oriental",
    "Ilocos Norte": "Ilocos Norte",
    "Ilocos Sur": "Ilocos Sur",
    "Lanao Del Norte": "Lanao del Norte",
    "Lanao Del Sur": "Lanao del Sur",
    "Misamis Occidental": "Misamis Occidental",
    "Misamis Oriental": "Misamis Oriental",
    "Negros Occidental": "Negros Occidental",
    "Negros Oriental": "Negros Oriental",
    "Samar Oriental": "Eastern Samar",
    "Samar Occidental": "Western Samar",
    "Sultan Kudarat": "Sultan Kudarat",
    "Zamboanga Del Norte": "Zamboanga del Norte",
    "Zamboanga Del Sur": "Zamboanga del Sur",
    "Zamboanga Sibugay": "Zamboanga Sibugay",
    "Metro Manila": "Metro Manila",
}


def province_name(raw: str) -> str:
    t = title_case(raw)
    return PROVINCE_FIXUPS.get(t, t)


# ------------------------------------------------------------------- seed parity

# Every triple SeedData.kt stores, transcribed here so the asset can be checked
# without parsing Kotlin. `test_ph_addresses_asset.py` asserts these against the
# real file, so a seed change that is not mirrored here fails the build.
SEED_TRIPLES = [
    ("Metro Manila", "Makati", "Poblacion"),
    ("Metro Manila", "Quezon City", "Batasan"),
    ("Metro Manila", "Mandaluyong", "Plainview"),
    ("Metro Manila", "Pasig", "San Antonio"),
    ("Rizal", "Antipolo", "San Roque"),
    ("Cavite", "Bacoor", "Molino"),
]

# PSGC spelling -> the seed's spelling, kept as an ADDITIONAL entry.
ALIASES = {
    # Quezon City: the official barangay is `Batasan Hills`; the seed and the
    # client's own workbook say `Batasan`.
    ("Metro Manila", "Quezon City", "BATASAN HILLS"): "Batasan",
    # Antipolo: PSGC carries the `(POB.)` qualifier.
    ("Rizal", "Antipolo", "SAN ROQUE (POB.)"): "San Roque",
    # Bacoor: `Molino` alone is how the seed and the address line spell it.
    ("Cavite", "Bacoor", "MOLINO I"): "Molino",
    # Taguig: everyone says BGC.
    ("Metro Manila", "Taguig", "FORT BONIFACIO"): "BGC",
}


def add(tree: dict, province: str, city: str, barangay: str) -> None:
    tree.setdefault(province, {}).setdefault(city, [])
    if barangay not in tree[province][city]:
        tree[province][city].append(barangay)


def build() -> dict:
    raw = json.loads(SOURCE.read_text(encoding="utf-8"))
    tree: dict[str, dict[str, list[str]]] = {}

    # ---- 1. NCR: fold the five pseudo-provinces into one `Metro Manila` --------
    ncr = raw["NCR"]["province_list"]
    metro: dict[str, list[str]] = {}
    manila_districts = ncr["NATIONAL CAPITAL REGION - MANILA"]["municipality_list"]
    # The 14 Manila districts become BARANGAYS of one `Manila` city.
    manila_barangays: list[str] = []
    for district, payload in manila_districts.items():
        manila_barangays.append(title_case(district))
        for b in payload["barangay_list"]:
            manila_barangays.append(title_case(b))
    metro["Manila"] = sorted(set(manila_barangays), key=str.lower)

    for pseudo, payload in ncr.items():
        if pseudo == "NATIONAL CAPITAL REGION - MANILA":
            continue
        for raw_city, city_payload in payload["municipality_list"].items():
            city = city_name(raw_city)
            if city == "Manila":
                continue
            metro[city] = sorted(
                {title_case(b) for b in city_payload["barangay_list"]},
                key=str.lower,
            )
    tree["Metro Manila"] = metro

    # ---- 2. every other region, province -> city -> barangays ------------------
    for region_key, region in raw.items():
        if region_key == "NCR":
            continue
        for raw_province, prov_payload in region["province_list"].items():
            province = province_name(raw_province)
            for raw_city, city_payload in prov_payload["municipality_list"].items():
                city = city_name(raw_city)
                for b in city_payload["barangay_list"]:
                    add(tree, province, city, title_case(b))

    # ---- 3. aliases so the seed's spellings resolve ---------------------------
    for (province, city, psgc_barangay), alias in ALIASES.items():
        target = tree.get(province, {}).get(city, [])
        # The PSGC name is ALL CAPS; the asset stores it title-cased. Match either
        # form so the alias lands whichever spelling the source used.
        wanted = title_case(psgc_barangay)
        official = next(
            (b for b in target if b.upper() == psgc_barangay.upper()),
            None,
        ) or next((b for b in target if b == wanted), None)
        if official is not None:
            add(tree, province, city, alias)
        else:
            print(
                f"WARN alias source missing: {province}/{city}/{psgc_barangay} "
                f"(looked for {wanted!r} in {len(target)} rows)",
                file=sys.stderr,
            )

    # ---- 4. guarantee seed parity no matter how the source drifts -------------
    for province, city, barangay in SEED_TRIPLES:
        add(tree, province, city, barangay)

    # ---- 5. order everything deterministically -------------------------------
    ordered: dict[str, dict[str, list[str]]] = {}
    # Metro Manila first (the client's own ordering), then the rest A-Z.
    ordered["Metro Manila"] = {
        city: sorted(metro[city], key=str.lower)
        for city in sorted(metro, key=str.lower)
    }
    for province in sorted(tree, key=str.lower):
        if province == "Metro Manila":
            continue
        ordered[province] = {
            city: sorted(tree[province][city], key=str.lower)
            for city in sorted(tree[province], key=str.lower)
        }
    return ordered


def main() -> int:
    if not SOURCE.exists():
        print(
            f"source not found: {SOURCE}\n"
            f"download it first:\n  curl -sL -o \"{SOURCE}\" \"{SOURCE_URL}\"",
            file=sys.stderr,
        )
        return 1

    tree = build()
    provinces = len(tree)
    cities = sum(len(v) for v in tree.values())
    barangays = sum(len(b) for v in tree.values() for b in v.values())

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    # One province per line keeps a diff of two builds readable.
    with OUTPUT.open("w", encoding="utf-8", newline="\n") as fh:
        fh.write("{\n")
        names = list(tree)
        for i, province in enumerate(names):
            fh.write("  " + json.dumps(province, ensure_ascii=False) + ": {\n")
            city_names = list(tree[province])
            for j, city in enumerate(city_names):
                row = json.dumps(tree[province][city], ensure_ascii=False)
                sep = "," if j < len(city_names) - 1 else ""
                fh.write(
                    "    " + json.dumps(city, ensure_ascii=False) + ": " + row + sep + "\n"
                )
            fh.write("  }" + ("," if i < len(names) - 1 else "") + "\n")
        fh.write("}\n")

    size = OUTPUT.stat().st_size
    print(f"wrote {OUTPUT}")
    print(f"  provinces : {provinces}")
    print(f"  cities    : {cities}")
    print(f"  barangays : {barangays}")
    print(f"  size      : {size / 1024:.0f} KB")

    # Fail loudly rather than shipping an asset the tests will reject anyway.
    checks = [
        (provinces >= 80, f"provinces {provinces} < 80"),
        (len(tree["Metro Manila"]) == 17, f"NCR LGUs {len(tree['Metro Manila'])} != 17"),
        (
            len(tree["Metro Manila"]["Taguig"]) >= 25,
            f"Taguig barangays {len(tree['Metro Manila']['Taguig'])} < 25",
        ),
        (
            len(tree.get("Pangasinan", {}).get("Umingan", [])) >= 50,
            "Umingan barangays < 50",
        ),
    ]
    bad = [msg for ok, msg in checks if not ok]
    for msg in bad:
        print("CHECK FAILED: " + msg, file=sys.stderr)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
