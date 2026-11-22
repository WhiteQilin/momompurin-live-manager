# Security Controls Checklist — Momompurin Live Manager

IAS PBL Milestone 1 · Group 8, III-BCSAD · 07 November 2026
**Rows re-verified against the shipped v2.3 app, 22 November 2026**

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

## v2.3 re-verification note (22.11.2026)

The v2.3 build moved to an in-process store (`data/MomoStore.kt`, a single
`StateFlow` mutation point) with the catalogue and account logic in an ops layer
(`data/CatalogueOps.kt`, `data/OrderOps.kt`, `data/AccountOps.kt`). The previous
data class repository (`CustomerRepository.kt`) and the Supabase client wrapper
(`Supabase.kt`) are **no longer in the source tree**, so any row that cited them
would have pointed at a deleted file. Those rows now cite the code that actually
ships. Nothing is newly claimed: where the mechanism changed, the row says so;
where it did not, the citation is simply accurate again.

The v2.3 notes distinguish controls implemented by the Android client from
controls that are implemented in the separate Supabase schema/CI but are not
called by the current in-process app. A source path is cited for each claim.


## 1. Confidentiality — data privacy and access control

| Status | Control | Where it lives |
|---|---|---|
| SPECIFIED | Exclude real customer PII from development and testing builds; synthetic datasets only | IAS Milestone 1 Revision 2 §III, §R.1; enforced by this repo's data rule — the client workbook is git-ignored, `demo/demo_data.py` generates a synthetic catalogue |
| IMPLEMENTED | Real customer PII excluded from THIS repository | `.gitignore` (workbook never committed), `algorithms/benchmark/bench_algocom_pbl.py` provenance note |
| IMPLEMENTED | Four-line RA 10173 Data Privacy Act statement on generated invoices | `ui/screens/InvoiceRenderer.kt` `privacyFooter()`; same four fixed lines are appended to the on-screen invoice, exported PDF and shared PNG |
| SPECIFIED | "Customer-Safe View" for shared invoices/catalogue links/packing photos — strips supplier costs, margins, other buyers' details | IAS Revision 2 §R.1 |
| EQUIVALENT | Named-column reads (no `SELECT *`) so undeclared fields cannot leak to a client | The v2.3 client uses typed in-process `MomoData` snapshots through `data/MomoStore.kt`, not ad-hoc SQL row maps; the Supabase domain schema remains in `supabase/migrations/0003_phase1_domain_schema.sql` and `0004_phase1_rls_and_policies.sql` |
| IMPLEMENTED | Row Level Security: anon role revoked, RLS forced on every table, owner-only policies | Backend schema/policies in `supabase/migrations/0002_enable_rls_and_policies.sql`, `0004_phase1_rls_and_policies.sql`; CI Gate 2 applies them to Postgres and asserts RLS is enabled |
| SPECIFIED | Pre-signed, short-lived (15-minute) access to shared artefacts | Cloudflare R2 access is specified in Revision 2 §R.2; v2.3 local exports use `content://` FileProvider URIs with `exported="false"` in `AndroidManifest.xml` (a separate local-file control, not a pre-signed URL) |
| IMPLEMENTED | Cleartext HTTP disabled for Android network traffic | `AndroidManifest.xml` (`usesCleartextTraffic="false"`, `networkSecurityConfig`); no TLS-version claim is made here |
| DESIGN-STAGE | AES-256 encryption of the local Room cache (SQLCipher) | lands with the Room layer — see SECURITY-AUDIT.md F-05 |
| DESIGN-STAGE | Private object storage (Cloudflare R2) for payment screenshots and product images | lands with the storage integration; policy intent already in `0002` |

## 2. Integrity — data accuracy and tamper resistance

| Status | Control | Where it lives |
|---|---|---|
| IMPLEMENTED | Atomic stock-state transitions — every move validated against the FSM; illegal transitions rejected | `algorithms/fsm.py`; `demo/run_demo.py` asserts legal and illegal paths |
| IMPLEMENTED | Separation of UI from business logic; no formula-driven data corruption (the Google Sheets failure mode) | business rules live in code (`algorithms/`), validated against the client workbook's own pricing rules in the PBL report |
| EQUIVALENT | Zod schema validators → Kotlin `@Serializable` data classes with compile-time-checked fields | `data/entity/Entities.kt` (typed domain entities: `Product`, `Account`, `Order`, `ShippingStatus` …) and `app/build.gradle.kts` (`kotlinx.serialization.json`); `data/MomoStore.kt` swaps whole typed snapshots, so a malformed field cannot reach the UI. No JS/TS tier exists in this stack |
| IMPLEMENTED | On-device owner-password KDF: salted `PBKDF2WithHmacSHA256` | `data/AccountOps.kt`: 16-byte random per-account salt, 65,536 rounds, stored as `pbkdf2$<iterations>$<saltHex>$<hashHex>`; a legacy unsalted digest still verifies and is re-digested on next success. **Production spec:** Supabase GoTrue Argon2id, parameters in IAS Revision 2 §R.4. The local KDF is PBKDF2 because it ships with `javax.crypto`; it is not claimed to be Argon2id |
| EQUIVALENT | Parameterised queries / Prisma ORM → Supabase PostgREST client (fully parameterised API, no SQL string building) | v2.3 has **no raw SQL on the client path** at all: all reads and writes go through typed ops (`data/CatalogueOps.kt`, `data/OrderOps.kt`, `data/AccountOps.kt`) over the `MomoStore` snapshot. The schema they mirror is defined in `supabase/migrations/*.sql`; the PostgREST dependency remains declared in `app/build.gradle.kts` for the sync layer |
| IMPLEMENTED | Append-only activity log with actor, action, entity, detail and timestamp | `data/entity/Entities.kt` `ActivityLogEntry` fields (`actor`, `action`, `entityType`, `entityId`, `detail`, `loggedAt`); writes go through `MomoStore` / ops, not a screen-owned mutable list |
| IMPLEMENTED | Tamper-evidence of the audit trail | **implemented** in `data/AuditChain.kt`: SHA-256 over each entry's pre-image **plus the previous digest**, so editing, deleting or re-ordering a past row breaks the chain. `verify()` distinguishes a re-hash mismatch, a broken link, and an unsealed row; the UI badge (`MomoStore.auditBadge()` → `ReportsAndSettingsScreen`) reads `Audit chain verified · N entries (SHA-256)` or names the first broken entry |
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
| Matching engine correct on the v2.3 port | `V23SprintTest.algocom_trigram_matcher_ranks_the_typo_top` and `algocom_trie_prefix_matches_the_linear_scan` |
| Tracking validator correct on the v2.3 port | `V23SprintTest.tracking_*` — accepts the four waybill shapes, refuses `JT123`, exempts Pickup, blocks dispatch on a malformed number and moves no stock |
| Audit chain tamper-evidence | `AuditChain.verify()` — a re-hash mismatch, a broken link, and an unsealed row are reported separately; `MomoStore.auditBadge()` renders the badge in Edit History |
| Password KDF on-device | `AccountOps` — `pbkdf2$<iterations>$<saltHex>$<hashHex>`, 16-byte salt, 65,536 rounds (`PBKDF2WithHmacSHA256`); Argon2id remains the production GoTrue spec |
| RLS defect reproduced and closed | `cd poc && npm install && node run_poc.mjs` — 8 rows → 0 rows |
| Schema hardened | CI Gate 2 applies both migrations to Postgres 16 and asserts `relrowsecurity` on every table and zero `anon` grants |
| No secrets in tree | `gitleaks detect --config security/gitleaks.toml` |
| Agent-skill governance | `docs/SECURITY-AUDIT.md` §2 (OWASP AST10 self-assessment) |

Counts: **14 implemented · 5 specified · 2 design-stage · 3 equivalent**,
covering every line of the milestone's Confidentiality, Integrity, and
Availability recommendation lists.

*Recount note (22.11.2026).* The in-process typed-data model is recorded as an
**equivalent** to named-column selects; pre-signed Cloudflare R2 URLs remain
**specified** (local `content://` exports are a separate implemented control).
The 4-line RA 10173 footer, SHA-256 audit chain with Edit History badge, and
on-device salted `PBKDF2WithHmacSHA256` parameters are cited from the v2.3 source.
Argon2id remains the production GoTrue spec. The count follows the status labels in
the table, not the earlier November tally.

