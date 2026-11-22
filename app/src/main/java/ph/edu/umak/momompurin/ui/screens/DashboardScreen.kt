package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import ph.edu.umak.momompurin.ui.theme.MomoDate

/**
 * A-02 Owner Dashboard.
 *
 * The four tiles are interactive (A-02 c2) and each opens the list its own
 * criterion names. Today's sales includes unpaid orders at FULL value, which is
 * A-02 c2's explicit rule - the figure a naive "sum of payments received" gets
 * wrong.
 *
 * Every tile hands over a [DashboardFilter] so the Orders screen opens ALREADY
 * filtered to the figure the owner just tapped. Sending all four to an
 * unfiltered list would make the tile a number with no matching list behind it.
 *
 * E.1: the notification bell sits beside the date line and opens the derived
 * alert sheet - incoming orders, overdue invoices, low stock.
 */
@Composable
fun DashboardScreen(
    store: MomoStore,
    onOpenOrders: (DashboardFilter) -> Unit,
    onOpenHeld: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenStock: () -> Unit,
) {
    val data by store.data.collectAsState()
    val owner = store.session.value
    val now = store.now

    val pending = data.pendingOrders()
    val unpaid = data.unpaidOrOverdue()
    val held = data.heldOrders()
    val toPack = data.toPackOrders()
    val lowStock = data.lowStockProducts()

    // E.1: the alert sheet's open state, and the alerts themselves - derived per
    // composition, so a paid order leaves the list the moment it is paid.
    var showAlerts by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val alerts = ph.edu.umak.momompurin.ui.components.ownerAlerts(data, now)

    // The Dashboard draws no app bar, so its outer Column (below) owns the
    // status-bar inset directly via windowInsetsPadding - the same pattern
    // MomoTopBar uses, applied one level up so the header notice sits inside it.

    // Bug 5: this screen draws no MomoTopBar, so it renders the header notice
    // itself - the same composition local every other screen's top bar reads.
    val headerNotice = ph.edu.umak.momompurin.ui.components.LocalHeaderNotice.current

    // HOTFIX: the notice used to be a LazyColumn item with
    // `.padding(horizontal = -MomoSpace.Lg)` to cancel the list's gutter. Compose
    // rejects a negative Dp - "Padding must be non-negative" - so turning Offline
    // mode ON and opening Home crashed the app outright (DashboardScreen.kt:109).
    //
    // The fix is structural rather than numeric: the notice moves OUT of the padded
    // LazyColumn and into an outer Column, where it is edge-to-edge by construction
    // and needs no negative offset. The whole Column owns the status-bar inset
    // once - the strip then sits BELOW the clock, not under it - and the list
    // keeps its own gutter, so every card still lines up with the 16dp margin the
    // rest of the app uses.
    Column(
        Modifier
            .fillMaxSize()
            .background(MomoColors.SurfaceCanvas)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        if (headerNotice != null) {
            Box(Modifier.fillMaxWidth()) { headerNotice() }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.Lg,
                bottom = MomoSpace.Xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        // A-02 c1: "Today" plus the current date.
                        Text(
                            "Today \u00B7 ${MomoDate.formatDate(now)}",
                            style = MomoType.CaptionBold,
                            color = MomoColors.TextSecondary,
                        )
                        Text(
                            // A-02 c4: the shop name and the signed-in owner's email.
                            data.setting("business_name") ?: "Momompurin",
                            style = MomoType.HeadingMedium,
                            color = MomoColors.BrandDeep,
                        )
                        Text(
                            owner?.email.orEmpty(),
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                    // E.1: the bell with the unread count.
                    ph.edu.umak.momompurin.ui.components.NotificationBell(
                        count = alerts.size,
                        onClick = { showAlerts = true },
                    )
                }
            }
        }

        item {
            MomoCard {
                Text("Today's sales", style = MomoType.CaptionSmall, color = MomoColors.TextSecondary)
                Text(
                    MomoMoney.format(data.todaysSales(now)),
                    style = MomoType.DisplayLarge,
                    color = MomoColors.BrandDeep,
                )
                Text(
                    "${data.todaysOrderCount(now)} orders placed today",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Md)) {
                Tile(
                    label = "Pending orders",
                    count = pending.size,
                    onClick = { onOpenOrders(DashboardFilter.PENDING) },
                    modifier = Modifier.weight(1f),
                )
                Tile(
                    label = "Unpaid or overdue",
                    count = unpaid.size,
                    onClick = { onOpenOrders(DashboardFilter.UNPAID) },
                    modifier = Modifier.weight(1f),
                    alert = unpaid.any { it.isOverdue(now) },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Md)) {
                Tile(
                    label = "Held orders",
                    count = held.size,
                    onClick = onOpenHeld,
                    modifier = Modifier.weight(1f),
                )
                Tile(
                    label = "To pack",
                    count = toPack.size,
                    onClick = { onOpenOrders(DashboardFilter.TO_PACK) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            MomoCard(Modifier.clickable(onClick = onOpenStock)) {
                Text(
                    "Low stock alerts",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                if (lowStock.isEmpty()) {
                    // A-02 c3's empty state.
                    Text(
                        "No low-stock items.",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                } else {
                    lowStock.forEach { product ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = MomoSpace.Xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${product.sku} \u00B7 ${product.name}",
                                style = MomoType.BodyDefault,
                                color = MomoColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                // A-02 c3: the AVAILABLE count, not on-hand.
                                "${product.available} available",
                                style = MomoType.DataCaption,
                                color = if (product.available == 0) {
                                    MomoColors.DangerInk
                                } else {
                                    MomoColors.WarningText
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Recent orders",
                style = MomoType.TitleSmall,
                color = MomoColors.TextPrimary,
                modifier = Modifier.padding(top = MomoSpace.Sm),
            )
        }
        items(data.ordersNewestFirst().take(4), key = { it.orderId }) { order ->
            RecentOrderRow(
                order = order,
                customerName = data.customerName(order.customerEmail),
                onClick = { onOpenOrder(order.orderId) },
            )
        }

        }
    }

    // E.1: the derived alert sheet. Tapping an order alert opens that order.
    if (showAlerts) {
        ph.edu.umak.momompurin.ui.components.NotificationSheet(
            alerts = alerts,
            onOpenOrder = onOpenOrder,
            onDismiss = { showAlerts = false },
        )
    }
}

/**
 * A-02 c2: which list a Dashboard tile opens.
 *
 * Each value is the predicate its own criterion states, so the tile's number and
 * the list it opens can never disagree - a tile whose count is 4 must not open a
 * list of 12.
 *
 * [INCOMING] is A-04's "Incoming" tab (stage Order Placed), which is the same
 * set as the "Pending orders" tile.
 */
enum class DashboardFilter(val wire: String) {
    /** Pending orders: stage Order Placed. */
    PENDING("pending"),

    /** Unpaid or overdue: money still owed past Incoming. */
    UNPAID("unpaid"),

    /** To pack: stage To Pack, excluding held orders. */
    TO_PACK("to_pack"),
    ;

    companion object {
        fun fromWire(value: String?): DashboardFilter? =
            entries.firstOrNull { it.wire == value }
    }
}

/**
 * One dashboard tile.
 *
 * A-02 c2: the figure IS the control - tapping it opens the matching list - so the
 * whole card is the tap target rather than a separate link beside a number.
 */
@Composable
private fun Tile(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alert: Boolean = false,
) {
    Column(
        modifier
            .heightIn(min = 96.dp)
            .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Card))
            .border(
                2.dp,
                if (alert) MomoColors.DangerBase else MomoColors.BorderSubtle,
                RoundedCornerShape(MomoRadius.Card),
            )
            .clickable(onClick = onClick)
            .padding(MomoSpace.Md),
    ) {
        Text(
            count.toString(),
            style = MomoType.DisplayLarge,
            color = if (alert) MomoColors.DangerInk else MomoColors.BrandDeep,
        )
        Text(
            label,
            style = MomoType.CaptionBold,
            color = MomoColors.TextPrimary,
            maxLines = 2,
        )
    }
}

@Composable
private fun RecentOrderRow(order: Order, customerName: String, onClick: () -> Unit) {
    MomoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // A-02 c6's row shape: "ORD-1042 - Pia Santos - P880.00".
                Text(
                    "${order.orderId} \u00B7 $customerName",
                    style = MomoType.BodyDefault,
                    color = MomoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    MomoMoney.format(order.total),
                    style = MomoType.DataCaption,
                    color = MomoColors.BrandDeep,
                )
            }
            StatusBadge(MomoBadges.forStage(order.stage))
        }
    }
}
