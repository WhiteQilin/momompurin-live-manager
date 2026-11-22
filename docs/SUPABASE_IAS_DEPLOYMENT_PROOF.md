# Supabase IAS Deployment Proof

**Status: DEPLOYED AND VERIFIED LIVE ON A HOSTED SUPABASE PROJECT**

| | |
|---|---|
| Project | Supabase, region `ap-southeast-1` — project ref withheld, since it is a credential-adjacent identifier and adds nothing to the proof |
| Engine | PostgreSQL 17.12 (aarch64) |
| Migrations applied | `0001` through `0006`, in filename order |
| Result | **all 5 IAS assertions PASS** against the live database |
| Data | synthetic only (prefix `SYN-`), no client workbook data |
| Credential handling | connection string in a `0600` temp file outside the repo, passed via `--env-file`; never in argv, never logged, purged after |

## The bug this deployment found

The live run failed assertion 1 on the **first attempt**, with 37 `anon` table
grants. It was not a test artifact. It is a privilege-escalation path that exists
only on hosted Supabase and that reading the migrations does not reveal.

Supabase attaches `ALTER DEFAULT PRIVILEGES` to the `public` schema so every newly
created table is granted to `anon`, `authenticated` and `service_role` with
`arwdDxtm`:

    select defaclacl from pg_default_acl
    where defaclnamespace = 'public'::regnamespace and defaclobjtype = 'r';
    -> {postgres=arwdDxtm/postgres,anon=Dxtm/postgres,
        authenticated=Dxtm/postgres,service_role=Dxtm/postgres}

`0002`'s `revoke all on all tables in schema public from anon` fires at that
moment and covers only the tables that already exist. Every table `0003` and
`0004` create afterwards silently comes back with `Dxtm` on `anon`.

`Dxtm` is **TRUNCATE, REFERENCES, TRIGGER, MAINTAIN**. And **RLS does not apply to
TRUNCATE** - RLS filters rows for SELECT/INSERT/UPDATE/DELETE, while TRUNCATE
removes the storage wholesale under table privilege alone. Proven on the live
project inside a rolled-back transaction:

    begin; set local role anon; truncate public.payment;
    -> TRUNCATE TABLE          (succeeded)

The anon key is publishable by design and ships compiled into the APK, so this
meant anyone who unzipped the released v2.3.0 APK could empty the client's
tables. The FORCE RLS work in `0005` does not help: it strengthens a mechanism
TRUNCATE never consults.

`0006_revoke_anon_residual_grants.sql` closes it in four layers - revoke ALL from
`anon` on tables/sequences/functions, revoke the mutating verbs from
`authenticated`/`service_role`, **remove the default privileges** so future tables
do not inherit the hole, then re-state the grants the app uses. The migration ends
with a `raise exception` if any anon privilege survives, so it fails loudly rather
than leaving the hole open.

After `0006` the same exploit returns `permission denied`, and
`has_table_privilege('anon', ..., 'TRUNCATE')` is `false`.

### A second defect, inside 0006 itself

`MAINTAIN` does not exist before PostgreSQL 17. Named unconditionally it raises
`unrecognized privilege type "maintain"` on 16 - which is what **CI runs**. The
first draft of `0006` therefore passed on the Supabase project (17.12) and broke
the gate locally. The verb is now revoked inside a `server_version_num >= 170000`
guard, so 16 skips with a NOTICE and 17 revokes. Both paths were exercised.

### A third blocker: the auth shims

`0001` and `0002` define `auth.uid()` and `auth.jwt()` so the policies are the
same text on a bare Postgres. On hosted Supabase those functions **already
exist**, owned by `supabase_admin`, and the connecting `postgres` role is not a
superuser and holds no CREATE privilege on `auth`:

    ERROR:  permission denied for schema auth

Both definitions are now guarded by `to_regprocedure(...) is null`, so a bare
Postgres still gets the reproduction (verified - the functions are created) and a
hosted project skips with a NOTICE. The guard keys on the function's absence
rather than on "is this Supabase", so both paths yield identical policies.

## Two table families

`0001` creates the plural PoC family (`customers`, `orders`, `order_lines`,
`inventory`, `invoices`) - the IAS **F-01** reproduction - and `0003` creates the
singular domain family. Both are present, giving 16 tables in `public`. Both are
RLS-forced and anon-free. Removing the plural family was considered and rejected:
it would break the F-01 demonstration and change what `v2.3.0` deploys.

## Synthetic data (RA 10173)

`supabase/seed/seed_synthetic_demo.sql`, applied with `SEED=1`. Every identifier
is prefixed `SYN-`; log details contain `synthetic:` so a reader of the live
database cannot mistake them for production. Names are pronoun placeholders,
phones use the reserved-zero block `0917-000-000N`, emails use `example.com`
(RFC 2606), addresses are public landmarks. Nothing derives from the client
workbook, which is git-ignored and was not opened.

Cost columns carry **non-zero** values on purpose: a view cannot be shown to omit
`unit_cost` if `unit_cost` is `0` everywhere, so the assertion would pass for the
wrong reason. `price` 100.00 against `unit_cost` 40.00 makes a leak visible.

## Migration inventory

| File | Purpose |
|---|---|
| `0001_initial_schema.sql` | Plural PoC tables; `auth.uid()` shim (now guarded) |
| `0002_enable_rls_and_policies.sql` | RLS + FORCE on the PoC tables; `auth.jwt()` shim (now guarded) |
| `0003_phase1_domain_schema.sql` | Singular domain tables |
| `0004_phase1_rls_and_policies.sql` | RLS on the domain tables; customer-safe views |
| `0005_append_only_audit_and_receipts.sql` | Append-only triggers, `FORCE` RLS on the domain tables, private `receipts` bucket |
| `0006_revoke_anon_residual_grants.sql` | Closes the anon TRUNCATE/TRIGGER escalation from Supabase's default ACLs |

No Kotlin, Gradle or APK file was touched. `v2.3.0` remains frozen at `279/279`.

---

## Captured transcript (live)

Produced by `supabase/verify/run_ias_assertions.sh` against the hosted project
after `0006`. Credential-free: the connection string lives in a `0600` file
outside the repository and reaches psql through an env-file, never argv.

```text

------------------------------------------------------------------------
ASSERTION 1  relrowsecurity=true and relforcerowsecurity=true on every public table; zero anon privileges

------------------------------------------------------------------------
SQL> select relname, relrowsecurity, relforcerowsecurity from pg_class ... where relkind='r'
psql: warning: extra command-line argument "live" ignored
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
psql: warning: extra command-line argument "live" ignored
tables missing ENABLE or FORCE: 0

SQL> select table_name, privilege_type from information_schema.role_table_grants where grantee='anon'
psql: warning: extra command-line argument "live" ignored
(no rows)
psql: warning: extra command-line argument "live" ignored
psql: warning: extra command-line argument "live" ignored
anon table grants: 0   anon column grants: 0
ASSERTION 1: PASS

------------------------------------------------------------------------
ASSERTION 2  SET LOCAL ROLE anon against customer/invoice tables and the customer views

------------------------------------------------------------------------

SQL> begin; set local role anon; select count(*) from public.customer; commit;
psql: warning: extra command-line argument "live" ignored
ERROR:  permission denied for table customer
HINT:  Grant the required privileges to the current role with: GRANT SELECT ON public.customer TO anon;
BEGIN
SET
  -> denied

SQL> begin; set local role anon; select count(*) from public.invoice; commit;
psql: warning: extra command-line argument "live" ignored
BEGIN
SET
ERROR:  permission denied for table invoice
HINT:  Grant the required privileges to the current role with: GRANT SELECT ON public.invoice TO anon;
  -> denied

SQL> begin; set local role anon; select count(*) from public.order; commit;
psql: warning: extra command-line argument "live" ignored
ERROR:  permission denied for table order
HINT:  Grant the required privileges to the current role with: GRANT SELECT ON public."order" TO anon;
BEGIN
SET
  -> denied

SQL> begin; set local role anon; select count(*) from public.customer_catalog; commit;
psql: warning: extra command-line argument "live" ignored
ERROR:  permission denied for view customer_catalog
BEGIN
SET
  -> denied

SQL> begin; set local role anon; select count(*) from public.customer_invoice_view; commit;
psql: warning: extra command-line argument "live" ignored
ERROR:  permission denied for view customer_invoice_view
BEGIN
SET
  -> denied
ASSERTION 2: PASS

------------------------------------------------------------------------
ASSERTION 3  customer_invoice_view exposes no internal cost/margin/review column

------------------------------------------------------------------------
SQL> select column_name from information_schema.columns where table_name='customer_invoice_view'
psql: warning: extra command-line argument "live" ignored
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
psql: warning: extra command-line argument "live" ignored
forbidden columns present: (none)
psql: warning: extra command-line argument "live" ignored
product table does hold: cost pack_cost unit_cost 
SQL> select sku, price, unit_cost, pack_cost, cost from public.product order by product_id;
psql: warning: extra command-line argument "live" ignored
SYN-W1|100.00|40.00|45.00|40.00
SYN-T1|50.00|20.00|25.00|20.00
psql: warning: extra command-line argument "live" ignored
products with non-zero unit_cost and pack_cost: 2
ASSERTION 3: PASS

------------------------------------------------------------------------
ASSERTION 4  UPDATE and DELETE on the append-only ledgers must fail

------------------------------------------------------------------------

SQL> update public.activity_log set detail = 'TAMPER' where true;
psql: warning: extra command-line argument "live" ignored
ERROR:  public.activity_log is append-only: update is not permitted
HINT:  Write a compensating entry instead of mutating history.
CONTEXT:  PL/pgSQL function enforce_append_only() line 5 at RAISE
  -> blocked by append-only trigger

SQL> delete from public.activity_log where true;
psql: warning: extra command-line argument "live" ignored
ERROR:  public.activity_log is append-only: delete is not permitted
HINT:  Write a compensating entry instead of mutating history.
CONTEXT:  PL/pgSQL function enforce_append_only() line 5 at RAISE
  -> blocked by append-only trigger

SQL> update public.stock_adjustment set reason = 'TAMPER' where true;
psql: warning: extra command-line argument "live" ignored
ERROR:  public.stock_adjustment is append-only: update is not permitted
HINT:  Write a compensating entry instead of mutating history.
CONTEXT:  PL/pgSQL function enforce_append_only() line 5 at RAISE
  -> blocked by append-only trigger

SQL> delete from public.stock_adjustment where true;
psql: warning: extra command-line argument "live" ignored
ERROR:  public.stock_adjustment is append-only: delete is not permitted
HINT:  Write a compensating entry instead of mutating history.
CONTEXT:  PL/pgSQL function enforce_append_only() line 5 at RAISE
  -> blocked by append-only trigger

SQL> select has_table_privilege('authenticated','public.activity_log','UPDATE') as can_update,
          has_table_privilege('authenticated','public.activity_log','DELETE') as can_delete;
psql: warning: extra command-line argument "live" ignored
false|false|false
  (columns: authenticated_can_update | authenticated_can_delete | anon_can_update)

SQL> triggers on the ledgers
psql: warning: extra command-line argument "live" ignored
activity_log|activity_log_append_only|t
stock_adjustment|stock_adjustment_append_only|t
ASSERTION 4: PASS

------------------------------------------------------------------------
ASSERTION 5  receipts bucket exists with public = false

------------------------------------------------------------------------
SQL> select id, name, public from storage.buckets;
psql: warning: extra command-line argument "live" ignored
receipts|receipts|f
psql: warning: extra command-line argument "live" ignored
receipts.public = f

SQL> policies on storage.objects
psql: warning: extra command-line argument "live" ignored
receipts_owner_delete|DELETE
receipts_owner_read|SELECT
receipts_owner_write|INSERT
ASSERTION 5: PASS

------------------------------------------------------------------------
ALL 5 ASSERTIONS PASSED

------------------------------------------------------------------------
```

## Assertion results (live)

| # | Assertion | Result | Evidence |
|---|---|---|---|
| 1 | `pg_class`: ENABLE + FORCE RLS on every `public` table; zero `anon` privileges | **PASS** | 16/16 forced, 0 anon grants |
| 2 | `SET LOCAL ROLE anon` against customer/invoice tables and views | **PASS** | permission denied on all 5 |
| 3 | Customer-Safe view omits cost/margin/review columns | **PASS** | 11 columns, none internal; base table holds 40.00/45.00 |
| 4 | `UPDATE`/`DELETE` on append-only ledgers | **PASS** | `is append-only` error on both ledgers, both verbs |
| 5 | `receipts` bucket `public = false` | **PASS** | `receipts` / `receipts` / `f`, 3 owner-only object policies |

## Reproducing

```bash
# hosted project: export the connection string, then
SEED=1 supabase/verify/deploy_to_supabase.sh    # inspect -> apply -> seed -> assert

# local container, assertions 1-4. A bare Postgres has no storage schema, so
# assertion 5 fails there by design - the migration must not invent a schema the
# platform owns.
docker run -d --name momo_ias_pg -e POSTGRES_PASSWORD=ci -e POSTGRES_DB=momo postgres:16
for f in supabase/migrations/000*.sql; do
  docker exec -i momo_ias_pg psql -U postgres -d momo -v ON_ERROR_STOP=1 -q < "$f"
done
PGCONTAINER=momo_ias_pg supabase/verify/run_ias_assertions.sh
```

## Residual risks

| Risk | Status |
|---|---|
| `storage` schema ACLs owned by `supabase_admin` | Not modified - `0006` scopes itself to `public`. The `receipts` bucket is protected by its object policies in `0005` |
| `service_role` key | Keeps data-level access by design (it bypasses RLS). TRUNCATE/TRIGGER removed, so a leaked key is not a one-statement wipe |
| Anon key in the shipped APK | Publishable by design; safety now rests on RLS **and** the revoke. Re-verify after any migration that adds a table |
| Two table families | Documented above; not removed, to preserve the F-01 demonstration |
