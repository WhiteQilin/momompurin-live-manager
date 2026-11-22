package ph.edu.umak.momompurin.data

/**
 * The per-courier tracking-number format check A-07 c8 needs (Prof. Nomar's
 * ALGOCOM regex-verification control).
 *
 * ## Why the format is checked at all
 *
 * A-07 c8 only requires a *non-empty* tracking number before In Transit, and that
 * is not enough: the dispatch is what moves physical and reserved stock, so a
 * mistyped `JT123` sends a real parcel into a status no one can trace and the
 * record can never be corrected by the customer. Validating the shape against the
 * route the owner already picked catches the typo at the moment it can still be
 * fixed, and the refusal names the route so the owner knows which format was
 * expected.
 *
 * ## Normalisation runs first
 *
 * Every courier prints its number with its own spacing - `JT 2612 3456 7890`,
 * `J&T-76123456789`, `jt0012345678` - so the input is uppercased and every space,
 * hyphen and dot is stripped before matching. Validating the raw text would refuse
 * the exact string the waybill prints.
 *
 * ## Pickup is exempt
 *
 * A customer pickup has no waybill, so [validate] returns [Outcome.NotRequired]
 * rather than a failure. That is deliberately a distinct outcome, not `null`: a
 * caller that treated "no rule" as "invalid" would block every pickup dispatch.
 */
object CourierTrackingValidator {

    /** The four routes A-07 c6 offers, in the order the dropdown lists them. */
    const val JT = "J&T Express"
    const val TIKTOK = "TikTok Shop"
    const val LALAMOVE_GRAB = "Lalamove/Grab"
    const val PICKUP = "Pickup"

    /** The message A-07 c8 pins for an empty field. */
    const val ENTER_TRACKING_FIRST = "Enter the tracking number first."

    /** A format mismatch, naming the route whose format was expected. */
    fun formatMessage(courier: String): String =
        "Tracking number does not match $courier format."

    /**
     * J&T: an optional `J&T`/`JT` prefix then 10-13 digits.
     *
     * Accepts `JT0012345678`, `JT 2612 3456 7890` (10 digits after stripping) and
     * `J&T-76123456789`; refuses `JT123` (too few digits).
     */
    private val JT_PATTERN = Regex("^(?:J&?T-?)?\\d{10,13}$")

    /**
     * Lalamove: an `LMB`/`LM` booking code, or a Grab `GRAB[-PH]` code.
     *
     * Both couriers share the one dropdown entry, so the pattern accepts either
     * house style rather than forcing the owner to pick a route they were not
     * offered.
     */
    private val LALAMOVE_GRAB_PATTERN =
        Regex("^(?:LMB|LM)[-A-Z0-9]{7,14}$|^GRAB(?:-PH)?[-A-Z0-9]{5,13}$")

    /** TikTok Shop: an `SPH` shipment id, or the bare numeric order id. */
    private val TIKTOK_PATTERN = Regex("^SPH\\d{10,18}$|^\\d{10,20}$")

    /** What the check concluded. */
    sealed interface Outcome {
        /** The number matches the route's format. */
        data object Valid : Outcome

        /** A pickup needs no tracking number at all. */
        data object NotRequired : Outcome

        /** The field was empty. */
        data object Missing : Outcome

        /** The field was filled but the shape is wrong for the route. */
        data class Malformed(val courier: String, val message: String) : Outcome
    }

    /**
     * The comparable form: uppercase, with spaces, hyphens, dots and slashes gone.
     *
     * A hyphen is stripped even though two patterns allow one, because the patterns
     * that allow it do so only as an internal separator (`J&T-`) and normalising
     * first keeps `JT - 0012345678` readable rather than refused.
     */
    fun normalise(raw: String): String =
        raw.trim().uppercase()
            .filterNot { it.isWhitespace() || it == '-' || it == '.' || it == '/' }

    /** True when [courier] is a pickup, which carries no waybill. */
    fun isPickup(courier: String?): Boolean =
        courier?.trim()?.equals(PICKUP, ignoreCase = true) == true

    /** True when the route is one the validator has a rule for. */
    fun isKnown(courier: String?): Boolean = when (courier?.trim()) {
        JT, TIKTOK, LALAMOVE_GRAB, PICKUP -> true
        else -> false
    }

    /**
     * Check [trackingNo] against [courier].
     *
     * An unknown courier is treated as [Outcome.Valid] rather than refused: the
     * owner may have a route the app does not list, and inventing a format rule for
     * it would block a dispatch for no reason.
     */
    fun validate(courier: String?, trackingNo: String?): Outcome {
        if (isPickup(courier)) return Outcome.NotRequired
        val raw = trackingNo?.trim().orEmpty()
        if (raw.isEmpty()) return Outcome.Missing

        val candidate = normalise(raw)
        val pattern = when (courier?.trim()) {
            JT -> JT_PATTERN
            TIKTOK -> TIKTOK_PATTERN
            LALAMOVE_GRAB -> LALAMOVE_GRAB_PATTERN
            else -> return Outcome.Valid
        }
        return if (pattern.matches(candidate)) {
            Outcome.Valid
        } else {
            Outcome.Malformed(
                courier = courier?.trim().orEmpty(),
                message = formatMessage(courier?.trim().orEmpty()),
            )
        }
    }

    /**
     * The message to show, or null when the dispatch may proceed.
     *
     * This is the shape the UI wants: `null` means "nothing to say", so a caller
     * cannot render a refusal in a success colour by mistake.
     */
    fun blockingMessage(courier: String?, trackingNo: String?): String? = when (
        val outcome = validate(courier, trackingNo)
    ) {
        is Outcome.Valid, is Outcome.NotRequired -> null
        is Outcome.Missing -> ENTER_TRACKING_FIRST
        is Outcome.Malformed -> outcome.message
    }
}
