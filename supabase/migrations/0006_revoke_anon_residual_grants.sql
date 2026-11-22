-- ============================================================================
--  0006_revoke_anon_residual_grants.sql
--  Closes a privilege-escalation path that only exists on a hosted Supabase
--  project. Found by running the IAS assertions against the real project, not
--  by reading the schema.
--
--  THE DEFECT
--  ----------
--  Supabase sets ALTER DEFAULT PRIVILEGES on the public schema so every table
--  created afterwards is granted to anon, authenticated and service_role with
--  `arwdDxtm` - that is, ALL plus TRUNCATE, REFERENCES, TRIGGER and MAINTAIN.
--  Confirmed on PostgreSQL 17.12:
--
--      select defaclacl from pg_default_acl
--      where defaclnamespace = 'public'::regnamespace and defaclobjtype = 'r';
--      -> {postgres=arwdDxtm/postgres,anon=Dxtm/postgres,
--          authenticated=Dxtm/postgres,service_role=Dxtm/postgres}
--
--  0002's `revoke all on all tables in schema public from anon` fires at that
--  moment and covers only the tables that already exist. Every table 0003 and
--  0004 create afterwards silently comes back with `Dxtm` granted to anon.
--
--  WHY IT MATTERS
--  --------------
--  Row Level Security does **not** apply to TRUNCATE. RLS filters rows returned
--  or written by SELECT/INSERT/UPDATE/DELETE; TRUNCATE removes the storage
--  wholesale and is governed by table privilege alone. So an `anon` holder can
--  empty a table with no policy ever being consulted.
--
--      begin; set local role anon; truncate public.payment; rollback;
--      -> TRUNCATE TABLE          (succeeded)
--
--  The anon key is publishable by design and ships compiled into the APK, so
--  "anon can TRUNCATE every domain table" means anyone who unzips the released
--  APK can destroy the client's data. The FORCE RLS work in 0005 does not help:
--  it strengthens a mechanism TRUNCATE never consults.
--
--  Other residual verbs and their severity:
--    REFERENCES  - lets anon add an FK pointing at the table; a lock/DoS vector,
--                  no data read.
--    TRIGGER     - lets anon attach a trigger to the table; arbitrary code in
--                  the table's context on every write. Worse than TRUNCATE.
--    MAINTAIN    - VACUUM/ANALYZE/REINDEX/CLUSTER privilege; DoS vector.
--
--  THE FIX
--  -------
--  1. Revoke ALL from anon on every table, sequence and function in public,
--     which clears the default-ACL residue rather than naming four verbs.
--  2. Revoke the TRUNCATE/REFERENCES/TRIGGER/MAINTAIN verbs from authenticated
--     and service_role too. The owner app has no use for them; service_role
--     bypasses RLS by design but should not be able to truncate either, so an
--     accidental use of that key is not a one-statement wipe.
--  3. Remove the default privileges themselves, so any FUTURE table created in
--     public does not inherit `Dxtm` for anon again. This is the part that
--     stops the defect recurring - a revoke alone would be a treadmill.
--  4. Re-state the grants the app actually needs: SELECT/INSERT/UPDATE/DELETE
--     to authenticated, and SELECT/INSERT on the two ledgers (0005 already
--     removed UPDATE/DELETE there).
--
--  Idempotent: revoke and alter default privileges are both safe to re-run.
--
--  NOTE ON SCOPE: this revokes in `public` only. The `storage` schema is owned
--  by supabase_admin and its default ACLs are the platform's business; the
--  receipts bucket is protected by its object policies in 0005.
-- ============================================================================

-- ---------------------------------------------------------- 1. current tables
-- ALL rather than a verb list: `revoke all` cannot miss a verb that a future
-- Postgres version adds to the default ACL, which is exactly how this defect
-- appeared.
revoke all on all tables    in schema public from anon;
revoke all on all sequences in schema public from anon;
revoke all on all functions in schema public from anon;

-- ------------------------------------------------------- 2. mutating verbs --
-- TRUNCATE and TRIGGER are the dangerous pair. REFERENCES and MAINTAIN are
-- denial-of-service vectors with no legitimate use for this app.
revoke truncate, references, trigger on all tables in schema public
  from authenticated, service_role;

-- service_role is documented as a bypass-RLS key for server-side jobs. It still
-- has no reason to destroy storage wholesale, so the two catastrophic verbs are
-- removed from it explicitly.
revoke truncate, trigger on all tables in schema public from service_role;

-- --------------------------------------------------- 3. default privileges --
-- The root cause. `alter default privileges` only affects tables created by the
-- role that runs it, which is `postgres` here - the same role Supabase's own
-- default ACL was attached to, so the two cancel out for future objects.
alter default privileges in schema public
  revoke all on tables from anon;
alter default privileges in schema public
  revoke all on sequences from anon;
alter default privileges in schema public
  revoke all on functions from anon;

alter default privileges in schema public
  revoke truncate, references, trigger on tables
  from authenticated, service_role;

-- ------------------------------------------------- 4. re-state what is used --
-- 0003 already granted these; restating them makes this migration correct on
-- its own and documents the intended surface in one place.
grant select, insert, update, delete on
  public.customer, public.product, public.product_option, public."order",
  public.order_line, public.invoice, public.payment, public.shipment,
  public.settings
  to authenticated;

-- The ledgers are append-only (0005): no UPDATE, no DELETE, ever.
grant select, insert on public.activity_log     to authenticated;
grant select, insert on public.stock_adjustment to authenticated;

-- The audit ledger stays readable by the owner only, which 0004's policy already
-- enforces; service_role keeps full data access for server-side reporting.
grant select, insert, update, delete on
  public.customer, public.product, public.product_option, public."order",
  public.order_line, public.invoice, public.payment, public.shipment,
  public.settings, public.stock_adjustment, public.activity_log
  to service_role;

-- MAINTAIN is PostgreSQL 17+; naming it on 16 raises "unrecognized privilege
-- type". CI runs 16 and the hosted project runs 17, so it is handled in the
-- version-guarded block below rather than inline.
revoke truncate, references, trigger on all tables in schema public
  from service_role;


-- --------------------------------------------------- 5. MAINTAIN (PG 17+) --
-- PostgreSQL 17 added the MAINTAIN privilege (VACUUM/ANALYZE/REINDEX/CLUSTER)
-- and Supabase's default ACL includes it. PostgreSQL 16 does not know the token
-- at all and errors, so this is executed only where it parses.
do $$
begin
  if current_setting('server_version_num')::int >= 170000 then
    execute 'revoke maintain on all tables in schema public from authenticated, service_role';
    execute 'alter default privileges in schema public revoke maintain on tables from authenticated, service_role';
    raise notice 'MAINTAIN revoked (PostgreSQL 17+)';
  else
    raise notice 'MAINTAIN not present below PostgreSQL 17 - skipped';
  end if;
end;
$$;


-- ============================================================================
--  Verification, inline. Any residual anon privilege on a public table is a
--  failure, so this raises rather than warns: a migration that silently leaves
--  the hole open is the defect it is meant to close.
-- ============================================================================
do $$
declare
  leftover text;
begin
  select string_agg(distinct g.table_name || '.' || g.privilege_type, ', ')
    into leftover
  from information_schema.role_table_grants g
  where g.grantee = 'anon' and g.table_schema = 'public';

  if leftover is not null then
    raise exception 'anon still holds table privileges in public: %', leftover
      using hint = 'See 0006_revoke_anon_residual_grants.sql';
  end if;

  raise notice 'anon holds zero privileges on every public table - TRUNCATE path closed';
end;
$$;
