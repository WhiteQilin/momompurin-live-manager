-- ============================================================================
--  0005_append_only_audit_and_receipts.sql
--  Closes the two IAS Revision 2 controls that 0001-0004 specify but never
--  implement, and finishes the FORCE ROW LEVEL SECURITY story for the domain
--  schema.
--
--  Why this is a separate migration rather than an edit to 0003/0004:
--  0001-0004 are the migrations CI Gate 2 has already applied and verified, and
--  0004 is the migration that the v2.3 release notes cite. Rewriting them would
--  change what is already deployed under a tag. Additive is safer and reviewable.
--
--  What was missing at the time of writing (measured, not assumed):
--    * no BEFORE UPDATE OR DELETE trigger anywhere  -> assertion 4 could not fail
--    * no REVOKE of UPDATE/DELETE on the log        -> tamper-evident by claim only
--    * the 11 singular domain tables: ENABLE ROW LEVEL SECURITY but no FORCE
--    * no storage schema/bucket                     -> assertion 5 could not pass
--  All four are fixed below, and every statement is idempotent so the file can
--  be re-applied against a database where part of it already ran.
--
--  ON THE TWO TABLE FAMILIES - read before "fixing" this:
--  A database built from 0001-0004 holds 16 tables in `public`, not 11. 0001
--  creates the plural PoC family (customers, orders, order_lines, inventory,
--  invoices) and 0003 creates the singular domain family (customer, product,
--  order, order_line, invoice). They are different schemas that happen to share
--  a noun, and both are intentional:
--
--    * 0001's plural family is the IAS "F-01" reproduction - the state the
--      Phase 1 APK actually shipped against, RLS disabled, kept so the defect can
--      be demonstrated and then closed by 0002. Its tables are referenced by
--      name in the security audit and by the benchmark provenance note.
--    * 0003's singular family is the Phase 1 domain model the app's business
--      rules were written against.
--
--  Dropping the plural family was considered and rejected: it would break the
--  F-01 demonstration, break any deployment that already holds rows in it, and
--  change what the v2.3.0 tag deploys. It is left in place and documented in
--  docs/SUPABASE_IAS_DEPLOYMENT_PROOF.md under "Known duplication" instead. Both
--  families are fully RLS-forced and anon-free, so the security posture is
--  identical either way; the duplication is a tidiness and clarity problem, not
--  an exposure.
-- ============================================================================


-- ============================================================================
--  1. Append-only audit and inventory ledgers
-- ============================================================================
--  `activity_log` is the audit trail (actor, action, entity, detail).
--  `stock_adjustment` is the inventory ledger (direction, qty, reason).
--
--  Both are histories: a ledger you can rewrite is not evidence. The control is
--  three layers deep on purpose, because each layer alone has a hole:
--
--    (a) REVOKE UPDATE, DELETE   stops any non-superuser role from even trying.
--        Alone it is insufficient: a role granted ALL later, or a SECURITY
--        DEFINER function, would bypass it.
--    (b) A BEFORE UPDATE OR DELETE trigger raises. This is the layer that holds
--        regardless of privileges, and it is what the IAS assertion checks.
--    (c) RLS FORCE                makes RLS apply to the table owner too, so a
--        policy mistake cannot quietly become a bypass.
--
--  Note what is deliberately absent: no `for each row` on an UPDATE that filters
--  rows. A BEFORE STATEMENT trigger can veto the statement even when it would
--  have matched zero rows, which is what makes the control testable on an empty
--  table. A row-level trigger would happily report success against 0 rows and
--  the test would pass for the wrong reason.

create or replace function public.enforce_append_only()
returns trigger
language plpgsql
as $$
begin
  -- The message is asserted verbatim by the deployment proof, so the table name
  -- is interpolated rather than hardcoded: one function, any ledger.
  raise exception 'public.% is append-only: % is not permitted', tg_table_name, lower(tg_op)
    using errcode = 'restrict_violation',
          hint = 'Write a compensating entry instead of mutating history.';
end;
$$;

comment on function public.enforce_append_only() is
  'BEFORE UPDATE OR DELETE trigger body for append-only ledgers (IAS R.1). Raises '
  'restrict_violation naming the table and operation.';

drop trigger if exists activity_log_append_only on public.activity_log;
create trigger activity_log_append_only
  before update or delete on public.activity_log
  for each statement execute function public.enforce_append_only();

drop trigger if exists stock_adjustment_append_only on public.stock_adjustment;
create trigger stock_adjustment_append_only
  before update or delete on public.stock_adjustment
  for each statement execute function public.enforce_append_only();

-- Layer (a). `revoke ... from public` too: PUBLIC is the default grantee for a
-- function, and on a table it is the catch-all that a later grant inherits past.
revoke update, delete, truncate on public.activity_log     from authenticated, anon, public;
revoke update, delete, truncate on public.stock_adjustment from authenticated, anon, public;

-- Insert and select stay: the owner appends and reads. 0004 already attaches
-- owner_insert_activity_log / owner_read_activity_log, so the policy gate is
-- unchanged; this migration only removes the two verbs that contradict
-- "append-only".
grant select, insert on public.activity_log     to authenticated;
grant select, insert on public.stock_adjustment to authenticated;


-- ============================================================================
--  2. FORCE ROW LEVEL SECURITY on the domain tables
-- ============================================================================
--  0002 forced the five PoC tables; 0004 enabled RLS on the eleven domain
--  tables but never forced it. ENABLE without FORCE leaves the table owner
--  exempt, so a query running as the owner - which is what a SECURITY DEFINER
--  helper or a psql session as `postgres` does - skips every policy. The
--  security checklist claims "RLS forced on every table", so this is the
--  statement that makes the claim true rather than approximately true.

alter table public.customer         force row level security;
alter table public.product          force row level security;
alter table public.product_option   force row level security;
alter table public."order"          force row level security;
alter table public.order_line       force row level security;
alter table public.invoice          force row level security;
alter table public.payment          force row level security;
alter table public.shipment         force row level security;
alter table public.stock_adjustment force row level security;
alter table public.settings         force row level security;
alter table public.activity_log     force row level security;


-- ============================================================================
--  3. Private `receipts` bucket for short-lived pre-signed URLs (IAS R.2)
-- ============================================================================
--  Supabase Storage keeps buckets in `storage.buckets` and objects in
--  `storage.objects`, both created by the platform. This migration does not
--  create the schema: it would collide with the platform's own definition on a
--  hosted project. On a bare Postgres (CI, or the local harness) the schema is
--  absent, so the block is guarded and skips instead of failing the migration.
--
--  `public = false` is the whole control: it makes every object unreadable
--  without a signed URL, so possession of a path grants nothing. The 15-minute
--  lifetime is a property of the signed URL the server mints
--  (`createSignedUrl` with `expiresIn: 900`), not a column on the bucket -
--  buckets cannot carry an expiry, so it belongs in the server code and is
--  asserted there, not here.
do $$
begin
  if to_regclass('storage.buckets') is null then
    raise notice 'storage schema absent (bare Postgres) - receipts bucket skipped';
    return;
  end if;

  insert into storage.buckets (id, name, public)
  values ('receipts', 'receipts', false)
  on conflict (id) do update set public = false;   -- re-assert private on re-run

  raise notice 'receipts bucket present and private';
end;
$$;


-- ============================================================================
--  4. Object policies for the bucket
-- ============================================================================
--  A private bucket is not automatically safe from the owner app: RLS on
--  storage.objects decides who may read a signed URL's target and who may
--  upload. Owner-only, matching every other table in this schema.
do $$
begin
  if to_regclass('storage.objects') is null then
    return;
  end if;

  -- Only manage policies for rows in our bucket; other buckets are not ours.
  drop policy if exists receipts_owner_read   on storage.objects;
  drop policy if exists receipts_owner_write  on storage.objects;
  drop policy if exists receipts_owner_delete on storage.objects;

  create policy receipts_owner_read on storage.objects
    for select to authenticated
    using (bucket_id = 'receipts' and public.is_owner());

  create policy receipts_owner_write on storage.objects
    for insert to authenticated
    with check (bucket_id = 'receipts' and public.is_owner());

  create policy receipts_owner_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'receipts' and public.is_owner());

  raise notice 'receipts object policies applied (owner-only)';
end;
$$;
