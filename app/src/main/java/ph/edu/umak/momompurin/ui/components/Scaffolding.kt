package ph.edu.umak.momompurin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * Shared primitives transcribed from the REAL component masters in
 * `Hi-Fi / Components` (`3415:361`) on page `3395:3205`:
 *
 *   - `Hi-Fi / App Bar`  `3494:1488`  412 x 72 — brand band `#FED161`, 44dp round
 *     back control at x=8 with a Fredoka `‹`, Fredoka SemiBold 18 title at x=60,
 *     optional white context chip (1dp Brand/Deep border, 28dp, radius pill).
 *   - The card surface every screen uses: white, radius 16, ONE 1dp
 *     `Border-/-Subtle` (#E6DCB8) rule — the J2.0/N8.0/B3.0 head, list and total
 *     cards are all this shape.
 *
 * Source-of-truth note: these are the coded component masters, NOT the
 * `Design Reference (EXAMPLE)` group (`3395:3206`).
 */

/**
 * A strip every screen's header draws directly beneath its title band.
 *
 * Bug 5 (real-phone report): the owner's offline notice used to be a sibling of
 * the whole NavHost, ABOVE every screen, while [MomoTopBar] below it also consumed
 * the status-bar inset - so the notice took the inset twice, the screen title was
 * pushed down, and on `More` the result read as a large empty yellow block. The
 * fix is structural: the notice becomes part of the header itself.
 *
 * Provided once by [ph.edu.umak.momompurin.ui.nav.MomoNavHost] and read by both
 * [MomoTopBar] and the Dashboard's custom header, so a screen cannot forget it and
 * cannot draw it twice.
 */
val LocalHeaderNotice = compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * The inner-screen app bar, matching the `Hi-Fi / App Bar` master (`3494:1488`):
 * 72dp yellow band, 44dp round back control, Fredoka SemiBold 18 title, optional
 * white context chip. Used by every screen; [MomoAppBar] in HiFiComponents
 * delegates here so the two cannot drift.
 */
@Composable
fun MomoTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /** The white pill on the right (`LIVE-0007` shape) when the screen carries one. */
    contextPill: String? = null,
    /**
     * An extra strip under the yellow band, in addition to [LocalHeaderNotice].
     * Screens with something of their own to say use this; the offline notice comes
     * from the composition local so it needs no call site.
     */
    bottomSlot: (@Composable () -> Unit)? = null,
) {
    val notice = LocalHeaderNotice.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MomoColors.BrandPrimary)
                // The window is edge-to-edge, so the bar owns the status-bar inset on
                // top of the master's 72dp content band.
                .windowInsetsPadding(WindowInsets.statusBars)
                // Bug 6 (real-phone report): the title used to start at 8dp, so on a
                // curved-edge phone "Home", "Orders", "Stock", "Customers", "More",
                // "Shop", "Cart" and "Account" sat almost against the bezel while the
                // cards below them started at 16dp. MomoSpace.Lg matches the card
                // gutter exactly, so every header title now lines up with the content
                // it labels.
                .padding(
                    start = if (onBack == null) MomoSpace.Lg else MomoSpace.Sm,
                    end = MomoSpace.Lg,
                    top = 12.dp,
                    bottom = 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            if (onBack != null) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "\u2039",
                        style = MomoType.TitleSmall,
                        color = MomoColors.BrandDarkest,
                    )
                }
            }
            Text(
                text = title,
                // The master's title slot is 200dp wide; reflow rather than truncate
                // (A-04 c3), but never let it push the chip out.
                style = MomoType.TitleSmall,
                color = MomoColors.BrandDarkest,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (contextPill != null) {
                Text(
                    contextPill,
                    style = MomoType.CaptionSmall,
                    color = MomoColors.BrandDarkest,
                    maxLines = 1,
                    modifier = Modifier
                        .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Pill))
                        .border(1.dp, MomoColors.BrandDeep, RoundedCornerShape(MomoRadius.Pill))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            actions()
        }
        // Bug 5 (real-phone report): the offline strip belongs to the HEADER, not to
        // the root Column. Drawn here it sits immediately under the yellow band, in
        // the same visual block, so the screen title is never displaced and no gap
        // opens between the band and the content.
        notice?.invoke()
        bottomSlot?.invoke()
    }
}

/**
 * The card surface: radius 16, white fill, ONE 1dp `Border-/-Subtle` hairline.
 * Every list/head/total card in the coded frames (J2.0's Head/Rows/Total, N8.0's
 * Head/List, B3.0's claim rows) is this shape.
 */
@Composable
fun MomoCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Card))
            .border(1.dp, MomoColors.BorderSubtle, RoundedCornerShape(MomoRadius.Card))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

/** A labelled figure, e.g. "Balance due  P280.00". */
@Composable
fun LabelledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false,
) {
    Column(modifier) {
        Text(label, style = MomoType.CaptionSmall, color = MomoColors.InkMuted, maxLines = 1)
        Text(
            text = value,
            style = if (emphasise) MomoType.DataMonospace else MomoType.DataCaption,
            color = MomoColors.InkStrong,
            maxLines = 1,
        )
    }
}

/** A hairline divider at the subtle border token (J2.0's row Divider). */
@Composable
fun MomoDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MomoColors.BorderSubtle),
    )
}
