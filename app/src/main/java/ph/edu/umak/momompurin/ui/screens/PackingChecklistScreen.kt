package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import ph.edu.umak.momompurin.ui.theme.MomoDate

/**
 * A-07 Pack and Ship Orders - the packing checklist.
 *
 * A-07 c3/c5 is the rule that shapes the screen: Mark as packed stays DISABLED
 * until every item is checked, and an early attempt states
 * "Check all items before marking the order as packed." The disabled state alone
 * is not enough - a disabled control with no explanation reads as a broken app -
 * so the message is also rendered inline once the owner has tried.
 */
@Composable
fun PackingChecklistScreen(
    store: MomoStore,
    orderId: String,
    onBack: () -> Unit,
) {
    val data by store.data.collectAsState()
    val order = data.order(orderId)
    val offline by store.offlineMode.collectAsState()

    // Patch 4: a combined shipment is ONE parcel, so its checklist covers every
    // member's lines and the confirmation packs them together.
    //
    // Listing only the tapped order's lines would let the owner tick a checklist
    // that looks complete while the box still holds an unticked sibling, and the
    // pack action would then advance one order and leave the other on the packing
    // table. [OrderOps.shipmentScope] is the same set the ops layer fans the write
    // out to, so this screen shows exactly what the tap will affect.
    val scopeIds = remember(data.orders, orderId) { OrderOps.shipmentScope(data, orderId) }
    val isShipment = scopeIds.size > 1
    val shipmentOrders = remember(data.orders, scopeIds) {
        scopeIds.mapNotNull { data.order(it) }
    }
    // Every line across the shipment, so the tick state is one set covering the
    // whole box.
    val lines = remember(data.orderLines, scopeIds) {
        scopeIds.flatMap { data.linesFor(it) }
    }
    // The per-order slices, for the grouped checklist headings.
    val linesByOrder = remember(data.orderLines, scopeIds) {
        scopeIds.map { id -> id to data.linesFor(id) }.filter { it.second.isNotEmpty() }
    }

    var checked by remember { mutableStateOf(setOf<String>()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    if (order == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = "Pack order", onBack = onBack)
            Text("That order no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    val allChecked = lines.isNotEmpty() && lines.all { it.lineId in checked }
    // Patch 4: the shipment is packed only when EVERY member is. Keying this on the
    // tapped order alone would hide a half-packed shipment behind a "Packed" pill.
    val alreadyPacked = shipmentOrders.isNotEmpty() && shipmentOrders.all {
        it.packingStatus == ph.edu.umak.momompurin.data.entity.PackingStatus.PACKED
    }
    // Patch 4: and it may only be packed when every member is PAID - A-07's money
    // rule is per order, so one member with a balance blocks the whole parcel.
    val allPaid = shipmentOrders.isNotEmpty() && shipmentOrders.all { it.isFullyPaid }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(
            title = if (isShipment) "Pack shipment" else "Pack order",
            onBack = onBack,
            // Patch 4: the pill names the SHIPMENT for a combined parcel, so the
            // header does not suggest this tap covers only the order in it.
            contextPill = order.combinedShipmentNo?.takeIf { isShipment } ?: order.orderId,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                KeyValueRow("Customer", data.customerName(order.customerEmail))
                // Patch 4: a combined parcel's total is the sum of its members, which
                // is the figure the courier and the invoice set both agree on.
                KeyValueRow(
                    "Total",
                    MomoMoney.format(shipmentOrders.sumOf { it.total }),
                    emphasise = true,
                )
                if (isShipment) {
                    // A-06 c6: the members are named, so every individual order stays
                    // identifiable from the packing screen that acts on all of them.
                    KeyValueRow("Combined shipment", scopeIds.joinToString(", "))
                }
                KeyValueRow(
                    "Packing",
                    if (alreadyPacked) "Packed" else "Not packed",
                )
                if (alreadyPacked && order.packedAt != null) {
                    KeyValueRow("Packed at", MomoDate.formatDateTime(order.packedAt!!))
                }
                KeyValueRow("Delivery address", order.addressBlock)
            }

            // A-07 c2: every item with its product, variant and quantity.
            MomoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Checklist",
                        style = MomoType.TitleSmall,
                        color = MomoColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    // Progress is stated as a COUNT, not only as a colour or a
                    // disabled button: A-07 c3 gates the whole action on completion,
                    // so the owner must be able to see how far along they are
                    // ("0 of 3 checked") without counting rows.
                    Text(
                        "${checked.size} of ${lines.size} checked",
                        style = MomoType.CaptionBold,
                        color = if (allChecked) MomoColors.SuccessText else MomoColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(MomoSpace.Sm))
                // Patch 4: for a combined shipment the checklist is grouped by the
                // order each item belongs to, so the owner packing one physical box
                // can still tell which order needs a given sticker. A single order
                // renders exactly as before - one group with no heading.
                linesByOrder.forEachIndexed { groupIndex, (memberId, memberLines) ->
                    if (isShipment) {
                        if (groupIndex > 0) {
                            Spacer(Modifier.height(MomoSpace.Md))
                            MomoDivider()
                            Spacer(Modifier.height(MomoSpace.Md))
                        }
                        Text(
                            memberId,
                            style = MomoType.CaptionBold,
                            color = MomoColors.BrandDeep,
                        )
                        Spacer(Modifier.height(MomoSpace.Xs))
                    }
                    memberLines.forEach { line ->
                    val isChecked = line.lineId in checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(enabled = !alreadyPacked) {
                                checked = if (isChecked) {
                                    checked - line.lineId
                                } else {
                                    checked + line.lineId
                                }
                                notice = null
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // A 26dp bordered box with a tick: an unchecked control that
                        // is only an outline reads as decoration, and A-07 c3 makes
                        // this the control that gates the whole action.
                        PackingCheckBox(checked = isChecked)
                        Spacer(Modifier.width(MomoSpace.Md))
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
                    // The separator is per GROUP, so the last item of one order's
                    // slice does not draw a line under a heading that follows it.
                    if (line != memberLines.last()) MomoDivider()
                    }
                }
            }

            if (notice != null) {
                Text(
                    notice!!,
                    style = MomoType.CaptionBold,
                    color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            // A-07 c3/c5: disabled until complete, with the reason stated.
            MomoButton(
                label = "Mark as packed",
                onClick = {
                    if (!allChecked) {
                        // A-07 c5's exact message on the early attempt.
                        notice = OrderOps.CHECK_ALL_ITEMS
                        isError = true
                    } else {
                        showDialog = true
                    }
                },
                enabled = !offline && !alreadyPacked && allPaid,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!allChecked && !alreadyPacked) {
                Text(
                    OrderOps.CHECK_ALL_ITEMS,
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            }
            // UAT fix (item 2): the money rule is stated on the screen, so an owner
            // who reaches a partially-paid order is told what is missing instead of
            // finding a disabled button and no reason.
            //
            // Patch 4: checked across the whole shipment, because one member owing
            // money is what disables the button for a combined parcel.
            if (!allPaid && !alreadyPacked) {
                Text(
                    OrderOps.NOT_FULLY_PAID_CANNOT_PACK,
                    style = MomoType.CaptionSmall,
                    color = MomoColors.WarningText,
                )
            }
            if (offline) {
                Text(
                    "Read-only while offline.",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.DangerInk,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

    // A-07 c4: the confirmation before the status changes.
    if (showDialog) {
        MomoConfirmDialog(
            title = "Mark as packed",
            // Patch 4: a shipment's confirmation names the whole parcel, so the
            // owner is never surprised that one tap advanced two orders.
            message = if (isShipment) {
                "All ${lines.sumOf { it.qty }} items across " +
                    "${shipmentOrders.size} combined orders " +
                    "(${scopeIds.joinToString(", ")}) are checked. " +
                    "They will be marked Packed with today's date and time and " +
                    "move to To Ship together."
            } else {
                "All ${lines.sumOf { it.qty }} items for ${order.orderId} are " +
                    "checked. The order will be marked Packed with today's date and time."
            },
            confirmLabel = "Mark as packed",
            dismissLabel = "Cancel",
            onConfirm = {
                showDialog = false
                // Patch 4: the SHIPMENT-aware write. For an uncombined order the
                // scope is that order alone, so this is the same call it was.
                when (val result = store.markShipmentPacked(orderId, checked)) {
                    is OrderOps.PackResult.Packed -> {
                        // UAT fix (item 5): name the stage the order just moved to,
                        // so "packed" and the order leaving this queue are one
                        // piece of news rather than two surprises.
                        //
                        // Patch 5: through the snackbar. Packing REMOVES this screen's
                        // whole reason to exist (the order leaves To Ship and the
                        // control goes with it), so an inline line landed in a layout
                        // that had just been rebuilt underneath the owner.
                        notice = null
                        ph.edu.umak.momompurin.ui.components.MomoMessages.show(
                            if (isShipment) {
                                "Combined shipment marked as packed and moved to To Ship."
                            } else {
                                "Order marked as packed and moved to To Ship."
                            },
                        )
                        isError = false
                    }
                    is OrderOps.PackResult.Refused -> {
                        notice = result.message
                        isError = true
                    }
                }
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun PackingCheckBox(checked: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .background(
                if (checked) MomoColors.BrandPrimary else MomoColors.SurfaceCard,
                RoundedCornerShape(6.dp),
            )
            .border(
                2.dp,
                if (checked) MomoColors.BrandDeep else MomoColors.TextSecondary,
                RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("\u2713", style = MomoType.CaptionBold, color = MomoColors.BrandDarkest)
        }
    }
}

/**
 * A-07 c6-c9: the shipping details.
 *
 * The three things this screen keeps straight:
 *  - Copy shipping details states "Shipping details copied." where the tester is
 *    looking, not only on the clipboard;
 *  - In Transit without a tracking number is REFUSED with its own message and the
 *    previous status survives;
 *  - the first dispatch reduces BOTH physical and reserved, which is why
 *    Available does not move (A-07 c9).
 */
@Composable
fun ShippingScreen(
    store: MomoStore,
    orderId: String,
    onBack: () -> Unit,
) {
    val data by store.data.collectAsState()
    val order = data.order(orderId)
    val offline by store.offlineMode.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var courier by remember(order?.courier) {
        mutableStateOf(order?.courier?.ifBlank { "J&T Express" } ?: "J&T Express")
    }
    var tracking by remember(order?.trackingNo) { mutableStateOf(order?.trackingNo.orEmpty()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    if (order == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = "Shipping", onBack = onBack)
            Text("That order no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Shipping details", onBack = onBack, contextPill = order.orderId)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                KeyValueRow("Recipient", order.recipientName)
                KeyValueRow("Mobile", order.mobile)
                KeyValueRow("Address", order.addressBlock)
                KeyValueRow("Total", MomoMoney.format(order.total), emphasise = true)
                Spacer(Modifier.height(MomoSpace.Sm))
                // A-07 c6: a real clipboard write plus a visible confirmation.
                MomoButton(
                    label = "Copy shipping details",
                    onClick = {
                        copyToClipboard(
                            context,
                            OrderOps.shippingDetailsText(data, orderId),
                        )
                        notice = OrderOps.SHIPPING_COPIED
                        isError = false
                    },
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MomoCard {
                Text(
                    "Delivery route",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                // A-07 c6: exactly the four routes the criterion names.
                OrderOps.DELIVERY_ROUTES.forEach { route ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .background(
                                if (courier == route) {
                                    MomoColors.SurfaceMutedVariant
                                } else {
                                    MomoColors.SurfaceCard
                                },
                                RoundedCornerShape(MomoRadius.Input),
                            )
                            .border(
                                if (courier == route) 2.dp else 1.dp,
                                if (courier == route) MomoColors.BrandDeep else MomoColors.BorderSubtle,
                                RoundedCornerShape(MomoRadius.Input),
                            )
                            .clickable(enabled = !offline) {
                                courier = route
                                save(courier = route, tracking = tracking, status = null, store, orderId) { msg, err ->
                                    notice = msg
                                    isError = err
                                }
                            }
                            .padding(horizontal = MomoSpace.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            route,
                            style = MomoType.BodyDefault,
                            color = MomoColors.TextPrimary,
                            modifier = Modifier
                                .padding(vertical = MomoSpace.Sm)
                                .weight(1f),
                        )
                        if (courier == route) {
                            Text(
                                "Selected",
                                style = MomoType.LabelChip,
                                color = MomoColors.BrandDeep,
                            )
                        }
                    }
                    Spacer(Modifier.height(MomoSpace.Sm))
                }
            }

            MomoCard {
                Text(
                    "Tracking number",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                // Phase 2.2: the format check runs live against the selected
                // courier, so a mistyped waybill is caught as it is typed rather
                // than only when In Transit is tapped. Pickup needs no number at
                // all, so its format check is skipped entirely. The empty-field
                // message is only an error at dispatch time (the ops layer enforces
                // it), so while typing only a FILLED but malformed number is
                // flagged inline.
                val inlineError = tracking.takeIf { it.isNotBlank() }?.let { filled ->
                    val outcome = ph.edu.umak.momompurin.data.CourierTrackingValidator.validate(
                        courier, filled,
                    )
                    (outcome as? ph.edu.umak.momompurin.data.CourierTrackingValidator.Outcome.Malformed)
                        ?.message
                }
                ph.edu.umak.momompurin.ui.components.MomoField(
                    value = tracking,
                    onValueChange = { tracking = it; notice = null },
                    label = "Tracking number",
                    labelOutside = true,
                    error = inlineError,
                    supporting = if (courier ==
                        ph.edu.umak.momompurin.data.CourierTrackingValidator.PICKUP
                    ) {
                        "Not required for pickup."
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MomoSpace.Md))
                Text(
                    "Shipping status",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                    listOf(
                        ph.edu.umak.momompurin.data.entity.ShippingStatus.FOR_PICKUP,
                        ph.edu.umak.momompurin.data.entity.ShippingStatus.IN_TRANSIT,
                        ph.edu.umak.momompurin.data.entity.ShippingStatus.RECEIVED,
                    ).forEach { status ->
                        StageChip(
                            label = status.label,
                            selected = order.shippingStatus == status,
                            onClick = {
                                // The ops layer re-validates on save; the UI check
                                // above is the early signal, this is the gate.
                                save(
                                    courier = courier,
                                    tracking = tracking,
                                    status = status,
                                    store = store,
                                    orderId = orderId,
                                ) { msg, err ->
                                    notice = msg
                                    isError = err
                                }
                            },
                        )
                    }
                }
            }

            if (notice != null) {
                Text(
                    notice!!,
                    style = MomoType.CaptionBold,
                    color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            if (order.shippingStatus ==
                ph.edu.umak.momompurin.data.entity.ShippingStatus.NOT_SHIPPED
            ) {
                Text(
                    "Setting a status here dispatches the order: On hand and Reserved " +
                        "both drop by the shipped quantity, so Available stays the same.",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}

/**
 * Applies a shipping change and reports the outcome.
 *
 * A refusal (In Transit without a tracking number) leaves the record untouched
 * because the ops layer returns its input - so the previous status survives,
 * which is exactly what A-07 c8 requires.
 */
private fun save(
    courier: String,
    tracking: String,
    status: ph.edu.umak.momompurin.data.entity.ShippingStatus?,
    store: MomoStore,
    orderId: String,
    onMessage: (String, Boolean) -> Unit,
) {
    // The Boolean is `isError`. Every call site used to pass `isError = false`
    // unconditionally, so "Enter the tracking number first." and the offline
    // refusal both rendered in SUCCESS green - a refusal that reads as a
    // confirmation is worse than no message at all.
    //
    // Patch 4: the SHIPMENT-aware write, so a courier and waybill recorded here for
    // a member of a combined parcel land on all of them and each member's stock is
    // deducted exactly once. For an uncombined order the scope is that order alone.
    when (val result = store.updateShipmentShipping(orderId, courier, tracking, status)) {
        is OrderOps.ShipResult.Updated -> {
            // Patch 5: the SNACKBAR, not the caller's inline notice. Every call site
            // of this helper used to render the confirmation inside this screen's
            // scrolling form, which is the pattern the report asks to remove - so the
            // success is raised here, once, and the callers no longer have to carry a
            // `(String, Boolean)` recovery path for it.
            ph.edu.umak.momompurin.ui.components.MomoMessages.show("Shipping details saved.")
            onMessage("", false)
        }
        is OrderOps.ShipResult.Refused -> onMessage(result.message, true)
    }
}

/** Writes text to the clipboard, with the confirmation toasted by the caller. */
private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Shipping details", text))
}
