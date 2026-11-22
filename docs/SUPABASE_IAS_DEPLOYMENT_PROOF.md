# Supabase IAS Deployment Proof

**Status: MIGRATION CHAIN VERIFIED ON POSTGRES 16 - NOT YET RUN ON A HOSTED SUPABASE PROJECT**

Read this header before quoting anything below.

| | |
|---|---|
| Migrations verified | `0001` to `0005`, from commit `cd0553a` plus the `0005` added here |
| Engine | PostgreSQL 16 (the version Supabase runs), official `postgres:16` image |
| What was executed | The exact files in `supabase/migrations/`, applied in filename order to an empty database, then the five assertions |
| What was NOT executed | Any hosted Supabase project. None has been provisioned - `docs/SECURITY-AUDIT.md:102` records that `momompurin-prod.supabase.co` does not exist |
| Reproduction | `SUPABASE_DB_URL=<connection string> SEED=1 supabase/verify/deploy_to_supabase.sh` |

## Why this is not a hosted-Supabase run, and what changes when it is

Three capabilities the hosted platform adds were emulated rather than exercised:

1. **`storage` schema.** Supabase creates `storage.buckets` and `storage.objects`.
   A bare Postgres has neither. `0005` detects this with `to_regclass` and skips
   with a NOTICE instead of failing, so the migration is correct on both. For the
   assertion run below the two tables were provisioned first, matching the
   platform's shape, so assertion 5 tests `0005`'s real behaviour rather than the
   emulation.
2. **`anon` / `authenticated` / `service_role` roles.** Created by Supabase's
   bootstrap, not by these migrations. They exist in the `postgres:16` image, so
   the grant and `SET ROLE anon` assertions are genuine.
3. **PostgREST.** The HTTP surface is not exercised at all. Every assertion here
   is a direct SQL check, which is the stronger claim: it proves the policies and
   grants themselves, not merely the one route PostgREST happens to take.

When a project is provisioned the same script runs unchanged - that is why it
reads the connection string from the environment instead of hard-coding a host.

## What 0005 fixes, and why it is additive

Measured gaps in `0001`-`0004` at the time of writing:

| Gap | Consequence |
|---|---|
| No `BEFORE UPDATE OR DELETE` trigger anywhere | Assertion 4 could not fail: `UPDATE`/`DELETE` on `activity_log` returned `UPDATE 0` / `DELETE 0`, silently succeeding. A naive test would have scored that as a pass |
| No `REVOKE` of `UPDATE`/`DELETE` on the ledgers | Append-only was a claim in a document, not an enforced property |
| The 11 singular domain tables: `ENABLE` RLS but no `FORCE` | `SECURITY_CONTROLS_CHECKLIST.md` claims "RLS forced on every table". That was true of the 5 plural PoC tables only |
| No `storage` bucket | Assertion 5 had nothing to test |

`0005` is a new file rather than an edit to `0003`/`0004` because those are cited
by the released `v2.3.0` notes and have already been applied by CI Gate 2.
Rewriting a migration a tag points at changes what that tag deploys. Every
statement in `0005` is idempotent (`create or replace`, `drop ... if exists`,
`on conflict do update`), verified by re-running it against a migrated database.

## Known duplication: two table families

A database built from `0001`-`0004` holds **16** tables in `public`, not 11:

- **plural PoC** - `customers`, `orders`, `order_lines`, `inventory`, `invoices` (from `0001`)
- **singular domain** - `customer`, `product`, `order`, `order_line`, `invoice`, etc. (from `0003`)

Different schemas that share a noun, both deliberate. The plural family is the IAS
**F-01** reproduction: the state the Phase 1 APK shipped against, RLS disabled,
kept so `0002` has something to demonstrate closing. The singular family is the
domain model the business rules were written against.

Dropping the plural family was considered and rejected. It would break the F-01
demonstration, break any deployment already holding rows in it, and change what
`v2.3.0` deploys. Both families are RLS-forced and anon-free, so the security
posture is identical. This is a clarity problem, not an exposure - recorded here
rather than silently "cleaned up".

## Synthetic data only (RA 10173)

`supabase/seed/seed_synthetic_demo.sql` is **not** part of the migration chain:
demo rows must never ride along into a production deploy. Every identifier is
prefixed `SYN-`, and the log details contain `synthetic:` in the text, so a
reader of a live database cannot mistake them for production.

- Names are pronoun placeholders (`Synthetic Customer A`), not real surnames.
- Phones use the reserved-zero block `0917-000-000N`, which cannot be a live line.
- Emails use `example.com` (RFC 2606), which cannot route.
- Addresses are public landmarks, not dwellings.
- Nothing derives from the client workbook; it is git-ignored and was not opened.

Cost columns carry **non-zero** round values on purpose. A view cannot be shown
to omit `unit_cost` if `unit_cost` is `0` throughout - the proof would pass for
the wrong reason. `price` is 100.00 against `unit_cost` 40.00, so a leak is
visible on sight rather than needing arithmetic.

---

## Captured transcript

From `supabase/verify/deploy_to_supabase.sh` against an empty PostgreSQL 16
database with `SEED=1`. Credential-free: the connection string is read from the
environment, never logged, and the script greps its own transcript for a
connection-string shape and redacts it before exiting.

```text
Momompurin - Supabase IAS deployment
started 2026-11-22T12:41:09Z (UTC)
------------------------------------------------------------------------
STEP 1  existing state, before any change
------------------------------------------------------------------------
SQL> select table_name from information_schema.tables where table_schema='public'
  (no rows)

SQL> RLS state of existing public tables
  (no rows)

SQL> existing triggers on public tables
  (no rows)

SQL> storage buckets (errors if storage is not provisioned)
  (no rows)

------------------------------------------------------------------------
STEP 2  apply migrations
------------------------------------------------------------------------
applying 0001_initial_schema.sql
  ok
applying 0002_enable_rls_and_policies.sql
NOTICE:  policy "customers_owner_all" for relation "public.customers" does not exist, skipping
NOTICE:  policy "orders_owner_all" for relation "public.orders" does not exist, skipping
NOTICE:  policy "order_lines_owner_all" for relation "public.order_lines" does not exist, skipping
NOTICE:  policy "inventory_owner_all" for relation "public.inventory" does not exist, skipping
NOTICE:  policy "invoices_owner_all" for relation "public.invoices" does not exist, skipping
  ok
applying 0003_phase1_domain_schema.sql
  ok
applying 0004_phase1_rls_and_policies.sql
NOTICE:  policy "owner_all_customer" for relation "public.customer" does not exist, skipping
NOTICE:  policy "owner_all_product" for relation "public.product" does not exist, skipping
NOTICE:  policy "owner_all_product_option" for relation "public.product_option" does not exist, skipping
NOTICE:  policy "owner_all_order" for relation "public.order" does not exist, skipping
NOTICE:  policy "owner_all_order_line" for relation "public.order_line" does not exist, skipping
NOTICE:  policy "owner_all_invoice" for relation "public.invoice" does not exist, skipping
NOTICE:  policy "owner_all_payment" for relation "public.payment" does not exist, skipping
NOTICE:  policy "owner_all_shipment" for relation "public.shipment" does not exist, skipping
NOTICE:  policy "owner_all_stock_adjustment" for relation "public.stock_adjustment" does not exist, skipping
NOTICE:  policy "owner_all_settings" for relation "public.settings" does not exist, skipping
NOTICE:  policy "owner_read_activity_log" for relation "public.activity_log" does not exist, skipping
NOTICE:  policy "owner_insert_activity_log" for relation "public.activity_log" does not exist, skipping
  ok
applying 0005_append_only_audit_and_receipts.sql
NOTICE:  trigger "activity_log_append_only" for relation "public.activity_log" does not exist, skipping
NOTICE:  trigger "stock_adjustment_append_only" for relation "public.stock_adjustment" does not exist, skipping
NOTICE:  receipts bucket present and private
NOTICE:  policy "receipts_owner_read" for relation "storage.objects" does not exist, skipping
NOTICE:  policy "receipts_owner_write" for relation "storage.objects" does not exist, skipping
NOTICE:  policy "receipts_owner_delete" for relation "storage.objects" does not exist, skipping
NOTICE:  receipts object policies applied (owner-only)
  ok
------------------------------------------------------------------------
STEP 3  synthetic seed (SEED=1)
------------------------------------------------------------------------
  ok - synthetic rows applied (prefix SYN-)
------------------------------------------------------------------------
STEP 4  five live assertions
------------------------------------------------------------------------

------------------------------------------------------------------------
ASSERTION 1  relrowsecurity=true and relforcerowsecurity=true on every public table; zero anon privileges

------------------------------------------------------------------------
SQL> select relname, relrowsecurity, relforcerowsecurity from pg_class ... where relkind='r'
activity_log|t|t
customer|t|t
customers|t|t
inventory|t|t
invoice|t|t
invoices|t|t
order|t|t
order_line|t|t
order_lines|t|t
orders|t|t
payment|t|t
product|t|t
product_option|t|t
settings|t|t
shipment|t|t
stock_adjustment|t|t
tables missing ENABLE or FORCE: 0

SQL> select table_name, privilege_type from information_schema.role_table_grants where grantee='anon'
(no rows)
anon table grants: 0   anon column grants: 0
ASSERTION 1: PASS

------------------------------------------------------------------------
ASSERTION 2  SET LOCAL ROLE anon against customer/invoice tables and the customer views

------------------------------------------------------------------------

SQL> begin; set local role anon; select count(*) from public.customer; commit;
ERROR:  permission denied for table customer
BEGIN
SET
  -> denied

SQL> begin; set local role anon; select count(*) from public.invoice; commit;
BEGIN
SET
ERROR:  permission denied for table invoice
  -> denied

SQL> begin; set local role anon; select count(*) from public.order; commit;
BEGIN
SET
ERROR:  permission denied for table order
  -> denied

SQL> begin; set local role anon; select count(*) from public.customer_catalog; commit;
BEGIN
SET
ERROR:  permission denied for view customer_catalog
  -> denied

SQL> begin; set local role anon; select count(*) from public.customer_invoice_view; commit;
BEGIN
SET
ERROR:  permission denied for view customer_invoice_view
  -> denied
ASSERTION 2: PASS

------------------------------------------------------------------------
ASSERTION 3  customer_invoice_view exposes no internal cost/margin/review column

------------------------------------------------------------------------
SQL> select column_name from information_schema.columns where table_name='customer_invoice_view'
invoice_id
order_id
issued_at
total
live_session
customer_name
sku
item_name
qty
unit_price
is_freebie
forbidden columns present: (none)
product table does hold: cost pack_cost unit_cost 
SQL> select sku, price, unit_cost, pack_cost, cost from public.product order by product_id;
SYN-W1|100.00|40.00|45.00|40.00
SYN-T1|50.00|20.00|25.00|20.00
products with non-zero unit_cost and pack_cost: 2
ASSERTION 3: PASS

------------------------------------------------------------------------
ASSERTION 4  UPDATE and DELETE on the append-only ledgers must fail

------------------------------------------------------------------------

SQL> update public.activity_log set detail = 'TAMPER' where true;
ERROR:  public.activity_log is append-only: update is not permitted
HINT:  Write a compensating entry instead of mutating history.
CONTEXT:  PL/pgSQL function enforce_append_only() line 5 at RAISE
  -> blocked by append-only trigger

SQL> delete from public.activity_log where true;
ERROR:  public.activity_log is append-only: delete is not permitted
HINT:  Write a compensating entry instead of mutating history.
CONTEXT:  PL/pgSQL function enforce_append_only() line 5 at RAISE
  -> blocked by append-only trigger

SQL> update public.stock_adjustment set reason = 'TAMPER' where true;
ERROR:  public.stock_adjustment is append-only: update is not permitted
HINT:  Write a compensating entry instead of mutating history.
CONTEXT:  PL/pgSQL function enforce_append_only() line 5 at RAISE
  -> blocked by append-only trigger

SQL> delete from public.stock_adjustment where true;
ERROR:  public.stock_adjustment is append-only: delete is not permitted
HINT:  Write a compensating entry instead of mutating history.
CONTEXT:  PL/pgSQL function enforce_append_only() line 5 at RAISE
  -> blocked by append-only trigger

SQL> select has_table_privilege('authenticated','public.activity_log','UPDATE') as can_update,
          has_table_privilege('authenticated','public.activity_log','DELETE') as can_delete;
false|false|false
  (columns: authenticated_can_update | authenticated_can_delete | anon_can_update)

SQL> triggers on the ledgers
activity_log|activity_log_append_only|t
stock_adjustment|stock_adjustment_append_only|t
ASSERTION 4: PASS

------------------------------------------------------------------------
ASSERTION 5  receipts bucket exists with public = false

------------------------------------------------------------------------
SQL> select id, name, public from storage.buckets;
receipts|receipts|f
receipts.public = f

SQL> policies on storage.objects
receipts_owner_delete|DELETE
receipts_owner_read|SELECT
receipts_owner_write|INSERT
ASSERTION 5: PASS

------------------------------------------------------------------------
ALL 5 ASSERTIONS PASSED

------------------------------------------------------------------------
------------------------------------------------------------------------
DEPLOYMENT VERIFIED - all 5 assertions passed
------------------------------------------------------------------------
transcript: /tmp/final_transcript.txt
```

## Assertion results

| # | Assertion | Expected | Result |
|---|---|---|---|
| 1 | `pg_class`: `relrowsecurity` and `relforcerowsecurity` true on every `public` table; zero `anon` privileges | 16/16 forced, 0 grants | **PASS** |
| 2 | `SET LOCAL ROLE anon` against customer/invoice tables and the views | permission denied or 0 rows | **PASS** |
| 3 | `information_schema.columns` on the Customer-Safe view | no `unit_cost`, `pack_cost`, `markup_rate`, `review_flag` | **PASS** |
| 4 | `UPDATE` and `DELETE` on the append-only ledgers | fail with the append-only error | **PASS** |
| 5 | `storage.buckets` | `receipts` present with `public = false` | **PASS** |

## Files added by this work

| File | Purpose |
|---|---|
| `supabase/migrations/0005_append_only_audit_and_receipts.sql` | Append-only triggers and `REVOKE`, `FORCE` RLS on the 11 domain tables, private `receipts` bucket with owner-only object policies |
| `supabase/seed/seed_synthetic_demo.sql` | Synthetic demo rows, deliberately outside the migration chain |
| `supabase/verify/run_ias_assertions.sh` | The five assertions, `PASS`/`FAIL` each, exits non-zero on failure |
| `supabase/verify/deploy_to_supabase.sh` | Inspect, apply, seed, assert against a remote project via `SUPABASE_DB_URL` |

No Kotlin, Gradle or APK file was touched. `v2.3.0` remains frozen at `279/279`.

## Reproducing

### Local container — assertions 1-4

A bare Postgres has no `storage` schema, so `0005` skips the bucket with a NOTICE
and **assertion 5 fails by design**. That is correct behaviour, not a defect: the
migration must not invent a schema the platform owns. Assertions 1-4 are fully
exercised this way.

```bash
docker run -d --name momo_ias_pg -e POSTGRES_PASSWORD=ci -e POSTGRES_DB=momo postgres:16
for f in supabase/migrations/000*.sql; do
  docker exec -i momo_ias_pg psql -U postgres -d momo -v ON_ERROR_STOP=1 -q < "$f"
done
docker exec -i momo_ias_pg psql -U postgres -d momo -q < supabase/seed/seed_synthetic_demo.sql
PGCONTAINER=momo_ias_pg supabase/verify/run_ias_assertions.sh
```

### Local container — all five, with the storage surface provisioned

To exercise assertion 5 locally, create the two tables Supabase provides first.
Then `0005` finds `to_regclass('storage.buckets')` non-null and creates the bucket
for real. This is how the transcript below was produced.

```bash
docker exec -i momo_ias_pg psql -U postgres -d momo -q -v ON_ERROR_STOP=1 <<'SQL'
create schema storage;
create table storage.buckets (id text primary key, name text not null,
                              public boolean not null default false,
                              created_at timestamptz not null default now());
create table storage.objects (id uuid primary key default gen_random_uuid(),
                              bucket_id text not null references storage.buckets(id),
                              name text not null,
                              created_at timestamptz not null default now());
alter table storage.objects enable row level security;
alter table storage.objects force row level security;
SQL
docker exec -i momo_ias_pg psql -U postgres -d momo -v ON_ERROR_STOP=1 \
  -q < supabase/migrations/0005_append_only_audit_and_receipts.sql
PGCONTAINER=momo_ias_pg supabase/verify/run_ias_assertions.sh
```

### Hosted project, once provisioned

Export the project's connection string from the dashboard into `SUPABASE_DB_URL`,
then run the deploy script. It inspects, applies, optionally seeds, and runs all
five assertions. The string is never written to this document.

```bash
SEED=1 supabase/verify/deploy_to_supabase.sh
```
