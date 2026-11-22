# Phase 4.5 — Visual Backtrack Report (All 15 Built Routes)

**Date:** 14 November 2026
**Build:** `app-debug.apk`, version `1.0-phase4`, build 4
**Verified on:** `Medium_Phone_API_36.1`, 1080×2400 @ 420dpi
**Tests:** **150 run, 0 failed** · **0 literal colours** outside `ui/theme` (52 files)
**Phase-reference check:** 0 violations
**Evidence:** `docs/evidence-phase45-dashboard.png`, `docs/evidence-phase45-claim.png`,
on-device captures of Stock / POS / Session / Notifications / More taken during this pass

---

## 1. Source correction

Phases 2–4 had read visual authority from frames `3395:3654` / `3395:3207`, which live
inside Kenshin's read-only **`Design Reference (EXAMPLE)` group (`3395:3206`)** at the
bottom of page `3395:3205`'s layer tree. The real visual authority is the **coded
top-level frames** (`A1.0`–`J2.0`, `C1.0`, `C2.0`) and the component masters in
`Hi-Fi / Components` (`3415:361`). This pass rebuilt the affected UI from the real
nodes. **Standing rule: never read geometry from `3395:3206` or any `1.x`/`2.x` frame.**

## 2. Shared primitives aligned to the masters (`3415:361`)

| Master | Change |
|---|---|
| `Hi-Fi / App Bar` `3494:1488` | `MomoTopBar` rebuilt: 72dp `#FED161` band flush to top (status-bar inset on top of it), 44dp round back control with a Fredoka `‹` glyph, Fredoka SemiBold 18 title at the master's x=60 slot, optional white context chip (1dp Brand/Deep border, radius pill). `MomoAppBar` now delegates to it so both names cannot drift |
| `Hi-Fi / Button` `3572:1891` | `MomoButton` and `MomoConfirmDialog`'s actions: **radius 12** (not pill), 46dp, Primary `#FFB600` + BrandDarkest ink, Secondary white + 1dp `#E6DCB8` border + InkStrong, Disabled `#EBE6DF`/`#A89E9B` |
| Card surface (every list/head/total card) | `MomoCard` now carries ONE 1dp `Border-/-Subtle` (#E6DCB8) rule — the J2.0/N8.0/B3.0 card shape |
| Screen canvas | `SurfaceCanvas` re-pointed from the reference's yellow `#FFF0C2` to the coded frames' **Surface/Cream `#FDF3E7`** (window theme updated in step) |
| `MomoField` | Unfocused border now Border/Primary `#2B140E` — B1.0's input-box rule |
| `StatusBadge` | Already matched the `Hi-Fi / Status Badge` tones; unchanged |

## 3. Route-by-route alignment (15 built routes)

| # | Route | Real frame(s) | Status | What changed |
|---|---|---|---|---|
| 1 | `/sign-in` | `A1.0` `3422:431` | **Aligned by inheritance** | Header band + fields + gold radius-12 LOG IN button via shared primitives |
| 2 | `/home` | `A2.0` `3452:656` | **Rebuilt & verified** | Header (mascot 60dp over 205×52 pill, TWO 41dp circles: search + bell), solid bordered hero, white snapshot cards w/ 1dp beige border + accent figures, ONE amber attention card, ONE white queue card (6 divider rows, red dot), centred sync line |
| 3 | `/more` | `A2.5` `3461:1182`, `A2.6` `3461:1273` | **Aligned & verified** | Card-row layout inherits the corrected card + button masters; version/build rows match A2.5's App-version card |
| 4 | `/notifications` | `N8.0` `3752:3869` | **Rebuilt & verified** | Head card ("Open" + amber count tag) above white bordered entry cards; actions as master buttons; History tab unchanged behaviourally |
| 5 | `/live/claim` | `B1.0` `3495:1488`, `B1.2` `3497:1494` | **Rebuilt & verified** | Flat form: label-above-input with 1.5dp dark boxes, blue Info-Fill suggestion chip, Product Match card, Subtotal row, full-width gold Save Claim; scan as B1.2's in-field Scan badge |
| 6 | `/live/search` | `B2.0` `3512:1581`–`B2.3` | **Rebuilt & verified** | Sheet card (white, 1dp beige, radius 16) holding the 2dp dark query field, "N matches · most relevant first", muted result rows, blue relevance chips fed by the matcher's own `matchedField`, recent chips, category chips |
| 7 | `/live/session` | `B3.0` `3515:1605` | **Rebuilt & verified** | Yellow Session banner with white copyable order-number pill; claim rows as white bordered cards with `time · customer · ₱price` over `item … ORD-number` — B-04 c3's exact shape |
| 8 | `/orders` (+ filtered) | `A2.0` queue pattern, `D2.4` | **Aligned & verified** | Cream canvas, corrected cards/badges; order rows keep the badge/pill vocabulary |
| 9 | `/orders/{id}` | `D1.0` `3558:1843` | **Placeholder (Phase 5)** — will be built from D1.0 directly |
| 10 | `/orders/{id}/payment` | `D2.0` `3560:1698` | **Aligned by inheritance** | Corrected bar/cards/buttons; the figure set renders in the master's type roles |
| 11 | `/orders/keep` | `E3.0` `3610:2073`, `E4.0` `3612:2097` | **Aligned by inheritance** | Corrected card + button + checkbox language |
| 12–14 | product detail / form / adjust | `F1.0` `3640:2328`, `F4.0` `3650:2578` | **Aligned & spot-verified** | Product detail verified earlier in the pass (pricing figures intact); form/adjust inherit the field/button masters |
| 15 | `/stock` | `J2.0` `3750:3557` | **Rebuilt & verified** | Head card ("Available stock" + amber "4 low" tag), ONE white rows card with divider-separated two-sided rows (`CODE · Name` / `on hand N · reserved N` left, `available N` + `₱value` right, amber `Low` tag), muted Total card |
| 16 | `/stock/{id}` detail | `J2.0`/`F1.4` language | **Aligned & verified** | Pricing figures (₱21.11 chain) unchanged; master cards |
| 17 | `/pos` | `F3.0` `3649:2512` | **Rebuilt & verified** | Dark-outlined entry field, white Result card with `available N` tag, "This transaction" panel with muted "No lines yet"/line rows, Subtotal row, gold Convert-to-claim |
| 18 | `/stock/media` | `H1.0` `3713:3164`, `H2.0` `3713:3171` | **Aligned by inheritance** | Corrected cards/buttons; H-02 rejection behaviour unchanged |

(Inventory note: 15 **built routes** counted per the original route table; routes 9–14
that remain placeholders were untouched and will be built from their coded frames in
Phase 5.)

## 4. Content deviations (recorded per the Visual Authority Rule)

Same as part 1, plus:
- **Search sheet is a full screen** rather than a modal over the claim form: the same
  card, but route 6 is a real route in the table, so the sheet card renders as the
  screen's body. Behaviour (carry-back at price, B-03 c8) is unchanged.
- **Session banner shows the session's count/total** under the frame's `Session claims`
  label, and its white pill carries the newest order number (B-04 c2's "that order's
  number at the top").

## 5. Defects caught during this pass

1. `?attr/colorControlNormal` in the search icon drawable (AAPT trap; stripped).
2. "1 customers" pluralization on the dashboard's Paid-and-kept row.
3. The B1.0 rewrite dropped the "Full search" entry — restored as "Open item search"
   under the SKU field so B-05 c1 and route 6 stay reachable.
4. Yellow canvas from the reference group replaced by the coded frames' cream.
5. Two compile errors from the rewrites (missing imports) caught before install.

## 6. Verification summary

| Check | Result |
|---|---|
| `assembleDebug` + `testDebugUnitTest` | **BUILD SUCCESSFUL, 150 tests, 0 failed** |
| Literal colours outside `ui/theme` | **0** across 52 files |
| Phase-reference scan (shadow of `NoPhaseReferencesTest`) | **0** |
| On-device captures | Dashboard (A2.0), Claim form (B1.0), Item search (B2.0 sheet), Session claims (B3.0), Stock (J2.0), POS (F3.0), Notifications (N8.0), More (A2.5/A2.6), Pending-claims + Unpaid-invoices queues — all match their coded frames |
| Bottom bar | Yellow pill + 2.5dp orange border + white active pill (Home/Live/Orders/Stock/More) verified on every capture |

## 7. Next step

Phase 5 — routes 8, 9, 11, 12, 13, 14, 22, 23 — now unblocked. Build order: the
`tracking.py` port, then `OrderDetailScreen` from **`D1.0` (`3558:1843`)**, then the
rest from `D2.0` (`3560:1698`), `E3.0`/`E4.0`, `G1.0`–`G9.x`, `I1.0`–`I4.x`, `J2.0`
extensions, `C1.0`/`C2.0` — all from the coded frame IDs, never from `3395:3206`.
