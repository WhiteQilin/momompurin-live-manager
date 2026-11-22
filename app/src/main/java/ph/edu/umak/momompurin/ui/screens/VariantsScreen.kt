package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import ph.edu.umak.momompurin.data.CartOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoMessages
import ph.edu.umak.momompurin.ui.components.ProductImage
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * A-13 c4: a variant parent's list.
 *
 * Each variant is its own sellable row with its own price and stock status, which
 * is what `MPBOW-PK Pink P220.00` / `MPBOW-LV Lavender P220.00` means in the
 * criteria.
 */
@Composable
fun VariantsScreen(
    store: MomoStore,
    parentSku: String,
    onBack: () -> Unit,
) {
    val data by store.data.collectAsState()

    val parent = data.product(parentSku)
    val variants = data.variantsOf(parentSku)

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(
            title = parent?.name ?: parentSku,
            onBack = onBack,
            contextPill = parentSku,
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.Lg,
                bottom = MomoSpace.BottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            items(variants, key = { it.sku }) { variant ->
                MomoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(
                            photoRef = variant.photoRef ?: parent?.photoRef,
                            contentDescription = OrderOps.variantName(variant).ifBlank { variant.name },
                            modifier = Modifier.size(60.dp),
                        )
                        Spacer(Modifier.width(MomoSpace.Md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                OrderOps.variantName(variant).ifBlank { variant.name },
                                style = MomoType.TitleSmall,
                                color = MomoColors.TextPrimary,
                            )
                            Text(
                                variant.sku,
                                style = MomoType.DataCaption,
                                color = MomoColors.TextSecondary,
                            )
                            Text(
                                MomoMoney.format(variant.price),
                                style = MomoType.DataMonospace,
                                color = MomoColors.BrandDeep,
                            )
                            Text(
                                "${variant.available} available",
                                style = MomoType.CaptionBold,
                                color = when (variant.stockStatus) {
                                    ph.edu.umak.momompurin.data.entity.StockStatus.IN_STOCK ->
                                        MomoColors.SuccessText
                                    ph.edu.umak.momompurin.data.entity.StockStatus.LOW_STOCK ->
                                        MomoColors.WarningText
                                    else -> MomoColors.DangerInk
                                },
                            )
                            StatusBadge(MomoBadges.forStock(variant.stockStatus))
                        }
                        ShopAddButton(
                            enabled = variant.stockStatus !=
                                ph.edu.umak.momompurin.data.entity.StockStatus.OUT_OF_STOCK,
                            onAdd = {
                                store.addToCart(variant.sku)
                                // A.5 / D.1: the same floating snackbar the Shop and
                                // PDP raise, with the item named. This screen used to
                                // set an inline `message` line that scrolled away -
                                // the exact understated confirmation the criterion
                                // replaces.
                                MomoMessages.show(
                                    "${CartOps.ADDED_TO_CART} ${variant.name}" +
                                        OrderOps.variantName(variant)
                                            .takeIf { it.isNotBlank() }
                                            ?.let { " - $it" }
                                            .orEmpty(),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopAddButton(enabled: Boolean, onAdd: () -> Unit) {
    Spacer(Modifier.height(0.dp))
    ph.edu.umak.momompurin.ui.components.MomoButton(
        label = "Add to cart",
        onClick = onAdd,
        enabled = enabled,
        compact = true,
    )
}
