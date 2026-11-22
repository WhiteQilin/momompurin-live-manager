# Phase 4.5 Report — Visual Backtrack to the REAL Coded Frames

**Date:** 14 November 2026
**Build:** `app-debug.apk`, version `1.0-phase4`, build 4
**Verified on:** `Medium_Phone_API_36.1`, 1080×2400 @ 420dpi
**Tests:** **150 run, 0 failed** (unchanged — no domain or store code touched)
**Literal colours outside `ui/theme`:** **0** across 52 files
**Evidence:** `docs/evidence-phase45-dashboard.png`, `docs/evidence-phase45-claim.png`

---

## 1. What went wrong and what this pass fixed

Phases 3 and 4 read their dashboard/claim geometry from Figma frames `3395:3654`
("1.1 - Overview Dashboard · With Data") and `3395:3207` ("Live Cart"). Both of those
live inside **`Design Reference (EXAMPLE)` (`3395:3206`)** — Kenshin's read-only
reference group at the bottom of the page `3395:3205` layer tree, marked
"DESIGN REFERENCE MADE BY KENSHIN (EXAMPLE ONLY!)". The real visual authority is the
top-level **coded frames** (`A1.0…J2.0`, `C1.0`, `C2.0`) and the component masters in
`Hi-Fi / Components` (`3415:361`).

This pass rebuilt the affected Compose UI from the real frames, measured from the
live node tree:

| Real source | What it changed |
|---|---|
| `Hi-Fi / Header` `3415:377` | Header rebuilt: mascot 60dp at (16,41) over a **205×52** dark pill; **two** 41dp dark circles on the right (search at x=320, bell at x=366) — the reference had one; greeting at y=109; short date form `Sat · 14 Nov 2026` bottom-right |
| `Hero / Live session` `3474:1308` | Callout rebuilt: **solid** `#FED161` (not a gradient) with a **1.5dp `#2B140E` border**; two gold `#FFB600` 164×46 buttons, radius 12, dark icon+label |
| `Section / Shop snapshot` `3452:706` | Cards rebuilt: white, 16dp radius, **one 1dp `#E6DCB8` border** (the neon per-queue borders are gone); stack icon → monospace figure → muted label → muted sub-line; red/rust accent figures on Payments due and Low stock |
| `Section / Needs your attention` `3452:711` | The 3-card "Critical Alerts" block replaced by **ONE amber card** (`#FFF0C2`, 2dp `#E67E22`), `!` icon, session name as title, counts as detail, chevron |
| `Section / Action queues` `3452:715` | Six separate tiles replaced by **ONE white card** (radius 16, 4dp padding) with six 82dp rows separated by hairline dividers; 8dp red alert dot beside the Unpaid value; `See all` actions in maroon `#9E1458` |
| Sync Row `3452:720` | Centred muted `Last synced … · pull to refresh` |
| `Hi-Fi / Bottom Navigation` `3419:421` | Bar rebuilt: yellow `#FED161` pill, **2.5dp `#FF9900` border**, five 79.8dp slots; active tab gets an 80×72 **white pill** with `#9E1458` icon+label; inactive in `#461204`; first tab label **"Home"** (was "Dashboard") |
| `B1.0 - Claim Form · Default` `3495:1488` | Claim form rebuilt to the flat form: app bar `< New Claim` + white `LIVE-0007` pill; label-above-input with 1.5dp `#2B140E` boxes (no cards); blue `Status/Info-Fill` suggestion chip (`Bea Cruz · @beacruz` shape); Product Match card (`Chiikawa Wipes` over `CHW · ₱35.00 · on hand 16`); Subtotal row; full-width gold **Save Claim** |
| `B1.2 - Claim Form · Scanned SKU` `3497:1494` | Scan mode restored as the frame's **Scan badge inside the SKU field** (trailing pill), not an app-bar toggle |

**Kept intact:** all 150 unit tests, both algorithm ports, `OrderOps`/`MomoStore`
writes, the claim/payment/keep behaviour verified in Phase 4. Only visual composition
changed; every criterion that Phase 4 verified on device is still served by the same
code paths.

## 2. Deliberate content deviations (recorded, per the Standing Visual Authority Rule)

1. **Real figures, not the frame's example numbers.** A2.0 shows ₱4,320 / 12 / 4 / 3
   as placeholder content. The app computes from the seed: ₱0.00 today's sales, 3 to
   pack, 7 payments due, 4 low stock. The criteria require the seeded truth (s.5.4).
2. **"Needs your attention" names the live session** (`LIVE-0008`) and the counts
   derived from the real data (`7 invoices due · 1 overdue · 4 products low`) instead
   of the frame's copy `Friday kawaii drop · 4 invoices due · 3 products low`. Same
   structure, real data.
3. **Queue sub-lines are computed** (`5 customers waiting`, `₱400.00 overdue`) rather
   than the frame's static examples, keeping J-01's live-count requirement.
4. **Sign out** stays as a quiet text link at the page end. The A2.0 frame implies a
   header control (A2.1's "Sign out Control" sits in the header); a visible link
   preserves A-01 c3 without inventing header chrome the frame does not draw.

## 3. Defects found and fixed during this pass

1. **`?attr/colorControlNormal` in the new search drawable** — the known AAPT trap;
   stripped, tint applied at the call site.
2. **"1 customers"** pluralization on the Paid-and-kept row (same class of bug as
   Phase 4's "1 orders"; caught on the screenshot).
3. **Scan toggle lost in the B1.0 rewrite** — restored per B1.2's in-field Scan badge.
4. Two compile errors from the rewrite (missing `Box` import, junk artifact in the
   header) caught before install.

## 4. On-device verification

| Check | Result |
|---|---|
| `assembleDebug` + `testDebugUnitTest` | **BUILD SUCCESSFUL, 150 tests, 0 failed** |
| Literal colours outside `ui/theme` | **0** across 52 files |
| Dashboard vs A2.0 | Header (2 circle buttons), solid bordered hero, white bordered snapshot cards with accents, single amber attention card, single white queue card with 6 divider rows + red dot, centred sync line — screenshot `docs/evidence-phase45-dashboard.png` |
| Bottom bar vs `3419:421` | Yellow pill, orange border, white active pill with maroon Home/Live/Orders ink — visible in both screenshots |
| Claim form vs B1.0 | `< New Claim` + white session pill, dark-outlined flat fields, Scan badge in-field, Product Match card, gold Save Claim — screenshot `docs/evidence-phase45-claim.png` |

## 5. Rule recorded for the rest of the build

**Never read geometry from `Design Reference (EXAMPLE)` (`3395:3206`) or any frame
numbered `1.x`/`2.x`.** The visual sources are the coded top-level frames
(`A1.0`–`J2.0`, `C1.0`, `C2.0`) on page `3395:3205` and the masters in
`Hi-Fi / Components` (`3415:361`). Phase 5 screens (D1.0, D2.0, E2.0, E3.0, E4.0,
G1.0–G9.x, I1.0–I4.x, J2.0, C1.0, C2.0) will be built from those node IDs directly.

## 6. Next step

Phase 5 — routes 8, 9, 11, 12, 13, 14, 22, 23 — now unblocked, starting with the
`tracking.py` port and `OrderDetailScreen` against real frame `D1.0` (`3558:1843`).
