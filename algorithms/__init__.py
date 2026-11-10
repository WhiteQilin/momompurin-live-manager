"""Momompurin Live Manager — core algorithm implementations.

Each module is the production-shaped implementation of one algorithm the
ALGOCOM PBL Milestone 1 specifies, extracted from the benchmark harness so it
can be read and run on its own:

  trie.py             prefix tree for POS-style SKU lookup (typo-tolerant
                      retrieval's fast path)
  inverted_index.py   trigram index + Jaccard scoring for messy live-chat
                      claim matching
  fsm.py              finite state machine over stock statuses with atomic
                      transition invariants
  expiry.py           min-heap order deadline expiry (24-hour auto-cancel
                      reminder sweep)

Design rule carried over from the benchmark harness: every implementation is
paired with the naive baseline a developer would write first, and a test
asserts the two agree on the answer. A fast wrong answer is not a result.
"""

from .trie import Trie
from .inverted_index import InvertedIndex, jaccard, trigrams
from .fsm import StockFSM, STATES, LEGAL
from .expiry import DeadlineHeap
from .tracking import validate_tracking, self_test as tracking_self_test

__all__ = [
    "Trie",
    "InvertedIndex",
    "jaccard",
    "trigrams",
    "StockFSM",
    "STATES",
    "LEGAL",
    "DeadlineHeap",
    "validate_tracking",
]
