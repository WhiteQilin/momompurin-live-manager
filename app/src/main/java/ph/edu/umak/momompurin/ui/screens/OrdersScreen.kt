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
import androidx.compose.foundation.layout.size
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
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.ui.components.BadgeSpec
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoMessages
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoSearchField
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
    /** UAT fix (item 1): the Held chip's banner links to Hold and combine orders. */
    onOpenHeld: () -> Unit = {},
) {
    val data by store.data.collectAsState()
    var stage by remember { mutableStateOf(initialFilter.toStage()) }
    // The two tiles that are NOT a single stage - "Unpaid or overdue" spans
    // several - need their own predicate alongside the stage chip.
    var special by remember { mutableStateOf(initialFilter) }
    // UAT fix (item 1): the Held chip is a PREDICATE, not a stage. A held order
    // still carries a lifecycle stage (To Pack, usually), so folding it into
    // [stage] would make it invisible in whichever stage tab it also belongs to.
    var heldOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // Patch 4: the list is built from ORDER ROWS, not raw orders, so 2+ orders
    // sharing a `combinedShipmentNo` render as one combined-shipment card.
    //
    // The grouping runs INSIDE the filter, after it, rather than before: filtering
    // is per ORDER (a tab is a stage, and a shipment's members could in principle
    // straddle one), while the grouping is per PARCEL. Doing it in this order means
    // a row appears in a tab when any of its orders belongs there, and the whole
    // shipment is shown - which is the honest presentation, because the box is one
    // object even if its paperwork is several.
    val rows = remember(data.orders, data.products, stage, special, heldOnly, query) {
        val matched = data.searchOrders(query).filter { order ->
            when {
                // A-02 c2: "Unpaid or overdue" is the Dashboard predicate, not a
                // stage - it spans Waiting for Payment and To Pack alike.
                special == DashboardFilter.UNPAID ->
                    order.balance > 0 &&
                        order.stage != OrderStage.ORDER_PLACED &&
                        order.stage != OrderStage.CANCELLED
                // UAT fix (item 1): paid held orders that are still waiting to be
                // combined. This is the SAME predicate [MomoData.heldOrders] uses,
                // so the chip's count and the Hold screen's list cannot disagree.
                heldOnly -> order.isHeld && order.isPaid &&
                    order.stage != OrderStage.CANCELLED
                // Patch 2: a paid held order is NOT waiting for payment.
                //
                // Its stage is Waiting for Payment until the parcel is combined, but
                // the money has arrived, so listing it here would put an order with
                // a cleared balance in the queue of orders that still owe money -
                // and print "Waiting for Payment" on it, the contradiction the bug
                // report names. [Order.isAwaitingPayment] is the money question, and
                // it answers false for a paid hold.
                stage == OrderStage.WAITING_PAYMENT ->
                    order.stage == OrderStage.WAITING_PAYMENT &&
                        order.isAwaitingPayment
                // The "To Pack" tab excludes held orders so it agrees with A-02's
                // tile: a held order waits for A-06's Combine, not for the packing
                // table.
                stage == OrderStage.TO_PACK ->
                    order.stage == OrderStage.TO_PACK && !order.isHeld
                else -> stage == null || order.stage == stage
            }
        }
        // Patch 4: group the matches back into shipment rows, using the ONE
        // grouping implementation the data layer owns - so the list and any test
        // that pins the grouping cannot describe two different shapes.
        data.orderRows(matched)
    }

    /** Leaving the Dashboard tile's special predicate by tapping any chip. */
    fun selectStage(next: OrderStage?) {
        stage = next
        special = null
        heldOnly = false
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
            MomoSearchField(
                value = query,
                onValueChange = { query = it },
                label = "Search order number or customer",
            )
        }

        // A-04 c3: All, Incoming, Waiting for Payment, Held, To Pack, To Ship,
        // Shipped, Completed, Cancelled. "Incoming" is Order Placed, which is the
        // name the criterion gives that queue.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Md),
            horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
        ) {
            StageChip(
                label = "All",
                selected = stage == null && special == null && !heldOnly,
                onClick = { selectStage(null) },
            )
            StageChip(
                label = "Incoming",
                selected = stage == OrderStage.ORDER_PLACED,
                onClick = { selectStage(OrderStage.ORDER_PLACED) },
            )
            StageChip(
                label = OrderStage.WAITING_PAYMENT.label,
                selected = stage == OrderStage.WAITING_PAYMENT,
                onClick = { selectStage(OrderStage.WAITING_PAYMENT) },
            )
            // UAT fix (item 1): Held sits between Waiting for Payment and To Pack,
            // which is where it belongs in the owner's actual day - after the money
            // arrives and before the parcel is boxed. It is a predicate rather than
            // a stage, so selecting it leaves `stage` null and `special` null.
            StageChip(
                label = "Held",
                selected = heldOnly,
                onClick = { selectStage(null); heldOnly = true },
            )
            listOf(
                OrderStage.TO_PACK,
                OrderStage.TO_SHIP,
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
            if (rows.isEmpty()) {
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

            // UAT fix (item 1): the Held chip's companion. Filtering to held orders
            // shows the owner the queue, but the ACTION for that queue lives on
            // another screen - so the banner carries the owner there instead of
            // leaving them to find More > Hold and combine orders themselves.
            if (heldOnly && rows.isNotEmpty()) {
                // The banner counts ORDERS, not rows: a combined shipment of two is
                // still two held orders the owner has to deal with, and a count of
                // "1 order" over a pair would understate the work.
                val heldCount = rows.sumOf { it.orders.size }
                item {
                    MomoCard(Modifier.clickable(onClick = onOpenHeld)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Hold and combine orders",
                                    style = MomoType.TitleSmall,
                                    color = MomoColors.BrandDeep,
                                )
                                Text(
                                    "$heldCount paid " +
                                        (if (heldCount == 1) "order" else "orders") +
                                        " waiting to be combined.",
                                    style = MomoType.CaptionSmall,
                                    color = MomoColors.TextSecondary,
                                )
                            }
                            Text(
                                "\u203A",
                                style = MomoType.TitleSmall,
                                color = MomoColors.BrandDeep,
                            )
                        }
                    }
                }
            }
            items(rows, key = { row -> row.orders.joinToString("+") { it.orderId } }) { row ->
                if (row.isCombined) {
                    // Patch 4: one parcel, one card.
                    CombinedShipmentCard(
                        row = row,
                        customerName = data.customerName(row.customerEmail()),
                        overdue = row.orders.any { it.isOverdue(store.now) },
                        // Tapping opens the shipment's first member. Each individual
                        // order and invoice stays reachable from the detail screen,
                        // which is what A-06 c6 requires - the grouping is about the
                        // LIST, never about hiding an order.
                        onClick = { onOpenOrder(row.orders.first().orderId) },
                    )
                } else {
                    val order = row.orders.first()
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

/**
 * Patch 4: one combined shipment, as one card.
 *
 * ## The shape
 *
 * ```
 * CS-0001 · Mika Tan                          ₱760.00
 * [Paid] [To Pack] [2 orders]
 * ORD-1037, ORD-1036
 * ```
 *
 * The header names the SHIPMENT and its customer; the combined total is the sum of
 * every member's own total, so it agrees with the two invoices it stands for. The
 * count badge is what makes the card's multiplicity explicit rather than leaving
 * the reader to infer it from the id list.
 *
 * ## What is deliberately NOT here
 *
 * The card carries no per-order money breakdown. A-06 c6 requires the individual
 * orders and their invoices to stay inspectable, and they do - each is one tap away
 * on the detail screen - but putting two sets of totals on a list card would make
 * the LIST harder to scan for the thing the owner is looking for (which parcel, for
 * whom, what state). The list groups; the detail itemizes.
 *
 * Badges use the same derived pair as a single-order row, so a shipment whose
 * members are all paid reads `Paid` and never `Partially Paid`, and the stage
 * badge is the members' SHARED stage - a shipment whose members disagree falls back
 * to [MomoBadges.forStage] of the first, because a half-advanced shipment is a data
 * state the pack/ship fan-out exists to prevent, not one this card should dress up.
 */
@Composable
private fun CombinedShipmentCard(
    row: MomoData.OrderRow,
    customerName: String,
    overdue: Boolean,
    onClick: () -> Unit,
) {
    MomoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${row.shipmentNo ?: "Combined"} \u00B7 $customerName",
                    style = MomoType.BodyDefault,
                    color = MomoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Patch 4: the member ids, in the order the card lists them. A-06
                // c2's example is exactly this pair, so the two identifiers the
                // owner knows are on the card they are looking at.
                Text(
                    row.orderIds.joinToString(", "),
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                MomoMoney.format(row.total),
                style = MomoType.DataMonospace,
                color = MomoColors.BrandDeep,
            )
        }
        Spacer(Modifier.height(MomoSpace.Sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Xs)) {
            // The shipment is paid only when every member is, which is what
            // [OrderRow.isFullyPaid] answers - a combined parcel is paid for as one
            // box, so a member still owing money keeps the whole card off `Paid`.
            StatusBadge(
                if (row.isFullyPaid) {
                    MomoBadges.forPayment(PaymentStatus.PAID, overdue)
                } else {
                    MomoBadges.forPayment(
                        row.sharedPayment ?: PaymentStatus.PARTIALLY_PAID,
                        overdue,
                    )
                },
            )
            // The shared stage, or the first member's when they disagree. `Held`
            // still applies for a shipment that has not been combined away yet.
            val stageOrder = row.orders.first()
            val shared = row.sharedStage
            StatusBadge(
                if (shared != null) {
                    MomoBadges.forOrderStage(stageOrder.copy(stage = shared))
                } else {
                    MomoBadges.forOrderStage(stageOrder)
                },
            )
            // The explicit count: "2 orders" is what tells the owner this card is a
            // parcel of two rather than one order with a long id.
            StatusBadge(
                BadgeSpec(
                    label = "${row.orders.size} orders",
                    width = 74.dp,
                    fill = MomoColors.SurfaceMutedVariant,
                    ink = MomoColors.TextSecondary,
                ),
            )
        }
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
        //
        // Patch 2: the payment badge reads [Order.effectivePaymentStatus], the
        // status the MONEY implies, so a row carrying a stale `Paid` flag beside a
        // non-zero balance renders `Partially Paid` rather than contradicting
        // itself. The stage badge reads `MomoBadges.forOrderStage`, which prints
        // `Held` for a paid hold instead of echoing its `Waiting for Payment` stage.
        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Xs)) {
            StatusBadge(MomoBadges.forPayment(order.effectivePaymentStatus, overdue))
            StatusBadge(MomoBadges.forOrderStage(order))
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

    // UAT fix (item 6): order edits are BUFFERED here and written only on an
    // explicit Save changes.
    //
    // The report's complaint was that edits were committed on Back press, so
    // leaving the screen silently rewrote an order. There is no autosave path at
    // all now: `draftQty` is UI-only state, the store is never touched until
    // [saveEdits] runs, and leaving the screen (or tapping Cancel) simply drops
    // the map. Because the draft lives in `remember`, walking away mid-edit
    // cannot write anything - discarding is the absence of a write, not an undo.
    var editing by remember { mutableStateOf(false) }
    var draftQty by remember(orderId, order?.itemSubtotal) {
        mutableStateOf(data.linesFor(orderId).associate { it.sku to it.qty })
    }

    if (order == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = orderId, onBack = onBack)
            Text("That order no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    val overdue = order.isOverdue(store.now)
    // A-05 c6: Paid locks editing. A-04 c8: Paid blocks cancelling.
    //
    // Patch 2: "Paid" here is the MONEY's answer, not the stored flag's. The lock
    // is a promise that nothing more is owed, and a row whose balance is still
    // positive has not earned it - `isFullyPaid` is that check, so a stale `Paid`
    // cannot lock an order that still owes money.
    val locked = order.isFullyPaid
    val canSendInvoice = order.stage == OrderStage.ORDER_PLACED && !offline
    val canCancel = !locked && order.packingStatus !=
        ph.edu.umak.momompurin.data.entity.PackingStatus.PACKED &&
        order.stage != OrderStage.CANCELLED && !offline

    // A-04 c5: quantities may only change while the order is unsettled, which is
    // the same window `OrderOps.editQuantities` enforces. The screen's flag is an
    // early signal; the ops layer's check is the gate.
    val canEditQuantities = order.stage == OrderStage.ORDER_PLACED && !locked && !offline
    val lines = data.linesFor(orderId)
    // "Dirty" is computed from the draft, never tracked by hand, so a field typed
    // back to its original value correctly reads as no change.
    val hasUnsavedEdits = editing && lines.any { draftQty[it.sku] != it.qty }

    /** UAT fix (item 6): the ONE place a buffered edit reaches the store. */
    fun saveEdits() {
        val result = store.editOrderQuantities(orderId, draftQty)
        when (result) {
            is OrderOps.EditResult.Applied -> {
                // Patch 5: the snackbar. The Save control lives in the header, so an
                // inline line at the bottom of a long order was nowhere near the tap
                // that caused it.
                notice = null
                MomoMessages.show("Order changes saved.")
                isError = false
                editing = false
            }
            is OrderOps.EditResult.Refused -> {
                notice = result.message
                isError = true
            }
        }
    }

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
                locked -> "Paid \u00B7 locked"
                order.stage == OrderStage.CANCELLED -> "Cancelled"
                // Patch 2: a paid held order reads `Held` here too. Without this the
                // pill echoed the raw stage and printed "Waiting for Payment" above a
                // card whose badges said Paid - the contradiction living in the
                // header while the list below had been fixed.
                else -> order.stageLabel
            },
            actions = {
                // UAT fix (item 6): the explicit Save changes lives in the header,
                // where it stays reachable while the items list scrolls - the whole
                // point is that the owner can see the control that commits their
                // work at any scroll position.
                if (editing) {
                    Box(
                        Modifier
                            .heightIn(min = MomoDimens.TouchTarget)
                            .background(
                                MomoColors.ButtonPrimaryFill,
                                RoundedCornerShape(MomoRadius.Input),
                            )
                            .clickable(enabled = hasUnsavedEdits) { saveEdits() }
                            .padding(horizontal = MomoSpace.Md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Save changes",
                            style = MomoType.LabelChip,
                            color = if (hasUnsavedEdits) {
                                MomoColors.BrandDarkest
                            } else {
                                MomoColors.ButtonDisabledContent
                            },
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
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
                    // Patch 2: both badges read the DERIVED payment status and the
                    // contradiction-free stage label, the same pair the list card
                    // prints - so opening an order cannot show a different status
                    // than the row that was tapped.
                    StatusBadge(MomoBadges.forPayment(order.effectivePaymentStatus, overdue))
                    StatusBadge(MomoBadges.forOrderStage(order))
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
                // Patch 3 (A-03): a held order carries no freight of its own, so the
                // input is disabled and pinned at P0.00 until the parcel is combined
                // or cleared for shipping. `OrderOps.sendInvoice` enforces the same
                // rule, so this is the affordance rather than the only guard.
                val feeLocked = OrderOps.shippingFeeLocked(order)
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
                        // Patch 3: the locked state explains itself in the helper
                        // text rather than leaving a greyed field with no reason.
                        supporting = if (feeLocked) {
                            OrderOps.HELD_SHIPPING_FEE_LOCKED
                        } else {
                            "Leave 0.00 for a held order"
                        },
                        // A.1: a decimal keyboard so a fee cannot even be typed with
                        // letters on the soft keyboard (a paste can still, which is
                        // what the inline validation catches).
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        // Patch 3: a held order's freight is decided by the combined
                        // shipment, not here, so the control is READ-ONLY. `readOnly`
                        // rather than `enabled = false` because a disabled Material
                        // field drops its text to a disabled ink that fails contrast
                        // on this surface, while a read-only one keeps P0.00 legible
                        // as the value it now holds.
                        readOnly = feeLocked,
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
            //
            // Patch 4: when this order belongs to a combined shipment, the card
            // states the shipment and lists its members BEFORE the actions, so the
            // owner knows the pack/ship tap covers the whole parcel rather than just
            // the order whose id is in the title.
            val shipmentNo = order.combinedShipmentNo
            val shipmentMembers = shipmentNo?.let { data.shipmentMembers(it) }.orEmpty()
            val inShipment = shipmentMembers.size >= 2
            if (inShipment && shipmentNo != null) {
                MomoCard {
                    Text(
                        shipmentNo,
                        style = MomoType.DataMonospace,
                        color = MomoColors.TextPrimary,
                    )
                    Text(
                        "Combined shipment of ${shipmentMembers.size} orders: " +
                            shipmentMembers.joinToString(", ") { it.orderId },
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                    Spacer(Modifier.height(MomoSpace.Xs))
                    Text(
                        "Packing and shipping apply to every order in this shipment.",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.InfoText,
                    )
                }
            }

            if (order.stage == OrderStage.TO_PACK) {
                MomoCard {
                    Text("Packing and shipping",
                        style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                    Spacer(Modifier.height(MomoSpace.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                        MiniAction(
                            label = if (inShipment) "Pack shipment" else "Pack order",
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
            } else if (order.stage == OrderStage.TO_SHIP || order.stage == OrderStage.SHIPPED) {
                // UAT fix (item 5): a packed order has nothing left to pack, so the
                // `Pack order` control is GONE - not merely disabled - and the
                // shipping details are shown here directly instead of behind a tap.
                //
                // The report's complaint was that after confirming `Mark as Packed`
                // the screen still offered `Pack order`, which read as though the
                // confirmation had not taken. Hiding it is the honest signal: the
                // parcel is boxed, and the only open work is the courier.
                ShippingDetailsCard(
                    store = store,
                    order = order,
                    offline = offline,
                )
            }

            MomoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Items",
                        style = MomoType.TitleSmall,
                        color = MomoColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    // A-04 c5 / UAT fix (item 6): Edit opens a DRAFT. Nothing is
                    // written until Save changes, and Cancel discards. The control
                    // is absent once the order is settled, so there is no way to
                    // start an edit the ops layer would refuse.
                    if (canEditQuantities) {
                        if (editing) {
                            MiniAction(
                                label = "Cancel",
                                onClick = {
                                    draftQty = lines.associate { it.sku to it.qty }
                                    editing = false
                                    notice = null
                                },
                            )
                        } else {
                            MiniAction(
                                label = "Edit items",
                                onClick = { editing = true; notice = null },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(MomoSpace.Sm))
                lines.forEach { line ->
                    val draft = draftQty[line.sku] ?: line.qty
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
                                    if (editing) null else "\u00D7 ${line.qty}",
                                ).joinToString(" \u00B7 "),
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                        if (editing) {
                            // The stepper writes ONLY to the draft map.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
                            ) {
                                QtyStepButton(
                                    label = "\u2212",
                                    enabled = draft > 0,
                                    onClick = {
                                        draftQty = draftQty + (line.sku to (draft - 1))
                                    },
                                )
                                Text(
                                    draft.toString(),
                                    style = MomoType.DataMonospace,
                                    color = MomoColors.TextPrimary,
                                )
                                QtyStepButton(
                                    label = "+",
                                    enabled = draft < 99,
                                    onClick = {
                                        draftQty = draftQty + (line.sku to (draft + 1))
                                    },
                                )
                            }
                        } else {
                            Text(
                                MomoMoney.format(line.lineTotal),
                                style = MomoType.DataCaption,
                                color = MomoColors.TextPrimary,
                            )
                        }
                    }
                }
                // The subtotal the draft would produce, so the money consequence
                // of a quantity change is visible BEFORE it is committed.
                if (hasUnsavedEdits) {
                    Spacer(Modifier.height(MomoSpace.Sm))
                    MomoDivider()
                    Spacer(Modifier.height(MomoSpace.Sm))
                    val draftSubtotal = lines.sumOf { line ->
                        (draftQty[line.sku] ?: line.qty).toLong() * line.unitPrice
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Subtotal after saving",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            MomoMoney.format(draftSubtotal),
                            style = MomoType.DataMonospace,
                            color = MomoColors.BrandDeep,
                        )
                    }
                    Text(
                        "Not saved yet.",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.WarningText,
                    )
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
            // Patch 3: a held order's confirmation states the P0.00 freight, so the
            // owner is not surprised by a total with no shipping on it after having
            // left a field that could not be edited.
            message = if (OrderOps.shippingFeeLocked(order)) {
                "Invoice MP-${orderId.removePrefix("ORD-")} will be sent to " +
                    "${data.customerName(order.customerEmail)} with P0.00 shipping, " +
                    "because this order is held for combined shipping. The 24-hour " +
                    "payment window will start."
            } else {
                "Invoice MP-${orderId.removePrefix("ORD-")} will be sent to " +
                    "${data.customerName(order.customerEmail)} and the 24-hour payment " +
                    "window will start."
            },
            confirmLabel = "Send invoice",
            dismissLabel = "Cancel",
            onConfirm = {
                showInvoiceDialog = false
                when (val result = store.sendInvoice(orderId, shippingFee)) {
                    is OrderOps.InvoiceResult.Sent -> {
                        // Patch 5: the snackbar. Sending the invoice REPLACES the whole
                        // "Send the invoice" card (the order leaves Order Placed), so
                        // an inline confirmation rendered in a spot that had just been
                        // removed from the layout.
                        notice = null
                        MomoMessages.show("Invoice ${result.invoiceNo} sent.")
                        isError = false
                    }
                    is OrderOps.InvoiceResult.Refused -> {
                        notice = result.message
                        isError = result.message == OrderOps.INVALID_SHIPPING_FEE ||
                            result.message == OrderOps.HELD_SHIPPING_FEE_LOCKED
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

/**
 * UAT fix (item 6): one step button on the buffered quantity editor.
 *
 * Deliberately not a `MomoField`: a free-text quantity would let the owner type
 * "0" or a letter and then have to read a refusal. Stepping can only produce a
 * count the ops layer accepts, so the only refusal left is availability, which
 * is a real business answer rather than a typo.
 */
@Composable
private fun QtyStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Input))
            .border(1.dp, MomoColors.BorderSubtle, RoundedCornerShape(MomoRadius.Input))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MomoType.DataCaption,
            color = if (enabled) MomoColors.BrandDeep else MomoColors.ButtonDisabledContent,
            maxLines = 1,
        )
    }
}

/**
 * UAT fix (item 5): the shipping details, shown inline once the parcel is packed.
 *
 * The same three fields the full shipping screen owns - courier route, tracking
 * number and shipping status - placed where the owner already is, so confirming
 * `Mark as Packed` flows straight into "which courier?" rather than sending them
 * back to hunt for another control.
 *
 * All three write through [MomoStore.updateShipping], which is the ONE place the
 * A-07 c8 tracking rules and the A-07 c9 stock arithmetic live. A status picked
 * here therefore dispatches the order exactly as the dedicated screen would, with
 * no second implementation to drift.
 */
@Composable
private fun ShippingDetailsCard(
    store: MomoStore,
    order: Order,
    offline: Boolean,
) {
    var notice by remember(order.orderId) { mutableStateOf<String?>(null) }
    var isError by remember(order.orderId) { mutableStateOf(false) }

    fun apply(courier: String?, tracking: String?, status: ShippingStatus?) {
        // Patch 4: routed through the SHIPMENT-aware write, so a courier and waybill
        // recorded on one member of a combined parcel land on all of them and each
        // member's stock is deducted exactly once. For an uncombined order the scope
        // is that order alone, so this is the same call it always was.
        when (
            val result = store.updateShipmentShipping(
                order.orderId, courier, tracking, status,
            )
        ) {
            is OrderOps.ShipResult.Updated -> {
                // Patch 5: the snackbar, matching the packing screen's own shipping
                // confirmation - the same save reported in two places must not use
                // two different controls.
                notice = null
                MomoMessages.show("Shipping details saved.")
                isError = false
            }
            is OrderOps.ShipResult.Refused -> {
                notice = result.message
                isError = true
            }
        }
    }

    MomoCard {
        Text(
            "Shipping details",
            style = MomoType.TitleSmall,
            color = MomoColors.TextPrimary,
        )
        Spacer(Modifier.height(MomoSpace.Sm))
        KeyValueRow("Recipient", order.recipientName.ifBlank { "\u2014" })
        KeyValueRow("Mobile", order.mobile.ifBlank { "\u2014" })
        KeyValueRow("Address", order.addressBlock.ifBlank { "\u2014" })

        Spacer(Modifier.height(MomoSpace.Md))
        Text(
            "Courier",
            style = MomoType.CaptionSmall,
            color = MomoColors.TextSecondary,
        )
        Spacer(Modifier.height(MomoSpace.Xs))
        // The four routes A-07 c6 names, as tappable chips rather than a dropdown:
        // there are only four and the chosen one has to stay visible beside the
        // tracking number it belongs to.
        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
            OrderOps.DELIVERY_ROUTES.take(2).forEach { route ->
                StageChip(
                    label = route,
                    selected = order.courier == route,
                    onClick = { apply(courier = route, tracking = null, status = null) },
                )
            }
        }
        Spacer(Modifier.height(MomoSpace.Xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
            OrderOps.DELIVERY_ROUTES.drop(2).forEach { route ->
                StageChip(
                    label = route,
                    selected = order.courier == route,
                    onClick = { apply(courier = route, tracking = null, status = null) },
                )
            }
        }

        Spacer(Modifier.height(MomoSpace.Md))
        var tracking by remember(order.trackingNo) { mutableStateOf(order.trackingNo) }
        MomoField(
            value = tracking,
            onValueChange = { tracking = it; notice = null },
            label = "Tracking number",
            labelOutside = true,
            supporting = if (order.courier ==
                ph.edu.umak.momompurin.data.CourierTrackingValidator.PICKUP
            ) {
                "Not required for pickup."
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MomoSpace.Sm))
        MiniAction(
            label = "Save tracking",
            emphasised = true,
            enabled = !offline,
            onClick = { apply(courier = null, tracking = tracking, status = null) },
        )

        Spacer(Modifier.height(MomoSpace.Md))
        Text(
            "Shipping status",
            style = MomoType.CaptionSmall,
            color = MomoColors.TextSecondary,
        )
        Spacer(Modifier.height(MomoSpace.Xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
            listOf(
                ShippingStatus.FOR_PICKUP,
                ShippingStatus.IN_TRANSIT,
                ShippingStatus.RECEIVED,
            ).forEach { status ->
                StageChip(
                    label = status.label,
                    selected = order.shippingStatus == status,
                    onClick = {
                        // A-07 c8: In Transit saves the tracking number typed above
                        // in the same tap, so the waybill the owner just entered is
                        // not silently dropped by a status-only save.
                        apply(
                            courier = null,
                            tracking = tracking.takeIf { it.isNotBlank() },
                            status = status,
                        )
                    },
                )
            }
        }
        if (order.shippingStatus == ShippingStatus.IN_TRANSIT) {
            Spacer(Modifier.height(MomoSpace.Sm))
            Text(
                "Waiting for the customer to confirm receipt.",
                style = MomoType.CaptionSmall,
                color = MomoColors.InfoText,
            )
        }

        if (notice != null) {
            Spacer(Modifier.height(MomoSpace.Sm))
            Text(
                notice!!,
                style = MomoType.CaptionBold,
                color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
            )
        }
        if (offline) {
            Spacer(Modifier.height(MomoSpace.Sm))
            Text(
                "Read-only while offline.",
                style = MomoType.CaptionSmall,
                color = MomoColors.DangerInk,
            )
        }
    }
}

/** A confirmation with a reason field, shared by the owner's cancel and restore. */@Composable
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
