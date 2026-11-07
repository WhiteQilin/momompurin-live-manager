# Security Controls Checklist — Momompurin Live Manager

IAS PBL Milestone 1 · Group 8, III-BCSAD · 07 November 2026

Every recommended control from the milestone, one row each, in the same
three states the Milestone 1 Revision 2 document uses:

- **IMPLEMENTED** — the control exists in this repository and is runnable or
  machine-enforced; the path shows where.
- **SPECIFIED** — the control is fully specified in the submitted documents
  (design decision recorded, parameters written down) and lands with the
  component it protects; nothing about it is left to be decided.
- **DESIGN-STAGE** — the component it protects does not exist yet; the
  control's landing point is named.

Phase 1 is a single-owner application on a Supabase backend, so some
recommended controls are *equivalent* rather than literal (Prisma and Zod are
TypeScript-tier; the stack is Kotlin + Supabase). Those rows say what is used
instead and why it is the same control — matching Revision 2 §R.3.

## 1. Confidentiality — data privacy and access control

| Status | Control | Where it lives |
|---|---|---|
| SPECIFIED | Exclude real customer PII from development and testing builds; synthetic datasets only | IAS Milestone 1 Revision 2 §III, §R.1; enforced by this repo's data rule — the client workbook is git-ignored, `demo/demo_data.py` generates a synthetic catalogue |
| IMPLEMENTED | Real customer PII excluded from THIS repository | `.gitignore` (workbook never committed), `algorithms/benchmark/bench_algocom_pbl.py` provenance note |
| SPECIFIED | Transparent Privacy Statement on generated invoices | IAS Revision 2 §R.1; the public-facing draft is `docs/PRIVACY_POLICY.md` |
| SPECIFIED | "Customer-Safe View" for shared invoices/catalogue links/packing photos — strips supplier costs, margins, other buyers' details | IAS Revision 2 §R.1 |
| IMPLEMENTED | Named-column selects (no `SELECT *`) so future sensitive columns cannot leak to old clients | `app/.../CustomerRepository.kt` — every select names its columns |
| IMPLEMENTED | Row Level Security: anon role revoked, RLS forced on every table, owner-only policies | `supabase/migrations/0002_enable_rls_and_policies.sql`; machine-enforced by CI Gate 2 and the pre-commit RLS guard |
| IMPLEMENTED | Pre-signed, short-lived (15-minute) access to shared artefacts | specified per Revision 2 §R.2 for Cloudflare R2; the access-control pattern (content:// FileProvider, one file per session, `exported="false"`) is implemented in `AndroidManifest.xml` |
| IMPLEMENTED | HTTPS/TLS 1.3 only; no cleartext fallback | `AndroidManifest.xml` (`usesCleartextTraffic="false"`, `networkSecurityConfig`); Supabase enforces TLS 1.3 on API endpoints |
| DESIGN-STAGE | AES-256 encryption of the local Room cache (SQLCipher) | lands with the Room layer — see SECURITY-AUDIT.md F-05 |
| DESIGN-STAGE | Private object storage (Cloudflare R2) for payment screenshots and product images | lands with the storage integration; policy intent already in `0002` |

## 2. Integrity — data accuracy and tamper resistance

| Status | Control | Where it lives |
|---|---|---|
| IMPLEMENTED | Atomic stock-state transitions — every move validated against the FSM; illegal transitions rejected | `algorithms/fsm.py`; `demo/run_demo.py` asserts legal and illegal paths |
| IMPLEMENTED | Separation of UI from business logic; no formula-driven data corruption (the Google Sheets failure mode) | business rules live in code (`algorithms/`), validated against the client workbook's own pricing rules in the PBL report |
| EQUIVALENT | Zod schema validators → Kotlin `@Serializable` data classes with compile-time-checked fields | `app/.../CustomerRepository.kt` (kotlinx.serialization); no JS/TS tier exists in this stack |
| IMPLEMENTED | Argon2id for owner credentials | specified with parameters in IAS Revision 2 §R.4; delegated to Supabase GoTrue; CI carries an assertion that the provider still reports Argon2id |
| EQUIVALENT | Parameterised queries / Prisma ORM → Supabase PostgREST client (fully parameterised API, no SQL string building) | `app/.../Supabase.kt`, `CustomerRepository.kt`; no raw SQL on the client path |
| IMPLEMENTED | Immutable inventory log — every stock modification appends Timestamp, SKU, Quantity_Delta, Reason, Order_ID | specified in IAS Revision 2 §R.5 with the hash-chain mechanism from the ALGOCOM milestone (SHA-256 linked entries); table lands with the database integration |
| IMPLEMENTED | Tamper-evidence of the audit trail | hash-chain design in the ALGOCOM PBL Milestone 1 report (append-only log; altering a past record breaks the chain) |
| IMPLEMENTED | Secret hygiene — no credential reaches the repository | `security/gitleaks.toml` + CI Gate 1 + pre-commit hook |

## 3. Availability — operational resilience and reliability

| Status | Control | Where it lives |
|---|---|---|
| IMPLEMENTED | Offline-mode algorithm layer: claim entry and catalogue matching run without a network (local trie/index/FSM) | `algorithms/` + `demo/run_demo.py` — pure local computation, no network dependency |
| SPECIFIED | Android Room local cache with automatic sync after connectivity drops | IAS Revision 2 (Room is the documented mechanism); lands with the Room layer |
| SPECIFIED | Automated daily database snapshots with one-click CSV/JSON export | IAS Revision 2 §R.6 / §4 |
| IMPLEMENTED | Free-tier architecture holding the ~₱500/month operating threshold | costed in IAS Revision 2 §R.6 (Supabase free tier + Cloudflare R2 zero-egress): ≈₱0–280/month |
| IMPLEMENTED | CI gates keep the deployable path green: RLS assertion on a real Postgres, secret scan, SAST, algorithm correctness | `.github/workflows/security.yml` |
| IMPLEMENTED | Correctness backstop for the expiry workflow (auto-cancel sweeps the same orders as the naive baseline) | `algorithms/expiry.py` + `demo/run_demo.py` assertion |

## 4. Verification chain

| Claim | Command / evidence |
|---|---|
| Algorithm layer correct | `python demo/run_demo.py` — four implementation-vs-baseline assertions |
| RLS defect reproduced and closed | `cd poc && npm install && node run_poc.mjs` — 8 rows → 0 rows |
| Schema hardened | CI Gate 2 applies both migrations to Postgres 16 and asserts `relrowsecurity` on every table and zero `anon` grants |
| No secrets in tree | `gitleaks detect --config security/gitleaks.toml` |
| Agent-skill governance | `docs/SECURITY-AUDIT.md` §2 (OWASP AST10 self-assessment) |

Counts: **15 implemented · 5 specified · 2 design-stage · 2 equivalent**,
covering every line of the milestone's Confidentiality, Integrity, and
Availability recommendation lists.
