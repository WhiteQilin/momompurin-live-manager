package ph.edu.umak.momompurin.data

import ph.edu.umak.momompurin.data.entity.Account
import ph.edu.umak.momompurin.data.entity.ActivityLogEntry
import ph.edu.umak.momompurin.data.entity.CartLine
import ph.edu.umak.momompurin.data.entity.Invoice
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.data.entity.OrderLine
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.Payment
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.data.entity.ProductOption
import ph.edu.umak.momompurin.data.entity.Role
import ph.edu.umak.momompurin.data.entity.Setting
import ph.edu.umak.momompurin.data.entity.StockAdjustment

data class ProductStockSummary(
    val physical: Int,
    val reserved: Int,
    val available: Int,
)

/**
 * A snapshot of every row the app holds.
 *
 * Immutable, so a screen that is recomposing while a write lands never renders a
 * half-applied change: the store swaps the whole snapshot in one [MomoStore.update].
 */
data class MomoData(
    val accounts: List<Account> = emptyList(),
    val products: List<Product> = emptyList(),
    val orders: List<Order> = emptyList(),
    val orderLines: List<OrderLine> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val productOptions: List<ProductOption> = emptyList(),
    val stockAdjustments: List<StockAdjustment> = emptyList(),
    val settings: List<Setting> = emptyList(),
    val activityLog: List<ActivityLogEntry> = emptyList(),
    /** A-14: cart rows per signed-in customer email. Nothing here holds stock. */
    val carts: Map<String, List<CartLine>> = emptyMap(),
) {

    // ------------------------------------------------------------- lookups ----

    fun account(email: String): Account? =
        accounts.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }

    fun owner(): Account? = accounts.firstOrNull { it.role == Role.OWNER }

    fun customers(): List<Account> =
        accounts.filter { it.role == Role.CUSTOMER }.sortedBy { it.name }

    fun product(sku: String): Product? =
        products.firstOrNull { it.sku.equals(sku.trim(), ignoreCase = true) }

    fun order(orderId: String): Order? = orders.firstOrNull { it.orderId == orderId }

    fun linesFor(orderId: String): List<OrderLine> =
        orderLines.filter { it.orderId == orderId }

    fun paymentsFor(orderId: String): List<Payment> = payments.filter { it.orderId == orderId }

    fun invoiceFor(orderId: String): Invoice? = invoices.firstOrNull { it.orderId == orderId }

    fun setting(name: String): String? = settings.firstOrNull { it.settingName == name }?.value

    fun cart(email: String): List<CartLine> = carts[email.trim().lowercase()].orEmpty()

    /** A-13 c4: the variants shown under a parent card. */
    fun variantsOf(parentSku: String): List<Product> =
        products.filter { it.parentSku.equals(parentSku, ignoreCase = true) && it.active }

    // ------------------------------------------------------------- products ----

    /**
     * A-13 c1: the customer-visible catalogue.
     *
     * Three exclusions, each with its own reason:
     *  - a deactivated product is hidden (A-03 c7 / A-13);
     *  - a parent is shown as one card while its variants are reached through it
     *    (A-13 c4);
     *  - a FREEBIE (a zero-priced giveaway such as the Freebie Sticker) is not
     *    merchandise - it has no price and no stock, so listing it offers the
     *    customer a P0.00 row that can never be bought.
     *
     * Sorted by name so the grid order is stable between recompositions.
     */
    fun catalogProducts(): List<Product> =
        products.filter { it.active && it.price > 0 }
            .sortedBy { it.name }

    /** A-13 c4: the Shop shows one card per top-level product. */
    fun shopProducts(): List<Product> =
        products.filter { product ->
            product.active && product.parentSku == null && (
                (product.price > 0 && !product.isParent) ||
                    (product.isParent && variantsOf(product.sku).any { it.price > 0 })
                )
        }.sortedBy { it.name }

    /** A-13 c4: the lowest variant price a parent card can quote. */
    fun parentPriceFrom(parentSku: String): Long? =
        variantsOf(parentSku).filter { it.price > 0 }.minOfOrNull { it.price }

    fun parentVariantCount(parentSku: String): Int = variantsOf(parentSku).count { it.price > 0 }

    /** Parent counts are the totals of its active variants; ordinary rows use their own stock. */
    fun stockSummary(product: Product): ProductStockSummary {
        if (!product.isParent) {
            return ProductStockSummary(
                physical = product.physical,
                reserved = product.reserved,
                available = product.available,
            )
        }
        val variants = variantsOf(product.sku)
        return ProductStockSummary(
            physical = variants.sumOf { it.physical },
            reserved = variants.sumOf { it.reserved },
            available = variants.sumOf { it.available },
        )
    }

    /** A-13 c2-c3: match the card or any active child, but return only top-level cards. */
    fun searchCatalog(
        query: String,
        category: String? = null,
        character: String? = null,
    ): List<Product> {
        val q = query.trim()
        return shopProducts().filter { card ->
            val related = if (card.isParent) listOf(card) + variantsOf(card.sku) else listOf(card)
            related.any { candidate ->
                (q.isEmpty() || candidate.name.contains(q, ignoreCase = true) ||
                    candidate.sku.contains(q, ignoreCase = true)) &&
                    (category == null || candidate.category == category) &&
                    (character == null || candidate.`character` == character)
            }
        }
    }

    /**
     * A-13 c3: the category chips.
     *
     * Offered from the managed list (A-10 c4) but INTERSECTED with what the
     * catalogue actually contains, so a chip can never filter to nothing. Without
     * the intersection the "Freebie" option (A-10 c6's own example value) would sit
     * on the Shop as a control that always yields an empty grid.
     */
    fun categories(): List<String> = offeredBy(
        ph.edu.umak.momompurin.data.entity.OptionKind.CATEGORY,
        catalogProducts().mapNotNull { it.category },
    )

    /** A-13 c3: the character chips, intersected with the catalogue. */
    fun characters(): List<String> = offeredBy(
        ph.edu.umak.momompurin.data.entity.OptionKind.CHARACTER,
        catalogProducts().mapNotNull { it.character },
    )

    /** The managed list's own order, limited to values the catalogue has. */
    private fun offeredBy(kind: ph.edu.umak.momompurin.data.entity.OptionKind, present: List<String>): List<String> {
        val inUse = present.map { it.trim().lowercase() }.toSet()
        return productOptions
            .filter { it.kind == kind && it.active && it.value.trim().lowercase() in inUse }
            .sortedBy { it.sortOrder }
            .map { it.value }
    }

    /** A-03 c5 / A-09 c5: every active product's On hand / Reserved / Available. */
    fun stockRows(): List<Product> = products.filter { it.active }.sortedBy { it.sku }

    /**
     * A-02 c3: products at or below the low-stock level.
     *
     * A FREEBIE row is excluded: a P0.00 sticker that is never stocked would sit
     * at "0 available" forever and push a permanent alert the owner cannot act on,
     * which is worse than no alert - it teaches them to ignore the list.
     */
    fun lowStockProducts(): List<Product> =
        products.filter {
            it.active && !it.isParent && it.price > 0 && it.available <= LOW_STOCK_LEVEL
        }.sortedBy { it.available }

    // --------------------------------------------------------------- orders ----

    /** A-04 c1: newest first, which is the order every list reads. */
    fun ordersNewestFirst(): List<Order> = orders.sortedByDescending { it.placedAt }

    fun customerName(email: String): String = account(email)?.name ?: email

    /** A-16 c1: one customer's own orders, newest first. */
    fun ordersFor(email: String): List<Order> =
        orders.filter { it.customerEmail.equals(email.trim(), ignoreCase = true) }
            .sortedByDescending { it.placedAt }

    /**
     * A-16 c4: the customer's held orders (A-16 c6 shows their total).
     *
     * PAID and not cancelled, the same definition [heldOrders] gives the owner.
     * Filtering on `isHeld` alone put a freshly checked-out HOLD_FOR_COMBINED
     * order (which is UNPAID, since payment is arranged off-app) straight into the
     * customer's "Held orders" and its total - while the owner's Held Orders screen
     * and A-06's Combine refused to list it. Two screens, two definitions.
     */
    fun heldOrdersFor(email: String): List<Order> =
        ordersFor(email).filter {
            it.isHeld && it.isPaid && it.stage != OrderStage.CANCELLED
        }

    /** A-16 c8: the customer's Cancelled Orders history. */
    fun cancelledOrdersFor(email: String): List<Order> =
        ordersFor(email).filter { it.stage == OrderStage.CANCELLED }

    /**
     * A-06 c1: held orders are "the PAID orders kept at the customer's request".
     *
     * Paid is part of the definition, not a nicety: an order the customer asked to
     * hold but has not paid for is still waiting for its invoice, and listing it
     * here would offer the owner a Combine that A-06 c8 must refuse.
     */
    fun heldOrders(): List<Order> = orders
        .filter { it.isHeld && it.isPaid && it.stage != OrderStage.CANCELLED }
        .sortedBy { it.placedAt }

    /** A-04 c3: the tab a list is filtered to. */
    fun ordersInStage(stage: OrderStage?): List<Order> =
        ordersNewestFirst().filter { stage == null || it.stage == stage }

    /**
     * A-02 c2's four tiles.
     *
     * They PARTITION nothing except by design, and each predicate is the one its
     * own criterion states:
     *
     *  - Pending orders    = stage Order Placed (the Incoming queue)
     *  - Unpaid or overdue = money still owed on an order that has left Incoming
     *  - Held orders       = Hold For Combined Shipping (A-06)
     *  - To pack           = stage To Pack
     *
     * ORD-1042 is deliberately in both "To pack" and "Unpaid or overdue": packing
     * and payment are independent tracks (A-04 c4), and hiding it from either tile
     * would misreport one of them.
     */
    fun pendingOrders(): List<Order> = ordersInStage(OrderStage.ORDER_PLACED)

    fun unpaidOrOverdue(): List<Order> = ordersNewestFirst().filter {
        it.balance > 0 && it.stage != OrderStage.ORDER_PLACED && it.stage != OrderStage.CANCELLED
    }

    /**
     * A-02 c2's "To pack" tile: work that is ready to pack.
     *
     * A held order is deliberately NOT here. It was bought with Hold For Combined
     * Shipping (A-15 c3), so it waits for A-06's Combine rather than for the
     * packing table - counting it would report packing work the owner cannot start.
     */
    fun toPackOrders(): List<Order> =
        ordersInStage(OrderStage.TO_PACK).filterNot { it.isHeld }

    /** A-04 c3: the "Incoming" tab, in the same shape as the tile. */
    fun incomingOrders(): List<Order> = pendingOrders()

    /** A-04 c3: search by order number or customer name. */
    fun searchOrders(query: String): List<Order> {
        val q = query.trim()
        if (q.isEmpty()) return ordersNewestFirst()
        return ordersNewestFirst().filter {
            it.orderId.contains(q, ignoreCase = true) ||
                customerName(it.customerEmail).contains(q, ignoreCase = true)
        }
    }

    /** A-04 c10 / A-16 c8: the orders cancelled by either party. */
    fun cancelledOrders(): List<Order> =
        ordersNewestFirst().filter { it.stage == OrderStage.CANCELLED }

    // --------------------------------------------------------------- reports ---

    /**
     * A-09 c1 / c3: orders PLACED inside the range. Sales is the full value of
     * those orders including unpaid ones - A-09 c3's "Sales include unpaid orders"
     * is the criterion, so this is the sum of `total`, not of payments received.
     */
    fun salesReport(startAt: Long, endAt: Long): SalesReport {
        val inRange = orders.filter {
            it.stage != OrderStage.CANCELLED && it.placedAt in startAt..endAt
        }
        val ids = inRange.map { it.orderId }.toSet()
        return SalesReport(
            orderCount = inRange.size,
            sales = inRange.sumOf { it.total },
            paymentsReceived = payments.filter { it.orderId in ids }.sumOf { it.amount },
            outstanding = inRange.sumOf { it.balance },
        )
    }

    data class SalesReport(
        val orderCount: Int,
        val sales: Long,
        val paymentsReceived: Long,
        val outstanding: Long,
    )

    /** A-02 c1: Today's sales - the full value of today's orders (A-02 c2). */
    fun todaysSales(now: Long): Long {
        val startOfDay = startOfDay(now)
        return orders.filter { it.stage != OrderStage.CANCELLED && it.placedAt >= startOfDay }
            .sumOf { it.total }
    }

    fun todaysOrderCount(now: Long): Int {
        val startOfDay = startOfDay(now)
        return orders.count { it.stage != OrderStage.CANCELLED && it.placedAt >= startOfDay }
    }

    private fun startOfDay(now: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ------------------------------------------------------- A-06 advisory -----

    /**
     * A-06 c2-c4: the 1-week advisory.
     *
     * It shows only when BOTH hold: the order contains an ELECTRONIC item and it
     * has been held for at least seven days. ORD-1036 (10 days, no electronic)
     * must show nothing however long it sits; the under-seven-day electronic case
     * must show nothing yet.
     */
    fun showsWeekAdvisory(order: Order, now: Long): Boolean {
        // A-06 c2's advisory is about a PAID held order sitting too long: the
        // reminder exists because the customer has already paid for a unit the shop
        // is storing, so an unpaid hold is not the situation it warns about.
        if (!order.isHeld || !order.isPaid) return false
        if (order.heldDays(now) < ADVISORY_DAYS) return false
        return linesFor(order.orderId).any { product(it.sku)?.isElectronic == true }
    }

    /** A-06 c6: held orders grouped by customer, for the combine selection. */
    fun heldOrdersByCustomer(): Map<String, List<Order>> =
        heldOrders().groupBy { it.customerEmail }

    companion object {
        /** A-02 c3 / A-13 c5: at or below this, a product is "Low Stock". */
        const val LOW_STOCK_LEVEL = 2

        /** A-06 c2: the advisory appears at 7 whole days held. */
        const val ADVISORY_DAYS = 7
    }
}
