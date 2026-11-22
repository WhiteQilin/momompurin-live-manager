#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# deploy_to_supabase.sh — apply the migration chain to a Supabase project
#
# Usage: put the project's connection string in SUPABASE_DB_URL and run this
# script. Supabase shows the string under Project Settings > Database >
# Connection string; it is the only thing this script needs.
#
#   export SUPABASE_DB_URL="$(pass show momompurin/supabase-db)"   # for example
#   supabase/verify/deploy_to_supabase.sh
#
# The connection string is read from the environment and never written to disk,
# logged, or echoed. `set -x` is deliberately not used anywhere in this file.
# The docs avoid printing a connection-string example at all: a placeholder that
# looks like `scheme://user:pass@host` trips the repo's own gitleaks rule, and
# weakening that rule to allow a pretend password would blunt the control this
# project relies on most.
#
# Order of operations, and why:
#   1. inspect current state FIRST, so the transcript shows what was already
#      there before anything changed
#   2. apply 0001-0005 in filename order, stopping at the first error
#   3. re-inspect and run the five assertions
#
# Every migration is idempotent by construction (create or replace, drop policy
# if exists, drop trigger if exists, on conflict do update), so a re-run against
# a partially-migrated project converges rather than failing.
# ---------------------------------------------------------------------------
set -uo pipefail

: "${SUPABASE_DB_URL:?set SUPABASE_DB_URL to the project connection string}"

cd "$(dirname "$0")/../.." || exit 1
ROOT=$(pwd)
OUT=${OUT:-/tmp/supabase_deploy_transcript.txt}
: > "$OUT"

# psql against the remote database. PGCONNECT_TIMEOUT keeps a firewall stall from
# hanging the run; sslmode=require is the default on Supabase but stated anyway.
# -X skips .psqlrc: a server-side rc file must not be able to change our output
# format or inject commands into a proof transcript.
PSQL=(psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -X -q)
log() { printf '%s\n' "$*" | tee -a "$OUT"; }

# Inspection must never abort the run. A fresh project legitimately has no
# storage schema, no triggers and no tables, and "the query errored" is itself
# useful evidence of that - but it is not a reason to stop before deploying.
inspect() {
  local label=$1 sql=$2 out
  log "SQL> $label"
  out=$("${PSQL[@]}" -At -F'|' -c "$sql" 2>&1)
  if [ $? -ne 0 ]; then
    log "  (query returned an error - expected on a fresh project)"
  fi
  [ -n "$out" ] && log "$out" || log "  (no rows)"
  log ""
}

rule() { log "------------------------------------------------------------------------"; }

log "Momompurin - Supabase IAS deployment"
log "started $(date -u '+%Y-%m-%dT%H:%M:%SZ') (UTC)"
rule

# ---------------------------------------------------------------- 1. inspect --
log "STEP 1  existing state, before any change"
rule

inspect "select table_name from information_schema.tables where table_schema='public'" "
  select table_name from information_schema.tables
  where table_schema='public' and table_type='BASE TABLE' order by table_name;"

inspect "RLS state of existing public tables" "
  select c.relname||'|rls='||c.relrowsecurity||'|force='||c.relforcerowsecurity
  from pg_class c join pg_namespace n on n.oid = c.relnamespace
  where n.nspname='public' and c.relkind='r' order by c.relname;"

inspect "existing triggers on public tables" "
  select c.relname||'.'||t.tgname
  from pg_trigger t join pg_class c on c.oid=t.tgrelid
  join pg_namespace n on n.oid=c.relnamespace
  where n.nspname='public' and not t.tgisinternal order by 1;"

inspect "storage buckets (errors if storage is not provisioned)" "
  select id||'|public='||public from storage.buckets order by id;"

# ---------------------------------------------------------------- 2. apply ---
rule
log "STEP 2  apply migrations"
rule

# Absolute paths throughout. A relative path here fails if psql resolves its own
# working directory differently from the shell's, which is exactly what happened
# on the first run of this script.
for f in "$ROOT"/supabase/migrations/0001_initial_schema.sql \
         "$ROOT"/supabase/migrations/0002_enable_rls_and_policies.sql \
         "$ROOT"/supabase/migrations/0003_phase1_domain_schema.sql \
         "$ROOT"/supabase/migrations/0004_phase1_rls_and_policies.sql \
         "$ROOT"/supabase/migrations/0005_append_only_audit_and_receipts.sql; do
  log "applying $(basename "$f")"
  if "${PSQL[@]}" -f "$f" >>"$OUT" 2>&1; then
    log "  ok"
  else
    log "  FAILED - stopping. See $OUT"
    exit 1
  fi
done

# ---------------------------------------------------------------- 3. seed ----
# Synthetic rows only. Skipped unless SEED=1, because a project that already
# holds data must not receive demo rows by accident.
if [ "${SEED:-0}" = "1" ]; then
  rule
  log "STEP 3  synthetic seed (SEED=1)"
  rule
  if "${PSQL[@]}" -f "$ROOT/supabase/seed/seed_synthetic_demo.sql" >>"$OUT" 2>&1; then
    log "  ok - synthetic rows applied (prefix SYN-)"
  else
    log "  FAILED - see $OUT"; exit 1
  fi
fi

# -------------------------------------------------------------- 4. assert ----
rule
log "STEP 4  five live assertions"
rule

# The assertion script switches itself to the remote project when
# SUPABASE_DB_URL is set, so it is handed the connection string through the
# environment (never as an argument, so it cannot land in a process listing).
SUPABASE_DB_URL="$SUPABASE_DB_URL" \
  bash "$ROOT/supabase/verify/run_ias_assertions.sh" 2>&1 | tee -a "$OUT"
rc=${PIPESTATUS[0]}

rule
if [ "$rc" -eq 0 ]; then
  log "DEPLOYMENT VERIFIED - all 5 assertions passed"
else
  log "DEPLOYMENT APPLIED but one or more assertions FAILED - review $OUT"
fi
rule
log "transcript: $OUT"

# Never let the connection string reach the transcript, even if a psql error
# echoed the command line.
if grep -qE 'postgres(ql)?://[^:]+:[^@]+@' "$OUT"; then
  echo "WARNING: a connection string appeared in the transcript - redacting" >&2
  sed -i -E 's#postgres(ql)?://[^:]+:[^@]+@[^ ]*#postgresql://REDACTED#' "$OUT"
fi

exit "$rc"
