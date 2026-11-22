package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
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
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.QrEncoder
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoDivider
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
    var showReceived by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

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

    // UAT fix (item 8): an invoice only exists once the OWNER has confirmed and
    // sent it. `stage == ORDER_PLACED` is precisely the window in which it does
    // not, so the View invoice control is absent there rather than leading to an
    // empty document.
    val invoiceAvailable = order.stage != OrderStage.ORDER_PLACED &&
        order.invoiceNo.isNotBlank()

    // UAT fix (item 5): the customer's own receipt confirmation, offered only
    // while the parcel is with the courier.
    val canConfirmReceipt = order.shippingStatus == ShippingStatus.IN_TRANSIT

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

            // UAT fix (item 8): before the invoice exists the order is with the
            // seller, and the customer's only question is "what is happening?".
            // The badge names the state in the customer's terms and the helper
            // text says what the seller is actually doing, so nobody waits for a
            // payment screen that has not been created yet.
            if (order.stage == OrderStage.ORDER_PLACED) {
                item {
                    MomoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(MomoBadges.forApproval())
                        }
                        Spacer(Modifier.height(MomoSpace.Sm))
                        Text(
                            "The seller is reviewing your order and setting the " +
                                "shipping fee before issuing your invoice.",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                }
            }

            // UAT fix (item 9): once approved, the two things the customer needs to
            // act are the deadline and where to send the money - so both are in one
            // card, above the totals rather than below them.
            if (invoiceAvailable && order.balance > 0) {
                item {
                    PaymentCard(order = order, store = store)
                }
            }

            // UAT fix (item 5): the customer's `Order Received` confirmation.
            if (canConfirmReceipt) {
                item {
                    MomoCard {
                        Text(
                            "Did your order arrive?",
                            style = MomoType.TitleSmall,
                            color = MomoColors.TextPrimary,
                        )
                        Spacer(Modifier.height(MomoSpace.Xs))
                        Text(
                            "Confirm once you have the parcel so the seller can " +
                                "close the order.",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                        Spacer(Modifier.height(MomoSpace.Md))
                        MomoButton(
                            label = "Order Received",
                            onClick = { showReceived = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
                    // UAT fix (item 8): only once it exists.
                    if (invoiceAvailable) {
                        MiniAction(
                            label = "View invoice",
                            onClick = { onOpenInvoice(orderId) },
                            emphasised = true,
                        )
                    }
                    if (canCancel) {
                        MiniAction(
                            label = "Cancel order",
                            onClick = { showCancel = true },
                            destructive = true,
                        )
                    }
                }
            }

            if (notice != null) {
                item {
                    Text(
                        notice!!,
                        style = MomoType.CaptionBold,
                        color = MomoColors.SuccessText,
                    )
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

    // UAT fix (item 5): a confirmation before the order closes - this is the last
    // irreversible step the CUSTOMER takes on their own order, so it asks first.
    if (showReceived) {
        MomoConfirmDialog(
            title = "Order Received",
            message = "Confirm that the parcel for $orderId has arrived. The order " +
                "will be marked Received and Completed.",
            confirmLabel = "Order Received",
            dismissLabel = "Not yet",
            onConfirm = {
                showReceived = false
                when (val result = store.markOrderReceived(orderId)) {
                    is OrderOps.ReceiveResult.Received -> {
                        notice = OrderOps.ORDER_RECEIVED_CONFIRMED
                    }
                    is OrderOps.ReceiveResult.Refused -> {
                        // The refusal states why; showing it as an error keeps a
                        // "already received" answer from reading as success.
                        notice = result.message
                    }
                }
            },
            onDismiss = { showReceived = false },
        )
    }
}

/**
 * UAT fix (item 9): the payment due date and the payment QR, once approved.
 *
 * ## The due date
 *
 * A-04 c6 opens a 24-hour window when the invoice is sent, so the deadline is
 * `invoiceSentAt + 24 hours`. That deadline is already stored on the order
 * ([Order.paymentDeadline]), which is what the owner's screen counts down - so
 * this card reads the SAME field rather than adding 24 hours again here. A second
 * derivation would be a second source of truth for a money deadline, and the two
 * would eventually disagree by however much the clocks drifted.
 *
 * When no deadline is stored (an order invoiced before the field existed), the
 * card says so instead of inventing one.
 *
 * ## The QR
 *
 * Generated locally by [QrEncoder] from the order's own payment details - there
 * is no network call, no bitmap asset to ship, and nothing to load asynchronously,
 * so the code is on screen the frame the card composes. The payload names the
 * shop, the amount owed and the reference, which is what a GCash/Maya transfer
 * needs to be matched to this order by hand.
 *
 * If the encoder refuses the payload (it is capped at version 10), the card shows
 * the reference and amount as text and says the QR is unavailable - it never
 * renders a code that does not encode the right thing.
 */
@Composable
private fun PaymentCard(order: Order, store: MomoStore) {
    MomoCard {
        Text(
            "Payment",
            style = MomoType.TitleSmall,
            color = MomoColors.TextPrimary,
        )
        Spacer(Modifier.height(MomoSpace.Sm))

        // ---- the deadline -----------------------------------------------------
        val deadline = order.paymentDeadline
        if (deadline != null) {
            val overdue = order.isOverdue(store.now)
            KeyValueRow(
                "Pay by",
                MomoDate.formatDateTime(deadline),
                valueColor = if (overdue) MomoColors.DangerInk else MomoColors.TextPrimary,
            )
            Text(
                if (overdue) {
                    "The 24-hour payment window has passed."
                } else {
                    "Due within 24 hours of the invoice being sent."
                },
                style = MomoType.CaptionSmall,
                color = if (overdue) MomoColors.DangerInk else MomoColors.TextSecondary,
            )
        } else {
            Text(
                "The payment deadline is being set by the seller.",
                style = MomoType.CaptionSmall,
                color = MomoColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(MomoSpace.Md))
        MomoDivider()
        Spacer(Modifier.height(MomoSpace.Md))

        // ---- the QR -----------------------------------------------------------
        KeyValueRow("Amount due", MomoMoney.format(order.balance), emphasise = true)
        if (order.invoiceNo.isNotBlank()) {
            KeyValueRow("Reference", order.invoiceNo)
        }

        Spacer(Modifier.height(MomoSpace.Md))
        val payload = paymentPayload(order)
        val matrix = remember(payload) { QrEncoder.encode(payload) }
        if (matrix != null) {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                QrCode(matrix = matrix, modifier = Modifier.size(180.dp))
            }
        } else {
            Text(
                "The payment QR is unavailable on this device. Use the reference " +
                    "and amount above.",
                style = MomoType.CaptionSmall,
                color = MomoColors.WarningText,
            )
        }

        Spacer(Modifier.height(MomoSpace.Md))
        Text(
            "Scan with GCash or Maya, or pay by Bank Transfer.",
            style = MomoType.CaptionSmall,
            color = MomoColors.TextSecondary,
        )
    }
}

/**
 * UAT fix (item 9): the text the payment QR encodes.
 *
 * A plain, human-checkable summary rather than a payment-provider URI: the shop
 * collects off-app (A-15 c8), so the code's job is to carry the reference and the
 * amount to whoever is looking at the customer's screen. A scheme the app cannot
 * honour - a `gcash://` deep link it has no way to test - would be a promise the
 * build cannot keep.
 *
 * Kept as its own function so a test can assert the payload rather than only the
 * matrix.
 */
fun paymentPayload(order: Order): String = listOf(
    "Momompurin",
    "Invoice ${order.invoiceNo.ifBlank { order.orderId }}",
    "Amount PHP ${MomoMoney.plain(order.balance)}",
).joinToString(" | ")

/**
 * Draws a QR matrix on a Canvas (UAT fix, item 9).
 *
 * Each module is one filled square, with FOUR modules of quiet zone - the border
 * the standard requires for a scanner to find the finder patterns. Without it a
 * camera can read a QR on paper but struggles with one drawn edge-to-edge inside
 * a card.
 */
@Composable
private fun QrCode(matrix: Array<BooleanArray>, modifier: Modifier = Modifier) {
    val dark = MomoColors.TextPrimary
    val light = MomoColors.SurfaceCard
    androidx.compose.foundation.Canvas(modifier) {
        val modules = matrix.size
        val quiet = 4
        val total = modules + quiet * 2
        val cell = size.minDimension / total
        drawRect(color = light, size = size)
        for (row in 0 until modules) {
            for (col in 0 until modules) {
                if (!matrix[row][col]) continue
                drawRect(
                    color = dark,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = (col + quiet) * cell,
                        y = (row + quiet) * cell,
                    ),
                    size = androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f),
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, spec: ph.edu.umak.momompurin.ui.components.BadgeSpec?) {    Row(
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
