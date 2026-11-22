#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run_ias_assertions.sh — the five live IAS verification assertions
#
# Applies nothing. Reads the database and reports each assertion as PASS/FAIL
# with the raw SQL output, so the transcript can be pasted into the deployment
# proof without editing.
#
# Two targets, chosen by environment:
#
#   local container   PGCONTAINER=momo_ias_pg supabase/verify/run_ias_assertions.sh
#   remote project    SUPABASE_DB_URL='postgresql://...' supabase/verify/run_ias_assertions.sh
#
# The connection string outranks PGCONTAINER so the same script proves the local
# harness and the real project without a code change. Nothing in this file prints
# the connection string.
#
# Exits non-zero if any assertion fails, so it can be wired into CI later.
# ---------------------------------------------------------------------------
set -uo pipefail

CONTAINER=${PGCONTAINER:-momo_ias_pg}
DB=${PGDB:-momo}
USER=${PGUSER:-postgres}
fail=0

# One indirection so every query below is target-agnostic. `-X` skips .psqlrc,
# which a Supabase-side psqlrc could otherwise use to alter output formats.
if [ -n "${SUPABASE_DB_URL:-}" ]; then
  q() { psql "$SUPABASE_DB_URL" -X -At -F'|' "$@"; }
  exec_sql() { psql "$SUPABASE_DB_URL" -X -v ON_ERROR_STOP=0 -At -c "$1" 2>&1; }
else
  q() { docker exec "$CONTAINER" psql -U "$USER" -d "$DB" -At -F'|' "$@"; }
  exec_sql() { docker exec "$CONTAINER" psql -U "$USER" -d "$DB" -v ON_ERROR_STOP=0 -At -c "$1" 2>&1; }
fi

rule() { printf '\n%s\n' "------------------------------------------------------------------------"; }
head2() { rule; printf '%s\n' "$1"; rule; }

# 1 ---------------------------------------------------------------- pg_class --
head2 "ASSERTION 1  relrowsecurity=true and relforcerowsecurity=true on every public table; zero anon privileges"

echo "SQL> select relname, relrowsecurity, relforcerowsecurity from pg_class ... where relkind='r'"
q -c "
select c.relname, c.relrowsecurity, c.relforcerowsecurity
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname='public' and c.relkind='r'
order by c.relname;"

rls_bad=$(q -c "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace
                where n.nspname='public' and c.relkind='r'
                  and (c.relrowsecurity = false or c.relforcerowsecurity = false);")
echo "tables missing ENABLE or FORCE: $rls_bad"

echo ""
echo "SQL> select table_name, privilege_type from information_schema.role_table_grants where grantee='anon'"
anon_grants=$(q -c "select table_name||'|'||privilege_type from information_schema.role_table_grants
                    where grantee='anon' and table_schema='public';")
echo "${anon_grants:-(no rows)}"
anon_count=$(q -c "select count(*) from information_schema.role_table_grants where grantee='anon' and table_schema='public';")
anon_cols=$(q -c "select count(*) from information_schema.column_privileges where grantee='anon' and table_schema='public';")
echo "anon table grants: $anon_count   anon column grants: $anon_cols"

if [ "$rls_bad" = "0" ] && [ "$anon_count" = "0" ] && [ "$anon_cols" = "0" ]; then
  echo "ASSERTION 1: PASS"
else
  echo "ASSERTION 1: FAIL (rls_bad=$rls_bad anon=$anon_count/$anon_cols)"; fail=1
fi

# 2 --------------------------------------------------------------- anon role --
head2 "ASSERTION 2  SET LOCAL ROLE anon against customer/invoice tables and the customer views"

a2_fail=0
for target in public.customer public.invoice public."order" public.customer_catalog public.customer_invoice_view; do
  echo ""
  echo "SQL> begin; set local role anon; select count(*) from $target; commit;"
  out=$(exec_sql "begin; set local role anon; select count(*) from $target; commit;")
  echo "$out"
  # A pass is either a permission error or a zero row count. A non-zero count is
  # a leak and must be loud.
  if echo "$out" | grep -qiE "permission denied"; then
    echo "  -> denied"
  elif echo "$out" | grep -qxE "0"; then
    echo "  -> 0 rows"
  else
    echo "  -> LEAK OR UNEXPECTED RESULT"; a2_fail=1
  fi
done
if [ "$a2_fail" -eq 0 ]; then echo "ASSERTION 2: PASS"; else echo "ASSERTION 2: FAIL"; fail=1; fi

# 3 ------------------------------------------------------------ view columns --
head2 "ASSERTION 3  customer_invoice_view exposes no internal cost/margin/review column"

echo "SQL> select column_name from information_schema.columns where table_name='customer_invoice_view'"
q -c "select column_name from information_schema.columns
      where table_schema='public' and table_name='customer_invoice_view' order by ordinal_position;"

forbidden=$(q -c "select coalesce(string_agg(column_name, ','), '') from information_schema.columns
                  where table_schema='public' and table_name='customer_invoice_view'
                    and column_name in ('unit_cost','pack_cost','markup_rate','review_flag','cost','net_earnings');")
echo "forbidden columns present: ${forbidden:-(none)}"

# also prove the underlying tables DO hold them, so the absence is not vacuous.
# A view is only proven to strip costs if the base table actually carries them:
# if unit_cost were 0 or NULL throughout, "not in the view" would be true but
# meaningless. Non-zero values make the omission structural rather than incidental.
underlying=$(q -c "select column_name from information_schema.columns
                   where table_schema='public' and table_name='product'
                     and column_name in ('unit_cost','pack_cost','cost')
                   order by column_name;")
echo "product table does hold: $(echo "$underlying" | tr '\n' ' ')"
echo "SQL> select sku, price, unit_cost, pack_cost, cost from public.product order by product_id;"
q -c "select sku, price, unit_cost, pack_cost, cost from public.product order by product_id;"

nonzero=$(q -c "select count(*) from public.product where coalesce(unit_cost,0) <> 0 and coalesce(pack_cost,0) <> 0;")
echo "products with non-zero unit_cost and pack_cost: $nonzero"

if [ -z "$forbidden" ] && [ "$nonzero" -ge 1 ]; then
  echo "ASSERTION 3: PASS"
elif [ -z "$forbidden" ]; then
  echo "ASSERTION 3: FAIL (view omits them but no product carries a non-zero cost - proof is vacuous)"; fail=1
else
  echo "ASSERTION 3: FAIL (view leaks $forbidden)"; fail=1
fi

# 4 ------------------------------------------------------------- append-only --
head2 "ASSERTION 4  UPDATE and DELETE on the append-only ledgers must fail"

# Each ledger gets an UPDATE against a column that actually exists on it, so the
# statement reaches the trigger instead of dying on a missing column (which would
# look like a pass while proving nothing).
#   activity_log      -> detail
#   stock_adjustment  -> reason
a4_fail=0
for pair in "public.activity_log:detail" "public.stock_adjustment:reason"; do
  tbl=${pair%%:*}; col=${pair##*:}
  for op in "update $tbl set $col = 'TAMPER' where true" "delete from $tbl where true"; do
    echo ""
    echo "SQL> $op;"
    out=$(exec_sql "$op;")
    echo "$out"
    if echo "$out" | grep -q "is append-only"; then
      echo "  -> blocked by append-only trigger"
    else
      echo "  -> NOT BLOCKED"; a4_fail=1
    fi
  done
done

echo ""
echo "SQL> select has_table_privilege('authenticated','public.activity_log','UPDATE') as can_update,"
echo "          has_table_privilege('authenticated','public.activity_log','DELETE') as can_delete;"
privs=$(q -c "select has_table_privilege('authenticated','public.activity_log','UPDATE')::text||'|'||
                     has_table_privilege('authenticated','public.activity_log','DELETE')::text||'|'||
                     has_table_privilege('anon','public.activity_log','UPDATE')::text;")
echo "$privs"
echo "  (columns: authenticated_can_update | authenticated_can_delete | anon_can_update)"
# false|false|false is the expected reading: no role holds a mutating verb on the
# ledger. Compared against the word form because ::text yields 'false', while a
# bare boolean in unaligned output would print 'f' - matching both would need a
# normalisation step that hides a real difference.
if [ "$privs" != "false|false|false" ]; then
  echo "  -> a role still holds UPDATE or DELETE on the ledger"; a4_fail=1
fi

echo ""
echo "SQL> triggers on the ledgers"
q -c "select c.relname, t.tgname, pg_get_triggerdef(t.oid) is not null as has_def
      from pg_trigger t join pg_class c on c.oid = t.tgrelid
      join pg_namespace n on n.oid = c.relnamespace
      where n.nspname='public' and not t.tgisinternal and c.relname in ('activity_log','stock_adjustment')
      order by c.relname, t.tgname;"

if [ "$a4_fail" -eq 0 ]; then echo "ASSERTION 4: PASS"; else echo "ASSERTION 4: FAIL"; fail=1; fi

# 5 ----------------------------------------------------------------- storage --
head2 "ASSERTION 5  receipts bucket exists with public = false"

echo "SQL> select id, name, public from storage.buckets;"
q -c "select id, name, public from storage.buckets order by id;"

bucket_public=$(q -c "select public from storage.buckets where id='receipts';")
echo "receipts.public = ${bucket_public:-MISSING}"

echo ""
echo "SQL> policies on storage.objects"
q -c "select policyname, cmd from pg_policies where schemaname='storage' and tablename='objects' order by policyname;"

if [ "$bucket_public" = "f" ]; then echo "ASSERTION 5: PASS"; else echo "ASSERTION 5: FAIL"; fail=1; fi

# -------------------------------------------------------------------- total --
rule
if [ "$fail" -eq 0 ]; then
  echo "ALL 5 ASSERTIONS PASSED"
else
  echo "ONE OR MORE ASSERTIONS FAILED"
fi
rule
exit "$fail"
