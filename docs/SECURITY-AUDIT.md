# Security audit record — Momompurin Live Manager

Course: IAS / ALGOCOM PBL Milestone 1 · Group 8, III-BCSAD, University of
Makati · Prepared 07 November 2026

Prof. Nomar's recommendation, implemented as named:

> Reports provided through the:
> ☐ Use Cloudflare's security-audit skill on github
> ☐ Use OWASP Agentic Skills Top 10 skill on github
> … Run these skills through your entire codebase.

This document is that report. Section 1 is the audit of this repository run
with the Cloudflare security-audit skill; Section 2 is the OWASP Agentic
Skills Top 10 (AST10) self-assessment of the agent tooling around this
repository; Section 3 states how both skills are wired into the workflow so
this is a standing mechanism, not a one-off document.

Status at the time of writing: the project is design-stage. The algorithm
layer, data layer and security controls are implemented and runnable here;
the application UI is still the Figma mid-fidelity prototype, and the Gradle
project has not been cut. The audit covers what exists.

---

## 1. Cloudflare security-audit skill

**Skill:** `github.com/cloudflare/security-audit-skill` — a six-phase
adversarial audit workflow for coding agents: (1) reconnaissance into an
architecture map and coverage ledger, (2) coverage-led hunting across attack
classes, (3) candidate validation by a verifier that tries to disprove each
finding, (4) structured findings, (5) independent re-verification, (6)
target-neutral reporting.

**How it was run here.** The skill requires a tool-using agent with parallel
sub-agents; for this submission the audit was executed by following the
skill's phase prompts and coverage-ledger discipline against every file in
this repository (14 source files, 2 migrations, 1 CI workflow, 1 hook, 1
policy). Each finding below was then re-checked by attempting to disprove it
against the actual file contents before being written down — the skill's
phase-3 discipline. When the Gradle project lands, the skill runs
agent-driven against the whole tree (`npx skills add
https://github.com/cloudflare/security-audit-skill --skill security-audit`,
then "security audit this codebase"); the findings ledger in §3.1 is where
its output merges.

### 1.1 Coverage ledger

| Unit | Files | Reviewed | Method |
|---|---|---|---|
| Android data layer | `app/src/main/java/.../CustomerRepository.kt`, `Supabase.kt` | yes | read + manual trace of every query path |
| Android manifest | `AndroidManifest.xml` | yes | control-by-control check |
| Schema (as shipped) | `supabase/migrations/0001_initial_schema.sql` | yes | full read; defect documented intentionally |
| Schema (hardened) | `supabase/migrations/0002_enable_rls_and_policies.sql` | yes | full read; every policy traced to `is_owner()` |
| CI gates | `.github/workflows/security.yml` | yes | full read; gate-by-gate dry run |
| Pre-commit guard | `security/pre-commit`, `.pre-commit-config.yaml`, `security/gitleaks.toml` | yes | full read + regex review |
| PoC harness | `poc/run_poc.mjs`, `poc/package.json` | yes | full read; key placeholders verified synthetic |
| Algorithm layer | `algorithms/*.py`, `demo/*` | yes | run with assertions (`python demo/run_demo.py`) |
| Threat model | `threat-model/momompurin-threat-dragon.json` | yes | cross-checked against implemented controls |

### 1.2 Findings

Severity follows the skill's reporting schema: HIGH = exploitable data
exposure or broken control; MEDIUM = control gap that needs a decision; LOW =
hygiene. Verdicts: `confirmed` (re-checked against the file), or
`needs_validation` (cannot be settled from the repository alone).

**F-01 · MEDIUM · confirmed — CI Gate 4 cannot pass until the Gradle project exists.**
`security.yml` invoked `./gradlew`, but the repository contains no wrapper and
no buildable module (the app is design-stage; the UI lives in Figma). The
gate would fail red on the first push for a reason unrelated to security.
*Action taken:* both Gradle steps now guard on the wrapper's existence and
print the activation condition instead of failing; a new `algo-demo` job runs
the Python correctness assertions, which are the buildable artefact today.
*Residual:* when the Gradle module lands, remove both guards in the same PR.

**F-02 · MEDIUM · confirmed — `auth.uid()` is re-created in migration 0001, which hosted Supabase already ships.**
On a hosted Supabase project, `auth.uid()` exists with a definition owned by
Supabase; `create or replace function auth.uid()` in our migration would
either fail on ownership or, if run with sufficient privilege, replace
Supabase's own definition with a local lookalike. The PoC harness needs the
function locally, where nothing exists yet. *Action taken:* none in code —
documented here instead as a migration-hygiene rule: `0001`/`0002` are for
the PoC's embedded Postgres; for the hosted project, apply only the
policy/grant statements and never touch the `auth` schema. *Residual:* if the
hosted project ever runs these files verbatim, step 1 of 0002 (`grant usage
on schema auth`) is harmless but the two `create or replace` statements are
not.

**F-03 · LOW · confirmed — the pre-commit RLS guard is regex-based and can be fooled by formatting.**
`security/pre-commit` detects `create table` / `enable row level security`
with grep. A migration could pass by commenting out the create (regex still
matches `create table` inside a comment? no — the grep matches text, so a
commented create would be flagged as created, failing safe), or evade by
renaming to `CREATE   TABLE` across lines. It fails closed on false
positives and open only on exotic formatting. *Action taken:* accepted for
the design stage; the CI RLS assertion (Gate 2) queries `pg_class` against a
real database and is the authoritative check. The hook is a fast first line,
not the control.

**F-04 · LOW · confirmed — `Supabase.kt` URL is a project placeholder.**
`https://momompurin-prod.supabase.co` does not exist yet; the anon key is
`REPLACE_WITH_PROJECT_ANON_KEY`. Confirmed intentional: no real project has
been provisioned, so there is nothing to leak. The gitleaks policy still
flags JWT-shaped strings so that a real service_role key pasted by mistake is
caught. *Action taken:* none. *Residual:* when the project is provisioned,
the URL/key must come from build config, not source; the file's own docstring
already states the anon key is publishable by design and safety rests on RLS.

**F-05 · MEDIUM · needs_validation — threat model lists T-08 (adb backup extracts the Room DB) but no Room database or SQLCipher dependency exists in the repo yet.**
The manifest controls (`allowBackup="false"`, `dataExtractionRules`) are the
control for T-08 and they are present and correct. The encrypted cache they
protect does not exist yet. *Residual:* when the Room layer lands, the IAS
portfolio's AES-256 (SQLCipher) requirement must land in the same PR;
the checklist in `docs/SECURITY_CONTROLS_CHECKLIST.md` marks this row
"design-stage".

**F-06 · LOW · confirmed — demo catalogue is synthetic, and that is load-bearing.**
`demo/demo_data.py` generates 752 items in the shape of the client's real
catalogue. The real 767-row workbook (customer-adjacent business data) stays
out of the repository by design, matching the PII-exclusion control from the
IAS milestone. The provenance note at the top of
`algorithms/benchmark/bench_algocom_pbl.py` documents how to reproduce the
real benchmark locally. No real customer or catalogue data is committed.

**F-07 · INFO · confirmed — PoC transcript is the evidentiary chain, and it is intact.**
`poc/run_poc.mjs` uses three synthetic JWTs explicitly marked
`POC_NOT_A_REAL_KEY`; the before/after rows (8 customers → 0) are generated
against an embedded Postgres, not asserted from text. The gitleaks allowlist
covers the file by path, which is scoped correctly.

### 1.3 What the audit did NOT find

Checked for and absent: real credentials anywhere in the tree (verified by
pattern sweep across every non-node_modules file); a table created without
RLS outside the intentionally-defective `0001`; a policy readable by `anon`;
a manifest control flipped permissive; a query path in
`CustomerRepository.kt` that selects `*` (all selects name columns).

---

## 2. OWASP Agentic Skills Top 10 (AST10)

**Skill:** `github.com/OWASP/www-project-agentic-skills-top-10` — the OWASP
framework for security risks in AI-agent skills (AST01 Malicious Skills,
AST02 Supply-Chain Compromise, AST03 Over-Privileged Skills, … AST10
Cross-Platform Reuse). This repository is *developed with* coding agents
(ZCode / Claude Code), and the security-audit skill above is an agent skill —
so AST10 applies to our own tooling, and this section is the self-assessment
the framework's checklist asks for, run over the agent skills this project
uses.

| # | Risk | Our exposure | Control in place |
|---|---|---|---|
| AST01 | Malicious Skills | The project's agent tooling is the stock ZCode install plus two project skills; nothing installed from unvetted marketplaces | Skills inventoried before use; no third-party skill is loaded into the agent session that touches this repo |
| AST02 | Supply-Chain Compromise | Generated code could carry vulnerable dependencies | `poc/package.json` pins `embedded-postgres` to a version range reviewed at install; CI runs Semgrep secrets ruleset; the Kotlin side adds no runtime dependency beyond the Supabase SDK |
| AST03 | Over-Privileged Skills | An agent skill with filesystem-wide reach could exfiltrate the client workbook | The client workbook is git-ignored and outside the repo; agent sessions run with the project directory as their working scope |
| AST04 | Insecure Metadata | Skill manifests (SKILL.md) could carry instructions that leak data | The two skills referenced by name (Cloudflare security-audit, OWASP AST10) are documentation/audit workflows, not code executors; their prompts produce findings files only |
| AST05 | Untrusted External Instructions | Web-fetched content instructing the agent | Fetched skill docs are treated as reference text, never as instructions that override the repo's own security policy (e.g. nothing can talk the agent into committing a key) |
| AST06 | Weak Isolation | Agent runs arbitrary code (the PoC starts a real Postgres) | The PoC binds to a throwaway embedded instance on a random port; no production credentials exist to reach |
| AST07 | Update Drift | A skill silently changes behaviour between runs | The two named skills are pinned by repository URL and read at a known commit for this submission; the audit record in §1 is tied to the repo state at commit time |
| AST08 | Poor Scanning | Agent output trusted without verification | The security-audit skill's own discipline requires a disproving pass (§1.2 "action taken / residual" pattern); CI Gate 2 (real-Postgres RLS assertion) is the machine backstop for the highest-severity class |
| AST09 | No Governance | Nobody owns skill usage | Gabriel Garlando (project leader) owns the agent tooling; the audit record is reviewed with the milestone, not ad hoc |
| AST10 | Cross-Platform Reuse | Skill behaviour differs across agents | The audit workflow is written in the repository (this document + the CI gates), not inside one agent's memory, so it travels across Claude Code / Codex / Cursor unchanged |

AST10 verdict: no red flags. The framework's two highest-severity categories
(AST01 malicious skills, AST02 supply chain) are closed because the project
installs no third-party executable skill and pins everything it does use.

---

## 3. Standing mechanism — how these skills stay in the workflow

A report is not a mechanism. Three wiring points keep both skills in
continuous use:

1. **The audit record is versioned with the code.** This file sits in
   `docs/`, next to the code it audits. Every finding carries an *action
   taken* and a *residual* line, so the next auditor (human or the skill
   itself) inherits the ledger instead of starting over. When the
   Cloudflare skill is run agent-driven post-Gradle, its `REPORT.md` output
   appends to this ledger.

2. **The OWASP AST10 checklist gates new skills.** Before any agent skill is
   added to the project tooling, the §2 table is re-run for it: what does it
   execute, what can it read, who published it, is it pinned. The
   framework's assessment checklist (`checklist.md` in the OWASP repo) is
   the form.

3. **The machine gates do not depend on any agent.** Gitleaks, the RLS
   assertion, Semgrep and the algorithm demo run in CI on every push. The
   agent-driven skills advise; the gates decide.

---

## 4. Verification chain

| Claim | How to check it |
|---|---|
| Algorithm correctness | `python demo/run_demo.py` — exits non-zero on any disagreement between implementation and baseline |
| RLS defect and fix | `cd poc && npm install && node run_poc.mjs` — prints before/after row counts |
| No secrets committed | `gitleaks detect --config security/gitleaks.toml` after clone (or push; the CI gate runs it) |
| RLS on every table | push to `main`; Gate 2 applies the migrations to a fresh Postgres and fails if any table lacks RLS |
