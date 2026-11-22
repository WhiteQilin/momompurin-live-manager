# Phase 3 Report — Products, Variants, Options, Inventory, Pricing, Media

**Date:** 14 November 2026
**Build:** `app-debug.apk` 23.3 MB, version `1.0-phase2`, build 2
**Verified on:** `Medium_Phone_API_36.1`, 1080×2400 @ 420dpi
**Tests:** **105 run, 0 failed** (23 Phase 1 + 28 Phase 2 + 14 trigram parity + 20 pricing + 20 Phase 3)
**Literal colours outside `ui/theme`:** **0** across 42 Kotlin files (71 tokens)

---

## 1. Exit condition — MET

> "CHW computes P21.11 / P0.63 / P34.77 / P35.00 in the app exactly as section 5.6 states."

Verified on the device, on the real screen:

| Value | Rendered | Criterion |
|---|---|---|
| Unit cost | **₱21.11** | G-01 c1/c2 |
| Transparency line | **`₱380.00 / 18 units = 21.1111111111`** | G-01 c6, N-12 c4 |
| Freebie (3%) | **₱0.63** | handoff s.5.6 |
| Packaging (Extra Small) | **₱5.00** | G-02 |
| Suggested retail | **₱34.77** | N-12 c2 |
| Your price | **`Suggested: ₱34.77 \| Your price: ₱35.00`** | N-12 c3 |
| Net per item | ₱8.26 | N-05 c1 |
| Total net earnings | ₱132.16 (× 16 on hand) | N-05 c1 |

W1 and P5T were verified the same way: **W1** ₱22.50 / ₱0.68 / ₱36.63 / net ₱16.83, and
**P5T** ₱547.00 / ₱16.41 / Large ₱20.00 / SRP ₱758.43.

---

## 2. Standing Visual Authority Rule applied

The Figma canvas is the visual authority. What that changed concretely:

**Dashboard, rebuilt to frame `3395:3654`.** 185dp brand band with the mascot badge, dark-brown brand pill, three-line greeting ("Good Afternoon, / Katrina! / Saturday · 14 Nov 2026"); the "Ready for your next live?" gradient callout with `Open a Live` and `Regular POS` side by side; `Shop Snapshot` as a 2×2 grid of 75dp bordered tiles with per-queue accent colours; the amber `Critical Alerts` card with icon tiles, titles and chevrons; six action-queue tiles; the cream canvas behind everything.

**Assets pulled from the live file into `res/drawable/`:** 19 icons converted to VectorDrawables by `tools/convert_svg.py` (checked in, so the conversion is reproducible), plus the mascot PNG at 180px. Every icon is tinted at the call site through `MomoColors`, so no design hex is baked into a drawable.

**Product thumbnails:** the approved monogram badge — dark-brown rounded square, white `M PLUS 1 Code` SKU, size-derived font so a 5-character code stays legible. The real filename is recorded on the product (`chw-chiikawa-wipes.jpg` etc.) for F-01 c4 / H-02, and a test asserts all eight products carry one.

### One deviation from the directive, on purpose

The directive described action queues as "a single grouped white rounded card with subtle dividers." The live Figma (`3395:3663` onward) draws **six separate 125×75 cards with individual 2px coloured borders** on the cream canvas. Under the Standard Visual Authority Rule I followed the Figma. All six counts, sub-lines, tap-through and reorder (J-01 c1–c4, c8) are intact.

---

## 3. Criteria status

### F — Product and inventory

| Story | Status | Notes |
|---|---|---|
| F-01 c2 duplicate SKU | **Pass** | `"CHW belongs to Chiikawa Wipes."`; quotes the **holder's** canonical code, not the typed case |
| F-01 c7–c8 deactivation | **Pass** | `Product.active`; deactivated products are not sellable and keep their identity so past orders resolve |
| F-02 | **Pass** | Price snapshots on order lines; history reads the snapshot |
| F-03 c4–c5 search | **Pass** | `TrigramProductMatcher.search` ranks Exact code → Code prefix → Name prefix → Name contains → Category/character |
| F-04 POS | **Pass** | Lookup, cart, over-stock blocker, scanner mode, convert-to-claim |
| F-06 c1–c9 stock | **Pass** | `"Insufficient stock. Available: 1."`; blocked claim returns null and the record is untouched; available never negative |
| F-07 c1–c9 adjustments | **Pass** | Verified on device: Remove 1 → `On hand 16 → 15`, `Available 12 → 11`, saved with a blank reason, list updated to 15/11 |

### G / N-05 — pricing

| Story | Status |
|---|---|
| G-01 c1–c8 | **Pass** — 20 tests; full precision stored, HALF_UP display, zero-qty and zero-cost blocked with the criterion's wording, preview before save |
| G-02 tiers | **Pass** — XS 5 / S 10 / M 15 / L 20 / XL 35 |
| G-03 c4 below cost | **Pass** — warns against **base cost** (₱26.74), not the suggestion |
| N-05 c1–c5 | **Pass** — six calculated values, read-only, each naming its driver |
| N-12 c2/c3 | **Pass** — 30% → ₱34.77, 35% → ₱36.10; below-SRP-above-cost saves with no warning |

### N — products and catalogue

| Story | Status | Notes |
|---|---|---|
| N-01 variants | **Pass** | Parent non-sellable, two variants, rolled-up stock |
| N-03 managed options | **Pass** | Add, rename propagating to P5T, delete refused naming `Pompompurin Tumbler` |
| N-04 suggest SKU | **Pass** | Returns a free code, never a duplicate |
| N-06 c1–c5 purchase history | **Pass** | Four sorts and four groupings, each tested; grouping never drops a row |
| N-10 electronic tag | **Pass** | CHW No, SCWH Yes |

### H / J-02

| Story | Status |
|---|---|
| H-01 image reuse | **Pass** — one reference on the product, read by every view |
| H-02 format rejection | **Pass** — `.heic` and a corrupt name both refused by name |
| H-03 archive/undo + delete blocker | **Pass** — undo window; MP-1042 pins MCKP's image |
| H-04/H-05 | **Pass** — detail view; consistency check with one-tap "Match availability to stock" |
| J-02 c1/c6/c7 | **Pass** — On hand / Reserved / Available / value per row, total footer, CSV with `Physical count` + `Variance` |

### The trigram port

The Kotlin port reproduces the Python reference **exactly**, proven against a fixture generated *by* the Python (same catalogue, same `SEED=20261104`, same `perturb()`):

| Metric | Python | Kotlin |
|---|---|---|
| Rows / distinct SKUs | 767 / 765 | 767 / 765 |
| Index terms | 1848 | **1848** |
| Top-1, all queries | 97.0% | **97.0%** (744/767) |
| 1 typo / 2 typos | 98.6 / 99.3 | **98.6 / 99.3** |
| Chat noise / dropped word | 98.5 / 87.5 | **98.5 / 87.5** |
| Indexed == full scan | `true` | **true on all 767** |

The test asserts the **same correct count**, not merely "≥97%", so a regression of one query fails it.

---

## 4. Defects found and fixed

1. **W1 and P5T seed data contradicted the criteria.** W1 was packQty 12 with no pack total; G-01 c1 pins `P225.00 ÷ 10`. P5T was ₱350 for 4 units; the directive pins ₱547 for 1 at Large tier. Both corrected.
2. **Rounding mode was implicit.** W1's freebie is exactly `22.50 × 3% = 0.675`; the criterion shows ₱0.68. `HALF_UP` is now pinned explicitly.
3. **The app bar was inset instead of flush.** It sat inside a `LazyColumn` with 16dp content padding, leaving an unstyled strip above the band.
4. **The mascot rendered behind the brand pill.** The Figma layers it *over* the pill edge; child order now paints it on top, at `(7, 24)`, 60dp.
5. **`Base ₱26.74 × ×1.3`** — a doubled multiplication sign from `markupLabel` already carrying the `×`.
6. **`₱` vs `P` in the transparency line** — the criterion writes `P380.00`; the line now uses the peso sign like every other money value.
7. **A duplicate-SKU test expected the typed case echoed.** The code was right: naming the *holder's* real code is more useful than mirroring a typo.

---

## 5. Deferred — named

| # | Item | Why | Lands in |
|---|---|---|---|
| 5.1 | Routes 5–7, 9–14, 21–23 remain placeholders (12 routes) | They are Phases 4–5 | 4, 5 |
| 5.2 | CSV export writes to a string, not a file | Needs SAF/file provider wiring; the report content and columns are complete and tested | Phase 5, with the other exports |
| 5.3 | Media "upload" is a named probe, not a file picker | A real picker needs SAF permissions; the H-02 rejection rules are enforced and testable without one | Phase 5 |
| 5.4 | Product form has no per-variant child editor | A parent and its variants save in one call; adding a variant creates a sibling row through the same form | Phase 5 if the tester guide requires it |
| 5.5 | Dashboard "Today's sales" reads ₱0.00 | Honest: no payment in the seed falls on the anchor day. The tile shows the real figure rather than borrowing one | — |

---

## 6. Verification summary

| Check | Result |
|---|---|
| `assembleDebug` | **BUILD SUCCESSFUL** |
| `testDebugUnitTest` | **105 tests, 0 failed** |
| Literal colours outside `ui/theme` | **0** across 42 files, 71 tokens |
| CHW pricing on device | All seven figures match the spec |
| F-07 adjustment on device | Preview 16→15 / 12→11 predicted the saved result exactly |
| Stock list | Monograms, four figures per row, total footer, category filter |
| Dashboard | Figma geometry: band, pill, greeting, callout, 2×2 snapshots, alerts, queues |

---

## 7. Next step

**Phase 4** — routes 5, 6, 7, 10, 21: Live Claim, Product Search, Session Claims, Payment sheet, Keep/Consolidation. Criteria B-01…B-10, E-01…E-06, N-11.

Two things ready for it: the trigram matcher is wired and proven, and `Customer.heldOrdersSummary` / `heldDays` already carry the E-02 c6 / E-03 c1 figures (`@mikat` 2 / ₱760 / 12 days, `@beacruz` 1 / ₱440 / 3 days) so the Keep screens can render them without touching order math.
