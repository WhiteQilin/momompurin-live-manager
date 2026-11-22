-- ============================================================================
--  0004_phase1_rls_and_policies.sql
--  Row Level Security for the ten Phase 1 domain tables created in 0003.
--
--  0002 established the pattern for the five PoC tables: revoke the anon grant,
--  enable RLS, attach an owner-only policy, and assert at startup that an
--  unauthenticated request returns nothing. This migration applies the same
--  pattern to the domain model, plus the read-only customer projections that
--  G5.3, C1.2 and C2.1 require.
--
--  The customer shared views are the interesting part: they are not "the owner
--  tables with a different WHERE clause". They are separate projections whose
--  column list excludes cost, unit_cost, net_earnings and supplier fields, so
--  an internal margin cannot leak through a customer route even by accident.
-- ============================================================================

-- ------------------------------------------------------------ enable RLS ----
alter table public.customer          enable row level security;
alter table public.product           enable row level security;
alter table public.product_option    enable row level security;
alter table public."order"           enable row level security;
alter table public.order_line        enable row level security;
alter table public.invoice           enable row level security;
alter table public.payment           enable row level security;
alter table public.shipment          enable row level security;
alter table public.stock_adjustment  enable row level security;
alter table public.settings          enable row level security;
alter table public.activity_log      enable row level security;

-- -------------------------------------------------------- owner policies ----
-- public.is_owner() was created in 0002 and reads the JWT app_metadata claim,
-- which a client cannot mint. Every domain table uses the same predicate, so
-- there is one place to audit.

drop policy if exists owner_all_customer on public.customer;
create policy owner_all_customer on public.customer
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_product on public.product;
create policy owner_all_product on public.product
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_product_option on public.product_option;
create policy owner_all_product_option on public.product_option
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_order on public."order";
create policy owner_all_order on public."order"
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_order_line on public.order_line;
create policy owner_all_order_line on public.order_line
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_invoice on public.invoice;
create policy owner_all_invoice on public.invoice
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_payment on public.payment;
create policy owner_all_payment on public.payment
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_shipment on public.shipment;
create policy owner_all_shipment on public.shipment
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_stock_adjustment on public.stock_adjustment;
create policy owner_all_stock_adjustment on public.stock_adjustment
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

drop policy if exists owner_all_settings on public.settings;
create policy owner_all_settings on public.settings
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

-- The log is append-and-read: an edit to history destroys the audit trail.
drop policy if exists owner_read_activity_log on public.activity_log;
create policy owner_read_activity_log on public.activity_log
  for select to authenticated using (public.is_owner());

drop policy if exists owner_insert_activity_log on public.activity_log;
create policy owner_insert_activity_log on public.activity_log
  for insert to authenticated with check (public.is_owner());

-- ------------------------------------------------- customer shared views -----
-- G5.3 / C1.2 / C2.1: a customer view is a read-only projection with the
-- internal columns absent. The views expose only what the catalog and the shared
-- invoice show. `security_invoker = false` is deliberate: the view runs with its
-- owner's rights, which is how an anon caller reads a projection of an
-- RLS-protected table without being granted the table.

create or replace view public.customer_catalog
with (security_invoker = false) as
select
  p.product_id,
  p.sku,
  p.name,
  p.character,
  p.category,
  p.price,
  -- Available is computed, never a raw stock figure: the customer sees "in
  -- stock" or not, not how many units sit in the owner's storeroom.
  (p.stock_qty - p.reserved_qty - p.shipped_qty) as available_qty,
  p.image_ref
from public.product p
where p.is_parent = false;          -- a parent is never offered (F4.8)
-- NOTE: cost, unit_cost, pack_cost, srp, net_earnings and supplier fields are
-- absent by construction. Do not add them.

create or replace view public.customer_invoice_view
with (security_invoker = false) as
select
  i.invoice_id,
  i.order_id,
  i.issued_at,
  i.total,
  o.live_session,
  c.name        as customer_name,
  ol.sku,
  ol.item_name,
  ol.qty,
  ol.unit_price,
  ol.is_freebie
from public.invoice i
join public."order" o   on o.order_id = i.order_id
join public.customer c  on c.customer_id = o.customer_id
left join public.order_line ol on ol.order_id = o.order_id;
-- As above: no item_cost_spent, freebies_spent, packaging_spent,
-- total_cost_spent or net_earnings. The customer sees what they pay,
-- never what the owner paid.

grant select on public.customer_catalog, public.customer_invoice_view to anon, authenticated;

-- ----------------------------------------------------- startup assertion ----
-- s.4.3: "Assert at startup that an unauthenticated request returns zero rows
-- from every owner table." This function is the server-side half of that check;
-- the app calls it once per launch and refuses to render owner screens if any
-- count is non-zero.
--
-- Implementation note: the counts must be taken AS THE anon ROLE, so the function
-- switches role for the duration of the query. A security-definer function that
-- merely tested `current_user = 'anon'` would test its own owner and pass
-- vacuously - an assertion that can never fail is worse than no assertion.
create or replace function public.assert_anon_sees_nothing()
returns table (relation text, visible_rows bigint)
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
begin
  for r in
    select c.relname
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relkind = 'r'
      and c.relrowsecurity            -- only tables we claim to have protected
    order by c.relname
  loop
    -- Run each count with anon's privileges. EXECUTE runs as the caller, so the
    -- role switch is what makes the number meaningful.
    set local role anon;
    execute format('select count(*) from public.%I', r.relname) into visible_rows;
    reset role;
    relation := r.relname;
    return next;
  end loop;
end;
$$;

revoke all on function public.assert_anon_sees_nothing() from public;
grant execute on function public.assert_anon_sees_nothing() to authenticated;

comment on view public.customer_catalog is
  'Read-only customer projection. Internal cost columns must stay absent (G5.3, C2.1).';
comment on view public.customer_invoice_view is
  'Read-only shared invoice. No cost or margin columns (C1.2, G5.3).';
