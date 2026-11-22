package ph.edu.umak.momompurin.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Handoff s.3.3. Radius and spacing are a closed scale: never introduce a value
 * outside it. Buttons use [RadiusPill], cards use [RadiusCard].
 */
object MomoRadius {
    val Card = 16.dp
    val Input = 12.dp
    val Chip = 30.dp
    val Pill = 999.dp
}

object MomoSpace {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp

    /**
     * Bottom content padding for a scrolling screen that sits above the persistent
     * navigation bar.
     *
     * The bar's own strip is 92dp (`MomoBottomBar`), but a `LazyColumn`/scrolling
     * Column lives in the `weight(1f)` slot ABOVE the bar, so this is NOT the bar
     * height - it is the breathing room the last card needs so its trailing action
     * is never jammed against the bar's edge. 32dp reads as deliberate space at
     * every density in this app; anything under ~24dp looks like a clipping
     * accident rather than a margin.
     */
    val BottomBarClearance = 32.dp

    /**
     * The gap between the yellow top bar and the first thing in a tab's content.
     *
     * Bug 7 (real-phone report): Orders put its search field straight against the
     * bar's lower edge, so the field looked glued to the header while every other
     * tab had breathing room. 16dp is the value Shop, Stock and Customers already
     * used, so naming it here is what makes "the same gap on every tab" a property
     * of one constant rather than of four remembered call sites.
     */
    val TopContentGap = 16.dp
}

/**
 * Fixed control heights.
 *
 * These are MINIMUMS everywhere they are used (`heightIn`, never `height`). A rigid
 * height crops a label the moment it needs a second line or the user enlarges the
 * font scale - which is exactly how "Confirm cancel" lost the top of its second
 * line in the cancel dialog, where a fixed 46dp box met a weighted, squeezed button.
 * Controls grow if they must; they never slice text.
 */
object MomoDimens {
    /** The `Hi-Fi / Button` master's standalone height (3572:1891). */
    val ButtonHeight = 46.dp

    /** A2.5/A2.6's in-card action height - the master's compact size. */
    val ButtonHeightCompact = 44.dp

    /** The bordered in-card MiniAction pill. */
    val MiniActionHeight = 34.dp

    /** The accessible minimum touch target (A-04 c1). */
    val TouchTarget = 48.dp
}
