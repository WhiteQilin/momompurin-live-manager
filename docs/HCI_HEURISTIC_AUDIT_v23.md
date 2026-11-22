# HCI Heuristic Audit — Momompurin Online Ordering v2.3

**Audit date:** 22 Nov 2026 · **APK:** `Momompurin-Online-Ordering-v2.3.apk` (versionName 2.3, versionCode 23, targetSdk 35) · **Device:** Medium_Phone_API_36.1, Android 16, 1080×2400 @ 420dpi (412dp width), font scale 1.0 · **Method:** live `mobile-mcp` walkthrough (fresh `pm clear`) + static Kotlin source citation + mathematically computed WCAG 2.1 relative-luminance contrast ratios from `MomoColors.kt` / `Theme.kt`.

**Evidence directory:** `_tmp/hci_audit_v23/screens/` (34 screenshots + 2 crash logs) · **Contrast scripts:** `_tmp/hci_audit_v23/tools/wcag.py`, `wcag_asrendered.py`

Approved business rules treated as scope, NOT bugs (per skill §3): shared Sign In with zero role switch (A-01/A-12), dual Payment+Fulfillment badges (A-04/A-16), off-app pay-later invoice, non-reserving cart, manual overdue cancellation.

---

## 1. Executive Scorecard

| Module | Verdict | Blocking items |
|---|---|---|
| **Auth** (Sign In, Create Account, cascade, mismatch) | **Pass** | none (2 minor contrast items deferred) |
| **Owner** (Dashboard, Orders, Stock, Customers, More/Reports/Settings/Edit-History/Offline) | **Needs Polish** | offline banner duplicated + clipped under status bar; shipping-fee prefill swallows keystrokes; danger-badge contrast |
| **Customer** (Shop, PDP, Cart, Checkout, My Orders, Account) | **Fail** | **Sev 4 crash** — Change Password with empty Current password force-closes the app |
| **WCAG 2.1 AA color contrast** (58 token pairs computed) | **40 pass / 18 raw fail** | 9 failures are dead tokens (never rendered); **4 as-rendered text pairs fail AA**; 5 non-text boundary pairs fail 1.4.11 |

**Count of findings:** 1 × Sev 4 · 5 × Sev 3 · 6 × Sev 2 · 4 × Sev 1.

### Pillar summary

- **Nielsen 10:** H1/H3/H5/H6/H7/H9/H10 largely exemplary (inline errors under field, dialogs abortable with zero side effects, live cart badge, typo-tolerant search, privacy footer). H8 broken by the duplicated offline banner. **H1 compromised by F-17: the add-to-cart snackbar — the app's most frequent confirmation — renders below the bottom bar where the thumb occludes it.** H2 clean (no raw enums observed; invoice "Not yet issued" is plain language).
- **Ergonomics:** stepper buttons 40×34dp (Cart) / 44×44dp (PDP), top-bar back 44dp, bell 44dp — all under the 48dp floor; IME occlusion handled; numeric keyboards verified in code; lifecycle resilience passed (process kill preserved session + route).
- **Typography floors:** 12sp nav/badge floor and 14sp body/SKU floor honored in `MomoTypography.kt` (DataCaption 13sp→14sp fix documented at `MomoTypography.kt:143-155`).
- **Contrast math:** see §2.

---

## 2. WCAG 2.1 AA Contrast — exact mathematics

Method: linearised sRGB luminance `L = 0.2126R + 0.7152G + 0.0722B`, `CR = (Llight+0.05)/(Ldark+0.05)`. Floors: normal text ≥ 4.5:1 (1.4.3); large text & UI boundaries ≥ 3.0:1 (1.4.3/1.4.11). Full 58-pair run: `_tmp/hci_audit_v23/tools/wcag.py`.

### 2.1 Every `MomoColors.kt` foreground/background pair as designed (58 pairs)

| # | Pair | FG | BG | Ratio | Floor | Verdict |
|---|---|---|---|---:|---:|---|
| 1 | TextPrimary on SurfaceCanvas (body copy) | #2B140E | #FDF3E7 | 13.87 | 4.5 | PASS |
| 2 | TextPrimary on SurfaceCard (card body) | #2B140E | #FFFFFF | 15.62 | 4.5 | PASS |
| 3 | TextSecondary on SurfaceCanvas (metadata) | #5E4633 | #FDF3E7 | 6.20 | 4.5 | PASS |
| 4 | TextSecondary on SurfaceCard | #5E4633 | #FFFFFF | 7.14 | 4.5 | PASS |
| 5 | TextSecondary on SurfaceMuted | #5E4633 | #F5F0EB | 6.73 | 4.5 | PASS |
| 6 | TextLinkAndBrand on SurfaceCanvas | #A85200 | #FDF3E7 | 4.95 | 4.5 | PASS |
| 7 | TextLinkAndBrand on SurfaceCard | #A85200 | #FFFFFF | 5.65 | 4.5 | PASS |
| 8 | InkStrong on SurfaceCanvas | #2D2321 | #FDF3E7 | 13.28 | 4.5 | PASS |
| 9 | InkMuted on SurfaceCanvas | #6E5D5A | #FDF3E7 | 5.21 | 4.5 | PASS |
| 10 | InkMuted on SurfaceCard | #6E5D5A | #FFFFFF | 5.94 | 4.5 | PASS |
| 11 | TextDisabled on SurfaceCard (disabled label) | #A89E9B | #FFFFFF | 2.62 | 4.5 | FAIL (exempt-ish, see F-09) |
| 12 | ButtonDisabledContent on ButtonDisabledFill | #A89E9B | #EBE6DF | 2.11 | 4.5 | FAIL (inactive exemption, F-09) |
| 13 | TextPrimary on ButtonPrimaryFill (primary CTA) | #2B140E | #FFB600 | 9.88 | 4.5 | PASS |
| 14 | TextPrimary on BrandPrimary | #2B140E | #FED161 | 12.00 | 4.5 | PASS |
| 15 | InkInverse on BrandDeep | #FFFFFF | #6F2511 | 10.78 | 4.5 | PASS |
| 16 | InkInverse on BrandDarkest | #FFFFFF | #461204 | 15.57 | 4.5 | PASS |
| 17 | InkInverse on BrandSecondary1Shade1 | #FFFFFF | #FF9900 | 2.14 | 4.5 | FAIL — **dead token risk** (onSecondary never rendered as text; see F-16) |
| 18 | TextPrimary on ButtonPrimaryPressed | #2B140E | #E5A300 | 7.92 | 4.5 | PASS |
| 19 | SuccessInk on SuccessFill | #11683B | #DEF5EA | 5.98 | 4.5 | PASS |
| 20 | WarnInk on WarnFill | #7A4B00 | #FFF0C2 | 6.53 | 4.5 | PASS |
| 21 | **DangerInk on DangerFill (danger chip/badge)** | #DD3350 | #FFE9F2 | **3.90** | 4.5 | **FAIL → F-05** |
| 22 | InfoInk on InfoFill | #1A4B8C | #E0ECFF | 7.25 | 4.5 | PASS |
| 23 | NeutralInk on NeutralFill | #6E5D5A | #F5F0EB | 5.49 | 4.5 | PASS |
| 24 | SuccessText on SuccessSurface | #1F6A3D | #E4F2E2 | 5.68 | 4.5 | PASS |
| 25 | SuccessText on SurfaceCard | #1F6A3D | #FFFFFF | 6.59 | 4.5 | PASS |
| 26 | SuccessBase on SuccessSurface | #2E7D32 | #E4F2E2 | 4.42 | 4.5 | FAIL — **dead token (0 call sites)** |
| 27 | WarningText on WarningSurface | #8A4500 | #FFEFD1 | 6.32 | 4.5 | PASS |
| 28 | WarningText on SurfaceCard | #8A4500 | #FFFFFF | 7.16 | 4.5 | PASS |
| 29 | WarningBase on WarningSurface | #E67E22 | #FFEFD1 | 2.51 | 4.5 | FAIL — **dead token (0 call sites)** |
| 30 | LowStockInk on SurfaceCard | #9A4A00 | #FFFFFF | 6.26 | 4.5 | PASS |
| 31 | LowStockInk on SurfaceCanvas | #9A4A00 | #FDF3E7 | 5.71 | 4.5 | PASS |
| 32 | DangerBase on DangerSurface | #D32F2F | #FCE6E4 | 4.17 | 4.5 | FAIL — only non-text use (borders/dots/badge fill), 4.98:1 as boundary = PASS at 3.0 |
| 33 | DangerText on DangerSurface | #8A2430 | #FCE6E4 | 7.40 | 4.5 | PASS |
| 34 | DangerText on SurfaceCard | #8A2430 | #FFFFFF | 8.84 | 4.5 | PASS |
| 35 | InfoBase on InfoSurface | #1976D2 | #E3EDF6 | 3.88 | 4.5 | FAIL — **dead token (0 call sites)** |
| 36 | InfoText on InfoSurface | #1B4A7A | #E3EDF6 | 7.68 | 4.5 | PASS |
| 37 | InfoText on SurfaceCard | #1B4A7A | #FFFFFF | 9.10 | 4.5 | PASS |
| 38 | TextPrimary on CalloutTop (dashboard card) | #2B140E | #FFDB7E | 12.97 | 4.5 | PASS |
| 39 | TextPrimary on CalloutBottom | #2B140E | #FFD269 | 12.14 | 4.5 | PASS |
| 40 | TextPrimary on AlertCard | #2B140E | #FEE6A5 | 14.12 | 4.5 | PASS |
| 41 | TextSecondary on AlertCard | #5E4633 | #FEE6A5 | 7.12 | 4.5 | PASS |
| 42 | TextPrimary on AlertChip (93% white over AlertCard) | #2B140E | #FFFDF9 | 17.10 | 4.5 | PASS |
| 43 | NavActiveInk on SurfaceCard (active tab) | #9E1458 | #FFFFFF | 7.79 | 4.5 | PASS |
| 44 | NavActiveInk on SurfaceCanvas | #9E1458 | #FDF3E7 | 7.11 | 4.5 | PASS |
| 45 | InkMuted on SurfaceCard (inactive tab) | #6E5D5A | #FFFFFF | 6.22 | 4.5 | PASS |
| 46 | TextSecondary on SurfaceCard (inactive tab) | #5E4633 | #FFFFFF | 8.75 | 4.5 | PASS |
| 47 | BorderSubtle hairline vs SurfaceCard | #D8C8A0 | #FFFFFF | 1.65 | 3.0 | FAIL (1.4.11, see F-10) |
| 48 | BorderSubtle vs SurfaceCanvas | #D8C8A0 | #FDF3E7 | 1.51 | 3.0 | FAIL (1.4.11) |
| 49 | SurfaceBorder vs SurfaceCard (input border / switch track) | #E4E4DC | #FFFFFF | 1.28 | 3.0 | FAIL (1.4.11, F-10) |
| 50 | BorderPrimary vs SurfaceCard (input border, enabled) | #2B140E | #FFFFFF | 17.37 | 3.0 | PASS |
| 51 | AlertChipBorder vs AlertCard | #FF9C86 | #FEE6A5 | 1.65 | 3.0 | FAIL — **dead token (0 call sites)** |
| 52 | ButtonPrimaryFill vs SurfaceCanvas (CTA boundary) | #FFB600 | #FDF3E7 | 1.60 | 3.0 | FAIL (1.4.11 nuance → F-15, cosmetic) |
| 53 | AccentRed vs SurfaceCard | #FF7071 | #FFFFFF | 2.69 | 3.0 | FAIL — **dead token** |
| 54 | AccentPink vs SurfaceCard | #FF9ADD | #FFFFFF | 1.92 | 3.0 | FAIL — **dead token** |
| 55 | AccentBlue vs SurfaceCard | #61C2FE | #FFFFFF | 1.97 | 3.0 | FAIL — **dead token** |
| 56 | AccentAquamarine vs SurfaceCard | #72E2BB | #FFFFFF | 1.58 | 3.0 | FAIL — **dead token** |
| 57 | AccentPurple vs SurfaceCard | #B476F6 | #FFFFFF | 3.06 | 3.0 | PASS |
| 58 | QueueToPackAccent vs SurfaceCard | #FED570 | #FFFFFF | 1.40 | 3.0 | FAIL — **dead token** |

### 2.2 As-rendered pairs for tokens that actually paint (verified by grep of call sites)

Script: `_tmp/hci_audit_v23/tools/wcag_asrendered.py`.

| As-rendered pair | FG | BG | Ratio | Floor | Verdict |
|---|---|---|---:|---:|---|
| DangerInk inline-error text on SurfaceCard | #DD3350 | #FFFFFF | 4.50 | 4.5 | PASS (marginal) |
| **DangerInk inline-error text on SurfaceCanvas** | #DD3350 | #FDF3E7 | **4.11** | 4.5 | **FAIL → F-06** |
| DangerInk text on SurfaceMuted | #DD3350 | #F5F0EB | 3.98 | 4.5 | FAIL (rare surface) |
| DangerBase alert border vs SurfaceCard | #D32F2F | #FFFFFF | 4.98 | 3.0 | PASS |
| **BrandSecondary1Shade1 focus ring vs SurfaceCard** | #FF9900 | #FFFFFF | **2.14** | 3.0 | **FAIL → F-08** |
| BrandSecondary1Shade1 focus ring vs SurfaceCanvas | #FF9900 | #FDF3E7 | 1.95 | 3.0 | FAIL |
| NavActiveInk active tab on SurfaceCard | #9E1458 | #FFFFFF | 7.79 | 4.5 | PASS |

**Dead tokens (0 grep call sites — cannot fail at runtime, housekeeping only):** `SuccessBase`, `WarningBase`, `InfoBase`, `AccentRed`, `AccentPink`, `AccentBlue`, `AccentAquamarine`, `QueueToPackAccent`, `AlertChipBorder` (9 tokens; 5 of them would fail AA if ever promoted to text).

---

## 3. Screen-by-screen findings

Severity per Nielsen scale: 4 = Catastrophe (blocks task/corrupts state), 3 = Major, 2 = Minor, 1 = Cosmetic.

| ID | Module & Screen | Heuristic / Standard | Sev | Evidence | Concrete Fix |
|---|---|---|---|---|---|
| **F-01** | Customer · Account → Change Password | H9/Robustness — **app crash** | **4** | `_tmp/hci_audit_v23/screens/crash_changepassword_empty.log`; reproduced 2× (logcat 12:50:19 PID 10855, 12:53:33 PID 14400); `AccountOps.kt:318` → `AccountOps.kt:303` → `AccountOps.kt:219` → `AccountScreen.kt:370` | Tapping **Save changes** with an empty Current-password field throws uncaught `InvalidKeySpecException: Could not generate secret key` from `pbkdf2()` (empty `PBEKeySpec`) and the process dies. Guard in `changePassword`: `if (current.isBlank()) return ProfileResult.Failure(PASSWORD_MISMATCH)` **and** wrap `pbkdf2` body in `runCatching { … }.getOrDefault(ByteArray(0))` so any spec failure degrades to a verify-failure instead of a crash. |
| **F-02** | Owner · Stock list + Product Detail + Adjust Stock | H8 Aesthetic/Minimalist — duplicate banner | **3** | `owner_28_stock_offline_duplicate_banner.png`, `owner_29_product_detail_offline_duplicate_banner.png`; `MomoNavHost.kt:172` (global banner) **and** `StockScreen.kt:116,385,818` (per-screen banners) | Two identical "Offline — Read-Only" banners stack on every Stock screen. Delete the three local renderings in `StockScreen.kt` (the NavHost-level banner already covers every owner screen, as its own comment states: "owned here rather than per-screen so a screen cannot forget it"). |
| **F-03** | Owner · every screen under offline (More, Dashboard, Orders…) | H1 Visibility — banner clipped under system bar | **3** | `owner_27_offline_mode_on_more.png` + `_crop_banner.png` (clock/shield/WiFi/battery painted over "Writes are blocked until you turn…") | The NavHost banner sits at y=0 in an edge-to-edge window; only `MomoTopBar` consumes `WindowInsets.statusBars` (`Scaffolding.kt:69`). Give the banner `windowInsetsPadding(WindowInsets.statusBars)` (or move it below the top bar's inset band) so the status text is fully readable — it is the *reason* for disabled controls (A.7). |
| **F-04** | Owner · Order Detail → Send the invoice → Shipping fee | H5 Error Prevention — prefill swallows keystrokes, corrupts money | **3** | `owner_04_order_detail_top.png` → typed "50" → `owner_06_invoice_sent_waiting.png` shows Shipping fee **₱0.01** (from "0.0050"); `OrdersScreen.kt` invoice section | The field arrives prefilled with the text "0.00"; typing appends → "0.0050" → invoice issued with a ₱0.01 fee. Fix: render "0.00" as **placeholder** (`MomoField` supports it) or select-all on focus so the first keystroke replaces it. Money-field contamination is a major, repeatable error. |
| **F-05** | Owner + Customer · danger status chips ("Overdue", cancel buttons on tint) | 1.4.3 AA — DangerInk on DangerFill | **3** | computed 3.90:1 (pair 21); `owner_03_orders_list_filters.png` ORD-1045 "Overdue" chip | Darken `DangerInk` from `#DD3350` to ≈`#C22442` (≈5.4:1 on DangerFill, ≈5.9:1 on white, ≈5.0:1 on canvas) — one token, keeps the rose hue. |
| **F-06** | Auth/Checkout/Owner forms · inline error text under fields on cream canvas | 1.4.3 AA — DangerInk on SurfaceCanvas | **3** | computed 4.11:1; `auth_02_signin_empty_validation.png` ("Enter your email address."), `auth_05_confirm_mismatch_inline_error.png` | Same token fix as F-05 (one shade darker DangerInk clears card 4.50→~5.9 and canvas 4.11→~5.0). No layout change. |
| **F-07** | Owner + Customer · Cart & PDP quantity steppers | Touch target ≥48dp | **2** | `CartScreen.kt:358` = 40×34dp; `CustomerProductDetailScreen.kt:330` = 44×44dp; crops `_crop_stepper.png` | Stepper boxes are below the 48dp floor (34–44dp). Raise to 48dp height (width may stay if a 48dp minimum-touch-tap is added). Same applies to `MomoTopBar` back 44dp (`Scaffolding.kt:77`) and `NotificationBell` 44dp (`NotificationBell.kt:199`). |
| **F-08** | All text fields · focus indicator | 1.4.11 Non-text contrast | **2** | computed 2.14:1; `MomoForm.kt:150` focusedBorderColor = `BrandSecondary1Shade1` (#FF9900) | The focused field border is nearly invisible on white. Use `BorderPrimary` (#2B140E, 17.4:1) or `TextLinkAndBrand` (#A85200, ~3.9:1) for the focus ring; keep gold for fills. |
| **F-09** | Cascading dropdowns (Registration/Checkout/Account) · disabled instruction text; offline-disabled CTA labels | 1.4.3 inactive-component exemption + perceivability | **2** | computed 2.31–2.62:1; `auth_03_registration_initial.png` ("Select a province first" #A89E9B on #F5F0EB); `owner_31_disabled_cta_labels.png` | WCAG exempts disabled controls, but the instruction "Select a province first" is the *only* guidance in that state and measures 2.31:1. Darken `TextDisabled` to ≈`#8A7B77` (≥3:1 on muted) or swap the instruction to `TextSecondary` while keeping the field visually disabled. |
| **F-10** | Cards/inputs/switch · hairline boundaries | 1.4.11 boundary visibility | **2** | computed 1.28–1.65:1; `Scaffolding.kt:128` card border, `MomoForm.kt:222` unchecked input border, `MoreScreen.kt:133` switch track | Identification is currently carried by labels (so this is polish, not a blocker), but the switch *state* rides on a 1.28:1 track. Darken `SurfaceBorder` to ≈`#C9C9BE` (~1.9:1) and/or give the unchecked switch thumb ≥3:1 against the track. |
| **F-11** | Customer · Checkout → order confirmation → BACK | H1/H3 — stale screen | **2** | `customer_17_order_placed_ORD1052.png` → BACK → `customer_18_checkout_stale_after_order.png` (Checkout still shows the placed item; badge cleared) | After `Agree & Place Order`, pop Checkout from the back stack (or `popUpTo(Shop)`) so BACK cannot re-show a dead form. No data risk (cart empty), so Minor. |
| **F-12** | Auth · Registration · CTA vs gesture handle | Ergonomics — bottom inset | **2** | `auth_04_registration_mismatch_attempt.png` + `_crop_bottom.png` (white system handle overlays "Create account" before scrolling) | The scroll container lacks a `navigationBars` inset pad; the last CTA can sit under the gesture handle. Add `windowInsetsPadding(WindowInsets.navigationBars)` to the Registration/Sign-In root scroll (bottom bar screens already do this in `MomoBottomBar.kt:71`). |
| **F-17** | Customer · PDP / Shop · Add-to-cart snackbar position | H1 Visibility — confirmation rendered in the dead zone **below** the bottom bar | **3** | `customer_32_addtocart_snackbar_below_bar_USER.png` (user-captured, snackbar fully under the bar); `_tmp/hci_audit_v23/screens/_crop_snackbar_zone.png`; `MomoNavHost.kt:569-574`; `MomoMessages.kt:87-101` | The "Added to cart" snackbar renders at the very bottom of the window — *under* the bottom bar, in the gesture-handle strip — the worst possible spot: it sits where the thumb naturally rests (finger occludes it), touches the system gesture zone, and is visually disconnected from the action that triggered it. **Code contradiction:** `MomoMessages.kt:89-91` documents a `bottomPadding` parameter that "clears the bar… which the caller supplies", but the actual signature (`MomoMessages.kt:98-101`) has **no** `bottomPadding` parameter, and the sole caller (`MomoNavHost.kt:572-574`) passes none. The host is the *last child* of the outer `Column`, composed after `MomoBottomBar` (lines 534-567), so it lays out below the bar instead of above it — the comment at `MomoNavHost.kt:569` ("sits above the bottom navigation bar") is false for the shipped layout. **Fix (one re-order):** move the `MomoSnackbarHost` call *inside* the `Box(Modifier.weight(1f))` that wraps the `NavHost` (`MomoNavHost.kt:180`), aligned to `Alignment.BottomCenter`, and add `bottomPadding = MomoSpace.Sm` (or `WindowInsets.navigationBars` clearance when no bar shows) — restoring the layout the docs already describe. |
| **F-13** | Palette hygiene | Maintainability | **1** | grep: 0 call sites for `SuccessBase`, `WarningBase`, `InfoBase`, `AccentRed`, `AccentPink`, `AccentBlue`, `AccentAquamarine`, `QueueToPackAccent`, `AlertChipBorder` | Remove the 9 dead tokens (5 of them fail AA if ever promoted), or annotate them "decorative-only". |
| **F-14** | Sign In · a11y tree label merge | Cosmetic | **1** | stale uiautomator dump: `katrinajj254@gmkatrina@ail.com` (two EditTexts merged) | TalkBack ordering artifact of the dump, not a visual defect; verify with a real screen reader if desired. |
| **F-15** | Primary CTA on cream canvas | 1.4.11 nuance | **1** | computed 1.60:1 gold vs canvas | The gold fill boundary is below 3:1, but the button is identified by its dark 9.88:1 label; acceptable brand aesthetic. Optional: 1dp `BorderPrimary`-tinted edge. |
| **F-16** | `Theme.kt` onSecondary mapping | Latent risk | **1** | `Theme.kt:17-18` white-on-#FF9900 = 2.14:1 | No component currently renders white text on the orange secondary; if one ever does it fails AA. Map `onSecondary` to `BrandDeep` instead. |

### Verified passes (evidence-backed highlights)

| Check | Evidence |
|---|---|
| Inline validation directly beneath the offending field; dialogs stay open, text preserved | `auth_02_signin_empty_validation.png`; `auth_05_confirm_mismatch_inline_error.png` ("Passwords do not match." under Confirm field, crimson border, content intact) |
| Cascading Province→City→Barangay enables in order; disabled states explained; "Other (type manually)" fallback | `auth_03_registration_initial.png` → cascade screenshots; `PhAddressCascade.kt:100-156` |
| Numeric/Decimal/Phone/Email keyboards on the right fields | live numpad on Mobile/Price (`owner_15_add_product_price_numpad.png`); `StockScreen.kt:655,666,674` Decimal/Number; `RegistrationScreen.kt:156,167`; `PaymentScreen.kt:184`; `ReportsAndSettingsScreen.kt:342,352` |
| Typo-tolerant search ("chiikawa wiipes" → match) | `customer_02_shop_typo_search.png` (`TrigramProductMatcher`) |
| Scroll-fade affordance on both chip rows (40dp cream gradient) | `customer_03_shop_chips_scrolled.png` + `_crop_chipfade.png`; `ShopScreen.kt:77,285-290` |
| Variant PDP: colourway switch updates photo + SKU pill + price + live stock instantly; stepper capped at live availability | `customer_06/07_pdp_variant_*.png`; `CustomerProductDetailScreen.kt:72-96,256-270` |
| Live cart badge on bottom nav (4→2→1→cleared) and header cart icon | `customer_09_add_to_cart_badge_2.png`, `customer_12_cart_badge_1.png` |
| Empty cart with recovery CTA "Browse products" | `customer_11_cart_empty.png` |
| Checkout prefills recipient/mobile/address (recognition over recall); Ship-Now notice is a confirm dialog, cancelable | `customer_13/14/16_*.png` |
| Snackbar anchored above bottom bar, success/danger variants with icon (not color alone) | ⚠ superseded by **F-17**: the *styling* (icon, surface, border, `MomoMessages.kt:107-130`) is correct, but live capture (`customer_32_addtocart_snackbar_below_bar_USER.png`, user-supplied; `_crop_snackbar_zone.png`) shows the host rendering **below** the bar in the gesture strip — see F-17 |
| Invoice RA 10173 privacy footer, version line, monospace invoice number | `owner_09_invoice_privacy_footer.png`; `InvoiceRenderer.kt` |
| Customer read-only invoice honestly says "Not yet issued" instead of blank | `customer_24_invoice_not_yet_issued.png` |
| Offline mode blocks writes with reason + disabled controls; banner copy explains how to fix (H9) | `owner_28/30_*.png`; `NetworkStatus.kt:120-152` |
| Edit History audit-chain integrity badge pairs icon + text (not color alone) | `owner_26_edit_history_audit_badge.png` |
| Log Out dialogs (both roles) cancel with zero side effects | `owner_32_logout_dialog.png` → `owner_19_more_screen.png` unchanged; `customer_29_logout_dialog_customer.png` |
| Process kill / relaunch preserves session + route + badges | Owner re-launch returned to Dashboard with updated totals (`owner_16` path); Customer re-launch restored PDP (12:52 screenshot) |
| 12sp badge/nav floor, 14sp body/SKU floor, monospace SKUs/order numbers | `MomoTypography.kt:108-182`; "Customers" fits the 5-tab bar at 12sp with −0.3sp tracking (`MomoBottomBar.kt:88-93`) |
| No internal cost/margin/review data leaks on customer PDP (suggested retail price is owner-only) | `customer_06_pdp_variant_pink.png` vs `owner_11_product_detail.png` |

---

## 4. Must-Fix Before Monday (Sev 3–4 only)

1. **F-01 — Change Password crash (Sev 4).** Guard blank `current` in `changePassword` + `runCatching` around `pbkdf2`. ~5 lines, zero UI impact, removes the only defect that kills the app in front of a panel.
2. **F-02 — Delete the 3 per-screen offline banners in `StockScreen.kt`** (the NavHost one already covers them). Pure deletion.
3. **F-03 — Add `windowInsetsPadding(WindowInsets.statusBars)` to the NavHost offline banner.** One modifier.
4. **F-04 — Shipping fee: make "0.00" a placeholder instead of prefilled text** (or select-all-on-focus). Prevents ₱0.01 invoices.
5. **F-05/F-06 — Darken `DangerInk` #DD3350 → ≈#C22442.** One token fixes both the 3.90:1 badge and 4.11:1 inline-error failures.
6. **F-17 — Move `MomoSnackbarHost` into the content `Box`, bottom-aligned** (`MomoNavHost.kt:572` → inside the `Box(Modifier.weight(1f))` at line 180). One composable relocation; restores the documented "above the bottom bar" position so every success/error confirmation in the app (add-to-cart, invoice sent, save confirmations) lands where the user can actually see it instead of under their thumb.

## 5. Top 3 Quick Wins (highest gain, zero regression risk)

1. **F-03 + F-02 together** — both are one-liners; the offline story goes from "two banners, one unreadable" to a single, fully visible banner on every owner screen.
2. **F-05/F-06 DangerInk darkening** — one constant in `MomoColors.kt`; every danger chip, badge, inline error and destructive label across both modules clears AA at once.
3. **F-04 placeholder swap** — `MomoField` already supports `supporting`/placeholder styling; no state machine touched.

## 6. Do-Not-Touch List (defer to Future Enhancements slide)

- **F-07** touch targets (40–44dp steppers/back/bell) — safe to ship; log as enhancement (48dp steppers).
- **F-08** focus ring recolor — touch-first app; add when keyboard/switch-access support is on the roadmap.
- **F-09/F-10** disabled-state & hairline contrast — polish batch.
- **F-11** back-stack pop after order — behavior is safe, just inelegant; bundle with any Checkout refactor.
- **F-12** registration inset pad — cosmetic unless a 3-button-nav device is in the demo kit.
- **F-13..F-16** palette hygiene, a11y-dump artifact, `onSecondary` mapping — housekeeping.
- **Never destabilize:** shared sign-in (A-01/A-12), dual Payment+Fulfillment badges (A-04/A-16), pay-later invoice & hold rules, offline read-only semantics, the static-font typography system (`MomoTypography.kt` — recently fixed for OEM weight collapse), and the audit-chain Edit History.

## 7. Severity totals

| Severity | Count | IDs |
|---|---|---|
| 4 — Catastrophe | 1 | F-01 |
| 3 — Major | 5 | F-02, F-03, F-04, F-05/F-06, F-17 |
| 2 — Minor | 6 | F-07, F-08, F-09, F-10, F-11, F-12 |
| 1 — Cosmetic | 4 | F-13, F-14, F-15, F-16 |
