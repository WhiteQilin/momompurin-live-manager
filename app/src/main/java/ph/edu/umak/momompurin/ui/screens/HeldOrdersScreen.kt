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
 * Combining is refused across customers with the criterion's exact message, and a
 * refusal leaves the selection and the records untouched.
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

    var selected by remember { mutableStateOf(setOf<String>()) }
    var showCombine by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val held = data.heldOrders()

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Hold and Combine Orders", onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.Lg,
                bottom = MomoSpace.Lg,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            if (held.isEmpty()) {
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
                        "Select two or more paid held orders for the same customer.",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
            }

            items(held, key = { it.orderId }) { order ->
                val isSelected = order.orderId in selected
                val advisory = data.showsWeekAdvisory(order, now)
                MomoCard(
                    Modifier.clickable {
                        // A-06 c8: only a PAID held order is selectable.
                        if (order.isPaid) {
                            selected = if (isSelected) {
                                selected - order.orderId
                            } else {
                                selected + order.orderId
                            }
                            notice = null
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectionMark(checked = isSelected, enabled = order.isPaid)
                        Spacer(Modifier.width(MomoSpace.Md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                order.orderId,
                                style = MomoType.DataMonospace,
                                color = MomoColors.TextPrimary,
                            )
                            Text(
                                data.customerName(order.customerEmail),
                                style = MomoType.CaptionBold,
                                color = MomoColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // A-06 c1: the days held.
                            Text(
                                "${order.heldDays(now)} days held",
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                MomoMoney.format(order.total),
                                style = MomoType.DataMonospace,
                                color = MomoColors.BrandDeep,
                            )
                            if (order.combinedShipmentNo != null) {
                                Text(
                                    order.combinedShipmentNo!!,
                                    style = MomoType.LabelChip,
                                    color = MomoColors.InfoText,
                                )
                            }
                        }
                    }

                    // A-06 c2: the 1-week advisory, on an electronic item held 7+ days.
                    if (advisory) {
                        Spacer(Modifier.height(MomoSpace.Sm))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                // Indented to the CONTENT column so the note hangs
                                // under the order text it describes, not under the
                                // checkbox beside it.
                                .padding(start = 24.dp + MomoSpace.Md)
                                .background(
                                    MomoColors.WarningSurface,
                                    RoundedCornerShape(MomoRadius.Input),
                                )
                                .padding(MomoSpace.Sm),
                        ) {
                            Text(
                                "1-week advisory \u00B7 this order holds an electronic " +
                                    "item for a week or more.",
                                style = MomoType.CaptionSmall,
                                color = MomoColors.WarningText,
                            )
                        }
                    }
                }
            }

            if (held.isNotEmpty()) {
                item {
                    if (notice != null) {
                        Text(
                            notice!!,
                            style = MomoType.CaptionBold,
                            color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                        )
                        Spacer(Modifier.height(MomoSpace.Sm))
                    }
                    MomoButton(
                        label = "Combine Shipments",
                        onClick = { showCombine = true },
                        enabled = selected.size >= 2 && !offline,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
        }
    }

    // A-06 c5/c7: the confirmation, then the refusal if the customers differ.
    if (showCombine) {
        val chosen = held.filter { it.orderId in selected }
        val customers = chosen.map { it.customerEmail }.distinct()
        val total = chosen.sumOf { it.total }
        if (customers.size > 1) {
            MomoConfirmDialog(
                title = "Combine Shipments",
                message = OrderOps.MIXED_CUSTOMERS,
                confirmLabel = "Close",
                dismissLabel = "Close",
                onConfirm = {
                    showCombine = false
                    notice = OrderOps.MIXED_CUSTOMERS
                    isError = true
                },
                onDismiss = {
                    showCombine = false
                    notice = OrderOps.MIXED_CUSTOMERS
                    isError = true
                },
            )
        } else {
            MomoConfirmDialog(
                title = "Combine Shipments",
                message = "${chosen.size} orders for " +
                    "${data.customerName(customers.firstOrNull().orEmpty())} will ship " +
                    "together, for ${MomoMoney.format(total)}. Each original order and " +
                    "its invoice is kept.",
                confirmLabel = "Combine",
                dismissLabel = "Cancel",
                onConfirm = {
                    showCombine = false
                    when (val result = store.combineOrders(selected.toList())) {
                        is OrderOps.CombineResult.Combined -> {
                            notice = "Combined as ${result.shipmentNo} \u00B7 " +
                                MomoMoney.format(result.total)
                            isError = false
                            selected = emptySet()
                        }
                        is OrderOps.CombineResult.Refused -> {
                            notice = result.message
                            isError = true
                        }
                    }
                },
                onDismiss = { showCombine = false },
            )
        }
    }
}

/** The combine selection mark: bordered, ticked when chosen, greyed when unpaid. */
@Composable
private fun SelectionMark(checked: Boolean, enabled: Boolean) {
    Box(
        Modifier
            .size(24.dp)
            .background(
                if (checked && enabled) MomoColors.BrandPrimary else MomoColors.SurfaceCard,
                RoundedCornerShape(6.dp),
            )
            .border(
                2.dp,
                when {
                    !enabled -> MomoColors.ButtonDisabledContent
                    checked -> MomoColors.BrandDeep
                    else -> MomoColors.TextSecondary
                },
                RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("\u2713", style = MomoType.CaptionBold, color = MomoColors.BrandDarkest)
        }
    }
}
