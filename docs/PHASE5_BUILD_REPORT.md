# Phase 5 Build Report — Final 8 Routes, Tracking Port, and the Stage-A Visual Pass

**Date:** 15 November 2026
**Build:** `app-debug.apk`, version **`1.0-phase5`**, `versionCode = 5`
**Verified on:** `Medium_Phone_API_36.1`, 1080×2400 @ 420dpi
**Tests:** **160 run, 0 failed** · **0 literal colours** outside `ui/theme` · **0 phase references** in UI strings
**`DeferredRouteScreen` call sites:** **0** (all 23 of 23 routes live; the composable definition remains because the A-03 c8 scan test asserts its parameter-free signature)

---

## 1. Baseline and toolchain

Phase 4.6's baseline was confirmed first: `assembleDebug` + `testDebugUnitTest`
BUILD SUCCESSFUL, **150 tests, 0 failed**, no drift. All Phase 5 work stacked on
that tree. supabase-kt stays at 3.1.4, AGP 8.9.2, compileSdk 36, `--no-daemon`
builds through `C:\tools\gradle\gradle-8.13`.

---

## 2. Stage A — the confirmed visual defects, fixed and re-verified on device

| # | Defect | Fix | On-device evidence |
|---|---|---|---|
| A1 | **Global line-spacing bug** — `Body/Default` (15sp) and `Caption/Small` (12sp) carried `lineHeight = 30sp` (a Figma paragraph-spacing column copied as line height), so every wrapped sentence opened a 30sp gap | All roles re-pointed to proportional ratios: Display 38→**44**, Title 18→**24**, Body 15→**21**, Caption 12→**17**, Button 14→**18**, Data 16→**22** (Heading keeps 24/30) | MoreScreen's restore-procedure sentence wraps with a normal 1.4× rhythm (`pairs/more.png`) |
| A2 | **`ic_stock.xml` rendered a solid black hexagon** — the converter emitted the cube's outer silhouette as a second `fillColor` path | The silhouette path dropped; the wireframe (top/front/side edges + interior seam) is the icon. Full `ic_*.xml` audit run: 26 icons scanned for solid fills, bounding-box paths and `colorControlNormal`; the only other hit (`ic_home_filled`) is unused, and `ic_shop.xml`'s stray export-frame rectangle was removed in A3 | Bottom bar shows the isometric cube outline on `home.png`, `more-bottom.png`, `catalog.png` |
| A3 | **Dashboard `Regular POS` collision** — `ic_shop.xml` carried the SVG export frame's bounding box (`M0.5 0.5H23.5V23.5H0.5V0.5Z`) as a visible path, and the button had no guaranteed icon/label gutter | Bounding box removed from the drawable; `HeroButton` gained an explicit 8dp spacer between icon and label plus an `outlined` parameter — A2.0 draws the 1dp `#2B140E` border on "Regular POS" only | `home.png`: clean icon, clean "R", dark border present |
| A4 | **MoreScreen backup buttons were tall pills** (`ActionPill`), and the Data checks section was an empty header card with findings floating outside as unbordered strips | `ActionPill` deleted; both buttons are `MomoButton` masters (radius 12, primary gold + secondary white/bordered, 44dp compact, 12dp gap). Data checks rebuilt as ONE bordered `MomoCard`: header + amber count tag + divider-separated findings, each with an aligned secondary `Fix` button. Top scroll padding made explicit (16dp under the bar) | `more.png` + `more-bottom.png` |
| A5 | **Below-the-fold captures** across the built routes | `home` bottom fold (6 queue rows + alert dot), `more` bottom fold (the rebuilt card), and every Phase 5 screen captured; 22/23 pairs stitched (see §5) | `docs/visual_qa/` |

The typography change touches every screen, so the full test suite and the
literal-colour audit were re-run after Stage A and again at the close: both clean.

---

## 3. Stage B1 — the last algorithm port: `TrackingValidator`

`algorithms/tracking.py` → `domain/shipping/TrackingValidator.kt`, with the
**11 FIXTURES verbatim** as both a `FIXTURES` list and a unit test
(`TrackingValidatorTest`, 10 tests). Contract notes:

- Patterns are the Python table's: `^(?:JT)?\d{10,13}$` (J&T),
  `^(?:LMB|LM)[-A-Z0-9]{7,14}$` (Lalamove), `^GRAB(?:-PH)?[-A-Z0-9]{5,13}$`
  (Grab), `^SPH\d{10,18}$|^\d{10,20}$` (TikTok).
- Matching trims, upper-cases, and **strips spaces** — one deliberate extension
  over the Python, because the seeded waybill is `JT 2612 3456 7890` WITH spaces
  (I-01 c5) and the strict pattern would refuse the number the walkthrough must
  dispatch with. Stripping is matching hygiene only: what the user typed is what
  is stored and displayed, verbatim. This is recorded on the class and in the
  test `seeded_number_validates_with_its_spaces`.
- **Customer Pickup has no pattern and never will** — the caller skips
  validation; the test `customer_pickup_has_no_pattern_the_caller_skips_it`
  enforces the structural rule.

## 4. Stage B2–B8 — the eight Phase 5 routes

Built from the coded frames on page `3395:3205` (never `3395:3206`). Route 9
first, as the handoff orders.

| # | Route | Screen file | Built from | Verified behaviour (criteria) |
|---|---|---|---|---|
| 9 | `/orders/{id}` | `OrderDetailScreen.kt` | **D1.0** `3558:1843` | Six separate labelled tracks — CLAIM / INVOICE / PAYMENT / PACKING / SHIPPING / STOCK — each with its time line and paired badge (c1, c8, c10). Per-track dropdowns offer ONLY that field's vocabulary (c3): packing offers TO PREP / PACKED / READY FOR DISPATCH, never IN TRANSIT. Changing one field never moves another (c4). Every change logs old → new + time + actor (c5); the timeline renders in the Status-history card (c6). `PAID WITH BALANCE` never renders as `PAID` (c9/D-02 c9). Invalid transitions refuse AT SELECTION beside the field: "Order not packed yet." (D-03 c3), the balance-outstanding refusal, and the cancellation rules (D-05 c1–c3). The D-01 c9 related-update prompt ("Also update packing status?") offers and applies only on confirm. D-04 reservation card (Reserved / Available / Shipped + "Reserved by ORD-1042"), I-06 c2 review flag ("! Review flag: unpaid history", owner-only, never blocks), B-07..B-09 copy-address, and the J-08 bottom action bar into Invoice / Pack / Ship / Export / Shared view |
| 8 | `/orders` (+ filtered) | `OrdersScreen.kt` | N1.0–N2.2, E5.x, N-07, N-09, E-04 | Side-by-side payment + fulfillment pills kept. **Three review filter chips** (Due for Cancellation Review / Partial Payment Deadline Passed / Paid with Balance Waiting for Deadline). **E-05 cancel** guarded by `OrderRules.cancellationBlock`, confirmed through `MomoConfirmDialog`, wired to `MomoStore.confirmCancelAndRestock` — a second cancel is refused and stock releases exactly once. **Order History sub-tab** lists cancelled orders with **Restore** (E-04 c8: back to Waiting for Payment; restore reserves nothing, so restock-exactly-once holds in both directions) |
| 11 | `/orders/{id}/invoice` | `InvoiceScreen.kt` | G4.0–G5.4 | Pulls the exact ₱880 set (MCKP 160 + SCWH 600 + freebie 0 = 760 + 120). Payment card: Total 880 / Paid 600 / Balance 280 + the "PAID WITH BALANCE" word beside the badge (G-05 c2). **The deliberate trap**: the review step shows +₱50 handling and −₱30 repeat-buyer discount landing on **₱850.00** (G-04 c2's "Total drops from ₱880.00 to ₱850.00") — the ₱50 is already inside the 880, so only the −₱30 moves the total. Confirm & issue persists and logs; unverified/cancelled orders cannot issue (G-04 c9) |
| 12 | `/orders/{id}/export` | `InvoiceExportScreen.kt` | G6/G7/G9 | Phone-width read-only preview with "Momompurin Online Shop", "Pia Santos — PAID WITH BALANCE", the full scrollable item list (G-09). Image (.png) / PDF (.pdf) chosen from one action (G-06 c8); files rendered into the configured FileProvider root and shared via `content://` (never `file://`). Share sheet opened on device (G-07 c1); dismissal records nothing and keeps the invoice (c4); a completed share records `"Invoice sent to Pia Santos via Messenger · <time>"` with the 3-second success pill (c3/c6); the rendered file stays reusable (c7). Cost/net/supplier absent everywhere |
| 13 | `/orders/{id}/pack` | `PackingChecklistScreen.kt` | G8.0–G8.3, E6.x | Checklist "0 of 3 packed" → "3 of 3 packed" → PACKED (c2/c5/c6). **E-06 exclusions with cause**: Kept orders show "Kept order — release the Keep flag to pack.", shipped/packed show their cause; after packing the screen states "Cancellation is no longer allowed after packing." (c4). Completion blocked while unpacked: "3 items still unpacked." → "1 item still unpacked." (c4). Per-line packer notes ("e.g. fragile") that never reach the customer invoice (c3). Checked state survives recomposition (c7); completion time recorded (c8) |
| 14 | `/orders/{id}/shipping` | `ShippingRecordScreen.kt` | I1.0–I4.2 | Route selector: J&T Express / TikTok Shop / Lalamove-Grab / Pickup (I-03 c1); Pickup needs no courier or tracking fields (c4/c6). Twelve fields pre-filled from Pia Santos's profile (I-02 c2); "Copy all fields" copies the portal block (c6). **Entry-time tracking validation**: `JT123` refused with the format message before handover; `JT 2612 3456 7890` accepted and **displayed verbatim with its spaces** (I-01 c5). Required-field refusal keeps entries visible (c2/c3). Confirm dispatch sets **FOR PICKUP** + dispatch date (I-02 c7); exception note recorded without changing status (I-01 c7); CSV export in portal column order with a dated filename (I-04). **I-05 has no screen, by design** |
| 22 | `/shared/invoice/{id}` | `SharedViewsScreen.kt` | C1.0 | Exact MP-1042 set: MCKP 160 / SCWH 600 / Freebie 0, subtotal 760, "Shipping · J&T Express" 120, total 880, PAID WITH BALANCE pill, "Paid ₱600.00 · Balance due ₱280.00". **Zero edit/add/delete controls** (c2), **cost/net/supplier entirely absent** (c3), no owner bottom bar. The C-01 c6 fallback ("This invoice is no longer available.") renders for an unknown id. Tapping the shop name walks to the catalog (walkthrough task 8.4) |
| 23 | `/shared/catalog` | `SharedViewsScreen.kt` | C2.0 | Approved products only; **P5T ₱750 OUT OF STOCK marked, not hidden**, with the optional "Notify me" interest toggle (c3/c6); CHW ₱35 IN STOCK — same source the owner maintains (c8). No internal columns (c2). Product cards open the customer-safe detail (name/price/availability only — C2.1, walkthrough task 8.6) |

### Anomalies preserved (handoff §4.3 — deliberately NOT "fixed")

1. G-04's ₱850.00 review total (the trap) — kept, and the arithmetic corrected
   from an initial 900 to the criterion's 850 (see §6, defect 1).
2. `PAID WITH BALANCE` / `PAID AND KEPT` never render as `PAID` — badges unchanged.
3. I-05 has no screen — the feasibility study is a document.
4. `JT 2612 3456 7890` keeps its spaces on every surface.
5. The buyer held-value pair stays deliberately unequal to the order rows.
6. SHIPPED + receive stays SHIPPED in `StockFsm` — untouched this phase.
7. Cancellation restocks exactly once — `confirmCancelAndRestock` reused, not reimplemented.
8. The app never flips payment status on its own — the OrderDetail payment track
   refuses PAID while a balance stands.

---

## 5. Side-by-side visual QA (§2 rule 2)

`tools/capture_routes.py` extended with `orderdetail / invoice / export / pack /
shipping / sharedinvoice / catalog` routes (the ORD-1042 row is found by UI dump,
never hardcoded). `tools/stitch_pairs.py` extended to the same routes.

**23/23 pairs stitched** into `docs/visual_qa/pairs/`. The Phase 5 pairs
compare against their EXACT coded frames: G4.0 (`3689-2779`), G6.0 (`3692-2859`),
G8.0 (`3693-2918`), I1.0 (`3742-3159`), C1.0 (`3765-3615`) and C2.0 (`3766-3652`)
were exported from Figma into `docs/visual_qa/figma/` once the connection
recovered, and `tools/stitch_pairs.py` was re-pointed from the D1.0 fallback
anchor to each frame. Every pair was inspected by eye.

### Defects the pairs/walkthrough caught this phase (all fixed, re-captured)

1. **Invoice review showed ₱900.00** (880+50−30) — the criteria pin **₱850.00**:
   the ₱50 handling is already inside the ₱880, so only −₱30 applies. Fixed in
   `InvoiceScreen`; re-verified on device (Review total ₱850.00; issued line
   "+₱50.00 handling and −₱30.00 repeat-buyer discount = ₱850.00").
2. **CLAIM track showed CONFIRMED** — the seed's Phase-1-era `OrderStatus.CONFIRMED`
   for ORD-1042 contradicted D-01 c1 ("Claim: CLAIMED") and the coded frame D1.0.
   The criteria win: seed re-pointed to CLAIMED (with the comment naming both
   sources), `InvoiceScreen`'s G-04 c9 gate re-based from the enum onto
   "has a reconciled invoice row and is not cancelled", and 160/0 held.
3. A capture-driver tap landed on the wrong order's shared view (MP-1038) while
   writing the pair — app behaviour was correct (MP-1042 verified manually
   before and after); the driver now re-captures via the ORD-1042 row.

### Stage-A verification on device (fresh captures, not stale ones)

- `pairs/more.png` + `android/more-bottom.png`: version **1.0-phase5 / build 5**;
  "Download a copy" is a white secondary master button; Data checks is one
  bordered card with the amber "2" tag and aligned Fix buttons; the Stock tab
  icon is the cube outline.
- `android/home-bottom.png`: 6 queue rows in one card, red alert dot on Unpaid
  invoices, sync line — matches A2.0's structure.
- `android/detail-dispatched.png`: after the walkthrough's dispatch, SHIPPING
  shows `JT 2612 3456 7890` verbatim with a **For Pickup** badge and PACKING
  shows the packed badge — the end-to-end state change is visible on the hub.

## 6. Walkthrough trace (9 task groups, TESTER_WALKTHROUGH_GUIDE-v1.13.11.2026)

Driven on the emulator via `tools/ui.py` dumps (coordinates derived per screen,
never hardcoded; IME and system dialogs dismissed and re-dumped when they stole
a tap — the Phase 4 trap list kept paying off):

1. **Sign in → dashboard** — app launches to the dashboard with ORD-1042's
   figures; hero buttons clean after A3.
2. **Orders list** — extended screen live: sub-tabs (Orders / Order History with
   a named empty state), the three review chips, "Record payment / Cancel order"
   actions on a cancellable row.
3. **Order detail (route 9)** — six tracks, CLAIMED, dropdowns per-track.
4. **Invoice (route 11)** — exact figure set; Review & issue shows the ₱850 trap;
   Confirm & issue → "Issued with +₱50.00 handling and −₱30.00 repeat-buyer
   discount = ₱850.00"; Export & share appears.
5. **Export (route 12)** — phone preview correct; format pickers; device share
   sheet opened (system UI); "Rendered: MP-1042.png · ready to share again
   without regenerating."
6. **Packing (route 13)** — "3 items still unpacked." refusal; checkboxes;
   "3 of 3 packed" → Complete → PACKED; the screen then shows the E-06 c4
   packed-state exclusion.
7. **Shipping (route 14)** — route selector; pre-filled receiver; `JT123`
   refused at entry; `JT 2612 3456 7890` accepted verbatim; dispatch confirmed;
   detail hub reflects FOR PICKUP + the tracking line.
8. **Shared views (routes 22/23)** — read-only MP-1042 invoice (no owner chrome,
   no cost fields); shop-name tap → catalog; CHW ₱35 IN STOCK; P5T ₱750 OUT OF
   STOCK + "Notify me"; product card → customer-safe detail (C2.1).
9. **Finish** — the session is walkable end-to-end with no manual step and no
   dead end; both customer routes render with cost/unit_cost/net_earnings absent
   (the Phase 5 exit condition).

## 7. Verification summary

| Check | Result |
|---|---|
| `assembleDebug` + `testDebugUnitTest` | **BUILD SUCCESSFUL, 169 tests, 0 failed** (150 baseline + 10 TrackingValidator + 9 InvoiceRenderer) |
| Literal colours outside `ui/theme` | **0** (audit re-run after every stage) |
| Phase references in UI strings | **0** (`NoPhaseReferencesTest` green) |
| `DeferredRouteScreen` call sites | **0** — all 23 routes live |
| Version | `1.0-phase5` / `versionCode = 5` |
| Pairs vs exact frames | G4.0/G6.0/G8.0/I1.0/C1.0/C2.0 exported; stitcher uses each screen's own frame |
| Pairs in `docs/visual_qa/pairs/` | **22** stitched and inspected |
| On-device captures | 15+ screens incl. every Phase 5 route and both Stage-A folds |

## 8. Deferred / named — CLOSE-OUT (all four tooling items closed same day)

The three §8 items the first draft named were closed in the same session:

1. **Export rendering is now REAL.** `InvoiceRenderer.kt` replaces the
   plain-text write: PNG via `Bitmap.compress(CompressFormat.PNG, 100, out)`
   (1080 px wide, height measured from a null-canvas layout pass) and PDF via
   `android.graphics.pdf.PdfDocument` (A4 595×842, one page refused past it per
   G-06 c7). It draws the customer-visible summary — brand band, MP-1042 /
   ORD-1042 / Pia Santos, item lines with right-aligned amounts, subtotal /
   shipping / total, the PAID WITH BALANCE word and the Paid/Balance line — on
   an Android Canvas using the app's own Fredoka / M PLUS 1 Code faces and
   `MomoColors` (no literal hex; the audit still reads 0). Verified end to end
   on device: `MP-1042.png` (1080×1197, valid PNG magic + IEND) and
   `MP-1042.pdf` (valid `%PDF-` + `%%EOF`) written under `cache/exports/`,
   shared through FileProvider, both pulled and inspected
   (`docs/visual_qa/rendered-MP-1042.png` shows the finished invoice). The
   content contract is unit-tested JVM-side through
   `InvoiceRenderer.visibleContent()` (+9 tests in `InvoiceRendererTest`):
   exact figure set present, payment state as words, cost/net-earnings/supplier
   figures provably absent even when the order row carries non-zero values.
2. **`search` capture re-tuned.** The step no longer scrolls (the 4.6 rebuild
   moved "Open item search" to y≈1196 on the unscrolled claim form) and now
   finds the entry by UI dump. `pairs/search.png` stitches — 23/23.
3. **Figma G/I/C exports recovered** — see §5: all six frames exported and the
   stitcher re-pointed; the D1.0 anchor is no longer used for any Phase 5 pair.
4. **Driver hardening (found while closing 1–3).** `capture_routes.py` now
   restarts the app before every order-chain step (the bottom bar's
   restoreState silently landed later captures on the wrong screen), un-escapes
   XML entities before text matching (`&amp;` hid "Confirm & issue"), scrolls
   ORD-1042 into view with a bounded loop, confirms the detail actually opened
   ("Order state" present) with retries, and lands `restart()` on the dashboard
   before continuing.

Remaining, named: the **shipping CSV** goes to the app temp dir (a production
build hands the same file to a storage provider); the record fields are
untouched by export (I-04 c6/c9). Nothing else was deferred. No commit or push
was made (per the handoff).

## 9. What changed, file by file

**New:** `domain/shipping/TrackingValidator.kt`,
`ui/screens/OrderDetailScreen.kt`, `ui/screens/InvoiceScreen.kt`,
`ui/screens/InvoiceExportScreen.kt`, `ui/screens/PackingChecklistScreen.kt`,
`ui/screens/ShippingRecordScreen.kt`, `ui/screens/SharedViewsScreen.kt`
(22+23), `test/.../TrackingValidatorTest.kt`.

**Modified:** `ui/theme/MomoTypography.kt` (line heights), `ui/components/MomoForm.kt`
(`MomoButton` compact size), `ui/components/HiFiComponents.kt` (HeroButton
spacer + outline), `ui/components/StatusBadge.kt` (`StatePill.forOrderStatus`),
`ui/screens/MoreScreen.kt` (buttons + data-checks card + padding),
`ui/screens/OrdersScreen.kt` (review chips, cancel, history/restore),
`data/MomoStore.kt` (`restoreCancelledOrder`), `data/SeedData.kt`
(ORD-1042 → CLAIMED with the source note), `ui/nav/MomoNavHost.kt` (all Phase 5
routes wired; imports), `res/drawable/ic_stock.xml` + `ic_shop.xml`,
`tools/capture_routes.py`, `tools/stitch_pairs.py`, `app/build.gradle.kts`
(version bump).

---

*End of Phase 5 report. The prototype is feature-complete against the 23-route
table: every route is a real screen, every Phase 4+5 criterion has a device-
verified surface, and the remaining named deferrals are tooling, not product.*
