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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.CartOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.ProductImage
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * A-14 Manage Shopping Cart (Shopee-style).
 *
 * The screen's job is to keep three things visibly separate, because confusing
 * them is what the criteria are written against:
 *
 *  - **selection** - the checkbox, and the subtotal counts only ticked rows;
 *  - **quantity** - capped at the LIVE available count, with its own message;
 *  - **availability** - a row can go Sold Out under the customer, and must then
 *    untick itself and stop contributing.
 *
 * Nothing on this screen writes a product's reserved count: the cart holds stock
 * for nobody (A-14 c1).
 */
@Composable
fun CartScreen(
    store: MomoStore,
    onCheckout: () -> Unit,
    onBrowse: () -> Unit,
) {
    val data by store.data.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    // A-14 c5: refresh against live stock on arrival, so a unit taken by another
    // customer while this customer browsed turns the row Sold Out on sight.
    LaunchedEffect(Unit) { store.refreshCart() }

    val rows = CartOps.refresh(data, store.cart())
    val subtotal = CartOps.subtotal(rows)
    val canCheckout = CartOps.canCheckout(rows)

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Cart")

        if (rows.isEmpty()) {
            // A-14 c7: the empty state, with a way back to the Shop (Nielsen #3 -
            // an empty state without an exit is a dead end).
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(MomoSpace.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(MomoSpace.Xxl))
                Text(
                    CartOps.EMPTY_CART,
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Spacer(Modifier.height(MomoSpace.Lg))
                MomoButton(label = "Browse products", onClick = onBrowse)
            }
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            items(rows, key = { it.line.sku }) { row ->
                CartRow(
                    sku = row.line.sku,
                    name = row.product?.name ?: row.line.sku,
                    photoRef = row.product?.photoRef
                        ?: row.product?.parentSku?.let { data.product(it)?.photoRef },
                    variantName = row.product?.let { ph.edu.umak.momompurin.data.OrderOps.variantName(it) }
                        .orEmpty(),
                    unitPrice = row.unitPrice,
                    qty = row.line.qty,
                    selected = row.line.selected,
                    soldOut = row.soldOut,
                    available = row.available,
                    stockLabel = row.stockLabel,
                    stockMessage = row.stockMessage,
                    lineTotal = row.lineTotal,
                    onToggle = { store.setCartSelected(row.line.sku, it) },
                    onIncrease = { message = store.increaseCartQty(row.line.sku) },
                    onDecrease = {
                        store.decreaseCartQty(row.line.sku)
                        message = null
                    },
                    onRemove = {
                        store.removeFromCart(row.line.sku)
                        message = null
                    },
                )
            }
        }

        // The subtotal bar. A-14 c2: it counts the ticked rows only, which is why
        // it reads from `rows` rather than from the cart list.
        Column(
            Modifier
                .fillMaxWidth()
                .background(MomoColors.SurfaceCard)
                .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Md),
        ) {
            if (message != null) {
                Text(
                    message!!,
                    style = MomoType.CaptionBold,
                    color = MomoColors.DangerInk,
                    modifier = Modifier.padding(bottom = MomoSpace.Sm),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Subtotal (ticked items)",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                    Text(
                        MomoMoney.format(subtotal),
                        style = MomoType.DataMonospace,
                        color = MomoColors.BrandDeep,
                    )
                }
                MomoButton(
                    label = "Checkout",
                    onClick = onCheckout,
                    enabled = canCheckout,
                )
            }
        }
    }
}

/**
 * One cart row.
 *
 * The checkbox is a bordered 24dp box, never a bare glyph: an unchecked control
 * that is only an outline on a white card is invisible at a glance, and A-14 c2
 * makes the checkbox the switch for what gets bought.
 */
@Composable
private fun CartRow(
    sku: String,
    name: String,
    photoRef: String?,
    variantName: String,
    unitPrice: Long,
    qty: Int,
    selected: Boolean,
    soldOut: Boolean,
    available: Int,
    stockLabel: String,
    stockMessage: String?,
    lineTotal: Long,
    onToggle: (Boolean) -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    MomoCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SelectionBox(
                    checked = selected,
                    enabled = !soldOut,
                    onToggle = onToggle,
                )
                ProductImage(
                    photoRef = photoRef,
                    contentDescription = name,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.width(MomoSpace.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MomoType.TitleSmall,
                    color = if (soldOut) MomoColors.TextDisabled else MomoColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (variantName.isNotBlank()) {
                    Text(
                        variantName,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
                Text(sku, style = MomoType.DataCaption, color = MomoColors.TextSecondary)
                Spacer(Modifier.height(MomoSpace.Xs))
                Text(
                    MomoMoney.format(unitPrice),
                    style = MomoType.DataMonospace,
                    color = MomoColors.BrandDeep,
                )

                Spacer(Modifier.height(MomoSpace.Sm))
                // A-14 c3: the live remaining stock, on the row.
                Text(
                    stockLabel,
                    style = MomoType.CaptionBold,
                    color = when {
                        soldOut -> MomoColors.DangerInk
                        available <= 2 -> MomoColors.WarningText
                        else -> MomoColors.SuccessText
                    },
                )
                // A-14 c4/c6: the short-stock message, with the count in it.
                if (stockMessage != null) {
                    Text(
                        stockMessage,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.DangerInk,
                    )
                }

                Spacer(Modifier.height(MomoSpace.Sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!soldOut) {
                        StepperButton(label = "-", onClick = onDecrease, enabled = qty > 1)
                        Text(
                            qty.toString(),
                            style = MomoType.DataMonospace,
                            color = MomoColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = MomoSpace.Md),
                        )
                        StepperButton(label = "+", onClick = onIncrease)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        MomoMoney.format(lineTotal),
                        style = MomoType.DataMonospace,
                        color = MomoColors.TextPrimary,
                    )
                }

                Spacer(Modifier.height(MomoSpace.Sm))
                Row {
                    Text(
                        "Remove",
                        style = MomoType.LabelChip,
                        color = MomoColors.DangerInk,
                        maxLines = 1,
                        modifier = Modifier
                            .heightIn(min = MomoDimens.MiniActionHeight)
                            .border(
                                1.dp,
                                MomoColors.DangerInk,
                                RoundedCornerShape(MomoRadius.Input),
                            )
                            .clickable(onClick = onRemove)
                            .padding(horizontal = MomoSpace.Md, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The selection checkbox.
 *
 * Drawn as a 24dp bordered box with a tick when checked. A tick-only unchecked
 * state reads as decoration on a white card, and A-14 c2 makes this control the
 * one that decides what gets bought.
 */
@Composable
private fun SelectionBox(
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val borderColor = when {
        !enabled -> MomoColors.ButtonDisabledContent
        checked -> MomoColors.BrandDeep
        else -> MomoColors.TextSecondary
    }
    // The VISUAL box stays 24dp (a bigger tick would dominate the row), but the
    // TAP TARGET is 48dp: a 24dp control is half the accessible minimum, and a
    // thumb aiming at the checkbox inside a tall card misses it constantly. The
    // extra 24dp is transparent padding around the drawn box, so the row's
    // geometry is unchanged while the hit area becomes reachable.
    Box(
        Modifier
            .size(MomoDimens.TouchTarget)
            .clickable(enabled = enabled) { onToggle(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(
                    if (checked && enabled) MomoColors.BrandPrimary else MomoColors.SurfaceCard,
                    RoundedCornerShape(6.dp),
                )
                .border(2.dp, borderColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    "\u2713",
                    style = MomoType.CaptionBold,
                    color = MomoColors.BrandDarkest,
                )
            }
        }
    }
}

/** A 34dp stepper button, bordered so the tap target is visible. */
@Composable
private fun StepperButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(width = 40.dp, height = MomoDimens.MiniActionHeight)
            .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Input))
            .border(
                1.dp,
                if (enabled) MomoColors.BorderPrimary else MomoColors.SurfaceBorder,
                RoundedCornerShape(MomoRadius.Input),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MomoType.ButtonAction,
            color = if (enabled) MomoColors.InkStrong else MomoColors.TextDisabled,
        )
    }
}
