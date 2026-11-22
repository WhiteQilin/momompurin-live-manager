#!/usr/bin/env python3
"""
Convert Figma-exported 24x24 SVGs into Android VectorDrawables.

Rules this applies:
  * viewBox maps to viewportWidth/Height so the glyph scales as the design intends.
  * Every fill/stroke colour becomes #FF000000 and the root carries
    android:tint="?attr/colorControlNormal", so Compose can recolour the icon
    through the token layer instead of baking a Figma hex into a drawable.
  * Elements are only <path>, which is all Android's vector format supports -
    the MCP export is already path-only for these icons.
  * Unsupported constructs (masks, filters, gradients) are reported, not silently
    dropped: a silently broken icon is worse than a refused one.

Usage: python convert_svg.py <input-dir> <output-drawable-dir>
"""
import re
import sys
import os
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)


def parse_dim(value):
    """'24' or '24px' -> 24.0"""
    return float(re.sub(r"[^0-9.]", "", value or "24") or 24)


def convert(src, dst, tinted=True):
    raw = open(src, encoding="utf-8").read()
    # Figma exports often use preserveAspectRatio="none" plus a vector-effect attr;
    # both are irrelevant once we emit explicit paths.
    root = ET.fromstring(raw)
    vb = root.get("viewBox", "0 0 24 24").split()
    vw, vh = float(vb[2]), float(vb[3])

    paths = []
    unsupported = []
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag in ("mask", "filter", "linearGradient", "radialGradient", "image", "text", "use"):
            unsupported.append(tag)
        if tag != "path":
            continue
        d = el.get("d")
        if not d:
            continue
        fill = el.get("fill")
        stroke = el.get("stroke")
        stroke_width = el.get("stroke-width")
        fill_rule = el.get("fill-rule") or el.get("fillRule")

        attrs = [f'android:pathData="{d}"']
        # A path with an explicit stroke and no fill is a stroke glyph.
        if stroke and not fill:
            attrs.append('android:strokeColor="#FF000000"')
            attrs.append(f'android:strokeWidth="{stroke_width or 1.5}"')
            cap = el.get("stroke-linecap") or "round"
            join = el.get("stroke-linejoin") or "round"
            attrs.append(f'android:strokeLineCap="{cap}"')
            attrs.append(f'android:strokeLineJoin="{join}"')
            attrs.append('android:fillColor="@android:color/transparent"')
        else:
            attrs.append('android:fillColor="#FF000000"')
            if stroke and stroke_width:
                attrs.append('android:strokeColor="#FF000000"')
                attrs.append(f'android:strokeWidth="{stroke_width}"')
            if fill_rule == "evenodd":
                attrs.append('android:fillType="evenOdd"')
        paths.append("    <path " + "\n        ".join(attrs) + " />")

    if not paths:
        raise SystemExit(f"no <path> found in {src}")

    tint_attr = '    android:tint="?attr/colorControlNormal"\n' if tinted else ""
    body = "\n".join(paths)
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<!-- Converted from the Figma Sticker Sheet. Tint at the call site through\n'
        f'     MomoColors so no design hex is baked into the drawable. -->\n'
        f'<vector xmlns:android="{ANDROID_NS}"\n'
        f'    android:width="24dp"\n'
        f'    android:height="24dp"\n'
        f'    android:viewportWidth="{vw:g}"\n'
        f'    android:viewportHeight="{vh:g}"\n'
        f'{tint_attr}>\n'
        f'{body}\n'
        f'</vector>\n'
    )
    open(dst, "w", encoding="utf-8").write(xml)
    return len(paths), sorted(set(unsupported))


def main():
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    total = 0
    for name in sorted(os.listdir(src_dir)):
        if not name.endswith(".svg"):
            continue
        res = "ic_" + re.sub(r"[^a-z0-9]+", "_", os.path.splitext(name)[0].lower()).strip("_")
        dst = os.path.join(out_dir, res + ".xml")
        n, unsupported = convert(os.path.join(src_dir, name), dst)
        flag = f"  ⚠ unsupported: {unsupported}" if unsupported else ""
        print(f"  {name:24s} -> {res}.xml  ({n} path{'s' if n != 1 else ''}){flag}")
        total += 1
    print(f"\nconverted {total} icons into {out_dir}")


if __name__ == "__main__":
    main()
