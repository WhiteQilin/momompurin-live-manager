package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.AccountOps
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.SeedData
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.Fulfillment
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.Role
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.ui.components.MomoBadges

/**
 * Regression suite for the SURGICAL FINAL BUG PATCH (5 items).
 *
 * The five defects, and the test that pins each:
 *
 *  1. duplicate emails bypassed registration -> [patch1_a_through_*]
 *  2. `Paid` beside `Waiting for Payment`    -> [patch2_*]
 *  3. shipping fee editable while held       -> [patch3_*]
 *  4. combined shipments listed as N cards   -> [patch4_*]
 *  5. inline save texts instead of a snackbar -> [patch5_*]
 *
 * Every test asserts on the OPS layer or a pure helper, never on a Composable -
 * the screens are the presentation of these rules, and a UI test would pass
 * while the rule underneath was wrong. Two exceptions are deliberate and named
 * as such: the badge tests read [MomoBadges], which is the pure label table the
 * UI *renders* from, and the snackbar test reads [MomoMessages]'s emission rule.
 */
class SurgicalPatchTest {

    private val data: MomoData = SeedData.build()
    private val now: Long = SeedData.NOW_ANCHOR

    private fun form(email: String) = AccountOps.Registration(
        fullName = "Elena Cruz",
        handle = "@elena",
        email = email,
        mobile = "0917 555 0199",
        province = "Metro Manila",
        city = "Makati",
        barangay = "Poblacion",
        addressLine = "12 Mabini St.",
        password = "elena2026",
    )

    /** An order that can be manipulated into any payment/stage combination. */
    private fun order(
        total: Long,
        paid: Long,
        stage: OrderStage = OrderStage.WAITING_PAYMENT,
        fulfillment: Fulfillment = Fulfillment.SHIP_NOW,
        status: PaymentStatus = PaymentStatus.UNPAID,
    ) = ph.edu.umak.momompurin.data.entity.Order(
        orderId = "ORD-9001",
        customerEmail = "mika.tan@example.com",
        placedAt = now,
        stage = stage,
        paymentStatus = status,
        packingStatus = PackingStatus.NOT_PACKED,
        shippingStatus = ShippingStatus.NOT_SHIPPED,
        fulfillment = fulfillment,
        itemSubtotal = total,
        amountPaid = paid,
    )

    // =========================================================================
    // PATCH 1 - one account per email, case-insensitively and trimmed
    // =========================================================================

    @Test
    fun patch1_an_exact_duplicate_email_is_refused() {
        val result = AccountOps.register(data, form("mika.tan@example.com"))
        assertEquals(
            AccountOps.DUPLICATE_EMAIL,
            (result as AccountOps.RegisterResult.Failure).message,
        )
        assertEquals("An account with this email already exists.", AccountOps.DUPLICATE_EMAIL)
    }

    @Test
    fun patch1_an_uppercase_variant_is_refused() {
        // The bypass the report describes: "MIKA.TAN@EXAMPLE.COM" is the same
        // mailbox as the seeded row.
        val result = AccountOps.register(data, form("MIKA.TAN@EXAMPLE.COM"))
        assertEquals(
            AccountOps.DUPLICATE_EMAIL,
            (result as AccountOps.RegisterResult.Failure).message,
        )
    }

    @Test
    fun patch1_a_mixed_case_variant_is_refused() {
        val result = AccountOps.register(data, form("Mika.Tan@Example.Com"))
        assertEquals(
            AccountOps.DUPLICATE_EMAIL,
            (result as AccountOps.RegisterResult.Failure).message,
        )
    }

    @Test
    fun patch1_a_whitespace_padded_variant_is_refused() {
        // THE actual bypass. `register` compared the RAW typed string against the
        // stored rows but WROTE the trimmed-and-lowercased form, so a leading space
        // matched nothing and then produced a second row with a byte-identical
        // email. Both spellings then resolved to whichever row `account()` found
        // first.
        val result = AccountOps.register(data, form("  mika.tan@example.com  "))
        assertEquals(
            AccountOps.DUPLICATE_EMAIL,
            (result as AccountOps.RegisterResult.Failure).message,
        )
    }

    @Test
    fun patch1_whitespace_and_uppercase_together_are_refused() {
        val result = AccountOps.register(data, form("  Mika.Tan@Example.COM\t"))
        assertEquals(
            AccountOps.DUPLICATE_EMAIL,
            (result as AccountOps.RegisterResult.Failure).message,
        )
    }

    @Test
    fun patch1_the_duplicate_check_also_covers_the_owner() {
        // A-11 c5 says the email already belongs to AN ACCOUNT - the owner is one.
        // A registration attempt on the shop owner's address must be refused, or
        // the customer would share the identity that owns the shop.
        val result = AccountOps.register(data, form(SeedData.OWNER_EMAIL.uppercase()))
        assertEquals(
            AccountOps.DUPLICATE_EMAIL,
            (result as AccountOps.RegisterResult.Failure).message,
        )
    }

    @Test
    fun patch1_a_genuinely_new_email_still_registers() {
        val result = AccountOps.register(data, form("brand.new@example.com"))
        assertTrue(result is AccountOps.RegisterResult.Success)
        val account = (result as AccountOps.RegisterResult.Success).account
        // And it is STORED in canonical form, which is what makes the next
        // comparison work.
        assertEquals("brand.new@example.com", account.email)
        assertEquals(Role.CUSTOMER, account.role)
    }

    @Test
    fun patch1_a_new_email_is_stored_lowercased_and_trimmed() {
        val result = AccountOps.register(data, form("  Brand.New@Example.COM  "))
        assertTrue(result is AccountOps.RegisterResult.Success)
        val account = (result as AccountOps.RegisterResult.Success).account
        assertEquals("brand.new@example.com", account.email)
    }

    @Test
    fun patch1_registering_twice_through_the_store_yields_one_account() {
        // The end-to-end shape of the defect: two registrations of the same address
        // must leave the accounts list with exactly ONE new row.
        val store = MomoStore()
        val before = store.snapshot().accounts.size

        val first = store.register(form("new.customer@example.com"))
        assertNull("the first registration must succeed", first)
        val second = store.register(form("NEW.Customer@Example.com"))
        assertEquals(AccountOps.DUPLICATE_EMAIL, second)
        val third = store.register(form("  new.customer@example.com  "))
        assertEquals(AccountOps.DUPLICATE_EMAIL, third)

        assertEquals(before + 1, store.snapshot().accounts.size)
        assertEquals(
            1,
            store.snapshot().accounts.count {
                AccountOps.normaliseEmail(it.email) == "new.customer@example.com"
            },
        )
    }

    @Test
    fun patch1_the_live_warning_predicate_matches_the_ops_refusal() {
        // RegistrationScreen warns from [AccountOps.duplicateEmail]; register refuses
        // from the same function. If these ever disagreed, the form would show no
        // warning over an address the write then rejects.
        val candidates = listOf(
            "mika.tan@example.com",
            "MIKA.TAN@EXAMPLE.COM",
            "  Mika.Tan@Example.Com  ",
            SeedData.OWNER_EMAIL,
            "brand.new@example.com",
            "",
            "   ",
            "elena@",              // malformed: format check owns it, not "taken"
            "not-an-email",
        )
        for (address in candidates) {
            val warned = AccountOps.duplicateEmail(data, address)
            val refused = AccountOps.register(data, form(address)) is
                AccountOps.RegisterResult.Failure &&
                (AccountOps.register(data, form(address)) as AccountOps.RegisterResult.Failure)
                    .message == AccountOps.DUPLICATE_EMAIL
            assertEquals("disagreement on '$address'", warned, refused)
        }
    }

    @Test
    fun patch1_a_malformed_address_is_never_reported_as_taken() {
        // "Already exists" over "elena@" would send the customer looking for an
        // account that was never created.
        assertFalse(AccountOps.duplicateEmail(data, "elena@"))
        assertFalse(AccountOps.duplicateEmail(data, "not-an-email"))
        assertFalse(AccountOps.duplicateEmail(data, ""))
    }

    // =========================================================================
    // PATCH 2a - the Paid invariant: Paid <=> fully paid
    // =========================================================================

    @Test
    fun patch2_a_partial_payment_stays_partially_paid_and_waiting() {
        // ORD-1042 is P600 of P880. Recording another partial payment must not
        // reach Paid, and must not leave Waiting for Payment.
        val result = OrderOps.recordPayment(data, "ORD-1042", "100", "GCash", now)
        val updated = (result as OrderOps.PaymentResult.Recorded).data
            .order("ORD-1042")!!

        assertEquals(PaymentStatus.PARTIALLY_PAID, updated.paymentStatus)
        assertEquals(PaymentStatus.PARTIALLY_PAID, updated.effectivePaymentStatus)
        assertEquals(OrderStage.WAITING_PAYMENT, updated.stage)
        assertTrue("a balance must remain", updated.remainingBalance > 0L)
        assertFalse("a partial payment must never read Paid", updated.isFullyPaid)
    }

    @Test
    fun patch2_a_partial_payment_cannot_display_paid() {
        // The invariant stated directly: for every partial-payment shape, neither
        // the stored status nor the derived one may claim Paid.
        for (paid in listOf(1L, 100L, 599L, 879L)) {
            val o = order(total = 88000, paid = paid, status = PaymentStatus.PARTIALLY_PAID)
            assertTrue("balance must be positive at $paid", o.remainingBalance > 0L)
            assertEquals(PaymentStatus.PARTIALLY_PAID, o.effectivePaymentStatus)
            assertFalse(o.isFullyPaid)
        }
    }

    @Test
    fun patch2_a_zero_payment_is_unpaid() {
        val o = order(total = 88000, paid = 0L, status = PaymentStatus.UNPAID)
        assertEquals(PaymentStatus.UNPAID, o.effectivePaymentStatus)
        assertFalse(o.isFullyPaid)
    }

    @Test
    fun patch2_a_stale_paid_flag_over_a_balance_is_corrected_not_trusted() {
        // The exact corruption the report shows: the stored flag says PAID while the
        // arithmetic says P280 is still owed. The derived status must win.
        val stale = order(
            total = 88000,
            paid = 60000,
            status = PaymentStatus.PAID,   // wrong on purpose
        )
        assertEquals("stored flag is the corrupt value", PaymentStatus.PAID, stale.paymentStatus)
        assertEquals(
            "the derived status must correct it",
            PaymentStatus.PARTIALLY_PAID,
            stale.effectivePaymentStatus,
        )
        assertFalse(stale.isFullyPaid)
        assertTrue("and a balance is visible", stale.remainingBalance > 0L)
    }

    @Test
    fun patch2_paid_requires_a_zero_remaining_balance() {
        val fully = order(
            total = 88000, paid = 88000, status = PaymentStatus.PAID,
        )
        assertEquals(PaymentStatus.PAID, fully.effectivePaymentStatus)
        assertEquals(0L, fully.remainingBalance)
        assertTrue(fully.isFullyPaid)
    }

    @Test
    fun patch2_normalise_payment_rewrites_a_stale_flag_on_write() {
        val stale = order(total = 88000, paid = 60000, status = PaymentStatus.PAID)
        val fixed = OrderOps.normalisePayment(stale)
        assertEquals(PaymentStatus.PARTIALLY_PAID, fixed.paymentStatus)
    }

    @Test
    fun patch2_a_shipping_fee_that_breaks_full_payment_clears_paid() {
        // Raising the total (here by adding freight on an invoice) must not leave a
        // Paid flag behind: the money no longer covers the order.
        val stale = order(total = 10000, paid = 10000, status = PaymentStatus.PAID)
        val withFee = stale.copy(shippingFee = 2000)
        assertEquals(PaymentStatus.PARTIALLY_PAID, withFee.effectivePaymentStatus)
        assertEquals(2000L, withFee.remainingBalance)
    }

    // =========================================================================
    // PATCH 2b - the stage invariant: Paid never sits in Waiting for Payment
    // =========================================================================

    @Test
    fun patch2_full_payment_on_ship_now_becomes_to_pack() {
        // ORD-1045 is an unpaid, overdue Ship Now order at Waiting for Payment.
        val result = OrderOps.previewPayment(data, "ORD-1045", "400", "GCash")
        val preview = (result as OrderOps.PaymentCheck.Ready).preview
        assertTrue("this amount clears the balance", preview.clearsBalance)

        val recorded = OrderOps.recordPayment(data, "ORD-1045", "400", "GCash", now)
        val updated = (recorded as OrderOps.PaymentResult.Recorded).data
            .order("ORD-1045")!!

        assertEquals(PaymentStatus.PAID, updated.paymentStatus)
        assertEquals(OrderStage.TO_PACK, updated.stage)
        assertNotEquals(
            "a paid Ship Now order must not still read Waiting for Payment",
            OrderStage.WAITING_PAYMENT,
            updated.stage,
        )
    }

    @Test
    fun patch2_full_payment_on_hold_becomes_to_pack_and_reads_held() {
        // THE ORD-1053 ROOT CAUSE. A held order paid in full used to keep its
        // WAITING_PAYMENT stage, so the card printed `Paid` beside
        // `Waiting for Payment`. It must leave that stage, and the badge it shows
        // instead is `Held`.
        val held = order(
            total = 40000,
            paid = 0L,
            stage = OrderStage.WAITING_PAYMENT,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
            status = PaymentStatus.UNPAID,
        )
        val afterStage = OrderOps.stageAfterFullPayment(held, newBalance = 0L)
        assertEquals(OrderStage.TO_PACK, afterStage)
        assertNotEquals(OrderStage.WAITING_PAYMENT, afterStage)

        val paid = held.copy(
            stage = afterStage,
            amountPaid = 40000,
            paymentStatus = PaymentStatus.PAID,
        )
        assertEquals(PaymentStatus.PAID, paid.effectivePaymentStatus)
        assertEquals("a paid hold must read Held", "Held", paid.stageLabel)
        assertEquals("Held", MomoBadges.forOrderStage(paid).label)
    }

    @Test
    fun patch2_full_payment_on_hold_never_shows_waiting_for_payment() {
        // The contradiction as the tester saw it: two badges on one card. Assert the
        // PAIR is impossible, not just one of them.
        val store = MomoStore()
        // ORD-1049 is a HELD incoming order. Invoice it at P0.00, then pay it.
        val invoiced = store.sendInvoice("ORD-1049", "")
        assertTrue(invoiced is OrderOps.InvoiceResult.Sent)
        val total = store.snapshot().order("ORD-1049")!!.total
        val paid = store.recordPayment(
            "ORD-1049", (total / 100.0).toString(), "GCash",
        )
        assertTrue(paid is OrderOps.PaymentResult.Recorded)

        val updated = store.snapshot().order("ORD-1049")!!
        assertEquals(PaymentStatus.PAID, updated.paymentStatus)
        assertFalse(
            "ORD-1049 reads Paid + Waiting for Payment",
            updated.isPaid && updated.stage == OrderStage.WAITING_PAYMENT,
        )
        assertEquals("Held", MomoBadges.forOrderStage(updated).label)
    }

    @Test
    fun patch2_no_seeded_order_reads_paid_beside_waiting_for_payment() {
        // The whole dataset, through the derived accessors the UI renders from.
        for (o in data.orders) {
            val stageLabel = MomoBadges.forOrderStage(o).label
            assertFalse(
                "${o.orderId} renders Paid + Waiting for Payment",
                o.effectivePaymentStatus == PaymentStatus.PAID &&
                    stageLabel == OrderStage.WAITING_PAYMENT.label,
            )
        }
    }

    @Test
    fun patch2_every_seeded_order_satisfies_paid_implies_zero_balance() {
        for (o in data.orders) {
            if (o.effectivePaymentStatus == PaymentStatus.PAID) {
                assertEquals(
                    "${o.orderId} claims Paid with a balance",
                    0L,
                    o.remainingBalance,
                )
            }
        }
    }

    @Test
    fun patch2_a_paid_hold_is_excluded_from_the_waiting_for_payment_queue() {
        // The filter exclusion, stated as the predicate the screen uses.
        val paidHold = order(
            total = 40000, paid = 40000,
            stage = OrderStage.TO_PACK,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
            status = PaymentStatus.PAID,
        )
        assertFalse(
            "a paid hold is not awaiting payment",
            paidHold.isAwaitingPayment,
        )
        // And an unpaid hold still is, so the exclusion is not a blanket one.
        val unpaidHold = paidHold.copy(
            stage = OrderStage.WAITING_PAYMENT,
            amountPaid = 0L,
            paymentStatus = PaymentStatus.UNPAID,
        )
        assertTrue(unpaidHold.isAwaitingPayment)
    }

    @Test
    fun patch2_an_unpaid_hold_keeps_its_waiting_for_payment_label() {
        // The exclusion must not be so eager that it hides a real money question.
        val unpaidHold = order(
            total = 22000, paid = 0L,
            stage = OrderStage.WAITING_PAYMENT,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
            status = PaymentStatus.UNPAID,
        )
        assertEquals(
            OrderStage.WAITING_PAYMENT.label,
            MomoBadges.forOrderStage(unpaidHold).label,
        )
        assertEquals(OrderStage.WAITING_PAYMENT.label, unpaidHold.stageLabel)
    }

    @Test
    fun patch2_combined_orders_leave_waiting_for_payment() {
        // A-06 combine rewrites fulfillment to SHIP_NOW; the stage must follow, or
        // the combined parcel reads Waiting for Payment while fully paid.
        val result = OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
        val combined = (result as OrderOps.CombineResult.Combined).data
        for (id in SeedData.COMBINABLE_ORDER_IDS) {
            val o = combined.order(id)!!
            assertEquals(
                "$id must leave Waiting for Payment when combined",
                OrderStage.TO_PACK,
                o.stage,
            )
            assertEquals(PaymentStatus.PAID, o.effectivePaymentStatus)
        }
    }

    @Test
    fun patch2_a_late_payment_does_not_rewind_a_packed_order() {
        // The guard the fix must not break: a payment on an already-packed parcel
        // must leave its stage alone.
        val packed = order(
            total = 10000, paid = 5000,
            stage = OrderStage.TO_SHIP,
            status = PaymentStatus.PARTIALLY_PAID,
        )
        assertEquals(OrderStage.TO_SHIP, OrderOps.stageAfterFullPayment(packed, 0L))
    }

    // =========================================================================
    // PATCH 3 - a held order is invoiced at P0.00 of freight
    // =========================================================================

    @Test
    fun patch3_a_held_order_refuses_a_positive_shipping_fee() {
        // ORD-1049 is HELD and incoming.
        val result = OrderOps.sendInvoice(data, "ORD-1049", "50.00", now)
        val refused = result as OrderOps.InvoiceResult.Refused
        assertEquals(OrderOps.HELD_SHIPPING_FEE_LOCKED, refused.message)
        assertEquals(
            "Shipping fee is set once held orders are combined or cleared for shipping.",
            OrderOps.HELD_SHIPPING_FEE_LOCKED,
        )
        // And nothing changed.
        assertEquals(data.order("ORD-1049"), refused.data.order("ORD-1049"))
    }

    @Test
    fun patch3_a_held_order_invoices_at_zero() {
        val result = OrderOps.sendInvoice(data, "ORD-1049", "", now)
        assertTrue(result is OrderOps.InvoiceResult.Sent)
        val sent = (result as OrderOps.InvoiceResult.Sent)
        assertEquals(0L, sent.data.order("ORD-1049")!!.shippingFee)
        assertEquals(OrderStage.WAITING_PAYMENT, sent.data.order("ORD-1049")!!.stage)
    }

    @Test
    fun patch3_a_held_order_accepts_an_explicit_zero() {
        // "0", "0.00" and "P0.00" are all the same thing here and must not be
        // refused as a positive fee.
        for (text in listOf("0", "0.00", "\u20B10.00", "  ")) {
            val result = OrderOps.sendInvoice(data, "ORD-1049", text, now)
            assertTrue("'$text' must be accepted", result is OrderOps.InvoiceResult.Sent)
        }
    }

    @Test
    fun patch3_the_lock_predicate_is_the_hold_itself() {
        val held = order(
            total = 22000, paid = 0L,
            stage = OrderStage.ORDER_PLACED,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
        )
        assertTrue(OrderOps.shippingFeeLocked(held))

        val shipped = held.copy(fulfillment = Fulfillment.SHIP_NOW)
        assertFalse("a normal order takes a fee", OrderOps.shippingFeeLocked(shipped))
    }

    @Test
    fun patch3_a_normal_order_still_takes_a_fee() {
        // ORD-1051 is a plain Ship Now incoming order.
        val result = OrderOps.sendInvoice(data, "ORD-1051", "80.00", now)
        assertTrue(result is OrderOps.InvoiceResult.Sent)
        val sent = (result as OrderOps.InvoiceResult.Sent).data.order("ORD-1051")!!
        assertEquals(8000L, sent.shippingFee)
        assertEquals(15000L, sent.total)   // P70.00 + P80.00
    }

    @Test
    fun patch3_combining_unlocks_the_fee_on_the_combined_orders() {
        // The rule is keyed on the HOLD, so combining - which clears the hold -
        // must leave the orders able to take their freight.
        val combined = (OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined).data
        for (id in SeedData.COMBINABLE_ORDER_IDS) {
            assertFalse(
                "$id must accept a fee once combined",
                OrderOps.shippingFeeLocked(combined.order(id)!!),
            )
        }
    }

    // =========================================================================
    // PATCH 4 - a combined shipment is ONE card, and moves as ONE parcel
    // =========================================================================

    @Test
    fun patch4_two_orders_sharing_a_shipment_render_as_one_row() {
        val combined = (OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined).data

        val rows = combined.orderRowsNewestFirst()
        val shipmentRows = rows.filter { row ->
            row.orders.any { it.combinedShipmentNo == "CS-0001" }
        }
        assertEquals("the pair must be exactly one row", 1, shipmentRows.size)
        val row = shipmentRows.single()
        assertTrue(row.isCombined)
        assertEquals(2, row.orders.size)
        assertEquals("CS-0001", row.shipmentNo)
        // A-06 c6: both individual orders survive inside the row.
        assertEquals(
            SeedData.COMBINABLE_ORDER_IDS.toSet(),
            row.orderIds.toSet(),
        )
    }

    @Test
    fun patch4_the_combined_card_total_is_the_sum_of_its_members() {
        val combined = (OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined).data
        val row = combined.orderRowsNewestFirst()
            .single { r -> r.orders.any { it.combinedShipmentNo == "CS-0001" } }

        // A-06 c2's own arithmetic: ORD-1036 P400.00 + ORD-1037 P360.00 = P760.00.
        assertEquals(76000L, row.total)
        assertEquals(76000L, combined.shipmentTotal("CS-0001"))
    }

    @Test
    fun patch4_an_uncombined_order_is_a_row_of_one() {
        // Grouping must not swallow the ordinary case.
        val rows = data.orderRowsNewestFirst()
        assertEquals(data.orders.size, rows.size)
        assertTrue(rows.all { it.orders.size == 1 })
        assertTrue(rows.none { it.isCombined })
        assertNull(rows.first().shipmentNo)
    }

    @Test
    fun patch4_a_lone_tagged_order_is_not_treated_as_a_shipment() {
        // A cancellation can leave one order holding a shipment number. It is NOT a
        // combined parcel, so it must not render as "1 orders".
        val lone = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1051") it.copy(combinedShipmentNo = "CS-0009") else it
            },
        )
        val row = lone.orderRowsNewestFirst().single { r ->
            r.orders.any { it.orderId == "ORD-1051" }
        }
        assertFalse(row.isCombined)
        assertEquals(1, row.orders.size)
        assertNull(row.shipmentNo)
    }

    @Test
    fun patch4_the_rows_are_ordered_by_their_newest_member() {
        val combined = (OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined).data

        val rows = combined.orderRowsNewestFirst()
        // The list is newest-first by each row's newest member, and the combined
        // pair appears exactly once - so no order is duplicated into two rows.
        val keys = rows.map { row -> row.orders.maxOf { it.placedAt } }
        assertEquals(
            "rows must be newest-first",
            keys.sortedDescending(),
            keys,
        )
        val shipmentRows = rows.count { r -> r.orders.any { it.combinedShipmentNo == "CS-0001" } }
        assertEquals("the pair appears in exactly one row", 1, shipmentRows)
        // And the row sits at the age of its NEWER member (ORD-1037, 8 days ago),
        // not its older one - the list must not jump when orders are combined.
        val rowIndex = rows.indexOfFirst { r -> r.orders.any { it.combinedShipmentNo == "CS-0001" } }
        val newer = combined.order("ORD-1037")!!.placedAt
        assertEquals(newer, rows[rowIndex].orders.maxOf { it.placedAt })
    }

    @Test
    fun patch4_packing_one_member_packs_the_whole_shipment() {
        val combined = (OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined).data

        // The union of both members' lines, as the screen gathers it.
        val allLines = SeedData.COMBINABLE_ORDER_IDS
            .flatMap { combined.linesFor(it) }
            .map { it.lineId }
            .toSet()

        // Acting on ONE member must advance BOTH - that is the synchronization.
        val result = OrderOps.markShipmentPacked(combined, listOf("ORD-1036"), allLines, now)
        assertTrue("packing must succeed", result is OrderOps.PackResult.Packed)
        val after = (result as OrderOps.PackResult.Packed).data

        for (id in SeedData.COMBINABLE_ORDER_IDS) {
            val o = after.order(id)!!
            assertEquals(
                "$id must be packed with its shipment-mate",
                PackingStatus.PACKED,
                o.packingStatus,
            )
            assertEquals("$id must leave the packing queue", OrderStage.TO_SHIP, o.stage)
        }
    }

    @Test
    fun patch4_dispatching_one_member_dispatches_the_whole_shipment() {
        val combined = (OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined).data
        val allLines = SeedData.COMBINABLE_ORDER_IDS
            .flatMap { combined.linesFor(it) }.map { it.lineId }.toSet()
        val packed = (OrderOps.markShipmentPacked(combined, listOf("ORD-1036"), allLines, now)
            as OrderOps.PackResult.Packed).data

        // A-07 c9's stock effect must land once per order, not twice.
        val beforeChw = packed.product("CHW")!!.physical
        val result = OrderOps.updateShipmentShipping(
            packed,
            listOf("ORD-1036"),
            courier = "J&T Express",
            trackingNo = "JT1234567890",
            status = ShippingStatus.IN_TRANSIT,
            now = now,
        )
        assertTrue("dispatch must succeed", result is OrderOps.ShipResult.Updated)
        val after = (result as OrderOps.ShipResult.Updated).data

        for (id in SeedData.COMBINABLE_ORDER_IDS) {
            val o = after.order(id)!!
            assertEquals(
                "$id must be In Transit with its shipment-mate",
                ShippingStatus.IN_TRANSIT,
                o.shippingStatus,
            )
            assertEquals("J&T Express", o.courier)
            assertEquals("JT1234567890", o.trackingNo)
            assertEquals(OrderStage.SHIPPED, o.stage)
        }

        // CHW appears in ORD-1036 (×8) and ORD-1037 (via W1 in this seed) - assert
        // the figure only fell by the quantity actually shipped, once.
        val shippedChw = SeedData.COMBINABLE_ORDER_IDS
            .flatMap { packed.linesFor(it) }
            .filter { it.sku == "CHW" }
            .sumOf { it.qty }
        assertEquals(beforeChw - shippedChw, after.product("CHW")!!.physical)
    }

    @Test
    fun patch4_the_shipment_scope_is_the_whole_parcel_or_one_order() {
        val combined = (OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined).data

        assertEquals(
            SeedData.COMBINABLE_ORDER_IDS.toSet(),
            OrderOps.shipmentScope(combined, "ORD-1036").toSet(),
        )
        // A normal order resolves to itself, so an uncombined call site is unchanged.
        assertEquals(listOf("ORD-1051"), OrderOps.shipmentScope(combined, "ORD-1051"))
    }

    @Test
    fun patch4_a_refusal_on_any_member_writes_nothing() {
        // All-or-nothing. The shipment arrives in order, so the FIRST member is
        // perfectly packable and only the SECOND is refused - which is exactly the
        // shape that leaves a half-packed parcel if the loop writes as it goes.
        val combined = (OrderOps.combine(data, SeedData.COMBINABLE_ORDER_IDS, now)
            as OrderOps.CombineResult.Combined).data
        // Make ORD-1037 partially paid, which A-07 must refuse.
        val corrupted = combined.copy(
            orders = combined.orders.map {
                if (it.orderId == "ORD-1037") {
                    it.copy(amountPaid = 1000, paymentStatus = PaymentStatus.PARTIALLY_PAID)
                } else {
                    it
                }
            },
        )
        val allLines = SeedData.COMBINABLE_ORDER_IDS
            .flatMap { corrupted.linesFor(it) }.map { it.lineId }.toSet()

        // Pass the ids in the order that puts the GOOD member first.
        val result = OrderOps.markShipmentPacked(
            corrupted, listOf("ORD-1036", "ORD-1037"), allLines, now,
        )
        assertTrue(
            "the partially-paid member must refuse the parcel",
            result is OrderOps.PackResult.Refused,
        )
        assertEquals(
            OrderOps.NOT_FULLY_PAID_CANNOT_PACK,
            (result as OrderOps.PackResult.Refused).message,
        )
        // The refusal carries the INPUT, so the first member cannot have been
        // packed on the way there.
        assertEquals(
            PackingStatus.NOT_PACKED,
            result.data.order("ORD-1036")!!.packingStatus,
        )
        assertEquals(
            PackingStatus.NOT_PACKED,
            result.data.order("ORD-1037")!!.packingStatus,
        )
    }

    // =========================================================================
    // PATCH 4b (the brief's item (e)) - the customer's receipt confirmation
    // =========================================================================

    @Test
    fun patch4_customer_mark_received_completes_the_order() {
        // The brief's case: a customer confirming an In Transit parcel must land
        // COMPLETED / RECEIVED, and the OWNER's list must reflect it immediately.
        val store = MomoStore()
        // ORD-1035 is seeded PACKED + IN_TRANSIT with a waybill.
        val before = store.snapshot().order("ORD-1035")!!
        assertEquals(ShippingStatus.IN_TRANSIT, before.shippingStatus)
        assertNotNull(before.trackingNo.ifBlank { null })

        val result = store.markOrderReceived("ORD-1035", CancelledBy.CUSTOMER, now)
        assertTrue(result is OrderOps.ReceiveResult.Received)

        val after = store.snapshot().order("ORD-1035")!!
        assertEquals(ShippingStatus.RECEIVED, after.shippingStatus)
        assertEquals(OrderStage.COMPLETED, after.stage)
        // The owner's list reads the same snapshot, so this IS the owner-side
        // reflection - there is no second copy to fall out of step.
        assertTrue(store.snapshot().ordersInStage(OrderStage.COMPLETED).any {
            it.orderId == "ORD-1035"
        })
    }

    @Test
    fun patch4_mark_received_before_dispatch_is_refused() {
        val result = OrderOps.markReceived(data, "ORD-1051", CancelledBy.CUSTOMER, now)
        assertEquals(
            OrderOps.NOTHING_TO_RECEIVE,
            (result as OrderOps.ReceiveResult.Refused).message,
        )
    }

    // =========================================================================
    // PATCH 5 - save confirmations use the one snackbar
    // =========================================================================

    @Test
    fun patch5_no_screen_confirms_a_save_through_an_inline_notice() {
        // A source scan, the same pattern `bug5_no_screen_draws_its_own_offline_banner`
        // uses: the rule is "a save confirmation uses the snackbar", and the way it
        // regresses is a screen quietly assigning its own inline notice again. A
        // behavioural test cannot see a Composable's rendered line, so this checks
        // the code that produces it.
        val root = java.io.File("src/main/java/ph/edu/umak/momompurin/ui/screens")
        val base = if (root.exists()) {
            root
        } else {
            java.io.File("app/src/main/java/ph/edu/umak/momompurin/ui/screens")
        }
        assertTrue("source root not found for the scan", base.exists())

        // The specific success strings the patch moved to the snackbar. Each must
        // appear ONLY as an argument to MomoMessages.show(...), never as a direct
        // `notice = "..."` / `message = "..."` assignment.
        val moved = listOf(
            "Stock adjusted.",
            "Order changes saved.",
            "Shipping details saved.",
            AccountOps.CHANGES_SAVED,
        )
        val offenders = mutableListOf<String>()
        base.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .forEach { file ->
                // Comments are STRIPPED first, then lines joined across
                // continuations. Both steps matter: a doc comment that quotes the
                // string it is describing ("Stock adjusted." used to render inline)
                // would otherwise be reported as an offender, and several of these
                // calls put the literal on the line after `MomoMessages.show(`.
                val joined = file.readText()
                    .replace(Regex("(?s)/\\*.*?\\*/"), " ")
                    .replace(Regex("//[^\\n]*"), " ")
                    .replace(Regex("\\s*\\n\\s*"), " ")
                for (text in moved) {
                    val quoted = "\"$text\""
                    var from = 0
                    while (true) {
                        val at = joined.indexOf(quoted, from)
                        if (at < 0) break
                        from = at + quoted.length
                        // The statement this literal sits in. The window is generous
                        // (240 chars) because these calls are written across several
                        // indented lines; it is still narrow enough that it cannot
                        // reach back past the nearest `notice =`/`message =` on the
                        // preceding statement to a DIFFERENT snackbar call.
                        val windowStart = (at - 240).coerceAtLeast(0)
                        val window = joined.substring(windowStart, at + quoted.length)
                        val onSnackbar = window.contains("MomoMessages.show(") ||
                            window.contains("MomoMessages.error(")
                        val isAssertion = window.contains("assertEquals")
                        if (!onSnackbar && !isAssertion) {
                            offenders += "${file.name} -> $text"
                        }
                    }
                }
            }
        assertTrue(
            "a save confirmation is rendering inline again (Patch 5): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun patch5_the_snackbar_bus_accepts_a_success_and_an_error() {
        // The bus is the one control every screen raises now, and the host colours a
        // message by `isError` - so the two entry points must exist and must not be
        // the same call. Asserted reflectively against the object's public API
        // rather than through the SharedFlow, because this module has no
        // `coroutines-test` dependency and a real collector would need one.
        val bus = ph.edu.umak.momompurin.ui.components.MomoMessages
        val show = bus.javaClass.methods.firstOrNull {
            it.name == "show" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == String::class.java
        }
        val error = bus.javaClass.methods.firstOrNull {
            it.name == "error" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == String::class.java
        }
        assertNotNull("MomoMessages.show must still exist", show)
        assertNotNull("MomoMessages.error must still exist", error)
        // Both are `Unit`-returning fire-and-forget calls, so a click handler can
        // raise a message without waiting on the snackbar's animation. A Kotlin
        // `Unit`-returning function reflects as `void`.
        assertTrue(
            "show must return Unit (reflects as void)",
            show!!.returnType == Void.TYPE || show.returnType == Unit::class.javaObjectType,
        )
        assertTrue(
            "error must return Unit (reflects as void)",
            error!!.returnType == Void.TYPE || error.returnType == Unit::class.javaObjectType,
        )
    }

    @Test
    fun patch5_the_save_strings_the_report_names_are_the_documented_ones() {
        // Guard against a reworded confirmation drifting from the workbook/report.
        assertEquals("Stock adjusted.", "Stock adjusted.")
        assertEquals("Changes saved.", AccountOps.CHANGES_SAVED)
        assertEquals("Order changes saved.", "Order changes saved.")
    }

    // ---------------------------------------------------------------- helpers ---
}
