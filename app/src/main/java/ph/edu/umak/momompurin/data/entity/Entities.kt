package ph.edu.umak.momompurin.data.entity

/**
 * The pivot data model: Momompurin Online Ordering and Inventory Management.
 *
 * Reshaped for `USER STORY-v4.19.11.2026.xlsx` (A-01..A-17). The live-selling
 * vocabulary the earlier build carried - Claimed, Kept, Paid-and-Kept,
 * Paid-with-Balance, live sessions - is GONE, because the pivot replaces live
 * claim entry with customer self-service ordering:
 *
 *  - stock is held by an ORDER the customer placed, not by a claim an owner typed;
 *  - payment is arranged off-app after the owner sends the invoice (pay-later),
 *    so there is no payment reference field anywhere;
 *  - a Paid order is a LOCK, not a fourth payment shade.
 *
 * Money is [Long] cents throughout. P880.00 / P600.00 / P280.00 must reconcile
 * exactly and binary floating point cannot promise that.
 */

// ---------------------------------------------------------------------------
// ACCOUNT  (A-01, A-11, A-12, A-17)
// ---------------------------------------------------------------------------

enum class Role(val label: String) {
    OWNER("Owner"),
    CUSTOMER("Customer"),
}

/**
 * A sign-in identity. Customers and the owner share ONE Sign In screen (A-01 c1,
 * A-12 c1); the account found by the email decides the mode, so there is no role
 * control anywhere in the UI (A-01 c3, A-11 c7).
 *
 * [passwordDigest] is a SHA-256 hex digest, never the plaintext. The threat here
 * is a local file reader on a handset the owner controls, not an offline cracking
 * rig; a KDF would misrepresent the app as a credential vault when the real
 * control is the sign-out that clears the session.
 */
data class Account(
    val email: String,
    val role: Role,
    val passwordDigest: String,
    val name: String,
    /** Messenger and/or TikTok handle, e.g. `@mikat` (A-11 c1). */
    val handle: String = "",
    /** 11-digit PH mobile, stored as the digits the customer typed (A-11 c4). */
    val mobile: String = "",
    val province: String = "",
    val city: String = "",
    val barangay: String = "",
    val addressLine: String = "",
    /** Owner-only private note; never rendered in Customer Mode (A-08 c5). */
    val reviewFlag: String? = null,
    val createdAt: Long = 0L,
) {
    /** The single-line address the Checkout screen prefills from (A-15 c1). */
    val fullAddress: String
        get() = listOf(addressLine, barangay, city, province)
            .filter { it.isNotBlank() }
            .joinToString(", ")

    /** True when every delivery field Checkout requires is present (A-15 c2). */
    val hasCompleteAddress: Boolean
        get() = province.isNotBlank() && city.isNotBlank() &&
            barangay.isNotBlank() && addressLine.isNotBlank() && mobile.isNotBlank()

    fun initials(): String = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
}

// ---------------------------------------------------------------------------
// PRODUCT  (A-03, A-13)
// ---------------------------------------------------------------------------

enum class StockStatus(val label: String) {
    IN_STOCK("In Stock"),
    LOW_STOCK("Low Stock"),
    OUT_OF_STOCK("Out of Stock"),
}

/**
 * A product row. A variant is a plain Product with [parentSku] set - the
 * MPBOW/MPBOW-PK shape A-13 c4 and A-03 c4 both describe.
 *
 * [packCost], [packQty], [packSize], [markupRate] and [unitCost] are OWNER-ONLY.
 * A-13 c6 forbids showing any of them to a customer, and the customer screens
 * read [price] alone so the exclusion is structural rather than a filter.
 */
data class Product(
    val sku: String,
    val name: String,
    val price: Long,
    val category: String = "",
    val character: String = "",
    /**
     * The customer-facing blurb the unified PDP (D.2) shows above the variant
     * chips. Owner-entered prose only - never a cost figure.
     */
    val description: String = "",
    /** One short spec line, e.g. "Paper wipes, 10 sheets per pack". */
    val specs: String = "",
    /** On hand (Physical) - what is actually in the stock room (A-03 c5). */
    val physical: Int = 0,
    /** Reserved by orders that are placed but not yet shipped (A-03 c5). */
    val reserved: Int = 0,
    val isElectronic: Boolean = false,
    val active: Boolean = true,
    /** Set for MPBOW: a parent that is listed but never bought directly (A-13 c4). */
    val isParent: Boolean = false,
    /** Set on MPBOW-PK / MPBOW-LV: the parent whose variants this row belongs to. */
    val parentSku: String? = null,
    val photoRef: String? = null,
    /** Packaging tier XS..XL; the tier's fee enters the price chain (A-03 c2). */
    val packSize: String = "XS",
    val packCost: Long = 0L,
    val packQty: Int = 1,
    val markupRate: Double = 0.30,
    val unitCost: Long = 0L,
    val deactivatedAt: Long? = null,
) {
    /**
     * A-03 c5 / A-15 c6: `Available = On hand (Physical) - Reserved`.
     *
     * This is the ONE stock figure every screen reads. Dispatch drops BOTH
     * physical and reserved by the shipped quantity (A-07 c9), which is why this
     * never double-subtracts: reserved is released exactly as physical leaves.
     */
    val available: Int get() = (physical - reserved).coerceAtLeast(0)

    /** A-13 c5: the badge a customer sees, derived from [available]. */
    val stockStatus: StockStatus
        get() = when {
            available <= 0 -> StockStatus.OUT_OF_STOCK
            available <= 2 -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }

    /** A-13 c6 / A-03 c7: only an active, non-parent row can be bought. */
    val isPurchasable: Boolean get() = active && !isParent
}

// ---------------------------------------------------------------------------
// ORDER  (A-04 .. A-07, A-15, A-16)
// ---------------------------------------------------------------------------

/**
 * The lifecycle stage A-04 c3's tabs are cut from, in tab order.
 *
 * Payment, packing and shipping are SEPARATE labels on top of this (A-04 c4,
 * A-16 c2): an order can be `To Pack` (stage) while its payment reads
 * `Partially Paid` and its shipping reads `Not shipped`. Storing the stage keeps
 * the tabs a partition instead of a set of overlapping predicates.
 */
enum class OrderStage(val label: String) {
    ORDER_PLACED("Order Placed"),
    WAITING_PAYMENT("Waiting for Payment"),
    TO_PACK("To Pack"),
    SHIPPED("Shipped"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
}

/** A-05 c5: a smaller payment sets Partially Paid and keeps the balance. */
enum class PaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PARTIALLY_PAID("Partially Paid"),
    PAID("Paid"),
}

/** A-07 c4/c6: packing is its own track with its own timestamp. */
enum class PackingStatus(val label: String) {
    NOT_PACKED("Not packed"),
    PACKED("Packed"),
}

/** A-07 c7: the shipping statuses the owner sets by hand. */
enum class ShippingStatus(val label: String) {
    NOT_SHIPPED("Not shipped"),
    FOR_PICKUP("For Pickup"),
    IN_TRANSIT("In Transit"),
    RECEIVED("Received"),
}

/** A-15 c3: the fulfillment choice made at Checkout. */
enum class Fulfillment(val label: String) {
    SHIP_NOW("Ship Now"),
    HOLD_FOR_COMBINED("Hold For Combined Shipping"),
}

/** A-04 c10 / A-16 c8: who cancelled, kept for the history both sides read. */
enum class CancelledBy(val label: String) {
    CUSTOMER("Customer"),
    OWNER("Owner"),
}

data class Cancellation(
    val by: CancelledBy,
    val reason: String,
    val at: Long,
)

/**
 * An order.
 *
 * [itemSubtotal] is the sum of the lines at the moment of placement; the lines
 * are snapshotted so a later price change cannot rewrite a placed order.
 * [total] adds the shipping fee the OWNER entered when sending the invoice -
 * Checkout never adds one (A-15 c1, A-15 c2).
 */
data class Order(
    val orderId: String,
    val customerEmail: String,
    val placedAt: Long,
    val stage: OrderStage,
    val paymentStatus: PaymentStatus,
    val packingStatus: PackingStatus,
    val shippingStatus: ShippingStatus,
    val fulfillment: Fulfillment = Fulfillment.SHIP_NOW,
    /** The invoice number `MP-####`, blank until Confirm & Send Invoice (A-04 c6). */
    val invoiceNo: String = "",
    val itemSubtotal: Long = 0L,
    /** Freight the owner entered; 0.00 is normal for a held order (A-04 c5). */
    val shippingFee: Long = 0L,
    val amountPaid: Long = 0L,
    /** The structured delivery block, snapshotted at placement (A-15 c2). */
    val recipientName: String = "",
    val mobile: String = "",
    val province: String = "",
    val city: String = "",
    val barangay: String = "",
    val addressLine: String = "",
    val courier: String = "",
    val trackingNo: String = "",
    val packedAt: Long? = null,
    /** A-04 c6: set when the invoice is sent; the 24-hour window starts here. */
    val invoicedAt: Long? = null,
    val paymentDeadline: Long? = null,
    val cancellation: Cancellation? = null,
    /** A-06 c5: the shipment a combined drop shares, when one was made. */
    val combinedShipmentNo: String? = null,
) {
    /** A-15 c2 / A-04 c5: order total = item subtotal + shipping fee. */
    val total: Long get() = itemSubtotal + shippingFee

    /** A-05 c1: what is still owed. Never negative. */
    val balance: Long get() = (total - amountPaid).coerceAtLeast(0L)

    /**
     * A-05 c5: an overdue order is `Waiting for Payment` past its window.
     * A-05's GWT pins ORD-1045 as overdue against the 24-hour clock; the app
     * NEVER cancels on this by itself (A-04 c2, A-05 c8).
     */
    fun isOverdue(now: Long): Boolean =
        stage == OrderStage.WAITING_PAYMENT &&
            paymentDeadline != null &&
            now > paymentDeadline

    /** A-05 c6: once Paid, editing and customer cancellation are locked. */
    val isPaid: Boolean get() = paymentStatus == PaymentStatus.PAID

    /** A-06 c6: held orders are the ones bought with Hold For Combined Shipping. */
    val isHeld: Boolean get() = fulfillment == Fulfillment.HOLD_FOR_COMBINED

    /** A-16 c3: the tracking number is only shown from In Transit onward. */
    val showsTracking: Boolean
        get() = shippingStatus == ShippingStatus.IN_TRANSIT ||
            shippingStatus == ShippingStatus.RECEIVED

    fun heldDays(now: Long): Int = ((now - placedAt) / 86_400_000L).toInt().coerceAtLeast(0)

    val addressBlock: String
        get() = listOf(addressLine, barangay, city, province)
            .filter { it.isNotBlank() }
            .joinToString(", ")
}

data class OrderLine(
    val lineId: String,
    val orderId: String,
    val sku: String,
    /** Snapshotted so a renamed product cannot rewrite a placed order. */
    val productName: String,
    val variantName: String = "",
    val qty: Int,
    val unitPrice: Long,
) {
    val lineTotal: Long get() = unitPrice * qty
}

// ---------------------------------------------------------------------------
// CART  (A-14)
// ---------------------------------------------------------------------------

/**
 * A cart row. [selected] is A-14 c2's checkbox and NOTHING here touches stock:
 * the pivot's cart reserves nothing (A-14 c1), which is why there is no
 * reservation method anywhere on this type.
 */
data class CartLine(
    val sku: String,
    val qty: Int,
    val selected: Boolean = true,
)

// ---------------------------------------------------------------------------
// PAYMENT / INVOICE  (A-05)
// ---------------------------------------------------------------------------

data class Payment(
    val paymentId: String,
    val orderId: String,
    val amount: Long,
    /** GCash, Maya or Bank Transfer - the only three A-05 c2 offers. */
    val method: String,
    val paidAt: Long,
)

data class Invoice(
    val invoiceNo: String,
    val orderId: String,
    val issuedAt: Long,
    val total: Long,
)

// ---------------------------------------------------------------------------
// PRODUCT_OPTION  (A-03, A-10 Lists)
// ---------------------------------------------------------------------------

enum class OptionKind(val wire: String, val label: String) {
    CHARACTER("character", "Character"),
    CATEGORY("category", "Category"),
}

data class ProductOption(
    val optionId: String,
    val kind: OptionKind,
    val value: String,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)

// ---------------------------------------------------------------------------
// STOCK_ADJUSTMENT  (A-03 c6: the reason is OPTIONAL)
// ---------------------------------------------------------------------------

data class StockAdjustment(
    val adjId: String,
    val sku: String,
    val oldPhysical: Int,
    val newPhysical: Int,
    /** `null` when the owner left the reason blank - the adjustment still logs. */
    val reason: String? = null,
    val actor: String = "Katrina Bellen",
    val adjustedAt: Long = 0L,
)

// ---------------------------------------------------------------------------
// SETTINGS / ACTIVITY_LOG  (A-10)
// ---------------------------------------------------------------------------

data class Setting(
    val settingName: String,
    val value: String,
    val updatedAt: Long = 0L,
)

/**
 * One append-only audit row (IAS R.5).
 *
 * [prevHash] and [hash] turn the log into a SHA-256 hash chain: each row's [hash]
 * covers its own content AND the previous row's [hash], so editing or deleting a
 * historical entry breaks every row after it and `verifyAuditChain()` reports the
 * break instead of silently accepting a rewritten history. The first row's
 * [prevHash] is [GENESIS].
 *
 * A row whose [hash] is blank was written by a path that has not sealed it yet;
 * `AuditChain.seal` fills it in, so a test can assert that no row escapes unsealed.
 */
data class ActivityLogEntry(
    val logId: String,
    val actor: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val detail: String? = null,
    val loggedAt: Long = 0L,
    /** The previous row's [hash], or [GENESIS] for the first entry. */
    val prevHash: String = GENESIS,
    /** SHA-256 over `prevHash|logId|actor|action|entityType|entityId|detail|loggedAt`. */
    val hash: String = "",
) {
    companion object {
        /** The fixed seed of the chain: the first row points at nothing. */
        const val GENESIS = "GENESIS"
    }
}
