package ph.edu.umak.momompurin.data

import ph.edu.umak.momompurin.data.entity.ActivityLogEntry
import ph.edu.umak.momompurin.data.entity.Cancellation
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.Fulfillment
import ph.edu.umak.momompurin.data.entity.Invoice
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.data.entity.OrderLine
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.Payment
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.data.entity.ShippingStatus

/**
 * Every order transition the pivot defines, in one place.
 *
 * Each function states the criterion it implements and returns the input dataset
 * UNCHANGED on a refusal, so "and change nothing" / "and place no order" hold
 * structurally rather than by the caller remembering to unwind.
 *
 * The invariant that ties the file together:
 *
 *     Available = On hand (Physical) - Reserved
 *
 *   Placing an order      reserved += qty        (A-15 c7)
 *   Cancelling an order   reserved -= qty        (A-04 c7, A-16 c6)  exactly once
 *   Dispatching an order  physical -= qty AND reserved -= qty        (A-07 c9)
 *
 * Dispatch dropping BOTH sides is what stops Available from double-subtracting;
 * A-07 c9 pins it with CHW 16 / 4 / 12 -> 14 / 2 / 12.
 */
object OrderOps {

    /**
     * Every transition result carries the dataset it produced.
     *
     * A refusal carries its INPUT unchanged, so the store can swap unconditionally
     * and "and change nothing" is impossible to get wrong at a call site. The
     * separate [Succeeded] marker is what tells the store whether to report
     * success, since a refusal's data must not be distinguished by comparing it.
     */
    interface HasData {
        val data: MomoData
    }

    // ---------------------------------------------------------------------------
    // User-facing strings (quoted verbatim from the workbook)
    // ---------------------------------------------------------------------------

    /** A-04 c5. */
    const val INVALID_SHIPPING_FEE = "Enter a valid shipping fee."

    /**
     * Patch 3 (A-03): why a held order's shipping fee is locked at P0.00.
     *
     * The helper text the owner's invoice card prints, and the reasoning the ops
     * layer enforces: a `Hold For Combined Shipping` parcel has no freight of its
     * own - the cost belongs to the combined parcel A-06 creates later. Charging
     * freight now would bill the customer for a box that does not exist yet, and
     * would then have to be charged AGAIN when the shipment is combined.
     */
    const val HELD_SHIPPING_FEE_LOCKED =
        "Shipping fee is set once held orders are combined or cleared for shipping."

    /** A-04 c7. */
    const val ORDER_CANCELLED = "Order cancelled. Reserved stock returned."

    /**
     * Bug 2 (real-phone report): a cancelled order cannot take a payment.
     *
     * Stated as an instruction rather than a bare refusal, because the tester's
     * next question is always "so how do I collect this money?" - the answer is
     * to restore the order first, and the sentence says so.
     */
    const val CANCELLED_TAKES_NO_PAYMENT =
        "Cancelled orders cannot accept payments. Restore the order first."

    /** A-04 c8. */
    const val CANNOT_CANCEL_PACKED = "Packed orders cannot be cancelled."

    /** A-04 c8 / A-05 c6 / A-16 c7. */
    const val CANNOT_CANCEL_PAID = "Paid orders cannot be cancelled."

    /** A-04 c9. */
    const val ORDER_RESTORED = "Order restored."

    /** A-05 c3. */
    const val INVALID_PAYMENT_AMOUNT =
        "Enter an amount greater than zero and not above the balance."

    /** A-05 c4. */
    const val MARK_AS_PAID_PROMPT = "This order will be marked as Paid."

    /** A-05 c2 / c5. */
    const val PAYMENT_RECORDED = "Payment recorded."

    /** A-06 c7. */
    const val MIXED_CUSTOMERS = "Select held orders for the same customer."

    /** A-07 c5. */
    const val CHECK_ALL_ITEMS = "Check all items before marking the order as packed."

    /**
     * A-07 (UAT fix, item 2): the money condition for packing.
     *
     * Stated as a positive instruction rather than a bare refusal, because the
     * owner's next question is always "so what do I do with this order?" - the
     * answer is to collect the balance on Record payment first.
     */
    const val NOT_FULLY_PAID_CANNOT_PACK =
        "Only fully paid orders can be packed. Record the balance first."

    /** A-07 c8. */
    const val ENTER_TRACKING_FIRST = "Enter the tracking number first."

    /** A-07 c6. */
    const val SHIPPING_COPIED = "Shipping details copied."

    /** A-15 c5. */
    const val OFFLINE_CHECKOUT = "Connect to the internet to place your order."

    /** A-15 c2. */
    const val INCOMPLETE_DELIVERY = "Complete the delivery details."

    /** A-15 c7. */
    const val ORDER_PLACED = "Order placed."

    /** A-16 c6. */
    const val CANCEL_NEEDS_REASON = "Enter a reason for cancelling."

    /** A-09 c4. */
    const val REVERSED_RANGE = "The end date cannot be earlier than the start date."

    /** A-15 c6. */
    fun notEnoughStock(sku: String) = "Not enough stock for $sku."

    // ---------------------------------------------------------------------------
    // A-15: placing an order (checkout)
    // ---------------------------------------------------------------------------

    /** A-15 c1: what Checkout submits. */
    data class CheckoutRequest(
        val customerEmail: String,
        val lines: List<ph.edu.umak.momompurin.data.entity.CartLine>,
        val fulfillment: Fulfillment,
        val recipientName: String,
        val mobile: String,
        val province: String,
        val city: String,
        val barangay: String,
        val addressLine: String,
    )

    sealed interface PlaceResult : HasData {
        data class Placed(
            override val data: MomoData,
            val orderId: String,
            val cart: List<ph.edu.umak.momompurin.data.entity.CartLine>,
        ) : PlaceResult

        data class Refused(val message: String, override val data: MomoData) : PlaceResult
    }

    /**
     * A-15 c6-c7: one atomic availability check, then reserve, then clear ONLY the
     * ordered rows from the cart, then create a pay-later order.
     *
     * "Atomic" is structural here: the check and the reservation are computed
     * against the SAME snapshot and swapped in as one value, so there is no
     * interleaving in which two checkouts could both pass the check and then both
     * reserve the last unit.
     */
    fun placeOrder(
        data: MomoData,
        request: CheckoutRequest,
        offline: Boolean,
        now: Long = System.currentTimeMillis(),
    ): PlaceResult {
        // A-15 c5: offline blocks the write and names the remedy.
        if (offline) return PlaceResult.Refused(OFFLINE_CHECKOUT, data)

        // A-15 c2: the delivery block must be complete.
        val delivery = listOf(
            request.recipientName, request.mobile, request.province,
            request.city, request.barangay, request.addressLine,
        )
        if (delivery.any { it.isBlank() }) {
            return PlaceResult.Refused(INCOMPLETE_DELIVERY, data)
        }
        if (request.lines.isEmpty()) return PlaceResult.Refused(CartOps.EMPTY_CART, data)

        // A-15 c6: the availability check, BEFORE any write.
        for (line in request.lines) {
            val product = data.product(line.sku)
                ?: return PlaceResult.Refused(notEnoughStock(line.sku), data)
            if (line.qty > product.available) {
                return PlaceResult.Refused(notEnoughStock(line.sku), data)
            }
        }

        val orderId = nextOrderId(data)
        val lines = request.lines.mapIndexed { index, cartLine ->
            val product = data.product(cartLine.sku)!!
            OrderLine(
                lineId = "%s-%d".format(orderId, index + 1),
                orderId = orderId,
                sku = product.sku,
                productName = product.name,
                variantName = variantName(product),
                qty = cartLine.qty,
                unitPrice = product.price,
            )
        }
        val subtotal = lines.sumOf { it.lineTotal }

        // A-15 c7: reserve by ADDING the ordered quantity to Reserved. Available
        // falls by the same amount because it is derived (physical - reserved).
        val reserved = request.lines.groupBy { it.sku }
            .mapValues { (_, rows) -> rows.sumOf { it.qty } }
        val products = data.products.map { product ->
            val qty = reserved[product.sku] ?: 0
            if (qty == 0) product else product.copy(reserved = product.reserved + qty)
        }

        val order = Order(
            orderId = orderId,
            customerEmail = request.customerEmail.trim().lowercase(),
            placedAt = now,
            // A-15 c7: lands in the owner's Incoming Orders as Order Placed.
            stage = OrderStage.ORDER_PLACED,
            paymentStatus = PaymentStatus.UNPAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            fulfillment = request.fulfillment,
            // A-15 c1: NO shipping fee is added at checkout.
            itemSubtotal = subtotal,
            shippingFee = 0L,
            amountPaid = 0L,
            recipientName = request.recipientName.trim(),
            mobile = request.mobile.trim(),
            province = request.province.trim(),
            city = request.city.trim(),
            barangay = request.barangay.trim(),
            addressLine = request.addressLine.trim(),
        )

        // A-15 c7: clear ONLY the checked-out items; unticked rows stay put.
        val orderedSkus = request.lines.map { it.sku }.toSet()
        val email = request.customerEmail.trim().lowercase()
        val remainingCart = data.cart(email).filterNot { it.sku in orderedSkus }

        return PlaceResult.Placed(
            data = data.copy(
                products = products,
                orders = data.orders + order,
                orderLines = data.orderLines + lines,
                carts = data.carts + (email to remainingCart),
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Placed $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "${request.lines.sumOf { it.qty }} item(s), pay-later",
                    now = now,
                ),
            ),
            orderId = orderId,
            cart = remainingCart,
        )
    }

    /** The next free ORD- number, four digits, past anything already used. */
    fun nextOrderId(data: MomoData): String {
        val highest = data.orders
            .mapNotNull { it.orderId.removePrefix("ORD-").toIntOrNull() }
            .maxOrNull() ?: 1000
        return "ORD-%04d".format(highest + 1)
    }

    /**
     * A-13 c4 / A-14 c11: the customer-visible variant name, derived from the SKU
     * suffix (`MPBOW-PK` -> "Pink", `MPBOW-LV` -> "Lavender") so there is no
     * second stored field to drift away from the code.
     */
    fun variantName(product: Product): String = when {
        product.parentSku == null -> ""
        product.sku.endsWith("-PK", ignoreCase = true) -> "Pink"
        product.sku.endsWith("-LV", ignoreCase = true) -> "Lavender"
        else -> ""
    }

    // ---------------------------------------------------------------------------
    // A-04: Confirm & Send Invoice
    // ---------------------------------------------------------------------------

    sealed interface InvoiceResult : HasData {
        data class Sent(override val data: MomoData, val invoiceNo: String) : InvoiceResult
        data class Refused(val message: String, override val data: MomoData) : InvoiceResult
    }

    /**
     * Patch 3 (A-03): is this order's shipping fee locked at P0.00 right now?
     *
     * True for an incoming order the buyer chose `Hold For Combined Shipping` on.
     * The lock LIFTS the moment the parcel stops being an individual hold: A-06's
     * [combine] rewrites `fulfillment` to [Fulfillment.SHIP_NOW], so the combined
     * orders - which are precisely the ones whose freight now exists - accept a
     * fee again through the normal path.
     *
     * Note this is keyed on [Order.isHeld] rather than on the stage, so clearing
     * the order for shipping by combining it is what unlocks the field; there is no
     * separate "unlock" flag that could drift out of step with the hold.
     */
    fun shippingFeeLocked(order: Order): Boolean = order.isHeld

    /**
     * A-04 c5-c6: send the invoice, set Waiting for Payment, start the 24-hour
     * window.
     *
     * The shipping fee is optional and numeric. A blank field means P0.00 (the
     * held-order case), a non-numeric one is refused with the criterion's message,
     * and the total becomes item subtotal + fee.
     */
    fun sendInvoice(
        data: MomoData,
        orderId: String,
        shippingFeeText: String,
        now: Long = System.currentTimeMillis(),
    ): InvoiceResult {
        val order = data.order(orderId)
            ?: return InvoiceResult.Refused("That order no longer exists.", data)
        if (order.stage != OrderStage.ORDER_PLACED) {
            return InvoiceResult.Refused("An invoice can only be sent for a new order.", data)
        }

        val fee = parseFee(shippingFeeText)
            ?: return InvoiceResult.Refused(INVALID_SHIPPING_FEE, data)

        // Patch 3 (A-03): a held order is invoiced at P0.00 of freight, whatever the
        // field says. Enforced HERE as well as by the disabled input, because the
        // field is a hint and this is the gate: a stale screen, a restored order or
        // a future caller must not be able to put freight on a parcel whose
        // combined shipment has not been created yet.
        if (shippingFeeLocked(order) && fee > 0L) {
            return InvoiceResult.Refused(HELD_SHIPPING_FEE_LOCKED, data)
        }

        val invoiceNo = "MP-" + orderId.removePrefix("ORD-")
        // Patch 2: the fee moves `total`, so the status is re-derived with it. An
        // order that had no fee and no payment yet re-derives to Unpaid (no change);
        // the point is that this path can no longer leave a status whose "fully
        // paid" claim the new total contradicts.
        val updated = normalisePayment(
            order.copy(
                stage = OrderStage.WAITING_PAYMENT,
                shippingFee = fee,
                invoiceNo = invoiceNo,
                invoicedAt = now,
                paymentDeadline = now + PAYMENT_WINDOW_MILLIS,
            ),
        )
        return InvoiceResult.Sent(
            data = data.copy(
                orders = data.orders.map { if (it.orderId == orderId) updated else it },
                invoices = data.invoices.filterNot { it.orderId == orderId } +
                    Invoice(invoiceNo, orderId, now, updated.total),
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Sent invoice $invoiceNo",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "shipping fee ${MomoMoneyLabel.of(fee)}",
                    now = now,
                ),
            ),
            invoiceNo = invoiceNo,
        )
    }

    /**
     * A-04 c5: blank means P0.00; anything non-numeric is a refusal.
     *
     * The peso sign, spaces and thousands separators are tolerated because a
     * tester may retype what the field already shows.
     */
    fun parseFee(text: String): Long? {
        val cleaned = text.trim()
            .removePrefix("\u20B1")
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return 0L
        return cleaned.toBigDecimalOrNull()
            ?.takeIf { it >= java.math.BigDecimal.ZERO }
            ?.movePointRight(2)
            ?.setScale(0, java.math.RoundingMode.HALF_UP)
            ?.toLong()
    }

    /** A-04 c6: the 24-hour payment window. */
    const val PAYMENT_WINDOW_MILLIS = 24L * 60L * 60L * 1000L

    // ---------------------------------------------------------------------------
    // A-04 c7-c9 / A-16 c6-c8: cancel and restore
    // ---------------------------------------------------------------------------

    sealed interface CancelResult : HasData {
        data class Cancelled(override val data: MomoData, val message: String) : CancelResult
        data class Refused(val message: String, override val data: MomoData) : CancelResult
    }

    /**
     * Cancel an unpaid order and return its reserved stock EXACTLY once.
     *
     * Guards, in the order the criteria list them:
     *  1. A-04 c8: packed -> "Packed orders cannot be cancelled."
     *  2. A-04 c8 / A-05 c6 / A-16 c7: Paid -> "Paid orders cannot be cancelled."
     *  3. A-16 c6: the reason is required.
     *  4. Already cancelled -> refused, so a second tap cannot return stock twice.
     *
     * The Paid check runs BEFORE the packed one so a Paid-and-packed order reports
     * the money reason, which is the one that actually stops the owner.
     */
    fun cancel(
        data: MomoData,
        orderId: String,
        by: CancelledBy,
        reason: String,
        now: Long = System.currentTimeMillis(),
    ): CancelResult {
        val order = data.order(orderId)
            ?: return CancelResult.Refused("That order no longer exists.", data)

        if (order.stage == OrderStage.CANCELLED) {
            return CancelResult.Refused("That order is already cancelled.", data)
        }
        if (order.isPaid) {
            return CancelResult.Refused(CANNOT_CANCEL_PAID, data)
        }
        if (order.packingStatus == PackingStatus.PACKED) {
            return CancelResult.Refused(CANNOT_CANCEL_PACKED, data)
        }
        if (reason.isBlank()) {
            return CancelResult.Refused(CANCEL_NEEDS_REASON, data)
        }

        val released = data.linesFor(orderId).groupBy { it.sku }
            .mapValues { (_, lines) -> lines.sumOf { it.qty } }
        val products = data.products.map { product ->
            val qty = released[product.sku] ?: 0
            if (qty == 0) product
            else product.copy(reserved = (product.reserved - qty).coerceAtLeast(0))
        }

        val cancelled = order.copy(
            stage = OrderStage.CANCELLED,
            cancellation = Cancellation(by = by, reason = reason.trim(), at = now),
        )
        return CancelResult.Cancelled(
            data = data.copy(
                products = products,
                orders = data.orders.map { if (it.orderId == orderId) cancelled else it },
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Cancelled $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "by ${by.label}: ${reason.trim()}",
                    now = now,
                ),
            ),
            message = ORDER_CANCELLED,
        )
    }

    /**
     * A-04 c9: restore a cancelled order and reserve its stock again.
     *
     * The order returns to Order Placed - the state it held before the invoice -
     * and its reserved quantities are taken again. Restoring is REFUSED when the
     * stock is no longer there, because re-reserving past availability is the
     * oversell A-15 c6 forbids.
     */
    fun restore(
        data: MomoData,
        orderId: String,
        now: Long = System.currentTimeMillis(),
    ): CancelResult {
        val order = data.order(orderId)
            ?: return CancelResult.Refused("That order no longer exists.", data)
        if (order.stage != OrderStage.CANCELLED) {
            return CancelResult.Refused("Only a cancelled order can be restored.", data)
        }

        val needed = data.linesFor(orderId).groupBy { it.sku }
            .mapValues { (_, lines) -> lines.sumOf { it.qty } }
        for ((sku, qty) in needed) {
            val product = data.product(sku) ?: continue
            if (qty > product.available) {
                return CancelResult.Refused(notEnoughStock(sku), data)
            }
        }

        val products = data.products.map { product ->
            val qty = needed[product.sku] ?: 0
            if (qty == 0) product else product.copy(reserved = product.reserved + qty)
        }
        val restored = normalisePayment(
            order.copy(
                stage = OrderStage.ORDER_PLACED,
                cancellation = null,
                invoiceNo = "",
                invoicedAt = null,
                paymentDeadline = null,
                shippingFee = 0L,
            ),
        )
        return CancelResult.Cancelled(
            data = data.copy(
                products = products,
                orders = data.orders.map { if (it.orderId == orderId) restored else it },
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Restored $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "back to Order Placed, stock reserved again",
                    now = now,
                ),
            ),
            message = ORDER_RESTORED,
        )
    }

    // ---------------------------------------------------------------------------
    // A-05: record a payment, the Paid lock, and Unmark Paid
    // ---------------------------------------------------------------------------

    sealed interface PaymentResult : HasData {
        /**
         * [needsPaidConfirmation] is true when the amount clears the balance:
         * A-05 c4 requires the "This order will be marked as Paid." dialog first.
         */
        data class Recorded(
            override val data: MomoData,
            val message: String,
            val needsPaidConfirmation: Boolean,
        ) : PaymentResult

        data class Refused(val message: String, override val data: MomoData) : PaymentResult
    }

    /**
     * A validated-but-unwritten payment: what the "Mark as Paid" dialog is deciding
     * about.
     *
     * The split exists because of the A-05 defect: the screen used to WRITE the
     * payment and then ask for confirmation, so tapping `Close` on the dialog left
     * the money recorded. [previewPayment] performs every check [recordPayment]
     * performs and writes nothing, so the dialog can be shown over an untouched
     * store and `Close` is a true abort.
     */
    data class PaymentPreview(
        val orderId: String,
        val amount: Long,
        val method: String,
        /** The order as it will read once the payment lands. */
        val newAmountPaid: Long,
        val newBalance: Long,
        val newStatus: PaymentStatus,
        /** True when this payment clears the balance, so A-05 c4's dialog applies. */
        val clearsBalance: Boolean,
    )

    /** The outcome of [previewPayment]: a refusal message, or the pending payment. */
    sealed interface PaymentCheck {
        data class Ready(val preview: PaymentPreview) : PaymentCheck
        data class Refused(val message: String) : PaymentCheck
    }

    /**
     * A-05 c1-c4, validation only: no dataset is returned and nothing is written.
     *
     * This is the ONLY place the "is this amount legal" rule lives, so the preview
     * the dialog is shown over and the commit that follows can never disagree about
     * what a valid amount is.
     */
    fun previewPayment(
        data: MomoData,
        orderId: String,
        amountText: String,
        method: String,
    ): PaymentCheck {
        val order = data.order(orderId)
            ?: return PaymentCheck.Refused("That order no longer exists.")
        // Bug 2 (real-phone report): a cancelled order is closed. Its reserved stock
        // has already gone back and no balance is collectable, so a payment booked
        // against it would put money on an order that will never ship. Refused here
        // as well as hidden in the UI, because the dialog could outlive the cancel.
        if (order.stage == OrderStage.CANCELLED) {
            return PaymentCheck.Refused(CANCELLED_TAKES_NO_PAYMENT)
        }
        val cents = MomoMoneyLabel.parse(amountText)
            ?: return PaymentCheck.Refused(INVALID_PAYMENT_AMOUNT)
        if (cents <= 0 || cents > order.balance) {
            return PaymentCheck.Refused(INVALID_PAYMENT_AMOUNT)
        }

        val newPaid = order.amountPaid + cents
        val clears = newPaid >= order.total
        return PaymentCheck.Ready(
            PaymentPreview(
                orderId = orderId,
                amount = cents,
                method = method,
                newAmountPaid = newPaid,
                newBalance = (order.total - newPaid).coerceAtLeast(0L),
                newStatus = if (clears) PaymentStatus.PAID else PaymentStatus.PARTIALLY_PAID,
                clearsBalance = clears,
            ),
        )
    }

    /**
     * A-05 c1-c4: save a payment.
     *
     * The amount is validated against the CURRENT balance, never the original
     * total, so coming back after a partial payment cannot overwrite the first
     * one's arithmetic - and the original order total is never rewritten
     * (A-05 c5).
     */
    fun recordPayment(
        data: MomoData,
        orderId: String,
        amountText: String,
        method: String,
        now: Long = System.currentTimeMillis(),
    ): PaymentResult {
        val order = data.order(orderId)
            ?: return PaymentResult.Refused("That order no longer exists.", data)

        // ONE validation path: the same preview the dialog was shown over.
        val preview = when (val check = previewPayment(data, orderId, amountText, method)) {
            is PaymentCheck.Refused -> return PaymentResult.Refused(check.message, data)
            is PaymentCheck.Ready -> check.preview
        }

        val updated = order.copy(
            // A-05 c4: clearing the balance marks it Paid, which locks it.
            paymentStatus = preview.newStatus,
            amountPaid = preview.newAmountPaid,
            // Bonus fix (spotted on ORD-1051): a fully-paid order must not keep
            // reading "Paid" beside "Waiting for Payment". Clearing the balance
            // moves the lifecycle on - to To Pack for a shipment, or to the
            // pickup queue for a held order - and only when it is still waiting,
            // so a packed or shipped order is never pulled backwards.
            stage = stageAfterFullPayment(order, preview.newBalance),
        )
        val paymentNumber = data.payments.count { it.orderId == orderId } + 1
        return PaymentResult.Recorded(
            data = data.copy(
                orders = data.orders.map { if (it.orderId == orderId) updated else it },
                payments = data.payments + Payment(
                    paymentId = "PMT-%s-%d".format(orderId.removePrefix("ORD-"), paymentNumber),
                    orderId = orderId,
                    amount = preview.amount,
                    method = method,
                    paidAt = now,
                ),
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Recorded ${MomoMoneyLabel.of(preview.amount)} on $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "$method, balance ${MomoMoneyLabel.of(updated.balance)}",
                    now = now,
                ),
            ),
            message = PAYMENT_RECORDED,
            needsPaidConfirmation = preview.clearsBalance,
        )
    }

    sealed interface UnmarkResult : HasData {
        data class Applied(override val data: MomoData) : UnmarkResult
        data class Refused(val message: String, override val data: MomoData) : UnmarkResult
    }

    /**
     * Bonus fix (spotted on ORD-1051): the stage a fully-paid order belongs in.
     *
     * A paid order that still reads "Waiting for Payment" is self-contradictory -
     * two badges on one card disagreeing about whether the money arrived. Clearing
     * the balance therefore advances the lifecycle, and ONLY from
     * [OrderStage.WAITING_PAYMENT]:
     *
     *  - a shipment goes to [OrderStage.TO_PACK], because packing is the next thing
     *    that actually has to happen;
     *  - a held order goes to [OrderStage.TO_PACK] as well.
     *
     * ## The held case (Patch 2 - the ORD-1053 root cause)
     *
     * This used to `return order.stage` for a held order, on the theory that the
     * Paid badge alone told the truth while the order waited for A-06's Combine.
     * It did not: the stage badge renders the STAGE, so the card read `Paid` beside
     * `Waiting for Payment` - the exact contradiction the bug report lists, and the
     * reason a tester could not tell whether ORD-1053 had been paid.
     *
     * A paid held order is no longer waiting for the money - it is waiting for the
     * parcel to be combined - so it leaves `Waiting for Payment` like any other
     * fully-paid order. It does NOT enter the packing queue yet, because
     * `MomoData.toPackOrders` already excludes held orders (A-02 c2), so the
     * Dashboard tile and the To Pack tab still do not count it. Its badge reads
     * `Held` (see `MomoBadges.forOrderStage`), which is the queue it is in.
     *
     * Any later stage (packed, shipped, completed, cancelled) is left alone: a
     * payment arriving on a packed order must not rewind it to To Pack.
     */
    fun stageAfterFullPayment(order: Order, newBalance: Long): OrderStage {
        if (newBalance > 0L) return order.stage
        if (order.stage != OrderStage.WAITING_PAYMENT) return order.stage
        // Both a shipment and a hold leave Waiting for Payment here. A held order's
        // `toPackOrders` exclusion is what keeps it out of the packing QUEUE; the
        // stage it leaves behind must still stop naming the money.
        return OrderStage.TO_PACK
    }

    /**
     * Bonus fix, the other direction: unmarking Paid reopens the payment question.
     *
     * Unmark withdraws the Paid CONFIRMATION. The money rows stay, so the balance is
     * still zero and the order reads Partially Paid - which is exactly the state the
     * tester saw as contradictory when the stage said To Pack: the queue said
     * "ready to pack" while the payment said "not confirmed".
     *
     * The decision therefore keys on the STATUS, not the balance: if the order is no
     * longer Paid and it is sitting in To Pack with nothing packed or shipped, it
     * goes back to Waiting for Payment so the owner can re-confirm.
     *
     * [OrderStage.TO_SHIP] is deliberately NOT one of the rewindable stages: an
     * order only reaches it by being packed, and `packingStatus == PACKED` already
     * guards that case - so listing the stage as well would be a second, weaker
     * copy of the same rule that could drift away from the first.
     */
    fun stageAfterUnmark(order: Order, newStatus: PaymentStatus): OrderStage {
        if (newStatus == PaymentStatus.PAID) return order.stage
        if (order.stage != OrderStage.TO_PACK) return order.stage
        if (order.packingStatus == PackingStatus.PACKED) return order.stage
        if (order.shippingStatus != ShippingStatus.NOT_SHIPPED) return order.stage
        return OrderStage.WAITING_PAYMENT
    }
    /** A-05 c6: after dispatch the money is with the courier; the lock stands. */
    const val UNMARK_AFTER_DISPATCH = "A dispatched order can no longer be unmarked as Paid."

    /** A-05 c6: the unmark that succeeded. */
    const val ORDER_UNMARKED = "Order unmarked as Paid."

    /** A-05 c6: the payment voided, and what that means for the balance. */
    const val PAYMENT_VOIDED = "Payment removed."

    /** A-05 c6: Unmark Paid is a pre-dispatch correction only. */
    fun canUnmarkPaid(order: Order): Boolean =
        order.isPaid && order.shippingStatus == ShippingStatus.NOT_SHIPPED

    /**
     * A-05 c6: Unmark Paid, BEFORE dispatch only.
     *
     * "Unmark" means the Paid CONFIRMATION is withdrawn, so the status can never
     * come back as PAID - otherwise unmarking a fully-paid order would re-derive
     * Paid from its own rows and the lock would never release.
     *
     * The money rows are KEPT (they are the record of what was received) and
     * `amountPaid` recomputes as their sum, so Paid and Balance stay honest. Two
     * outcomes:
     *
     *  - rows cover less than the total -> Partially Paid (or Unpaid), balance > 0,
     *    and Record payment accepts a corrected amount immediately;
     *  - rows cover the whole total -> Partially Paid with a zero balance. That is
     *    NOT the old trap, because every row now carries a Void action: removing a
     *    row recomputes Paid / Balance / status from the survivors, which is the
     *    escape the old build lacked.
     *
     * The old form hard-coded PARTIALLY_PAID over a stale `amountPaid`, so unmarking
     * a fully-paid order left it reading `Balance P0.00` + `Partially Paid` with no
     * way to reopen the balance - the exact defect reported on ORD-1051 and MP-1049.
     */
    fun unmarkPaid(
        data: MomoData,
        orderId: String,
        now: Long = System.currentTimeMillis(),
    ): UnmarkResult {
        val order = data.order(orderId)
            ?: return UnmarkResult.Refused("That order no longer exists.", data)
        if (!order.isPaid) {
            return UnmarkResult.Refused("That order is not marked as Paid.", data)
        }
        if (!canUnmarkPaid(order)) {
            return UnmarkResult.Refused(UNMARK_AFTER_DISPATCH, data)
        }
        val recorded = data.paymentsFor(orderId).sumOf { it.amount }
        val newStatus = if (recorded >= order.total) {
            PaymentStatus.PARTIALLY_PAID
        } else {
            statusFor(recorded, order.total)
        }
        val updated = order.copy(
            amountPaid = recorded,
            // Never PAID: the confirmation is what unmark withdraws.
            paymentStatus = newStatus,
            // Bonus fix: the confirmation is withdrawn, so an order that only
            // reached To Pack because it was paid returns to Waiting for Payment.
            stage = stageAfterUnmark(order, newStatus),
        )
        return UnmarkResult.Applied(
            data.copy(
                orders = data.orders.map { if (it.orderId == orderId) updated else it },
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Unmarked Paid on $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "now ${updated.paymentStatus.label}, " +
                        "balance ${MomoMoneyLabel.of(updated.balance)}",
                    now = now,
                ),
            ),
        )
    }

    /**
     * A-05 c6: remove ONE recorded payment row and recompute the order from what is
     * left.
     *
     * This is the second half of the trapped-state fix: unmarking alone cannot help
     * an order whose rows already sum to the full total, because the balance is
     * genuinely zero. The owner needs a way to take back a payment that was entered
     * by mistake, and this is it - the row is dropped, `amountPaid` becomes the sum
     * of the survivors, and the status follows.
     *
     * Refused after dispatch, on the same rule as [unmarkPaid]: once the parcel has
     * left, the money is with the courier and the record must not be rewritten.
     */
    fun voidPayment(
        data: MomoData,
        orderId: String,
        paymentId: String,
        now: Long = System.currentTimeMillis(),
    ): UnmarkResult {
        val order = data.order(orderId)
            ?: return UnmarkResult.Refused("That order no longer exists.", data)
        if (order.shippingStatus != ShippingStatus.NOT_SHIPPED) {
            return UnmarkResult.Refused(UNMARK_AFTER_DISPATCH, data)
        }
        val payment = data.payments.firstOrNull {
            it.paymentId == paymentId && it.orderId == orderId
        } ?: return UnmarkResult.Refused("That payment no longer exists.", data)

        val remaining = data.payments.filterNot { it.paymentId == paymentId }
        val recorded = remaining.filter { it.orderId == orderId }.sumOf { it.amount }
        val updated = order.copy(
            amountPaid = recorded,
            paymentStatus = statusFor(recorded, order.total),
        )
        return UnmarkResult.Applied(
            data.copy(
                payments = remaining,
                orders = data.orders.map { if (it.orderId == orderId) updated else it },
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Removed ${MomoMoneyLabel.of(payment.amount)} on $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "payment voided, now ${updated.paymentStatus.label}, " +
                        "balance ${MomoMoneyLabel.of(updated.balance)}",
                    now = now,
                ),
            ),
        )
    }

    /**
     * The payment status an amount implies.
     *
     * ONE function so `recordPayment`, `unmarkPaid` and `voidPayment` cannot drift:
     * the invariant `Paid <=> amountPaid >= total` holds by construction, and
     * `Unpaid` is reachable again once the last row is voided.
     */
    fun statusFor(amountPaid: Long, total: Long): PaymentStatus = when {
        amountPaid <= 0L -> PaymentStatus.UNPAID
        amountPaid >= total -> PaymentStatus.PAID
        else -> PaymentStatus.PARTIALLY_PAID
    }

    /**
     * Patch 2: re-derive `paymentStatus` from the money on an order being written.
     *
     * ## Why every money-changing write needs this
     *
     * [statusFor] pins the rule at the moment a PAYMENT is recorded, but the total
     * it is compared against is not a constant: sending an invoice sets the shipping
     * fee, editing an unsettled order rewrites the subtotal, and restoring an order
     * clears the fee. Each of those can move `total` across `amountPaid` and turn a
     * fully-paid order into one that owes money - while the stored status still
     * reads `Paid`. That stale `Paid` is then printed next to a non-zero balance,
     * which is the contradiction the report shows.
     *
     * Applying this once, on the write path, means the stored status can never
     * disagree with the arithmetic. The UI derives its badge from the same rule
     * (see `Order.effectivePaymentStatus`) so a row that predates this fix is also
     * displayed correctly, but a write here keeps the DATA honest too.
     */
    fun normalisePayment(order: ph.edu.umak.momompurin.data.entity.Order) =
        order.copy(paymentStatus = statusFor(order.amountPaid, order.total))

    // ---------------------------------------------------------------------------
    // A-06: combine held orders
    // ---------------------------------------------------------------------------

    sealed interface CombineResult : HasData {
        data class Combined(
            override val data: MomoData,
            val shipmentNo: String,
            val total: Long,
        ) : CombineResult

        data class Refused(val message: String, override val data: MomoData) : CombineResult
    }

    /**
     * A-06 c5-c7: combine two or more PAID held orders of the SAME customer.
     *
     * The original orders and their invoices are left untouched - only the
     * `combinedShipmentNo` link is added - so A-06 c6's "still show ORD-1036 and
     * ORD-1037 with their own totals and invoices" holds by construction.
     *
     * UAT fix (item 3): the two things combining used to get wrong.
     *
     *  - The combined orders stayed `HOLD_FOR_COMBINED`, so they sat in `Hold and
     *    combine orders` forever and the owner could combine the same pair again.
     *    Combining is the END of holding, so the hold is cleared here.
     *  - They kept whatever stage they had, which for a seeded hold was `To Pack`
     *    while the payment read `Paid` - correct by luck, not by construction, and
     *    an earlier build moved them to `Waiting for Payment` instead. The parcel
     *    is now boxed and waiting for its courier, so the stage is set explicitly.
     *
     * Together those make "the combined orders leave the hold list and appear in To
     * Pack, Paid" a property of this function rather than of the seed.
     */
    fun combine(
        data: MomoData,
        orderIds: List<String>,
        now: Long = System.currentTimeMillis(),
    ): CombineResult {
        if (orderIds.size < 2) {
            return CombineResult.Refused("Select at least two held orders to combine.", data)
        }
        val orders = orderIds.mapNotNull { data.order(it) }
        if (orders.size != orderIds.size) {
            return CombineResult.Refused("That order no longer exists.", data)
        }

        // A-06 c7: mixed customers, with the criterion's exact message.
        if (orders.map { it.customerEmail.lowercase() }.distinct().size > 1) {
            return CombineResult.Refused(MIXED_CUSTOMERS, data)
        }
        // A-06 c8: an unpaid held order is refused rather than silently skipped.
        if (orders.any { !it.isPaid }) {
            return CombineResult.Refused("Only paid held orders can be combined.", data)
        }
        if (orders.any { !it.isHeld }) {
            return CombineResult.Refused(
                "Only orders held for combined shipping can be combined.", data,
            )
        }
        if (orders.any { it.stage == OrderStage.CANCELLED }) {
            return CombineResult.Refused("A cancelled order cannot be combined.", data)
        }

        val nextNumber = (data.orders
            .mapNotNull { it.combinedShipmentNo?.removePrefix("CS-")?.toIntOrNull() }
            .maxOrNull() ?: 0) + 1
        val shipmentNo = "CS-%04d".format(nextNumber)
        val total = orders.sumOf { it.total }
        return CombineResult.Combined(
            data = data.copy(
                orders = data.orders.map { order ->
                    if (order.orderId in orderIds) {
                        normalisePayment(
                            order.copy(
                                combinedShipmentNo = shipmentNo,
                                // The hold is over: the parcel is packed with its
                                // shipment-mates and leaves the hold queue.
                                fulfillment = Fulfillment.SHIP_NOW,
                                // Only ever FROM a holding stage. An order already in
                                // transit keeps its place - combining must never rewind
                                // a dispatched parcel to the packing table.
                                //
                                // Patch 2: WAITING_PAYMENT is included because every
                                // combinable order is PAID (checked above), so a paid
                                // row sitting there is precisely the contradiction
                                // this fix removes - combining it must clear the stage
                                // too, not preserve it.
                                stage = when (order.stage) {
                                    OrderStage.WAITING_PAYMENT,
                                    OrderStage.TO_PACK,
                                    -> OrderStage.TO_PACK
                                    else -> order.stage
                                },
                            ),
                        )
                    } else {
                        order
                    }
                },
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Combined ${orderIds.joinToString(" + ")}",
                    entityType = "Shipment",
                    entityId = shipmentNo,
                    detail = "total ${MomoMoneyLabel.of(total)} for " +
                        data.customerName(orders.first().customerEmail),
                    now = now,
                ),
            ),
            shipmentNo = shipmentNo,
            total = total,
        )
    }

    // ---------------------------------------------------------------------------
    // A-06 c3 / Patch 4: one combined shipment moves as ONE parcel
    // ---------------------------------------------------------------------------

    /**
     * Patch 4: mark every order in a combined shipment packed.
     *
     * A combined shipment is a single parcel (A-06 c3), so packing it is one act:
     * the owner ticks one checklist and the WHOLE shipment leaves the packing
     * queue. Applying [markPacked] to only the tapped order would leave the card
     * half-packed - one member at `To Ship`, its shipment-mate still at `To Pack` -
     * which is a parcel the shop has physically boxed reading as two different
     * states, and it makes the next tap re-pack a box that is already taped.
     *
     * Each member's own checklist is required, because each order keeps its own
     * lines: [checkedLineIds] is the union the caller gathered from the members it
     * is packing. All-or-nothing - if one member refuses (a balance appeared on it,
     * say), nothing is written, so a shipment can never be left partly advanced.
     */
    fun markShipmentPacked(
        data: MomoData,
        orderIds: List<String>,
        checkedLineIds: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): PackResult {
        if (orderIds.isEmpty()) return PackResult.Refused("That order no longer exists.", data)

        // Patch 4: the scope is EXPANDED here, not trusted from the caller.
        //
        // A call site that passes one member's id - which is the natural thing to do
        // from a detail screen - must still pack the whole parcel. Expanding inside
        // the op means the "one shipment, one parcel" rule holds no matter which
        // member the UI happened to hand over, and there is no way to reach this
        // function with half a shipment.
        val scope = orderIds.flatMap { shipmentScope(data, it) }.distinct()

        // Validate EVERY member against the same snapshot before writing any of
        // them, so a refusal on the last one cannot leave the first ones packed.
        var working = data
        for (orderId in scope) {
            val result = markPacked(working, orderId, checkedLineIds, now)
            if (result is PackResult.Refused) return PackResult.Refused(result.message, data)
            working = result.data
        }
        return PackResult.Packed(working)
    }

    /**
     * Patch 4: apply a shipping update to every order in a combined shipment.
     *
     * The courier and waybill belong to the PARCEL, so recording them once must not
     * leave one member of the shipment un-shipped and its stock un-deducted. Each
     * member runs through [updateShipping], so the A-07 c8 tracking rules and the
     * A-07 c9 stock arithmetic are the same ones a single order gets - this is a
     * fan-out, not a second implementation.
     *
     * The stock effect stays correct because [updateShipping] dispatches on the
     * FIRST transition only: each member deducts its own lines exactly once.
     *
     * All-or-nothing: every member is validated against the same snapshot first, so
     * a malformed waybill on the last one cannot leave the earlier ones dispatched.
     */
    fun updateShipmentShipping(
        data: MomoData,
        orderIds: List<String>,
        courier: String?,
        trackingNo: String?,
        status: ShippingStatus?,
        now: Long = System.currentTimeMillis(),
    ): ShipResult {
        if (orderIds.isEmpty()) return ShipResult.Refused("That order no longer exists.", data)

        // Patch 4: the scope is expanded here for the same reason
        // [markShipmentPacked] does it - a detail screen passes one member's id and
        // the whole parcel must still move.
        val scope = orderIds.flatMap { shipmentScope(data, it) }.distinct()

        var working = data
        for (orderId in scope) {
            val result = updateShipping(working, orderId, courier, trackingNo, status, now)
            if (result is ShipResult.Refused) return ShipResult.Refused(result.message, data)
            working = result.data
        }
        return ShipResult.Updated(working)
    }

    /**
     * Patch 4: the member orders a pack/ship action on [orderId] must cover.
     *
     * Returns the whole combined shipment when the order belongs to one, and the
     * order alone otherwise - so a call site never has to ask whether it is looking
     * at a shipment, and cannot accidentally act on half of one.
     */
    fun shipmentScope(data: MomoData, orderId: String): List<String> {
        val order = data.order(orderId) ?: return listOf(orderId)
        val shipmentNo = order.combinedShipmentNo ?: return listOf(orderId)
        val members = data.shipmentMembers(shipmentNo)
        return if (members.size >= 2) members.map { it.orderId } else listOf(orderId)
    }

    // ---------------------------------------------------------------------------
    // A-07: pack and dispatch
    // ---------------------------------------------------------------------------

    sealed interface PackResult : HasData {
        data class Packed(override val data: MomoData) : PackResult
        data class Refused(val message: String, override val data: MomoData) : PackResult
    }

    /**
     * A-07 c3-c5: mark the order packed, but only when EVERY line is checked.
     *
     * [checkedLineIds] is what the checklist currently holds. An early attempt is
     * refused with the criterion's message and the order stays unpacked.
     *
     * UAT fix (items 2 and 5), two changes:
     *
     *  - the order must be FULLY PAID. A-07's packing step exists to hand a parcel
     *    to a courier, which is work done on money the shop has received; the UAT
     *    run found `Partially Paid` orders sitting in `To Pack`, which is exactly
     *    the state this refusal prevents from becoming a packed parcel.
     *  - success moves the stage to [OrderStage.TO_SHIP], so a packed order leaves
     *    the packing queue and the `Pack order` control goes away with it.
     */
    fun markPacked(
        data: MomoData,
        orderId: String,
        checkedLineIds: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): PackResult {
        val order = data.order(orderId)
            ?: return PackResult.Refused("That order no longer exists.", data)
        // A-07: the money comes first. Checked before the item ticks so a
        // partially-paid order states the real blocker instead of asking the owner
        // to tick items for a packing job that will then be refused.
        if (!order.isPaid) return PackResult.Refused(NOT_FULLY_PAID_CANNOT_PACK, data)
        val lines = data.linesFor(orderId)
        if (lines.isEmpty() || !lines.all { it.lineId in checkedLineIds }) {
            return PackResult.Refused(CHECK_ALL_ITEMS, data)
        }
        if (order.packingStatus == PackingStatus.PACKED) {
            return PackResult.Packed(data)
        }
        val updated = order.copy(
            packingStatus = PackingStatus.PACKED,
            packedAt = now,
            // UAT fix: a packed order is no longer packing work. Anything already
            // at or past TO_SHIP is left alone, so re-saving a packed order cannot
            // pull a dispatched parcel backwards.
            stage = when (order.stage) {
                OrderStage.TO_PACK -> OrderStage.TO_SHIP
                else -> order.stage
            },
        )
        return PackResult.Packed(
            data.copy(
                orders = data.orders.map { if (it.orderId == orderId) updated else it },
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Marked $orderId as packed",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "${lines.sumOf { it.qty }} item(s) checked",
                    now = now,
                ),
            ),
        )
    }

    sealed interface ShipResult : HasData {
        data class Updated(override val data: MomoData) : ShipResult
        data class Refused(val message: String, override val data: MomoData) : ShipResult
    }

    /** A-07 c8: the message a malformed tracking number shows. */
    fun trackingFormatMessage(courier: String): String =
        CourierTrackingValidator.formatMessage(courier)

    /**
     * A-07 c7-c9: set the route, the tracking number and the shipping status, and
     * on first dispatch reduce BOTH physical and reserved by the shipped quantity.
     *
     * A-07 c9's pinned arithmetic: CHW 16 / 4 / 12 dispatching 2 becomes 14 / 2 /
     * 12. Available is unchanged precisely because both sides fall together.
     *
     * A-07 c8's tracking rule has two halves: the field must not be empty, and its
     * SHAPE must match the route (see [CourierTrackingValidator]). Both are checked
     * before any stock moves, so a mistyped waybill cannot dispatch a parcel.
     */
    fun updateShipping(
        data: MomoData,
        orderId: String,
        courier: String?,
        trackingNo: String?,
        status: ShippingStatus?,
        now: Long = System.currentTimeMillis(),
    ): ShipResult {
        val order = data.order(orderId)
            ?: return ShipResult.Refused("That order no longer exists.", data)

        val effectiveCourier = courier?.trim()?.takeIf { it.isNotEmpty() } ?: order.courier
        // A-07 c8: In Transit needs a tracking number. The check reads what is
        // being saved, falling back to what is stored, so a later status change
        // does not force the number to be retyped.
        val effectiveTracking = trackingNo?.trim()?.takeIf { it.isNotEmpty() } ?: order.trackingNo

        // The format check runs whenever the route carries a waybill and a number
        // is present, so a typo is caught on the save that introduces it rather than
        // only at the moment of dispatch.
        if (!CourierTrackingValidator.isPickup(effectiveCourier) &&
            effectiveTracking.isNotBlank()
        ) {
            when (val outcome = CourierTrackingValidator.validate(effectiveCourier, effectiveTracking)) {
                is CourierTrackingValidator.Outcome.Malformed ->
                    return ShipResult.Refused(outcome.message, data)
                else -> Unit
            }
        }

        if (status == ShippingStatus.IN_TRANSIT && effectiveTracking.isBlank()) {
            return ShipResult.Refused(ENTER_TRACKING_FIRST, data)
        }

        // A-07 c9: the stock effect happens on the FIRST dispatch only. Guarding on
        // the previous status keeps a repeated save from subtracting twice.
        val dispatching = status != null &&
            status != ShippingStatus.NOT_SHIPPED &&
            order.shippingStatus == ShippingStatus.NOT_SHIPPED

        val products = if (dispatching) {
            val shipped = data.linesFor(orderId).groupBy { it.sku }
                .mapValues { (_, lines) -> lines.sumOf { it.qty } }
            data.products.map { product ->
                val qty = shipped[product.sku] ?: 0
                if (qty == 0) {
                    product
                } else {
                    product.copy(
                        physical = (product.physical - qty).coerceAtLeast(0),
                        reserved = (product.reserved - qty).coerceAtLeast(0),
                    )
                }
            }
        } else {
            data.products
        }

        val updated = order.copy(
            courier = effectiveCourier,
            trackingNo = effectiveTracking,
            shippingStatus = status ?: order.shippingStatus,
            stage = when {
                status == ShippingStatus.RECEIVED -> OrderStage.COMPLETED
                status != null && status != ShippingStatus.NOT_SHIPPED -> OrderStage.SHIPPED
                else -> order.stage
            },
        )
        return ShipResult.Updated(
            data.copy(
                products = products,
                orders = data.orders.map { if (it.orderId == orderId) updated else it },
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Shipping update on $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = listOfNotNull(
                        updated.courier.takeIf { it.isNotBlank() },
                        updated.trackingNo.takeIf { it.isNotBlank() },
                        status?.label,
                    ).joinToString(" \u00B7 "),
                    now = now,
                ),
            ),
        )
    }

    /** A-07 c6: the four routes the Delivery route dropdown offers. */
    val DELIVERY_ROUTES = listOf("J&T Express", "TikTok Shop", "Lalamove/Grab", "Pickup")

    // ---------------------------------------------------------------------------
    // A-07 c7 + UAT fix (item 5): the CUSTOMER confirms the parcel arrived
    // ---------------------------------------------------------------------------

    /** UAT fix (item 5): the parcel has not left yet, so there is nothing to confirm. */
    const val NOTHING_TO_RECEIVE =
        "This order has not shipped yet, so there is nothing to confirm."

    /** UAT fix (item 5): the confirmation the customer sees after tapping. */
    const val ORDER_RECEIVED_CONFIRMED = "Thanks! Your order is now marked as received."

    /** UAT fix (item 5): the stage a received order must already be past. */
    sealed interface ReceiveResult : HasData {
        data class Received(override val data: MomoData) : ReceiveResult
        data class Refused(val message: String, override val data: MomoData) : ReceiveResult
    }

    /**
     * UAT fix (item 5): the CUSTOMER says the parcel arrived.
     *
     * The UAT compromise recorded in the report: A-07 lets the OWNER set `Received`
     * by hand, but the person who actually knows whether the parcel turned up is
     * the customer, and leaving the last step to the seller means an order can only
     * close when the seller remembers to close it. The customer therefore gets a
     * prominent `Order Received` action - and it is deliberately reachable ONLY
     * from [ShippingStatus.IN_TRANSIT]:
     *
     *  - before dispatch there is no parcel, so a `Received` confirmation would be
     *    a claim about an event that has not happened;
     *  - after the owner has already marked it `Received` there is nothing left to
     *    confirm, and re-confirming would rewrite the completion time.
     *
     * Completing sets BOTH halves: the shipping status and the lifecycle stage, so
     * the badge row and the tab partition cannot disagree about whether the order
     * is finished.
     */
    fun markReceived(
        data: MomoData,
        orderId: String,
        by: CancelledBy,
        now: Long = System.currentTimeMillis(),
    ): ReceiveResult {
        val order = data.order(orderId)
            ?: return ReceiveResult.Refused("That order no longer exists.", data)

        if (order.shippingStatus == ShippingStatus.RECEIVED) {
            return ReceiveResult.Refused("This order is already marked as received.", data)
        }
        if (order.shippingStatus != ShippingStatus.IN_TRANSIT) {
            return ReceiveResult.Refused(NOTHING_TO_RECEIVE, data)
        }
        if (order.stage == OrderStage.CANCELLED) {
            return ReceiveResult.Refused("A cancelled order cannot be received.", data)
        }

        val updated = order.copy(
            shippingStatus = ShippingStatus.RECEIVED,
            stage = OrderStage.COMPLETED,
        )
        return ReceiveResult.Received(
            data.copy(
                orders = data.orders.map { if (it.orderId == orderId) updated else it },
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "${by.label} confirmed receipt of $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "marked Received and Completed",
                    now = now,
                    actor = if (by == CancelledBy.CUSTOMER) {
                        order.customerEmail
                    } else {
                        SeedData.OWNER_EMAIL
                    },
                ),
            ),
        )
    }
    /** A-07 c6: the text Copy shipping details places on the clipboard. */
    fun shippingDetailsText(data: MomoData, orderId: String): String {
        val order = data.order(orderId) ?: return ""
        return buildString {
            appendLine("Order ${order.orderId}")
            appendLine("Recipient ${order.recipientName}")
            appendLine("Mobile ${order.mobile}")
            appendLine(order.addressBlock)
            append("Total ${MomoMoneyLabel.of(order.total)}")
        }
    }

    // ---------------------------------------------------------------------------
    // A-04 c5: edit an unsettled order
    // ---------------------------------------------------------------------------

    sealed interface EditResult : HasData {
        data class Applied(override val data: MomoData) : EditResult
        data class Refused(val message: String, override val data: MomoData) : EditResult
    }

    /**
     * A-04 c5: edit an unsettled (Order Placed) order's quantities.
     *
     * Refused once the order has left Order Placed, and refused when the increase
     * would exceed availability - the edit moves reserved stock, so it obeys the
     * same check as placement.
     */
    fun editQuantities(
        data: MomoData,
        orderId: String,
        quantities: Map<String, Int>,
        now: Long = System.currentTimeMillis(),
    ): EditResult {
        val order = data.order(orderId)
            ?: return EditResult.Refused("That order no longer exists.", data)
        if (order.stage != OrderStage.ORDER_PLACED) {
            return EditResult.Refused("Only an unsettled order can be edited.", data)
        }
        if (order.isPaid) return EditResult.Refused(CANNOT_CANCEL_PAID, data)

        val lines = data.linesFor(orderId)
        val before = lines.groupBy { it.sku }.mapValues { (_, l) -> l.sumOf { it.qty } }
        val after = before.mapValues { (sku, old) -> quantities[sku] ?: old }

        for ((sku, newQty) in after) {
            val delta = newQty - (before[sku] ?: 0)
            if (delta <= 0) continue
            val product = data.product(sku) ?: continue
            if (delta > product.available) return EditResult.Refused(notEnoughStock(sku), data)
        }

        val newLines = lines.map { line ->
            val newQty = after[line.sku] ?: line.qty
            line.copy(qty = newQty)
        }.filter { it.qty > 0 }
        val subtotal = newLines.sumOf { it.lineTotal }

        val products = data.products.map { product ->
            val delta = (after[product.sku] ?: 0) - (before[product.sku] ?: 0)
            if (delta == 0) product
            else product.copy(reserved = (product.reserved + delta).coerceAtLeast(0))
        }
        val updated = normalisePayment(order.copy(itemSubtotal = subtotal))

        return EditResult.Applied(
            data.copy(
                products = products,
                orders = data.orders.map { if (it.orderId == orderId) updated else it },
                orderLines = data.orderLines.filterNot { it.orderId == orderId } + newLines,
                activityLog = data.activityLog + log(
                    entryCount = data.activityLog.size,
                    action = "Edited $orderId",
                    entityType = "Order",
                    entityId = orderId,
                    detail = "subtotal ${MomoMoneyLabel.of(subtotal)}",
                    now = now,
                ),
            ),
        )
    }

    // ---------------------------------------------------------------------------
    // A-08: the owner's private review flag
    // ---------------------------------------------------------------------------

    /**
     * A-08 c4-c5: set or clear the owner-only note.
     *
     * The flag lives on the [ph.edu.umak.momompurin.data.entity.Account] row and
     * NO customer screen reads it, so A-08 c5's "never in the customer's own view"
     * is a property of which screen reads which field rather than a filter that
     * could be forgotten.
     */
    fun setReviewFlag(
        data: MomoData,
        email: String,
        flag: String?,
        now: Long = System.currentTimeMillis(),
    ): MomoData {
        val account = data.account(email) ?: return data
        return data.copy(
            accounts = data.accounts.map {
                if (it.email.equals(email, ignoreCase = true)) {
                    it.copy(reviewFlag = flag?.takeIf { f -> f.isNotBlank() })
                } else {
                    it
                }
            },
            activityLog = data.activityLog + log(
                entryCount = data.activityLog.size,
                action = if (flag.isNullOrBlank()) {
                    "Removed note on ${account.name}"
                } else {
                    "Added note on ${account.name}"
                },
                entityType = "Customer",
                entityId = email,
                detail = flag,
                now = now,
            ),
        )
    }

    // ---------------------------------------------------------------------------
    // A-09 reports: the reversed-range guard
    // ---------------------------------------------------------------------------

    /**
     * A-09 c4-c5: a rejected range keeps the previous report, so this returns null
     * rather than an empty report the caller might render.
     */
    fun rangeError(startAt: Long, endAt: Long): String? =
        if (endAt < startAt) REVERSED_RANGE else null

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Appends a log entry carrying the next LOG- number. */
    private fun log(
        entryCount: Int,
        action: String,
        entityType: String,
        entityId: String,
        detail: String?,
        now: Long,
        actor: String = SeedData.OWNER_EMAIL,
    ) = ActivityLogEntry(
        logId = "LOG-%04d".format(entryCount + 1),
        actor = actor,
        action = action,
        entityType = entityType,
        entityId = entityId,
        detail = detail,
        loggedAt = now,
    )
}

/**
 * Money parsing and formatting for the ops layer.
 *
 * Kept out of the UI formatter so the data layer can build a log line without
 * reaching into the theme package.
 */
object MomoMoneyLabel {
    fun of(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return "\u20B1$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** Reads "280", "₱280.00", "1,200.50" into cents; null when not a number. */
    fun parse(text: String): Long? {
        val cleaned = text.trim()
            .removePrefix("\u20B1")
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.setScale(0, java.math.RoundingMode.HALF_UP)
            ?.toLong()
    }
}
