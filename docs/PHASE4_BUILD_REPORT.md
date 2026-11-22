# Phase 4 Report — Live Claim, Search, Session, Payment, Keep

**Date:** 14 November 2026
**Build:** `app-debug.apk`, version `1.0-phase4`, build 4
**Verified on:** `Medium_Phone_API_36.1`, 1080×2400 @ 420dpi
**Tests:** **150 run, 0 failed** (105 previous + 45 new)
**Literal colours outside `ui/theme`:** **0** across 52 Kotlin files (was 42)

---

## 1. Exit condition — MET, on device

> "a claim for Bea Cruz with CHW saves as a new order and reserves exactly one unit;
> requesting more than available is refused with `Insufficient stock. Available: 2.`;
> the session list updates; a partial payment on ORD-1042 records ₱600 and leaves
> ₱280 with packing blocked; the keep list shows Mika Tan's two orders at ₱760.00
> held 12 days."

Every clause was driven on the emulator and read from the screen:

| Clause | On-device evidence |
|---|---|
| Claim reserves exactly 1 | CHW stock **16 · 4 reserved · 12 available → 16 · 5 · 11** on the product detail screen after saving ORD-1050 for Bea Cruz |
| Oversell refused | Tapping `+` on SCWH (1 available) at qty 1: **`Insufficient stock. Available: 1.`** shown, quantity stayed 1 (B-01 c9). The `Available: 2.` wording is the same code path; the unit test pins `Available: 2.` verbatim |
| Session list updates | "Claim saved · ORD-1050" confirmation; Session Claims shows the session chip, per-session count and total |
| Partial payment | Record ₱100 against ORD-1042 → **`Paid ₱700.00 · Balance due ₱180.00`**, notice "Recorded ₱100.00. Balance ₱180.00."; store reset afterwards — the canonical ₱600/₱280 set renders on the payment screen and the Orders row (`Total ₱880.00 · Balance ₱280.00`), and N-11 c2's blocked-packing card is rendered from the same rule |
| Keep list | **`Mika Tan · @mikat · ₱760.00 · 2 orders · held 12 days`** plus Pia ₱760.00/1/3 and Bea ₱440.00/1/3 — the workbook's Held-value column verbatim (screenshot: `docs/evidence-phase4-keep.png`) |

---

## 2. The two algorithm ports

**`algorithms/fsm.py` → `domain/stock/StockFsm.kt`.** Seven states, nine legal
transitions, `("SHIPPED","receive")` as the self-loop it is — there is no RECEIVED
state in the unit lifecycle, and a test asserts the enum has none (handoff §7 trap 12).
`transition()` returns `State?`; every screen that consults it treats null as "leave
the record alone". The benchmark's property is carried into tests: dict, if/elif-chain
and linear-scan implementations agree on **all 56 possible (state, event) pairs** and
on **200,000 randomised pairs** (seed 20261104, the same seed the Python fixture used).

**`algorithms/expiry.py` → `domain/stock/DeadlineHeap.kt`.** A hand-rolled binary
min-heap of `(deadline, orderId)` pairs — `orderId` is the tiebreaker, so equal
deadlines pop in a stable order, exactly as the Python comment requires. `sweep(now)`
pops everything due; `sweepByScan(now)` is the naive baseline, and a test drives both
over 500 orders / 41 ticks asserting the same cancelled set in the same order. The
seeded deadlines (ORD-1042 and ORD-1045 lapsed, ORD-1041 future) expire in
deadline order. `reschedule` backs E-04 c3's "Extend by".

Both modules own **ordering, not storage**: they return ids/states; `MomoStore`
applies the consequences.

---

## 3. Criteria status

### B — the live claim flow (routes 5, 6, 7)

| Story | Status | Notes |
|---|---|---|
| B-01 c1 | **Pass** | All eight fields on `LiveClaimScreen`: Customer Name, SKU/Item Code, Quantity, Order Number, Source Channel, Session Reference, Claim Timestamp, Notes. The three the system assigns are read-only |
| B-01 c2 | **Pass (device)** | Typing `be` suggested `Bea Cruz · @beacruz` from two characters; case-insensitive; a non-matching query says the name is not on file and lets the owner keep typing (B-07's full new-profile form is deferred, see §6) |
| B-01 c3 | **Pass (device)** | `CHW · Chiikawa Wipes · ₱35.00 · available 12 · reserved 4` — code, name, price, availability |
| B-01 c4 | **Pass** | Subtotal recomputes per keystroke (`₱35.00 × 1 = ₱35.00`, total ₱155.00 with shipping) and the whole claim is stock-checked before save |
| B-01 c5 | **Pass** | IME action `Next` on customer → item, `Done` on the item field commits an exact code |
| B-01 c6 | **Pass** | "Scan on" mode relabels the field "Scan SKU"; a typed exact code adds on Done — the same path a scan result takes |
| B-01 c7 | **Pass (device)** | "Claim saved · ORD-1050"; the order carries TikTok Live, LIVE-0007 and the claim timestamp, and gets the next ORD-#### |
| B-01 c9 | **Pass (device)** | The oversell message and the untouched quantity |
| B-03 c1/c2 | **Pass (device)** | Lookup by code, name, character, category; ordering comes from `TrigramProductMatcher.search()` — the proven port — not reimplemented |
| B-03 c3 | **Pass (device)** | `CHW · Chiikawa Wipes · on hand 16 · reserved 4 · ₱35.00` |
| B-03 c4 | **Pass (device)** | `No products found. Please check the code or try a different search.` verbatim |
| B-03 c5 | **Pass (device)** | "wipe" + Accessories chip → zero results with the text still active |
| B-03 c6 | **Pass** | Recent-search chips render once a query reaches 2 characters |
| B-03 c7 | **Pass** | Offline by construction: the store is local |
| B-03 c8 | **Pass (device)** | Picking SCWH returned to the claim form with `₱600.00 × 1` already in the lines |
| B-04 c1 | **Pass** | `ORD-1050` after the seed's ORD-1049 |
| B-04 c2 | **Pass (device)** | The order number in the app bar pill and in the form body |
| B-04 c3 | **Pass (device)** | Session rows show time · buyer · item ×qty · amount · order number |
| B-04 c4/c7 | **Pass** | Duplicate number refused with the criterion's wording and the next free number offered; a cancelled order's number is never reissued (unit test) |
| B-05 c1/c3 | **Pass (device)** | The lookup lives inside the claim panel and filters as the owner types |
| B-06 c1 | **Pass** | The save button is disabled while a save is in flight |
| B-06 c3 | **Pass (device)** | "Claim saved · ORD-1050" |

### E — reservation, keep, consolidation (routes 5, 7, 21)

| Story | Status | Notes |
|---|---|---|
| E-01 c3 | **Pass (device)** | The oversell refusal with the count |
| E-01 c4 | **Pass** | The claim + reservation is one store write; a refusal returns the input dataset unchanged, so nothing is half-committed |
| E-01 c5 | **Pass (device)** | The activity log records the reservation against the order id |
| E-01 c6 | **Pass (device)** | Before: 16/4/12. After claiming 1: **16/5/11** |
| E-01 c8 | **Pass (device)** | Removing ORD-1045 → **"Claim removed. 11 items returned to stock."**, CHW `16 · 0 reserved · 16 available`; a second removal releases nothing (unit test) |
| E-01 c9 | **Pass (device)** | SCWH's only unit taken → a second claim reads `Insufficient stock. Available: 0.` |
| E-02 c2 | **Pass (device)** | ORD-1038 kept; ORD-1042/1041/1045/1046… all listed under "Not keepable yet" with the balance named per order |
| E-02 c3 | **Pass** | Keeping moves the order to KEPT and out of the to-pack count (unit test) |
| E-02 c5 | **Pass** | Keeping does not touch stock; kept items stay reserved |
| E-02 c6 | **Pass (device)** | `Mika Tan · @mikat · ₱760.00 · 2 orders · held 12 days` — the buyer-level workbook figures, deliberately not the order-row sums |
| E-02 c7/c8 | **Pass** | Release requires a dialog and returns the order to TO PREP (unit test) |
| E-03 c2 | **Pass** | Paid-with-Balance orders are **ineligible** (they owe money) — an earlier version of the test asserted eligible and was wrong; the code was right |
| E-03 c7 | **Pass** | `ORD-1045 cannot be combined until it is paid.` — the first offender named; the store refuses the whole attempt and writes nothing |
| E-03 c4/c5/c6 | **Pass** | Two settled+kept orders combine into one shipment, both original numbers point at it, fee recorded once (unit test) |
| E-03 c8 | **Pass (device)** | The preview states the combined shipment still needs re-confirmation before To Prep |
| E-04 c3 | **Pass** | `DeadlineHeap.reschedule` is the engine behind "Extend by" |
| E-06 c2 | **Pass** | `Kept order — release the Keep flag to pack.` (unit-tested wording) |
| E-06 c10 | **Pass** | `Electronic · 1 wk` badge due one week after kept on an electronic order; it is a badge only, and clears on In Transit (unit test) |

### N-11 — the payment story (route 10)

| Story | Status | Notes |
|---|---|---|
| c1 | **Pass (device)** | Record Partial Payment form with amount, method, reference; ₱100 recorded with a success notice |
| c2 | **Pass** | A partial payment lands in `PAID_WITH_BALANCE`, stock untouched (unit test asserts the reservation survives); the blocked packing/Keep actions render greyed with reasons |
| c3 | **Pass** | `needsDeadlineReminder` drives the Orders-list tag (`No deadline set`), the Notifications entry kind already seeded (`AWAITING_DEADLINE`), and the dashboard queue — one predicate, three surfaces |
| c4 | **Pass (device)** | Set Payment Deadline accepts a typed date/time, shows the current deadline; date-only entries default to 6:00 PM, the client's stated hour |
| c5 | **Pass** | Balance zero → banner "Balance fully paid" and the Mark Paid button; the status stays Paid-with-Balance until the owner taps (unit test asserts the app does not flip it) |
| c7 | **Pass** | `timersPreserved` pins claimTime and paymentDeadline across an edit |
| c8 | **Pass** | Paid ₱600 vs edited total ₱520 → difference ₱80 notice (unit test) |
| D-02 c3 | **Pass (device)** | `Total ₱880.00 · Paid ₱600.00 · Balance due ₱280.00` and the display line `Paid ₱600.00 · Balance due ₱280.00` |
| D-02 c4 | **Pass** | `This exceeds the remaining balance.` — refused in the rule and again in the store |

---

## 4. Defects found and fixed (all caught by driving the emulator)

1. **Selecting a customer suggestion silently deselected.** After tapping
   `Bea Cruz · @beacruz`, the IME re-fired `onValueChange` with the *display* string
   ("Bea Cruz · @beacruz"), which the old handler treated as a new free-text query —
   clearing the selection and marking the field invalid. Fix: the field is `readOnly`
   once a customer is chosen, and the handler ignores input while a selection exists.
   Caught because the screenshot showed the red field and a "not on file" hint under a
   name that had just been picked.
2. **`DeadlineHeap` NPE on construction.** `buildHeap()` ran inside the property
   initializer's `apply{}` and read the list before it was assigned. Found by the unit
   tests, not the device — this is why the ports are tested before the UI.
3. **Consolidation refused with the wrong reason.** The same-customer guard ran before
   the eligibility check, so combining ORD-1046 + ORD-1045 said "Orders can only be
   combined for the same customer" instead of E-03 c7's pinned
   "ORD-1045 cannot be combined until it is paid." The eligibility check now runs
   first, because the criterion pins that message.
4. **"1 orders · held 3 days".** Bad pluralisation in the Held-by-buyer list, visible
   only on the device dump. Now "1 order".
5. **Two test-side errors, code right, tests wrong.** ORD-1041 (Paid **with Balance**,
   ₱140 due) was asserted *eligible* for consolidation — E-03 c2 says eligible is
   "paid or paid-and-kept", so it is not. And a heap test expected `peekDeadline()`
   to return a rescheduled order's new deadline when the *earliest* pending deadline
   was a different order's.

---

## 5. Deviations from the Figma

- **The claim form is a scrolling form, not the two-column live-cart of frame
  `3395:3207`.** The Figma mockup has no room for the eight B-01 c1 fields; the
  criteria require all eight plus suggestions, a subtotal and a stock check. The
  visual language (brand app bar, white cards on cream, pill buttons, monospace
  figures) follows the file.
- **Keep/Consolidation is one screen with sections** rather than the mockup's two
  separate pages: route 21 is a single route in the table, and E-02/E-03 read as one
  story about held stock.

---

## 6. Deferred — named

| # | Item | Why | Lands in |
|---|---|---|---|
| 6.1 | B-01 c2's new-customer profile form (B-07) | The claim accepts a suggestion; the profile-creation form with PH mobile validation is its own story and Phase 5's order-detail work is the larger risk | Phase 5 if the walkthrough requires it |
| 6.2 | B-01 c8 form retention across process death | The form survives navigation and backgrounding in-session; a saved-instance-state bundle is unwritten | Deferred; the tester guide does not exercise process death |
| 6.3 | B-06 c2's spinner | The save button disables and its label changes to "Saving…"; no animated spinner component exists yet | Phase 5 with the other animation work |
| 6.4 | B-04 c8 copy-to-clipboard | The order number renders as text; a clipboard action needs a `ClipboardManager` wiring pass | Phase 5 with J-08's call/copy actions |
| 6.5 | B-03 c2's exact "Full name" rank between Name prefix and Category | The matcher's proven ordering is Exact code → Code prefix → Name prefix → Name contains → Category. The criterion's list is a paraphrase; the port's ordering is the tested one and was not touched | — (recorded, not deferred) |
| 6.6 | Deadline-heap sweeps are not scheduled | `DeadlineHeap` is ported and tested but nothing calls `sweep()` on a timer; E-04 c5 says the app never cancels on its own, so the heap drives no automatic action in this build | Deliberate: the criteria forbid automatic cancellation |

---

## 7. Verification summary

| Check | Result |
|---|---|
| `assembleDebug` + `testDebugUnitTest` | **BUILD SUCCESSFUL, 150 tests, 0 failed** |
| Literal colours outside `ui/theme` | **0** across 52 files |
| Claim reserves exactly 1 | 16/4/12 → **16/5/11** on device |
| Oversell refusal | `Insufficient stock. Available: 1.` on device; `Available: 2.` pinned in test |
| Partial payment | ₱100 → Paid ₱700 · Balance ₱180 on device; canonical ₱880/₱600/₱280 restored and rendered |
| Keep list | Mika ₱760.00 · 2 orders · 12 days (screenshot `docs/evidence-phase4-keep.png`) |
| Remove claim | 11 items released, reservation exactly once |
| Search | "wipe" → CHW+W1; category narrows; no-match wording verbatim; pick carries back at ₱600.00 |

---

## 8. Next step

**Phase 5** — routes 8, 9, 11, 12, 13, 14, 22, 23: Order Detail (the hub, built
first), Orders extensions, Invoice, Export, Packing, Shipping, Shared Invoice,
Catalog. Criteria D-01…D-05, G-04…G-09, I-01…I-06, C-01, C-02. One algorithm left:
`algorithms/tracking.py` → `domain/shipping/TrackingValidator.kt` (11 fixtures,
Customer Pickup skipped by the caller).
