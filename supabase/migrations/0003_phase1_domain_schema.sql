-- ============================================================================
--  0003_phase1_domain_schema.sql
--  Momompurin Live Manager - Phase 1 (Android) domain schema
--
--  Creates the ten entities of `system_modelling_v8/drawio/ERD v8.drawio` and
--  the 97 fields of `system_modelling_v8/spec/dictionary.json`, applying the
--  four schema overrides ANDROID_PROTOTYPE_HANDOFF.md s.4.2 requires.
--
--  Why a new migration rather than an edit of 0001:
--  0001 is the artifact the IAS Milestone PoC analyses - its five tables and
--  its missing RLS are the subject of poc/run_poc.mjs and the security audit.
--  Rewriting it would invalidate both. 0003 adds the real domain model on top,
--  and 0004 attaches the policies that 0001 deliberately omitted.
--
--  Conventions:
--   * money is numeric(12,2). The Android client stores Long cents and divides
--     by 100 for display, so no binary floating point touches a total.
--   * order_type status columns stay three independent columns (s.4.2 item 4).
--   * enum-like columns are CHECK-constrained to the closed vocabulary in
--     s.3.4 - a status outside that table does not exist.
-- ============================================================================

-- ---------------------------------------------------------------- tables ----

-- CUSTOMER: buyer master record with structured delivery details.
create table public.customer (
  customer_id        text primary key,                 -- '@mikat'
  name               text not null,
  contact_number     text,
  email              text,
  receiver_name      text,                             -- defaults to name
  province           text,
  city               text,
  barangay           text,
  address_line       text,
  address_updated_at timestamptz,
  created_at         timestamptz not null default now()
);

-- PRODUCT_OPTION must exist before PRODUCT if we later add an FK; the dictionary
-- keeps character/category as text on PRODUCT and the option list as a managed
-- lookup, which is what s.4.2 item 3 asks for.
create table public.product_option (
  option_id  text primary key,
  kind       text not null check (kind in ('character', 'category')),
  value      text not null,
  sort_order integer not null default 0,
  active     boolean not null default true,
  unique (kind, value)
);

-- PRODUCT: inventory item with pack-based costing and one catalog image.
create table public.product (
  product_id            text primary key,
  sku                   text not null unique,
  name                  text not null,
  character             text,
  category              text,
  cost                  numeric(12,2) not null default 0,
  price                 numeric(12,2) not null default 0,
  stock_qty             integer not null default 0 check (stock_qty >= 0),
  reserved_qty          integer not null default 0 check (reserved_qty >= 0),
  -- Not in the 16-field dictionary, but F-06 c3 defines
  -- available = on hand - reserved - shipped, so shipped has to be a column.
  shipped_qty           integer not null default 0 check (shipped_qty >= 0),
  status                text not null default 'In Stock'
                          check (status in ('In Stock', 'Low Stock', 'Out of Stock')),
  pack_qty              integer not null default 1 check (pack_qty >= 1),
  unit_cost             numeric(12,2) not null default 0,
  freebie_rate          numeric(5,4) not null default 0.03 check (freebie_rate between 0 and 1),
  pack_cost             numeric(12,2) not null default 0,
  srp                   numeric(12,2) not null default 0,
  image_ref             text,
  -- s.4.2 item 2 (N-01): parent-child variants.
  parent_product_id     text references public.product(product_id),
  is_parent             boolean not null default false,
  -- N-10: the electronic tag behind the one-week reminder.
  is_electronic         boolean not null default false,
  pack_size             text not null default 'XS',
  pack_total_cost       numeric(12,2) not null default 0,
  owner_price_override  numeric(12,2),
  active_in_claim_panel boolean not null default true,
  created_at            timestamptz not null default now(),
  -- A parent is never its own parent, and a variant always has one.
  constraint product_parent_not_self check (parent_product_id is null or parent_product_id <> product_id),
  constraint product_variant_shape check (
    (is_parent and parent_product_id is null) or
    (not is_parent)
  )
);
create index product_parent_idx on public.product (parent_product_id);
create index product_sku_lower_idx on public.product (lower(sku));

-- ORDER: one live-session claim bundle with independent state tracks.
create table public.shipment (
  shipment_id     text primary key,
  courier         text not null,
  tracking_no     text,
  shipping_method text not null default 'Delivery'
                    check (shipping_method in ('Pickup', 'Delivery')),
  fee             numeric(12,2) not null default 0,
  dispatch_date   date,
  status          text not null default 'For pickup'
                    check (status in ('For pickup', 'In transit', 'Received', 'Returned')),
  exception_note  text,
  receiver_name   text,
  receiver_phone  text,
  province        text,
  city            text,
  barangay        text,
  address_line    text,
  created_at      timestamptz not null default now()
);

create table public."order" (
  order_id         text primary key,                   -- ORD-1042
  customer_id      text not null references public.customer(customer_id),
  shipment_id      text references public.shipment(shipment_id),
  live_session     text not null,                      -- LIVE-0007
  channel          text,
  claim_time       timestamptz not null default now(),
  order_status     text not null default 'Claimed'
                     check (order_status in ('Claimed', 'Confirmed', 'Cancelled')),
  keep_flag        boolean not null default false,
  -- s.4.2 item 4 (D-01 c10): three independent tracks. Never collapse these.
  payment_status   text not null default 'Waiting'
                     check (payment_status in
                       ('Waiting', 'Paid', 'Paid with balance', 'Paid and kept',
                        'Marketplace paid', 'Cancelled', 'Overdue')),
  packing_status   text not null default 'To prep'
                     check (packing_status in ('To prep', 'Preparing', 'Packed', 'Kept')),
  shipping_status  text not null default 'Not shipped'
                     check (shipping_status in
                       ('Not shipped', 'For pickup', 'In transit', 'Received', 'Returned')),
  -- Computed cost/sales columns from the dictionary.
  item_cost_spent  numeric(12,2) not null default 0,
  freebies_spent   numeric(12,2) not null default 0,
  packaging_spent  numeric(12,2) not null default 0,
  total_cost_spent numeric(12,2) not null default 0,
  order_sales      numeric(12,2) not null default 0,
  net_earnings     numeric(12,2) not null default 0,
  -- Needed by the seed set: shipping is part of the customer-facing total and
  -- the balance is what the payment sheet reconciles against.
  shipping_fee     numeric(12,2) not null default 0,
  amount_paid      numeric(12,2) not null default 0,
  payment_deadline timestamptz,
  created_at       timestamptz not null default now()
);
create index order_customer_idx on public."order" (customer_id);
create index order_session_idx on public."order" (live_session);
create index order_payment_status_idx on public."order" (payment_status);
create index order_claim_time_idx on public."order" (claim_time desc);
-- The deadline heap (algorithms/expiry.py) reads this index instead of scanning.
create index order_payment_deadline_idx on public."order" (payment_deadline)
  where payment_status = 'Waiting';

create table public.order_line (
  line_id    text primary key,
  order_id   text not null references public."order"(order_id) on delete cascade,
  product_id text not null references public.product(product_id),
  qty        integer not null check (qty >= 1),
  unit_price numeric(12,2) not null check (unit_price >= 0),
  -- Snapshot of the name at claim time: F-02 c7 says a later rename or price
  -- change must not rewrite a past invoice.
  item_name  text not null default '',
  is_freebie boolean not null default false
);
create index order_line_order_idx on public.order_line (order_id);
create index order_line_product_idx on public.order_line (product_id);

create table public.invoice (
  invoice_id       text primary key,                   -- MP-1042
  order_id         text not null unique references public."order"(order_id),
  issued_at        timestamptz not null default now(),
  total            numeric(12,2) not null default 0,
  share_format     text check (share_format in ('Image', 'PDF')),
  packing_complete boolean not null default false
);

create table public.payment (
  payment_id text primary key,
  order_id   text not null references public."order"(order_id) on delete cascade,
  amount     numeric(12,2) not null check (amount > 0),
  method     text not null,
  reference  text,
  paid_at    timestamptz not null default now()
);
create index payment_order_idx on public.payment (order_id);

-- STOCK_ADJUSTMENT: s.4.2 item 1 - reason is OPTIONAL, overriding the
-- dictionary's "Required". This reverses F-07's original mandatory-reason rule:
-- the client made the reason optional, and the adjustment is logged regardless.
create table public.stock_adjustment (
  adj_id      text primary key,
  product_id  text not null references public.product(product_id),
  order_id    text references public."order"(order_id),
  direction   text not null check (direction in ('Increase', 'Decrease')),
  qty         integer not null check (qty <> 0),
  reason      text,                                    -- OPTIONAL (was Required)
  actor       text not null default '',
  adjusted_at timestamptz not null default now()
);
create index stock_adjustment_product_idx on public.stock_adjustment (product_id);

create table public.settings (
  setting_name text primary key,
  value        text not null,
  updated_at   timestamptz not null default now()
);

create table public.activity_log (
  log_id      text primary key,
  actor       text not null default '',
  action      text not null,
  entity_type text not null,
  entity_id   text not null,
  detail      text,
  logged_at   timestamptz not null default now()
);
create index activity_log_entity_idx on public.activity_log (entity_type, entity_id);

-- ---------------------------------------------------------------- grants ----
-- Every table gets RLS in 0004. The grants here are the minimum the
-- authenticated owner needs; the anon role gets nothing on the new tables,
-- which is the correction 0002 established for the 0001 tables.
grant select, insert, update, delete on
  public.customer, public.product, public.product_option, public."order",
  public.order_line, public.invoice, public.payment, public.shipment,
  public.stock_adjustment, public.settings, public.activity_log
  to authenticated;

-- No anon grant here, deliberately. A previous revision granted select on
-- product, order_line and invoice to anon, which contradicted the paragraph
-- above and re-opened the plane 0002 closed: RLS is enabled in 0004, these
-- tables carry owner-only policies, and "any invoice readable by id" is exactly
-- what C1.2 forbids. Customers read the catalogue and their invoices through
-- the projections in 0004, which run with their owner's rights and are granted
-- to authenticated.

comment on table public.stock_adjustment is
  'STOCK_ADJUSTMENT.reason is OPTIONAL per the client change; do not re-impose NOT NULL.';
comment on column public."order".payment_status is
  'Independent track. "Paid with balance" and "Paid and kept" are distinct from "Paid" (D-02 c9).';
comment on column public.product.is_parent is
  'A parent is rolled up from its variants and is never claimable directly (N-01, F4.8).';
