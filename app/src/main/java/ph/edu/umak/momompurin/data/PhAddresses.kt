package ph.edu.umak.momompurin.data

import java.io.File

/**
 * The complete Philippine address tree behind the cascading pickers (C.1).
 *
 * ## What changed and why
 *
 * This used to be a hand-curated seven-province map. The trade-off was honest but
 * the outcome was not: a customer in Umingan, Pangasinan - a real municipality with
 * 58 published barangays - was pushed into "Other (type manually)" for a value the
 * government has published for decades. The tester's report asked for the complete
 * list, searchable, and that is what ships now.
 *
 * ## Where the data comes from
 *
 * `app/src/main/assets/ph_addresses.json`, generated from the community PSGC dump
 * by `tools/build_ph_addresses_asset.py`. That script is the only thing allowed to
 * write the asset: it collapses the five NCR pseudo-provinces into one
 * [NCR] key, title-cases every ALL-CAPS name without mangling `I`/`II`/`Pob.`,
 * folds Manila's 14 districts into barangays of one `Manila` city, and guarantees
 * every triple in [SeedData] is present.
 *
 * ## Loading, and why it is lazy rather than eager
 *
 * The asset is ~600 KB of JSON holding 42,000 barangays. Parsing it on the main
 * thread during `Application.onCreate` would add visible startup latency to a cold
 * launch for a screen most sessions never open. So it is parsed on FIRST USE and
 * cached for the process lifetime - the first address field the user taps pays the
 * cost.
 *
 * ## JVM tests must see the same data
 *
 * A Gradle unit test has no `Context` and no `assets/` directory. Rather than ship
 * a second copy of the tree for tests, [tree] resolves the asset through a
 * three-step lookup: the Android asset, then the asset path relative to the module
 * directory (which is where a JVM test runs), then a compact built-in fallback so
 * the app still works if the asset is somehow missing from the APK.
 */
object PhAddresses {

    /** The escape hatch: type the value by hand when it is not in the map. */
    const val MANUAL = "Other (type manually)"

    /**
     * The province the revised spec names explicitly as `Metro Manila (NCR)`.
     *
     * The map's KEY stays `"Metro Manila"` because every seeded account, order and
     * saved profile stores that exact string - renaming the key would strand them
     * all in manual mode. Only the LABEL the dropdown shows carries the `(NCR)`
     * suffix, which is what the criterion asks the customer to see.
     */
    const val NCR = "Metro Manila"

    /** What the Province dropdown prints for [province]. */
    fun provinceLabel(province: String): String =
        if (province == NCR) "$NCR (NCR)" else province

    // ---------------------------------------------------------------- loading ---

    /** The asset's path inside the APK, and on disk for a JVM test. */
    const val ASSET_NAME = "ph_addresses.json"

    /** Set once by the Application so the packaged asset can be opened. */
    @Volatile
    private var assetLoader: (() -> String?)? = null

    /**
     * Hand the object a way to read the packaged asset.
     *
     * Called from [ph.edu.umak.momompurin.MomompurinApp] with
     * `{ context.assets.open(ASSET_NAME).bufferedReader().readText() }`. Kept as a
     * lambda rather than a `Context` field so nothing in the data layer holds an
     * Activity-scoped reference, and so a unit test can inject a reader without
     * Android being on the classpath.
     */
    fun installAssetLoader(loader: (() -> String)?) {
        assetLoader = loader
        cached = null
    }

    /** Parsed tree, built once per process. */
    @Volatile
    private var cached: Map<String, Map<String, List<String>>>? = null

    /** The full province -> city -> barangays tree. */
    val tree: Map<String, Map<String, List<String>>>
        get() = cached ?: synchronized(this) {
            cached ?: load().also { cached = it }
        }

    private fun load(): Map<String, Map<String, List<String>>> {
        // 1. The packaged asset, once the app has installed a reader.
        runCatching { assetLoader?.invoke() }.getOrNull()
            ?.let { text -> parse(text)?.let { return it } }

        // 2. The asset on disk, relative to the module directory. This is the path a
        //    Gradle unit test takes: it runs with the module as its working
        //    directory, so `src/main/assets/ph_addresses.json` is right there.
        val candidates = listOf(
            File("src/main/assets/$ASSET_NAME"),
            File("app/src/main/assets/$ASSET_NAME"),
        )
        for (file in candidates) {
            if (file.isFile) {
                runCatching { file.readText() }.getOrNull()
                    ?.let { text -> parse(text)?.let { return it } }
            }
        }

        // 3. Last resort: the compact map this object used to carry. Never reached in
        //    a normal build, but it keeps the cascade usable if the asset is lost -
        //    a missing JSON file must not turn every address field into a dead end.
        return FALLBACK
    }

    /**
     * Parse the asset.
     *
     * Hand-rolled rather than pulling in a JSON dependency: the shape is fixed and
     * shallow (object of objects of arrays of strings), and the reader below is
     * short against a library the app otherwise does not need. A malformed document
     * returns null so the caller falls through to the next source instead of
     * crashing.
     */
    private fun parse(text: String): Map<String, Map<String, List<String>>>? =
        runCatching { JsonReader(text).readAddressTree() }.getOrNull()

    // ------------------------------------------------------------------ queries --

    /** Every province: [NCR] first, then the rest alphabetically. */
    fun provinces(): List<String> = tree.keys.toList()

    /**
     * The cities of [province], or empty when nothing is selected.
     *
     * Empty - not "all cities" - because C.1 requires the City dropdown to be
     * disabled and empty until a province is picked. Returning everything would let
     * a customer pick a city that belongs to another province.
     */
    fun cities(province: String?): List<String> =
        tree[province?.trim()].orEmpty().keys.toList()

    /** The barangays of [city] within [province], or empty. */
    fun barangays(province: String?, city: String?): List<String> =
        tree[province?.trim()]?.get(city?.trim()).orEmpty()

    /** True when [province] is one of the mapped provinces. */
    fun knowsProvince(province: String?): Boolean =
        tree.containsKey(province?.trim())

    /** True when [city] belongs to [province]. */
    fun knowsCity(province: String?, city: String?): Boolean =
        tree[province?.trim()]?.containsKey(city?.trim()) == true

    /** True when [barangay] belongs to [city] within [province]. */
    fun knowsBarangay(province: String?, city: String?, barangay: String?): Boolean =
        tree[province?.trim()]?.get(city?.trim())?.contains(barangay?.trim()) == true

    /**
     * What a province change leaves behind: the province, with city and barangay
     * cleared.
     *
     * Returned as a triple so the three fields move together - a screen that cleared
     * the city but forgot the barangay would leave a value whose parent no longer
     * matches, which is the state C.1 exists to prevent.
     */
    fun clearBelow(province: String): Triple<String, String, String> =
        Triple(province, "", "")

    /** What a city change leaves behind: province and city, with barangay cleared. */
    fun clearBarangay(province: String, city: String): Triple<String, String, String> =
        Triple(province, city, "")

    /**
     * The option list for a level, with [MANUAL] appended.
     *
     * [MANUAL] is always last so it never sits where a real value would be, which
     * keeps the common case at the top of a short list.
     */
    fun options(values: List<String>): List<String> = values + MANUAL

    /** True when the chosen value means "I will type it". */
    fun isManual(value: String?): Boolean = value?.trim() == MANUAL

    // --------------------------------------------------------- fallback map ------

    /**
     * The seven-province map this object carried before the PSGC asset existed.
     *
     * Kept only so a lost asset degrades to a usable cascade instead of a dead end.
     * `PhAddressesAssetTest` asserts the real asset is what loads in tests, so this
     * path is not the one the suite exercises.
     */
    private val FALLBACK: Map<String, Map<String, List<String>>> = linkedMapOf(
        NCR to linkedMapOf(
            "Makati" to listOf("Bel-Air", "Poblacion", "San Lorenzo", "Urdaneta"),
            "Mandaluyong" to listOf("Addition Hills", "Plainview", "Wack-Wack"),
            "Manila" to listOf("Ermita", "Malate", "Sampaloc", "Tondo"),
            "Pasig" to listOf("Kapitolyo", "Ortigas", "San Antonio"),
            "Quezon City" to listOf("Batasan", "Cubao", "Diliman", "Fairview"),
            "Taguig" to listOf("BGC", "Pateros-side", "Signal Village", "Ususan"),
        ),
        "Cavite" to linkedMapOf(
            "Bacoor" to listOf("Molino", "Panapaan", "Talaba", "Zapote"),
            "Dasmariñas" to listOf("Burol", "Paliparan", "Salawag", "San Agustin"),
            "Imus" to listOf("Anabu", "Bucandala", "Malagasang", "Tanzang Luma"),
        ),
        "Rizal" to linkedMapOf(
            "Antipolo" to listOf("Dela Paz", "Mambugan", "San Roque", "Mayamot"),
            "Binangonan" to listOf("Calumpang", "Layunan", "Pag-asa"),
            "Cainta" to listOf("San Andres", "San Isidro", "Sto. Domingo"),
        ),
        "Pangasinan" to linkedMapOf(
            "Dagupan" to listOf("Bonuan Gueset", "Lucao", "Tapuac"),
            "Umingan" to listOf("Don Montano", "Poblacion East", "Poblacion West"),
        ),
        "Laguna" to linkedMapOf(
            "Calamba" to listOf("Canlubang", "Real", "Sala", "Turbina"),
            "San Pedro" to listOf("Landayan", "Nueva", "San Antonio"),
            "Santa Rosa" to listOf("Balibago", "Dila", "Tagapo"),
        ),
        "Bulacan" to linkedMapOf(
            "Meycauayan" to listOf("Banga", "Camalig", "Poblacion"),
            "San Jose del Monte" to listOf("Muzon", "San Rafael", "Tungkong Mangga"),
            "Santa Maria" to listOf("Bagbaguin", "Poblacion", "Sto. Tomas"),
        ),
    )

    // ------------------------------------------------------------- tiny parser ---

    /**
     * A minimal reader for the asset's fixed shape: object of objects of arrays of
     * strings, with only string keys and string values.
     *
     * Written by hand because the app has no JSON dependency and the document is
     * machine-generated with a known shape. It handles escapes and unicode so a
     * name like `Las Piñas` or `Sto. Tomas` round-trips exactly.
     */
    private class JsonReader(private val src: String) {
        private var i = 0

        private fun skipWs() {
            while (i < src.length && src[i].isWhitespace()) i++
        }

        private fun expect(ch: Char) {
            skipWs()
            require(i < src.length && src[i] == ch) { "expected '$ch' at $i" }
            i++
        }

        private fun readString(): String {
            skipWs()
            require(i < src.length && src[i] == '"') { "expected string at $i" }
            i++
            val sb = StringBuilder()
            while (i < src.length) {
                when (val c = src[i]) {
                    '"' -> { i++; return sb.toString() }
                    '\\' -> {
                        i++
                        when (val esc = src[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(i + 1, i + 5)
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(esc)
                        }
                        i++
                    }
                    else -> { sb.append(c); i++ }
                }
            }
            error("unterminated string")
        }

        fun readAddressTree(): Map<String, Map<String, List<String>>> {
            val out = LinkedHashMap<String, Map<String, List<String>>>()
            expect('{')
            skipWs()
            if (i < src.length && src[i] == '}') { i++; return out }
            while (true) {
                val province = readString()
                expect(':')
                out[province] = readCityMap()
                skipWs()
                when {
                    i < src.length && src[i] == ',' -> { i++ }
                    i < src.length && src[i] == '}' -> { i++; return out }
                    else -> error("expected , or } at $i")
                }
            }
        }

        private fun readCityMap(): Map<String, List<String>> {
            val out = LinkedHashMap<String, List<String>>()
            expect('{')
            skipWs()
            if (i < src.length && src[i] == '}') { i++; return out }
            while (true) {
                val city = readString()
                expect(':')
                out[city] = readStringArray()
                skipWs()
                when {
                    i < src.length && src[i] == ',' -> { i++ }
                    i < src.length && src[i] == '}' -> { i++; return out }
                    else -> error("expected , or } at $i")
                }
            }
        }

        private fun readStringArray(): List<String> {
            val out = ArrayList<String>()
            expect('[')
            skipWs()
            if (i < src.length && src[i] == ']') { i++; return out }
            while (true) {
                out.add(readString())
                skipWs()
                when {
                    i < src.length && src[i] == ',' -> { i++ }
                    i < src.length && src[i] == ']' -> { i++; return out }
                    else -> error("expected , or ] at $i")
                }
            }
        }
    }
}
