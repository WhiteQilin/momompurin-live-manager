package ph.edu.umak.momompurin.data

import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.domain.search.TrigramProductMatcher
import ph.edu.umak.momompurin.domain.search.TrieProductAutocomplete

/**
 * The live search pipeline (Phase 2.1): PrefixTrie -> substring -> trigram.
 *
 * ## Why three stages, in this order
 *
 * The two ALGOCOM structures answer different questions, and the order matters:
 *
 *  1. **PrefixTrie over SKUs** answers "which rows start with what I typed" in
 *     O(|prefix| + hits). `CH`, `SCWH`, `MPBOW-PK` are all deliberate queries and
 *     their answers are deterministic, so they must never be outranked by a
 *     similarity score.
 *  2. **Substring** over name / SKU / category / character catches `bow pouch` and
 *     `kuromi`, which prefix matching cannot.
 *  3. **TrigramProductMatcher** (inverted trigram index + Jaccard) is the fallback
 *     for MESSY text: `chikwa wips`, `meldy pouch`, `sanrio wach`. It only runs
 *     when the deterministic stages returned nothing, so a typo query costs one
 *     indexed lookup rather than paying similarity on every keystroke.
 *
 * Each stage only runs when the one before it found nothing, which keeps the common
 * case at trie speed and the typo case correct rather than fast-but-wrong.
 *
 * ## Parent resolution (A-13 c4, preserved)
 *
 * A variant SKU that matches (`MPBOW-PK`, or `meldy pouch` landing on the variant
 * name) surfaces its PARENT's card, never a bare variant row - the Shop shows one
 * card per top-level product. Matching on a parent directly returns the parent.
 */
class SearchPipeline private constructor(
    private val products: List<Product>,
    private val matcher: TrigramProductMatcher,
    private val autocomplete: TrieProductAutocomplete,
) {

    /** The searchable text per SKU, built once. */
    private val haystack: Map<String, String> = products.associate { product ->
        product.sku to buildString {
            append(product.sku); append(' ')
            append(product.name); append(' ')
            append(product.category); append(' ')
            append(product.character); append(' ')
            // Variants of a parent are searchable THROUGH the parent card, which is
            // what makes `meldy pouch` (a variant-only name) find MPBOW.
            if (product.isParent) {
                products.filter { it.parentSku == product.sku }.forEach { child ->
                    append(child.sku); append(' ')
                    append(child.name); append(' ')
                }
            }
        }.lowercase()
    }

    /** Every parent card, keyed by SKU. */
    private val parents: Map<String, Product> =
        products.filter { it.parentSku == null }.associateBy { it.sku }

    /**
     * Search, returning top-level cards in ranked order.
     *
     * An empty query returns every card in catalogue order (A-13's unfiltered grid).
     */
    fun search(query: String): List<Product> {
        val q = query.trim()
        if (q.isEmpty()) {
            return parents.values.filter { isListable(it) }.sortedBy { it.name }
        }
        val skuOrder = runPipeline(q)
        return skuOrder.mapNotNull { parents[it] }.filter { isListable(it) }
    }

    private fun isListable(product: Product): Boolean {
        if (!product.active) return false
        if (product.isParent) {
            // A parent lists only while at least one variant has a price, which is
            // the same rule MomoData.shopProducts applies to the unfiltered grid.
            return products.any { it.parentSku == product.sku && it.price > 0 && it.active }
        }
        return product.price > 0
    }

    /**
     * The three stages, returning SKUs in rank order.
     *
     * Stage results are concatenated, de-duplicated keeping the best rank, so a row
     * the trie and the substring pass both found appears once, at its best rank.
     */
    private fun runPipeline(query: String): List<String> {
        val q = query.trim()
        val lower = q.lowercase()
        val ranked = LinkedHashMap<String, Int>()

        // ---- Stage 1: exact / prefix SKU through the trie (O(|p| + k)) --------
        autocomplete.byPrefix(q).forEach { row ->
            val sku = row.sku
            if (!ranked.containsKey(sku)) ranked[sku] = ranked.size
        }

        // ---- Stage 2: substring over name / SKU / category / character --------
        haystack.forEach { (sku, text) ->
            if (text.contains(lower) && !ranked.containsKey(sku)) {
                ranked[sku] = ranked.size
            }
        }

        // ---- Stage 3: trigram + Jaccard, ONLY when the deterministic stages
        // found nothing. Running it unconditionally would let a fuzzy score
        // outrank rows the owner typed exactly, which is the failure mode the
        // matcher's own docs warn about.
        if (ranked.isEmpty()) {
            matcher.search(q).forEach { match ->
                val sku = match.sku
                if (!ranked.containsKey(sku)) ranked[sku] = ranked.size
            }
        }

        // ---- Parent resolution: a variant match surfaces the parent card -------
        return ranked.keys.map { sku ->
            products.firstOrNull { it.sku == sku }?.let { product ->
                product.parentSku ?: product.sku
            } ?: sku
        }.distinct()
    }

    companion object {

        /** Build the pipeline over a catalogue snapshot. */
        fun build(data: MomoData): SearchPipeline {
            val products = data.products
            val rows = products.map { product ->
                TrigramProductMatcher.CatalogueRow(
                    sku = product.sku,
                    name = product.name,
                    character = product.character.takeIf { it.isNotBlank() },
                    category = product.category.takeIf { it.isNotBlank() },
                )
            }
            return SearchPipeline(
                products = products,
                matcher = TrigramProductMatcher(rows),
                autocomplete = TrieProductAutocomplete(rows),
            )
        }
    }
}
