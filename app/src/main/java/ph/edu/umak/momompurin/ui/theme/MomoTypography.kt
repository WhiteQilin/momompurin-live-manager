package ph.edu.umak.momompurin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ph.edu.umak.momompurin.R

/**
 * The three families the design specifies, each shipped as FOUR STATIC weight
 * instances rather than one variable font.
 *
 * ---------------------------------------------------------------------------
 * WHY STATIC, NOT VARIABLE
 * ---------------------------------------------------------------------------
 * These families used to ship as single variable TTFs, with the weight chosen by
 * `FontVariation.Settings(FontVariation.weight(...))`. That is only honoured on
 * API 36, which maps `FontWeight` onto the `wght` axis. Android 14/15 and OEM
 * renderers (HyperOS / MIUI / OneUI) load a variable font resource at its DEFAULT
 * `wght` axis and ignore the settings - and **Fredoka's default is wght=300
 * (Light)**, so on those phones every Fredoka weight rendered LIGHTER than
 * Regular and the whole type hierarchy collapsed to thin.
 *
 * The fix: one static file per weight. `tools/make_static_fonts.py` instances them
 * from the variable source, strips `fvar`/`gvar`, and sets `OS/2.usWeightClass` to
 * 400/500/600/700. A static face has no axis to default to, so every Android
 * version and OEM skin renders the weight that was asked for.
 *
 * No `variationSettings` here on purpose: on a static face they are a no-op, and
 * keeping them would hide a future regression back to a variable file.
 */

val FredokaFamily = FontFamily(
    Font(R.font.fredoka_regular, weight = FontWeight.Normal),
    Font(R.font.fredoka_medium, weight = FontWeight.Medium),
    Font(R.font.fredoka_semibold, weight = FontWeight.SemiBold),
    Font(R.font.fredoka_bold, weight = FontWeight.Bold),
)

val ComfortaaFamily = FontFamily(
    Font(R.font.comfortaa_regular, weight = FontWeight.Normal),
    Font(R.font.comfortaa_medium, weight = FontWeight.Medium),
    Font(R.font.comfortaa_semibold, weight = FontWeight.SemiBold),
    Font(R.font.comfortaa_bold, weight = FontWeight.Bold),
)

/**
 * Monospace is not decorative. Every SKU, order number, invoice number and
 * tracking number uses this family so a tester can read `JT 2612 3456 7890`
 * character by character (handoff s.3.2).
 */
val MonospaceFamily = FontFamily(
    Font(R.font.mplus1code_regular, weight = FontWeight.Normal),
    Font(R.font.mplus1code_medium, weight = FontWeight.Medium),
    Font(R.font.mplus1code_semibold, weight = FontWeight.SemiBold),
    Font(R.font.mplus1code_bold, weight = FontWeight.Bold),
)

/**
 * Named type roles from handoff s.3.2. Screens use these by name; a screen that
 * needs a new role adds it here rather than inlining a size.
 */
object MomoType {
    /**
     * Line heights are proportional to their font size.
     *
     * The Figma type table lists a `30` column beside each role, but that number is
     * the frame's paragraph spacing, not a line height. Copying it as `lineHeight`
     * made every wrapped 12sp/15sp sentence open a 30sp gap between its two lines -
     * visible on MoreScreen's "The restore procedure is a checklist in the handover
     * guide." Ratios below are ~1.15-1.4x, the normal band for these families.
     */

    /** Display/Large - Fredoka Bold 38/44. Screen hero numbers. */
    val DisplayLarge = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight.Bold,
        fontSize = 38.sp, lineHeight = 44.sp,
    )

    /** Heading/Medium - Fredoka SemiBold 24/30. Screen titles. */
    val HeadingMedium = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp,
    )

    /** Title/Small - Fredoka SemiBold 18/24. Card titles. */
    val TitleSmall = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    )

    /** Body/Default - Comfortaa Regular 15/21. Body copy. */
    val BodyDefault = TextStyle(
        fontFamily = ComfortaaFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp,
    )

    /**
     * Body/Small - Comfortaa Regular 14/20.
     *
     * The E.2 floor for body copy, SKUs and metadata: 14sp is the smallest size a
     * reader with mild vision loss can follow a sentence at without pinch-zooming,
     * and it is the size the handoff's own dense table cells use. Screens that
     * previously reached for CaptionSmall on a full sentence move here instead.
     */
    val BodySmall = TextStyle(
        fontFamily = ComfortaaFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    )

    /**
     * Caption/Small - Comfortaa Medium 12/17. Metadata.
     *
     * 12sp is the FLOOR, not a suggestion (E.2): every badge, caption, helper line
     * and navigation label is at least this size. It used to be reached for body
     * sentences as well, which is why [BodySmall] now exists above it.
     */
    val CaptionSmall = TextStyle(
        fontFamily = ComfortaaFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 17.sp,
    )

    /** Caption/Bold - Comfortaa Bold 13/18. The dashboard's date line. */
    val CaptionBold = TextStyle(
        fontFamily = ComfortaaFamily, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 18.sp,
    )

    /** Button/Action - Fredoka SemiBold 14/18. Button labels. */
    val ButtonAction = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp,
    )

    /** Data/Monospace - M PLUS 1 Code SemiBold 16/22. SKUs, order IDs, money. */
    val DataMonospace = TextStyle(
        fontFamily = MonospaceFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    )

    /**
     * Data/Caption - M PLUS 1 Code Medium 14/19. Dense table cells and badges.
     *
     * Was 13sp. E.2's floor for a SKU code is **14sp** - the criterion names "SKU
     * codes" explicitly alongside body copy - so a SKU rendered at 13sp was the one
     * piece of data the criterion singled out sitting below its own minimum. 14sp
     * with a 1.36 line ratio keeps the dense stock and report rows compact while
     * clearing the floor.
     */
    val DataCaption = TextStyle(
        fontFamily = MonospaceFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 19.sp,
    )

    /**
     * Label/Chip - Fredoka SemiBold 12/16. Badge and chip labels.
     *
     * 12sp is E.2's floor for a badge, so this role cannot go smaller without
     * breaking the criterion. A chip's label is the badge's whole meaning, which is
     * why the badge also never truncates.
     */
    val LabelChip = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp,
    )

    /**
     * Label/Navigation - Fredoka SemiBold 12/15. The bottom-bar tab labels.
     *
     * The owner's five-tab bar is the tightest text in the app: five equally
     * weighted slots inside a 412dp strip, with "Customers" the longest label. At
     * 12sp with a -0.3sp tracking it measures ~62dp and fits the ~76dp slot on a
     * 360dp-wide handset without clipping, which is the check E.2 asks for. It
     * carries a slightly tighter line height than [LabelChip] because the bar's
     * strip is a fixed 92dp and the label sits under a 22dp icon.
     */
    val LabelNavigation = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = (-0.3).sp,
    )
}

/**
 * Material3's slot mapping, so stock components (AlertDialog body text, Button
 * labels, supporting text) inherit the brand families instead of Roboto.
 */
val MomoTypography = Typography(
    displayLarge = MomoType.DisplayLarge,
    headlineMedium = MomoType.HeadingMedium,
    titleMedium = MomoType.TitleSmall,
    bodyLarge = MomoType.BodyDefault,
    bodyMedium = MomoType.BodyDefault,
    bodySmall = MomoType.CaptionSmall,
    labelLarge = MomoType.ButtonAction,
    labelMedium = MomoType.LabelChip,
    labelSmall = MomoType.CaptionSmall,
)
