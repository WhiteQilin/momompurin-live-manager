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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * A-06 Hold and Combine Orders.
 *
 * The advisory is the subtle rule here, and it is a THREE-part condition:
 * an electronic item AND seven days held. ORD-1038 (electronic, exactly 7 days)
 * shows it; ORD-1036 (10 days, no electronic) must not; and a six-day electronic
 * hold must not either - which is why the badge reads [MomoData.showsWeekAdvisory]
 * rather than a day count alone.
 *
 * ## UAT fix (item 4): grouped by CUSTOMER, not one flat list
 *
 * The report's complaint was that the old single list made cross-customer
 * combining a mistake waiting to happen: every held order looked like every other
 * one, and the only thing stopping a mixed selection was the refusal that arrived
 * AFTER the owner had already chosen. The list is now a column of per-customer
 * cards, each with its own selection and its own `Combine selected` action, so a
 * mixed selection cannot be assembled in the first place - the constraint is
 * structural rather than a validation the user can trip over.
 *
 * ## UAT fix (item 3): combining clears the hold
 *
 * [OrderOps.combine] now also clears `HOLD_FOR_COMBINED`, so a combined order
 * leaves this screen the moment the store commits. The confirmation's message
 * says so, because an order vanishing from a list needs to be explained at the
 * moment it happens or it reads as a lost record.
 */
@Composable
fun HeldOrdersScreen(
    store: MomoStore,
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val data by store.data.collectAsState()
    val offline by store.offlineMode.collectAsState()
    val now = store.now

    // Selection is PER CUSTOMER. The key is the customer's email, so two customers
    // can each have a partial selection open without either leaking into the
    // other's combine.
    var selectedByCustomer by remember { mutableStateOf(mapOf<String, Set<String>>()) }
    var pendingCustomer by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // A-06 c6: the same grouping the data layer already exposes, so this screen and
    // any future report read one definition of "held orders by customer".
    val grouped = data.heldOrdersByCustomer()

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Hold and Combine Orders", onBack = onBack)

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
            if (grouped.isEmpty()) {
                // A-06 c1's empty state.
                item {
                    Text(
                        "No held orders.",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextSecondary,
                        modifier = Modifier.padding(vertical = MomoSpace.Xl),
                    )
                }
            } else {
                item {
                    Text(
                        "Select two or more paid held orders for one customer, " +
                            "then combine them.",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
            }

            grouped.forEach { (email, orders) ->
                item(key = "customer-$email") {
                    val selection = selectedByCustomer[email].orEmpty()
                    val total = orders.sumOf { it.total }
                    MomoCard {
                        // ---- the customer head ---------------------------------
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    data.customerName(email),
                                    style = MomoType.TitleSmall,
                                    color = MomoColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${orders.size} held " +
                                        (if (orders.size == 1) "order" else "orders") +
                                        " \u00B7 ${MomoMoney.format(total)}",
                                    style = MomoType.CaptionSmall,
                                    color = MomoColors.TextSecondary,
                                )
                            }
                            Text(
                                email,
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextDisabled,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.height(MomoSpace.Sm))
                        MomoDivider()

                        // ---- each order with its own items ---------------------
                        orders.forEach { order ->
                            val isSelected = order.orderId in selection
                            Spacer(Modifier.height(MomoSpace.Sm))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedByCustomer = selectedByCustomer + (
                                            email to if (isSelected) {
                                                selection - order.orderId
                                            } else {
                                                selection + order.orderId
                                            }
                                            )
                                        notice = null
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SelectionMark(checked = isSelected)
                                Spacer(Modifier.width(MomoSpace.Md))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${order.orderId} \u00B7 ${order.heldDays(now)} " +
                                            "day" + (if (order.heldDays(now) == 1) "" else "s") +
                                            " held",
                                        style = MomoType.BodyDefault,
                                        color = MomoColors.TextPrimary,
                                    )
                                    // A-06 c6 / UAT fix (item 4): the order's ITEMS are
                                    // shown on the card, so the owner can tell two holds
                                    // of the same value apart without opening each one.
                                    Text(
                                        data.linesFor(order.orderId).joinToString(", ") {
                                            val variant = it.variantName
                                                .takeIf { v -> v.isNotBlank() }
                                            listOfNotNull(
                                                it.productName,
                                                variant,
                                                "\u00D7 ${it.qty}",
                                            ).joinToString(" ")
                                        }.ifBlank { "\u2014" },
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
                            Spacer(Modifier.height(MomoSpace.Xs))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp + MomoSpace.Md),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    "Open order",
                                    style = MomoType.CaptionBold,
                                    color = MomoColors.TextLinkAndBrand,
                                    modifier = Modifier
                                        .clickable { onOpenOrder(order.orderId) }
                                        .padding(vertical = MomoSpace.Xs),
                                )
                            }
                            // A-06 c2: the 1-week advisory, on an electronic item
                            // held 7+ days.
                            if (data.showsWeekAdvisory(order, now)) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp + MomoSpace.Md)
                                        .background(
                                            MomoColors.WarningSurface,
                                            RoundedCornerShape(MomoRadius.Input),
                                        )
                                        .padding(MomoSpace.Sm),
                                ) {
                                    Text(
                                        "1-week advisory \u00B7 this order holds an " +
                                            "electronic item for a week or more.",
                                        style = MomoType.CaptionSmall,
                                        color = MomoColors.WarningText,
                                    )
                                }
                            }
                            if (order != orders.last()) {
                                Spacer(Modifier.height(MomoSpace.Sm))
                                MomoDivider()
                            }
                        }

                        // ---- the per-customer combine ------------------------
                        Spacer(Modifier.height(MomoSpace.Md))
                        MomoButton(
                            label = if (selection.isEmpty()) {
                                "Combine selected"
                            } else {
                                "Combine ${selection.size} selected"
                            },
                            onClick = { pendingCustomer = email },
                            enabled = selection.size >= 2 && !offline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (selection.size == 1) {
                            Text(
                                "Select one more order to combine.",
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            if (notice != null) {
                item {
                    Text(
                        notice!!,
                        style = MomoType.CaptionBold,
                        color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                    )
                }
            }
            if (offline && grouped.isNotEmpty()) {
                item {
                    Text(
                        "Read-only while offline.",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.DangerInk,
                    )
                }
            }
        }
    }

    // A-06 c5/c7: the confirmation, for ONE customer's selection.
    val customer = pendingCustomer
    if (customer != null) {
        val chosen = grouped[customer].orEmpty()
            .filter { it.orderId in selectedByCustomer[customer].orEmpty() }
        val total = chosen.sumOf { it.total }
        MomoConfirmDialog(
            title = "Combine Shipments",
            message = "${chosen.size} orders for ${data.customerName(customer)} will " +
                "ship together, for ${MomoMoney.format(total)}. Each original order " +
                "and its invoice is kept, and the combined orders leave this list " +
                "for To Pack.",
            confirmLabel = "Combine",
            dismissLabel = "Cancel",
            onConfirm = {
                pendingCustomer = null
                when (val result = store.combineOrders(chosen.map { it.orderId })) {
                    is OrderOps.CombineResult.Combined -> {
                        notice = "Combined as ${result.shipmentNo} \u00B7 " +
                            MomoMoney.format(result.total) + " \u00B7 moved to To Pack."
                        isError = false
                        // The orders are no longer held, so their selection is
                        // meaningless - clearing it keeps the screen honest rather
                        // than showing a stale tick against a row that vanished.
                        selectedByCustomer = selectedByCustomer - customer
                    }
                    is OrderOps.CombineResult.Refused -> {
                        notice = result.message
                        isError = true
                    }
                }
            },
            onDismiss = { pendingCustomer = null },
        )
    }
}

/** The combine selection mark: bordered, ticked when chosen. */
@Composable
private fun SelectionMark(checked: Boolean) {
    Box(
        Modifier
            .size(24.dp)
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
