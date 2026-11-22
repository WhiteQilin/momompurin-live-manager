package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import ph.edu.umak.momompurin.ui.theme.MomoDate

/**
 * A-04 Process Orders - the owner's list.
 *
 * A-04 c3's seven tabs are a PARTITION of the lifecycle stages, so an order is in
 * exactly one tab and no order can hide behind two. Payment and fulfillment are
 * separate badges on every row (A-04 c4), which is what makes ORD-1042 read
 * "Partially Paid" AND "To Pack" at once.
 *
 * [initialFilter] is A-02 c2's hand-over from a Dashboard tile: the list opens
 * already showing exactly the orders the tapped figure counted. It is read once,
 * as the starting tab, so the owner can still switch tabs afterwards.
 */
@Composable
fun OrdersScreen(
    store: MomoStore,
    onOpenOrder: (String) -> Unit,
    initialFilter: DashboardFilter? = null,
) {
    val data by store.data.collectAsState()
    var stage by remember { mutableStateOf(initialFilter.toStage()) }
    // The two tiles that are NOT a single stage - "Unpaid or overdue" spans
    // several - need their own predicate alongside the stage chip.
    var special by remember { mutableStateOf(initialFilter) }
    var query by remember { mutableStateOf("") }

    val results = remember(data.orders, data.products, stage, special, query) {
        data.searchOrders(query).filter { order ->
            when {
                // A-02 c2: "Unpaid or overdue" is the Dashboard predicate, not a
                // stage - it spans Waiting for Payment and To Pack alike.
                special == DashboardFilter.UNPAID ->
                    order.balance > 0 &&
                        order.stage != OrderStage.ORDER_PLACED &&
                        order.stage != OrderStage.CANCELLED
                // The "To Pack" tab excludes held orders so it agrees with A-02's
                // tile: a held order waits for A-06's Combine, not for the packing
                // table.
                stage == OrderStage.TO_PACK ->
                    order.stage == OrderStage.TO_PACK && !order.isHeld
                else -> stage == null || order.stage == stage
            }
        }
    }

    /** Leaving the Dashboard tile's special predicate by tapping any chip. */
    fun selectStage(next: OrderStage?) {
        stage = next
        special = null
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Orders")

        // Bug 7 (real-phone report): the search field used to start at 0dp below the
        // bar, so its label kissed the yellow band's lower edge while Shop, Stock and
        // Customers all breathed. TopContentGap is that same 16dp, named once.
        Column(
            Modifier.padding(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.TopContentGap,
            ),
        ) {
            MomoField(
                value = query,
                onValueChange = { query = it },
                label = "Search order number or customer",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // A-04 c3: All, Incoming, Waiting for Payment, To Pack, Shipped, Completed,
        // Cancelled. "Incoming" is Order Placed, which is the name the criterion
        // gives that queue.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Md),
            horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
        ) {
            StageChip(
                label = "All",
                selected = stage == null && special == null,
                onClick = { selectStage(null) },
            )
            StageChip(
                label = "Incoming",
                selected = stage == OrderStage.ORDER_PLACED,
                onClick = { selectStage(OrderStage.ORDER_PLACED) },
            )
            listOf(
                OrderStage.WAITING_PAYMENT,
                OrderStage.TO_PACK,
                OrderStage.SHIPPED,
                OrderStage.COMPLETED,
                OrderStage.CANCELLED,
            ).forEach { s ->
                StageChip(
                    label = s.label,
                    selected = stage == s,
                    onClick = { selectStage(s) },
                )
            }
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
            if (results.isEmpty()) {
                // A-04 c3's zero-match state, e.g. searching "zzz".
                item {
                    Text(
                        "No orders found.",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextSecondary,
                        modifier = Modifier.padding(vertical = MomoSpace.Xl),
                    )
                }
            }
            items(results, key = { it.orderId }) { order ->
                OwnerOrderRow(
                    order = order,
                    customerName = data.customerName(order.customerEmail),
                    overdue = order.isOverdue(store.now),
                    onClick = { onOpenOrder(order.orderId) },
                )
            }
        }
    }
}

/**
 * A-02 c2: the stage chip a Dashboard tile lands on.
 *
 * "Incoming" IS "Pending orders" (both are stage Order Placed), so those two
 * agree by construction. "Unpaid or overdue" is not a stage at all and maps to
 * null with its own predicate carried alongside.
 */
private fun DashboardFilter?.toStage(): OrderStage? = when (this) {
    DashboardFilter.PENDING -> OrderStage.ORDER_PLACED
    DashboardFilter.TO_PACK -> OrderStage.TO_PACK
    DashboardFilter.UNPAID, null -> null
}

@Composable
fun StageChip(label: String, selected: Boolean, onClick: () -> Unit) {    Box(
        Modifier
            .heightIn(min = 32.dp)
            .background(
                if (selected) MomoColors.ButtonPrimaryFill else MomoColors.SurfaceCard,
                RoundedCornerShape(MomoRadius.Pill),
            )
            .border(
                1.dp,
                if (selected) MomoColors.BrandDeep else MomoColors.BorderSubtle,
                RoundedCornerShape(MomoRadius.Pill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MomoSpace.Md, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MomoType.LabelChip,
            color = if (selected) MomoColors.BrandDarkest else MomoColors.TextSecondary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun OwnerOrderRow(
    order: Order,
    customerName: String,
    overdue: Boolean,
    onClick: () -> Unit,
) {
    MomoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${order.orderId} \u00B7 $customerName",
                    style = MomoType.BodyDefault,
                    color = MomoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
        // A-04 c4: the two tracks, side by side and never merged.
        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Xs)) {
            StatusBadge(MomoBadges.forPayment(order.paymentStatus, overdue))
            StatusBadge(MomoBadges.forStage(order.stage))
            if (order.combinedShipmentNo != null) {
                StatusBadge(
                    ph.edu.umak.momompurin.ui.components.BadgeSpec(
                        label = order.combinedShipmentNo!!,
                        width = 78.dp,
                        fill = MomoColors.InfoSurface,
                        ink = MomoColors.InfoText,
                    ),
                )
            }
        }
    }
}

/**
 * A-04 c5-c9 + A-05 c6: one order's detail, with every action that criterion set
 * defines.
 *
 * The two rules that shape this screen:
 *  - every critical action shows a confirmation dialog BEFORE saving;
 *  - a Paid order is read-only, and its Cancel is replaced by the message that
 *    says why (A-05 c6, A-16 c7).
 */
@Composable
fun OrderDetailScreen(
    store: MomoStore,
    orderId: String,
    onBack: () -> Unit,
    onOpenPayment: (String) -> Unit,
    onOpenInvoice: (String) -> Unit,
    onOpenPack: (String) -> Unit,
    onOpenShipping: (String) -> Unit,
) {
    val data by store.data.collectAsState()
    val order = data.order(orderId)
    val offline by store.offlineMode.collectAsState()

    var showInvoiceDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showUnmarkDialog by remember { mutableStateOf(false) }
    // Starts EMPTY, with "0.00" shown as the placeholder instead of as text.
    //
    // A prefilled "0.00" is not a hint, it is a value: typing "50" into it appended
    // rather than replaced, so the field read "0.0050" and the invoice went out
    // with a ₱0.01 shipping fee - on a real money document (v2.3 HCI audit F-04).
    // Blank already means "no fee yet" to OrderOps.parseFee, so an untouched field
    // still issues a held order exactly as before.
    var shippingFee by remember { mutableStateOf("") }
    var cancelReason by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    if (order == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = orderId, onBack = onBack)
            Text("That order no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    val overdue = order.isOverdue(store.now)
    // A-05 c6: Paid locks editing. A-04 c8: Paid blocks cancelling.
    val locked = order.isPaid
    val canSendInvoice = order.stage == OrderStage.ORDER_PLACED && !offline
    val canCancel = !locked && order.packingStatus !=
        ph.edu.umak.momompurin.data.entity.PackingStatus.PACKED &&
        order.stage != OrderStage.CANCELLED && !offline

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(
            title = order.orderId,
            onBack = onBack,
            // A.3: the header pill is bound to the LIVE order state, not a snapshot.
            // It reads `order.stage.label` on every recomposition, so marking the
            // order Paid (or unmarking it, or dispatching it) updates the pill in
            // the same frame as the badges below - the old pill froze at the stage
            // the screen was opened with.
            contextPill = when {
                order.isPaid -> "Paid \u00B7 locked"
                order.stage == OrderStage.CANCELLED -> "Cancelled"
                else -> order.stage.label
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                KeyValueRow("Customer", data.customerName(order.customerEmail))
                KeyValueRow("Placed", MomoDate.formatDateTime(order.placedAt))
                KeyValueRow("Fulfillment", order.fulfillment.label)
                if (overdue) {
                    KeyValueRow(
                        "Payment window",
                        "Overdue",
                        valueColor = MomoColors.DangerInk,
                    )
                }
            }

            MomoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Status",
                        style = MomoType.TitleSmall,
                        color = MomoColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (locked) {
                        Text(
                            "Paid \u00B7 locked",
                            style = MomoType.CaptionBold,
                            color = MomoColors.SuccessText,
                        )
                    }
                }
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Xs)) {
                    StatusBadge(MomoBadges.forPayment(order.paymentStatus, overdue))
                    StatusBadge(MomoBadges.forStage(order.stage))
                    MomoBadges.forPacking(order.packingStatus)?.let { StatusBadge(it) }
                    MomoBadges.forShipping(order.shippingStatus)?.let { StatusBadge(it) }
                }
                if (order.trackingNo.isNotBlank() && order.showsTracking) {
                    Spacer(Modifier.height(MomoSpace.Sm))
                    KeyValueRow("Tracking number", order.trackingNo)
                }
                if (order.courier.isNotBlank()) {
                    KeyValueRow("Delivery route", order.courier)
                }
            }

            // A-04 c5-c6: the invoice controls, only while the order is Order Placed.
            if (order.stage == OrderStage.ORDER_PLACED) {
                // A.1: the fee's own validity, computed on every keystroke so the
                // error appears under the FIELD as the text goes wrong - not buried
                // below the Items card, which is where the tester had to scroll to
                // find it.
                val feeValid = shippingFee.isBlank() || OrderOps.parseFee(shippingFee) != null
                MomoCard {
                    Text(
                        "Send the invoice",
                        style = MomoType.TitleSmall,
                        color = MomoColors.TextPrimary,
                    )
                    Spacer(Modifier.height(MomoSpace.Sm))
                    MomoField(
                        value = shippingFee,
                        onValueChange = { shippingFee = it; notice = null },
                        label = "Shipping fee",
                        labelOutside = true,
                        // "0.00" is the hint, not the value (v2.3 HCI audit F-04).
                        placeholder = "0.00",
                        supporting = "Leave 0.00 for a held order",
                        // A.1: a decimal keyboard so a fee cannot even be typed with
                        // letters on the soft keyboard (a paste can still, which is
                        // what the inline validation catches).
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        error = if (!feeValid) OrderOps.INVALID_SHIPPING_FEE else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(MomoSpace.Md))
                    MomoButton(
                        label = "Confirm & Send Invoice",
                        onClick = { showInvoiceDialog = true },
                        // A.1: the dialog cannot even open over a malformed fee.
                        enabled = canSendInvoice && feeValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (offline) {
                        Text(
                            "Read-only while offline.",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.DangerInk,
                        )
                    }
                }
            }

            // A-05 c1: the money, and the actions on it.
            MomoCard {
                Text("Payment", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))
                KeyValueRow("Item subtotal", MomoMoney.format(order.itemSubtotal))
                KeyValueRow("Shipping fee", MomoMoney.format(order.shippingFee))
                KeyValueRow("Total", MomoMoney.format(order.total), emphasise = true)
                KeyValueRow("Paid", MomoMoney.format(order.amountPaid))
                KeyValueRow(
                    "Balance",
                    MomoMoney.format(order.balance),
                    emphasise = true,
                    valueColor = if (order.balance > 0) MomoColors.DangerInk else MomoColors.SuccessText,
                )
                if (order.invoiceNo.isNotBlank()) {
                    KeyValueRow("Invoice", order.invoiceNo)
                }
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                    // Bug 2 (real-phone report): a cancelled order used to keep an
                    // active "Record payment" button, which let money be booked
                    // against an order whose stock had already been returned. A
                    // cancelled order takes no payment at all - the owner restores it
                    // first - so the control is hidden outright rather than shown
                    // disabled, because there is no action left for it to offer.
                    if (!locked && order.stage != OrderStage.ORDER_PLACED &&
                        order.stage != OrderStage.CANCELLED
                    ) {
                        MiniAction(
                            label = "Record payment",
                            onClick = { onOpenPayment(orderId) },
                            emphasised = true,
                            enabled = !offline,
                        )
                    }
                    if (order.invoiceNo.isNotBlank()) {
                        MiniAction(
                            label = "View invoice",
                            onClick = { onOpenInvoice(orderId) },
                        )
                    }
                    // A-05 c6: Unmark Paid, pre-dispatch only.
                    if (locked && OrderOps.canUnmarkPaid(order)) {
                        MiniAction(
                            label = "Unmark Paid",
                            onClick = { showUnmarkDialog = true },
                            destructive = true,
                            enabled = !offline,
                        )
                    }
                }
            }

            // A-07: the packing and shipping entries.
            if (order.stage == OrderStage.TO_PACK || order.stage == OrderStage.SHIPPED) {
                MomoCard {
                    Text("Packing and shipping",
                        style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                    Spacer(Modifier.height(MomoSpace.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                        MiniAction(
                            label = "Pack order",
                            onClick = { onOpenPack(orderId) },
                            enabled = !offline,
                        )
                        MiniAction(
                            label = "Shipping details",
                            onClick = { onOpenShipping(orderId) },
                            enabled = !offline,
                        )
                    }
                }
            }

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
                                listOfNotNull(
                                    line.variantName.takeIf { it.isNotBlank() },
                                    "\u00D7 ${line.qty}",
                                ).joinToString(" \u00B7 "),
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
            }

            if (order.cancellation != null) {
                MomoCard {
                    Text(
                        "Cancelled by ${order.cancellation!!.by.label}",
                        style = MomoType.TitleSmall,
                        color = MomoColors.DangerInk,
                    )
                    Text(
                        order.cancellation!!.reason,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                    Text(
                        MomoDate.formatDateTime(order.cancellation!!.at),
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
            }

            if (notice != null) {
                Text(
                    notice!!,
                    style = MomoType.CaptionBold,
                    color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                if (canCancel) {
                    MiniAction(
                        label = "Cancel order",
                        onClick = { showCancelDialog = true },
                        destructive = true,
                    )
                }
                if (order.stage == OrderStage.CANCELLED) {
                    MiniAction(
                        label = "Restore",
                        emphasised = true,
                        onClick = {
                            val result = store.restoreOrder(orderId)
                            when (result) {
                                is OrderOps.CancelResult.Cancelled -> {
                                    notice = result.message
                                    isError = false
                                }
                                is OrderOps.CancelResult.Refused -> {
                                    notice = result.message
                                    isError = true
                                }
                            }
                        },
                    )
                }
            }

            // A-04 c8's refusals, stated where the tester will look for them.
            if (locked) {
                Text(
                    OrderOps.CANNOT_CANCEL_PAID,
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            } else if (order.packingStatus == ph.edu.umak.momompurin.data.entity.PackingStatus.PACKED) {
                Text(
                    OrderOps.CANNOT_CANCEL_PACKED,
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

    // ---------------------------------- A-04 c6: the invoice confirmation -----
    if (showInvoiceDialog) {
        MomoConfirmDialog(
            title = "Confirm & Send Invoice",
            message = "Invoice MP-${orderId.removePrefix("ORD-")} will be sent to " +
                "${data.customerName(order.customerEmail)} and the 24-hour payment " +
                "window will start.",
            confirmLabel = "Send invoice",
            dismissLabel = "Cancel",
            onConfirm = {
                showInvoiceDialog = false
                when (val result = store.sendInvoice(orderId, shippingFee)) {
                    is OrderOps.InvoiceResult.Sent -> {
                        notice = "Invoice ${result.invoiceNo} sent."
                        isError = false
                    }
                    is OrderOps.InvoiceResult.Refused -> {
                        notice = result.message
                        isError = result.message == OrderOps.INVALID_SHIPPING_FEE
                    }
                }
            },
            onDismiss = { showInvoiceDialog = false },
        )
    }

    // ------------------------------------ A-04 c7: the cancel confirmation ----
    if (showCancelDialog) {
        OwnerReasonDialog(
            title = "Cancel order",
            reason = cancelReason,
            onReasonChange = { cancelReason = it },
            confirmLabel = "Cancel order",
            onConfirm = {
                showCancelDialog = false
                when (
                    val result = store.cancelOrder(
                        orderId = orderId,
                        by = CancelledBy.OWNER,
                        reason = cancelReason,
                    )
                ) {
                    is OrderOps.CancelResult.Cancelled -> {
                        notice = result.message
                        isError = false
                    }
                    is OrderOps.CancelResult.Refused -> {
                        notice = result.message
                        isError = true
                    }
                }
            },
            onDismiss = { showCancelDialog = false },
        )
    }

    // ------------------------------------------- A-05 c6: Unmark Paid --------
    if (showUnmarkDialog) {
        MomoConfirmDialog(
            title = "Unmark Paid",
            // A.3: the new status is DERIVED from the payment rows, so the dialog
            // states what it will become rather than promising "Partially Paid" -
            // an order whose rows still sum to the total stays Paid-shaped in its
            // balance, and voiding a row (on the payment screen) is what truly
            // reopens it.
            message = "This order returns to its recorded balance " +
                "(${MomoMoney.format(order.balance)} remains on record) and the " +
                "change is written to Edit History. You can remove individual " +
                "payments from Record payment.",
            confirmLabel = "Unmark Paid",
            dismissLabel = "Keep Paid",
            destructive = true,
            onConfirm = {
                showUnmarkDialog = false
                when (val result = store.unmarkPaid(orderId)) {
                    is OrderOps.UnmarkResult.Applied -> {
                        notice = OrderOps.ORDER_UNMARKED
                        isError = false
                    }
                    is OrderOps.UnmarkResult.Refused -> {
                        notice = result.message
                        isError = true
                    }
                }
            },
            onDismiss = { showUnmarkDialog = false },
        )
    }
}

/** A confirmation with a reason field, shared by the owner's cancel and restore. */
@Composable
fun OwnerReasonDialog(
    title: String,
    reason: String,
    onReasonChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
            MomoField(
                value = reason,
                onValueChange = onReasonChange,
                label = "Reason",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Md)) {
                MomoButton(
                    label = "Keep order",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    labelFontSize = 13.sp,
                    labelHorizontalPadding = 10.dp,
                )
                MomoButton(
                    label = confirmLabel,
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
