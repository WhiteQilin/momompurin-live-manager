"""Prefix tree for POS-style SKU lookup.

Works like phone predictive text: as the owner types the first letters of a
product code ("POM-"), the engine traverses the tree and returns every item
whose key passes through that node, without scanning the catalogue.

`hits` at each node carries every row whose key passes through it, so a prefix
query is one traversal and no scan. Building costs O(total key length); a
query costs O(len(prefix) + output).

The baseline it is measured against — "filter every row" — lives in
demo/run_demo.py, and the demo asserts both return identical result sets.
"""

from __future__ import annotations


class TrieNode:
    __slots__ = ("children", "hits")

    def __init__(self):
        self.children: dict[str, "TrieNode"] = {}
        self.hits: list[int] = []


class Trie:
    """Prefix tree keyed on a normalised string, holding row indices."""

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
        """Return every row index whose key starts with `prefix`."""
        node = self.root
        for ch in prefix:
            node = node.children.get(ch)
            if node is None:
                return []
        return node.hits
