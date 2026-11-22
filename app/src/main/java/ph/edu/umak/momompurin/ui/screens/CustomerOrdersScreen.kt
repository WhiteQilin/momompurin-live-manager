package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import ph.edu.umak.momompurin.ui.theme.MomoDate

/**
 * A-16 Track Orders - the customer's own list.
 *
 * Newest first (A-16 c1), with the three sub-views the criteria name: the held
 * total (A-16 c4), the read-only invoice (A-16 c5), and Cancelled Orders with who
 * cancelled, why and when (A-16 c8).
 */
@Composable
fun CustomerOrdersScreen(
    store: MomoStore,
    onOpenOrder: (String) -> Unit,
    onOpenHeld: () -> Unit,
    onOpenCancelled: () -> Unit,
) {
    val data by store.data.collectAsState()
    val email = store.session.value?.email.orEmpty()
    val orders = data.ordersFor(email)

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Orders")

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.Lg,
                // Clears the bottom navigation: the last card must not sit under it.
                bottom = MomoSpace.BottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                    MiniAction(label = "Held orders", onClick = onOpenHeld)
                    MiniAction(label = "Cancelled Orders", onClick = onOpenCancelled)
                }
            }

            if (orders.isEmpty()) {
                // A-16 c9: the brand-new-account state, e.g. Elena Cruz.
                item {
                    Text(
                        "No orders yet.",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextSecondary,
                        modifier = Modifier.padding(vertical = MomoSpace.Xl),
                    )
                }
            }

            items(orders, key = { it.orderId }) { order ->
                CustomerOrderCard(
                    order = order,
                    onOpen = { onOpenOrder(order.orderId) },
                )
            }
        }
    }
}

@Composable
fun CustomerOrderCard(order: Order, onOpen: () -> Unit) {
    MomoCard(Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    order.orderId,
                    style = MomoType.DataMonospace,
                    color = MomoColors.TextPrimary,
                )
                // A-16 c1: the date.
                Text(
                    MomoDate.formatDate(order.placedAt),
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            }
            Text(
                MomoMoney.format(order.total),
                style = MomoType.DataMonospace,
                color = MomoColors.BrandDeep,
            )
        }
        Spacer(Modifier.height(MomoSpace.Sm))
        // Payment first, then the lifecycle stage - the same order the owner's
        // card uses. Two orders in one list must not disagree about which label
        // leads, and money is the fact a customer scans for.
        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Xs)) {
            StatusBadge(MomoBadges.forPayment(order.paymentStatus))
            StatusBadge(MomoBadges.forStage(order.stage))
        }
        // A-16 c3: the tracking number appears only from In Transit onward.
        if (order.showsTracking && order.trackingNo.isNotBlank()) {
            Spacer(Modifier.height(MomoSpace.Xs))
            Text(
                order.trackingNo,
                style = MomoType.DataCaption,
                color = MomoColors.InfoText,
            )
        }
    }
}

/**
 * A-16 c4 / c6: the held orders and their total.
 *
 * The total is the sum of THIS customer's held orders, which for the seeded Mika
 * Tan is ORD-1036 P400.00 + ORD-1037 P360.00 = P760.00.
 */
@Composable
fun CustomerHeldScreen(store: MomoStore, onBack: () -> Unit) {
    val data by store.data.collectAsState()
    val email = store.session.value?.email.orEmpty()
    val held = data.heldOrdersFor(email)

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Held orders", onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.Lg,
                // Clears the bottom navigation: the last card must not sit under it.
                bottom = MomoSpace.BottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            // A-16 c6: only show a total when there IS one. The card was emitted
            // unconditionally, so a customer with no held orders saw
            // "Total held   P0.00" immediately above "No held orders." - a balance
            // card for money that does not exist.
            if (held.isNotEmpty()) {
                item {
                    MomoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Total held",
                                style = MomoType.BodyDefault,
                                color = MomoColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                MomoMoney.format(held.sumOf { it.total }),
                                style = MomoType.DataMonospace,
                                color = MomoColors.BrandDeep,
                            )
                        }
                    }
                }
            }
            if (held.isEmpty()) {
                item {
                    Text(
                        "No held orders.",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextSecondary,
                    )
                }
            }
            items(held, key = { it.orderId }) { order ->
                MomoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                order.orderId,
                                style = MomoType.DataMonospace,
                                color = MomoColors.TextPrimary,
                            )
                            Text(
                                "Held ${order.heldDays(store.now)} days",
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                        Text(
                            MomoMoney.format(order.total),
                            style = MomoType.DataMonospace,
                            color = MomoColors.BrandDeep,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A-16 c8: Cancelled Orders, showing who cancelled, the reason and the timestamp.
 */
@Composable
fun CustomerCancelledScreen(store: MomoStore, onBack: () -> Unit) {
    val data by store.data.collectAsState()
    val email = store.session.value?.email.orEmpty()
    val cancelled = data.cancelledOrdersFor(email)

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Cancelled Orders", onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.Lg,
                // Clears the bottom navigation: the last card must not sit under it.
                bottom = MomoSpace.BottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            if (cancelled.isEmpty()) {
                item {
                    Text(
                        "No cancelled orders.",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextSecondary,
                    )
                }
            }
            items(cancelled, key = { it.orderId }) { order ->
                MomoCard {
                    Text(
                        order.orderId,
                        style = MomoType.DataMonospace,
                        color = MomoColors.TextPrimary,
                    )
                    val cancellation = order.cancellation
                    if (cancellation != null) {
                        Text(
                            "Cancelled by ${cancellation.by.label}",
                            style = MomoType.CaptionBold,
                            color = MomoColors.DangerInk,
                        )
                        Text(
                            cancellation.reason,
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                        Text(
                            MomoDate.formatDateTime(cancellation.at),
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A-16 c2-c7: one order's detail with the three statuses shown SEPARATELY, the
 * read-only invoice, and the customer's own Cancel path.
 */
@Composable
fun CustomerOrderDetailScreen(
    store: MomoStore,
    orderId: String,
    onBack: () -> Unit,
    onOpenInvoice: (String) -> Unit,
) {
    val data by store.data.collectAsState()
    val order = data.order(orderId)
    var showCancel by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    if (order == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = orderId, onBack = onBack)
            Text("That order no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    // A-16 c6: cancellable while Order Placed or Waiting for Payment; A-16 c7:
    // disabled once Paid.
    val canCancel = !order.isPaid && (
        order.stage == OrderStage.ORDER_PLACED ||
            order.stage == OrderStage.WAITING_PAYMENT
        )

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = orderId, onBack = onBack, contextPill = order.stage.label)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.Lg,
                // Clears the bottom navigation: the last card must not sit under it.
                bottom = MomoSpace.BottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            item {
                MomoCard {
                    Text("Status", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                    Spacer(Modifier.height(MomoSpace.Sm))
                    // A-16 c2: three separate labels, never merged into one.
                    StatusRow("Payment", MomoBadges.forPayment(order.paymentStatus))
                    StatusRow("Packing", MomoBadges.forPacking(order.packingStatus))
                    StatusRow("Shipping", MomoBadges.forShipping(order.shippingStatus))
                    // A-16 c3: the tracking number from In Transit onward only.
                    if (order.showsTracking && order.trackingNo.isNotBlank()) {
                        Spacer(Modifier.height(MomoSpace.Sm))
                        Text(
                            "Tracking number",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                        Text(
                            order.trackingNo,
                            style = MomoType.DataMonospace,
                            color = MomoColors.TextPrimary,
                        )
                    }
                }
            }

            item {
                MomoCard {
                    Text("Items", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                    Spacer(Modifier.height(MomoSpace.Sm))
                    data.linesFor(orderId).forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = MomoSpace.Xs)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    line.productName,
                                    style = MomoType.BodyDefault,
                                    color = MomoColors.TextPrimary,
                                )
                                Text(
                                    "\u00D7 ${line.qty}",
                                    style = MomoType.CaptionSmall,
                                    color = MomoColors.TextSecondary,
                                )
                            }
                            Text(
                                MomoMoney.format(line.lineTotal),
                                style = MomoType.DataCaption,
                                color = MomoColors.TextPrimary,
                            )
                        }
                    }
                    ph.edu.umak.momompurin.ui.components.MomoDivider()
                    Spacer(Modifier.height(MomoSpace.Sm))
                    ph.edu.umak.momompurin.ui.components.KeyValueRow(
                        "Item subtotal",
                        MomoMoney.format(order.itemSubtotal),
                    )
                    ph.edu.umak.momompurin.ui.components.KeyValueRow(
                        "Shipping fee",
                        MomoMoney.format(order.shippingFee),
                    )
                    ph.edu.umak.momompurin.ui.components.KeyValueRow(
                        "Total",
                        MomoMoney.format(order.total),
                        emphasise = true,
                    )
                    ph.edu.umak.momompurin.ui.components.KeyValueRow(
                        "Paid",
                        MomoMoney.format(order.amountPaid),
                    )
                    ph.edu.umak.momompurin.ui.components.KeyValueRow(
                        "Balance",
                        MomoMoney.format(order.balance),
                        valueColor = if (order.balance > 0) {
                            MomoColors.DangerInk
                        } else {
                            MomoColors.SuccessText
                        },
                    )
                }
            }

            // A-15 c8's off-app payment note, shown to the customer on their order.
            if (order.stage == OrderStage.WAITING_PAYMENT) {
                item {
                    MomoCard {
                        Text(
                            "Payment",
                            style = MomoType.TitleSmall,
                            color = MomoColors.TextPrimary,
                        )
                        Spacer(Modifier.height(MomoSpace.Xs))
                        Text(
                            "Pay by GCash, Maya or Bank Transfer. The owner will " +
                                "coordinate the payment with you after invoice " +
                                "${order.invoiceNo}.",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                    // A-16 c5: the invoice opens read-only, with no edit or delete.
                    MiniAction(
                        label = "View invoice",
                        onClick = { onOpenInvoice(orderId) },
                        emphasised = true,
                    )
                    if (canCancel) {
                        MiniAction(
                            label = "Cancel order",
                            onClick = { showCancel = true },
                            destructive = true,
                        )
                    }
                }
            }

            if (!canCancel && order.isPaid) {
                item {
                    // A-16 c7: the reason Cancel is disabled, stated rather than
                    // leaving a tester to guess.
                    Text(
                        ph.edu.umak.momompurin.data.OrderOps.CANNOT_CANCEL_PAID,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
            }

            if (error != null) {
                item {
                    Text(
                        error!!,
                        style = MomoType.CaptionBold,
                        color = MomoColors.DangerInk,
                    )
                }
            }
        }
    }

    // A-16 c6: the reason is required, and a blank one is refused by the ops layer.
    if (showCancel) {
        MomoCancelReasonDialog(
            title = "Cancel order",
            reason = reason,
            onReasonChange = { reason = it; error = null },
            error = error,
            onConfirm = {
                val result = store.cancelOrder(
                    orderId = orderId,
                    by = ph.edu.umak.momompurin.data.entity.CancelledBy.CUSTOMER,
                    reason = reason,
                )
                when (result) {
                    is ph.edu.umak.momompurin.data.OrderOps.CancelResult.Cancelled -> {
                        showCancel = false
                    }
                    is ph.edu.umak.momompurin.data.OrderOps.CancelResult.Refused -> {
                        // Keep the dialog OPEN on a refusal. Closing it pushed the
                        // message to the bottom of a scrolling page behind a window
                        // that had just vanished, and threw away the typed reason.
                        error = result.message
                    }
                }
            },
            onDismiss = { showCancel = false },
        )
    }
}

@Composable
private fun StatusRow(label: String, spec: ph.edu.umak.momompurin.ui.components.BadgeSpec?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MomoType.CaptionSmall,
            color = MomoColors.TextSecondary,
            modifier = Modifier.width(84.dp),
        )
        if (spec != null) {
            StatusBadge(spec)
        } else {
            Text("\u2014", style = MomoType.CaptionSmall, color = MomoColors.TextSecondary)
        }
    }
}

/** The reason dialog A-16 c6 requires. */
@Composable
private fun MomoCancelReasonDialog(
    title: String,
    reason: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** A refusal renders UNDER the reason field, where the user is looking. */
    error: String? = null,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Card))
                .padding(MomoSpace.Xxl),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            Text(title, style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
            ph.edu.umak.momompurin.ui.components.MomoField(
                value = reason,
                onValueChange = onReasonChange,
                label = "Reason",
                labelOutside = true,
                error = error,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Md)) {
                ph.edu.umak.momompurin.ui.components.MomoButton(
                    label = "Keep order",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    labelFontSize = 13.sp,
                    labelHorizontalPadding = 10.dp,
                )
                ph.edu.umak.momompurin.ui.components.MomoButton(
                    label = "Cancel order",
                    onClick = onConfirm,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    labelFontSize = 13.sp,
                    labelHorizontalPadding = 10.dp,
                )
            }
        }
    }
}
