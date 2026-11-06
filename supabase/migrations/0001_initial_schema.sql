-- ============================================================================
--  0001_initial_schema.sql
--  Momompurin Live Manager - Phase 1 (Android) remote schema
--
--  This is the state the application shipped to the client's device. It models
--  the Supabase project as created by the team: tables, grants, and the anon
--  role that every copy of the APK authenticates as.
--
--  DEFECT (intentional, reproduced for the IAS Milestone PoC):
--  Row Level Security is never enabled. Supabase's PostgREST exposes every
--  table it can see to any role holding the project's anon key, and that key
--  is compiled into the APK. The client is therefore untrusted by construction
--  and the database has no policy standing between it and the internet.
-- ============================================================================

create schema if not exists auth;

-- Supabase's auth.uid() reads the JWT "sub" claim from the connection GUC.
-- Reproduced here so the policies in 0002 are the same text that ships.
create or replace function auth.uid() returns uuid
language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

-- ---------------------------------------------------------------- tables ----
create table public.customers (
  id           uuid primary key default gen_random_uuid(),
  fb_handle    text not null unique,
  full_name    text,
  phone        text,
  province     text,
  city         text,
  barangay     text,
  address_line text,
  created_at   timestamptz not null default now()
);

create table public.orders (
  id            uuid primary key default gen_random_uuid(),
  order_number  text not null unique,
  customer_id   uuid not null references public.customers(id),
  live_session  text not null,
  order_status  text not null default 'CLAIMED',
  keep_flag     boolean not null default false,
  total_amount  numeric(10,2) not null default 0,
  created_at    timestamptz not null default now()
);

create table public.order_lines (
  id         uuid primary key default gen_random_uuid(),
  order_id   uuid not null references public.orders(id) on delete cascade,
  sku        text not null,
  item_name  text not null,
  qty        integer not null check (qty > 0),
  unit_price numeric(10,2) not null
);

create table public.inventory (
  sku       text primary key,
  item_name text not null,
  character text,
  unit_cost numeric(10,2) not null,
  srp       numeric(10,2) not null,
  stock     integer not null default 0
);

create table public.invoices (
  id             uuid primary key default gen_random_uuid(),
  order_id       uuid not null references public.orders(id),
  invoice_number text not null unique,
  total          numeric(10,2) not null,
  status         text not null default 'UNPAID',
  issued_at      timestamptz not null default now()
);

-- ---------------------------------------------------------------- roles -----
-- Supabase ships these three roles. anon is the one compiled into the APK.
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon nologin noinherit;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin noinherit;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then
    create role service_role nologin noinherit bypassrls;
  end if;
end $$;

grant usage on schema public to anon, authenticated, service_role;

-- THE DEFECT: broad table grants to the public API role, with no policy behind
-- them. In Supabase this is what `alter default privileges` plus a missing
-- "Enable RLS" click in the dashboard produces.
grant select, insert, update, delete on all tables in schema public
  to anon, authenticated;

-- ---------------------------------------------------------------- seed ------
-- Synthetic records shaped like the client workbook (CLIENT ANSWERS /
-- Momompurin Live Manager.xlsx): FB handles as customer identifiers, ORD-nnnn
-- numbering, LIVE-nnnn sessions. No real personal data is present.
insert into public.customers (fb_handle, full_name, phone, province, city, barangay, address_line) values
  ('@ae',              'Ana E.',        '0917-000-0001', 'Metro Manila', 'Makati',    'West Rembo',  '12 J.P. Rizal Ext.'),
  ('@aly',             'Aly R.',        '0917-000-0002', 'Cebu',         'Cebu City', 'Lahug',       '88 Gorordo Ave.'),
  ('@apolchiatea',     'Apol C.',       '0917-000-0003', 'Laguna',       'Calamba',   'Real',        '5 National Hwy'),
  ('@jazzyfindscuties','Jazzy F.',      '0917-000-0004', 'Metro Manila', 'Taguig',    'Ususan',      '31 M.L. Quezon'),
  ('@meowez',          'Meow E.',       '0917-000-0005', 'Pampanga',     'Angeles',   'Balibago',    '7 Fields Ave.'),
  ('@princess',        'Princess S.',   '0917-000-0006', 'Metro Manila', 'Pasig',     'Ortigas',     '19 Emerald Ave.'),
  ('@mhel',            'Mhel D.',       '0917-000-0007', 'Batangas',     'Lipa',      'Sabang',      '3 Gen. Luna St.'),
  ('@len_fb',          'Len B.',        '0917-000-0008', 'Metro Manila', 'Quezon City','Batasan',    '44 Commonwealth');

insert into public.orders (order_number, customer_id, live_session, order_status, keep_flag, total_amount)
select v.num, c.id, v.session, v.status, v.keep, v.total
from (values
  ('ORD-0070','@ae','LIVE-0007','CLAIMED', false,  710.00),
  ('ORD-0071','@aly','LIVE-0007','PACKED',  true,  6380.00),
  ('ORD-0072','@apolchiatea','LIVE-0007','CLAIMED', false, 160.00),
  ('ORD-0073','@jazzyfindscuties','LIVE-0007','SHIPPED', false, 785.00),
  ('ORD-0074','@meowez','LIVE-0008','CLAIMED', false, 2365.00),
  ('ORD-0075','@princess','LIVE-0008','PACKED', true, 1295.00),
  ('ORD-0076','@mhel','LIVE-0008','CLAIMED', false, 4620.00),
  ('ORD-0077','@len_fb','LIVE-0008','CANCELLED', false, 0.00),
  ('ORD-0078','@ae','LIVE-0008','CLAIMED', false, 350.00),
  ('ORD-0079','@aly','LIVE-0008','CLAIMED', true, 890.00),
  ('ORD-0080','@jazzyfindscuties','LIVE-0008','PACKED', false, 135.00),
  ('ORD-0081','@princess','LIVE-0008','CLAIMED', false, 640.00),
  ('ORD-0082','@mhel','LIVE-0008','CLAIMED', false, 1120.00)
) as v(num, handle, session, status, keep, total)
join public.customers c on c.fb_handle = v.handle;

insert into public.order_lines (order_id, sku, item_name, qty, unit_price)
select o.id, v.sku, v.item, v.qty, v.price
from (values
  ('ORD-0070','W1','Hangyodon Wipes',2,355.00),
  ('ORD-0074','MCP8','My Melody Ceramic Plate',1,350.00),
  ('ORD-0074','CCP','Cinnamoroll Ceramic Plate',1,300.00),
  ('ORD-0076','HBPM','Melody Pencils Set 12p',4,135.00),
  ('ORD-0081','T1','Hangyodon Tissue',8,80.00)
) as v(num, sku, item, qty, price)
join public.orders o on o.order_number = v.num;

insert into public.inventory (sku, item_name, character, unit_cost, srp, stock) values
  ('W1',  'Hangyodon Wipes',            'Hangyodon',  22.50, 45.00,   8),
  ('T1',  'Hangyodon Tissue',           'Hangyodon',  20.17, 35.00,   5),
  ('W2',  'HK mini wipes 8pcs Blue',    'Hello Kitty',11.56, 35.00,   8),
  ('MCP8','My Melody Ceramic Plate',    'My Melody', 210.00, 350.00,  3),
  ('CCP', 'Cinnamoroll Ceramic Plate',  'Cinnamoroll',180.00, 300.00, 4),
  ('HBPM','Melody Pencils Set 12p',     'My Melody',  78.00, 135.00, 12);

insert into public.invoices (order_id, invoice_number, total, status)
select o.id, 'INV-' || right(o.order_number, 4), o.total_amount, 'UNPAID'
from public.orders o where o.order_status <> 'CANCELLED';
