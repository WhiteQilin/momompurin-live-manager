package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.PhAddresses
import ph.edu.umak.momompurin.data.SeedData
import java.io.File

/**
 * The PSGC asset that replaced the seven-province hand-curated map.
 *
 * Two kinds of assertion live here:
 *
 *  - **the data is complete** - 82 provinces, the 17 NCR LGUs, Taguig's 28+ real
 *    barangays, Umingan's 58 - so a regenerated asset cannot silently shrink;
 *  - **the app can still find what it stores** - every address triple in
 *    [SeedData] resolves, so a seeded customer opening Edit profile sees their own
 *    address pre-selected instead of a blank field.
 *
 * The asset is loaded from `src/main/assets/ph_addresses.json`, which is the same
 * file that ships in the APK: [PhAddresses] falls back to that path when no
 * Android Context has installed a reader, which is exactly the JVM test case.
 */
class PhAddressesAssetTest {

    private val tree = PhAddresses.tree

    // ------------------------------------------------------------ completeness --

    @Test
    fun the_asset_loads_and_is_not_the_fallback_map() {
        // 82 provinces and ~42,000 barangays is the real PSGC shape. The fallback
        // carries 7 provinces, so this single assertion proves the asset - not the
        // emergency map - is what loaded.
        assertTrue(
            "expected the PSGC asset, got ${tree.size} provinces (fallback has 7)",
            tree.size >= 80,
        )
        val barangays = tree.values.sumOf { cities -> cities.values.sumOf { it.size } }
        assertTrue("expected ~42,000 barangays, got $barangays", barangays > 40_000)
    }

    @Test
    fun there_are_at_least_eighty_provinces() {
        val provinces = PhAddresses.provinces()
        assertTrue("provinces: ${provinces.size}", provinces.size >= 80)
        // Metro Manila is pinned first; the rest follow alphabetically.
        assertEquals("Metro Manila", provinces.first())
        val rest = provinces.drop(1)
        assertEquals(
            "provinces after Metro Manila must be alphabetical",
            rest.sortedBy { it.lowercase() },
            rest,
        )
    }

    @Test
    fun the_named_provinces_are_present() {
        val provinces = PhAddresses.provinces()
        listOf("Metro Manila", "Pangasinan", "Cavite", "Cebu", "Davao del Sur")
            .forEach { province ->
                assertTrue("missing province: $province", province in provinces)
            }
    }

    @Test
    fun metro_manila_has_exactly_the_seventeen_ncr_lgus() {
        val cities = PhAddresses.cities("Metro Manila")
        assertEquals("NCR LGUs: $cities", 17, cities.size)
        listOf(
            "Manila", "Makati", "Taguig", "Quezon City", "Pateros",
            "Pasay City", "Caloocan City", "Las Piñas", "Parañaque",
            "Muntinlupa", "Marikina", "Valenzuela", "Malabon City",
            "Navotas City", "San Juan City", "Mandaluyong", "Pasig",
        ).forEach { lgu ->
            assertTrue("missing NCR LGU: $lgu (have $cities)", lgu in cities)
        }
    }

    @Test
    fun manilas_districts_are_folded_into_barangays_not_cities() {
        // The PSGC splits the capital into 14 districts whose "barangays" are zone
        // numbers. A customer picks Manila then a barangay - never "Binondo" as a
        // city - so the districts must appear as barangays of Manila.
        val manila = PhAddresses.barangays("Metro Manila", "Manila")
        assertTrue("Manila should carry its district names", "Binondo" in manila)
        assertTrue("Manila should carry its zone numbers", manila.any { it.startsWith("Barangay ") })
        assertFalse(
            "Binondo must NOT be a city of Metro Manila",
            "Binondo" in PhAddresses.cities("Metro Manila"),
        )
    }

    @Test
    fun taguig_has_all_its_real_barangays() {
        val taguig = PhAddresses.barangays("Metro Manila", "Taguig")
        assertTrue("Taguig barangays: ${taguig.size}", taguig.size >= 25)
        listOf(
            "Lower Bicutan", "Upper Bicutan", "Western Bicutan", "Central Bicutan",
            "New Lower Bicutan", "Fort Bonifacio", "Ususan",
            // Taguig has FOUR Signal Village barangays; there is no bare
            // "Signal Village", which is exactly why the assertion lists each.
            "North Signal Village", "South Signal Village", "Central Signal Village",
        ).forEach { barangay ->
            assertTrue("missing Taguig barangay: $barangay (have $taguig)", barangay in taguig)
        }
    }

    @Test
    fun bgc_is_searchable_under_taguig() {
        // `BGC` is what everyone types; `Fort Bonifacio` is the official name. Both
        // resolve, which is the point of the alias.
        val taguig = PhAddresses.barangays("Metro Manila", "Taguig")
        assertTrue("BGC alias missing", "BGC" in taguig)
        assertTrue("official name missing", "Fort Bonifacio" in taguig)
    }

    @Test
    fun umingan_has_its_full_barangay_list() {
        val umingan = PhAddresses.barangays("Pangasinan", "Umingan")
        assertTrue("Umingan barangays: ${umingan.size}", umingan.size >= 50)
        // A couple of the real names, so a truncated list cannot pass on count alone.
        assertTrue("expected real Umingan barangays, got $umingan", "Annam" in umingan)
        assertTrue("expected real Umingan barangays, got $umingan", "Abot Molina" in umingan)
    }

    @Test
    fun a_large_city_opens_with_a_full_barangay_list() {
        // Quezon City has 142 barangays and Manila has ~900 - the case the report
        // named. Both must be complete, which is what makes the LazyColumn in the
        // sheet load-bearing rather than decorative.
        val qc = PhAddresses.barangays("Metro Manila", "Quezon City")
        assertTrue("QC barangays: ${qc.size}", qc.size >= 100)
        val manila = PhAddresses.barangays("Metro Manila", "Manila")
        assertTrue("Manila barangays: ${manila.size}", manila.size >= 200)
    }

    // -------------------------------------------------------------- seed parity --

    @Test
    fun every_seeded_address_triple_resolves() {
        // `Account` carries the three fields flat, so the triple is built directly.
        val triples: List<Triple<String, String, String>> = SeedData.build().accounts
            .map { Triple(it.province, it.city, it.barangay) }
            .filter { it.first.isNotBlank() }
            .distinct()
        assertTrue("expected seeded addresses to test", triples.isNotEmpty())

        triples.forEach { (province, city, barangay) ->
            assertTrue("province not mapped: $province", PhAddresses.knowsProvince(province))
            assertTrue(
                "city not mapped: $province / $city",
                PhAddresses.knowsCity(province, city),
            )
            assertTrue(
                "barangay not mapped: $province / $city / $barangay",
                PhAddresses.knowsBarangay(province, city, barangay),
            )
        }
    }

    @Test
    fun the_seeded_order_addresses_resolve_too() {
        val orders = SeedData.build().orders
        assertTrue(orders.isNotEmpty())
        orders.forEach { order ->
            assertTrue(
                "order ${order.orderId} province not mapped: ${order.province}",
                PhAddresses.knowsProvince(order.province),
            )
            assertTrue(
                "order ${order.orderId} city not mapped: ${order.province} / ${order.city}",
                PhAddresses.knowsCity(order.province, order.city),
            )
            assertTrue(
                "order ${order.orderId} barangay not mapped: " +
                    "${order.province} / ${order.city} / ${order.barangay}",
                PhAddresses.knowsBarangay(order.province, order.city, order.barangay),
            )
        }
    }

    @Test
    fun the_specific_seeded_values_the_report_named_are_present() {
        assertTrue(PhAddresses.knowsBarangay("Metro Manila", "Quezon City", "Batasan"))
        assertTrue(PhAddresses.knowsBarangay("Rizal", "Antipolo", "San Roque"))
        assertTrue(PhAddresses.knowsBarangay("Cavite", "Bacoor", "Molino"))
        assertTrue(PhAddresses.knowsBarangay("Metro Manila", "Mandaluyong", "Plainview"))
        assertTrue(PhAddresses.knowsBarangay("Metro Manila", "Pasig", "San Antonio"))
        assertTrue(PhAddresses.knowsBarangay("Metro Manila", "Makati", "Poblacion"))
    }

    // ------------------------------------------------------------- C.1 contract --

    @Test
    fun metro_manila_still_prints_with_its_ncr_suffix() {
        assertEquals("Metro Manila (NCR)", PhAddresses.provinceLabel("Metro Manila"))
        assertEquals("Cavite", PhAddresses.provinceLabel("Cavite"))
    }

    @Test
    fun the_manual_row_is_still_offered_at_every_level() {
        assertTrue(PhAddresses.options(PhAddresses.provinces()).any { PhAddresses.isManual(it) })
        assertTrue(
            PhAddresses.options(PhAddresses.cities("Metro Manila")).any {
                PhAddresses.isManual(it)
            },
        )
        assertTrue(
            PhAddresses.options(PhAddresses.barangays("Metro Manila", "Taguig")).any {
                PhAddresses.isManual(it)
            },
        )
    }

    @Test
    fun an_unknown_province_is_still_treated_as_manual() {
        // A typed province is not in the map, which is what flips that level into
        // manual mode rather than a validation error.
        assertFalse(PhAddresses.knowsProvince("Atlantis"))
        assertTrue(PhAddresses.cities("Atlantis").isEmpty())
    }

    @Test
    fun the_cascade_clearing_rules_are_unchanged() {
        assertEquals(Triple("Metro Manila", "", ""), PhAddresses.clearBelow("Metro Manila"))
        assertEquals(
            Triple("Metro Manila", "Taguig", ""),
            PhAddresses.clearBarangay("Metro Manila", "Taguig"),
        )
    }

    // ------------------------------------------------------- title-case quality --

    @Test
    fun names_are_title_cased_without_mangling_numerals_or_abbreviations() {
        val cavite = PhAddresses.barangays("Cavite", "Bacoor")
        // `MOLINO I`..`MOLINO VII` must keep their roman numerals, not become
        // `Molino i`.
        listOf("Molino I", "Molino II", "Molino III", "Molino VII").forEach { name ->
            assertTrue("roman numeral mangled: expected $name in $cavite", name in cavite)
        }

        // `(POB.)` must survive as an abbreviation, not `(Pob.)` lowercased away or
        // `Pob` with the dot lost.
        val antipolo = PhAddresses.barangays("Rizal", "Antipolo")
        assertTrue(
            "Pob. abbreviation lost: $antipolo",
            antipolo.any { it.contains("Pob.") },
        )
    }

    @Test
    fun city_prefixes_are_stripped_but_city_suffixes_are_kept() {
        val ncr = PhAddresses.cities("Metro Manila")
        // `CITY OF MAKATI` -> `Makati`, never `City Of Makati`.
        assertTrue("Makati must be bare", "Makati" in ncr)
        assertFalse(ncr.any { it.startsWith("City Of") })
        // `QUEZON CITY` and `PASAY CITY` keep the suffix people search for.
        assertTrue("Quezon City must keep its suffix", "Quezon City" in ncr)
        assertTrue("Pasay City must keep its suffix", "Pasay City" in ncr)
    }

    @Test
    fun no_name_is_left_in_all_caps() {
        // A spot check on the largest lists: an ALL-CAPS leftover means the
        // title-caser missed a shape, and it would show in the picker.
        val qc = PhAddresses.barangays("Metro Manila", "Quezon City")
        val allCaps = qc.filter { it.length > 4 && it == it.uppercase() && it.any { c -> c.isLetter() } }
        assertTrue("ALL-CAPS names survived in QC: ${allCaps.take(5)}", allCaps.isEmpty())
    }

    @Test
    fun the_asset_file_is_present_and_parses_as_utf8() {
        val file = listOf(
            File("src/main/assets/ph_addresses.json"),
            File("app/src/main/assets/ph_addresses.json"),
        ).firstOrNull { it.isFile }
        assertTrue("ph_addresses.json not found from ${File(".").absolutePath}", file != null)
        val bytes = file!!.readBytes()
        assertTrue("asset is empty", bytes.size > 100_000)
        // Valid UTF-8: a lone 0xB1 byte would be a mojibake'd ñ.
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue("asset did not decode as UTF-8", text.contains("Las Pi"))
        assertTrue("expected the ñ to survive", text.contains("Las Pi\u00F1as"))
        assertTrue("expected Parañaque to survive", text.contains("Para\u00F1aque"))
    }
}
