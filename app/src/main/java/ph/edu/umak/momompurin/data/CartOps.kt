package ph.edu.umak.momompurin.data

import ph.edu.umak.momompurin.data.entity.CartLine
import ph.edu.umak.momompurin.data.entity.Product

/**
 * The cart rules of A-14.
 *
 * The pivot's defining constraint is A-14 c1: **adding to the cart does not lock
 * or reserve stock.** There is therefore no reserve call anywhere in this file -
 * the only write is the cart's own row list - and browsing can never move a
 * product's `reserved` figure. That absence is deliberate and `CartOpsTest`
 * asserts it by reading the product back after every add.
 */
object CartOps {

    /** A-14 c1: what the customer is told when an item lands. */
    const val ADDED_TO_CART = "Added to cart."

    /** A-14 c5: a row whose product ran out while it sat in the cart. */
    const val SOLD_OUT = "Sold Out"

    /** A-14 c7: nothing left in the cart. */
    const val EMPTY_CART = "Your cart is empty."

    /**
     * A-14 c4 / c6: the short-stock message.
     *
     * The criteria spell two shapes and both are load-bearing:
     * `Insufficient stock (Only N left).` is the quantity-control message (A-14 c6),
     * and A-14 c6's own GWT shows `Only 1 left` / `Only 2 left` with the same
     * parenthesis. One builder keeps them identical.
     */
    fun insufficientStock(available: Int): String = "Insufficient stock (Only $available left)."

    /**
     * A-14 c1: add one unit, or bump an existing row.
     *
     * The returned cart is a pure value - no product is touched - so the caller
     * cannot accidentally reserve stock by adding to the cart.
     */
    fun add(cart: List<CartLine>, sku: String, available: Int): List<CartLine> {
        if (available <= 0) return cart
        val index = cart.indexOfFirst { it.sku == sku }
        if (index < 0) return cart + CartLine(sku = sku, qty = 1, selected = true)
        val existing = cart[index]
        // A-14 c4: the cap is the LIVE available count, not the count at add time.
        if (existing.qty >= available) return cart
        return cart.toMutableList().also {
            it[index] = existing.copy(qty = existing.qty + 1)
        }
    }

    /**
     * A-14 c4: raise one row's quantity by one, refusing past `available`.
     *
     * Returns the new cart plus the message to show, or null for a silent success
     * where the caller only needs the cart.
     */
    data class QuantityChange(
        val cart: List<CartLine>,
        val message: String? = null,
    )

    fun increase(cart: List<CartLine>, sku: String, available: Int): QuantityChange {
        val index = cart.indexOfFirst { it.sku == sku }
        if (index < 0) return QuantityChange(cart)
        val existing = cart[index]
        if (existing.qty >= available) {
            // A-14 c6: the row keeps its quantity and states why.
            return QuantityChange(cart, insufficientStock(available))
        }
        return QuantityChange(
            cart.toMutableList().also { it[index] = existing.copy(qty = existing.qty + 1) }
        )
    }

    /** A-14 c4: "-" never goes below one; Remove is the way out of the cart. */
    fun decrease(cart: List<CartLine>, sku: String): List<CartLine> {
        val index = cart.indexOfFirst { it.sku == sku }
        if (index < 0) return cart
        val existing = cart[index]
        if (existing.qty <= 1) return cart
        return cart.toMutableList().also { it[index] = existing.copy(qty = existing.qty - 1) }
    }

    /** A-14 c6: Remove takes the row out entirely and the subtotal follows. */
    fun remove(cart: List<CartLine>, sku: String): List<CartLine> = cart.filterNot { it.sku == sku }

    /** A-14 c2: the checkbox. */
    fun setSelected(cart: List<CartLine>, sku: String, selected: Boolean): List<CartLine> =
        cart.map { if (it.sku == sku) it.copy(selected = selected) else it }

    /**
     * A-14 c5: refresh every row against LIVE stock.
     *
     * Two things happen to a row that can no longer be bought:
     *  - a product whose available count reached zero reads "Sold Out",
     *    unticks itself and drops out of the subtotal;
     *  - a row whose quantity exceeds what is left keeps its quantity but carries
     *    the short-stock message, because A-14 c6 requires the customer to see
     *    `Insufficient stock (Only 2 left).` rather than silently losing units.
     */
    data class RefreshedRow(
        val line: CartLine,
        val product: Product?,
        /** A-14 c5: the row cannot be checked out at all. */
        val soldOut: Boolean,
        /** A-14 c4/c6: the row's quantity exceeds the live available count. */
        val shortOnStock: Boolean,
        val available: Int,
    ) {
        val unitPrice: Long get() = product?.price ?: 0L
        val lineTotal: Long get() = unitPrice * line.qty

        /** The label under the quantity control, e.g. "1 available" or "Sold Out". */
        val stockLabel: String
            get() = when {
                soldOut -> SOLD_OUT
                else -> "$available available"
            }

        /** A-14 c4/c6: the message shown on the row, when there is one. */
        val stockMessage: String?
            get() = if (shortOnStock && !soldOut) CartOps.insufficientStock(available) else null

        /** A-14 c2/c5: only a ticked, in-stock, sufficient row is counted and carried. */
        val contributes: Boolean get() = !soldOut && line.selected && !shortOnStock
    }

    fun refresh(data: MomoData, cart: List<CartLine>): List<RefreshedRow> = cart.map { line ->
        val product = data.product(line.sku)
        val available = product?.available ?: 0
        val purchasable = product != null && product.isPurchasable
        RefreshedRow(
            line = line,
            product = product,
            soldOut = !purchasable || available <= 0,
            shortOnStock = purchasable && available > 0 && line.qty > available,
            available = available,
        )
    }

    /**
     * A-14 c5: the rows are auto-unticked so a Sold Out row cannot be carried
     * into Checkout even if it was ticked before the stock ran out.
     */
    fun normaliseSelection(data: MomoData, cart: List<CartLine>): List<CartLine> {
        val rows = refresh(data, cart)
        return rows.map { row ->
            if (row.contributes || (!row.soldOut && !row.shortOnStock)) row.line
            else row.line.copy(selected = false)
        }
    }

    /**
     * A-14 c2 / c3: the live subtotal over the rows that count.
     *
     * A row that is Sold Out or short on stock does not contribute, which is what
     * "the subtotal excludes a Sold Out row" means in A-14 c5.
     */
    fun subtotal(rows: List<RefreshedRow>): Long =
        rows.filter { it.contributes }.sumOf { it.lineTotal }

    /** The rows Checkout receives: A-14 c3 "only the ticked items". */
    fun selectedLines(rows: List<RefreshedRow>): List<CartLine> =
        rows.filter { it.contributes }.map { it.line }

    /** A-14 c3: the Checkout button is disabled on an empty (or wholly unusable) cart. */
    fun canCheckout(rows: List<RefreshedRow>): Boolean =
        rows.isNotEmpty() && rows.any { it.contributes }

    /** A-14 c7: the empty-state flag. */
    fun isEmpty(cart: List<CartLine>): Boolean = cart.isEmpty()
}
