# HCI Usability Audit Report — Phase 5.5

**Date:** 15 November 2026
**Scope:** all 23 routes of the Momompurin Live Manager Android prototype (`1.0-phase5`, build 5)
**Lens:** a first-time, non-IT shop owner working WITHOUT the tester walkthrough guide in front of them
**Verification:** `assembleDebug` + `testDebugUnitTest` → **BUILD SUCCESSFUL, 169 tests, 0 failed** · **0 literal colours** outside `ui/theme` · **0 phase references** in UI strings
**Evidence:** re-captured emulator screenshots for every modified screen in `docs/visual_qa/android/`; 23/23 stitched `[Figma|Android]` pairs in `docs/visual_qa/pairs/`

Guardrails held throughout: every canonical seed figure and criterion-tested string is unchanged (`₱880.00`, the CHW chain, all pinned messages); the brand language stays inside `ui/theme` tokens (the new controls reuse `BrandPrimary/BrandDeep/SurfaceCard/BorderSubtle/ButtonPrimaryFill`, radius tokens and the master type roles); nothing required was removed — every fix is additive clarity.

---

## Part 1 — Confirmed fixes (applied first)

| Fix | Where | Result |
|---|---|---|
| **Sign out relocated** (A-01 c3 + Jakob's Law): the floating footer link — the only "Sign out" in the app — sat at the very bottom of a long scroll, one accidental tap from the Home tab | `DashboardScreen.kt`, `HiFiComponents.kt` | Footer link **removed**. Added a bordered **"Sign out" pill chip in the header band** (under the two circle buttons, 1.5dp Brand/Deep ring matching their style) and a full **Account card at the foot of More** ("Signed in as katrinajj254@gmail.com" + outlined secondary Sign out master button). Both open the same A2.1 confirmation dialog. Verified on `home.png` / `more-bottom.png` |
| **Header search wired** (information scent): the magnifying glass opened the pending-claims queue — not a search | `DashboardScreen.kt` + nav | Now opens **`/live/search`**, the product/item lookup a search icon means |
| **Cross-tab discoverability**: Keep & Consolidation was reachable only by tapping the Paid-and-kept queue card; Media and "add a product" had no visible entry at all | `OrdersScreen.kt`, `StockScreen.kt`, nav | Orders tab gained a named **"Kept & consolidation"** bordered button + one-line explainer; Stock tab gained **"+ Add product"** (gold primary mini-button, routed to the product form's create mode) and **"Media library"** |

## Part 2 — Systematic audit findings & fixes

- **Editable vs read-only status tracks were indistinguishable** (`/orders/{id}`): all six tracks ended in the same badge, so a user could not tell which respond to a tap. Editable tracks now render a **bordered selector pill with a ▾ caret** (1.5dp Brand/Deep ring); read-only tracks keep the plain badge. The difference IS the affordance.
- **Inline card actions were bare text links** ("Record payment", "Cancel order", "Restore", "Adjust", "Fix", "Copy address", "Resolve"): no target boundary, easy to mis-tap the parent card. All converted to a new **`MiniAction`** component — a 34dp bordered pill using the same radius/border tokens (destructive = DangerInk border/ink; primary = gold fill). Stock rows' "Adjust" and More's "Fix" included.
- **Zero-count dead ends**: an empty queue showed only "Nothing in this queue" — no way back. Empty states now name WHY it is empty and offer **"Show all orders"** (filtered queue) or **"Clear this check"** (review chip), both wired in the nav graph. Verified on `queue-empty.png` (Awaiting pickup: 0).
- **Specialised workflows lacked plain-language framing** — one-line helpers added: Order state ("Six separate tracks. Change one without moving the others."), Keep & consolidation ("Tick two or more from the same buyer to combine them into one delivery — you pay the shipping once, not per order."), Packing checklist ("Tick each item as you put it in the parcel…"), Invoice review ("The handling fee is already part of the order total, so only the discount changes it." — the ₱850 trap now explains itself), Adjust stock ("A stocktake correction… changes on-hand stock, not the price."), Product form ("Enter what you paid for the WHOLE pack…"). Keep's title became **"Keep & consolidation"**.
- **Shared previews could strand a tester**: `/shared/invoice/{id}` and `/shared/catalog` had no back control. A **"‹ Back"** chip in each brand band (status-bar inset added so it clears the clock) gives owners previewing on-device a way out — it is navigation only: **zero** edit/add/delete controls and **zero** internal cost fields were added (C-01 c2/c3 still hold). The catalog's sub-line now says "prices and stock as the shop has them."
- **Visual symmetry**: shared bands' status-bar insets fixed; card paddings already uniform (14/12); the Stock rows' long names truncate with ellipsis by design; all chips share the LabelChip role at 23–32dp.

---

## Part 3 — The 23-route audit table

| # | Route | (a) Reachability from bottom nav | (b) HCI friction found (or verified clean) | (c) Fix applied | (d) Heuristic / principle |
|---|---|---|---|---|---|
| 1 | `/sign-in` | Entry point (no bar — correct, pre-auth) | **REBUILT in Phase 5.6** — the Phase 2 build did NOT match A1.0 (no yellow header band, no mascot badge, centred brand text instead of the welcome block, floating labels on pale boxes, pill LOG IN). The earlier "verified clean" row here was wrong: the screen had never been captured pre-auth and inspected against its frame | Rebuilt 1:1 from `A1.0` (`3422:431`): #FED161 band with status-bar inset, cream halo + real exported mascot overlapping the boundary, left-aligned welcome block, label-above fields with 1.5dp #2B140E rules and r12 corners, Show/Hide toggle in the box, gold r12 LOG IN (disabled "SIGNING IN…" per A1.1), footer "Owner access only · Momompurin Live Manager". Capture step made deterministic (`pm clear` → guaranteed pre-auth); `pairs/signin.png` inspected against the frame | Jakob's Law (recognition over recall on return visits); Aesthetic-usability heuristic; Norman mapping (label above its own box) |
| 2 | `/home` | Home tab | "Sign out" was a floating footer link near the Home tab (Jakob's Law: account controls live top-right); header search opened a queue, not a lookup | Footer link removed; bordered header chip + search→`/live/search`; More Account card added | Jakob's Law; information scent; Norman signifier |
| 3 | `/more` | More tab | No account section — a user hunting "sign out" opens settings first; Data-checks "Fix" was bare text | Account card (owner email + secondary Sign out master, same A2.1 dialog); Fix → bordered MiniAction | Jakob's Law; Nielsen #2 (real-world match); Fitts (target size) |
| 3b | `/more/history` | More → Edit history row | Verified clean: named purpose ("Field, old value, new value, actor, time"), back in bar | — | Nielsen #1 (system status) |
| 4 | `/notifications` | Home → bell | Verified clean: Open/History tabs, actions as master buttons, back in bar | — | Nielsen #1, #3 |
| 5 | `/live/claim` | Live tab | Verified clean: label-above fields, Scan badge, suggestions, master Save; pack-costing helper added later on the product form | — | Nielsen #2 |
| 6 | `/live/search` | Live tab → "Open item search" (+ NEW: dashboard header search) | Entry point was hidden inside the claim form only; a search icon on Home did not reach it | Header magnifying glass routes here; capture step re-tuned; relevance chips explain ordering | Information scent; Nielsen #6 (recognition) |
| 7 | `/live/session` | Live tab → session card | Verified clean: banner + copyable order pill, rows show time·customer·₱·ORD | — | Nielsen #1 |
| 8 | `/orders` | Orders tab | Keep & Consolidation discoverable only through a queue card; card actions were bare links; empty filtered states were dead ends | Named "Kept & consolidation" button + explainer; Record payment / Cancel order / Restore as MiniAction (destructive tinted); empty states name why + "Show all orders" / "Clear this check" | Nielsen #3 (user control), #6; Norman signifier; Fitts |
| 8b | `/orders/filter/{queue}` | Dashboard queue cards | 0-count queues stranded the user | Same empty-state fix, wired via `onShowAllOrders` | Nielsen #3 |
| 9 | `/orders/{id}` | Orders list row | Editable and read-only status tracks looked identical; helper absent; Copy address / Resolve were bare links | Editable tracks = bordered selector pills with ▾ caret; "Six separate tracks…" helper; MiniActions; D-01 c3 vocab untouched | Norman (signifier, affordance); Nielsen #2, #6 |
| 10 | `/orders/{id}/payment` | Order detail → Record payment | Verified clean: figure set, blocked-packing message, Mark-Paid invariant intact | — | Nielsen #1 |
| 11 | `/orders/{id}/invoice` | Order detail → Invoice | The ₱850 review total reads like a bug without explanation ("+ Handling" implied the 880 grew) | Helper line ("handling already part of the order total…"); label now "Handling (already included)"; figures unchanged (880/850 pinned) | Nielsen #2 (match real world); error prevention |
| 12 | `/orders/{id}/export` | Order detail → Export | Verified clean: phone preview, PNG/PDF choice, share sheet, success pill; format buttons already bordered masters | — | Nielsen #3 |
| 13 | `/orders/{id}/pack` | Order detail → Pack | "0 of 3 packed" lacked purpose framing | Helper ("Tick each item as you put it in the parcel…"); exclusions already state cause (E-06) | Nielsen #1, #2 |
| 14 | `/orders/{id}/shipping` | Order detail → Ship | Verified clean: route selector, entry-time tracking validation, Copy-all-fields, verbatim `JT 2612 3456 7890` | — | Nielsen #2, error prevention |
| 15 | `/stock` | Stock tab | "Adjust" was bare inline text in every row; Media + add-product had no named entry; long names truncated (by design) | "+ Add product" (gold) + "Media library" MiniActions; Adjust → bordered MiniAction | Norman signifier; Fitts; information scent |
| 16 | `/stock/{id}` | Stock row tap | Verified clean: pricing chain + transparency line, Match-availability one-tap | — | Nielsen #1 |
| 17 | `/stock/{id}/edit` | Product detail → Edit (NEW: Stock → + Add product) | Pack-cost fields read like a single-item price entry | Helper ("Enter what you paid for the WHOLE pack…"); create mode already keyed by unresolvable id | Nielsen #2; error prevention (G-01 chain) |
| 18 | `/pos` | Home → Regular POS | Verified clean: entry field, results, transaction panel, convert action | — | Nielsen #6 |
| 19 | `/stock/{id}/adjust` | Product row / detail → Adjust | "Adjust stock" reads like a price edit | Stocktake helper ("changes on-hand stock, not the price"); large-adjustment confirm untouched | Nielsen #2; error prevention |
| 20 | `/stock/media` | Stock → Media (bar + NEW named button) | Entry existed only as a bar text link | "Media library" MiniAction added; H-02 rejection behaviour unchanged | Information scent |
| 21 | `/orders/keep` | NEW: Orders → "Kept & consolidation" (was: only via Paid-and-kept queue) | Title "Keep" hid the consolidation job; combine mechanic unexplained | Title → "Keep & consolidation"; combine helper ("…you pay the shipping once, not per order"); kept/explanations already present | Nielsen #2; information scent |
| 22 | `/shared/invoice/{id}` | Order detail → Shared view | No back control — an owner previewing on-device was stranded; back = navigation, not an owner control | "‹ Back" chip in brand band (status-bar inset fixed); zero edit/add/delete and zero cost fields added (C-01 c2/c3 hold) | Nielsen #3 (user control); constraint preserved |
| 23 | `/shared/catalog` | Shared invoice → shop name | Same stranding risk; sub-line gave no orientation | Same back chip + "prices and stock as the shop has them" sub-line; P5T OUT OF STOCK + Notify me untouched (C-02 c3/c6) | Nielsen #3, #2 |

**Reachability verdict:** all 23 routes reachable through visible UI from the 5-tab bar (previously routes 6, 17, 20, 21 depended on non-obvious paths — now all four have named entry points).

---

## Files changed

`HiFiComponents.kt` (header band sign-out chip), `MomoForm.kt` (`MiniAction`), `DashboardScreen.kt`, `MoreScreen.kt`, `OrdersScreen.kt`, `StockScreen.kt`, `OrderDetailScreen.kt`, `KeepConsolidationScreen.kt`, `PackingChecklistScreen.kt`, `InvoiceScreen.kt`, `StockAdjustScreen.kt`, `ProductFormScreen.kt`, `SharedViewsScreen.kt`, `MomoNavHost.kt` (wiring: sign-out×2, product search, show-all-orders, add product, shared backs).

Nothing removed; no seed figure, criterion wording or pinned message altered; no commit or push made.
