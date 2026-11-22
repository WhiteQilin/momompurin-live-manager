package ph.edu.umak.momompurin.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * One displayed tab: what the bar needs, independent of which mode it serves.
 *
 * [badgeCount] is the live numeric badge (D.2). Only the customer's Cart tab uses
 * it - 0 renders nothing - so a tab that carries no counter is the same shape as
 * one that does and the bar has no second code path.
 */
data class BarTab(
    val route: String,
    val label: String,
    val iconRes: Int,
    val badgeCount: Int = 0,
)

/**
 * The persistent bottom navigation.
 *
 * A-02 c1-c4 (owner) and A-13..A-17 (customer) both require the same strip: every
 * tab reachable in ONE tap from any other, the active one marked with a pill, and
 * the label carrying the meaning while the pill carries the state.
 *
 * Geometry is transcribed from the `Hi-Fi / Bottom Navigation` master
 * (`3419:421`): a 92dp strip holding a 64dp yellow bar with a 2.5dp orange rule,
 * five or four equal slots, and a white pill behind the active one. The inner
 * 5dp padding is what keeps the pill clear of the curved ends.
 */
@Composable
fun MomoBottomBar(
    tabs: List<BarTab>,
    activeRoute: String?,
    onSelect: (BarTab) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MomoColors.SurfaceCream)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .padding(top = 14.dp, bottom = 14.dp)
                .padding(horizontal = 6.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MomoColors.BrandPrimary, RoundedCornerShape(999.dp))
                    .border(2.5.dp, MomoColors.BrandSecondary1Shade1, RoundedCornerShape(999.dp))
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val selected = tab.route == activeRoute
                    // E.2: the label is 12sp in BOTH bars. The five-tab owner bar
                    // used to squeeze to 10sp to fit "Customers", which is below the
                    // badge/caption floor and was the single smallest text in the
                    // app. LabelNavigation carries the -0.3sp tracking that lets
                    // "Customers" fit the ~76dp slot at 12sp on a 360dp handset
                    // without clipping, so the size no longer has to bend.
                    val compactOwnerLabel = tabs.size == 5
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onSelect(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(MomoColors.SurfaceCard, RoundedCornerShape(999.dp)),
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // D.2: the badge rides the ICON, not the label, so a
                            // count never widens the label slot and "Customers"
                            // keeps its 12sp fit in the five-tab owner bar.
                            Box {
                                Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = tab.label,
                                    tint = if (selected) {
                                        MomoColors.NavActiveInk
                                    } else {
                                        MomoColors.BrandDarkest
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                                if (tab.badgeCount > 0) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 9.dp, y = (-7).dp)
                                            .heightIn(min = 16.dp)
                                            .background(
                                                MomoColors.DangerBase,
                                                RoundedCornerShape(999.dp),
                                            )
                                            .border(
                                                1.5.dp,
                                                if (selected) {
                                                    MomoColors.SurfaceCard
                                                } else {
                                                    MomoColors.BrandPrimary
                                                },
                                                RoundedCornerShape(999.dp),
                                            )
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            if (tab.badgeCount > 9) "9+" else tab.badgeCount.toString(),
                                            style = MomoType.LabelChip,
                                            color = MomoColors.InkInverse,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = tab.label,
                                style = MomoType.LabelNavigation,
                                color = if (selected) {
                                    MomoColors.NavActiveInk
                                } else {
                                    MomoColors.BrandDarkest
                                },
                                // Owner labels stay single-line in the five equally
                                // weighted slots; the tracking is what buys the fit.
                                modifier = Modifier.padding(horizontal = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                                overflow = if (compactOwnerLabel) {
                                    TextOverflow.Clip
                                } else {
                                    TextOverflow.Ellipsis
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The owner's five tabs, in A-02's order. */
val ownerBarTabs: List<BarTab> = OwnerTab.entries.map {
    BarTab(it.route, it.label, it.iconRes)
}

/** The customer's four tabs, in A-13's order. */
val customerBarTabs: List<BarTab> = CustomerTab.entries.map {
    BarTab(it.route, it.label, it.iconRes)
}
