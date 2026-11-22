package ph.edu.umak.momompurin.domain.search

/**
 * Port of `algorithms/trie.py` - a prefix tree for POS-style SKU lookup.
 *
 * Like phone predictive text: as the owner types "POM-", the tree is traversed and
 * every item whose key passes through that node comes back, with no scan.
 *
 * `hits` at each node carries every row whose key passes through it, so a prefix
 * query costs O(len(prefix) + output). The Python reports a 119.1x speedup against
 * scanning every row, with identical result sets - [TrieProductAutocomplete] keeps
 * a linear baseline so a test can assert that equality here too.
 */
class PrefixTrie(keys: List<String>) {

    private class Node {
        val children = HashMap<Char, Node>()
        val hits = ArrayList<Int>()
    }

    private val root = Node()

    init {
        keys.forEachIndexed { idx, key ->
            var node = root
            node.hits.add(idx)
            for (ch in key) {
                node = node.children.getOrPut(ch) { Node() }
                node.hits.add(idx)
            }
        }
    }

    /** Every row index whose key starts with [prefix]. */
    fun prefix(prefix: String): List<Int> {
        var node: Node? = root
        for (ch in prefix) {
            node = node?.children?.get(ch) ?: return emptyList()
        }
        return node!!.hits
    }
}

/**
 * SKU autocomplete backed by [PrefixTrie], with the linear baseline the benchmark
 * compares against.
 */
class TrieProductAutocomplete(rows: List<TrigramProductMatcher.CatalogueRow>) {

    private val rows = rows
    /** The trie is keyed on the normalised SKU, as the Python demo keys on codes. */
    private val keys = rows.map { TrigramText.norm(it.sku) }
    private val trie = PrefixTrie(keys)

    /** Trie-backed prefix hit. */
    fun byPrefix(prefix: String): List<TrigramProductMatcher.CatalogueRow> {
        val p = TrigramText.norm(prefix)
        if (p.isEmpty()) return emptyList()
        return trie.prefix(p).map { rows[it] }
    }

    /** The baseline: filter every row. Must return the same rows in the same order. */
    fun byPrefixScan(prefix: String): List<TrigramProductMatcher.CatalogueRow> {
        val p = TrigramText.norm(prefix)
        if (p.isEmpty()) return emptyList()
        return rows.filterIndexed { i, _ -> keys[i].startsWith(p) }
    }
}
