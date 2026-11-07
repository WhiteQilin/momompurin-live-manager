# Momompurin Live Manager — implementation repository

Phase 1 Android application for Momompurin Online Shop, a sole-proprietorship
live-commerce business (Group 8, III-BCSAD, University of Makati). This
repository is the codebase referenced by the IAS and ALGOCOM PBL Milestone 1
submissions; the graded documents themselves live in the group's submission
folder.

Phase 1 is design-stage software: the algorithm layer, the data layer and the
security controls specified by the two PBL milestones are implemented here and
runnable, while the full application UI remains in the Figma mid-fidelity
prototype.

## What is here

| Path | Contents |
|---|---|
| `algorithms/` | The four core algorithm implementations (see below) |
| `algorithms/benchmark/` | The benchmark harness that produced the figures in the ALGOCOM report |
| `demo/` | `python demo/run_demo.py` — runs all four algorithms on the client catalogue, asserts each against its naive baseline, and ranks five matching algorithms on one query set |
| `app/` | Kotlin sources for the data/session layer, plus the manifest controls |
| `supabase/migrations/` | Shipped schema (`0001`) and the hardening migration (`0002`) |
| `poc/` | Runnable proof of concept of the RLS defect and its fix: `node poc/run_poc.mjs` |
| `security/` | Gitleaks policy and the pre-commit guard |
| `.github/workflows/security.yml` | CI gates: secret scan, RLS assertion, SAST, build |
| `threat-model/` | OWASP Threat Dragon v2 model, Level 0 and Level 1 |
| `docs/` | Privacy Policy, Terms and Conditions, Security Controls Checklist |

## The four core algorithms (ALGOCOM)

Each is extracted into its own module, paired with the naive baseline, and
asserted to agree with it — the same correctness rule the benchmark harness
enforces. Run them all with `python demo/run_demo.py` (standard library only).

| Algorithm | Module | Role in the app | Benchmark result (767-row real catalogue) |
|---|---|---|---|
| Prefix trie | `algorithms/trie.py` | POS-style SKU autocomplete while the owner types | 121x faster than linear prefix scan |
| Trigram inverted index + Jaccard | `algorithms/inverted_index.py` | Maps a messy live-chat claim ("mine melody pouch po") to the catalogue item | 97.0% top-1, 1.7x vs full scan |
| Finite state machine | `algorithms/fsm.py` | Validates stock transitions (claim/pay/pack/dispatch/cancel) atomically | 3.5x vs if/elif chain, all paths agree |
| Min-heap deadline expiry | `algorithms/expiry.py` | Sweeps lapsed 24-hour payment deadlines for auto-cancel | 63.5x vs linear rescan |

Per Prof. Nomar's instruction, the security plumbing (Argon2id hashing, JSON
serialisation, checksums) is NOT claimed as core algorithm work; the four
above are the algorithms the app actually leans on.

## The finding this repository documents (IAS)

The application talks to Supabase directly, so the anon key is compiled into
the APK — that is how Supabase is designed and the key cannot be kept secret.
Safety rests entirely on Row Level Security.

The shipped schema (`0001`) granted `SELECT` on every table to `anon` and
never enabled RLS. Any person holding the APK could read the full customer
table, order history and supplier cost list with a plain HTTPS GET.

`poc/run_poc.mjs` reproduces this against a real PostgreSQL instance, applies
the hardening migration, and re-runs the identical request:

```
$ node poc/run_poc.mjs
  Before  : anon read 8 customers, 13 orders, 6 inventory rows
  After   : anon read 0 rows
  Owner   : 8 rows (unchanged)
```

## Running the proof of concept

Requires Node 20 or later. The harness starts its own PostgreSQL server;
nothing needs to be installed or running first.

```bash
cd poc
npm install
node run_poc.mjs
```

## Running the algorithm demo

Requires Python 3.10+; no third-party packages.

```bash
python demo/run_demo.py
```

`algorithms/benchmark/catalogue.csv` carries the client catalogue the benchmarks
run on: 767 rows, SKU and item name only. Those are the two fields the retrieval
engine reads and the two the shop already displays publicly in its live
sessions. Cost, price, stock and every customer field are excluded, so the
client's workbook itself is not in this repository; the provenance note at the
top of `algorithms/benchmark/bench_algocom_pbl.py` explains how to point the
full harness at a local copy.

## The four CI gates

1. **Secret scan** — gitleaks over the pull-request diff.
2. **RLS assertion** — applies the migrations to a throwaway Postgres and
   fails if any table in `public` has `relrowsecurity = false`, or if `anon`
   still holds a table grant.
3. **SAST** — detekt for Kotlin, Semgrep for the security and secrets
   rulesets.
4. **Build** — assemble and unit tests.

Gate 2 is the one that matters: it is the machine check that would have
stopped the defect in `0001` from reaching the client's device.

## Local hook

```bash
pip install pre-commit && pre-commit install
```

The hook refuses a commit that (a) stages a credential, (b) creates a table
without enabling RLS in the same migration, or (c) flips `allowBackup` or
`usesCleartextTraffic` to a permissive value.

## Scope note

Phase 1 is a single-owner application: one authenticated account, the business
owner. There is no customer-facing login, so every policy in `0002`
authorises the owner and nothing else. The customer-facing policy is written
but commented out in `0002`, so the intent is recorded before the Phase 2
portal exists.
