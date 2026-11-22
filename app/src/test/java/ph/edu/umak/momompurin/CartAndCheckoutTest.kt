package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.CartOps
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.SeedData
import ph.edu.umak.momompurin.data.entity.CartLine
import ph.edu.umak.momompurin.data.entity.Fulfillment
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PaymentStatus

/**
 * A-14 Manage Shopping Cart and A-15 Checkout and Payment.
 *
 * The two defining inversions of the pivot are both asserted here:
 *  - **A-14 c1: the cart reserves nothing.** Every add/quantity test reads the
 *    product back and proves `reserved` did not move.
 *  - **A-15 c8: checkout collects no payment.** The placed order has a zero
 *    amount paid, no shipping fee, and a status of Order Placed.
 */
class CartAndCheckoutTest {

    private val data = SeedData.build()
    private val mika = "mika.tan@example.com"

    // =========================================================================
    // A-14 c1: adding to the cart does not lock or reserve stock
    // =========================================================================

    @Test
    fun a14_adding_to_the_cart_changes_no_product_at_all() {
        val chwBefore = data.product("CHW")!!
        // The cart is a pure value; this is the same call the store makes.
        val cart = CartOps.add(emptyList(), "CHW", chwBefore.available)

        assertEquals(1, cart.size)
        // The product object the cart was built from is byte-identical.
        assertEquals(chwBefore, data.product("CHW"))
        assertEquals(12, data.product("CHW")!!.available)
        assertEquals(20, data.product("CHW")!!.reserved)
    }

    @Test
    fun a14_adding_a_second_time_bumps_the_quantity_rather_than_duplicating_the_row() {
        var cart = CartOps.add(emptyList(), "CHW", 12)
        cart = CartOps.add(cart, "CHW", 12)
        assertEquals(1, cart.size)
        assertEquals(2, cart.first().qty)
    }

    @Test
    fun a14_a_sold_out_product_cannot_be_added() {
        // P5T has 0 available; the card's button is disabled AND the ops refuse.
        assertEquals(emptyList<CartLine>(), CartOps.add(emptyList(), "P5T", 0))
    }

    // =========================================================================
    // A-14 c2: the subtotal counts only ticked items
    // =========================================================================

    @Test
    fun a14_the_subtotal_counts_only_ticked_items() {
        // A-14 c2's GWT: CHW P35.00 ticked, MMBBS P450.00 unticked -> P35.00.
        val cart = listOf(
            CartLine("CHW", qty = 1, selected = true),
            CartLine("MMBBS", qty = 1, selected = false),
        )
        val rows = CartOps.refresh(data.copy(carts = mapOf(mika to cart)), cart)
        assertEquals(3500L, CartOps.subtotal(rows))
    }

    @Test
    fun a14_ticking_both_counts_both() {
        val cart = listOf(
            CartLine("CHW", qty = 1, selected = true),
            CartLine("MMBBS", qty = 1, selected = true),
        )
        val rows = CartOps.refresh(data, cart)
        assertEquals(3500L + 45000L, CartOps.subtotal(rows))
    }

    @Test
    fun a14_checkout_carries_only_the_ticked_items() {
        val cart = listOf(
            CartLine("CHW", qty = 2, selected = true),
            CartLine("MMBBS", qty = 1, selected = false),
        )
        val rows = CartOps.refresh(data, cart)
        val carried = CartOps.selectedLines(rows)
        assertEquals(1, carried.size)
        assertEquals("CHW", carried.first().sku)
    }

    @Test
    fun a14_selection_toggles() {
        val cart = listOf(CartLine("CHW", qty = 1, selected = true))
        val unticked = CartOps.setSelected(cart, "CHW", false)
        assertFalse(unticked.first().selected)
    }

    // =========================================================================
    // A-14 c4 / c6: quantity, capped at the live available count
    // =========================================================================

    @Test
    fun a14_increasing_the_quantity_updates_the_line_and_the_subtotal() {
        // A-14 c3's GWT: quantity 1 -> 2, line total P70.00.
        val cart = listOf(CartLine("CHW", qty = 1, selected = true))
        val change = CartOps.increase(cart, "CHW", available = 12)
        assertEquals(2, change.cart.first().qty)
        assertEquals(null, change.message)

        val rows = CartOps.refresh(data, change.cart)
        assertEquals(7000L, CartOps.subtotal(rows))
    }

    @Test
    fun a14_the_quantity_cannot_exceed_availability() {
        // A-14 c6's GWT: SCWH at quantity 1 with 1 available -> refused.
        val cart = listOf(CartLine("SCWH", qty = 1, selected = true))
        val change = CartOps.increase(cart, "SCWH", available = 1)
        assertEquals(1, change.cart.first().qty)
        assertEquals("Insufficient stock (Only 1 left).", change.message)
    }

    @Test
    fun a14_the_short_stock_message_names_the_remaining_count() {
        assertEquals("Insufficient stock (Only 2 left).", CartOps.insufficientStock(2))
        assertEquals("Insufficient stock (Only 5 left).", CartOps.insufficientStock(5))
    }

    @Test
    fun a14_a_row_over_the_live_stock_carries_the_message() {
        // A-14 c6's GWT: CHW at quantity 3 while only 2 remain. The seeded CHW has
        // 12 available, so the scenario is built explicitly rather than assumed.
        val scarce = data.copy(
            products = data.products.map {
                if (it.sku == "CHW") it.copy(physical = it.reserved + 2) else it
            },
        )
        val cart = listOf(CartLine("CHW", qty = 3, selected = true))
        val row = CartOps.refresh(scarce, cart).first()
        assertEquals(2, row.available)
        assertTrue(row.shortOnStock)
        assertEquals("Insufficient stock (Only 2 left).", row.stockMessage)
        // And it does not contribute to the subtotal while it is over.
        assertFalse(row.contributes)
    }

    @Test
    fun a14_decreasing_never_goes_below_one() {
        val cart = listOf(CartLine("CHW", qty = 1, selected = true))
        assertEquals(1, CartOps.decrease(cart, "CHW").first().qty)
        val two = listOf(CartLine("CHW", qty = 2, selected = true))
        assertEquals(1, CartOps.decrease(two, "CHW").first().qty)
    }

    // =========================================================================
    // A-14 c5: Sold Out refresh
    // =========================================================================

    @Test
    fun a14_a_row_refreshes_to_sold_out_when_the_last_unit_goes() {
        // A-14 c5's GWT: MMBBS in the cart, another customer takes the last unit.
        val soldOutData = data.copy(
            products = data.products.map {
                if (it.sku == "MMBBS") it.copy(physical = 0) else it
            },
        )
        val cart = listOf(CartLine("MMBBS", qty = 1, selected = true))
        val row = CartOps.refresh(soldOutData, cart).first()
        assertTrue(row.soldOut)
        assertEquals("Sold Out", row.stockLabel)
        assertFalse(row.contributes)
    }

    @Test
    fun a14_a_sold_out_row_is_unticked_so_it_cannot_be_carried_to_checkout() {
        val soldOutData = data.copy(
            products = data.products.map {
                if (it.sku == "MMBBS") it.copy(physical = 0) else it
            },
        )
        val cart = listOf(
            CartLine("MMBBS", qty = 1, selected = true),
            CartLine("CHW", qty = 1, selected = true),
        )
        val normalised = CartOps.normaliseSelection(soldOutData, cart)
        assertFalse(normalised.first { it.sku == "MMBBS" }.selected)
        // The healthy row keeps its tick.
        assertTrue(normalised.first { it.sku == "CHW" }.selected)
    }

    @Test
    fun a14_a_deactivated_product_reads_sold_out() {
        // A-03 c7 hides a deactivated product from the catalog; a stale cart row
        // must not still be buyable.
        val hidden = data.copy(
            products = data.products.map {
                if (it.sku == "MMBBS") it.copy(active = false) else it
            },
        )
        val row = CartOps.refresh(hidden, listOf(CartLine("MMBBS", qty = 1, selected = true))).first()
        assertTrue(row.soldOut)
    }

    @Test
    fun a14_the_subtotal_excludes_a_sold_out_row() {
        val soldOutData = data.copy(
            products = data.products.map {
                if (it.sku == "MMBBS") it.copy(physical = 0) else it
            },
        )
        val cart = listOf(
            CartLine("MMBBS", qty = 1, selected = true),
            CartLine("CHW", qty = 1, selected = true),
        )
        val rows = CartOps.refresh(soldOutData, cart)
        assertEquals(3500L, CartOps.subtotal(rows))
    }

    // =========================================================================
    // A-14 c6/c7: remove and empty
    // =========================================================================

    @Test
    fun a14_removing_an_item_updates_the_subtotal() {
        val cart = listOf(
            CartLine("CHW", qty = 1, selected = true),
            CartLine("MMBBS", qty = 1, selected = true),
        )
        val after = CartOps.remove(cart, "CHW")
        assertEquals(1, after.size)
        assertEquals("MMBBS", after.first().sku)
        assertEquals(45000L, CartOps.subtotal(CartOps.refresh(data, after)))
    }

    @Test
    fun a14_removing_the_last_item_empties_the_cart() {
        val cart = listOf(CartLine("CHW", qty = 1, selected = true))
        val after = CartOps.remove(cart, "CHW")
        assertTrue(CartOps.isEmpty(after))
        assertFalse(CartOps.canCheckout(CartOps.refresh(data, after)))
        assertEquals("Your cart is empty.", CartOps.EMPTY_CART)
    }

    @Test
    fun a14_a_cart_of_only_sold_out_rows_cannot_check_out() {
        val soldOutData = data.copy(
            products = data.products.map { it.copy(physical = 0, reserved = 0) },
        )
        val cart = listOf(CartLine("CHW", qty = 1, selected = true))
        assertFalse(CartOps.canCheckout(CartOps.refresh(soldOutData, cart)))
    }

    // =========================================================================
    // A-15: checkout and placement
    // =========================================================================

    private fun checkout(
        lines: List<CartLine> = listOf(CartLine("CHW", qty = 2, selected = true)),
        offline: Boolean = false,
        email: String = mika,
        barangay: String = "Poblacion",
    ) = OrderOps.placeOrder(
        data = data,
        request = OrderOps.CheckoutRequest(
            customerEmail = email,
            lines = lines,
            fulfillment = Fulfillment.SHIP_NOW,
            recipientName = "Mika Tan",
            mobile = "0917 555 0101",
            province = "Metro Manila",
            city = "Makati",
            barangay = barangay,
            addressLine = "12 Mabini St., Unit 4B",
        ),
        offline = offline,
        now = SeedData.NOW_ANCHOR,
    )

    @Test
    fun a15_a_placed_order_adds_no_shipping_fee_and_collects_no_payment() {
        // A-15 c1 + c8: the total is the item subtotal, and nothing is paid.
        val result = checkout()
        assertTrue(result is OrderOps.PlaceResult.Placed)
        val placed = result as OrderOps.PlaceResult.Placed
        val order = placed.data.order(placed.orderId)!!

        assertEquals(7000L, order.itemSubtotal)
        assertEquals(0L, order.shippingFee)
        assertEquals(7000L, order.total)
        assertEquals(0L, order.amountPaid)
        assertEquals(PaymentStatus.UNPAID, order.paymentStatus)
        assertEquals(OrderStage.ORDER_PLACED, order.stage)
    }

    @Test
    fun a15_placement_reserves_exactly_the_ordered_quantity() {
        // A-15 c7's GWT: CHW x2 raises Reserved by 2.
        val before = data.product("CHW")!!
        val placed = checkout() as OrderOps.PlaceResult.Placed
        val after = placed.data.product("CHW")!!

        assertEquals(before.reserved + 2, after.reserved)
        assertEquals(before.available - 2, after.available)
        // Physical does not move on placement - only dispatch removes units.
        assertEquals(before.physical, after.physical)
    }

    @Test
    fun a15_placement_clears_only_the_checked_out_rows() {
        // A-15 c7's GWT: CHW ordered while MMBBS was unticked -> MMBBS stays.
        val cart = listOf(
            CartLine("CHW", qty = 2, selected = true),
            CartLine("MMBBS", qty = 1, selected = false),
        )
        val withCart = data.copy(carts = mapOf(mika to cart))
        val result = OrderOps.placeOrder(
            data = withCart,
            request = OrderOps.CheckoutRequest(
                customerEmail = mika,
                lines = CartOps.selectedLines(CartOps.refresh(withCart, cart)),
                fulfillment = Fulfillment.SHIP_NOW,
                recipientName = "Mika Tan",
                mobile = "0917 555 0101",
                province = "Metro Manila",
                city = "Makati",
                barangay = "Poblacion",
                addressLine = "12 Mabini St.",
            ),
            offline = false,
            now = SeedData.NOW_ANCHOR,
        ) as OrderOps.PlaceResult.Placed

        val remaining = result.data.cart(mika)
        assertEquals(1, remaining.size)
        assertEquals("MMBBS", remaining.first().sku)
    }

    @Test
    fun a15_overselling_is_refused_with_the_sku_in_the_message() {
        // A-15 c6's GWT: SCWH x2 while only 1 is available.
        val result = checkout(lines = listOf(CartLine("SCWH", qty = 2, selected = true)))
        assertTrue(result is OrderOps.PlaceResult.Refused)
        assertEquals(
            "Not enough stock for SCWH.",
            (result as OrderOps.PlaceResult.Refused).message,
        )
        // And nothing was written. (Note the argument ORDER: expected first.
        // The previous form `assertEquals(actual, expected)` passed no matter
        // what the seed held, which is how it survived the reserved-count drift.)
        assertEquals(3, data.product("SCWH")!!.reserved)
        assertEquals(3, result.data.product("SCWH")!!.reserved)
        assertEquals(13, result.data.orders.size)
    }

    @Test
    fun a15_checkout_while_offline_is_refused() {
        val result = checkout(offline = true)
        assertEquals(
            "Connect to the internet to place your order.",
            (result as OrderOps.PlaceResult.Refused).message,
        )
        assertEquals(13, result.data.orders.size)
    }

    @Test
    fun a15_a_blank_delivery_field_is_refused() {
        val result = checkout(barangay = "")
        assertEquals(
            OrderOps.INCOMPLETE_DELIVERY,
            (result as OrderOps.PlaceResult.Refused).message,
        )
        assertEquals("Complete the delivery details.", OrderOps.INCOMPLETE_DELIVERY)
    }

    @Test
    fun a15_the_new_order_lands_in_the_owners_incoming_queue() {
        // A-15 c7's GWT: the owner's Incoming Orders lists it as Order Placed.
        val placed = checkout() as OrderOps.PlaceResult.Placed
        val incoming = placed.data.pendingOrders().map { it.orderId }
        assertTrue("the new order must be in the incoming queue", placed.orderId in incoming)
    }

    @Test
    fun a15_hold_for_combined_shipping_creates_a_held_order() {
        // A-15 c3: the choice is recorded on the order.
        val result = OrderOps.placeOrder(
            data = data,
            request = OrderOps.CheckoutRequest(
                customerEmail = mika,
                lines = listOf(CartLine("CHW", qty = 1, selected = true)),
                fulfillment = Fulfillment.HOLD_FOR_COMBINED,
                recipientName = "Mika Tan",
                mobile = "0917 555 0101",
                province = "Metro Manila",
                city = "Makati",
                barangay = "Poblacion",
                addressLine = "12 Mabini St.",
            ),
            offline = false,
            now = SeedData.NOW_ANCHOR,
        ) as OrderOps.PlaceResult.Placed

        val order = result.data.order(result.orderId)!!
        assertEquals(Fulfillment.HOLD_FOR_COMBINED, order.fulfillment)
        assertTrue(order.isHeld)
        // It is NOT in A-06's list yet: A-06 c1 defines held orders as the PAID
        // ones, and this one is still Order Placed awaiting its invoice.
        assertFalse(result.data.heldOrders().any { it.orderId == result.orderId })

        // Once paid it appears, which is the state A-06 actually combines.
        val paid = OrderOps.recordPayment(
            result.data, result.orderId, "35.00", "GCash", SeedData.NOW_ANCHOR,
        ) as OrderOps.PaymentResult.Recorded
        assertTrue(paid.data.heldOrders().any { it.orderId == result.orderId })
    }

    @Test
    fun a15_the_next_order_number_is_past_every_existing_order() {
        assertEquals("ORD-1052", OrderOps.nextOrderId(data))
    }

    @Test
    fun a15_placement_is_atomic_across_two_sequential_attempts() {
        // "Atomic" means the check and the reservation see one snapshot: a second
        // checkout against the UPDATED data sees the reduced availability.
        val first = checkout(lines = listOf(CartLine("SCWH", qty = 1, selected = true)))
            as OrderOps.PlaceResult.Placed
        val second = OrderOps.placeOrder(
            data = first.data,
            request = OrderOps.CheckoutRequest(
                customerEmail = "pia.santos@example.com",
                lines = listOf(CartLine("SCWH", qty = 1, selected = true)),
                fulfillment = Fulfillment.SHIP_NOW,
                recipientName = "Pia Santos",
                mobile = "0917 555 0142",
                province = "Metro Manila",
                city = "Makati",
                barangay = "Poblacion",
                addressLine = "12A Sampaguita St",
            ),
            offline = false,
            now = SeedData.NOW_ANCHOR,
        )
        assertTrue(second is OrderOps.PlaceResult.Refused)
        assertEquals(
            "Not enough stock for SCWH.",
            (second as OrderOps.PlaceResult.Refused).message,
        )
    }

    @Test
    fun a15_a_variant_carries_its_variant_name_onto_the_order_line() {
        // A-14 c11's GWT: MPBOW-LV Lavender reaches the cart with its name shown.
        val result = checkout(lines = listOf(CartLine("MPBOW-LV", qty = 1, selected = true)))
            as OrderOps.PlaceResult.Placed
        val line = result.data.linesFor(result.orderId).first()
        assertEquals("MPBOW-LV", line.sku)
        assertEquals("Lavender", line.variantName)
        assertEquals(22000L, line.unitPrice)
    }
}
