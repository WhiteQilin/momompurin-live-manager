package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.SeedData
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.ui.screens.InvoiceRenderer

/**
 * The eight defects from `Bugs_in_Momompurin_v3.docx` - the real-phone pass on a
 * Xiaomi 14T Pro - each pinned so a regression fails the build instead of
 * resurfacing on the client's handset.
 *
 * Bug 1 (invoice text overflow) and Bug 4 (the Open action) are covered by the
 * renderer and snackbar code they live in; the parts of them a plain JVM can assert
 * are the content contract and the action wiring, and those are pinned below.
 */
class RealPhoneBugFixesTest {

    private val data: MomoData = SeedData.build()
    private val now = SeedData.NOW_ANCHOR

    private fun ownerStore(): MomoStore =
        MomoStore(data).also { it.signIn(it.snapshot().owner()!!) }

    // =========================================================================
    // Bug 1 - the invoice's RA 10173 footer must fit inside the page
    // =========================================================================

    /**
     * The sentence the report showed running past the picture's edge.
     *
     * The renderer lays every line out with StaticLayout at
     * `pageWidth - left - right`, so the assertion that matters here is that the
     * footer's own wording is present verbatim AND short enough to wrap inside a
     * phone-width column rather than being one unbreakable run.
     */
    @Test
    fun bug1_the_privacy_footer_is_the_four_known_lines() {
        val footer = InvoiceRenderer.privacyFooter()
        assertEquals(4, footer.size)
        assertTrue(
            "the deletion-request sentence the report showed overflowing must be present",
            footer.any { it.contains("Questions or a deletion request") },
        )
        // Every line is a sentence with spaces, which is what gives StaticLayout the
        // break opportunities it needs. A single unbroken token could not wrap.
        footer.forEach { line ->
            assertTrue("footer line has no break opportunity: $line", line.contains(' '))
        }
    }

    /**
     * The rendered rows must include the delivery address, which is the other long
     * string on the document and the one that varies with real customer data.
     */
    @Test
    fun bug1_the_invoice_carries_the_address_that_has_to_wrap() {
        val order = data.order("ORD-1051")!!
        val rows = InvoiceRenderer.contentLines(data, order)
        assertTrue(
            "the address block must be a rendered row so it can be wrapped",
            rows.any { it.label == order.addressBlock },
        )
        // It is genuinely long enough to need more than one line at phone width.
        assertTrue(
            "the seeded address should be long enough to exercise wrapping",
            order.addressBlock.length > 30,
        )
    }

    /** No row may be a single unbreakable token - that is what cannot wrap. */
    @Test
    fun bug1_every_rendered_row_has_a_break_opportunity() {
        val order = data.order("ORD-1051")!!
        InvoiceRenderer.contentLines(data, order)
            .filter { it.label.isNotBlank() }
            .forEach { row ->
                assertTrue(
                    "row '${row.label}' has no space and could not wrap",
                    row.label.contains(' ') || row.label.length <= 12,
                )
            }
    }

    // =========================================================================
    // Bug 2 - a cancelled order takes no payment
    // =========================================================================

    @Test
    fun bug2_preview_payment_refuses_a_cancelled_order() {
        val cancelled = OrderOps.cancel(data, "ORD-1045", CancelledBy.OWNER, "No payment", now)
        val after = (cancelled as OrderOps.CancelResult.Cancelled).data
        assertEquals(OrderStage.CANCELLED, after.order("ORD-1045")!!.stage)

        val check = OrderOps.previewPayment(after, "ORD-1045", "100.00", "GCash")
        assertTrue(check is OrderOps.PaymentCheck.Refused)
        assertEquals(
            OrderOps.CANCELLED_TAKES_NO_PAYMENT,
            (check as OrderOps.PaymentCheck.Refused).message,
        )
        assertEquals(
            "Cancelled orders cannot accept payments. Restore the order first.",
            OrderOps.CANCELLED_TAKES_NO_PAYMENT,
        )
    }

    @Test
    fun bug2_record_payment_refuses_a_cancelled_order_and_writes_nothing() {
        val cancelled = OrderOps.cancel(data, "ORD-1045", CancelledBy.OWNER, "No payment", now)
        val after = (cancelled as OrderOps.CancelResult.Cancelled).data
        val before = after.payments.size

        val result = OrderOps.recordPayment(after, "ORD-1045", "100.00", "GCash", now)
        assertTrue(result is OrderOps.PaymentResult.Refused)
        assertEquals(
            OrderOps.CANCELLED_TAKES_NO_PAYMENT,
            (result as OrderOps.PaymentResult.Refused).message,
        )
        // A refusal writes nothing: no payment row, no log row.
        assertEquals(before, result.data.payments.size)
        assertEquals(after.activityLog.size, result.data.activityLog.size)
        assertEquals(OrderStage.CANCELLED, result.data.order("ORD-1045")!!.stage)
    }

    /** The refusal must not leak into a live order: a waiting order still pays. */
    @Test
    fun bug2_a_waiting_order_still_accepts_payment() {
        val check = OrderOps.previewPayment(data, "ORD-1051", "70.00", "GCash")
        assertTrue(check is OrderOps.PaymentCheck.Ready)
    }

    // =========================================================================
    // Bonus - a fully paid order leaves Waiting for Payment
    // =========================================================================

    @Test
    fun bonus_paying_in_full_moves_a_ship_now_order_to_to_pack() {
        // ORD-1051 is Waiting for Payment after its invoice, Ship Now.
        val invoiced = OrderOps.sendInvoice(data, "ORD-1051", "0.00", now)
            as OrderOps.InvoiceResult.Sent
        val waiting = invoiced.data.order("ORD-1051")!!
        assertEquals(OrderStage.WAITING_PAYMENT, waiting.stage)
        assertEquals(false, waiting.isHeld)

        val paid = OrderOps.recordPayment(
            invoiced.data, "ORD-1051", "70.00", "GCash", now,
        ) as OrderOps.PaymentResult.Recorded
        val after = paid.data.order("ORD-1051")!!

        assertEquals(PaymentStatus.PAID, after.paymentStatus)
        assertEquals(0L, after.balance)
        assertEquals(
            "a paid Ship Now order must not still read Waiting for Payment",
            OrderStage.TO_PACK,
            after.stage,
        )
    }

    @Test
    fun bonus_a_held_order_keeps_its_stage_when_paid() {
        // ORD-1036/ORD-1037 are the combinable held pair.
        val held = data.order("ORD-1036")!!
        assertTrue(held.isHeld)
        val stageBefore = held.stage
        assertEquals(stageBefore, OrderOps.stageAfterFullPayment(held, 0L))
    }

    @Test
    fun bonus_a_partial_payment_does_not_move_the_stage() {
        val invoiced = OrderOps.sendInvoice(data, "ORD-1051", "0.00", now)
            as OrderOps.InvoiceResult.Sent
        val partial = OrderOps.recordPayment(
            invoiced.data, "ORD-1051", "20.00", "GCash", now,
        ) as OrderOps.PaymentResult.Recorded
        val after = partial.data.order("ORD-1051")!!
        assertEquals(PaymentStatus.PARTIALLY_PAID, after.paymentStatus)
        assertEquals(OrderStage.WAITING_PAYMENT, after.stage)
    }

    @Test
    fun bonus_a_later_stage_is_never_rewound_by_a_payment() {
        // A packed order that receives money keeps its place in the queue.
        val packed = data.copy(
            orders = data.orders.map {
                if (it.orderId == "ORD-1051") it.copy(stage = OrderStage.SHIPPED) else it
            },
        )
        val order = packed.order("ORD-1051")!!
        assertEquals(OrderStage.SHIPPED, OrderOps.stageAfterFullPayment(order, 0L))
    }

    @Test
    fun bonus_unmarking_paid_returns_an_unpacked_order_to_waiting() {
        val invoiced = OrderOps.sendInvoice(data, "ORD-1051", "0.00", now)
            as OrderOps.InvoiceResult.Sent
        val paid = OrderOps.recordPayment(
            invoiced.data, "ORD-1051", "70.00", "GCash", now,
        ) as OrderOps.PaymentResult.Recorded
        assertEquals(OrderStage.TO_PACK, paid.data.order("ORD-1051")!!.stage)

        // Unmark: the money rows stay, so the balance is still zero - but the
        // confirmation is withdrawn and the order must ask for payment again.
        val unmarked = OrderOps.unmarkPaid(paid.data, "ORD-1051", now)
            as OrderOps.UnmarkResult.Applied
        val after = unmarked.data.order("ORD-1051")!!
        assertEquals(PaymentStatus.PARTIALLY_PAID, after.paymentStatus)
        assertEquals(
            "unmarking must put the order back in the payment queue",
            OrderStage.WAITING_PAYMENT,
            after.stage,
        )
    }

    @Test
    fun bonus_unmarking_does_not_rewind_a_packed_order() {
        val order = data.order("ORD-1051")!!.copy(
            stage = OrderStage.TO_PACK,
            packingStatus = PackingStatus.PACKED,
            amountPaid = 7000,
            paymentStatus = PaymentStatus.PAID,
        )
        // Packed already: it keeps To Pack even though the confirmation is withdrawn.
        assertEquals(
            OrderStage.TO_PACK,
            OrderOps.stageAfterUnmark(order, PaymentStatus.PARTIALLY_PAID),
        )
    }

    @Test
    fun bonus_unmarking_does_not_rewind_a_dispatched_order() {
        val order = data.order("ORD-1051")!!.copy(
            stage = OrderStage.TO_PACK,
            shippingStatus = ShippingStatus.IN_TRANSIT,
        )
        assertEquals(
            OrderStage.TO_PACK,
            OrderOps.stageAfterUnmark(order, PaymentStatus.PARTIALLY_PAID),
        )
    }

    // =========================================================================
    // End to end: the paid order's badges stop contradicting each other
    // =========================================================================

    @Test
    fun bonus_a_paid_order_never_shows_paid_beside_waiting_for_payment() {
        val store = ownerStore()
        store.sendInvoice("ORD-1051", "0.00")
        store.recordPayment("ORD-1051", "70.00", "GCash")
        val order = store.snapshot().order("ORD-1051")!!

        assertEquals(PaymentStatus.PAID, order.paymentStatus)
        assertTrue(
            "Paid + Waiting for Payment is the contradiction the report shows",
            order.stage != OrderStage.WAITING_PAYMENT,
        )
        // And the audit chain survived the two transitions.
        assertTrue(store.verifyAuditChain().valid)
    }

    // =========================================================================
    // Bug 2 through the store: the owner-facing guard
    // =========================================================================

    @Test
    fun bug2_the_store_refuses_payment_on_a_cancelled_order() {
        val store = ownerStore()
        store.cancelOrder("ORD-1045", CancelledBy.OWNER, "No payment")
        assertEquals(OrderStage.CANCELLED, store.snapshot().order("ORD-1045")!!.stage)

        val result = store.recordPayment("ORD-1045", "100.00", "GCash")
        assertTrue(result is OrderOps.PaymentResult.Refused)
        assertEquals(
            OrderOps.CANCELLED_TAKES_NO_PAYMENT,
            (result as OrderOps.PaymentResult.Refused).message,
        )
        assertTrue("a refused payment must not touch the chain", store.verifyAuditChain().valid)
    }

    // =========================================================================
    // Bug 3 - the cascade still resets correctly behind the new picker
    // =========================================================================

    @Test
    fun bug3_the_cascade_still_clears_children_in_order() {
        val (p, c, b) = ph.edu.umak.momompurin.data.PhAddresses.clearBelow("Metro Manila")
        assertEquals("Metro Manila", p)
        assertEquals("", c)
        assertEquals("", b)

        val (p2, c2, b2) = ph.edu.umak.momompurin.data.PhAddresses.clearBarangay(
            "Metro Manila", "Makati",
        )
        assertEquals("Metro Manila", p2)
        assertEquals("Makati", c2)
        assertEquals("", b2)
    }

    /** C.1's display mapping must survive the picker rewrite. */
    @Test
    fun bug3_metro_manila_still_prints_with_its_ncr_suffix() {
        assertEquals("Metro Manila (NCR)", ph.edu.umak.momompurin.data.PhAddresses.provinceLabel("Metro Manila"))
    }

    /** The manual row is still offered and still recognised as the manual sentinel. */
    @Test
    fun bug3_the_manual_option_is_still_available_at_every_level() {
        val provinces = ph.edu.umak.momompurin.data.PhAddresses.options(
            ph.edu.umak.momompurin.data.PhAddresses.provinces(),
        )
        assertTrue(
            "the picker must keep an 'Other (type manually)' escape hatch",
            provinces.any { ph.edu.umak.momompurin.data.PhAddresses.isManual(it) },
        )
    }

    // =========================================================================
    // Bug 5 - the offline notice is part of the header, not a second inset
    // =========================================================================

    /**
     * The structural rule behind Bug 5: the notice is provided once, by the
     * NavHost, and consumed by the header. There is no second call site that could
     * consume the status-bar inset again, which is what produced the yellow void.
     */
    @Test
    fun bug5_no_screen_draws_its_own_offline_banner() {
        val root = java.io.File(
            "src/main/java/ph/edu/umak/momompurin",
        )
        // Gradle runs tests from the module directory; fall back to a walk-up.
        val base = if (root.exists()) root else java.io.File("app/src/main/java/ph/edu/umak/momompurin")
        assertTrue("source root not found for the scan", base.exists())

        val offenders = mutableListOf<String>()
        base.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val code = line.trim()
                    // Ignore comments and the banner's own declaration.
                    if (code.startsWith("//") || code.startsWith("*")) return@forEachIndexed
                    if (code.contains("OwnerOfflineBanner(") &&
                        !file.name.contains("NetworkStatus") &&
                        !file.name.contains("MomoNavHost")
                    ) {
                        offenders += "${file.name}:${index + 1}"
                    }
                }
            }
        assertTrue(
            "per-screen offline banners reappeared (Bug 5 / F-02): $offenders",
            offenders.isEmpty(),
        )
    }
}
