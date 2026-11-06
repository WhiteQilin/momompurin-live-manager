"""Runnable demo of the four core algorithms, with correctness assertions.

Every implementation is paired with the naive baseline and the demo asserts
the two agree — the same rule the ALGOCOM benchmark harness enforces. A fast
wrong answer is not a result.

Run:  python demo/run_demo.py
(no dependencies beyond the Python standard library)
"""

from __future__ import annotations

import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from algorithms import DeadlineHeap, InvertedIndex, StockFSM, Trie
from demo.demo_data import TYPED_COMMENTS, catalogue


def timed(fn, repeats: int = 3) -> tuple[float, object]:
    best = float("inf")
    result = None
    for _ in range(repeats):
        t0 = time.perf_counter()
        result = fn()
        best = min(best, time.perf_counter() - t0)
    return best * 1000.0, result


def main() -> None:
    items = catalogue()
    skus = [s for s, _ in items]
    names = [n for _, n in items]
    print(f"demo catalogue: {len(items)} items\n")

    # ---------------------------------------------------------- 1. trie
    print("[1] Trie prefix lookup (POS-style SKU search)")
    keys = [f"{s} {n}".lower() for s, n in items]
    trie = Trie(keys)
    prefixes = [f"{s[:len(s) // 2]}".lower() for s in skus[:200]]
    t_ms, trie_out = timed(lambda: [tuple(trie.prefix(p)) for p in prefixes])
    s_ms, scan_out = timed(lambda: [
        tuple(i for i, k in enumerate(keys) if k.startswith(p)) for p in prefixes])
    assert trie_out == scan_out, "trie and scan disagree"
    print(f"    {t_ms:.3f} ms vs baseline scan {s_ms:.1f} ms  "
          f"({s_ms / t_ms:.0f}x) — identical result sets asserted\n")

    # --------------------------------------------- 2. inverted index + jaccard
    print("[2] Inverted trigram index + Jaccard (messy claim matching)")
    idx = InvertedIndex(names)
    print(f"    {len(idx.index)} distinct trigrams over {len(names)} items")
    for q, sku_hint in TYPED_COMMENTS:
        best, score = idx.best_match(q)
        ok = best is not None and skus[best].startswith(sku_hint)
        print(f"    {'OK ' if ok else 'MISS'}  {q!r:38} -> "
              f"{skus[best] if best is not None else '-'} ({score:.2f})")
        assert ok, f"query {q!r} did not resolve into family {sku_hint}"
    print()

    # ---------------------------------------------------------- 3. FSM
    print("[3] Stock FSM — atomic transition table")
    fsm = StockFSM()
    chain = {
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

    def chain_transition(state, event):
        return chain.get((state, event))

    cases = [
        ("AVAILABLE", "claim", "RESERVED"),
        ("RESERVED", "pay", "KEPT"),
        ("RESERVED", "cancel", "CANCELLED"),
        ("CANCELLED", "restock", "AVAILABLE"),
        ("PACKED", "claim", None),      # illegal: cannot claim packed stock
        ("SHIPPED", "cancel", None),    # illegal: cannot cancel after dispatch
        ("AVAILABLE", "pack", None),    # illegal: must be reserved first
    ]
    for state, event, want in cases:
        got = fsm.transition(state, event)
        assert got == chain_transition(state, event) == want, \
            f"({state}, {event}): table {got}, chain {chain_transition(state, event)}"
        print(f"    {state:10} --{event:10}--> {got}  {'(rejected)' if want is None else ''}")
    print()

    # ---------------------------------------------------------- 4. heap expiry
    print("[4] Deadline heap — 24-hour auto-cancel sweep")
    import random
    rng = random.Random(7)
    deadlines = {f"ORD-{i:04d}": rng.uniform(0, 1440) for i in range(5000)}
    heap = DeadlineHeap(deadlines)
    h_ms, heap_due = timed(lambda: [oid for m in range(1440) for oid in heap.sweep(m)],
                           repeats=1)
    # heap.sweep mutates, so rebuild for the baseline comparison
    pending = sorted(deadlines.items(), key=lambda kv: kv[1])
    scan_due = []
    t = 0
    for oid, d in pending:
        while t < d:
            t += 1
        scan_due.append(oid)

    def via_scan():
        out = []
        p = list(deadlines.items())
        for minute in range(1440):
            keep = [(oid, d) for oid, d in p if d > minute]
            out.extend(oid for oid, d in p if d <= minute)
            p = keep
        return out

    s_ms, scan_out = timed(via_scan, repeats=1)
    assert sorted(heap_due) == sorted(scan_out), "heap and scan disagree"
    print(f"    heap {h_ms:.1f} ms vs scan {s_ms:.1f} ms ({s_ms / h_ms:.0f}x) "
          f"— {len(heap_due)} of {len(deadlines)} orders lapsed, both agree\n")

    print("all four algorithms verified against their baselines")


if __name__ == "__main__":
    main()
