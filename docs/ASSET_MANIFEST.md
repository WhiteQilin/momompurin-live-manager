# Sticker Sheet Asset Manifest — `res/drawable/` Library

**Source of truth:** Figma `Momompurin Sticker Sheet` (page `Sticker Sheet`, root frame
`2890:3034`, v5) and the standing mascot from `A1.0 - Sign In · Default` (`3422:481`).
Every icon in the Sticker Sheet's `Icons` frame (`3353:2257`, ~70 unique glyphs across
ten categories) plus the four raster mascot poses are now in the repository —
**including icons no screen references yet**, so the whole asset library is on-call.

Conversion rules (applied by `tools/convert_sticker_batch.py` over Figma SVG exports in
`tools/svg_batch/`, using `tools/convert_svg.py`):
- every fill/stroke becomes `#FF000000`; **no design hex is baked into a drawable** —
  screens tint through `MomoColors` at the call site;
- the converter's `?attr/colorControlNormal` tint attribute is stripped after export
  (AAPT fails to link it — the standing trap);
- exported node geometry is used as-is; no bounding-box rectangles were present in
  this batch (each export was the icon node itself, not an enclosing frame).

## Vector icons (`res/drawable/ic_*.xml`)

### Category / Mobile Header & More Menu
| Node | Drawable | Design name |
|---|---|---|
| `2890:3761` | `ic_cart.xml` | Commerce / shopping-cart |
| `2890:3762` | `ic_search.xml` | Iconex/Broken/Search (replaces the earlier export) |
| `2890:3763` | `ic_bell.xml` | Iconex/Light/Bell (re-exported) |
| `2890:3764` | `ic_box.xml` | Iconex/Light/Box 1 |
| `2890:3766` | `ic_history.xml` | Arrow / history |
| `2890:3768` | `ic_chart_square.xml` | chart-square |
| `2890:3769` | `ic_settings.xml` | setting-2 |
| `2890:3770` | `ic_box_tick.xml` | box-tick |
| `2890:3771` | `ic_shop.xml` | vuesax/linear/shop (replaces the earlier export with the bounding-box path) |

### Category / Live Selling & POS
| Node | Drawable | Design name |
|---|---|---|
| `2890:3825` | `ic_broadcast.xml` | Media / broadcast (re-exported) |
| `2890:3826` | `ic_stop_circle.xml` | stop-circle |
| `2890:3827` | `ic_play_circle.xml` | play-circle |
| `2890:3828` | `ic_trash_full.xml` | System / trash-full |
| `2890:3829` | `ic_check.xml` | check |
| `2890:3830` | `ic_user_add.xml` | user-circle-add |
| `2890:3831` | `ic_add.xml` | add (+) |
| `2890-3832` | `ic_minus.xml` | minus |
| `2890:3833` | `ic_shopping_bag.xml` | Commerce / shopping-bag-alt |
| `2890:3834` | `ic_user_check.xml` | User / user-check-corner |

### Category / Inventory & Product Management
| Node | Drawable | Design name |
|---|---|---|
| `2890:3893` | `ic_photo_plus.xml` | photo.badge.plus (image slot + badge) |
| `2890:3896` | `ic_barcode.xml` | barcode |
| `2890:3897` | `ic_tag.xml` | Commerce / tag |
| `2890:3898` | `ic_layers.xml` | Iconex/Light/Layers |
| `2890:3899` | `ic_edit.xml` | edit |
| `2890:3900` | `ic_export.xml` | export |
| `2890:3901` | `ic_package_add.xml` | File / package-plus-corner |
| `2890:3905` | `ic_box_taped.xml` | File / box-taped (re-exported) |

### Category / Orders & Payments
| Node | Drawable | Design name |
|---|---|---|
| `2890:3966` | `ic_receipt.xml` | Iconex/Light/Receipt (re-exported) |
| `2890:3967` | `ic_receipt_search.xml` | receipt-search |
| `2890:3968` | `ic_wallet_empty.xml` | empty-wallet |
| `2890:3970` | `ic_dollar_square.xml` | dollar-square (`2890:3969` is an empty duplicate instance) |
| `2890:3971` | `ic_timer.xml` | timer |
| `2890:3972` | `ic_timer_pause.xml` | timer-pause |
| `2890:3973` | `ic_circle_check.xml` | Shape / circle-check |
| `2890:3974` | `ic_circle_close.xml` | Shape / circle-close |
| `2890:3975` | `ic_hexagon_warning.xml` | Shape / hexagon-warning |
| `2890:3976` | `ic_rotate_left.xml` | rotate-left |
| `2890:3984` | `ic_share.xml` | Communication / share-alt |
| `2890:3985` | `ic_copy.xml` | Edit / copy-alt-2 |

### Category / Packing & Delivery
| Node | Drawable | Design name |
|---|---|---|
| `2890:4009` | `ic_map_pin.xml` | Navigation / map-pin |
| `2890:4010` | `ic_truck_fast.xml` | Travel / truck-fast |
| `2890:4012` | `ic_task_square.xml` | task-square |
| `2890:4013` | — | export refused (no visible layers; the variant of circle-check used is `2890:3973`) |

### Category / Customer Management
| Node | Drawable | Design name |
|---|---|---|
| `2890:4040` | `ic_call.xml` | Iconex/Light/Call |
| `2890:4041` | `ic_contacts.xml` | Communication / contacts |
| `2890:4042` | `ic_user_square.xml` | User / square-user |
| `2890:4043` | `ic_users.xml` | User / users (re-exported) |

### Category / Notification & System Feedback
| Node | Drawable | Design name |
|---|---|---|
| `2890:4084` | `ic_bell_light.xml` | Iconex/Light/Bell (notification's own instance) |
| `2890:4085` | `ic_circle_warning.xml` | Shape / circle-warning (re-exported) |
| `2890:4086` | `ic_triangle_warning.xml` | Shape / triangle-warning |
| `2890:4087` | `ic_square_info.xml` | Shape / square-info |
| `2890:4089` | `ic_icloud_x.xml` | xmark.icloud (offline / no cloud) |
| `2890:4094` | `ic_lock.xml` | Iconex/Light/Lock |

### Category / Actions & Controls
| Node | Drawable | Design name |
|---|---|---|
| `2890:4146` | `ic_filter.xml` | System / filter |
| `2890:4147` | `ic_swap.xml` | Iconex/Light/Swap 3 |
| `2890:4148` | `ic_arrow_swap.xml` | arrow-swap |
| `2890:4149` | `ic_check_small.xml` | Shape / check |
| `2890:4150` | `ic_close.xml` | Shape / close |
| `2890:4151` | `ic_chevron_left.xml` | Arrow / chevron-left-large |
| `2890:4152` | `ic_chevron_right.xml` | Arrow / chevron-right-large (replaces the earlier export) |
| `2890:4153` | `ic_more_dots.xml` | more (…) |
| `2890:4154` | `ic_eye.xml` | eye (show password, A1.6) |
| `2890:4155` | `ic_eye_slash.xml` | eye-slash (hide password, A1.6) |
| `2890:4156` | `ic_circle_help.xml` | Shape / circle-help |
| `2890:4157` | `ic_download.xml` | Iconex/Light/Download 2 |
| `2890:4158` | `ic_logout.xml` | Iconex/Light/Logout (the sign-out affordance) |
| `2890:4181` | `ic_shopping_cart.xml` | Commerce / shopping-cart (standalone copy) |

### Navigation / tab icons (Bottom Bar `3419:421` variants)
| Node | Drawable | Design name |
|---|---|---|
| `2890:3673` | `ic_clipboard_fill.xml` | Edit / clipboard-list — filled pill state (Orders) |
| `2890:3674` | `ic_burger_fill.xml` | Iconex/Light/Burger — filled (More) |
| `2890:3675` | `ic_live_fill.xml` | Live Sell — filled (Live, active) |
| `2890:3678` | `ic_box_taped_fill.xml` | File / box-taped — filled (Stock, active) |
| `2890:3679` | `ic_home_fill.xml` | Iconex/Filled/Home (Home, active) |
| `2890:3687` | `ic_live_outline.xml` | Live Sell — default (Live, inactive) |
| `2890:3688` | `ic_clipboard_list.xml` | Edit / clipboard-list — default (Orders, inactive) |

## Raster mascots (`res/drawable-nodpi/mascot_*.png`)

| Node | File | Design name |
|---|---|---|
| `3422:481` | `mascot_signin.png` (288×288 @4x) | **A1.0's standing mascot badge** — the sign-in hero |
| `2890:3694` | `mascot_pudding_sit.png` | Sticker Sheet mascot pose 1 |
| `2890:3695` | `mascot_pudding_stand.png` | Sticker Sheet mascot pose 2 |
| `2890:3696` | `mascot_pudding_wave.png` | Sticker Sheet mascot pose 3 |
| `2890:3697` | `mascot_pudding_cheer.png` | Sticker Sheet mascot pose 4 |
| — | `mascot.png` (pre-existing, 160×160) | The dashboard header badge (kept; already wired) |

## Legend / non-asset groups (not exported — by design)

The Sticker Sheet's other groups are **specifications, not assets**: `Header` (the
`MOMOMPURIN` word-mark artboard), `Status Badges` (the 3253:7941 component set —
rendered in Compose by `StatusBadge.kt`), `Color Palette` (transcribed into
`MomoColors.kt`), `Typography Cards` (→ `MomoTypography.kt`), `Spacing & Radius`
(→ `MomoDimens.kt`), `Buttons & States Card` (→ `MomoButton` / `MiniAction`), and
`Form Elements` (→ `MomoField` / `MomoPicker`).

## Legacy drawables kept

`ic_bell_circle`, `ic_burger`, `ic_circle_warning` (superseded by the re-export but
still referenced), `ic_dollar_square` (replaced), `ic_empty_wallet`, `ic_home`,
`ic_home_filled` (unused, retained for the filled-tab work), `ic_launcher_foreground`,
`ic_live`, `ic_more`, `ic_orders`, `ic_receipt` (replaced), `ic_search`,
`ic_shopping_bag`, `ic_timer`, `ic_timer_pause`, `ic_truck_fast`, `ic_users` — every
file remains compilable and tint-clean.
