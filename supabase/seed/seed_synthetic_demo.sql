-- ============================================================================
--  seed_synthetic_demo.sql
--  Synthetic demo rows for the IAS live verification. NOT a migration.
--
--  Why this is not numbered 0006: the migration chain is the deployable schema
--  and CI Gate 2 applies all of it to a fresh database. Injecting demo rows into
--  that chain would put test data on a production database, which is the exact
--  thing IAS R.1 forbids. This file is applied by hand against a demo project and
--  is safe to skip.
--
--  RA 10173 compliance, and why this file is defensible:
--
--    * Every person is invented. Names are the pronoun-style placeholders the
--      HCI audit already used ("Synthetic Customer A"), not real surnames.
--    * Phone numbers use the 0917-000-000N range, which is sequential by
--      construction and cannot be a real subscriber number: no real line ends in
--      -0001 through -0004 of a reserved-zero block.
--    * Email addresses use the reserved example.com domain (RFC 2606), which
--      cannot be routed.
--    * Addresses are public landmarks, not dwellings.
--    * Nothing here was derived from the client workbook. If a row looks real,
--      that is a defect in this file, not a coincidence.
--
--  Every row also carries a synthetic marker in its text so a reader of the live
--  database cannot mistake it for production data: ids are prefixed SYN-, and the
--  log entries say so in `detail`.
--
--  Idempotent: every statement is an upsert or an on-conflict-do-nothing, so
--  re-running leaves the same row count.
-- ============================================================================

-- ------------------------------------------------------------ customers ----
insert into public.customer (customer_id, name, contact_number, email, province, city, barangay, address_line)
values
  ('SYN-CUST-001', 'Synthetic Customer A', '0917-000-0001', 'synthetic.a@example.com',
   'Metro Manila', 'Makati', 'West Rembo', '12 Synthetic Ave.'),
  ('SYN-CUST-002', 'Synthetic Customer B', '0917-000-0002', 'synthetic.b@example.com',
   'Cebu', 'Cebu City', 'Lahug', '88 Synthetic Rd.')
on conflict (customer_id) do nothing;

-- --------------------------------------------------------------- product ----
-- Every internal cost column is populated on purpose. A view cannot be shown to
-- omit `unit_cost` if `unit_cost` is 0 or NULL in the base table: the proof would
-- pass for the wrong reason. Round numbers make a leak unmistakable -
-- price 100.00 against unit_cost 40.00 and pack_cost 45.00, so any of those
-- appearing in a customer-facing projection is obvious on sight rather than
-- needing arithmetic.
insert into public.product (product_id, sku, name, character, category,
                            cost, unit_cost, pack_cost, price, srp,
                            stock_qty, reserved_qty, shipped_qty, status)
values
  ('SYN-PROD-001', 'SYN-W1', 'Synthetic Wipes',  'Hangyodon',   'Wipes',
   40.00, 40.00, 45.00, 100.00, 90.00, 50, 0, 0, 'In Stock'),
  ('SYN-PROD-002', 'SYN-T1', 'Synthetic Tissue', 'Hello Kitty', 'Tissue',
   20.00, 20.00, 25.00,  50.00, 45.00, 25, 0, 0, 'In Stock')
on conflict (product_id) do update set
  cost      = excluded.cost,
  unit_cost = excluded.unit_cost,
  pack_cost = excluded.pack_cost,
  price     = excluded.price,
  srp       = excluded.srp;

-- ----------------------------------------------------------------- order ----
-- Status values are the schema's own vocabularies (see the CHECK constraints on
-- public."order"): order_status is Claimed/Confirmed/Cancelled,
-- packing_status is To prep/Preparing/Packed/Kept,
-- payment_status is Waiting/Paid/.../Overdue,
-- shipping_status is Not shipped/For pickup/In transit/Received/Returned.
insert into public."order" (order_id, customer_id, live_session, order_status, payment_status, packing_status, shipping_status, order_sales, channel)
values
  ('SYN-ORD-001', 'SYN-CUST-001', 'SYN-LIVE-01', 'Claimed',   'Paid',    'Packed',   'For pickup', 150.00, 'Live'),
  ('SYN-ORD-002', 'SYN-CUST-002', 'SYN-LIVE-01', 'Confirmed', 'Waiting', 'To prep',  'Not shipped', 50.00, 'Live')
on conflict (order_id) do nothing;

-- ------------------------------------------------------------ order_line ----
insert into public.order_line (line_id, order_id, product_id, qty, unit_price, item_name, is_freebie)
values
  ('SYN-LINE-001', 'SYN-ORD-001', 'SYN-PROD-001', 1, 100.00, 'Synthetic Wipes',  false),
  ('SYN-LINE-002', 'SYN-ORD-001', 'SYN-PROD-002', 1,  50.00, 'Synthetic Tissue', false),
  ('SYN-LINE-003', 'SYN-ORD-002', 'SYN-PROD-002', 1,  50.00, 'Synthetic Tissue', false)
on conflict (line_id) do nothing;

-- --------------------------------------------------------------- invoice ----
insert into public.invoice (invoice_id, order_id, total, packing_complete)
values
  ('SYN-INV-001', 'SYN-ORD-001', 150.00, true),
  ('SYN-INV-002', 'SYN-ORD-002',  50.00, false)
on conflict (invoice_id) do nothing;

-- ---------------------------------------------------------- activity_log ----
-- A genesis row plus one entry per event, so the hash chain in AuditChain.kt has
-- something to link and the append-only trigger has a row to defend.
insert into public.activity_log (log_id, actor, action, entity_type, entity_id, detail)
values
  ('SYN-LOG-001', 'system', 'SEED',            'session',  'SYN-LIVE-01',  'synthetic demo seed - not production data'),
  ('SYN-LOG-002', 'owner',  'ORDER_CREATED',   'order',    'SYN-ORD-001',  'synthetic: order created in demo session'),
  ('SYN-LOG-003', 'owner',  'MARKED_PAID',     'order',    'SYN-ORD-001',  'synthetic: payment recorded, PHP 150.00'),
  ('SYN-LOG-004', 'owner',  'STOCK_ADJUSTED',  'product',  'SYN-PROD-001', 'synthetic: -1 unit for SYN-ORD-001')
on conflict (log_id) do nothing;

-- ------------------------------------------------------ stock_adjustment ----
insert into public.stock_adjustment (adj_id, product_id, order_id, direction, qty, reason, actor)
values
  ('SYN-ADJ-001', 'SYN-PROD-001', 'SYN-ORD-001', 'Decrease', 1, 'synthetic: claimed in demo order', 'owner')
on conflict (adj_id) do nothing;
