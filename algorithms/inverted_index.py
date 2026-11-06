"""Inverted trigram index + Jaccard scoring for live-chat claim matching.

During a livestream buyers post messy comments ("mine blue melody pouch"). The
engine maps each phrase to the closest catalogue item:

  1. Build an index from character trigram -> list of item ids, once at
     startup.
  2. For a query, union the posting lists of the query's trigrams into a
     candidate pool. This prunes the catalogue (55.4% pruned at 767 rows).
  3. Score each candidate with the Jaccard coefficient of its trigram set
     against the query's, and return the top-1.

Why trigram Jaccard and not edit distance: the errors this domain produces are
word-level ("hello kitty" -> "hk", dropped words, pasted chat noise like
"po ako claim"). Edit distance treats those as many character edits; Jaccard
over trigram sets degrades gracefully and measured 97.0% top-1 against
Damerau-Levenshtein's 74.0% on the same queries.

Two correctness rules carried from the benchmark:

  * The candidate pool is unioned, not intersected. A query sharing only one
    trigram with the correct item would be excluded by an intersection.
  * The pool is iterated in sorted order. A set iterates in hash order, so on
    an exact score tie the index could return a different item than the full
    scan; sorted(pool) makes the two implementations agree by construction.
"""

from __future__ import annotations


def norm(s: str) -> str:
    """Lowercase, expand '&' and strip punctuation/extra spaces."""
    s = s.lower().replace("&", " and ")
    out = []
    for ch in s:
        out.append(ch if ch.isalnum() or ch == " " else " ")
    return " ".join("".join(out).split())


def trigrams(s: str) -> frozenset[str]:
    p = f"  {s}  "
    return frozenset(p[i:i + 3] for i in range(len(p) - 2))


def jaccard(a: frozenset[str], b: frozenset[str]) -> float:
    if not a and not b:
        return 0.0
    inter = len(a & b)
    return inter / (len(a) + len(b) - inter)


class InvertedIndex:
    """Trigram posting-list index over a catalogue of normalised names."""

    def __init__(self, names: list[str]):
        self.names = [norm(n) for n in names]
        self.tri = [trigrams(n) for n in self.names]
        self.index: dict[str, list[int]] = {}
        for i, ts in enumerate(self.tri):
            for g in ts:
                self.index.setdefault(g, []).append(i)

    def candidates(self, query: str) -> set[int]:
        """Union of posting lists for the query's trigrams."""
        pool: set[int] = set()
        for g in trigrams(norm(query)):
            pool.update(self.index.get(g, ()))
        if not pool:
            pool = set(range(len(self.names)))
        return pool

    def best_match(self, query: str) -> tuple[int | None, float]:
        """Return (row index, score) of the top-1 candidate, or (None, 0.0)."""
        qt = trigrams(norm(query))
        pool = self.candidates(query)
        best, best_s = None, -1.0
        for i in sorted(pool):
            s = jaccard(qt, self.tri[i])
            if s > best_s:
                best_s, best = s, i
        return best, max(best_s, 0.0)
