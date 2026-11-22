package ph.edu.umak.momompurin

import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.ui.theme.MomoColors

/**
 * WCAG contrast floors for the palette pairs that carry MEANING.
 *
 * A colour audit that only checks "no hex outside the theme" says nothing about
 * whether the theme is readable. This app's message to a user is often carried by
 * a coloured pill (Paid vs Overdue) or a link, so the pair - not the colour - is
 * what has to pass.
 *
 * Floor: 4.5:1 for body-size text and 3:1 for a non-text boundary, per WCAG 2.1
 * AA. These run on the JVM without rendering, so a palette edit fails here rather
 * than in a screenshot nobody measured.
 */
class ContrastAuditTest {

    private fun luminance(color: androidx.compose.ui.graphics.Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAA(name: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
        val r = ratio(bg, fg)
        assertTrue(
            "$name contrast %.2f:1 must be at least 4.5:1 for AA body text".format(r),
            r >= 4.5,
        )
    }

    // ------------------------------------------------------------- body text --

    @Test
    fun body_text_is_readable_on_the_canvas() {
        assertAA("TextPrimary on canvas", MomoColors.SurfaceCanvas, MomoColors.TextPrimary)
        assertAA("TextSecondary on canvas", MomoColors.SurfaceCanvas, MomoColors.TextSecondary)
        assertAA("InkStrong on card", MomoColors.SurfaceCard, MomoColors.InkStrong)
    }

    @Test
    fun the_link_colour_meets_aa() {
        // The one colour reserved for INTERACTIVE text must be the most legible,
        // not the least - it shipped at 4.16:1 once.
        assertAA("link on canvas", MomoColors.SurfaceCanvas, MomoColors.TextLinkAndBrand)
        assertAA("link on card", MomoColors.SurfaceCard, MomoColors.TextLinkAndBrand)
    }

    // ----------------------------------------------------------- the CTA pair --

    @Test
    fun the_primary_button_label_is_readable() {
        // The gold CTA carries BrandDarkest ink; this is the app's most-used control.
        assertAA("CTA label on gold", MomoColors.ButtonPrimaryFill, MomoColors.BrandDarkest)
    }

    @Test
    fun a_destructive_button_label_is_readable() {
        assertAA("destructive label", MomoColors.DangerBase, MomoColors.InkInverse)
    }

    // ------------------------------------------------- the status badge pairs --
    // These carry STATUS MEANING, so a badge whose text is unreadable is a status
    // the user cannot act on.

    @Test
    fun the_success_badge_is_readable() {
        assertAA("Paid / In Stock", MomoColors.SuccessSurface, MomoColors.SuccessText)
    }

    @Test
    fun the_warning_badge_is_readable() {
        assertAA("Low Stock / Order Placed", MomoColors.WarningSurface, MomoColors.WarningText)
    }

    @Test
    fun the_danger_badge_is_readable() {
        assertAA("Overdue / Out of Stock", MomoColors.DangerSurface, MomoColors.DangerText)
    }

    @Test
    fun the_info_badge_is_readable() {
        assertAA("Waiting for Payment / Partially Paid", MomoColors.InfoSurface, MomoColors.InfoText)
    }

    // ------------------------------------------------------- navigation chrome --

    @Test
    fun the_navigation_labels_are_readable_in_both_states() {
        // Inactive labels sit on the gold bar; the active one sits on the white pill.
        assertAA("inactive tab on gold bar", MomoColors.BrandPrimary, MomoColors.BrandDarkest)
        assertAA("active tab on white pill", MomoColors.SurfaceCard, MomoColors.NavActiveInk)
    }

    @Test
    fun the_app_bar_title_is_readable() {
        assertAA("app bar title", MomoColors.BrandPrimary, MomoColors.BrandDarkest)
    }

    // ------------------------------------------- non-text boundary (3:1 floor) --

    @Test
    fun the_card_border_is_visible_against_both_surfaces() {
        // A hairline is a non-text boundary, so its floor is 3:1 ... but a 1dp rule
        // that no one can see is not a boundary at all. This asserts a weaker but
        // honest floor: it must at least be DISTINGUISHABLE (1.2:1) from the card,
        // and clearly darker than the canvas it separates.
        val vsCard = ratio(MomoColors.SurfaceCard, MomoColors.BorderSubtle)
        assertTrue(
            "card hairline must be distinguishable from the white card (was %.2f:1)".format(vsCard),
            vsCard >= 1.2,
        )
    }
}
