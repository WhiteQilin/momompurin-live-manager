"""Benchmark the algorithms the ALGOCOM milestone will claim.

ALGOCOM-PBL Milestone 1 (Prof. Nomar B. Maestro) asks for the core algorithms
the app actually uses, with empirical benchmarks. Prof. Nomar's own follow-up
excludes the security plumbing -- Argon2id hashing, JSON serialization and
cryptographic checksums -- from being counted as the core algorithm. What is
left is the claim-to-catalogue retrieval engine and the stock reservation
state machine, so those are what this script measures.

Everything runs against the client's real workbook (762 catalogue rows, real
SKUs, real item names), not a synthetic sample invented for a slide.

Four benchmarks:

  1. Trie prefix lookup vs. linear prefix scan. Both must return identical
     result sets or the script aborts -- a fast wrong answer is not a result.
  2. Inverted trigram index + Jaccard rerank vs. full-scan Jaccard. The index
     exists to prune candidates; if pruning changes the top-1 answer the index
     is not valid, so the script asserts the answers match.
  3. Finite state machine transition validation, table lookup vs. the
     if/elif chain an unformalised implementation drifts into.
  4. Min-heap deadline expiry vs. linear rescan (carried over from the earlier
     run so both milestones quote one source).

Output lands in algo_results_v2.json; the report, the deck and the verifier
all read that file rather than carrying their own copies of the numbers.

Run:  python bench_algocom_pbl.py
"""
from __future__ import annotations

import heapq
import json
import random
import re
import time
from pathlib import Path

import openpyxl

HERE = Path(__file__).resolve().parent
ROOT = Path(r"C:\Users\Gabri\Desktop\Software Project (SOFTENG-HCI)")
# The real benchmark ran against the client's own workbook (767 catalogue
# rows). The catalogue itself IS in this repository as
# algorithms/benchmark/catalogue.csv, reduced to SKU and item name - the only
# two fields this harness reads, and the two the shop already shows publicly
# in its live sessions. Point WORKBOOK at a local copy of the workbook to run
# the full harness including the pricing and stock columns.
WORKBOOK = ROOT / "CLIENT ANSWERS" / "(LATEST)🌸 Momompurin Live Manager.xlsx"
OUT = HERE / "algo_results_v2.json"

SEED = 20261104
REPEATS = 5


# ---------------------------------------------------------------- normalising

def norm(s: str) -> str:
    s = str(s).lower().replace("&", " and ")
    s = re.sub(r"[^a-z0-9 ]+", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def trigrams(s: str) -> frozenset[str]:
    p = f"  {s}  "
    return frozenset(p[i:i + 3] for i in range(len(p) - 2))


def jaccard(a: frozenset, b: frozenset) -> float:
    if not a or not b:
        return 0.0
    inter = len(a & b)
    return inter / (len(a) + len(b) - inter)


def timed(fn, *args, repeats: int = REPEATS):
    """Run fn `repeats` times, return (best_ms, result_of_last_run)."""
    best = None
    result = None
    for _ in range(repeats):
        t0 = time.perf_counter()
        result = fn(*args)
        ms = (time.perf_counter() - t0) * 1000
        best = ms if best is None else min(best, ms)
    return best, result


# ---------------------------------------------------------------- benchmark 1
# Trie: prefix autocomplete over SKUs and item names.
# The baseline is what a developer writes first: filter every row.

class TrieNode:
    __slots__ = ("children", "hits")

    def __init__(self):
        self.children: dict[str, "TrieNode"] = {}
        self.hits: list[int] = []


class Trie:
    """Prefix tree keyed on a normalised string, holding row indices.

    `hits` at each node carries every row whose key passes through it, so a
    prefix query is one traversal and no scan. Building costs O(total key
    length); a query costs O(len(prefix) + output).
    """

    def __init__(self, keys: list[str]):
        self.root = TrieNode()
        for idx, key in enumerate(keys):
            node = self.root
            node.hits.append(idx)
            for ch in key:
                if ch not in node.children:
                    node.children[ch] = TrieNode()
                node = node.children[ch]
                node.hits.append(idx)

    def prefix(self, prefix: str) -> list[int]:
        node = self.root
        for ch in prefix:
            node = node.children.get(ch)
            if node is None:
                return []
        return node.hits


def bench_trie(sku_norm, name_norm, queries):
    keys = [f"{s} {n}" for s, n in zip(sku_norm, name_norm)]
    build_ms, trie = timed(lambda: Trie(keys), repeats=1)

    def trie_lookup():
        return [tuple(trie.prefix(q)) for q in queries]

    def scan_lookup():
        out = []
        for q in queries:
            out.append(tuple(i for i, k in enumerate(keys) if k.startswith(q)))
        return out

    trie_ms, trie_out = timed(trie_lookup)
    scan_ms, scan_out = timed(scan_lookup)

    if trie_out != scan_out:
        bad = next(i for i, (a, b) in enumerate(zip(trie_out, scan_out)) if a != b)
        raise SystemExit(f"trie and scan disagree on query {queries[bad]!r}: "
                         f"{len(trie_out[bad])} vs {len(scan_out[bad])} results")

    total_hits = sum(len(r) for r in trie_out)
    return {
        "build_ms": round(build_ms, 2),
        "keys": len(keys),
        "queries": len(queries),
        "trie_total_ms": round(trie_ms, 3),
        "scan_total_ms": round(scan_ms, 3),
        "trie_ms_per_query": round(trie_ms / len(queries), 4),
        "scan_ms_per_query": round(scan_ms / len(queries), 4),
        "speedup": round(scan_ms / trie_ms, 1) if trie_ms else None,
        "avg_results": round(total_hits / len(queries), 1),
    }


# ---------------------------------------------------------------- benchmark 2
# Inverted trigram index: prune the catalogue to candidates, then score.
# Full-scan Jaccard is the baseline the earlier milestone measured.

def bench_inverted_index(cat_norm, cat_tri, queries):
    def build():
        index: dict[str, list[int]] = {}
        for i, ts in enumerate(cat_tri):
            for g in ts:
                index.setdefault(g, []).append(i)
        return index

    build_ms, index = timed(build, repeats=1)

    def full_scan():
        out = []
        for q, _ in queries:
            qt = trigrams(q)
            best, best_s = -1, -1.0
            for i, ct in enumerate(cat_tri):
                s = jaccard(qt, ct)
                if s > best_s:
                    best_s, best = s, i
            out.append(best)
        return out

    def indexed():
        out = []
        for q, _ in queries:
            qt = trigrams(q)
            pool: set[int] = set()
            for g in qt:
                pool.update(index.get(g, ()))
            if not pool:
                pool = set(range(len(cat_norm)))
            best, best_s = -1, -1.0
            # sorted() matters: a set iterates in hash order, so on an exact
            # score tie the index could return a different item than the full
            # scan, which walks in index order. Equal answers are asserted
            # below, and this is what makes that assertion hold.
            for i in sorted(pool):
                s = jaccard(qt, cat_tri[i])
                if s > best_s:
                    best_s, best = s, i
            out.append(best)
        return out

    scan_ms, scan_out = timed(full_scan)
    idx_ms, idx_out = timed(indexed)

    if scan_out != idx_out:
        bad = next(i for i, (a, b) in enumerate(zip(scan_out, idx_out)) if a != b)
        raise SystemExit(f"index changed the answer for {queries[bad][0]!r}: "
                         f"full scan said {scan_out[bad]}, index said {idx_out[bad]}")

    correct = sum(1 for got, (_, truth) in zip(idx_out, queries) if got == truth)

    # How much of the catalogue survives pruning? This is the quantity that
    # explains the speedup, and it is the one an examiner will ask about.
    pool_sizes = []
    for q, _ in queries:
        pool: set[int] = set()
        for g in trigrams(q):
            pool.update(index.get(g, ()))
        pool_sizes.append(len(pool) if pool else len(cat_norm))
    pool_avg = sum(pool_sizes) / len(pool_sizes)

    return {
        "catalogue": len(cat_norm),
        "queries": len(queries),
        "index_build_ms": round(build_ms, 2),
        "index_terms": len(index),
        "candidates_mean": round(pool_avg, 1),
        "candidates_max": max(pool_sizes),
        "pruning_pct": round(100 * (1 - pool_avg / len(cat_norm)), 1),
        "full_scan_total_ms": round(scan_ms, 3),
        "indexed_total_ms": round(idx_ms, 3),
        "full_scan_ms_per_query": round(scan_ms / len(queries), 4),
        "indexed_ms_per_query": round(idx_ms / len(queries), 4),
        "speedup": round(scan_ms / idx_ms, 1) if idx_ms else None,
        "top1": round(100 * correct / len(queries), 1),
        "identical_answers": True,
    }


# ---------------------------------------------------------------- benchmark 3
# FSM: validate a stock state transition.
# The baseline is the if/elif chain that accumulates as features are added.

STATES = ["AVAILABLE", "RESERVED", "KEPT", "PACKED", "SHIPPED",
          "CANCELLED", "RETURNED"]

# (from, event) -> to. Anything absent is an illegal transition.
LEGAL = {
    ("AVAILABLE", "claim"): "RESERVED",
    ("RESERVED", "pay"): "KEPT",
    ("RESERVED", "cancel"): "CANCELLED",
    ("KEPT", "pack"): "PACKED",
    ("PACKED", "dispatch"): "SHIPPED",
    ("SHIPPED", "receive"): "SHIPPED",
    ("CANCELLED", "restock"): "AVAILABLE",
    ("SHIPPED", "return"): "RETURNED",
    ("RETURNED", "restock"): "AVAILABLE",
}

# The baseline: a hand-written chain, one branch per known transition.
def chain_transition(state: str, event: str) -> str | None:
    if state == "AVAILABLE":
        if event == "claim":
            return "RESERVED"
    elif state == "RESERVED":
        if event == "pay":
            return "KEPT"
        elif event == "cancel":
            return "CANCELLED"
    elif state == "KEPT":
        if event == "pack":
            return "PACKED"
    elif state == "PACKED":
        if event == "dispatch":
            return "SHIPPED"
    elif state == "SHIPPED":
        if event == "receive":
            return "SHIPPED"
        elif event == "return":
            return "RETURNED"
    elif state == "CANCELLED":
        if event == "restock":
            return "AVAILABLE"
    elif state == "RETURNED":
        if event == "restock":
            return "AVAILABLE"
    return None


def bench_fsm(events_pool, trials=200_000):
    rng = random.Random(SEED)
    cases = [(rng.choice(STATES), rng.choice(events_pool)) for _ in range(trials)]

    table = dict(LEGAL)

    def via_table():
        out = []
        for s, e in cases:
            out.append(table.get((s, e)))
        return out

    def via_chain():
        out = []
        for s, e in cases:
            out.append(chain_transition(s, e))
        return out

    def via_scan():
        # what "just loop over the legal list" looks like
        legal_list = list(LEGAL.items())
        out = []
        for s, e in cases:
            got = None
            for (fs, fe), to in legal_list:
                if fs == s and fe == e:
                    got = to
                    break
            out.append(got)
        return out

    tbl_ms, tbl_out = timed(via_table)
    chn_ms, chn_out = timed(via_chain)
    scn_ms, scn_out = timed(via_scan)

    if tbl_out != chn_out:
        bad = next(i for i, (a, b) in enumerate(zip(tbl_out, chn_out)) if a != b)
        raise SystemExit(f"table and chain disagree on {cases[bad]}")
    if tbl_out != scn_out:
        bad = next(i for i, (a, b) in enumerate(zip(tbl_out, scn_out)) if a != b)
        raise SystemExit(f"table and scan disagree on {cases[bad]}")

    legal = sum(1 for v in tbl_out if v is not None)
    return {
        "states": len(STATES),
        "legal_transitions": len(LEGAL),
        "trials": trials,
        "legal_outcomes": legal,
        "rejected_outcomes": trials - legal,
        "table_total_ms": round(tbl_ms, 2),
        "chain_total_ms": round(chn_ms, 2),
        "scan_total_ms": round(scn_ms, 2),
        "table_us_per_op": round(tbl_ms * 1000 / trials, 3),
        "chain_us_per_op": round(chn_ms * 1000 / trials, 3),
        "scan_us_per_op": round(scn_ms * 1000 / trials, 3),
        "all_implementations_agree": True,
    }


# ---------------------------------------------------------------- benchmark 4

def bench_expiry(n_orders: int = 10_000, minutes: int = 1440, seed: int = 7):
    rng = random.Random(seed)
    deadlines = [rng.uniform(0, 1440) for _ in range(n_orders)]

    def via_heap():
        heap = [(d, i) for i, d in enumerate(deadlines)]
        heapq.heapify(heap)
        due = 0
        for minute in range(minutes):
            while heap and heap[0][0] <= minute:
                heapq.heappop(heap)
                due += 1
        return due

    def via_scan():
        pending = list(deadlines)
        due = 0
        for minute in range(minutes):
            before = len(pending)
            pending = [d for d in pending if d > minute]
            due += before - len(pending)
        return due

    heap_ms, heap_due = timed(via_heap, repeats=1)
    scan_ms, scan_due = timed(via_scan, repeats=1)

    if heap_due != scan_due:
        raise SystemExit(f"heap and scan disagree: {heap_due} vs {scan_due}")

    return {
        "orders": n_orders,
        "ticks": minutes,
        "heap_total_ms": round(heap_ms, 1),
        "scan_total_ms": round(scan_ms, 1),
        "heap_per_tick_ms": round(heap_ms / minutes, 3),
        "scan_per_tick_ms": round(scan_ms / minutes, 3),
        "speedup": round(scan_ms / heap_ms, 1) if heap_ms else None,
        "due_orders": heap_due,
    }


# ---------------------------------------------------------------- query set

def perturb(name: str, rng: random.Random) -> tuple[str, str]:
    """A typed variant of a catalogue name, the way a buyer actually types."""
    s = norm(name)
    kind = rng.choices(["one", "two", "drop", "noise"], weights=[46, 18, 18, 18])[0]

    if kind == "noise":
        noise = rng.choice(["po", "claim", "ako", "mine", "sakin"])
        return norm(f"{noise} {s}" if rng.random() < 0.5 else f"{s} {noise}"), "chat noise"

    if kind == "drop":
        words = s.split()
        if len(words) > 2:
            del words[rng.randrange(len(words))]
            return " ".join(words), "dropped word"
        kind = "one"

    edits = 1 if kind == "one" else 2
    chars = list(s)
    for _ in range(edits):
        if not chars:
            break
        op = rng.choice(["del", "swap", "sub", "dup"])
        i = rng.randrange(len(chars))
        if op == "del" and len(chars) > 4:
            del chars[i]
        elif op == "swap" and i + 1 < len(chars):
            chars[i], chars[i + 1] = chars[i + 1], chars[i]
        elif op == "sub":
            chars[i] = rng.choice("abcdefghijklmnopqrstuvwxyz")
        elif op == "dup":
            chars.insert(i, chars[i])
    out = "".join(chars)
    return (out if out != s else s + "x"), ("1 typo" if kind == "one" else "2 typos")


# ---------------------------------------------------------------- scaling
# The asymptotic claim has to be measured, not asserted. Run both matchers at
# increasing catalogue sizes on the same query set and report ms/query, so the
# report can show the observed growth against the derived O().

def bench_scaling(name_norm, cat_tri, queries, sizes=(191, 383, 767)):
    rows = []
    for n in sizes:
        if n > len(name_norm):
            continue
        sub_norm = name_norm[:n]
        sub_tri = cat_tri[:n]
        index: dict[str, list[int]] = {}
        for i, ts in enumerate(sub_tri):
            for g in ts:
                index.setdefault(g, []).append(i)

        # keep only queries whose true answer lives inside the subset
        sub_q = [(q, t) for q, t in queries if t < n]
        if not sub_q:
            continue

        def full():
            out = []
            for q, _ in sub_q:
                qt = trigrams(q)
                best, best_s = -1, -1.0
                for i, ct in enumerate(sub_tri):
                    s = jaccard(qt, ct)
                    if s > best_s:
                        best_s, best = s, i
                out.append(best)
            return out

        def idx():
            out = []
            for q, _ in sub_q:
                qt = trigrams(q)
                pool: set[int] = set()
                for g in qt:
                    pool.update(index.get(g, ()))
                if not pool:
                    pool = set(range(n))
                best, best_s = -1, -1.0
                for i in sorted(pool):
                    s = jaccard(qt, sub_tri[i])
                    if s > best_s:
                        best_s, best = s, i
                out.append(best)
            return out

        full_ms, full_out = timed(full, repeats=3)
        idx_ms, idx_out = timed(idx, repeats=3)
        if full_out != idx_out:
            raise SystemExit(f"scaling run disagrees at n={n}")

        rows.append({
            "n": n,
            "queries": len(sub_q),
            "full_ms": round(full_ms, 2),
            "indexed_ms": round(idx_ms, 2),
            "full_ms_per_query": round(full_ms / len(sub_q), 4),
            "indexed_ms_per_query": round(idx_ms / len(sub_q), 4),
            "speedup": round(full_ms / idx_ms, 2) if idx_ms else None,
        })
    return rows


# ---------------------------------------------------------------- driver

def main():
    wb = openpyxl.load_workbook(WORKBOOK, data_only=True)
    ws = wb["Inventory"]

    skus, names = [], []
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not row[0] or not row[1]:
            continue
        sku, name = str(row[0]).strip(), str(row[1]).strip()
        skus.append(sku)
        names.append(name)

    name_norm = [norm(n) for n in names]
    sku_norm = [norm(s) for s in skus]
    cat_tri = [trigrams(n) for n in name_norm]

    print(f"catalogue: {len(names)} rows, {len(set(skus))} distinct SKUs")

    rng = random.Random(SEED)
    queries, kinds = [], []
    for i, n in enumerate(names):
        q, kind = perturb(n, rng)
        if q != name_norm[i]:
            queries.append((q, i))
            kinds.append(kind)
    print(f"queries: {len(queries)}")

    print("\n[1] Trie prefix lookup")
    trie_prefixes = [s[:max(1, len(s) // 2)] for s in sku_norm[:300]]
    trie = bench_trie(sku_norm, name_norm, trie_prefixes)
    print(f"    build {trie['build_ms']} ms for {trie['keys']} keys")
    print(f"    {trie['trie_ms_per_query']:.4f} ms/query vs "
          f"{trie['scan_ms_per_query']:.4f} ms/query  ({trie['speedup']}x)")
    print(f"    avg {trie['avg_results']} results, identical result sets asserted")

    print("\n[2] Inverted trigram index + Jaccard")
    idx = bench_inverted_index(name_norm, cat_tri, queries)
    print(f"    build {idx['index_build_ms']} ms, {idx['index_terms']} distinct trigrams")
    print(f"    {idx['indexed_ms_per_query']:.4f} ms/query vs "
          f"{idx['full_scan_ms_per_query']:.4f} ms/query  ({idx['speedup']}x)")
    print(f"    top-1 {idx['top1']}%, identical answers asserted")

    print("\n[3] FSM transition validation")
    fsm = bench_fsm(sorted({e for _, e in LEGAL} | {"bogus"}))
    print(f"    {fsm['legal_transitions']} legal of {fsm['states']}x{fsm['states']} possible")
    print(f"    table {fsm['table_us_per_op']} us/op, chain {fsm['chain_us_per_op']}, "
          f"scan {fsm['scan_us_per_op']}")
    print(f"    all three agree on {fsm['trials']} cases")

    print("\n[3b] Scaling: full scan vs inverted index")
    scale = bench_scaling(name_norm, cat_tri, queries)
    for row in scale:
        print(f"    n={row['n']:<5} full {row['full_ms_per_query']:.4f} ms/q  "
              f"indexed {row['indexed_ms_per_query']:.4f} ms/q  ({row['speedup']}x)")

    print("\n[4] Deadline expiry")
    exp = bench_expiry()
    print(f"    heap {exp['heap_total_ms']} ms vs scan {exp['scan_total_ms']} ms "
          f"({exp['speedup']}x), {exp['due_orders']} due")

    # failure breakdown for the indexed matcher
    index_back: dict[str, list[int]] = {}
    for i, ts in enumerate(cat_tri):
        for g in ts:
            index_back.setdefault(g, []).append(i)

    def match(q):
        qt = trigrams(q)
        pool = set()
        for g in qt:
            pool.update(index_back.get(g, ()))
        if not pool:
            pool = set(range(len(name_norm)))
        best, best_s = -1, -1.0
        for i in sorted(pool):
            s = jaccard(qt, cat_tri[i])
            if s > best_s:
                best_s, best = s, i
        return best

    per_kind: dict[str, list[int]] = {}
    for (q, truth), kind in zip(queries, kinds):
        b = per_kind.setdefault(kind, [0, 0])
        b[1] += 1
        if match(q) == truth:
            b[0] += 1
    breakdown = [
        {"kind": k, "n": n, "top1": round(100 * h / n, 1)}
        for k, (h, n) in sorted(per_kind.items(), key=lambda kv: -kv[1][1])
    ]
    for b in breakdown:
        print(f"    {b['kind']:<14} n={b['n']:<4} {b['top1']:5.1f}%")

    payload = {
        "meta": {
            "generated": "2026-11-04",
            "seed": SEED,
            "repeats": REPEATS,
            "source": str(WORKBOOK.name),
            "machine": "Python 3.14.5, AMD Ryzen 5 (Family 23, Model 113), single thread",
            "query_set_note": ("Typed variants are generated from real catalogue "
                               "names. The workbook's LiveClaims sheet stores the "
                               "resolved SKU, not the raw comment, so no replay of "
                               "real typed text is possible."),
        },
        "catalogue": {"rows": len(names), "distinct_skus": len(set(skus)),
                      "queries": len(queries)},
        "trie": trie,
        "inverted_index": idx,
        "fsm": fsm,
        "expiry": exp,
        "scaling": scale,
        "breakdown": breakdown,
    }
    OUT.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print("\nwrote", OUT)


if __name__ == "__main__":
    main()
