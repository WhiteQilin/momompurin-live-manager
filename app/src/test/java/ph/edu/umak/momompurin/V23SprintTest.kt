package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.AuditChain
import ph.edu.umak.momompurin.data.CourierTrackingValidator
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.SearchPipeline
import ph.edu.umak.momompurin.data.SeedData
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.ProductOption
import ph.edu.umak.momompurin.data.entity.Role
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.domain.search.TrigramProductMatcher
import ph.edu.umak.momompurin.domain.search.TrieProductAutocomplete
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * The v2.3 sprint's own controls, each pinned by a test so a regression fails the
 * build rather than resurfacing in a demo:
 *
 *  - A-05: the payment preview/commit split (Close aborts with zero writes);
 *  - A-05: the Unmark Paid recompute and the payment void that reopens a balance;
 *  - ALGOCOM: the trigram typo query and the trie prefix query through the live
 *    pipeline, with the parent-resolution rule preserved;
 *  - A-07 c8: the per-courier tracking format check;
 *  - IAS R.5: the SHA-256 audit chain, sealed on the seed and breaking on tamper;
 *  - IAS R.4: the salted PBKDF2 password hash (covered in AccountOpsTest, cited
 *    here for the round trip through the store).
 */
class V23SprintTest {

    private val data: MomoData = SeedData.build()
    private val store = MomoStore(data)

    // ---------------------------------------------------------------- A.3 ----

    /** A.3: the exact defect that failed A-05 - Close committed the payment. */
    @Test
    fun a03_checkPayment_writes_nothing_and_preview_reports_the_clear() {
        // ORD-1051: total 70.00, nothing paid. A full 70.00 payment clears it.
        val check = store.checkPayment("ORD-1051", "70.00", "GCash")
        assertTrue(check is OrderOps.PaymentCheck.Ready)
        val preview = (check as OrderOps.PaymentCheck.Ready).preview
        assertTrue("a balance-clearing payment must ask for the Paid dialog", preview.clearsBalance)
        assertEquals(PaymentStatus.PAID, preview.newStatus)
        assertEquals(0L, preview.newBalance)

        // The store is UNTOUCHED: zero writes, zero payment rows, zero log rows.
        assertEquals(data, store.snapshot())
        assertTrue(store.snapshot().paymentsFor("ORD-1051").isEmpty())
    }

    @Test
    fun a03_close_aborts_cleanly_then_the_corrected_amount_can_be_saved() {
        // Preview a clearing payment...
        val check = store.checkPayment("ORD-1051", "70.00", "GCash")
        assertNotNull((check as OrderOps.PaymentCheck.Ready).preview)
        // ..."Close" is modelled by simply NOT calling recordPayment. The corrected
        // partial amount then goes through the same validation and succeeds.
        val partial = store.recordPayment("ORD-1051", "30.00", "Maya")
        assertTrue(partial is OrderOps.PaymentResult.Recorded)
        val order = store.snapshot().order("ORD-1051")!!
        assertEquals(MomoMoney.parse("30.00"), order.amountPaid)
        assertEquals(PaymentStatus.PARTIALLY_PAID, order.paymentStatus)
        assertEquals(MomoMoney.parse("40.00"), order.balance)
    }

    /** A.3: Unmark Paid keeps the money rows and reopens the status. */
    @Test
    fun a03_unmarkPaid_keeps_rows_reopens_status_and_recordPayment_accepts_again() {
        // ORD-1040 is seeded fully Paid (57.00 of 57.00). Unmark it.
        val result = store.unmarkPaid("ORD-1040")
        assertTrue(result is OrderOps.UnmarkResult.Applied)
        val order = store.snapshot().order("ORD-1040")!!
        // Never PAID after an unmark - the confirmation is what was withdrawn.
        assertFalse(order.isPaid)
        // The money rows are kept: the recorded 57.00 remains the amount on record.
        assertEquals(MomoMoney.parse("570.00"), order.amountPaid)
        assertEquals(1, store.snapshot().paymentsFor("ORD-1040").size)
        // And Record payment is reachable again once a row is voided: void the
        // payment, the balance reopens, a new amount is accepted.
        val voided = store.voidPayment("ORD-1040", "PMT-1040-1")
        assertTrue(voided is OrderOps.UnmarkResult.Applied)
        val reopened = store.snapshot().order("ORD-1040")!!
        assertEquals(PaymentStatus.UNPAID, reopened.paymentStatus)
        assertEquals(0L, reopened.amountPaid)
        assertEquals(MomoMoney.parse("570.00"), reopened.balance)
        val again = store.recordPayment("ORD-1040", "570.00", "GCash")
        assertTrue(again is OrderOps.PaymentResult.Recorded)
        assertEquals(PaymentStatus.PAID, store.snapshot().order("ORD-1040")!!.paymentStatus)
    }

    /** A.3: the trapped state - Paid rows summing to the total - is escapable. */
    @Test
    fun a03_voiding_a_row_recomputes_paid_balance_and_status() {
        // Build the trap: a 50.00 order paid in full with one row.
        var d = data
        val placed = OrderOps.placeOrder(
            d,
            OrderOps.CheckoutRequest(
                customerEmail = "mika.tan@example.com",
                lines = listOf(ph.edu.umak.momompurin.data.entity.CartLine("CHW", 1)),
                fulfillment = ph.edu.umak.momompurin.data.entity.Fulfillment.SHIP_NOW,
                recipientName = "Mika Tan", mobile = "0917 555 0101",
                province = "Metro Manila", city = "Makati",
                barangay = "Poblacion", addressLine = "12 Mabini St.",
            ),
            offline = false,
        )
        d = (placed as OrderOps.PlaceResult.Placed).data
        val orderId = placed.orderId
        d = OrderOps.sendInvoice(d, orderId, "0").let {
            (it as OrderOps.InvoiceResult.Sent).data
        }
        d = (OrderOps.recordPayment(d, orderId, "35.00", "GCash")
            as OrderOps.PaymentResult.Recorded).data
        val paid = d.order(orderId)!!
        assertEquals(PaymentStatus.PAID, paid.paymentStatus)
        assertEquals(0L, paid.balance)

        // Unmark, then void the row: the order goes back to Unpaid with its full
        // balance - NOT stuck at Partially Paid / zero balance.
        d = (OrderOps.unmarkPaid(d, orderId) as OrderOps.UnmarkResult.Applied).data
        val paymentId = d.paymentsFor(orderId).single().paymentId
        d = (OrderOps.voidPayment(d, orderId, paymentId) as OrderOps.UnmarkResult.Applied).data
        val reopened = d.order(orderId)!!
        assertEquals(PaymentStatus.UNPAID, reopened.paymentStatus)
        assertEquals(0L, reopened.amountPaid)
        assertEquals(MomoMoney.parse("35.00"), reopened.balance)
        assertTrue(d.paymentsFor(orderId).isEmpty())
    }

    @Test
    fun a03_voidPayment_is_refused_after_dispatch() {
        // ORD-1035 has shipped: the money is with the courier.
        val result = store.voidPayment("ORD-1035", "PMT-1035-1")
        assertTrue(result is OrderOps.UnmarkResult.Refused)
        assertEquals(
            OrderOps.UNMARK_AFTER_DISPATCH,
            (result as OrderOps.UnmarkResult.Refused).message,
        )
    }

    // ------------------------------------------------------- ALGOCOM 2.1 ----

    /** Phase 2.1: the typo query the brief pins, through the LIVE pipeline. */
    @Test
    fun algocom_typo_query_chikwa_wips_finds_chiikawa_wipes_first() {
        val pipeline = SearchPipeline.build(data)
        val results = pipeline.search("chikwa wips")
        assertTrue(results.isNotEmpty())
        assertEquals("CHW", results.first().sku)
    }

    @Test
    fun algocom_typo_queries_find_their_rows() {
        val pipeline = SearchPipeline.build(data)
        assertEquals("MPBOW", pipeline.search("meldy pouch").first().sku)
        assertEquals("SCWH", pipeline.search("sanrio wach").first().sku)
    }

    /** Phase 2.1: prefix SKU queries resolve through the trie stage. */
    @Test
    fun algocom_prefix_query_mp_finds_melody_bow_pouch_parent() {
        val pipeline = SearchPipeline.build(data)
        val results = pipeline.search("MP")
        assertTrue(results.any { it.sku == "MPBOW" })
        // And the parent-resolution rule holds: only the PARENT card is returned,
        // never a bare variant row (A-13 c4).
        assertTrue(results.none { it.parentSku != null })
    }

    @Test
    fun algocom_exact_variant_sku_surfaces_the_parent_card() {
        val pipeline = SearchPipeline.build(data)
        val results = pipeline.search("MPBOW-PK")
        // The variant SKU matches, but the customer sees the parent.
        assertEquals("MPBOW", results.first().sku)
    }

    /** The structures underneath, asserted directly. */
    @Test
    fun algocom_trigram_matcher_ranks_the_typo_top() {
        val rows = data.products.map {
            TrigramProductMatcher.CatalogueRow(it.sku, it.name, it.character, it.category)
        }
        val matcher = TrigramProductMatcher(rows)
        assertEquals("CHW", matcher.search("chikwa wips").first().sku)
    }

    @Test
    fun algocom_trie_prefix_matches_the_linear_scan() {
        val rows = data.products.map {
            TrigramProductMatcher.CatalogueRow(it.sku, it.name, it.character, it.category)
        }
        val trie = TrieProductAutocomplete(rows)
        for (prefix in listOf("CH", "SCWH", "MPBOW", "W1", "HKEYE")) {
            assertEquals(trie.byPrefixScan(prefix), trie.byPrefix(prefix))
        }
    }

    // --------------------------------------------------- A-07 c8 / 2.2 ------

    @Test
    fun tracking_jt_accepts_the_valid_shapes_and_refuses_jt123() {
        listOf("JT0012345678", "JT 2612 3456 7890", "J&T-76123456789", "jt0012345678")
            .forEach { candidate ->
                assertEquals(
                    "$candidate must be valid",
                    CourierTrackingValidator.Outcome.Valid,
                    CourierTrackingValidator.validate("J&T Express", candidate),
                )
            }
        assertTrue(
            CourierTrackingValidator.validate("J&T Express", "JT123")
                is CourierTrackingValidator.Outcome.Malformed,
        )
    }

    @Test
    fun tracking_lalamove_and_grab_formats() {
        listOf("LMB12345678", "LM-9X2K4Q7A", "GRAB123456", "GRAB-PH9Z8K2Q4R")
            .forEach { candidate ->
                assertEquals(
                    "$candidate must be valid",
                    CourierTrackingValidator.Outcome.Valid,
                    CourierTrackingValidator.validate("Lalamove/Grab", candidate),
                )
            }
        assertTrue(
            CourierTrackingValidator.validate("Lalamove/Grab", "LM1")
                is CourierTrackingValidator.Outcome.Malformed,
        )
    }

    @Test
    fun tracking_tiktok_shop_formats() {
        listOf("SPH1234567890", "1234567890", "SPH00123456789012345")
            .forEach { candidate ->
                assertEquals(
                    "$candidate must be valid",
                    CourierTrackingValidator.Outcome.Valid,
                    CourierTrackingValidator.validate("TikTok Shop", candidate),
                )
            }
        assertTrue(
            CourierTrackingValidator.validate("TikTok Shop", "SP12")
                is CourierTrackingValidator.Outcome.Malformed,
        )
    }

    @Test
    fun tracking_pickup_needs_no_number_and_empty_still_blocks_dispatch() {
        assertEquals(
            CourierTrackingValidator.Outcome.NotRequired,
            CourierTrackingValidator.validate("Pickup", ""),
        )
        assertEquals(
            CourierTrackingValidator.Outcome.Missing,
            CourierTrackingValidator.validate("J&T Express", ""),
        )
        // And the ops layer refuses an In Transit without a number, unchanged.
        val result = OrderOps.updateShipping(
            data, SeedData.PACKING_ORDER, "J&T Express", "", ShippingStatus.IN_TRANSIT,
        )
        assertTrue(result is OrderOps.ShipResult.Refused)
        assertEquals(
            OrderOps.ENTER_TRACKING_FIRST,
            (result as OrderOps.ShipResult.Refused).message,
        )
    }

    /** The full dispatch path: a malformed number must NOT move stock. */
    @Test
    fun tracking_malformed_number_blocks_the_dispatch_and_moves_no_stock() {
        val before = data.product("CHW")!!
        val result = OrderOps.updateShipping(
            data, SeedData.PACKING_ORDER, "J&T Express", "JT123",
            ShippingStatus.IN_TRANSIT,
        )
        assertTrue(result is OrderOps.ShipResult.Refused)
        assertEquals(
            "Tracking number does not match J&T Express format.",
            (result as OrderOps.ShipResult.Refused).message,
        )
        val after = (result as OrderOps.ShipResult.Refused).data.product("CHW")!!
        assertEquals(before.physical, after.physical)
        assertEquals(before.reserved, after.reserved)
    }

    // ---------------------------------------------------- IAS R.5 / 2.3 -----

    @Test
    fun audit_seed_is_sealed_and_verifies() {
        val verification = store.verifyAuditChain()
        assertTrue(verification.valid)
        assertEquals(3, verification.entries)
        assertTrue(store.auditBadge().contains("Audit chain verified"))
        assertTrue(store.auditBadge().contains("SHA-256"))
        // Every seeded row carries a hash and links to its predecessor.
        val log = store.snapshot().activityLog
        assertEquals(ActivityLogEntryGenesis, log.first().prevHash)
        for (i in 1 until log.size) {
            assertEquals(log[i - 1].hash, log[i].prevHash)
        }
    }

    @Test
    fun audit_appended_rows_extend_the_chain_and_still_verify() {
        // A real write: save a product, which appends a log row, which is sealed.
        store.saveProduct(
            data.product("CHW")!!.copy(price = 3600),
            changedFields = "price",
        )
        val verification = store.verifyAuditChain()
        assertTrue(verification.valid)
        assertEquals(4, verification.entries)
        val log = store.snapshot().activityLog
        assertEquals("LOG-0004", log.last().logId)
        assertEquals(log[log.size - 2].hash, log.last().prevHash)
        assertTrue(log.last().hash.isNotEmpty())
    }

    @Test
    fun audit_a_tampered_entry_breaks_verification_at_that_row() {
        // Rewrite a HISTORICAL row's detail, the way a quiet editor would.
        val tampered = store.snapshot().activityLog.toMutableList().also { log ->
            log[1] = log[1].copy(detail = "tampered")
        }
        val verification = AuditChain.verify(tampered)
        assertFalse(verification.valid)
        assertEquals("LOG-0002", verification.failedAt)
        assertNotNull(verification.reason)
    }

    @Test
    fun audit_a_deleted_entry_breaks_the_link_of_the_row_after_it() {
        val deleted = store.snapshot().activityLog.filterNot { it.logId == "LOG-0002" }
        val verification = AuditChain.verify(deleted)
        assertFalse(verification.valid)
        assertEquals("LOG-0003", verification.failedAt)
    }

    /**
     * v2.3 HCI audit R.5: the chain must survive a REAL owner session, not just the
     * seed and one catalogue save.
     *
     * During the HCI walkthrough the Edit History badge went red (DangerFill +
     * warning icon) right after sending an invoice. The cause was not the chain but
     * two write paths that swapped the dataset in directly - `placeOrder` and the
     * shared `commit()` helper behind sendInvoice / recordPayment / cancel /
     * combine / pack / adjust - so their appended log rows were never sealed. Both
     * now route through `update`, which seals. This test drives the five mutations
     * the audit named and asserts the badge stays green after every one.
     */
    @Test
    fun audit_chain_stays_verified_after_send_invoice_payment_stock_note_and_password() {
        store.signIn(store.snapshot().owner()!!)
        val seededEntries = store.verifyAuditChain().entries
        assertTrue(store.verifyAuditChain().valid)

        // 1. Send the invoice (F-04's path: a blank fee is a held order).
        store.sendInvoice("ORD-1051", "50.00")
        assertTrue("invoice append must be sealed", store.verifyAuditChain().valid)

        // 2. Record a payment against the same order.
        store.recordPayment("ORD-1051", "70.00", "GCash")
        assertTrue("payment append must be sealed", store.verifyAuditChain().valid)

        // 3. Adjust stock, which writes both a movement and a log row.
        store.adjustStock("CHW", 40, "delivery arrived")
        assertTrue("stock append must be sealed", store.verifyAuditChain().valid)

        // 4. Save a private note on a customer.
        store.setReviewFlag("mika.tan@example.com", "Prefers LBC")
        assertTrue("note append must be sealed", store.verifyAuditChain().valid)

        // 5. Change the password (F-01's path).
        assertNull(store.changePassword("katrina@", "katrina2026", "katrina2026"))
        assertTrue("password append must be sealed", store.verifyAuditChain().valid)

        // Every mutation grew the log, and the badge is the green one.
        val verification = store.verifyAuditChain()
        assertTrue(verification.valid)
        assertNull(verification.failedAt)
        assertTrue(
            "the badge must read as verified, not as a broken chain",
            store.auditBadge().startsWith("Audit chain verified"),
        )
        assertTrue("each mutation must have appended a row", verification.entries > seededEntries)
    }

    /**
     * The same guarantee for a CUSTOMER checkout, which reaches the log through
     * `placeOrder` rather than through `commit()`.
     */
    @Test
    fun audit_chain_stays_verified_after_a_customer_places_an_order() {
        store.signIn(store.snapshot().account("mika.tan@example.com")!!)
        store.addToCart("CHW")
        val placed = store.placeOrder(
            fulfillment = ph.edu.umak.momompurin.data.entity.Fulfillment.SHIP_NOW,
            recipientName = "Mika Tan", mobile = "0917 555 0101",
            province = "Metro Manila", city = "Makati",
            barangay = "Poblacion", addressLine = "12 Mabini St.",
        )
        assertTrue(placed is OrderOps.PlaceResult.Placed)
        val verification = store.verifyAuditChain()
        assertTrue("a customer order must not break the chain", verification.valid)
        assertNull(verification.failedAt)
    }

    /**
     * No log row may ever sit in the store with a blank digest - the structural
     * form of the same rule, checked over every row after a burst of writes.
     */
    @Test
    fun audit_no_appended_row_ever_ships_unsealed() {
        store.signIn(store.snapshot().owner()!!)
        store.sendInvoice("ORD-1051", "")
        store.recordPayment("ORD-1051", "10.00", "Maya")
        store.adjustStock("CHW", 41, "recount")
        val log = store.snapshot().activityLog
        assertTrue(log.isNotEmpty())
        log.forEach { entry ->
            assertTrue("row ${entry.logId} shipped with a blank hash", entry.hash.isNotEmpty())
            assertTrue("row ${entry.logId} shipped with a blank prevHash", entry.prevHash.isNotEmpty())
        }
    }

    // ---------------------------------------------------- offline A.7 -------

    @Test
    fun offline_owner_switch_blocks_owner_writes_but_never_a_customer_checkout() {
        // The owner signs in and flips the test switch. ownerReadOnly requires an
        // OWNER session by design - the flag means nothing without one.
        store.signIn(store.snapshot().owner()!!)
        store.setOfflineMode(true)
        assertTrue(store.ownerReadOnly)
        // An owner write is refused.
        val save = store.recordPayment("ORD-1042", "100.00", "GCash")
        assertTrue(save is OrderOps.PaymentResult.Refused)
        assertEquals(MomoStore.OFFLINE_READ_ONLY, (save as OrderOps.PaymentResult.Refused).message)
        assertTrue(store.ownerReadOnly)

        // A CUSTOMER session on the same store is NOT read-only: the owner's test
        // switch never reaches Customer Mode (A.7 / D.4).
        store.setOfflineMode(false)
        store.signIn(store.snapshot().account("mika.tan@example.com")!!)
        assertFalse(store.ownerReadOnly)
        assertFalse(store.isOffline)
        // A customer checkout blocks only on the DEVICE's connectivity, so give
        // Mika a cart first (the seed ships no carts).
        store.addToCart("CHW")
        assertEquals(1, store.cart().size)
        store.setDeviceOnline(false)
        val placed = store.placeOrder(
            fulfillment = ph.edu.umak.momompurin.data.entity.Fulfillment.SHIP_NOW,
            recipientName = "Mika Tan", mobile = "0917 555 0101",
            province = "Metro Manila", city = "Makati",
            barangay = "Poblacion", addressLine = "12 Mabini St.",
        )
        assertTrue(placed is OrderOps.PlaceResult.Refused)
        assertEquals(
            OrderOps.OFFLINE_CHECKOUT,
            (placed as OrderOps.PlaceResult.Refused).message,
        )
        // Back online, the same checkout goes through.
        store.setDeviceOnline(true)
        val placedNow = store.placeOrder(
            fulfillment = ph.edu.umak.momompurin.data.entity.Fulfillment.SHIP_NOW,
            recipientName = "Mika Tan", mobile = "0917 555 0101",
            province = "Metro Manila", city = "Makati",
            barangay = "Poblacion", addressLine = "12 Mabini St.",
        )
        assertTrue(placedNow is OrderOps.PlaceResult.Placed)
    }

    // ------------------------------------------------------ A.6 removal -----

    @Test
    fun removing_an_unused_option_succeeds_and_a_used_one_is_refused() {
        // "Freebie" is a managed category but no purchasable product carries it as
        // a category... actually STKFR does. Use it as the BLOCKED case.
        val blocked = store.removeProductOption("Freebie", ph.edu.umak.momompurin.data.entity.OptionKind.CATEGORY)
        assertNotNull(blocked)
        assertTrue(blocked!!.contains("STKFR"))
        // Nothing was removed.
        assertTrue(
            store.snapshot().productOptions.any {
                it.kind == ph.edu.umak.momompurin.data.entity.OptionKind.CATEGORY &&
                    it.value == "Freebie"
            },
        )

        // An unused value removes cleanly. Add one first.
        val added = ph.edu.umak.momompurin.data.CatalogueOps.addOption(
            store.snapshot().productOptions,
            ph.edu.umak.momompurin.data.entity.OptionKind.CHARACTER,
            "Kawaii Extra",
        )
        store.saveProductOptions((added as ph.edu.umak.momompurin.data.CatalogueOps.OptionResult.Added).options)
        val failure = store.removeProductOption(
            "Kawaii Extra", ph.edu.umak.momompurin.data.entity.OptionKind.CHARACTER,
        )
        assertNull(failure)
        assertTrue(
            store.snapshot().productOptions.none {
                it.value == "Kawaii Extra"
            },
        )
    }

    // -------------------------------------------------- PH addresses C.1 ----

    @Test
    fun every_seeded_address_triple_exists_in_the_directory() {
        data.accounts.forEach { account ->
            if (account.province.isNotBlank()) {
                assertTrue(
                    "${account.email}: ${account.province}",
                    ph.edu.umak.momompurin.data.PhAddresses.knowsProvince(account.province),
                )
                assertTrue(
                    "${account.email}: ${account.province}/${account.city}",
                    ph.edu.umak.momompurin.data.PhAddresses.knowsCity(account.province, account.city),
                )
                assertTrue(
                    "${account.email}: ${account.province}/${account.city}/${account.barangay}",
                    ph.edu.umak.momompurin.data.PhAddresses.knowsBarangay(
                        account.province, account.city, account.barangay,
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------- revised-doc deltas ---

    /**
     * C.1: the revised spec names "Metro Manila (NCR)" explicitly. The stored key
     * stays "Metro Manila" so every seeded row still resolves; only the label the
     * dropdown prints carries the suffix.
     */
    @Test
    fun c1_metro_manila_prints_the_ncr_suffix_but_stores_the_plain_key() {
        assertEquals(
            "Metro Manila (NCR)",
            ph.edu.umak.momompurin.data.PhAddresses.provinceLabel("Metro Manila"),
        )
        // Other provinces print unchanged.
        assertEquals(
            "Cavite",
            ph.edu.umak.momompurin.data.PhAddresses.provinceLabel("Cavite"),
        )
        // And the key the seed stores is still the one the tree knows.
        assertTrue(ph.edu.umak.momompurin.data.PhAddresses.knowsProvince("Metro Manila"))
        // The tree now carries the complete PSGC list, so NCR holds all 17 LGUs
        // rather than the six the curated map used to offer. The seeded six must
        // still be among them - that is what keeps saved addresses resolving.
        val cities = ph.edu.umak.momompurin.data.PhAddresses.cities("Metro Manila")
        assertEquals("NCR should hold its 17 LGUs", 17, cities.size)
        listOf("Makati", "Mandaluyong", "Manila", "Pasig", "Quezon City", "Taguig")
            .forEach { seeded ->
                assertTrue("seeded city lost from the tree: $seeded", seeded in cities)
            }
    }

    /** C.1: the cascade clears the levels below, which is the reset rule. */
    @Test
    fun c1_cascade_clears_the_levels_below_on_change() {
        val afterProvince = ph.edu.umak.momompurin.data.PhAddresses.clearBelow("Cavite")
        assertEquals(Triple("Cavite", "", ""), afterProvince)

        val afterCity = ph.edu.umak.momompurin.data.PhAddresses.clearBarangay("Cavite", "Bacoor")
        assertEquals(Triple("Cavite", "Bacoor", ""), afterCity)

        // An unselected province offers NO cities, so City stays disabled rather
        // than offering another province's municipalities.
        assertTrue(ph.edu.umak.momompurin.data.PhAddresses.cities("").isEmpty())
        assertTrue(ph.edu.umak.momompurin.data.PhAddresses.barangays("Cavite", "").isEmpty())
    }

    /**
     * E.2: the 14sp floor. DataCaption renders SKU codes, so it must not sit below
     * the criterion's body/metadata minimum.
     */
    @Test
    fun e2_data_caption_meets_the_fourteen_sp_floor() {
        assertTrue(
            "DataCaption is the SKU style and must be >= 14sp",
            ph.edu.umak.momompurin.ui.theme.MomoType.DataCaption.fontSize.value >= 14f,
        )
        assertTrue(
            "LabelChip is the badge style and must be >= 12sp",
            ph.edu.umak.momompurin.ui.theme.MomoType.LabelChip.fontSize.value >= 12f,
        )
        assertTrue(
            "LabelNavigation is the nav-label style and must be >= 12sp",
            ph.edu.umak.momompurin.ui.theme.MomoType.LabelNavigation.fontSize.value >= 12f,
        )
        assertTrue(
            "BodySmall is body copy and must be >= 14sp",
            ph.edu.umak.momompurin.ui.theme.MomoType.BodySmall.fontSize.value >= 14f,
        )
    }

    /**
     * D.2: the bottom bar carries a badge slot, and the customer's Cart tab is the
     * one that fills it. Asserted on the tab list rather than by rendering, because
     * the property under test is "the bar CAN show a count on Cart".
     */
    @Test
    fun d2_the_customer_cart_tab_carries_a_badge_slot() {
        val tabs = ph.edu.umak.momompurin.ui.nav.customerBarTabs
        assertEquals(4, tabs.size)
        val cart = tabs.single { it.route == ph.edu.umak.momompurin.ui.nav.Routes.CART }
        // A fresh bar shows no badge...
        assertEquals(0, cart.badgeCount)
        // ...and the data class can carry one.
        assertEquals(3, cart.copy(badgeCount = 3).badgeCount)
    }

    companion object {
        private const val ActivityLogEntryGenesis =
            ph.edu.umak.momompurin.data.entity.ActivityLogEntry.GENESIS
    }
}
