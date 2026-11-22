package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.entity.StockStatus
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.components.ProductImage
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

@Composable
fun OwnerVariantsScreen(
    store: MomoStore,
    parentSku: String,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    val data by store.data.collectAsState()
    val parent = data.product(parentSku)
    val variants = data.variantsOf(parentSku)

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(
            title = parent?.name ?: "Variants",
            onBack = onBack,
            contextPill = parentSku,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                top = MomoSpace.Lg,
                end = MomoSpace.Lg,
                bottom = MomoSpace.BottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            if (variants.isEmpty()) {
                item {
                    Text("No active variants.", style = MomoType.BodyDefault, color = MomoColors.TextSecondary)
                }
            }
            items(variants, key = { it.sku }) { variant ->
                MomoCard(Modifier.clickable { onOpenProduct(variant.sku) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(
                            photoRef = variant.photoRef,
                            contentDescription = variant.name,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.size(MomoSpace.Md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                OrderOps.variantName(variant).ifBlank { variant.name },
                                style = MomoType.TitleSmall,
                                color = MomoColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(variant.sku, style = MomoType.DataCaption, color = MomoColors.TextSecondary)
                            Text(MomoMoney.format(variant.price), style = MomoType.DataMonospace, color = MomoColors.BrandDeep)
                        }
                        StatusBadge(MomoBadges.forStock(variant.stockStatus))
                    }
                    Spacer(Modifier.height(MomoSpace.Sm))
                    Text(
                        "On hand ${variant.physical} · Reserved ${variant.reserved} · Available ${variant.available}",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
            }
        }
    }
}
