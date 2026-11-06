-- ============================================================================
--  0002_enable_rls_and_policies.sql
--  Hardening migration. Same database, same rows, same client APK.
--
--  Three changes, each independently sufficient to close the exposure:
--    1. revoke every grant the public API role holds
--    2. enable Row Level Security on every table in the exposed schema
--    3. attach an explicit owner-only policy per table
--
--  The last step is the one that matters. Grants alone are not enough: PostgREST
--  runs as anon, and once anon holds SELECT the absence of a policy is an
--  absence of authorization, not a default-deny.
-- ============================================================================

-- JWT claims are delivered by PostgREST in a GUC; mirror that for the harness.
grant usage on schema auth to anon, authenticated, service_role;

create or replace function auth.jwt() returns jsonb
language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb, '{}'::jsonb)
$$;

-- The owner account is the only account the Phase 1 app can create. The claim
-- is set by the Supabase Auth hook at sign-up, not by the client, so a modified
-- APK cannot mint it.
create or replace function public.is_owner() returns boolean
language sql stable as $$
  select coalesce((auth.jwt() -> 'app_metadata' ->> 'role') = 'owner', false)
$$;

-- 1. remove the public role from the data plane entirely
revoke all on all tables in schema public from anon;
revoke all on all sequences in schema public from anon;

-- 2. enable RLS everywhere. A table added later without RLS is caught by the
--    CI assertion in .github/workflows/security.yml, not by a human reviewer.
alter table public.customers   enable row level security;
alter table public.orders      enable row level security;
alter table public.order_lines enable row level security;
alter table public.inventory   enable row level security;
alter table public.invoices    enable row level security;

alter table public.customers   force row level security;
alter table public.orders      force row level security;
alter table public.order_lines force row level security;
alter table public.inventory   force row level security;
alter table public.invoices    force row level security;

-- 3. explicit policies. Default deny is what an unlisted operation gets.
drop policy if exists customers_owner_all on public.customers;
create policy customers_owner_all on public.customers
  for all to authenticated
  using (public.is_owner())
  with check (public.is_owner());

drop policy if exists orders_owner_all on public.orders;
create policy orders_owner_all on public.orders
  for all to authenticated
  using (public.is_owner())
  with check (public.is_owner());

drop policy if exists order_lines_owner_all on public.order_lines;
create policy order_lines_owner_all on public.order_lines
  for all to authenticated
  using (public.is_owner())
  with check (public.is_owner());

drop policy if exists inventory_owner_all on public.inventory;
create policy inventory_owner_all on public.inventory
  for all to authenticated
  using (public.is_owner())
  with check (public.is_owner());

drop policy if exists invoices_owner_all on public.invoices;
create policy invoices_owner_all on public.invoices
  for all to authenticated
  using (public.is_owner())
  with check (public.is_owner());

-- Phase 2 only: a customer may read their own orders and nothing else. Kept
-- here, disabled, so the intent is recorded before the customer app exists.
-- create policy orders_customer_self on public.orders
--   for select to authenticated
--   using (customer_id = auth.uid());
