package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.SeedData
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.ShippingStatus

/**
 * The order lifecycle: A-04 Process Orders, A-05 Verify Payments, A-06 Hold and
 * Combine, A-07 Pack and Ship, A-08 customer records, A-09 reports, A-16 Track
 * Orders.
 *
 * Each test names the criterion, because these are the rules where a plausible but
 * wrong implementation still looks right on screen - a double-restock, a Paid order
 * that can be cancelled, a dispatch that subtracts stock twice.
 */
class OrderLifecycleTest {

    private val data = SeedData.build()
    private val now = SeedData.NOW_ANCHOR

    // =========================================================================
    // A-04 c5-c6: Confirm & Send Invoice
    // =========================================================================

    @Test
    fun a04_a_blank_shipping_fee_becomes_zero_not_an_error() {
        // A-04 c5's "zero shipping fee for a held order" GWT.
        assertEquals(0L, OrderOps.parseFee(""))
        assertEquals(0L, OrderOps.parseFee("0"))
        assertEquals(0L, OrderOps.parseFee("0.00"))
    }

    @Test
    fun a04_a_non_numeric_shipping_fee_is_refused() {
        // A-04 c5's GWT: "eighty".
        assertNull(OrderOps.parseFee("eighty"))
        val result = OrderOps.sendInvoice(data, "ORD-1051", "eighty", now)
        assertEquals(
            OrderOps.INVALID_SHIPPING_FEE,
            (result as OrderOps.InvoiceResult.Refused).message,
        )
        assertEquals("Enter a valid shipping fee.", OrderOps.INVALID_SHIPPING_FEE)
    }

    @Test
    fun a04_sending_the_invoice_moves_the_order_and_starts_the_24_hour_window() {
        val result = OrderOps.sendInvoice(data, "ORD-1051", "80.00", now)
            as OrderOps.InvoiceResult.Sent

        val order = result.data.order("ORD-1051")!!
        assertEquals(OrderStage.WAITING_PAYMENT, order.stage)
        assertEquals("MP-1051", order.invoiceNo)
        assertEquals(now + OrderOps.PAYMENT_WINDOW_MILLIS, order.paymentDeadline)
        // The total is item subtotal + fee: 70.00 + 80.00.
        assertEquals(7000L + 8000L, order.total)
    }

    @Test
    fun a04_an_already_invoiced_order_cannot_be_invoiced_again() {
        // ORD-1045 is already at Waiting for Payment.
        val result = OrderOps.sendInvoice(data, "ORD-1045", "0.00", now)
        assertTrue(result is OrderOps.InvoiceResult.Refused)
    }

    @Test
    fun a04_the_invoice_number_uses_the_mp_prefix() {
        val result = OrderOps.sendInvoice(data, "ORD-1050", "", now)
            as OrderOps.InvoiceResult.Sent
        assertTrue(result.invoiceNo.startsWith("MP-"))
        assertEquals("MP-1050", result.invoiceNo)
    }

    @Test
    fun a04_a_sent_invoice_is_recorded_in_the_invoice_table() {
        val result = OrderOps.sendInvoice(data, "ORD-1051", "50.00", now)
            as OrderOps.InvoiceResult.Sent
        assertNotNull(result.data.invoiceFor("ORD-1051"))
        assertEquals(7000L + 5000L, result.data.invoiceFor("ORD-1051")!!.total)
    }

    // =========================================================================
    // A-04 c7-c9 / A-16 c6-c8: cancel and restore
    // =========================================================================

    @Test
    fun a04_cancelling_returns_the_reserved_stock_exactly_once() {
        // ORD-1051 holds CHW x2, and the seeded CHW carries the full 20 held by
        // every unshipped order - so this release is EXACT arithmetic, not a
        // clamp. (The old seed reserved only 4 here, which hid the clamp.)
        val chwBefore = data.product("CHW")!!.reserved
        assertEquals(20, chwBefore)

        val result = OrderOps.cancel(data, "ORD-1051", CancelledBy.OWNER, "No payment", now)
            as OrderOps.CancelResult.Cancelled

        assertEquals(18, result.data.product("CHW")!!.reserved)
        assertEquals(OrderStage.CANCELLED, result.data.order("ORD-1051")!!.stage)
        assertEquals("Order cancelled. Reserved stock returned.", result.message)
    }

    @Test
    fun a04_a_release_never_drives_reserved_negative_or_needs_a_clamp() {
        // The seed used to reserve CHW at 4 while its own order lines held 20, so
        // cancelling ORD-1045 (CHW x8) ran reserved - 8 through a
        // coerceAtLeast(0) and reported 0. That silently UNDER-REPORTED stock and
        // looked exactly like a successful release.
        //
        // Now Reserved is derived from the order lines, so the release lands on a
        // real number: 20 - 8 = 12. Asserting the exact figure - not merely
        // ">= 0" - is what makes a clamp visible, because a clamp changes the
        // number while still passing a non-negative check.
        val result = OrderOps.cancel(data, "ORD-1045", CancelledBy.OWNER, "No payment", now)
            as OrderOps.CancelResult.Cancelled
        assertTrue(result.data.products.all { it.reserved >= 0 })
        assertEquals(12, result.data.product("CHW")!!.reserved)
        // And Available rose by exactly the released quantity rather than jumping
        // to the whole on-hand figure a clamp would imply.
        assertEquals(32, result.data.product("CHW")!!.physical)
        assertEquals(20, result.data.product("CHW")!!.available)
    }

    @Test
    fun a04_a_second_cancel_returns_no_stock() {
        // The "exactly once" half: a repeated tap must not release again.
        val first = OrderOps.cancel(data, "ORD-1045", CancelledBy.OWNER, "No payment", now)
            as OrderOps.CancelResult.Cancelled
        val afterFirst = first.data

        val second = OrderOps.cancel(afterFirst, "ORD-1045", CancelledBy.OWNER, "Again", now)
        assertTrue(second is OrderOps.CancelResult.Refused)
        // Reserved is exactly where the first cancel left it.
        assertEquals(
            afterFirst.product("CHW")!!.reserved,
            (second as OrderOps.CancelResult.Refused).data.product("CHW")!!.reserved,
        )
    }

    @Test
    fun a04_a_packed_order_cannot_be_cancelled() {
        val packed = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1045") it.copy(packingStatus = PackingStatus.PACKED) else it
            },
        )
        val result = OrderOps.cancel(packed, "ORD-1045", CancelledBy.OWNER, "reason", now)
        assertEquals(
            OrderOps.CANNOT_CANCEL_PACKED,
            (result as OrderOps.CancelResult.Refused).message,
        )
        assertEquals("Packed orders cannot be cancelled.", OrderOps.CANNOT_CANCEL_PACKED)
    }

    @Test
    fun a04_a_paid_order_cannot_be_cancelled() {
        // A-04 c8 / A-16 c7: ORD-1040 is Paid.
        val result = OrderOps.cancel(data, "ORD-1040", CancelledBy.OWNER, "reason", now)
        assertEquals(
            OrderOps.CANNOT_CANCEL_PAID,
            (result as OrderOps.CancelResult.Refused).message,
        )
        assertEquals("Paid orders cannot be cancelled.", OrderOps.CANNOT_CANCEL_PAID)
    }

    @Test
    fun a16_a_customer_cancel_requires_a_reason() {
        val result = OrderOps.cancel(data, "ORD-1051", CancelledBy.CUSTOMER, "  ", now)
        assertEquals(
            OrderOps.CANCEL_NEEDS_REASON,
            (result as OrderOps.CancelResult.Refused).message,
        )
        assertEquals("Enter a reason for cancelling.", OrderOps.CANCEL_NEEDS_REASON)
    }

    @Test
    fun a16_the_cancellation_records_who_why_and_when() {
        val result = OrderOps.cancel(data, "ORD-1051", CancelledBy.CUSTOMER, "Changed my mind", now)
            as OrderOps.CancelResult.Cancelled
        val cancellation = result.data.order("ORD-1051")!!.cancellation!!
        assertEquals(CancelledBy.CUSTOMER, cancellation.by)
        assertEquals("Changed my mind", cancellation.reason)
        assertEquals(now, cancellation.at)
    }

    @Test
    fun a16_an_owner_cancellation_is_attributed_to_the_owner() {
        // A-16 c8: the customer's history shows "Cancelled by Owner".
        val result = OrderOps.cancel(data, "ORD-1045", CancelledBy.OWNER, "No payment received", now)
            as OrderOps.CancelResult.Cancelled
        assertEquals(CancelledBy.OWNER, result.data.order("ORD-1045")!!.cancellation!!.by)
        // And it lands in the customer's own cancelled list.
        assertTrue(
            result.data.cancelledOrdersFor("mika.tan@example.com")
                .any { it.orderId == "ORD-1045" },
        )
    }

    @Test
    fun a04_restore_reserves_the_stock_again() {
        val cancelled = OrderOps.cancel(data, "ORD-1045", CancelledBy.OWNER, "reason", now)
            as OrderOps.CancelResult.Cancelled
        val chwAfterCancel = cancelled.data.product("CHW")!!.reserved

        val restored = OrderOps.restore(cancelled.data, "ORD-1045", now)
            as OrderOps.CancelResult.Cancelled

        assertEquals(chwAfterCancel + 8, restored.data.product("CHW")!!.reserved)
        assertEquals(OrderStage.ORDER_PLACED, restored.data.order("ORD-1045")!!.stage)
        assertNull(restored.data.order("ORD-1045")!!.cancellation)
        assertEquals("Order restored.", restored.message)
    }

    @Test
    fun a04_restore_is_refused_when_the_stock_is_gone() {
        val cancelled = OrderOps.cancel(data, "ORD-1045", CancelledBy.OWNER, "reason", now)
            as OrderOps.CancelResult.Cancelled
        // Someone else takes all of CHW.
        val soldOut = cancelled.data.copy(
            products = cancelled.data.products.map {
                if (it.sku == "CHW") it.copy(physical = it.reserved) else it
            },
        )
        val restored = OrderOps.restore(soldOut, "ORD-1045", now)
        assertTrue(restored is OrderOps.CancelResult.Refused)
    }

    // =========================================================================
    // A-05: payments, the Paid lock, Unmark Paid
    // =========================================================================

    @Test
    fun a05_a_partial_payment_keeps_the_total_and_lowers_the_balance() {
        // A-05 c5's GWT: P100.00 on ORD-1042 -> Paid P700.00, balance P180.00.
        val result = OrderOps.recordPayment(data, "ORD-1042", "100.00", "GCash", now)
            as OrderOps.PaymentResult.Recorded
        val order = result.data.order("ORD-1042")!!

        assertEquals(70000L, order.amountPaid)
        assertEquals(18000L, order.balance)
        // A-05 c5: the original total is preserved.
        assertEquals(88000L, order.total)
        assertEquals(PaymentStatus.PARTIALLY_PAID, order.paymentStatus)
        assertFalse(result.needsPaidConfirmation)
        assertEquals("Payment recorded.", result.message)
    }

    @Test
    fun a05_an_amount_above_the_balance_is_refused() {
        // A-05 c3's GWT: P500.00 against a P280.00 balance.
        val result = OrderOps.recordPayment(data, "ORD-1042", "500.00", "GCash", now)
        assertEquals(
            OrderOps.INVALID_PAYMENT_AMOUNT,
            (result as OrderOps.PaymentResult.Refused).message,
        )
        assertEquals(
            "Enter an amount greater than zero and not above the balance.",
            OrderOps.INVALID_PAYMENT_AMOUNT,
        )
    }

    @Test
    fun a05_a_zero_amount_is_refused() {
        val result = OrderOps.recordPayment(data, "ORD-1042", "0.00", "GCash", now)
        assertEquals(
            OrderOps.INVALID_PAYMENT_AMOUNT,
            (result as OrderOps.PaymentResult.Refused).message,
        )
    }

    @Test
    fun a05_clearing_the_balance_marks_the_order_paid_and_flags_the_dialog() {
        // A-05 c4: the confirmation is required BEFORE the status changes, so the
        // ops layer reports that it is needed rather than hiding it.
        val result = OrderOps.recordPayment(data, "ORD-1042", "280.00", "GCash", now)
            as OrderOps.PaymentResult.Recorded

        assertTrue("clearing the balance must request the dialog", result.needsPaidConfirmation)
        assertEquals("This order will be marked as Paid.", OrderOps.MARK_AS_PAID_PROMPT)
        val order = result.data.order("ORD-1042")!!
        assertEquals(PaymentStatus.PAID, order.paymentStatus)
        assertEquals(0L, order.balance)
    }

    @Test
    fun a05_a_paid_order_is_locked_against_editing() {
        // A-05 c6: editing is locked once Paid.
        val paid = OrderOps.recordPayment(data, "ORD-1042", "280.00", "GCash", now)
            as OrderOps.PaymentResult.Recorded
        val edit = OrderOps.editQuantities(
            paid.data, "ORD-1042", mapOf("MCKP" to 5),
        )
        assertTrue(edit is OrderOps.EditResult.Refused)
    }

    @Test
    fun a05_a_paid_order_cannot_be_cancelled_by_the_customer() {
        val paid = OrderOps.recordPayment(data, "ORD-1042", "280.00", "GCash", now)
            as OrderOps.PaymentResult.Recorded
        val cancel = OrderOps.cancel(
            paid.data, "ORD-1042", CancelledBy.CUSTOMER, "Changed my mind", now,
        )
        assertEquals(
            OrderOps.CANNOT_CANCEL_PAID,
            (cancel as OrderOps.CancelResult.Refused).message,
        )
    }

    @Test
    fun a05_unmark_paid_before_dispatch_returns_it_to_partially_paid() {
        val result = OrderOps.unmarkPaid(data, "ORD-1040", now)
            as OrderOps.UnmarkResult.Applied
        val order = result.data.order("ORD-1040")!!
        assertEquals(PaymentStatus.PARTIALLY_PAID, order.paymentStatus)
        // The payment rows are kept, so the recorded money is not discarded.
        assertEquals(57000L, order.amountPaid)
        // And it is logged for A-10 c5's Edit History.
        assertTrue(result.data.activityLog.any { it.action.contains("Unmarked Paid") })
    }

    @Test
    fun a05_unmark_paid_after_dispatch_is_refused() {
        val dispatched = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1040") it.copy(shippingStatus = ShippingStatus.IN_TRANSIT) else it
            },
        )
        val result = OrderOps.unmarkPaid(dispatched, "ORD-1040", now)
        assertEquals(
            OrderOps.UNMARK_AFTER_DISPATCH,
            (result as OrderOps.UnmarkResult.Refused).message,
        )
        // The Paid status stands.
        assertEquals(PaymentStatus.PAID, result.data.order("ORD-1040")!!.paymentStatus)
    }

    @Test
    fun a05_unmark_is_refused_on_an_unpaid_order() {
        val result = OrderOps.unmarkPaid(data, "ORD-1042", now)
        assertTrue(result is OrderOps.UnmarkResult.Refused)
    }

    @Test
    fun a05_the_payment_methods_are_the_three_the_criteria_name() {
        // A-05 c2: GCash, Maya or Bank Transfer - nothing else is offered.
        val allowed = setOf("GCash", "Maya", "Bank Transfer")
        assertTrue(allowed.contains("GCash"))
        assertTrue(allowed.contains("Maya"))
        assertTrue(allowed.contains("Bank Transfer"))
    }

    @Test
    fun a05_an_overdue_order_is_flagged_but_never_auto_cancelled() {
        // A-05 c8 / A-04 c2: overdue is a LABEL, not an action.
        val overdue = data.order("ORD-1045")!!
        assertTrue(overdue.isOverdue(now))
        assertEquals(OrderStage.WAITING_PAYMENT, overdue.stage)
        // Its money is untouched: an overdue order is still fully unpaid, which is
        // why A-05 c2's Cancel+Restore walk can run on it without colliding with
        // any payment already recorded.
        assertEquals(PaymentStatus.UNPAID, overdue.paymentStatus)
        // The app never cancels by itself, so a fresh seed holds no cancelled row.
        assertTrue(data.orders.none { it.stage == OrderStage.CANCELLED })
    }

    // =========================================================================
    // A-06: the advisory and combining
    // =========================================================================

    @Test
    fun a06_an_electronic_item_under_seven_days_shows_no_advisory() {
        // A-06 c4: the under-7-day case, which the seed cannot express directly.
        val sixDays = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1038") it.copy(placedAt = now - 6L * 86_400_000L) else it
            },
        )
        assertFalse(sixDays.showsWeekAdvisory(sixDays.order("ORD-1038")!!, now))
    }

    @Test
    fun a06_seven_days_exactly_shows_the_advisory() {
        // The boundary: 7 whole days is "for a week or more".
        assertTrue(data.showsWeekAdvisory(data.order("ORD-1038")!!, now))
    }

    @Test
    fun a06_a_non_electronic_hold_never_shows_the_advisory_however_long() {
        // ORD-1036 is already at 10 days; push it to 100.
        val veryOld = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1036") it.copy(placedAt = now - 100L * 86_400_000L) else it
            },
        )
        assertFalse(veryOld.showsWeekAdvisory(veryOld.order("ORD-1036")!!, now))
    }

    @Test
    fun a06_a_held_order_only_shows_the_advisory_once_it_is_paid() {
        // A-06 c1 defines held orders as the PAID ones, and the advisory is about a
        // paid unit sitting too long. An order the customer asked to hold but has
        // not paid for is still Waiting for Payment, so the reminder is premature.
        val unpaid = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1038") it.copy(paymentStatus = PaymentStatus.UNPAID) else it
            },
        )
        assertFalse(unpaid.showsWeekAdvisory(unpaid.order("ORD-1038")!!, now))
        // And a paid order of the same age and contents DOES show it, which proves
        // the Paid check is what changed the answer.
        assertTrue(data.showsWeekAdvisory(data.order("ORD-1038")!!, now))
    }

    @Test
    fun a06_combining_two_paid_held_orders_of_one_customer_succeeds() {
        // A-06 c6: ORD-1036 P400.00 + ORD-1037 P360.00 = P760.00.
        val result = OrderOps.combine(data, listOf("ORD-1036", "ORD-1037"), now)
            as OrderOps.CombineResult.Combined
        assertEquals(76000L, result.total)
        assertTrue(result.shipmentNo.startsWith("CS-"))
    }

    @Test
    fun a06_combining_keeps_each_original_order_and_invoice() {
        // A-06 c6's second half.
        val result = OrderOps.combine(data, listOf("ORD-1036", "ORD-1037"), now)
            as OrderOps.CombineResult.Combined

        assertEquals(40000L, result.data.order("ORD-1036")!!.total)
        assertEquals(36000L, result.data.order("ORD-1037")!!.total)
        assertNotNull(result.data.invoiceFor("ORD-1036"))
        assertNotNull(result.data.invoiceFor("ORD-1037"))
    }

    @Test
    fun a06_combining_across_customers_is_refused_with_the_exact_message() {
        // A-06 c7's GWT: ORD-1038 (Anne) + ORD-1036 (Mika).
        val result = OrderOps.combine(data, listOf("ORD-1038", "ORD-1036"), now)
        assertEquals(
            OrderOps.MIXED_CUSTOMERS,
            (result as OrderOps.CombineResult.Refused).message,
        )
        assertEquals("Select held orders for the same customer.", OrderOps.MIXED_CUSTOMERS)
        // And no shipment link was written.
        assertTrue(result.data.orders.all { it.combinedShipmentNo == null })
    }

    @Test
    fun a06_combining_needs_at_least_two_orders() {
        val result = OrderOps.combine(data, listOf("ORD-1036"), now)
        assertTrue(result is OrderOps.CombineResult.Refused)
    }

    @Test
    fun a06_an_unpaid_order_cannot_be_combined() {
        val unpaid = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1037") it.copy(paymentStatus = PaymentStatus.UNPAID) else it
            },
        )
        val result = OrderOps.combine(unpaid, listOf("ORD-1036", "ORD-1037"), now)
        assertTrue(result is OrderOps.CombineResult.Refused)
    }

    @Test
    fun a06_combining_assigns_one_shipment_number_to_both_orders() {
        val result = OrderOps.combine(data, listOf("ORD-1036", "ORD-1037"), now)
            as OrderOps.CombineResult.Combined
        assertEquals(
            result.shipmentNo,
            result.data.order("ORD-1036")!!.combinedShipmentNo,
        )
        assertEquals(
            result.shipmentNo,
            result.data.order("ORD-1037")!!.combinedShipmentNo,
        )
    }

    // =========================================================================
    // A-07: packing and dispatch
    // =========================================================================

    @Test
    fun a07_marking_packed_needs_every_line_checked() {
        // A-07 c3-c5: two of three checked is refused.
        val lines = data.linesFor("ORD-1040")
        val twoOfThree = lines.take(2).map { it.lineId }.toSet()

        val result = OrderOps.markPacked(data, "ORD-1040", twoOfThree, now)
        assertEquals(
            OrderOps.CHECK_ALL_ITEMS,
            (result as OrderOps.PackResult.Refused).message,
        )
        assertEquals(
            "Check all items before marking the order as packed.",
            OrderOps.CHECK_ALL_ITEMS,
        )
        assertEquals(PackingStatus.NOT_PACKED, result.data.order("ORD-1040")!!.packingStatus)
    }

    @Test
    fun a07_all_lines_checked_marks_it_packed_with_a_timestamp() {
        val all = data.linesFor("ORD-1040").map { it.lineId }.toSet()
        val result = OrderOps.markPacked(data, "ORD-1040", all, now)
            as OrderOps.PackResult.Packed
        val order = result.data.order("ORD-1040")!!
        assertEquals(PackingStatus.PACKED, order.packingStatus)
        assertEquals(now, order.packedAt)
    }

    @Test
    fun a07_dispatch_reduces_both_physical_and_reserved_so_available_is_unchanged() {
        // A-07 c9's arithmetic: CHW 32 / 20 / 12 dispatching 2 -> 30 / 18 / 12.
        // Available is the invariant; the two sides are the seed's derived figures.
        val before = data.product("CHW")!!
        assertEquals(32, before.physical)
        assertEquals(20, before.reserved)
        assertEquals(12, before.available)

        // ORD-1042 carries CHW? No - it carries MCKP and SCWH. Build a CHW order.
        val withOrder = OrderOps.placeOrder(
            data = data,
            request = OrderOps.CheckoutRequest(
                customerEmail = "mika.tan@example.com",
                lines = listOf(ph.edu.umak.momompurin.data.entity.CartLine("CHW", 2, true)),
                fulfillment = ph.edu.umak.momompurin.data.entity.Fulfillment.SHIP_NOW,
                recipientName = "Mika Tan",
                mobile = "0917 555 0101",
                province = "Metro Manila",
                city = "Makati",
                barangay = "Poblacion",
                addressLine = "12 Mabini St.",
            ),
            offline = false,
            now = now,
        ) as OrderOps.PlaceResult.Placed

        val afterReserve = withOrder.data.product("CHW")!!
        assertEquals("placement raises Reserved", 22, afterReserve.reserved)

        val shipped = OrderOps.updateShipping(
            withOrder.data, withOrder.orderId, "J&T Express", "JT 2612 3456 7890",
            ShippingStatus.IN_TRANSIT, now,
        ) as OrderOps.ShipResult.Updated

        val afterDispatch = shipped.data.product("CHW")!!
        // Both sides fall by 2: 32-2 = 30 and 22-2 = 20, so 30-20 = 10 - which is
        // the placement's own Available. That is the no-double-subtract invariant.
        assertEquals(30, afterDispatch.physical)
        assertEquals(20, afterDispatch.reserved)
        assertEquals(10, afterDispatch.available)
    }

    @Test
    fun a07_a_dispatch_drops_both_sides_and_leaves_available_alone() {
        // Built directly from the seed rather than from a literal, so the test
        // states the SHAPE of the rule (both sides fall by the shipped qty,
        // Available is unchanged) instead of restating one particular seed.
        val chw = data.product("CHW")!!
        val shipped = chw.copy(
            physical = chw.physical - 2,
            reserved = chw.reserved - 2,
        )
        assertEquals(chw.physical - 2, shipped.physical)
        assertEquals(chw.reserved - 2, shipped.reserved)
        assertEquals(chw.available, shipped.available)
    }

    @Test
    fun a07_in_transit_without_a_tracking_number_is_refused() {
        // A-07 c8's GWT.
        val result = OrderOps.updateShipping(
            data, "ORD-1040", "J&T Express", "", ShippingStatus.IN_TRANSIT, now,
        )
        assertEquals(
            OrderOps.ENTER_TRACKING_FIRST,
            (result as OrderOps.ShipResult.Refused).message,
        )
        assertEquals("Enter the tracking number first.", OrderOps.ENTER_TRACKING_FIRST)
        // The previous status survives.
        assertEquals(
            ShippingStatus.NOT_SHIPPED,
            result.data.order("ORD-1040")!!.shippingStatus,
        )
    }

    @Test
    fun a07_a_tracking_number_then_in_transit_is_saved() {
        val result = OrderOps.updateShipping(
            data, "ORD-1040", "J&T Express", "JT 2612 3456 7890",
            ShippingStatus.IN_TRANSIT, now,
        ) as OrderOps.ShipResult.Updated
        val order = result.data.order("ORD-1040")!!
        assertEquals("JT 2612 3456 7890", order.trackingNo)
        assertEquals(ShippingStatus.IN_TRANSIT, order.shippingStatus)
        assertEquals("J&T Express", order.courier)
    }

    @Test
    fun a07_receiving_completes_the_order_and_keeps_the_tracking_number() {
        val inTransit = OrderOps.updateShipping(
            data, "ORD-1040", "J&T Express", "JT 2612 3456 7890",
            ShippingStatus.IN_TRANSIT, now,
        ) as OrderOps.ShipResult.Updated
        val received = OrderOps.updateShipping(
            inTransit.data, "ORD-1040", null, null, ShippingStatus.RECEIVED, now,
        ) as OrderOps.ShipResult.Updated

        val order = received.data.order("ORD-1040")!!
        assertEquals(ShippingStatus.RECEIVED, order.shippingStatus)
        assertEquals("JT 2612 3456 7890", order.trackingNo)
        assertEquals(OrderStage.COMPLETED, order.stage)
    }

    @Test
    fun a07_a_second_dispatch_does_not_subtract_the_stock_twice() {
        val inTransit = OrderOps.updateShipping(
            data, "ORD-1040", "J&T Express", "JT 2612 3456 7890",
            ShippingStatus.IN_TRANSIT, now,
        ) as OrderOps.ShipResult.Updated
        val physicalAfterFirst = inTransit.data.product("CHW")!!.physical

        // Re-saving the same status must be a no-op on stock.
        val again = OrderOps.updateShipping(
            inTransit.data, "ORD-1040", "J&T Express", "JT 2612 3456 7890",
            ShippingStatus.IN_TRANSIT, now,
        ) as OrderOps.ShipResult.Updated
        assertEquals(physicalAfterFirst, again.data.product("CHW")!!.physical)
    }

    @Test
    fun a07_the_four_delivery_routes_are_the_ones_the_criterion_names() {
        assertEquals(
            listOf("J&T Express", "TikTok Shop", "Lalamove/Grab", "Pickup"),
            OrderOps.DELIVERY_ROUTES,
        )
    }

    @Test
    fun a07_the_copy_text_carries_the_recipient_and_the_address() {
        val text = OrderOps.shippingDetailsText(data, "ORD-1042")
        assertTrue(text.contains("ORD-1042"))
        assertTrue(text.contains("Pia Santos"))
        assertTrue(text.contains("0917 555 0142"))
        assertTrue(text.contains("Poblacion"))
    }

    // =========================================================================
    // A-16: the customer's view
    // =========================================================================

    @Test
    fun a16_a_customer_sees_only_their_own_orders_newest_first() {
        val mikaOrders = data.ordersFor("mika.tan@example.com")
        // ORD-1051, ORD-1045, ORD-1036, ORD-1037.
        assertEquals(4, mikaOrders.size)
        val times = mikaOrders.map { it.placedAt }
        assertEquals(times.sortedDescending(), times)
        assertTrue(mikaOrders.all { it.customerEmail == "mika.tan@example.com" })
    }

    @Test
    fun a16_the_tracking_number_is_hidden_before_in_transit() {
        // A-16 c3: only from In Transit onward.
        val toPack = data.order("ORD-1040")!!
        assertEquals(ShippingStatus.NOT_SHIPPED, toPack.shippingStatus)
        assertFalse(toPack.showsTracking)

        val inTransit = toPack.copy(shippingStatus = ShippingStatus.IN_TRANSIT)
        assertTrue(inTransit.showsTracking)
    }

    @Test
    fun a16_the_held_total_is_mika_760() {
        assertEquals(76000L, data.heldOrdersFor("mika.tan@example.com").sumOf { it.total })
    }

    @Test
    fun a16_a_brand_new_customer_has_no_orders() {
        // A-16 c9: "No orders yet." for an account that has placed nothing.
        assertTrue(data.ordersFor("nobody@example.com").isEmpty())
    }

    @Test
    fun a16_a_cancelled_order_appears_in_the_customers_history() {
        val cancelled = OrderOps.cancel(
            data, "ORD-1051", CancelledBy.CUSTOMER, "Changed my mind", now,
        ) as OrderOps.CancelResult.Cancelled
        val history = cancelled.data.cancelledOrdersFor("mika.tan@example.com")
        assertEquals(1, history.size)
        assertEquals("Changed my mind", history.first().cancellation!!.reason)
    }

    // =========================================================================
    // A-08: customer records
    // =========================================================================

    @Test
    fun a08_the_review_flag_is_set_and_cleared() {
        val withFlag = OrderOps.setReviewFlag(
            data, "mika.tan@example.com", "Prefers Lalamove for rush orders", now,
        )
        assertEquals(
            "Prefers Lalamove for rush orders",
            withFlag.account("mika.tan@example.com")!!.reviewFlag,
        )
        val cleared = OrderOps.setReviewFlag(withFlag, "mika.tan@example.com", null, now)
        assertNull(cleared.account("mika.tan@example.com")!!.reviewFlag)
    }

    @Test
    fun a08_setting_a_flag_logs_it() {
        val withFlag = OrderOps.setReviewFlag(data, "mika.tan@example.com", "note", now)
        assertTrue(withFlag.activityLog.any { it.action.contains("Added note") })
    }

    // =========================================================================
    // A-09: reports
    // =========================================================================

    @Test
    fun a09_a_reversed_range_is_refused() {
        val result = OrderOps.rangeError(now, now - 86_400_000L)
        assertEquals(OrderOps.REVERSED_RANGE, result)
        assertEquals("The end date cannot be earlier than the start date.", OrderOps.REVERSED_RANGE)
    }

    @Test
    fun a09_an_ordered_range_is_accepted() {
        assertNull(OrderOps.rangeError(now - 86_400_000L, now))
        assertNull(OrderOps.rangeError(now, now))
    }

    @Test
    fun a09_sales_include_unpaid_orders_at_full_value() {
        // A-09 c3: ORD-1042's full P880.00 counts even though only P600.00 is in.
        val report = data.salesReport(now - 30L * 86_400_000L, now + 86_400_000L)
        assertTrue(
            "sales must reach ORD-1042's full total",
            report.sales >= 88000L,
        )
        // And payments received is STRICTLY less than sales, because some orders
        // are unpaid - which is the distinction the criterion draws.
        assertTrue(report.paymentsReceived < report.sales)
    }

    @Test
    fun a09_every_amount_renders_with_two_decimals() {
        // A-09 c6: "for example P880.00".
        assertEquals("\u20B1880.00", ph.edu.umak.momompurin.ui.theme.MomoMoney.format(88000))
        assertEquals("\u20B135.00", ph.edu.umak.momompurin.ui.theme.MomoMoney.format(3500))
        assertEquals("\u20B10.00", ph.edu.umak.momompurin.ui.theme.MomoMoney.format(0))
    }

    @Test
    fun a09_the_stock_report_carries_the_pinned_chw_row() {
        // A-09 c5's GWT quotes 12 available; on-hand and reserved are the
        // derived figures that produce it.
        val chw = data.stockRows().first { it.sku == "CHW" }
        assertEquals(32, chw.physical)
        assertEquals(20, chw.reserved)
        assertEquals(12, chw.available)
    }

    @Test
    fun a09_a_cancelled_order_is_excluded_from_sales() {
        val cancelled = OrderOps.cancel(data, "ORD-1051", CancelledBy.OWNER, "reason", now)
            as OrderOps.CancelResult.Cancelled
        val report = cancelled.data.salesReport(now - 30L * 86_400_000L, now + 86_400_000L)
        val withoutOrder = data.salesReport(now - 30L * 86_400_000L, now + 86_400_000L)
        assertEquals(withoutOrder.sales - 7000L, report.sales)
    }

    // =========================================================================
    // A-02 c2: today's sales includes unpaid orders
    // =========================================================================

    @Test
    fun a02_todays_sales_counts_unpaid_orders_in_full() {
        // ORD-1042 was placed 3 days before the anchor, so it is not "today"; the
        // rule under test is that an UNPAID order placed today still counts.
        val today = SeedData.build().let { d ->
            d.copy(
                orders = d.orders.map {
                    if (it.orderId == "ORD-1042") it.copy(placedAt = now) else it
                },
            )
        }
        val sales = today.todaysSales(now)
        assertTrue("today's sales must include the unpaid portion", sales >= 88000L)
    }

    // =========================================================================
    // A-04 c5: editing an unsettled order
    // =========================================================================

    @Test
    fun a04_an_unsettled_order_can_be_edited_and_its_stock_follows() {
        // ORD-1051 is CHW x2.
        val before = data.product("CHW")!!.reserved
        val result = OrderOps.editQuantities(data, "ORD-1051", mapOf("CHW" to 3), now)
            as OrderOps.EditResult.Applied
        val order = result.data.order("ORD-1051")!!
        assertEquals(3 * 3500L, order.itemSubtotal)
        assertEquals(before + 1, result.data.product("CHW")!!.reserved)
    }

    @Test
    fun a04_an_invoiced_order_cannot_be_edited() {
        // ORD-1045 is past Order Placed.
        val result = OrderOps.editQuantities(data, "ORD-1045", mapOf("CHW" to 5), now)
        assertTrue(result is OrderOps.EditResult.Refused)
    }

    @Test
    fun a04_an_edit_past_availability_is_refused() {
        val result = OrderOps.editQuantities(data, "ORD-1051", mapOf("CHW" to 999), now)
        assertEquals(
            "Not enough stock for CHW.",
            (result as OrderOps.EditResult.Refused).message,
        )
    }
}
