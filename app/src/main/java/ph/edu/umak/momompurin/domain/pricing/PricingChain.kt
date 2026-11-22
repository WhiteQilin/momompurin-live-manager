package ph.edu.umak.momompurin.domain.pricing

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The pricing chain, from `G-01`-`G-03` and handoff s.5.6.
 *
 * The chain is fixed and every figure on every screen comes from here:
 *
 *     unit cost   = pack total cost / pack quantity
 *     freebie     = unit cost x freebie rate (3%)
 *     base        = unit cost + freebie + packaging tier
 *     suggested   = base x (1 + markup)          (markup default 30%)
 *     net         = owner price - unit cost - freebie - packaging
 *
 * Two rules that are easy to get wrong:
 *
 *  1. **Full precision is stored; two decimals are display only.** G-01 c2 and
 *     N-12 c4 both say so, and the transparency line in the UI exists to prove it:
 *     `P380.00 / 18 units = P21.1111...`.
 *  2. **Rounding is HALF_UP, not HALF_EVEN.** W1's freebie is exactly
 *     `22.50 x 3% = 0.675`, which the criterion shows as `P0.68`. Half-even would
 *     render 0.68 here too, but not for every input, so the mode is pinned
 *     explicitly rather than left to the platform default.
 *
 * Money crosses this boundary as [Long] cents, matching the data layer; the
 * intermediate values are [BigDecimal] so a penny never vanishes into a Double.
 */
object PricingChain {

    /** The default markup, mirrored from SETTINGS.profit_markup (N-12 c1). */
    val DEFAULT_MARKUP: BigDecimal = BigDecimal("0.30")

    /** The freebie share of unit cost (handoff s.5.6). */
    val DEFAULT_FREEBIE_RATE: BigDecimal = BigDecimal("0.03")

    /** Packaging tiers, XS..XL (handoff s.5.7). */
    val PACKAGING_TIERS: Map<String, Long> = mapOf(
        "XS" to 500L,
        "S" to 1_000L,
        "M" to 1_500L,
        "L" to 2_000L,
        "XL" to 3_500L,
    )

    fun packagingFor(tier: String): Long = PACKAGING_TIERS[tier.trim().uppercase()] ?: 0L

    /**
     * G-02 c6: "reject a percentage outside 0 to 100 and keep the last good value."
     *
     * Accepts what the owner types (e.g. "130" or "3") and returns the stored rate
     * as a fraction, or null when the input is not a percentage in range. Null is
     * the "reject and keep the last good value" signal - the caller must not write.
     */
    fun parsePercentage(input: String): BigDecimal? {
        val n = input.trim().removeSuffix("%").trim().toBigDecimalOrNull() ?: return null
        if (n < BigDecimal.ZERO || n > BigDecimal(100)) return null
        return n.movePointLeft(2)
    }

    /** G-02 c6: the message the settings field shows on an out-of-range entry. */
    const val INVALID_PERCENTAGE = "Invalid percentage."

    /**
     * Every computed value, at full precision, for one product.
     *
     * [unitCost], [freebie] and [suggestedPrice] are the rounded *display* values;
     * the [unitCostExact] / [freebieExact] / [suggestedExact] fields keep the raw
     * decimal so the transparency line and a recomputation can use them.
     */
    data class Result(
        val packTotalCostCents: Long,
        val packQty: Int,
        val packTier: String,
        val packagingCents: Long,
        val markup: BigDecimal,
        val freebieRate: BigDecimal,
        val unitCostExact: BigDecimal,
        val freebieExact: BigDecimal,
        val baseExact: BigDecimal,
        val suggestedExact: BigDecimal,
        val unitCost: Long,
        val freebie: Long,
        val suggestedPrice: Long,
        val ownerPrice: Long,
        val netPerItem: Long,
        val totalNetEarnings: Long,
        /** G-03 c4: the owner price is below the base cost. */
        val belowCost: Boolean,
    ) {
        /**
         * The transparency line, e.g. `P380.00 / 18 units = P21.1111...`.
         * G-01 c6 and N-12 c4: the reader must be able to verify the division.
         */
        val transparencyLine: String
            get() = "${fmt(packTotalCostCents)} / $packQty units = ${unitCostExact.stripTrailingZeros().toPlainString()}"
    }

    /**
     * Compute the chain.
     *
     * @param packTotalCostCents the whole pack's cost, e.g. CHW's P380.00
     * @param packQty units per pack; must be > 0 (G-01 c3)
     * @param packTier XS/S/M/L/XL
     * @param ownerPriceCents the owner's price, defaulting to the suggested one
     * @param stockOnHand used only for [Result.totalNetEarnings]
     */
    fun compute(
        packTotalCostCents: Long,
        packQty: Int,
        packTier: String,
        ownerPriceCents: Long?,
        stockOnHand: Int = 0,
        markup: BigDecimal = DEFAULT_MARKUP,
        freebieRate: BigDecimal = DEFAULT_FREEBIE_RATE,
    ): Result {
        require(packQty > 0) { "Pack quantity must be greater than zero." }
        require(packTotalCostCents > 0) { "Cost must be greater than zero." }

        val packTotal = BigDecimal(packTotalCostCents).movePointLeft(2)
        val packaging = BigDecimal(packagingFor(packTier)).movePointLeft(2)

        // Full precision, 12 significant digits - the same precision the Python
        // reference and the client's workbook use for the unit-cost column.
        val unitExact = packTotal.divide(BigDecimal(packQty), MathContext(12))
        val freebieExact = unitExact.multiply(freebieRate).round(MathContext(12))
        val baseExact = unitExact.add(freebieExact).add(packaging).round(MathContext(12))
        val suggestedExact = baseExact.multiply(BigDecimal.ONE.add(markup)).round(MathContext(12))

        val priceCents = ownerPriceCents ?: toCents(suggestedExact)
        val price = BigDecimal(priceCents).movePointLeft(2)
        val net = price.subtract(unitExact).subtract(freebieExact).subtract(packaging).round(MathContext(12))

        return Result(
            packTotalCostCents = packTotalCostCents,
            packQty = packQty,
            packTier = packTier,
            packagingCents = packagingFor(packTier),
            markup = markup,
            freebieRate = freebieRate,
            unitCostExact = unitExact,
            freebieExact = freebieExact,
            baseExact = baseExact,
            suggestedExact = suggestedExact,
            unitCost = toCents(unitExact),
            freebie = toCents(freebieExact),
            suggestedPrice = toCents(suggestedExact),
            ownerPrice = priceCents,
            netPerItem = toCents(net),
            totalNetEarnings = toCents(net) * stockOnHand,
            // G-03 c4: below the *base cost*, not below the suggestion.
            belowCost = price < baseExact,
        )
    }

    /** G-01 c1/c2: two decimals, HALF_UP, from a full-precision decimal. */
    fun toCents(value: BigDecimal): Long =
        value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

    /** G-01 c5: preview a cost change without saving (`380 -> 400` shows `22.22`). */
    fun previewUnitCost(packTotalCostCents: Long, packQty: Int): Long =
        if (packQty <= 0) 0L
        else toCents(BigDecimal(packTotalCostCents).movePointLeft(2).divide(BigDecimal(packQty), MathContext(12)))

    private fun fmt(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return "₱$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }
}
