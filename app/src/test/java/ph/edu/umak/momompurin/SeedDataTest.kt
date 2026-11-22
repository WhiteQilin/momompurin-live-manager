package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.CatalogueOps
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.SeedData
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.data.entity.StockStatus
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * Every pinned figure `USER STORY-v4.19.11.2026.xlsx` quotes.
 *
 * These are not "sensible defaults" - each assertion names the criterion that
 * states the number, so a seed edited to a self-consistent but different value
 * fails the build instead of quietly rewriting the tester's expected result.
 */
class SeedDataTest {

    private val data = SeedData.build()

    // -------------------------------------------------------------------------
    // A-03: products and stock
    // -------------------------------------------------------------------------

    @Test
    fun chw_carries_the_pinned_stock_and_price() {
        val chw = data.product("CHW")!!
        assertEquals("Chiikawa Wipes", chw.name)
        assertEquals(MomoMoney.parse("35.00"), chw.price)
        // A-03 c5 / A-09 c5: 12 available is the figure the criteria quote.
        // On hand and Reserved are DERIVED from the seeded order lines
        // (SeedData.reservedFromOrders), so the two sides can never disagree.
        assertEquals(12, chw.available)
        assertEquals(20, chw.reserved)
        assertEquals(32, chw.physical)
    }

    /**
     * The invariant the old hand-typed seed violated.
     *
     * Every product's `reserved` must equal the exact sum of its lines on orders
     * that still hold stock. Seeding CHW at `reserved = 4` against 20 held units
     * made cancelling ORD-1045 (CHW x8) clamp at zero and silently under-report
     * stock - a defect that looks identical to a successful release.
     */
    @Test
    fun reserved_equals_the_holding_order_lines_for_every_sku() {
        val expected = SeedData.reservedFromOrders()
        for (product in data.products) {
            assertEquals(
                "reserved for ${product.sku} must equal its holding order lines",
                expected[product.sku] ?: 0,
                product.reserved,
            )
        }
    }

    /** And the arithmetic a customer sees is the derived one, for every product. */
    @Test
    fun available_is_physical_minus_reserved_for_every_product() {
        for (product in data.products) {
            assertEquals(
                "${product.sku}: physical - reserved",
                product.physical - product.reserved,
                product.available,
            )
            assertTrue(
                "${product.sku}: reserved may not exceed physical",
                product.reserved <= product.physical,
            )
        }
    }

    /**
     * A-04 c7's release has to be exact, not clamped: cancelling the overdue
     * ORD-1045 gives back its own quantities and the figure still reconciles.
     */
    @Test
    fun cancelling_the_overdue_order_releases_its_stock_without_clamping() {
        val chwBefore = data.product("CHW")!!
        val hkeyeBefore = data.product("HKEYE")!!
        val result = OrderOps.cancel(
            data, SeedData.OVERDUE_CANCEL_RESTORE_ORDER,
            CancelledBy.OWNER, "Overdue",
        )
        val after = (result as OrderOps.CancelResult.Cancelled).data
        val chwAfter = after.product("CHW")!!
        val hkeyeAfter = after.product("HKEYE")!!
        // ORD-1045 holds CHW x8 + HKEYE x4.
        assertEquals(chwBefore.reserved - 8, chwAfter.reserved)
        assertEquals(hkeyeBefore.reserved - 4, hkeyeAfter.reserved)
        // Physical is untouched by a cancel, so Available rises by the released qty.
        assertEquals(chwBefore.physical, chwAfter.physical)
        assertEquals(chwBefore.available + 8, chwAfter.available)
        assertEquals(hkeyeBefore.available + 4, hkeyeAfter.available)
    }

    /**
     * A-07 c9's dispatch invariant: both sides fall, so Available never
     * double-subtracts. ORD-1040 ships CHW x2, HKEYE x2 and MPBOW-LV x2.
     */
    @Test
    fun dispatching_an_order_drops_both_sides_and_keeps_available_steady() {
        val before = data.product("CHW")!!
        // Phase 2.2: the tracking number must pass the courier's format check
        // before a dispatch moves stock, so this uses a valid 13-digit J&T number
        // rather than the placeholder the old test typed.
        val result = OrderOps.updateShipping(
            data, SeedData.PACKING_ORDER, "J&T Express", "JT-0012345678901",
            ShippingStatus.IN_TRANSIT,
        )
        val after = (result as OrderOps.ShipResult.Updated).data.product("CHW")!!
        assertEquals(before.physical - 2, after.physical)
        assertEquals(before.reserved - 2, after.reserved)
        // The whole point of A-07 c9: Available is unchanged by a dispatch.
        assertEquals(before.available, after.available)
    }

    /** Restoring after a cancel re-reserves exactly, so the round trip is lossless. */
    @Test
    fun cancel_then_restore_is_a_lossless_round_trip() {
        val before = data.product("CHW")!!
        val cancelled = OrderOps.cancel(
            data, SeedData.OVERDUE_CANCEL_RESTORE_ORDER, CancelledBy.OWNER, "Overdue",
        )
        val mid = (cancelled as OrderOps.CancelResult.Cancelled).data
        val restored = OrderOps.restore(mid, SeedData.OVERDUE_CANCEL_RESTORE_ORDER)
        val after = (restored as OrderOps.CancelResult.Cancelled).data.product("CHW")!!
        assertEquals(before.physical, after.physical)
        assertEquals(before.reserved, after.reserved)
        assertEquals(before.available, after.available)
    }

    @Test
    fun chw_pack_is_380_pesos_over_18_pieces() {
        val chw = data.product("CHW")!!
        assertEquals(MomoMoney.parse("380.00"), chw.packCost)
        assertEquals(18, chw.packQty)
        assertEquals("XS", chw.packSize)
    }

    @Test
    fun chw_price_chain_reproduces_the_workbook_figures() {
        // A-03 c2's GWT: P380.00 / 18 = P21.11 unit cost, 3% freebie P0.63,
        // Extra Small packaging P5.00, SRP P34.77 at 30% markup.
        val chw = data.product("CHW")!!
        val preview = CatalogueOps.pricePreview(chw.packCost, chw.packQty, chw.packSize, 30.0)
        assertEquals("unit cost", MomoMoney.parse("21.11"), preview.unitCost)
        assertEquals("3% freebie", MomoMoney.parse("0.63"), preview.freebie)
        assertEquals("packaging", MomoMoney.parse("5.00"), preview.packagingCents)
        assertEquals("suggested retail", MomoMoney.parse("34.77"), preview.suggestedPrice)
    }

    @Test
    fun scwh_is_electronic_low_stock_and_priced_at_600() {
        val scwh = data.product("SCWH")!!
        assertTrue("SCWH drives the A-06 electronic advisory", scwh.isElectronic)
        assertEquals(MomoMoney.parse("600.00"), scwh.price)
        // SCWH is held by ORD-1042 / ORD-1039 / ORD-1038 = 3 reserved; the
        // available target is the 1 unit A-13 c5 needs for its Low Stock badge.
        assertEquals(3, scwh.reserved)
        assertEquals(4, scwh.physical)
        assertEquals(1, scwh.available)
        // A-13 c5: exactly one unit left reads "Low Stock".
        assertEquals(StockStatus.LOW_STOCK, scwh.stockStatus)
    }

    @Test
    fun the_stock_badges_partition_at_the_pinned_counts() {
        // A-13 c5's three labels, from the three pinned counts.
        assertEquals(StockStatus.IN_STOCK, data.product("CHW")!!.stockStatus)   // 12 available
        assertEquals(StockStatus.LOW_STOCK, data.product("SCWH")!!.stockStatus) // 1 available
        assertEquals(StockStatus.OUT_OF_STOCK, data.product("P5T")!!.stockStatus) // 0 available
    }

    @Test
    fun mmbbs_is_the_last_unit_sold_walk() {
        val mmbbs = data.product("MMBBS")!!
        assertEquals(MomoMoney.parse("450.00"), mmbbs.price)
        assertEquals(1, mmbbs.available)
    }

    @Test
    fun p5t_is_out_of_stock_at_750() {
        val p5t = data.product("P5T")!!
        assertEquals("Pompompurin Tumbler", p5t.name)
        assertEquals(MomoMoney.parse("750.00"), p5t.price)
        assertEquals(0, p5t.available)
        // A-13 c5: Add to cart is disabled, which follows from being unpurchasable.
        assertTrue(p5t.isPurchasable) // active, but zero available
        assertEquals(0, p5t.available)
    }

    @Test
    fun mpbow_has_two_variants_at_220() {
        // A-13 c4 / A-03 c4's GWT.
        val parent = data.product("MPBOW")!!
        assertTrue(parent.isParent)
        assertEquals(0, parent.price)

        val variants = data.variantsOf("MPBOW")
        assertEquals(2, variants.size)
        val pk = data.product("MPBOW-PK")!!
        val lv = data.product("MPBOW-LV")!!
        assertEquals(MomoMoney.parse("220.00"), pk.price)
        assertEquals(MomoMoney.parse("220.00"), lv.price)
        assertEquals("Pink", OrderOps.variantName(pk))
        assertEquals("Lavender", OrderOps.variantName(lv))
    }

    @Test
    fun cmeye_is_absent_so_a03_can_add_it() {
        // A-03 c3's "Save a product" walk adds CMEYE; a seeded CMEYE would make
        // that criterion unperformable.
        assertNull(data.product("CMEYE"))
    }

    @Test
    fun available_is_always_physical_minus_reserved() {
        data.products.forEach { product ->
            assertEquals(
                "${product.sku}: available must be physical - reserved",
                (product.physical - product.reserved).coerceAtLeast(0),
                product.available,
            )
        }
    }

    // -------------------------------------------------------------------------
    // A-02 / A-04 / A-05: the orders
    // -------------------------------------------------------------------------

    @Test
    fun ord1042_carries_the_canonical_figures() {
        val order = data.order("ORD-1042")!!
        assertEquals("pia.santos@example.com", order.customerEmail)
        // A-05 c1: Total P880.00, Paid P600.00, Balance P280.00.
        assertEquals(MomoMoney.parse("880.00"), order.total)
        assertEquals(MomoMoney.parse("600.00"), order.amountPaid)
        assertEquals(MomoMoney.parse("280.00"), order.balance)
        assertEquals(PaymentStatus.PARTIALLY_PAID, order.paymentStatus)
        // UAT fix (item 2): a Partially Paid order may not sit in To Pack - A-07
        // packs only fully-paid orders. The canonical payment figures above are
        // unchanged; only the queue the order waits in moved.
        assertEquals(OrderStage.WAITING_PAYMENT, order.stage)
        assertEquals("MP-1042", order.invoiceNo)
    }

    @Test
    fun ord1042_lines_sum_to_its_item_subtotal() {
        val reported = data.order("ORD-1042")!!
        val lineSum = data.linesFor("ORD-1042").sumOf { it.lineTotal }
        assertEquals(reported.itemSubtotal, lineSum)
        // 760.00 goods + 120.00 shipping = 880.00.
        assertEquals(MomoMoney.parse("760.00"), lineSum)
        assertEquals(MomoMoney.parse("120.00"), reported.shippingFee)
    }

    @Test
    fun every_order_lines_sum_to_its_subtotal() {
        // A drift here would make an invoice disagree with the list it sits under.
        data.orders.forEach { order ->
            val lineSum = data.linesFor(order.orderId).sumOf { it.lineTotal }
            assertEquals(
                "${order.orderId}: lines must sum to itemSubtotal",
                order.itemSubtotal,
                lineSum,
            )
        }
    }

    @Test
    fun todays_sales_and_todays_order_count_agree() {
        // A-02 c1-c2: the headline figure and the sub-line describe the SAME set
        // of orders, so a clock mistake that pushed an order over midnight would
        // show here rather than only in a screenshot.
        val sales = data.todaysSales(SeedData.NOW_ANCHOR)
        val count = data.todaysOrderCount(SeedData.NOW_ANCHOR)
        assertEquals("2 orders placed today", 2, count)
        // ORD-1051 P70.00 + ORD-1050 P450.00.
        assertEquals(MomoMoney.parse("520.00"), sales)
    }

    @Test
    fun the_customer_catalog_shows_no_freebie_and_no_deactivated_row() {
        // A-13 c1: the catalogue is sellable merchandise. A P0.00 freebie has no
        // price and no stock, so it would render a row the customer can never buy.
        val skus = data.catalogProducts().map { it.sku }
        assertFalse("the freebie must not be sold", skus.contains("STKFR"))
        assertTrue("real merchandise is listed", skus.contains("CHW"))
        assertTrue("variants are listed individually", skus.contains("MPBOW-PK"))
        assertFalse("a parent is reached through its variants", skus.contains("MPBOW"))
        // And nothing in the catalogue has a zero price.
        assertTrue(data.catalogProducts().all { it.price > 0 })
    }

    @Test
    fun the_shop_lists_one_top_level_mpbow_card_without_child_skus() {
        val shopProducts = data.shopProducts()
        val mpbowCards = shopProducts.filter { it.sku == "MPBOW" }

        assertEquals(1, mpbowCards.size)
        assertTrue("the shop contains only top-level products", shopProducts.all { it.parentSku == null })
        assertFalse("the Pink child SKU is not a shop card", shopProducts.any { it.sku == "MPBOW-PK" })
        assertFalse("the Lavender child SKU is not a shop card", shopProducts.any { it.sku == "MPBOW-LV" })
        assertNull(mpbowCards.single().parentSku)
        assertTrue(mpbowCards.single().isParent)
    }

    @Test
    fun child_search_and_filters_return_the_mpbow_parent_card() {
        val byChildSku = data.searchCatalog("MPBOW-PK")
        val byCategory = data.searchCatalog("", category = "Pouch")
        val byCharacter = data.searchCatalog("", character = "My Melody")

        assertTrue(byChildSku.any { it.sku == "MPBOW" })
        assertTrue(byCategory.any { it.sku == "MPBOW" })
        assertTrue(byCharacter.any { it.sku == "MPBOW" })
        listOf(byChildSku, byCategory, byCharacter).forEach { results ->
            assertTrue("catalogue search results stay top-level", results.all { it.parentSku == null })
            assertFalse("catalogue search does not return child SKUs", results.any {
                it.sku == "MPBOW-PK" || it.sku == "MPBOW-LV"
            })
        }
    }

    @Test
    fun mpbow_parent_card_quotes_variant_price_and_aggregates_stock() {
        val parent = data.product("MPBOW")!!
        val summary = data.stockSummary(parent)
        val status = when {
            summary.available <= 0 -> StockStatus.OUT_OF_STOCK
            summary.available <= MomoData.LOW_STOCK_LEVEL -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }

        assertEquals(MomoMoney.parse("220.00"), data.parentPriceFrom("MPBOW"))
        assertEquals(2, data.parentVariantCount("MPBOW"))
        assertEquals(10, summary.physical)
        assertEquals(3, summary.reserved)
        assertEquals(7, summary.available)
        assertEquals(StockStatus.IN_STOCK, status)
        assertEquals(4, data.product("MPBOW-PK")!!.available)
        assertEquals(3, data.product("MPBOW-LV")!!.available)
    }

    @Test
    fun a_freebie_row_is_never_a_low_stock_alert() {
        // A P0.00 row cannot be restocked to sell, so listing it would be a
        // permanent alert the owner can never clear.
        val alerts = data.lowStockProducts().map { it.sku }
        assertFalse("STKFR is a freebie, not an alert", alerts.contains("STKFR"))
        // The real low-stock rows are still reported.
        assertTrue(alerts.contains("SCWH"))
        assertTrue(alerts.contains("MMBBS"))
    }

    @Test
    fun the_dashboard_tiles_count_what_a02_names() {
        // A-02 c1-c2's four tiles against the v6 seed: every epic has the order
        // it needs, so the counts follow from the tables rather than from a
        // hand-written expectation.
        //
        //   Pending     = stage Order Placed                 -> 4
        //   Unpaid      = balance > 0 past Incoming          -> 3 (1042, 1043, 1045)
        //   Held        = Hold For Combined + Paid           -> 4 (1036, 1037, 1038, 1039)
        //   To pack     = stage To Pack, held excluded       -> 2 (1052, 1040)
        assertEquals("Pending orders", 4, data.pendingOrders().size)
        assertEquals("Unpaid or overdue", 3, data.unpaidOrOverdue().size)
        assertEquals("Held orders", 4, data.heldOrders().size)
        // To pack excludes held orders: a held order waits for A-06's Combine, not
        // for the packing table.
        //
        // UAT fix (item 2): the count is 2, not 3. 1042 and 1043 are Partially
        // Paid, and A-07 refuses to pack an order that is not fully paid, so they
        // no longer occupy this queue. ORD-1052 is the replacement row that keeps
        // two genuinely packable orders on a fresh install.
        assertEquals("To pack", 2, data.toPackOrders().size)
    }

    @Test
    fun the_pending_queue_is_exactly_the_four_order_placed_orders() {
        assertEquals(
            SeedData.PENDING_ORDER_IDS.toSet(),
            data.pendingOrders().map { it.orderId }.toSet(),
        )
    }

    @Test
    fun unpaid_or_overdue_is_the_three_orders_still_owing_money() {
        // ORD-1042 (partially paid, Waiting for Payment), ORD-1043 (partially
        // paid, Packed) and ORD-1045 (overdue). ORD-1043 is what makes A-04's
        // "Packed orders cannot be cancelled." reachable without walking A-07.
        assertEquals(
            setOf("ORD-1042", "ORD-1043", "ORD-1045"),
            data.unpaidOrOverdue().map { it.orderId }.toSet(),
        )
    }

    @Test
    fun ord1045_is_overdue_on_the_24_hour_window() {
        val order = data.order("ORD-1045")!!
        assertTrue(
            "ORD-1045 must read overdue against the fixed anchor",
            order.isOverdue(SeedData.NOW_ANCHOR),
        )
        assertEquals(MomoMoney.parse("400.00"), order.total)
    }

    @Test
    fun no_order_is_cancelled_or_completed_in_the_seed() {
        // A-04 c2: the app never cancels by itself, so nothing starts cancelled -
        // which is what lets the Cancel walk produce a first cancellation.
        assertTrue(data.orders.none { it.stage == OrderStage.CANCELLED })
    }

    // -------------------------------------------------------------------------
    // A-06: held orders and the advisory
    // -------------------------------------------------------------------------

    @Test
    fun the_four_held_orders_are_paid_and_held_for_combined_shipping() {
        // A-06 needs all three advisory states visible on one screen:
        // ORD-1038 (7d, electronic -> advisory), ORD-1039 (3d, electronic -> none),
        // ORD-1036 (10d, non-electronic -> none). ORD-1037 is the combine partner.
        val held = data.heldOrders()
        assertEquals(
            setOf("ORD-1039", "ORD-1038", "ORD-1036", "ORD-1037"),
            held.map { it.orderId }.toSet(),
        )
        held.forEach {
            assertEquals(it.orderId, PaymentStatus.PAID, it.paymentStatus)
            assertTrue(it.orderId, it.isHeld)
        }
    }

    @Test
    fun ord1038_is_electronic_and_held_exactly_seven_days() {
        val order = data.order("ORD-1038")!!
        assertEquals(7, order.heldDays(SeedData.NOW_ANCHOR))
        assertTrue(
            "A-06 c2: electronic + 7 days shows the advisory",
            data.showsWeekAdvisory(order, SeedData.NOW_ANCHOR),
        )
    }

    @Test
    fun ord1036_is_held_ten_days_and_shows_no_advisory() {
        val order = data.order("ORD-1036")!!
        assertEquals(10, order.heldDays(SeedData.NOW_ANCHOR))
        // A-06 c3: no electronic item, so the day count is irrelevant.
        assertTrue(
            "A-06 c3: a non-electronic hold shows no advisory",
            !data.showsWeekAdvisory(order, SeedData.NOW_ANCHOR),
        )
    }

    @Test
    fun mika_held_total_is_760() {
        // A-16 c6: ORD-1036 P400.00 + ORD-1037 P360.00.
        val mikaHeld = data.heldOrdersFor("mika.tan@example.com").sumOf { it.total }
        assertEquals(SeedData.MIKA_HELD_TOTAL, mikaHeld)
        assertEquals(MomoMoney.parse("760.00"), mikaHeld)
    }

    @Test
    fun the_combinable_pair_belongs_to_one_customer() {
        val pair = SeedData.COMBINABLE_ORDER_IDS.map { data.order(it)!! }
        assertEquals(1, pair.map { it.customerEmail }.distinct().size)
        assertEquals(MomoMoney.parse("760.00"), pair.sumOf { it.total })
    }

    // -------------------------------------------------------------------------
    // A-07 / A-16: ORD-1040
    // -------------------------------------------------------------------------

    @Test
    fun ord1040_is_paid_to_pack_with_three_items() {
        val order = data.order("ORD-1040")!!
        assertEquals("jessa.reyes@example.com", order.customerEmail)
        assertEquals(MomoMoney.parse("570.00"), order.total)
        assertEquals(3, data.linesFor("ORD-1040").size)
        assertEquals(PaymentStatus.PAID, order.paymentStatus)
        assertEquals(OrderStage.TO_PACK, order.stage)
        assertEquals(PackingStatus.NOT_PACKED, order.packingStatus)
    }

    @Test
    fun ord1040_line_quantities_are_two_two_and_two() {
        val lines = data.linesFor("ORD-1040")
        assertEquals(listOf(2, 2, 2), lines.map { it.qty })
        // A-07 c2's GWT quotes "CHW x 2" as the first item.
        assertEquals("CHW", lines.first().sku)
    }

    // -------------------------------------------------------------------------
    // A-01 / A-11 / A-12 / A-17: the accounts
    // -------------------------------------------------------------------------

    @Test
    fun the_owner_account_is_the_one_the_criteria_name() {
        val owner = data.owner()!!
        assertEquals("katrinajj254@gmail.com", owner.email)
        assertEquals(ph.edu.umak.momompurin.data.entity.Role.OWNER, owner.role)
    }

    @Test
    fun every_seeded_customer_account_is_present() {
        val names = data.customers().map { it.name }.toSet()
        // The five the A-01/A-08 criteria name, plus the two whose orders appear in
        // the incoming queue. Without Lea Castro and Marie Chu, ORD-1049 and
        // ORD-1048 rendered their raw email where a customer name belongs.
        listOf(
            "Mika Tan", "Pia Santos", "Anne Garcia", "Jessa Reyes", "Bea Cruz",
            "Lea Castro", "Marie Chu",
        ).forEach { name ->
            assertTrue("$name must have an account", names.contains(name))
        }
    }

    @Test
    fun every_order_resolves_to_a_named_customer() {
        // A name falling back to the email address is the visible symptom of a
        // missing account, so the seed is asserted to have none.
        data.orders.forEach { order ->
            val account = data.account(order.customerEmail)
            assertNotNull("${order.orderId} has no customer account", account)
            assertTrue(
                "${order.orderId}'s customer must have a real name",
                account!!.name.isNotBlank() && !account.name.contains("@"),
            )
        }
    }

    @Test
    fun mika_carries_her_seeded_contact_and_address() {
        val mika = data.account("mika.tan@example.com")!!
        assertEquals("Mika Tan", mika.name)
        assertEquals("@mikat", mika.handle)
        // Stored as the digits the customer typed (A-11 c4 normalises on input).
        assertEquals("09175550101", mika.mobile.filter { it.isDigit() })
        assertEquals("Metro Manila", mika.province)
        assertEquals("Makati", mika.city)
        assertEquals("Poblacion", mika.barangay)
    }

    @Test
    fun no_account_seeds_a_review_flag() {
        // A-08 c4's "Add note" walk must start from nothing on a fresh install.
        assertTrue(data.accounts.all { it.reviewFlag == null })
    }

    @Test
    fun a_fresh_install_starts_with_empty_carts() {
        // A-14 c7: the empty state is reachable on first launch.
        assertTrue(data.carts.isEmpty())
        assertTrue(data.cart("mika.tan@example.com").isEmpty())
    }

    @Test
    fun the_settings_carry_the_rates_the_product_form_reads() {
        assertEquals("30", data.setting("profit_markup"))
        assertEquals("3", data.setting("freebie_rate"))
        assertEquals("Momompurin", data.setting("business_name"))
    }

    @Test
    fun stock_adjustments_carry_one_reason_and_one_blank() {
        // A-03 c6: the reason is optional in BOTH directions.
        val withReason = data.stockAdjustments.filter { it.reason != null }
        val withoutReason = data.stockAdjustments.filter { it.reason == null }
        assertTrue("at least one adjustment records a reason", withReason.isNotEmpty())
        assertTrue("at least one adjustment has no reason", withoutReason.isNotEmpty())
    }

    @Test
    fun the_edit_history_starts_with_real_entries() {
        // A-10 c5: the history is not an empty fiction on first launch.
        assertTrue(data.activityLog.isNotEmpty())
        assertTrue(data.activityLog.all { it.actor == SeedData.OWNER_EMAIL })
        assertNotNull(data.activityLog.firstOrNull { it.action.contains("Changed") || it.action.startsWith("Adjusted") })
    }
}
