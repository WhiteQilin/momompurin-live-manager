#!/usr/bin/env python3
"""Convert the Phase 5.6 Sticker-Sheet SVG batch into named Android drawables.

Input:  tools/svg_batch/<node-id>.svg   (exported from the Sticker Sheet 'Icons'
        frame; node ids are stable Figma identities).
Output: app/src/main/res/drawable/ic_<name>.xml, plus a printed manifest table.

Naming comes from ASSET_NAMES below - the node id -> design-name mapping, so the
drawable name says what the icon MEANS (ic_cart) rather than which library it
came from (ic_commerce_shopping_cart_v3).
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BATCH = os.path.join(HERE, "svg_batch")
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")

# node id -> (design name, what it is on the Sticker Sheet)
ASSET_NAMES = {
    # ---- Category / Mobile Header & More Menu ------------------------------
    "2890-3761": "cart",                      # Commerce / shopping-cart
    "2890-3762": "search",                    # Iconex/Broken/Search (already existed; re-exported)
    "2890-3763": "bell",                      # Iconex/Light/Bell
    "2890-3764": "box",                       # Iconex/Light/Box 1
    "2890-3766": "history",                   # Arrow / history
    "2890-3768": "chart_square",              # chart-square
    "2890-3769": "settings",                  # setting-2
    "2890-3770": "box_tick",                  # box-tick
    "2890-3771": "shop",                      # vuesax/linear/shop
    # ---- Category / Live Selling & POS ------------------------------------
    "2890-3825": "broadcast",                 # Media / broadcast
    "2890-3826": "stop_circle",               # stop-circle
    "2890-3827": "play_circle",               # play-circle
    "2890-3828": "trash_full",                # System / trash-full
    "2890-3829": "check",                     # check
    "2890-3830": "user_add",                  # user-circle-add
    "2890-3831": "add",                       # add (+)
    "2890-3832": "minus",                     # minus
    "2890-3833": "shopping_bag",              # Commerce / shopping-bag-alt
    "2890-3834": "user_check",                # User / user-check-corner
    # ---- Category / Inventory & Product Management --------------------------
    "2890-3893": "photo_plus",                # photo.badge.plus (image slot)
    "2890-3896": "barcode",                   # barcode
    "2890-3897": "tag",                       # Commerce / tag
    "2890-3898": "layers",                    # Iconex/Light/Layers
    "2890-3899": "edit",                      # edit
    "2890-3900": "export",                    # export
    "2890-3901": "package_add",               # File / package-plus-corner
    "2890-3905": "box_taped",                 # File / box-taped
    # ---- Category / Orders & Payments --------------------------------------
    "2890-3966": "receipt",                   # Iconex/Light/Receipt
    "2890-3967": "receipt_search",            # receipt-search
    "2890-3968": "wallet_empty",              # empty-wallet
    "2890-3970": "dollar_square",             # dollar-square (3969 is an empty dup)
    "2890-3971": "timer",                     # timer
    "2890-3972": "timer_pause",               # timer-pause
    "2890-3973": "circle_check",              # Shape / circle-check
    "2890-3974": "circle_close",              # Shape / circle-close
    "2890-3975": "hexagon_warning",           # Shape / hexagon-warning
    "2890-3976": "rotate_left",               # rotate-left
    "2890-3984": "share",                     # Communication / share-alt
    "2890-3985": "copy",                      # Edit / copy-alt-2
    # ---- Category / Packing & Delivery -------------------------------------
    "2890-4009": "map_pin",                   # Navigation / map-pin
    "2890-4010": "truck_fast",                # Travel / truck-fast
    "2890-4012": "task_square",               # task-square
    "2890-4013": "circle_check_fill",         # Shape / circle-check (filled variant)
    # ---- Category / Customer Management ------------------------------------
    "2890-4040": "call",                      # Iconex/Light/Call
    "2890-4041": "contacts",                  # Communication / contacts
    "2890-4042": "user_square",               # User / square-user
    "2890-4043": "users",                     # User / users
    # ---- Category / Notification & System Feedback ---------------------------
    "2890-4084": "bell_light",                # Iconex/Light/Bell (light variant)
    "2890-4085": "circle_warning",            # Shape / circle-warning
    "2890-4086": "triangle_warning",          # Shape / triangle-warning
    "2890-4087": "square_info",               # Shape / square-info
    "2890-4089": "icloud_x",                  # xmark.icloud (offline)
    "2890-4094": "lock",                      # Iconex/Light/Lock
    # ---- Category / Actions & Controls --------------------------------------
    "2890-4146": "filter",                    # System / filter
    "2890-4147": "swap",                      # Iconex/Light/Swap 3
    "2890-4148": "arrow_swap",                # arrow-swap
    "2890-4149": "check_small",               # Shape / check
    "2890-4150": "close",                     # Shape / close
    "2890-4151": "chevron_left",              # Arrow / chevron-left-large
    "2890-4152": "chevron_right",             # Arrow / chevron-right-large
    "2890-4153": "more_dots",                 # more (…)
    "2890-4154": "eye",                       # eye (show password)
    "2890-4155": "eye_slash",                 # eye-slash (hide password)
    "2890-4156": "circle_help",               # Shape / circle-help
    "2890-4157": "download",                  # Iconex/Light/Download 2
    "2890-4158": "logout",                    # Iconex/Light/Logout
    "2890-4181": "shopping_cart",             # Commerce / shopping-cart (standalone)
    # ---- Navigation / tab icons (filled + highlighted + default) ------------
    "2890-3673": "clipboard_fill",            # Edit / clipboard-list (filled pill)
    "2890-3674": "burger_fill",               # Iconex/Light/Burger (filled)
    "2890-3675": "live_fill",                 # Live Sell (filled)
    "2890-3678": "box_taped_fill",            # File / box-taped (filled)
    "2890-3679": "home_fill",                 # Iconex/Filled/Home
    "2890-3687": "live_outline",              # Live Sell (default)
    "2890-3688": "clipboard_list",            # Edit / clipboard-list (default)
    # ---- Mascots (raster, exported separately; listed here for the manifest) --
    # 2890-3694..3697 -> res/drawable/mascot_*.png (see export step)
}


def main():
    # convert_svg.py exposes convert(src, dst); import it directly. Its CLI takes
    # DIRECTORIES, so calling it per-file would os.makedirs() a directory named
    # like the output file and then fail to write it.
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "convert_svg", os.path.join(HERE, "convert_svg.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)

    manifest, failures = [], []
    for node_id, name in sorted(ASSET_NAMES.items()):
        src = os.path.join(BATCH, node_id + ".svg")
        if not os.path.exists(src):
            print(f"  MISSING {node_id} ({name})")
            failures.append((node_id, name, "missing"))
            continue
        dst = os.path.join(OUT, f"ic_{name}.xml")
        try:
            n, unsupported = mod.convert(src, dst)
            flag = f"  UNSUPPORTED {unsupported}" if unsupported else ""
            manifest.append((node_id, name, n, flag))
            print(f"  {node_id} -> ic_{name}.xml ({n} path{'s' if n != 1 else ''}){flag}")
        except SystemExit as e:
            print(f"  FAILED {node_id} ({name}): {e}")
            failures.append((node_id, name, str(e)))
    print(f"\n{len(manifest)} icons written to {OUT}")
    if failures:
        print(f"{len(failures)} failed:")
        for node_id, name, why in failures:
            print(f"  {node_id} {name}: {why}")


if __name__ == "__main__":
    main()
