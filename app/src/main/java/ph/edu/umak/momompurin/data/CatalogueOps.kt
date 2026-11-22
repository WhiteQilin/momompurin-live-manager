package ph.edu.umak.momompurin.data

import ph.edu.umak.momompurin.data.entity.ActivityLogEntry
import ph.edu.umak.momompurin.data.entity.OptionKind
import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.data.entity.ProductOption

/**
 * Product, stock and list operations for A-03 and A-10.
 *
 * Each rule is quoted from the criterion it serves so a screen never re-derives
 * one: it calls a function and gets back either the message the workbook quotes or
 * the row it should save.
 */
object CatalogueOps {

    // ------------------------------------------------- A-03 c1-c3: the form ----

    /** A-03 c3: a missing Product name or Product code. */
    const val REQUIRED_FIELDS = "Complete the required fields."

    /** A-03 c3: the code is already taken. */
    const val DUPLICATE_CODE = "Product code already exists."

    /** A-03 c3: a save that went through. */
    const val PRODUCT_SAVED = "Product saved."

    /**
     * A-03 c3: the code-uniqueness check.
     *
     * Case-insensitive, because `chw` and `CHW` would be the same shelf label and
     * the workbook treats the code as an identifier rather than a display string.
     */
    fun duplicateCodeError(
        code: String,
        products: List<Product>,
        excludingSku: String? = null,
    ): String? {
        val c = code.trim()
        if (c.isEmpty()) return null
        val clash = products.any {
            it.sku.equals(c, ignoreCase = true) &&
                (excludingSku == null || !it.sku.equals(excludingSku, ignoreCase = true))
        }
        return if (clash) DUPLICATE_CODE else null
    }

    /** A-03 c1/c3: the two fields a save cannot proceed without. */
    fun requiredFieldsError(name: String, code: String): String? =
        if (name.isBlank() || code.isBlank()) REQUIRED_FIELDS else null

    /**
     * A-03 c2: the price chain's four figures for the form's preview.
     *
     * Delegates to [ph.edu.umak.momompurin.domain.pricing.PricingChain] so the
     * screen and the tests cannot disagree about P380.00 / 18.
     *
     * [markupPercent] and [freebiePercent] are the OWNER's A-10 c3 settings rather
     * than constants. Hardcoding 30 / 3 here made both settings write-only: the
     * field accepted "50", said "Settings saved.", and the next product quote still
     * used 30 - a control that is drawn but not wired.
     */
    fun pricePreview(
        packCost: Long,
        packQty: Int,
        packSize: String,
        markupPercent: Double,
        freebiePercent: Double = DEFAULT_FREEBIE_PERCENT,
    ): ph.edu.umak.momompurin.domain.pricing.PricingChain.Result {
        val safeQty = packQty.coerceAtLeast(1)
        val safeCost = packCost.coerceAtLeast(1)
        return ph.edu.umak.momompurin.domain.pricing.PricingChain.compute(
            packTotalCostCents = safeCost,
            packQty = safeQty,
            packTier = packSize,
            ownerPriceCents = null,
            markup = java.math.BigDecimal.valueOf(markupPercent).movePointLeft(2),
            // The chain wants the share (0.03), not the percentage.
            freebieRate = java.math.BigDecimal.valueOf(freebiePercent).movePointLeft(2),
        )
    }

    /** A-10 c1's seeded markup rate, as a percentage. */
    const val DEFAULT_MARKUP_PERCENT = 30.0

    /** A-10 c1's seeded freebie rate, as a percentage. */
    const val DEFAULT_FREEBIE_PERCENT = 3.0

    /**
     * Reads a stored rate setting as a percentage.
     *
     * A blank or out-of-range setting falls back to the seeded rate, so a cleared
     * field can never make the calculator quote a wild price.
     */
    fun rateSetting(raw: String?, fallback: Double): Double =
        raw?.trim()?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 } ?: fallback

    // --------------------------------------------------- A-03 c5: stock rows ----

    /**
     * A-03 c5 / A-09 c5: the Available figure.
     *
     * A method rather than a second stored column, so it can never disagree with
     * Physical and Reserved - this project's most-cited defect class.
     */
    fun available(product: Product): Int = product.available

    // ------------------------------------------------ A-03 c6: adjustments -----

    /** A-03 c6: a new On hand count. The reason is OPTIONAL. */
    fun validAdjustment(physical: Int): String? =
        if (physical < 0) "Enter an on hand count of zero or more." else null

    // -------------------------------------------------- A-10 c3: the rates ------

    /** A-10 c3's message. */
    const val INVALID_RATE = "Enter a rate between 0 and 100."

    /**
     * A-10 c3: a rate outside 0-100 is refused and the last good value survives.
     *
     * Returns the rate as a whole percentage, or null when the input is rejected.
     */
    fun parseRate(input: String): Double? {
        val n = input.trim().removeSuffix("%").trim().toDoubleOrNull() ?: return null
        if (n < 0.0 || n > 100.0) return null
        return n
    }

    // -------------------------------------------------- A-10 c4: the lists ------

    sealed interface OptionResult {
        data class Added(val options: List<ProductOption>, val value: String) : OptionResult
        data class Refused(val message: String) : OptionResult
    }

    /**
     * A-10 c4: add a character or category and have the product form offer it.
     *
     * A duplicate is refused because the list feeds a dropdown, and two rows with
     * the same label would be indistinguishable to the owner picking one.
     */
    fun addOption(
        options: List<ProductOption>,
        kind: OptionKind,
        value: String,
    ): OptionResult {
        val v = value.trim()
        if (v.isEmpty()) return OptionResult.Refused("Enter a name for the option.")
        val exists = options.any { it.kind == kind && it.value.equals(v, ignoreCase = true) }
        if (exists) return OptionResult.Refused("\"$v\" is already in the list.")
        val sortOrder = options.filter { it.kind == kind }.maxOfOrNull { it.sortOrder + 1 } ?: 0
        return OptionResult.Added(
            options + ProductOption(
                optionId = "OPT-%s-%03d".format(kind.wire.take(1).uppercase(), options.size + 1),
                kind = kind,
                value = v,
                sortOrder = sortOrder,
            ),
            v,
        )
    }

    /** A-10 c4: remove an option so the form stops offering it. */
    fun removeOption(
        options: List<ProductOption>,
        value: String,
        kind: OptionKind,
    ): List<ProductOption> =
        options.filterNot { it.kind == kind && it.value.equals(value, ignoreCase = true) }

    // -------------------------------------------------------- the log line -----

    fun log(
        action: String,
        sku: String,
        detail: String?,
        now: Long,
        index: Int,
        actor: String = SeedData.OWNER_EMAIL,
    ) = ActivityLogEntry(
        logId = "LOG-%04d".format(index),
        actor = actor,
        action = action,
        entityType = "Product",
        entityId = sku,
        detail = detail,
        loggedAt = now,
    )
}
