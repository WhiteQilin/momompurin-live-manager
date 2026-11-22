# Phase 1 Report — Core shell, tokens, data layer, fixtures

**Date:** 14 November 2026
**Build:** `app-debug.apk` 22.8 MB, `ph.edu.umak.momompurin`, minSdk 26, compileSdk 36
**Repository:** `IAS-ALGOCOM/momompurin-live-manager`
**Verified on:** `Medium_Phone_API_36.1` emulator, 1080×2400 @ 420dpi

---

## 1. Exit condition — MET

> "The app launches, signs in against the seeded owner, and the dashboard shows ORD-1042's real figures."

| Part | Status | Evidence |
|---|---|---|
| App launches | **Met** | `MainActivity` resumes, no crash, empty crash buffer |
| Dashboard renders ORD-1042 from seeded data | **Met** | Total ₱880.00, Paid ₱600.00, Balance ₱280.00, Session LIVE-0007, badge "Paid with Balance" |
| Signs in against the seeded owner | **Deferred — §5.1** | Sign-in is A-01, a Phase 2 story. The route is registered and the owner account is seeded in Settings. Phase 1 starts at the dashboard because that is what the exit condition asks to see. |

ORD-1042 as verified on device:

```
ORD-1042            Paid with Balance
Pia Santos
Total      Paid      Balance     Session
₱880.00    ₱600.00   ₱280.00     LIVE-0007
```

The figures are **computed, not printed**: `Order.total = orderSales + shippingFee` =
76000 + 12000 cents; `Order.balance = total − amountPaid`. The test
`ord1042_composition_is_mckp_plus_scwh_plus_freebie_plus_shipping` pins the composition
(MCKP ₱160 + SCWH ₱600 + freebie ₱0 + shipping ₱120 = ₱880).

---

## 2. What was built

### 2.1 Build system (was absent)

The repository had **no Gradle files at all** — only two Kotlin sources and a manifest, which
therefore could not compile. Added:

| File | Purpose |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` | AGP 8.9.2, Kotlin 2.0.21, Compose BOM 2024.10.01 |
| `gradle/libs.versions.toml` | Version catalogue |
| `gradle.properties` | Pins the JDK (§6.1) |
| `.gitignore` | `build/`, `.gradle/`, `local.properties`, APK/AAB |

### 2.2 Token layer — handoff §3, complete

`ui/theme/` holds **57 colour tokens**, 8 named type roles and the radius/spacing scale.

- `MomoColors.kt` — brand, ink, surface, accents, two status ramps, buttons
- `MomoTypography.kt` — Fredoka / Comfortaa / M PLUS 1 Code, bundled as variable fonts in
  `res/font/`, weight selected through `FontVariation`
- `MomoDimens.kt` — `MomoRadius` (card 16, input 12, chip 30, pill 999), `MomoSpace` (xs…xxl)
- `MomoFormat.kt` — money as `Long` cents with `format` / `plain` / `divideAtFullPrecision`

**Constraint verified mechanically:** 20 Kotlin files scanned, **0 hex literals and 0
`Color(...)` calls outside `ui/theme`**. One guarded exception: `res/values/colors.xml` carries
`#FFF0C2` for the pre-Compose window background, which the platform inflates before Kotlin runs.

### 2.3 Data layer — handoff §4, all four overrides applied

`data/entity/Entities.kt` — the 10 entities as `@Serializable` models (compile-time
serialization, no reflective parsing).

| Override | Applied as |
|---|---|
| 1. `STOCK_ADJUSTMENT.reason` OPTIONAL | `reason: String? = null`; nullable SQL column; a seeded row has a null reason. **Reverses F-07's mandatory rule.** |
| 2. `PRODUCT` parent-child variants | `parent_product_id`, `is_parent`, `isSellable`, `rollupAvailable()`; seeded parent `MPBOW` with 2 variants |
| 3. `PRODUCT_OPTION` managed lists | `ProductOption` + `OptionKind`; 15 seeded options; a test asserts every product's character/category exists in the list |
| 4. Three independent status tracks | Separate `payment_status`, `packing_status`, `shipping_status` columns and enums |

Two deliberate representation choices:

- **Money is `Long` cents, never `Double`.** ₱880.00 must reconcile exactly; binary floating
  point cannot promise that.
- **Status vocabularies are enums**, because §3.4 states the vocabulary is closed.

Derived rules are computations, never stored columns: `Product.available = stock − reserved −
shipped` (F-06 c3), `Order.balance`, `Order.isOverdue`, and the six queue counts.

### 2.4 Database schema — two new migrations

`0001` and `0002` are **preserved unmodified**: they are the artifact the IAS PoC and the
security audit analyse, and rewriting them would invalidate both.

- `0003_phase1_domain_schema.sql` — 11 tables, the four overrides, CHECK constraints to the
  closed vocabularies, a partial index on `payment_deadline` for the deadline heap
- `0004_phase1_rls_and_policies.sql` — RLS on all 11 tables, owner-only policies, two read-only
  customer projections that omit every internal cost column, and the startup assertion function

**Validation:** all four migrations parse against the real PostgreSQL grammar (pglast 8.8) —
104 statements, 0 errors. 37 structural assertions re-checked the four overrides, RLS on every
table, and the absence of `cost` / `unit_cost` / `net_earnings` / `pack_cost` / `srp` from the
customer projections: **37/37 pass**.

One defect found and fixed during this work: the first draft of `assert_anon_sees_nothing()`
tested `current_user = 'anon'`, but inside a `security definer` function `current_user` is the
function's *owner* — the assertion would have passed vacuously forever. It now performs
`set local role anon` around each count, which is what makes the number mean anything.

### 2.5 Seeding job — handoff §5, exact

`data/SeedData.kt`: 9 orders, 8 buyers, 12 products, 13 order lines, 4 invoices, 4 payments,
1 shipment, 15 options, 11 settings, 3 stock adjustments.

Every block is annotated with the section it came from, and **24 unit tests** pin the figures
(`SeedDataTest`), so a refactor that "tidies" a number fails a test rather than a testing session.

Seeds that carry a specific criterion:

- `CMEYE` exists with `activeInClaimPanel = false` — the row is real and claimable; it is only
  absent from the panel's opening cache, which is exactly what B-05 asks the tester to prove
- `P5T` exists with `stock_qty = 0` for the catalog's out-of-stock state
- `SHP-1042` keeps `JT 2612 3456 7890` **with its spaces**
- `ORD-1038` is `PAID_AND_KEPT` + `keepFlag`, and is excluded from the to-pack queue

### 2.6 Navigation — all 23 routes

`ui/nav/` registers every route from §2.1. `BottomTab` maps any route to its tab, so the bar
lights the correct item on detail screens too (A-02 c2).

Routes whose screen belongs to a later phase render `DeferredRouteScreen`, which **names the
owning phase and its criteria** rather than showing a blank screen that looks finished.

---

## 3. Verification performed

| Check | Result |
|---|---|
| `gradle :app:assembleDebug` | **BUILD SUCCESSFUL**, 22.8 MB APK |
| `gradle :app:testDebugUnitTest` | **24 tests run, 0 failed** |
| Install + launch on emulator | Resumes, no crash, empty crash buffer |
| Dashboard shows ORD-1042 figures | Confirmed on device |
| Dashboard arithmetic | ₱2,723.00 = sum of 7 balances, independently recomputed |
| Queue counts | 4 waiting / 1 overdue / 3 to pack / 1 kept / 0 dispatch — all match the seed |
| Literal colours outside theme | **0** across 20 Kotlin files |
| SQL migrations parse | 4/4 files, 104 statements, 0 errors |
| Schema invariants | 37/37 |
| All 5 bottom-bar tabs | Navigate, correct title, correct active ink |
| Card → detail → Back | Round trip works |

Three defects were found by looking at the screen and fixed:

1. The status bar overlapped the app-bar title (`enableEdgeToEdge` with no inset padding).
2. `StatusBadge` truncated "Waiting for Payment" to "Waiting for" — 121dp at 12sp is too
   narrow. Width is now a **minimum, never a cap**, because the label *is* the meaning under
   D-01 c8.
3. Detail routes had no back affordance, so arriving from a card tap trapped the user. All 12
   pushed routes now take an `onBack`.

---

## 4. Criteria status

### Passing now (base-layer criteria, ahead of their phase)
- D-01 c8 — status colour always paired with its text label
- F-06 c3 — `available = on hand − reserved − shipped`
- D-02 c9 — `Paid with Balance` is a distinct enum value and badge, never `Paid`
- N-01 / F4.8 — a parent is not sellable; variant stock rolls up
- N-03 / F2.8 / F2.9 — options are a managed list, validated against every product
- F-07 (as reversed) — a stock adjustment saves with no reason
- D-01 c10 — three independent status columns exist and cannot be collapsed

### Not yet passing — by phase
- **Phase 2:** A-01 c1–c13, A-02 c5–c6, J-01…J-08, N-08
- **Phase 3:** F-01…F-07, G-01…G-03, H-01…H-05, N-01…N-06, N-10, N-12
- **Phase 4:** B-01…B-10, E-01…E-06, N-11
- **Phase 5:** D-01…D-05, G-04…G-09, I-01…I-06, C-01, C-02

---

## 5. Deferred — named, not quietly omitted

| # | Deferred item | Why | Lands in |
|---|---|---|---|
| 5.1 | Sign-in screen; `startDestination` is `HOME` | The Phase 1 exit condition is the dashboard. `Routes.SIGN_IN` is registered and the owner account seeded; one line restores it | Phase 2 (A-01) |
| 5.2 | 18 of 23 screens are `DeferredRouteScreen` placeholders | Each names its phase and criteria. Building them now would put them ahead of the token/data review | Phases 2–5 |
| 5.3 | No live Supabase connection | Needs a project and a real anon key; `Supabase.ANON_KEY` is still the `REPLACE_WITH_PROJECT_ANON_KEY` placeholder it shipped with. The store is the local cache the remote layer will hydrate | Phase 2+ |
| 5.4 | The four algorithms are not yet ported to Kotlin | They are Python specifications with measured benchmarks. The port must *preserve* 97.0% top-1 / 0.3022 ms / 87.5% dropped-word — its own pass with its own tests | Phase 3 (search), 4 (FSM, heap) |
| 5.5 | No PDF/image export; no Room persistence | Behind Phase 5 and Phase 3 respectively. The store survives activity recreation, not process death | Phase 5, 3 |
| 5.6 | `0003`/`0004` not applied to a live database | No Postgres on this machine and no Supabase project reachable. Validated by parser + structural assertions only | Phase 2, with the project |
| 5.7 | Figma not consulted | Visual questions are settled by the handoff's token tables, transcribed verbatim. The MCP is available for the phase that builds real screens | Phase 2 |

---

## 6. Environment notes for the next thread

### 6.1 The build pins its own JDK
The JDK on `PATH` is 11; AGP 8.9 needs 17+. `gradle.properties` sets `org.gradle.java.home` to
Android Studio's bundled JBR (21). On another machine, override it.

### 6.2 Version pins that are not arbitrary
- **AGP 8.9.2 / compileSdk 36** — supabase-kt's auth module depends on `androidx.browser
  1.10.0`, which requires AGP ≥ 8.9.1 and compileSdk ≥ 36.
- **supabase-kt 3.1.4, not 3.9.0** — 3.9.0 pulls androidx lifecycle 2.11.0, which demands
  compileSdk **37** (not installed). 3.1.4 has no androidx leakage.
- Package rename during setup: `io.github.jan.supabase.gotrue.Auth` → `...auth.Auth`.

### 6.3 Two source-document discrepancies found

**a) `Test Data` §3 "Held value" does not reconcile with its own order rows.**

| Buyer | Stated | Sum of their §5.4 orders |
|---|---|---|
| @mikat | ₱760.00 | ₱800.00 (ORD-1045 ₱400 + ORD-1046 ₱400) |
| @pias | ₱720.00 | ₱880.00 (ORD-1042) |
| @beacruz | ₱440.00 | ₱440.00 ✓ |

₱760.00 is ORD-1042's **subtotal before shipping**, so the column looks derived from a
different basis. **The order rows win** — every screen shows them and the guide cross-checks
ORD-1042 at ₱880.00 throughout. The test
`held_value_column_disagrees_with_the_order_table_and_the_orders_win` pins the reconciliation
so it cannot be silently "fixed".

**b) Jessa Reyes (ORD-1040) and Anne Garcia (ORD-1038) appear in no buyer table.** They were
seeded as full customers, but their addresses are invented, not sourced — flagged for review.

### 6.4 A 97-field vs 87-field discrepancy
Handoff §4 calls `dictionary.json` "97 field definitions". The file contains **87** across the
10 entities. The models follow the file, plus what §4.2 and the seed set require
(`shipped_qty`, `is_electronic`, `pack_size`, and the three `order` commercial columns). Worth
correcting in the handoff so the next reader is not hunting for ten missing fields.

---

## 7. Next step

**Phase 2** — routes 1, 2, 3, 4: Sign In (A-01), the five-tab shell with live badge counts
(A-02), the six dashboard queues wired to real transitions (J-01…J-08), and Notifications
(N-08). Exit condition: A-01 through A-05 and J-01 through J-08 all pass.

Two things to settle first: the real Supabase anon key (§5.3), and whether the guide's
`@mikat` / `@pias` held values should be corrected in the workbook rather than in the app
(§6.3a).
