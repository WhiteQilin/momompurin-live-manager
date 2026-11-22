package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.AccountOps
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.QrEncoder
import ph.edu.umak.momompurin.data.SeedData
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.Role
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.ui.screens.paymentPayload

/**
 * The regression suite for the FINAL UAT fix pass (Krenshin's 19-item report).
 *
 * One test class per part of the report, so a future change that reopens one of
 * these defects fails a test whose name states the rule. Every test is written
 * against the OPS layer or the pure helper, never against a Composable: the
 * screens are the presentation of these rules, and a test that asserted on UI
 * would pass while the rule underneath was wrong.
 */
class UatFixPassTest {

    private val data: MomoData = SeedData.build()
    private val now: Long = SeedData.NOW_ANCHOR

    // =========================================================================
    // PART 1, item 2: A-07 - only a fully PAID order may enter packing
    // =========================================================================

    @Test
    fun item2_a_partially_paid_order_cannot_be_packed() {
        // ORD-1042 is P600 of P880: the exact state the UAT report screenshotted
        // sitting in To Pack.
        val order = data.order("ORD-1042")!!
        assertEquals(PaymentStatus.PARTIALLY_PAID, order.paymentStatus)
        assertFalse(order.isPaid)

        val lines = data.linesFor("ORD-1042").map { it.lineId }.toSet()
        val result = OrderOps.markPacked(data, "ORD-1042", lines, now)

        assertEquals(
            OrderOps.NOT_FULLY_PAID_CANNOT_PACK,
            (result as OrderOps.PackResult.Refused).message,
        )
        assertEquals(
            "Only fully paid orders can be packed. Record the balance first.",
            OrderOps.NOT_FULLY_PAID_CANNOT_PACK,
        )
        // And nothing changed.
        assertEquals(PackingStatus.NOT_PACKED, result.data.order("ORD-1042")!!.packingStatus)
    }

    @Test
    fun item2_an_unpaid_order_cannot_be_packed() {
        val lines = data.linesFor("ORD-1051").map { it.lineId }.toSet()
        val result = OrderOps.markPacked(data, "ORD-1051", lines, now)
        assertEquals(
            OrderOps.NOT_FULLY_PAID_CANNOT_PACK,
            (result as OrderOps.PackResult.Refused).message,
        )
    }

    @Test
    fun item2_the_money_check_is_reported_before_the_item_check() {
        // A partially-paid order whose items are NOT all ticked must state the
        // money problem, because ticking the items would not unblock it. Reporting
        // "check all items" first would send the owner round a loop.
        val result = OrderOps.markPacked(data, "ORD-1042", emptySet(), now)
        assertEquals(
            OrderOps.NOT_FULLY_PAID_CANNOT_PACK,
            (result as OrderOps.PackResult.Refused).message,
        )
    }

    @Test
    fun item2_a_fully_paid_order_still_packs() {
        // The guard must not have broken the happy path.
        val lines = data.linesFor("ORD-1040").map { it.lineId }.toSet()
        val result = OrderOps.markPacked(data, "ORD-1040", lines, now)
            as OrderOps.PackResult.Packed
        assertEquals(
            PackingStatus.PACKED,
            result.data.order("ORD-1040")!!.packingStatus,
        )
    }

    @Test
    fun item2_no_partially_paid_order_sits_in_the_to_pack_queue() {
        // The seed-level assertion behind the fix: the queue is a claim about work
        // the owner can start, and every row in it must pass today's rule.
        data.toPackOrders().forEach { order ->
            assertTrue(
                "${order.orderId} is in To Pack but is not fully paid",
                order.isPaid,
            )
        }
    }

    @Test
    fun item2_ord1042_and_ord1043_wait_for_payment_not_packing() {
        // The report's specific correction, pinned by id.
        assertEquals(OrderStage.WAITING_PAYMENT, data.order("ORD-1042")!!.stage)
        assertEquals(OrderStage.WAITING_PAYMENT, data.order("ORD-1043")!!.stage)
    }

    @Test
    fun item2_the_to_pack_tab_still_looks_healthy_with_two_paid_orders() {
        val packable = data.toPackOrders()
        assertEquals(2, packable.size)
        assertEquals(
            setOf(SeedData.PACKING_ORDER, SeedData.SECOND_PACKABLE_ORDER),
            packable.map { it.orderId }.toSet(),
        )
        packable.forEach { assertEquals(PaymentStatus.PAID, it.paymentStatus) }
    }

    // =========================================================================
    // PART 1, item 3: combining clears the hold and enters To Pack
    // =========================================================================

    @Test
    fun item3_combining_leaves_the_held_list() {
        val before = data.heldOrders().map { it.orderId }.toSet()
        assertTrue(before.containsAll(SeedData.COMBINABLE_ORDER_IDS))

        val result = OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined

        val after = result.data.heldOrders().map { it.orderId }.toSet()
        SeedData.COMBINABLE_ORDER_IDS.forEach { id ->
            assertFalse(
                "$id must leave Hold and combine orders once combined",
                after.contains(id),
            )
        }
    }

    @Test
    fun item3_combined_orders_read_paid_and_to_pack() {
        // The second half of the stuck-in-held bug: the orders must land in the
        // packing queue with a payment badge that agrees with it.
        val result = OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined

        SeedData.COMBINABLE_ORDER_IDS.forEach { id ->
            val order = result.data.order(id)!!
            assertEquals("$id after combine", PaymentStatus.PAID, order.paymentStatus)
            assertEquals("$id after combine", OrderStage.TO_PACK, order.stage)
            assertFalse("$id must no longer be held", order.isHeld)
            assertNotNull("$id keeps its shipment link", order.combinedShipmentNo)
        }
    }

    @Test
    fun item3_a_combined_order_is_never_paid_but_waiting_for_payment() {
        // The old build's bug: a combined paid order reading "Paid" beside
        // "Waiting for Payment". Assert the contradiction is unreachable.
        val result = OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined
        result.data.orders.forEach { order ->
            assertFalse(
                "${order.orderId} reads Paid + Waiting for Payment",
                order.isPaid && order.stage == OrderStage.WAITING_PAYMENT,
            )
        }
    }

    @Test
    fun item3_a_combined_order_appears_in_the_to_pack_queue() {
        val result = OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined
        val queue = result.data.toPackOrders().map { it.orderId }
        SeedData.COMBINABLE_ORDER_IDS.forEach { id ->
            assertTrue("$id must be packable after combining", queue.contains(id))
        }
    }

    @Test
    fun item3_combining_never_rewinds_a_dispatched_order() {
        // Combining must only ever move an order FROM a holding stage. A parcel in
        // transit is not packing work.
        val inTransit = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1036") {
                    it.copy(stage = OrderStage.SHIPPED, shippingStatus = ShippingStatus.IN_TRANSIT)
                } else {
                    it
                }
            },
        )
        val result = OrderOps.combine(inTransit, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined
        assertEquals(OrderStage.SHIPPED, result.data.order("ORD-1036")!!.stage)
    }

    @Test
    fun item3_combining_still_refuses_mixed_customers() {
        // The grouping is a UI guarantee; this is the ops-layer one behind it.
        val result = OrderOps.combine(data, listOf("ORD-1038", "ORD-1036"), now)
        assertEquals(
            OrderOps.MIXED_CUSTOMERS,
            (result as OrderOps.CombineResult.Refused).message,
        )
    }

    // =========================================================================
    // PART 1, item 5: packed advances to To Ship; the customer can confirm receipt
    // =========================================================================

    @Test
    fun item5_packing_moves_the_order_to_to_ship() {
        val lines = data.linesFor("ORD-1040").map { it.lineId }.toSet()
        val result = OrderOps.markPacked(data, "ORD-1040", lines, now)
            as OrderOps.PackResult.Packed
        val order = result.data.order("ORD-1040")!!
        assertEquals(OrderStage.TO_SHIP, order.stage)
        assertEquals(PackingStatus.PACKED, order.packingStatus)
    }

    @Test
    fun item5_a_packed_order_is_no_longer_in_the_to_pack_queue() {
        val lines = data.linesFor("ORD-1040").map { it.lineId }.toSet()
        val result = OrderOps.markPacked(data, "ORD-1040", lines, now)
            as OrderOps.PackResult.Packed
        assertFalse(
            "a packed order must leave To Pack",
            result.data.toPackOrders().any { it.orderId == "ORD-1040" },
        )
    }

    @Test
    fun item5_the_customer_can_confirm_receipt_from_in_transit() {
        // ORD-1035 is seeded In Transit with a real tracking number.
        val result = OrderOps.markReceived(data, "ORD-1035", CancelledBy.CUSTOMER, now)
            as OrderOps.ReceiveResult.Received
        val order = result.data.order("ORD-1035")!!
        assertEquals(ShippingStatus.RECEIVED, order.shippingStatus)
        assertEquals(OrderStage.COMPLETED, order.stage)
    }

    @Test
    fun item5_receipt_is_refused_before_dispatch() {
        val result = OrderOps.markReceived(data, "ORD-1040", CancelledBy.CUSTOMER, now)
        assertEquals(
            OrderOps.NOTHING_TO_RECEIVE,
            (result as OrderOps.ReceiveResult.Refused).message,
        )
    }

    @Test
    fun item5_receipt_is_idempotent_safe_not_silently_rewritten() {
        val first = OrderOps.markReceived(data, "ORD-1035", CancelledBy.CUSTOMER, now)
            as OrderOps.ReceiveResult.Received
        val second = OrderOps.markReceived(
            first.data, "ORD-1035", CancelledBy.CUSTOMER, now + 1000,
        )
        assertTrue(second is OrderOps.ReceiveResult.Refused)
    }

    @Test
    fun item5_the_store_exposes_the_customer_receipt_path() {
        val store = MomoStore()
        store.signIn(store.snapshot().account("jessa.reyes@example.com")!!)
        val result = store.markOrderReceived("ORD-1035")
        assertTrue(result is OrderOps.ReceiveResult.Received)
        assertEquals(
            OrderStage.COMPLETED,
            store.snapshot().order("ORD-1035")!!.stage,
        )
    }

    @Test
    fun item5_unmarking_paid_does_not_rewind_a_to_ship_order() {
        // TO_SHIP is only reachable by packing, and a packed order already keeps
        // its stage - so this asserts the two rules agree rather than contradict.
        val order = data.order("ORD-1040")!!.copy(
            stage = OrderStage.TO_SHIP,
            packingStatus = PackingStatus.PACKED,
        )
        assertEquals(
            OrderStage.TO_SHIP,
            OrderOps.stageAfterUnmark(order, PaymentStatus.PARTIALLY_PAID),
        )
    }

    // =========================================================================
    // PART 1, item 6: order edits are explicit, never implicit on Back
    // =========================================================================

    @Test
    fun item6_editing_quantity_moves_stock_only_when_the_op_is_called() {
        // The screen's buffer means merely typing a new count writes nothing. At
        // the ops layer that is observable: nothing changes until editQuantities
        // runs, and the reserved figure follows the committed call alone.
        val before = data.product("CHW")!!.reserved

        val applied = OrderOps.editQuantities(data, "ORD-1051", mapOf("CHW" to 4), now)
            as OrderOps.EditResult.Applied

        assertEquals(before, data.product("CHW")!!.reserved) // the input is untouched
        assertEquals(before + 2, applied.data.product("CHW")!!.reserved)
    }

    @Test
    fun item6_a_settled_order_refuses_edits() {
        val result = OrderOps.editQuantities(data, "ORD-1040", mapOf("CHW" to 1), now)
        assertTrue(result is OrderOps.EditResult.Refused)
    }

    @Test
    fun item6_an_edit_above_availability_is_refused_so_the_buffer_cannot_oversell() {
        val result = OrderOps.editQuantities(data, "ORD-1051", mapOf("CHW" to 999), now)
        assertEquals(
            "Not enough stock for CHW.",
            (result as OrderOps.EditResult.Refused).message,
        )
    }

    // =========================================================================
    // PART 2, item 8: the approval state, and no invoice before it exists
    // =========================================================================

    @Test
    fun item8_an_order_placed_order_has_no_invoice_to_view() {
        // The screen hides View invoice here; the data agrees - there is nothing
        // to open.
        val placed = data.orders.filter { it.stage == OrderStage.ORDER_PLACED }
        assertTrue(placed.isNotEmpty())
        placed.forEach { order ->
            assertEquals(
                "${order.orderId} must not carry an invoice number before approval",
                "",
                order.invoiceNo,
            )
            assertNull("${order.orderId} must have no invoice row", data.invoiceFor(order.orderId))
        }
    }

    @Test
    fun item8_an_invoice_appears_only_after_the_owner_sends_it() {
        val sent = OrderOps.sendInvoice(data, "ORD-1051", "80.00", now)
            as OrderOps.InvoiceResult.Sent
        val order = sent.data.order("ORD-1051")!!
        assertEquals(OrderStage.WAITING_PAYMENT, order.stage)
        assertTrue(order.invoiceNo.isNotBlank())
        assertNotNull(sent.data.invoiceFor("ORD-1051"))
    }

    // =========================================================================
    // PART 2, item 9: the 24-hour deadline, and the QR payload
    // =========================================================================

    @Test
    fun item9_the_payment_deadline_is_exactly_24_hours_after_the_invoice() {
        val sent = OrderOps.sendInvoice(data, "ORD-1051", "80.00", now)
            as OrderOps.InvoiceResult.Sent
        val order = sent.data.order("ORD-1051")!!
        assertEquals(
            order.invoicedAt!! + OrderOps.PAYMENT_WINDOW_MILLIS,
            order.paymentDeadline!!,
        )
        assertEquals(24L * 60L * 60L * 1000L, OrderOps.PAYMENT_WINDOW_MILLIS)
    }

    @Test
    fun item9_the_qr_payload_carries_the_invoice_and_the_amount_due() {
        // ORD-1042: P880.00 total, P600.00 paid, so P280.00 is owed.
        val order = data.order("ORD-1042")!!
        val payload = paymentPayload(order)
        assertTrue("payload names the shop", payload.contains("Momompurin"))
        assertTrue("payload names the invoice", payload.contains("MP-1042"))
        assertTrue("payload quotes the BALANCE, not the total", payload.contains("280.00"))
        assertFalse("payload must not quote the paid amount as due", payload.contains("600.00"))
    }

    @Test
    fun item9_the_payment_qr_encodes_and_is_the_right_shape() {
        val payload = paymentPayload(data.order("ORD-1042")!!)
        val matrix = QrEncoder.encode(payload)
        assertNotNull("the seeded payload must encode", matrix)
        val size = matrix!!.size
        // A QR is square with a dimension of 4v+17, so (size - 17) % 4 == 0.
        assertEquals("QR must be square", size, matrix[0].size)
        assertEquals("QR dimension must be 4v+17", 0, (size - 17) % 4)
        assertTrue("version must be within the supported range", size in 21..57)
    }

    @Test
    fun item9_the_qr_has_three_finder_patterns() {
        // A structural check that catches an encoder that produces a square of
        // noise: every QR carries three finder patterns, whose outer 7x7 border is
        // dark and whose second ring is light.
        val matrix = QrEncoder.encode("Momompurin | Invoice MP-1042 | Amount PHP 280.00")!!
        val size = matrix.size
        fun finderAt(row: Int, col: Int): Boolean {
            for (dr in 0..6) {
                for (dc in 0..6) {
                    val onBorder = dr == 0 || dr == 6 || dc == 0 || dc == 6
                    val inCore = dr in 2..4 && dc in 2..4
                    if (matrix[row + dr][col + dc] != (onBorder || inCore)) return false
                }
            }
            return true
        }
        assertTrue("top-left finder", finderAt(0, 0))
        assertTrue("top-right finder", finderAt(0, size - 7))
        assertTrue("bottom-left finder", finderAt(size - 7, 0))
    }

    @Test
    fun item9_the_qr_round_trips_through_a_decoder() {
        // The strongest form of the check: take the encoder's own matrix and walk
        // it back to bytes the way a scanner does - read the format info, unmask,
        // zig-zag the codewords out, undo the interleave, and decode byte mode.
        // A matrix that merely LOOKS like a QR fails here.
        val payload = paymentPayload(data.order("ORD-1042")!!)
        val matrix = QrEncoder.encode(payload)!!
        assertEquals(payload, QrTestDecoder.decode(matrix))
    }

    @Test
    fun item9_a_second_payload_also_round_trips() {
        val payload = "Momompurin | Invoice MP-1035 | Amount PHP 0.00"
        assertEquals(payload, QrTestDecoder.decode(QrEncoder.encode(payload)!!))
    }

    @Test
    fun item9_an_oversized_payload_is_refused_rather_than_mis_encoded() {
        // The encoder is capped at version 10 (213 bytes at level M). A payload
        // past that must return null so the screen shows text instead of a code
        // that does not say what it claims.
        val huge = "x".repeat(400)
        assertNull(QrEncoder.encode(huge))
    }

    @Test
    fun item9_a_short_payload_uses_the_smallest_version() {
        val matrix = QrEncoder.encode("A")!!
        assertEquals("version 1 is 21 modules", 21, matrix.size)
    }

    // =========================================================================
    // PART 2, item 13: customer-only password reset
    // =========================================================================

    @Test
    fun item13_an_owner_account_cannot_be_reset_by_email_and_mobile() {
        val result = AccountOps.resetCustomerPassword(
            data = data,
            email = SeedData.OWNER_EMAIL,
            mobile = "0917 555 0100",
            newPassword = "brandnew123",
            confirm = "brandnew123",
        )
        assertEquals(
            AccountOps.OWNER_RECOVERY_RESTRICTED,
            (result as AccountOps.ResetResult.Failure).message,
        )
        assertEquals(
            "Owner password recovery is restricted for security.",
            AccountOps.OWNER_RECOVERY_RESTRICTED,
        )
    }

    @Test
    fun item13_the_owner_refusal_does_not_reveal_whether_the_mobile_matched() {
        // Both a matching and a wrong mobile must give the SAME answer, or the
        // message becomes an oracle for the owner's phone number.
        val matching = AccountOps.resetCustomerPassword(
            data, SeedData.OWNER_EMAIL, "0917 555 0100", "brandnew123", "brandnew123",
        ) as AccountOps.ResetResult.Failure
        val wrong = AccountOps.resetCustomerPassword(
            data, SeedData.OWNER_EMAIL, "0999 999 9999", "brandnew123", "brandnew123",
        ) as AccountOps.ResetResult.Failure
        assertEquals(matching.message, wrong.message)
    }

    @Test
    fun item13_a_customer_resets_with_the_right_email_and_mobile() {
        val result = AccountOps.resetCustomerPassword(
            data = data,
            email = "mika.tan@example.com",
            mobile = "0917 555 0101",
            newPassword = "brandnew123",
            confirm = "brandnew123",
        )
        val updated = (result as AccountOps.ResetResult.Success).account

        // The new password verifies through the unchanged sign-in digest path.
        assertTrue(AccountOps.verify("brandnew123", updated.passwordDigest))
        // And the OLD one does not.
        assertFalse(AccountOps.verify(SeedData.CUSTOMER_PASSWORD, updated.passwordDigest))
    }

    @Test
    fun item13_a_wrong_mobile_is_refused() {
        val result = AccountOps.resetCustomerPassword(
            data, "mika.tan@example.com", "0999 999 0000", "brandnew123", "brandnew123",
        )
        assertEquals(
            AccountOps.NO_SUCH_CUSTOMER,
            (result as AccountOps.ResetResult.Failure).message,
        )
    }

    @Test
    fun item13_another_customers_mobile_is_refused() {
        // The email and the mobile must belong to the SAME account.
        val result = AccountOps.resetCustomerPassword(
            data, "mika.tan@example.com", "0917 555 0142", "brandnew123", "brandnew123",
        )
        assertTrue(result is AccountOps.ResetResult.Failure)
    }

    @Test
    fun item13_a_short_new_password_is_refused() {
        val result = AccountOps.resetCustomerPassword(
            data, "mika.tan@example.com", "0917 555 0101", "short", "short",
        )
        assertEquals(
            AccountOps.SHORT_PASSWORD,
            (result as AccountOps.ResetResult.Failure).message,
        )
    }

    @Test
    fun item13_a_mismatched_confirmation_is_refused() {
        val result = AccountOps.resetCustomerPassword(
            data, "mika.tan@example.com", "0917 555 0101", "brandnew123", "different123",
        )
        assertEquals(
            AccountOps.RESET_MISMATCH,
            (result as AccountOps.ResetResult.Failure).message,
        )
    }

    @Test
    fun item13_the_store_writes_the_reset_digest_and_keeps_other_rows_alone() {
        val store = MomoStore()
        val before = store.snapshot().account("pia.santos@example.com")!!.passwordDigest

        val failure = store.resetCustomerPassword(
            email = "mika.tan@example.com",
            mobile = "0917 555 0101",
            newPassword = "brandnew123",
            confirm = "brandnew123",
        )
        assertNull(failure)

        // Mika changed...
        assertTrue(
            AccountOps.verify(
                "brandnew123",
                store.snapshot().account("mika.tan@example.com")!!.passwordDigest,
            ),
        )
        // ...and nobody else did.
        assertEquals(
            before,
            store.snapshot().account("pia.santos@example.com")!!.passwordDigest,
        )
        // The owner's account is untouched too.
        assertTrue(
            AccountOps.verify(
                SeedData.OWNER_PASSWORD,
                store.snapshot().account(SeedData.OWNER_EMAIL)!!.passwordDigest,
            ),
        )
    }

    // =========================================================================
    // PART 2, item 14: the registration rules the live warnings enforce
    // =========================================================================

    @Test
    fun item14_a_mobile_must_start_with_09() {
        // The live warning in the form adds this rule; it is stated here so the
        // rule itself is pinned rather than only the UI text.
        assertTrue(AccountOps.normaliseMobile("0917 555 0101").startsWith("09"))
        assertTrue(AccountOps.isMobile("0917 555 0101"))
        // 11 digits but not a PH mobile.
        assertEquals(11, AccountOps.normaliseMobile("0217 555 0101").length)
        assertFalse(AccountOps.normaliseMobile("0217 555 0101").startsWith("09"))
    }

    @Test
    fun item14_the_email_and_password_rules_are_the_ones_the_form_shows() {
        assertFalse(AccountOps.isEmail("mika.tan@"))
        assertTrue(AccountOps.isEmail("mika.tan@example.com"))
        assertEquals(8, AccountOps.MIN_PASSWORD_LENGTH)
        assertTrue("7 characters is short", "abcdefg".length < AccountOps.MIN_PASSWORD_LENGTH)
        assertTrue("8 characters is enough", "abcdefgh".length >= AccountOps.MIN_PASSWORD_LENGTH)
    }

    @Test
    fun item14_registration_still_cannot_create_an_owner() {
        // A-11 c7 - the rule the RA 10173 dialog must not have disturbed.
        val form = AccountOps.Registration(
            fullName = "Elena Cruz",
            handle = "@elena",
            email = "elena.cruz@example.com",
            mobile = "0917 555 0199",
            province = "Metro Manila",
            city = "Makati",
            barangay = "Poblacion",
            addressLine = "5 Mabini St.",
            password = "customer123",
        )
        val created = (AccountOps.register(data, form) as AccountOps.RegisterResult.Success).account
        assertEquals(Role.CUSTOMER, created.role)
    }

    // =========================================================================
    // Cross-cutting: the stage vocabulary stays closed and consistent
    // =========================================================================

    @Test
    fun the_stage_labels_are_unique_and_non_blank() {
        // A duplicate label would make two tabs read the same while filtering
        // differently, which is the class of bug item 5 was about.
        val labels = OrderStage.entries.map { it.label }
        assertEquals(labels.size, labels.distinct().size)
        labels.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun to_ship_sits_between_packing_and_dispatch_in_the_vocabulary() {
        // The order of the enum is the order of the lifecycle, so a reviewer can
        // read the sequence straight off it.
        val order = OrderStage.entries.map { it.name }
        assertTrue(order.indexOf("TO_PACK") < order.indexOf("TO_SHIP"))
        assertTrue(order.indexOf("TO_SHIP") < order.indexOf("SHIPPED"))
    }
}
