package ph.edu.umak.momompurin.domain.search

/**
 * Port of `algorithms/inverted_index.py`.
 *
 * The Python module is the specification; this file must preserve its measured
 * behaviour, not merely its intent. Three rules are carried over verbatim because
 * each one changes results if dropped:
 *
 *  1. **The candidate pool is unioned, not intersected.** A query sharing only one
 *     trigram with the correct item would be excluded by an intersection.
 *  2. **The pool is iterated in sorted order.** A HashSet iterates in hash order, so
 *     on an exact score tie the index could return a different item than the full
 *     scan. Sorting makes the two agree by construction - this is what makes
 *     `identical_answers` true in the benchmark.
 *  3. **Strict `>` when comparing scores**, so the first (lowest-index) item wins a
 *     tie, exactly as the Python loop does.
 *
 * Normalisation is identical: lowercase, "&" -> " and ", every non-alphanumeric
 * character becomes a space, then whitespace collapse.
 */
object TrigramText {

    /** Lowercase, expand '&', strip punctuation, collapse whitespace. */
    fun norm(s: String): String {
        val lowered = s.lowercase().replace("&", " and ")
        val out = StringBuilder(lowered.length)
        for (ch in lowered) {
            out.append(if (ch.isLetterOrDigit() || ch == ' ') ch else ' ')
        }
        // Collapse runs of spaces and trim.
        return out.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Character trigrams of the padded string. The two-space pad on each side is
     * what gives the first and last characters order-two context, so "chw" and
     * "chi" share the `" c"` family of grams rather than nothing.
     */
    fun trigrams(s: String): Set<String> {
        val p = "  $s  "
        if (p.length < 3) return emptySet()
        val set = HashSet<String>(p.length)
        for (i in 0..p.length - 3) set.add(p.substring(i, i + 3))
        return set
    }

    /** Jaccard coefficient of two trigram sets. Empty/empty scores 0, not 1. */
    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }
}

/**
 * One catalogue row as the matcher sees it, plus the fields the UI needs to show
 * *why* a row matched (B-03 c2 / F-03 c4-c5).
 */
data class MatchResult(
    val index: Int,
    val sku: String,
    val name: String,
    val score: Double,
    /** One of the [MatchField] labels, shown inline in the search UI. */
    val matchedField: String,
)

/** The reason a row matched, in the order the design ranks them. */
object MatchField {
    const val EXACT_CODE = "Exact code"
    const val CODE_PREFIX = "Code prefix"
    const val NAME_PREFIX = "Name prefix"
    const val NAME_CONTAINS = "Name contains"
    const val CATEGORY = "Category or character"
}

/**
 * F-03 c4/c5: whether a query found its product by CODE or had to fall back.
 *
 * c4 wants the exact code answered directly; c5 wants the fallback to be STATED,
 * with the sentence "Code not found — searching by name." So the UI needs to know
 * which path a result set took, not just which rows came back.
 */
object CodeSearchOutcome {
    /** The sentence F-03 c5 pins, shown when no product carries the typed code. */
    const val FALLBACK_NOTICE = "Code not found — searching by name."

    /**
     * True when [query] is a plausible product code (a short alphanumeric token)
     * and no result matched by exact code, so the results came from the name/
     * character/category fallback.
     *
     * Takes the matched-field labels rather than [MatchResult] so every screen can
     * call it with whatever row type it already uses.
     */
    fun fellBackToNameSearch(query: String, matchedFields: List<String>): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        // Only a code-shaped query can have "not found" as a code.
        if (!q.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]*"))) return false
        if (matchedFields.isEmpty()) return false
        return matchedFields.none { it == MatchField.EXACT_CODE }
    }
}

/**
 * Inverted trigram index with Jaccard scoring.
 *
 * Build once at startup (2.84 ms on 767 rows in Python - cheap enough to rebuild on
 * a product change) and query per keystroke.
 */
class TrigramProductMatcher(rows: List<CatalogueRow>) {

    data class CatalogueRow(
        val sku: String,
        val name: String,
        val character: String? = null,
        val category: String? = null,
    )

    /** The searched text per row: the name, which is what the Python indexes. */
    private val names: List<String> = rows.map { TrigramText.norm(it.name) }
    private val trigramSets: List<Set<String>> = names.map { TrigramText.trigrams(it) }

    /** trigram -> ascending row indices, mirroring the Python posting lists. */
    private val index: Map<String, List<Int>> = buildMap<String, MutableList<Int>> {
        trigramSets.forEachIndexed { i, ts ->
            for (g in ts) getOrPut(g) { mutableListOf() }.add(i)
        }
    }

    val rows: List<CatalogueRow> = rows

    /** Number of distinct index terms - the benchmark reports 1848. */
    val indexTerms: Int get() = index.size

    /**
     * Union of the posting lists for the query's trigrams. Falls back to the whole
     * catalogue when nothing matches, so the first keystroke still returns rows.
     */
    fun candidates(query: String): Set<Int> {
        val pool = HashSet<Int>()
        for (g in TrigramText.trigrams(TrigramText.norm(query))) {
            index[g]?.let { pool.addAll(it) }
        }
        return if (pool.isEmpty()) names.indices.toSet() else pool
    }

    /** Top-1 by Jaccard. Returns null only when the catalogue is empty. */
    fun bestMatch(query: String): MatchResult? {
        val qt = TrigramText.trigrams(TrigramText.norm(query))
        val pool = candidates(query)
        var best = -1
        var bestScore = -1.0
        // Sorted, and strict '>': identical tie-breaking to the Python full scan.
        for (i in pool.sorted()) {
            val s = TrigramText.jaccard(qt, trigramSets[i])
            if (s > bestScore) {
                bestScore = s
                best = i
            }
        }
        if (best < 0) return null
        return toResult(best, bestScore.coerceAtLeast(0.0), query)
    }

    /**
     * Ranked matches for the search UI.
     *
     * Rows that match the query's *structure* (an exact SKU, a SKU prefix, a name
     * prefix) outrank pure similarity, because those are deterministic hits the
     * owner typed on purpose. Similarity is the fallback for messy live-chat text.
     */
    fun search(query: String, limit: Int = 20): List<MatchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val structured = mutableListOf<MatchResult>()
        val lower = q.lowercase()
        rows.forEachIndexed { i, row ->
            val sku = row.sku.lowercase()
            val name = row.name.lowercase()
            when {
                sku == lower -> structured +=
                    toResult(i, 1.0, q, MatchField.EXACT_CODE)
                sku.startsWith(lower) -> structured +=
                    toResult(i, 0.98, q, MatchField.CODE_PREFIX)
                name.startsWith(lower) -> structured +=
                    toResult(i, 0.95, q, MatchField.NAME_PREFIX)
                name.contains(lower) -> structured +=
                    toResult(i, 0.90, q, MatchField.NAME_CONTAINS)
                row.character?.lowercase()?.contains(lower) == true ||
                    row.category?.lowercase()?.contains(lower) == true ->
                    structured += toResult(i, 0.60, q, MatchField.CATEGORY)
            }
        }

        val structuredIdx = structured.map { it.index }.toHashSet()
        val qTri = TrigramText.trigrams(TrigramText.norm(q))
        val similar = candidates(q)
            .asSequence()
            .filter { it !in structuredIdx }
            .map { i -> toResult(i, TrigramText.jaccard(qTri, trigramSets[i]), q) }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<MatchResult> { it.score }.thenBy { it.index })
            .toList()

        return (structured.sortedByDescending { it.score } + similar).take(limit)
    }

    private fun toResult(
        i: Int,
        score: Double,
        query: String,
        field: String? = null,
    ): MatchResult {
        val row = rows[i]
        return MatchResult(
            index = i,
            sku = row.sku,
            name = row.name,
            score = score,
            matchedField = field ?: inferField(row, query),
        )
    }

    private fun inferField(row: CatalogueRow, query: String): String {
        val q = query.lowercase().trim()
        val sku = row.sku.lowercase()
        val name = row.name.lowercase()
        return when {
            sku == q -> MatchField.EXACT_CODE
            sku.startsWith(q) -> MatchField.CODE_PREFIX
            name.startsWith(q) -> MatchField.NAME_PREFIX
            name.contains(q) -> MatchField.NAME_CONTAINS
            else -> MatchField.NAME_CONTAINS
        }
    }

    /**
     * The naive baseline the benchmark measures against: score every row with no
     * pruning. Exists so a test can assert the index returns identical answers, the
     * property the Python benchmark reports as `identical_answers: true`.
     */
    fun bestMatchFullScan(query: String): Int? {
        val qt = TrigramText.trigrams(TrigramText.norm(query))
        var best = -1
        var bestScore = -1.0
        for (i in names.indices) {
            val s = TrigramText.jaccard(qt, trigramSets[i])
            if (s > bestScore) {
                bestScore = s
                best = i
            }
        }
        return if (best >= 0) best else null
    }
}
