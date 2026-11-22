# Checklist of Recommended Algorithms — implementation status

**ALGOCOM PBL Milestone 1 · Group 8 · III-BCSAD · Momompurin Live Manager**
**22 November 2026 — updated to the shipped v2.3 Android app**

Every algorithm from the recommendation list, one row each, in three states:

- **IMPLEMENTED** — the algorithm exists as code in the repository, is runnable
  (`python demo/run_demo.py`), and is asserted against a baseline or fixtures.
- **BENCHMARKED** — the algorithm was measured in the Benchmarking Analysis
  (Colab notebook). All cells run.
- **OUT OF BENCHMARK SCOPE** — per the consultation and the follow-up message:
  the algorithm is implemented and stays in the app, but it is not presented as
  a core-algorithm benchmark subject.

The benchmarking analysis, per the consultation, concentrates on the **matching
engine** and the **comparison of matching algorithms** — the selection question
with a measurable answer — plus the tracking-validation feature.

## What the v2.3 pivot changed for this checklist

After client and SOFTENG validation, order entry moved from owner-typed live
claims to a **two-sided self-service app**: `Customer Shop` (A-13) and
`Owner Stock` (A-03). The algorithms did not change with the pivot; **where they
run did**. The rows below therefore cite the Kotlin that ships, not only the
Python reference the notebook measures:

| Algorithm | Python reference (benchmarked) | Kotlin that ships in v2.3 |
|---|---|---|
| Trigram Jaccard + inverted index | `algorithms/inverted_index.py` | `domain/search/TrigramProductMatcher.kt` (`TrigramText` + index), used by `data/SearchPipeline.kt`; called from `ui/screens/ShopScreen.kt` (A-13) and `ui/screens/StockScreen.kt` (A-03) |
| Prefix trie | `algorithms/trie.py` | `domain/search/TrieProductAutocomplete.kt`; `SearchPipeline` stage 1 |
| Tracking-number validation | `algorithms/tracking.py` | `data/CourierTrackingValidator.kt`; gates A-07 c8 In Transit |

The parity claim is enforced, not asserted: `app/src/test/java/ph/edu/umak/momompurin/V23SprintTest.kt`
carries `algocom_trigram_matcher_ranks_the_typo_top` (asserting `"chikwa wips"` → `CHW`) and
`algocom_trie_prefix_matches_the_linear_scan` (trie vs linear scan on five prefixes). The notebook
measures the Python reference; those tests pin the Kotlin port to it.

## Matching (typed search text → catalogue row; was: live claim → catalogue item)

| Status | Algorithm | Where it lives | Evidence |
|---|---|---|---|
| BENCHMARKED + IMPLEMENTED | **Trigram Jaccard over an inverted index** — the matching engine | `algorithms/inverted_index.py`; ships as `TrigramProductMatcher.kt` via `SearchPipeline` in A-13 (Customer Shop) and A-03 (Owner Stock) | `ALGOCOM_Benchmarking-v3.ipynb` §3: asserted identical to the full scan on all 767 queries; index footprint measured with `tracemalloc` in §3 |
| BENCHMARKED + IMPLEMENTED | **Comparison of matching algorithms** — exact match, inverted index + Jaccard, Jaccard full scan, index→Damerau hybrid, Jaro-Winkler, unclamped Damerau-Levenshtein | notebook §4; `demo/run_demo.py` | six matchers on one query set. The standings and the winning figure are printed by §4 and carried into §6 — **not restated here, because the notebook's run is the source of truth** (the earlier `maxd=4` cap was removed in v3) |
| IMPLEMENTED | Prefix trie for POS-style SKU lookup | `algorithms/trie.py`; `TrieProductAutocomplete.kt` | benchmark removed per consultation (no comparable alternative); the trie remains the app's SKU-search fast path, stage 1 of `SearchPipeline` |
| NOT ADOPTED | Machine-learning shorthand matcher (Levenshtein-based NLP) | — | rejected per consultation: ML would defeat the optimisation purpose; no training data exists for a 3-month-old shop; a scoring function the owner can read beats a model that cannot explain a wrong match |

## Real-time stock reservation

| Status | Algorithm | Where it lives | Evidence |
|---|---|---|---|
| OUT OF BENCHMARK SCOPE + IMPLEMENTED | **Finite State Machine with atomic stock invariants** | `algorithms/fsm.py`; enforced in the ops layer (`data/OrderOps.kt`, e.g. `ShipResult.Refused`) | per the follow-up message, FSM is excluded from core-algorithm benchmarking; the transition table ships and is asserted in the demo and in `V23SprintTest` |
| NOT ADOPTED | Logistic Regression / Random Forest cancellation-risk classifier | — | rejected: no labelled claim-history data exists yet (the shop is 3 months old); the FSM's Keep/Paid states plus the 24-hour notification already surface the behaviour the classifier would flag |

## Shipping dispatch

| Status | Algorithm | Where it lives | Evidence |
|---|---|---|---|
| IMPLEMENTED | **Regex tracking-number verification** — J&T Express, Lalamove/Grab, TikTok Shop formats, with Pickup exempt | `algorithms/tracking.py`; ships as `data/CourierTrackingValidator.kt` | notebook §5 mirrors the Kotlin regexes/normalisation and runs 29 fixtures (valid formats, wrong-length, malformed, empty, `None`, whitespace-wrapped, pickup). The Python wrapper additionally accepts the legacy `Lalamove` / `Grab Express` labels and `Customer Pickup`; those aliases are disclosed and do not alter the Kotlin route list. `V23SprintTest.tracking_*` asserts the same core shapes against the APK |
| IMPLEMENTED (Python reference) | Trigram Jaccard for packing-checklist item confirmation | `algorithms/inverted_index.py` | Python reference/demo only; no Kotlin call site in the v2.3 app was verified |

## Operational support (outside the benchmark scope)

| Status | Algorithm | Where it lives | Evidence |
|---|---|---|---|
| OUT OF BENCHMARK SCOPE + IMPLEMENTED (Python reference) | Min-heap deadline expiry (24-hour payment reminder sweep) | `algorithms/expiry.py` | Python reference/demo implementation; not a v2.3 Kotlin implementation claim. Per the consultation, not a benchmark subject |
| OUT OF SCOPE | Bcrypt / Argon2id password hashing | on-device: salted `PBKDF2WithHmacSHA256`, 16-byte random salt, 65,536 rounds (`data/AccountOps.kt`); production spec: Supabase GoTrue Argon2id (IAS Revision 2 §R.4) | excluded per the follow-up message. **The two are not interchanged silently:** the local KDF is PBKDF2 because it ships with `javax.crypto`, and the same criterion — per-account salt plus a deliberately slow iterated KDF — is met either way |
| OUT OF SCOPE | RBAC bitmasking / gate middleware | Supabase RLS owner-only policies (`supabase/migrations/0002_enable_rls_and_policies.sql`, `0004_phase1_rls_and_policies.sql`) | excluded per the follow-up message |
| OUT OF SCOPE | Cryptographic append-only event logging (SHA-256 chain) | **implemented** in `data/AuditChain.kt` — append-only log; `verify()` reports a re-hash mismatch, a broken link, and an unsealed row separately; the badge is surfaced in Edit History via `MomoStore.auditBadge()` → `ReportsAndSettingsScreen` | excluded from *benchmarking* per the follow-up message, but **shipped and verifiable** — the badge reads `Audit chain verified · N entries (SHA-256)` |

## Counts

**4 implemented algorithms in scope · 1 comparison (6 matchers) · 1 feature (tracking
validation) · 2 rejected on evidence · 4 implemented but out of benchmark scope** —
every line of the recommendation list is accounted for, with the consultation
decisions recorded next to each.

*Revised 22.11.2026:* the comparison is six matchers (the production indexed engine was
added alongside the five earlier rows), the `maxd=4` cap on Damerau-Levenshtein was
removed so that row is judged unclamped, and each algorithm now cites the Kotlin that
runs in the v2.3 APK rather than the Python reference alone.
