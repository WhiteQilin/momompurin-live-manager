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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.CartOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.entity.Fulfillment
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * A-15 Checkout and Payment.
 *
 * Three things this screen must NOT do, each an explicit criterion:
 *  - add a shipping fee (A-15 c1) - the total is the item subtotal, full stop;
 *  - collect a payment or a payment reference (A-15 c8) - payment is arranged
 *    off-app after the owner sends the invoice;
 *  - place the order on the first tap (A-15 c4) - the Additional Shipping Fee
 *    Notice stands between the tap and the write.
 */
@Composable
fun CheckoutScreen(
    store: MomoStore,
    onBack: () -> Unit,
    onPlaced: (String) -> Unit,
) {
    val data by store.data.collectAsState()
    val account = store.session.value
    // D.4: the customer's REAL connectivity. The owner's test switch is never
    // read here, so it cannot block a checkout it was never meant to gate.
    val deviceOnline = store.deviceOnline.collectAsState().value
    val customerOffline = deviceOnline == false

    // Prefilled from the profile, and editable here (A-15 c2).
    var recipient by remember { mutableStateOf(account?.name.orEmpty()) }
    var mobile by remember { mutableStateOf(account?.mobile.orEmpty()) }
    var province by remember { mutableStateOf(account?.province.orEmpty()) }
    var city by remember { mutableStateOf(account?.city.orEmpty()) }
    var barangay by remember { mutableStateOf(account?.barangay.orEmpty()) }
    var addressLine by remember { mutableStateOf(account?.addressLine.orEmpty()) }
    var fulfillment by remember { mutableStateOf(Fulfillment.SHIP_NOW) }
    var showNotice by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // D.4: a customer with no connection gets the full-screen offline state with
    // its Try Again button, not a disabled button with no explanation. Try Again
    // simply re-reads the flag the connectivity callback keeps current.
    if (customerOffline) {
        ph.edu.umak.momompurin.ui.components.CustomerOfflineState(
            onRetry = { store.setDeviceOnline(!customerOffline) },
        )
        return
    }

    val rows = CartOps.refresh(data, store.cart())
    val selected = rows.filter { it.contributes }
    val subtotal = CartOps.subtotal(rows)

    fun place() {
        error = null
        val result = store.placeOrder(
            fulfillment = fulfillment,
            recipientName = recipient,
            mobile = mobile,
            province = province,
            city = city,
            barangay = barangay,
            addressLine = addressLine,
        )
        when (result) {
            is OrderOps.PlaceResult.Placed -> {
                showNotice = false
                onPlaced(result.orderId)
            }
            is OrderOps.PlaceResult.Refused -> {
                showNotice = false
                error = result.message
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Checkout", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MomoSpace.Lg)
                .padding(top = MomoSpace.Lg, bottom = MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            // ------------------------------------------------- the items ----
            MomoCard {
                Text("Items", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))
                selected.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = MomoSpace.Xs)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.product?.name ?: row.line.sku,
                                style = MomoType.BodyDefault,
                                color = MomoColors.TextPrimary,
                            )
                            val variant = row.product?.let { OrderOps.variantName(it) }.orEmpty()
                            Text(
                                listOfNotNull(
                                    variant.takeIf { it.isNotBlank() },
                                    "\u00D7 ${row.line.qty}",
                                ).joinToString(" \u00B7 "),
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                        Text(
                            MomoMoney.format(row.lineTotal),
                            style = MomoType.DataCaption,
                            color = MomoColors.TextPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(MomoSpace.Sm))
                ph.edu.umak.momompurin.ui.components.MomoDivider()
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // A-15 c1: ITEM SUBTOTAL, and no shipping-fee line beneath it.
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Item subtotal",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                        Text(
                            "Shipping fees are arranged separately",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                    Text(
                        MomoMoney.format(subtotal),
                        style = MomoType.DataMonospace,
                        color = MomoColors.BrandDeep,
                    )
                }
            }

            // --------------------------------------------- delivery details --
            Text("Delivery details", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
            MomoField(
                value = recipient,
                onValueChange = { recipient = it; error = null },
                label = "Recipient name",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MomoField(
                value = mobile,
                onValueChange = { mobile = it; error = null },
                label = "Mobile number",
                labelOutside = true,
                keyboardType = KeyboardType.Phone,
                modifier = Modifier.fillMaxWidth(),
            )
            // C.1: the same cascading picker Registration uses, so an address can
            // never be a province the city list cannot match. A prefilled profile
            // address flows straight in; a value outside the map shows as manual.
            ph.edu.umak.momompurin.ui.components.PhAddressCascade(
                province = province,
                city = city,
                barangay = barangay,
                onChange = { p, c, b ->
                    province = p
                    city = c
                    barangay = b
                    error = null
                },
            )
            MomoField(
                value = addressLine,
                onValueChange = { addressLine = it; error = null },
                // UAT fix (item 10): matches the label Registration and Edit
                // account now use, for the street-level part of the address.
                label = "Street Name, Building, House No.",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ------------------------------------------------ fulfillment ----
            Text("Fulfillment", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
            Fulfillment.entries.forEach { option ->
                FulfillmentOption(
                    label = option.label,
                    selected = fulfillment == option,
                    onSelect = { fulfillment = option },
                )
            }

            if (error != null) {
                Text(
                    error!!,
                    style = MomoType.CaptionBold,
                    color = MomoColors.DangerInk,
                )
            }

            Spacer(Modifier.height(MomoSpace.Sm))

            // A-15 c4: the first tap opens the notice; it does not place.
            MomoButton(
                label = "Agree & Place Order",
                onClick = { showNotice = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

    // ---------------------------------------- A-15 c4: the fee notice --------
    if (showNotice) {
        MomoConfirmDialog(
            title = "Additional Shipping Fee Notice",
            message = "Shipping fees are handled separately. The owner will confirm " +
                "the fee with your invoice, and payment by GCash, Maya or Bank " +
                "Transfer is arranged after the invoice arrives.",
            confirmLabel = "Agree & Place Order",
            dismissLabel = "Cancel",
            onConfirm = { place() },
            onDismiss = { showNotice = false },
        )
    }
}

/** One radio-style fulfillment row: a bordered card, ticked when chosen. */
@Composable
private fun FulfillmentOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(
                if (selected) MomoColors.SurfaceMutedVariant else MomoColors.SurfaceCard,
                RoundedCornerShape(MomoRadius.Input),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MomoColors.BrandDeep else MomoColors.BorderSubtle,
                RoundedCornerShape(MomoRadius.Input),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = MomoSpace.Md, vertical = MomoSpace.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(
                    if (selected) MomoColors.BrandDeep else MomoColors.SurfaceCard,
                    RoundedCornerShape(999.dp),
                )
                .border(2.dp, MomoColors.BrandDeep, RoundedCornerShape(999.dp)),
        )
        Text(
            label,
            style = MomoType.BodyDefault,
            color = MomoColors.TextPrimary,
            modifier = Modifier.padding(start = MomoSpace.Md),
        )
    }
}
