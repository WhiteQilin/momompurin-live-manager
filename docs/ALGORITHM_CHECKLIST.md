# Checklist of Recommended Algorithms — implementation status

**ALGOCOM PBL Milestone 1 · Group 8 · III-BCSAD · Momompurin Live Manager**
**10 November 2026, after the 07 November consultation with Prof. Nomar B. Maestro**

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

## Matching (live claim → catalogue item)

| Status | Algorithm | Where it lives | Evidence |
|---|---|---|---|
| BENCHMARKED + IMPLEMENTED | **Trigram Jaccard over an inverted index** — the matching engine | `algorithms/inverted_index.py` | 97.0% top-1 on 767 real catalogue rows; index prunes 55.4% of candidates; asserted identical to full scan |
| BENCHMARKED + IMPLEMENTED | **Comparison of matching algorithms** — Jaro-Winkler, Damerau-Levenshtein (full scan), index→Damerau hybrid, exact match, vs trigram Jaccard | notebook §4, `demo/run_demo.py` §5 | five matchers on one query set: Jaccard 97.0% wins on accuracy; JW 93.5%; hybrid 95.4%; Damerau 76.0%; accuracy decides, speed breaks ties |
| IMPLEMENTED | Prefix trie for POS-style SKU lookup | `algorithms/trie.py` | benchmark removed per consultation (no comparable alternative); the trie remains the app's SKU-search fast path |
| NOT ADOPTED | Machine-learning shorthand matcher (Levenshtein-based NLP) | — | rejected per consultation: ML would defeat the optimisation purpose; no training data exists for a 3-month-old shop; a scoring function the owner can read beats a model that cannot explain a wrong match |

## Real-time stock reservation

| Status | Algorithm | Where it lives | Evidence |
|---|---|---|---|
| OUT OF BENCHMARK SCOPE + IMPLEMENTED | **Finite State Machine with atomic stock invariants** | `algorithms/fsm.py` | per the follow-up message, FSM is excluded from core-algorithm benchmarking; the transition table (7 states, 9 legal moves) ships and is asserted in the demo |
| NOT ADOPTED | Logistic Regression / Random Forest cancellation-risk classifier | — | rejected: no labelled claim-history data exists yet (the shop is 3 months old); the FSM's Keep/Paid states plus the 24-hour notification already surface the behaviour the classifier would flag |

## Shipping dispatch

| Status | Algorithm | Where it lives | Evidence |
|---|---|---|---|
| IMPLEMENTED | **Regex tracking-number verification** — J&T Express, Lalamove, Grab Express, TikTok Shop formats | `algorithms/tracking.py` | 11/11 fixtures pass (accepts valid J&T/TikTok/Lalamove/Grab numbers, rejects wrong-length and malformed ones); runs at entry time, before dispatch |
| IMPLEMENTED | Trigram Jaccard for packing-checklist item confirmation | `algorithms/inverted_index.py` (reused) | same engine as live matching, applied to checklist line verification |

## Operational support (outside the benchmark scope)

| Status | Algorithm | Where it lives | Evidence |
|---|---|---|---|
| OUT OF BENCHMARK SCOPE + IMPLEMENTED | Min-heap deadline expiry (24-hour payment reminder sweep) | `algorithms/expiry.py` | implemented and asserted; per the consultation, not a benchmark subject |
| OUT OF SCOPE | Bcrypt / Argon2id password hashing | delegated to Supabase GoTrue; parameters recorded in IAS Revision 2 §R.4 | excluded per the follow-up message |
| OUT OF SCOPE | RBAC bitmasking / gate middleware | Supabase RLS owner-only policies (equivalent control) | excluded per the follow-up message |
| OUT OF SCOPE | Cryptographic append-only event logging (SHA-256 chain) | specified in IAS Revision 2 §R.5 | excluded per the follow-up message |

## Counts

**4 implemented algorithms in scope · 1 comparison (5 matchers) · 1 feature (tracking
validation) · 2 rejected on evidence · 4 implemented but out of benchmark scope** —
every line of the recommendation list is accounted for, with the consultation
decisions recorded next to each.
