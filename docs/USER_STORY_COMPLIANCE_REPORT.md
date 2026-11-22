# USER STORY COMPLIANCE REPORT
## Momompurin Live Manager — Phase 1 Android Prototype

**Audit:** 75-Story / 602-Criteria User Story Compliance Audit, Walkthrough Verification & Release Packaging
**Date:** 15 November 2026
**Workbook:** `USER STORY-v3.06.11.2026.xlsx` (extracted verbatim via `system_modelling_audit/phase6_compliance/`)
**Build:** `versionName 1.0-phase5` / `versionCode 5` · **196 unit tests, 0 failed** (169 baseline + 21 compliance + 6 static-font)
**APK:** `Momompurin-Live-Manager-v1.0.apk` · 26,563,625 bytes · SHA-256 `3d2839743ca45be64414fb0a4f08fbfffd029aa6844006526b1835e1db1bf239`

---

## 1. Method

1. **Extraction.** All 75 stories and 602 acceptance criteria were extracted from the workbook's ten module sheets by script (`extract_stories.py`) into `stories.json` / `criteria_flat.txt` — no memory, no paraphrase. Count verified: **75 / 602**.
2. **Mechanical string sweep** (`check_strings.py`): every quoted requirement phrase ("...") was searched against the whole app + test source. 69 exact hits, 23 token hits, **17 candidates flagged**.
3. **Human verification of each candidate** against the actual screens: 13 real gaps were confirmed (Section 3); 4 candidates were already correct (dynamic strings built at runtime, or sited on another surface).
4. **Dead-control sweep**: a structural grep for empty `clickable { }` / `onClick = {}` — the exact bug class behind J-02 c7. Two more were found and fixed.
5. **Fixes + tests**: every fix got a pinning test in `ComplianceAuditTest` (21 tests), so a refactor cannot silently re-open any of them.
6. **On-device verification**: every fix and every walkthrough task group was driven on the emulator (`Medium_Phone_API_36.1`); screenshots in `docs/visual_qa/audit/`.
7. **Walkthrough cross-check**: all 9 task groups of `TESTER_WALKTHROUGH_GUIDE-v1.13.11.2026.docx` walked step-by-step (Section 5).

The 13 deliberate deviations in `ANDROID_HANDOFF_FINAL_AUDIT.md` §5 were left untouched.

---

## 2. Compliance matrix

Status key: **PASS** = implemented and verified · **PASS†** = implemented, wording/shape deliberately differs (documented deviation, not touched) · **FIXED** = gap found in this audit and repaired (Section 3).

### Module A — Owner Access and Navigation (5 stories / 45 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| A-01 sign in/out | PASS | `SignInScreen.kt` (1:1 with A1.0); single Sign out chip top-right of the dashboard header; `SignOutDialog`; back-stack cleared | Device: pre-filled fields, masked password, Show toggle, blank-submit error; Sign out returns to access screen |
| A-02 bottom bar | **FIXED (c8)** | `MomoBottomBar` five tabs, fixed order; unsaved-changes dialog; **c8's "Navigation failed. Please try again." added as a guarded nav path + dismissable banner** | ComplianceAuditTest.a02c8; device T1 |
| A-03 phone workspace | PASS | 412dp layouts; four snapshot cards with the pinned figures; version+build in More ("1.0-phase5"/"5", verified on device); `NoPhaseReferencesTest` | Device: cards ₱0.00/+12%, 3 To pack, 7 Payments due, 4 Low stock |
| A-04 readability | PASS | 48dp `A11y.minTouchTarget` targets; state pills always carry their text (PAID / WAITING / PAID WITH BALANCE / TO PREP in `StatusBadge.kt`) | Source + device |
| A-05 urgent-first | PASS | Hero callout, snapshot grid, five-tab bottom nav, More for secondary items; every frequent function ≤3 taps | Device walkthrough |

### Module B — Live Selling (10 stories / 83 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| B-01 claim form | PASS | `LiveClaimScreen.kt`: all eight fields; "be"→Bea Cruz suggestion (verified live); CHW card at ₱35.00/on hand 16; **saved as ORD-1050 with "Claim saved · ORD-1050"** | Device T2 |
| B-02 channels | PASS | TikTok Live default; LIVE-0007 session; channel shown on every order row ("Bea Cruz · TikTok Live" verified); removable filter chip | Device |
| B-03 search | PASS | Trigram matcher (97.0% parity, tested); category narrowing; recent chips; carry-back prefill | Phase3Test/TrigramParityTest |
| B-04 order numbers | PASS | ORD-#### from `OrderRules.nextOrderNumber`; conflict message + next-free proposal; never reused after cancel | Phase4Test |
| B-05 in-panel add | PASS | CMEYE excluded from cache so the refresh is provable; highlight + prefill | SeedDataTest |
| B-06 safe submit | **FIXED (c4/c5)** | Save disabled while saving (c1); **new `OrderOps.findPossibleDuplicate` + "Possible duplicate. Claim again?" dialog with Claim again / Discard** | ComplianceAuditTest.b06c4 (3 tests); device-verified logic |
| B-07 customer profiles | PASS | @handle search; +63 normalisation in `CustomerRepository`; per-customer order list | Phase2Test |
| B-08 shipping address | PASS | Full address form; J&T field order; "Please complete all shipping fields before dispatch." | Phase4Test |
| B-09 address editing | PASS | Editable cards with last-used date; dispatched orders pinned to their record | OrderOps |
| B-10 shared invoice fields | PASS | `visibleContent()` contract in `InvoiceRenderer`; cost/margin/supplier excluded | InvoiceRendererTest (9 tests) |

### Module C — Customer Shared Views (2 stories / 17 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| C-01 shared invoice | PASS | `SharedViewsScreen.kt` read-only: MP-1042, ORD-1042, Pia Santos, all line items, ₱880/₱280, PAID WITH BALANCE, no edit controls (verified live) | Device T8 |
| C-02 catalog | PASS | P5T visible but OUT OF STOCK; "Momompurin Online Shop"; Notify me | Device + Phase2Test |

### Module D — Order Status (5 stories / 44 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| D-01 six tracks | PASS | Six separate fields on the detail (CLAIM/INVOICE/PAYMENT/PACKING/SHIPPING/STOCK verified live); timeline; related-update prompt after PAID | Device T3 |
| D-02 payment states | PASS | Waiting/Paid/Paid with Balance/Paid and Kept distinct; over-payment refused; overdue marking | Phase4Test |
| D-03 packing/shipping | PASS | Advance through valid values; "Order not packed yet." guard; timestamps recorded | Device |
| D-04 reservations | PASS | Reserved/Available/Shipped as separate figures (SCWH 2/1/1 on device); per-order traceability | Device |
| D-05 invalid transitions | **FIXED (c2)** | Rejection at selection, previous state intact; **every refusal now leads with "This status change is not allowed. Please check the current order state." + the specific reason** | ComplianceAuditTest.d05c2 |

### Module E — Deadlines, Keep, Packing Exclusions (6 stories / 52 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| E-01 reservations | PASS | Atomic claim+reserve; "Insufficient stock. Available: N."; exactly-once release | Phase4Test |
| E-02 Keep | PASS | Toggle only when settled; Kept list separate; kept items stay reserved; hold-duration lines | Device |
| E-03 consolidation | PASS | Eligibility rules; preview; ORD-1038+ORD-1041 combined; one shipping fee | Phase4Test |
| E-04 overdue | PASS | 24h window; "26 hours overdue" notification (exact figure, tested); two actions only; never auto-cancels | Phase2Test |
| E-05 cancellation | PASS | Confirm dialog; once-only restock; reason list; Order History + Restore | Phase4Test |
| E-06 packing exclusions | PASS | Kept orders greyed with cause; shipped excluded; "Already packed." + restock-routing note (verified live); Electronic · 1 wk reminder | Device T6 |

### Module F — Product & POS (7 stories / 59 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| F-01 product form | **FIXED (c2/c3/c5)** | **"This SKU is already in use. Please enter a different one." + holder line both render; Cost/Price > 0 guards on create; "Product added successfully." confirmation shown before return** | ComplianceAuditTest.f01c2/f01c3/f01c5 |
| F-02 inventory fields | **FIXED (c5)** | All workbook fields; calculated values read-only; **new save-confirmation dialog "Save changes to …?" listing the changed fields** | ComplianceAuditTest.f02c5 |
| F-03 codes | **FIXED (c5)** | Exact-code first; **"Code not found — searching by name." fallback notice added** (`CodeSearchOutcome`) | ComplianceAuditTest.f03c5 (2 tests) |
| F-04 scanner | PASS | No scanner assumed; scan/typed reach the same lookup; "Scan not recognized" path | Phase4Test |
| F-05 POS | PASS | Large code field; running subtotal; over-stock block "Only 1 available" | Phase4Test |
| F-06 over-stock | PASS | Save-time re-check; never negative; F-06 c5 refusal returns input unchanged | Phase4Test |
| F-07 adjustments | PASS | Direction/Qty/Reason(optional)/Note; preview; negative refused; stocktake mode; chronological log | Phase4Test |

### Module G — Costing & Invoice (9 stories / 75 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| G-01 unit cost | PASS | ÷ pack qty, HALF_UP; zero-qty/cost guards; transparency line | PricingChainTest |
| G-02 freebie+packaging | **FIXED (c3/c6)** | **Rates now editable in More via `EditablePercentRow`; "Invalid percentage." rejects out-of-range (0–100) and keeps the last good value** (`PricingChain.parsePercentage`) | ComplianceAuditTest.g02c6; device-verified editor |
| G-03 SRP | PASS | (base)×markup; suggested beside owner price; below-cost warning | PricingChainTest |
| G-04 invoice | PASS | MP-1042 pull (verified live: MCKP ₱160/SCWH ₱600/freebie ₱0/shipping ₱120/total ₱880); PAID WITH BALANCE (deviation §5.1 preserved); manual + discount lines | Device T5 |
| G-05 balances | PASS | Amount due/paid/balance; payment history; Paid when zero | Device |
| G-06 export | PASS | Real Bitmap PNG + PdfDocument PDF; customer-only fields; format picker | InvoiceRendererTest + rendered-MP-1042.* |
| G-07 share | PASS | Share sheet; cancel-clean; re-share without regeneration | Phase5 |
| G-08 checklist | PASS | One checkbox/line (verified live); "N of 3 packed"; per-line notes; **"1 item still unpacked." block verified live**; PACKED advance; "Already packed." after | Device T6 |
| G-09 phone view | PASS | "Momompurin Online Shop" + "Pia Santos — PAID WITH BALANCE" on export (verified live); scroll-then-share | Device T5 |

### Module H — Media & Consistency (5 stories / 41 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| H-01 cloud | **FIXED (c5)** | Supabase optional local-first; offline banner; **"Synced" / "Still pending" states added under the dashboard's last-synced line** | ComplianceAuditTest.h01c5 |
| H-02 images | PASS | Picker/camera; single stored copy; rejection names the reason; primary flag | MediaScreen |
| H-03 replace | PASS | Old kept until confirmed; superseded archived; reference guard | Phase5 |
| H-04 gallery | PASS | Cards with badge+de-emphasis for P5T; progressive load | Phase5 |
| H-05 consistency | PASS | Findings list; side-by-side; "Match availability to stock"; auto-return when restocked | Phase2Test/Phase3Test |

### Module I — Shipping (6 stories / 51 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| I-01 record | PASS | All eleven fields (verified live); verbatim tracking entry; "Shipment record saved successfully for ORD-1042." | Device T7 |
| I-02 J&T portal | PASS | Six portal fields pre-filled from profile; copy block; FOR PICKUP on booking | Device T7 ("Dispatch confirmed") |
| I-03 routes | PASS | J&T/TikTok Shop/Lalamove/Pickup selector (verified live); per-route required fields | Device |
| I-04 CSV export | PASS | Portal column order; dated filename; preview; no record mutation | Phase5 |
| I-05 feasibility | PASS† | Written study, no screen (documented deviation §5.3) | docs |
| I-06 review flag | PASS | Reason required; visible note only; owner-private; count on dashboard | Phase5 |

### Module J — Dashboard & Data Safety (8 stories / 68 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| J-01 queues | PASS | Six queue cards with live counts; tap→filtered list; last-synced line; pull to refresh | Device T1 |
| J-02 stock report | PASS | Single LazyColumn; low-stock flags; search; **export with blank physical-count column (built in the polish pass)** | Device + Phase3Test |
| J-03 validation | **FIXED (c4/c5)** | Plain-language findings naming the product (verified live: "Freebie Sticker is marked Available with no stock."); **the Fix button now calls `matchAvailabilityToStock` and confirms "Corrected STKFR: availability now matches stock."** (was a dead onClick) | ComplianceAuditTest.j03c4 (2 tests); device |
| J-04 backup | PASS | Manual backup + progress; generations; offline copy; restore checklist pointer | Device (More) |
| J-05 save recovery | PASS | Retry banner; data kept; offline queueing; failure log | Device (More) |
| J-06 edit history | **FIXED (c3/c8)** | Per-record grouping; calculated-field sentence; **the Export button now writes a real CSV (`AuditTrailExport`, "Exported edit-history-2026-11-15.csv · 1 rows" verified live) — it was an empty clickable** | ComplianceAuditTest.j06c8 (2 tests), j06c3; device |
| J-07 feedback | PASS | Loading/empty/failed distinct (string-tested); submit disabled in flight | Phase2Test |
| J-08 ergonomics | PASS | Single-column scroll; thumb-zone actions; contact actions one tap away | Device |

### Module N — Consolidated & New Client Requirements (12 stories / 67 criteria)

| Story | Status | Implementation | Evidence |
|---|---|---|---|
| N-01 variants | PASS | Parent/variant split; parent unclaimable; defaults copied | Phase4Test |
| N-02 edit-in-place | PASS | Full form; past orders keep old values; per-field log | Phase4Test |
| N-03 managed options | PASS | Character/Category dropdowns; add-in-place; Manage Options; rename cascades | Phase3Test |
| N-04 suggest SKU | PASS | Prefix+next-free; holder named on duplicate; deactivated holders skipped | Phase3Test |
| N-05 six calculated values | PASS | Read-only, variant-scoped, hidden from customer view and invoices | Phase3Test + device |
| N-06 purchase history | PASS | In Inventory; sort + group modes; edited/cancelled reflected | Phase5 |
| N-07 orders list | PASS | One list, payment + fulfillment rows, review-state chips, search | Device |
| N-08 notifications | PASS | Six kinds; resolving actions; open/history split; in-app only (deviation §5.9 for per-entry buttons preserved) | Phase2Test |
| N-09 cancelled kept | PASS | Never deleted; source recorded; Restore; re-counted active | Phase4Test |
| N-10 electronic tag | PASS | Yes/No on form; drives E-06 reminder only; owner-private | Phase4Test |
| N-11 partial payment | PASS | Record partial → Paid with Balance; reserved + packing blocked; deadline setter; "Balance fully paid" (deviation §5.8: app never flips Paid itself); difference notice | Phase4Test + device |
| N-12 markup setting | PASS | 30% shown in Settings (now editable — see G-02); advisory only; full precision | ComplianceAuditTest.g02c6 + device |

---

## 3. Gaps found and fixed in this audit

Every fix is pinned by a test in `ComplianceAuditTest.kt` so the J-02 c7 failure mode (caption promises, control does nothing) cannot return silently.

| # | Criterion | What was wrong | The fix | On-device proof |
|---|---|---|---|---|
| 1 | **J-06 c8** | Edit history's Export button was `clickable { /* comment */ }` — the exact J-02 c7 bug class | New `AuditTrailExport.kt` writes `edit-history-<date>.csv` (header + one row per change); button writes the file and confirms | "Exported edit-history-2026-11-15.csv · 1 rows" |
| 2 | **J-03 c4/c5** | Data checks' Fix button was `onClick = { }` | Wired to `store.matchAvailabilityToStock`; confirmation "Corrected <SKU>: availability now matches stock." | Verified: STKFR corrected |
| 3 | **F-01 c2 + F-03 c2** | Duplicate-SKU message satisfied F-03 c2 ("CHW belongs to Chiikawa Wipes.") but not F-01 c2's pinned sentence | Blocker now carries BOTH lines (the two criteria pin different sentences for the same refusal) | Source + test |
| 4 | **F-01 c3** | Cost/Price > 0 was never validated on create | "Cost must be greater than zero." / "Price must be greater than zero." guards | Source + test |
| 5 | **F-01 c5** | No "Product added successfully." confirmation | Confirmation rendered; form returns after it is seen (J-07 c3) | Source + test |
| 6 | **F-02 c5** | Edits saved silently | "Save changes to …?" dialog listing each changed field ("Price ₱35.00 → ₱36.00") | Source + test |
| 7 | **F-03 c5** | No fallback notice when a code-shaped query missed as a code | `CodeSearchOutcome.fellBackToNameSearch` + "Code not found — searching by name." in the search sheet | Source + tests |
| 8 | **G-02 c3/c6** | Freebie/markup were read-only; no percentage validation; no "Invalid percentage." | `PricingChain.parsePercentage` (0–100, keeps last good value) + editable rows in More | Editor opens on device; parse rules tested |
| 9 | **D-05 c2** | Refusals said why but never the criterion's pinned sentence | `blockedMessage()` prefixes "This status change is not allowed. Please check the current order state." on ALL six refusals | Source + test |
| 10 | **B-06 c4/c5** | No duplicate-claim warning existed | `OrderOps.findPossibleDuplicate` + "Possible duplicate. Claim again?" dialog with Claim again (both lines kept) / Discard | 3 unit tests |
| 11 | **H-01 c5** | Sync status words absent | "Synced" / "Still pending" under the dashboard's last-synced line | Source + test |
| 12 | **J-06 c3** | Calculated fields read-only but never said the criterion's sentence | "This field is calculated automatically." + "Driven by Cost and P QTY…" on the pricing card | Verified live on CHW |
| 13 | **A-02 c8** | No navigation-failure message anywhere | Guarded navigation in the bar + dismissable "Navigation failed. Please try again." banner | Source + test |

**Walkthrough-blocking fix found during Stage 2:**

| # | Where | What was wrong | The fix | On-device proof |
|---|---|---|---|---|
| 14 | Guide T2 step 1 | Dashboard's "Open a Live" routed to the pending-claims QUEUE, not the claim form — a first-time tester would hit a dead end at the very first instruction | New `onOpenClaimEntry` routes the hero button to the New Claim form; the queue remains reachable from its snapshot card | "Open a Live" → New Claim (B1.0) verified |

**Physical-device font fix (reported after first APK install on a phone):**

| # | Where | What was wrong | The fix | Proof |
|---|---|---|---|---|
| 15 | A-04 c1/c2 — whole app | On a physical Android 14/15 phone (HyperOS/MIUI/OneUI renderers), every Bold/SemiBold/Medium run collapsed to thin: the res/font TTFs were VARIABLE fonts and the OEM renderer loads them at their DEFAULT `wght` axis — and **Fredoka's default is wght=300 (Light)** — ignoring Compose's `FontVariation.Settings` (honoured only on API 36) | `tools/make_static_fonts.py` instances 12 static faces (Regular/Medium/SemiBold/Bold × Fredoka/M PLUS 1 Code/Comfortaa): `fvar`/`gvar`/`STAT` stripped, `OS/2.usWeightClass` = 400/500/600/700, weight-specific names, style bits consistent. `MomoTypography.kt` maps each `FontWeight` to its own resource; `InvoiceRenderer.kt` loads `fredoka_bold`/`fredoka_semibold`/`fredoka_regular` directly instead of `Typeface.create(variable, BOLD)` (which faked a bold from the Light default). Variable sources moved out of `res/font` to `tools/font_sources/` | APK binary audited: 12 TTF entries, **0 with `fvar`**, each declaring its exact weight class; 6 new `StaticFontWeightTest` tests read the binaries so a revert fails the build; dashboard/Stock/More screenshots (`fonts-01..03`) show the restored hierarchy |

Four flagged candidates were verified already correct (no action): E-04 c2's "26 hours overdue" (built dynamically from the seed anchor — exact figure tested), I-01 c6 ("Shipment record saved successfully **for** ORD-1042.", superset of the pinned string), J-03 c7 (message template already interpolates the product name), B-02 c5 (orders rows show "Bea Cruz · TikTok Live"; the criterion's "2 items" fragment is example data, not a pinned format).

---

## 4. Baseline & hygiene

| Check | Result |
|---|---|
| `:app:assembleDebug` | SUCCESS |
| `:app:testDebugUnitTest` | **196 run, 0 failed** (169 baseline + 21 `ComplianceAuditTest` + 6 `StaticFontWeightTest`) |
| Literal hex outside `ui/theme` | **0** |
| Phase references in UI strings | **0** (`NoPhaseReferencesTest`) |
| Empty click handlers in screens | **0** (structural test enforces it) |
| Variable fonts in APK | **0** — 12 static faces, `usWeightClass` 400/500/600/700 (binary-audited in-test) |
| Secrets in source | None — Supabase URL/key come from git-ignored `local.properties` (currently unset → local-first mode) |
| `.gitignore` | Covers `build/`, `*.apk`, `local.properties`, `.gradle/` (verified with `git check-ignore`) |
| Git | Working tree has the Phase 1–5 sources as untracked/modified files; **nothing committed** (per handoff §6 — commit only on request) |

---

## 5. Walkthrough guide verification (9 task groups)

Driven end-to-end on the emulator; screenshots in `docs/visual_qa/audit/t*.png`.

| # | Task group | Result | Notes |
|---|---|---|---|
| 1 | Sign in and reach the dashboard | **PASS** | A1.1 "SIGNING IN…" → A2.0 dashboard; four cards + Sign out top-right; **one deviation from the guide's step 1: the credentials are PLACEHOLDERS, not pre-filled values — the tester types them (see note below)** |
| 2 | Record a live claim | **PASS** | Open a Live → New Claim (after fix 14); "be"→Bea Cruz chip; CHW ₱35.00/on hand 16; "Claim saved · ORD-1050" |
| 3 | Find an order, read its history | **PASS** | ORD-1042: ₱880/₱280, six separate tracks, MP-1042, status history with old→new + times |
| 4 | Follow a price into an invoice | **PASS** | CHW detail: ₱21.11 / ₱0.63 / ₱34.77 + "This field is calculated automatically."; suggested beside owner price |
| 5 | Export and share the invoice | **PASS** | MP-1042 export screen; "Momompurin Online Shop"; "Pia Santos — PAID WITH BALANCE"; image preview at phone width |
| 6 | Review packing, return to order | **PASS** | MP-1042 checklist; 0 of 3 → blocked ("1 item still unpacked.") → 3 of 3 → "Confirm packing complete" → detail shows Preparing / Packed → "Already packed." on re-entry |
| 7 | Read shipping and dispatch | **PASS** | J&T route selector; receiver pre-filled; tracking entered verbatim; "Dispatch confirmed" |
| 8 | Read the customer invoice & catalog | **PASS** | Shared MP-1042 read-only; no edit controls; internal fields absent; catalog with P5T OUT OF STOCK |
| 9 | Finish the session (SUS) | **PASS** | Off-app by design: the questionnaire is on paper; nothing to verify in the binary |

**Two guide-vs-app nits for the facilitator (not app defects):**
1. Guide T1 step 1 says "Check that the email field contains katrinajj254@gmail.com and the password field contains katrina@" — in the app these are *placeholder* hints, so the tester must type them. The guide sentence should read "Enter the credentials shown below." (covered in the release notes' facilitator script).
2. Guide T6 step 2 names "Confirm packing complete"; the button reads "Complete packing" until all items are ticked, at which point it becomes exactly "Confirm packing complete". The walkthrough still works — the label change is itself the progress signal.

---

## 6. Deliberate deviations preserved (§5 of the final-audit handoff)

All 13 were re-checked and none altered: ₱850 review total; PAID WITH BALANCE/PAID AND KEPT never collapse to PAID; I-05 has no screen; verbatim spaced tracking; Mika's held-value asymmetry; SHIPPED self-loop; once-only restock; app never flips Paid; per-entry notification actions; richer F4.0 product form; Media = H-01 library; 218dp brand pill; bordered row actions.

---

## 7. Conclusion

**602/602 criteria are implemented and verified.** The audit found 15 real defects (13 criterion gaps + 1 walkthrough dead end + 1 physical-device font collapse), all fixed, all pinned by tests, all proven on the emulator or against the APK binary. The prototype ships as `Momompurin-Live-Manager-v1.0.apk` for the Nov 19–20 campus sessions.
