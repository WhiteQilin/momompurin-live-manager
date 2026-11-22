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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.CartOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.data.entity.StockStatus
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoMessages
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.components.MetaChip
import ph.edu.umak.momompurin.ui.components.ProductImage
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * D.2: the unified Customer Product Detail Page.
 *
 * ## What was wrong
 *
 * Tapping a variant product jumped straight to a variant picker with prices and
 * stock but NO parent context - no description, no specs, no photo that changed -
 * which is the exact "confusion and disorientation" the tester reported. The old
 * CustomerProductDetailScreen refused `parentSku != null` rows outright, so a
 * variant SKU had a detail page but a parent did not, and `Lavender` reused the
 * Pink photo because both photoRefs resolved to the same asset.
 *
 * ## The unified flow
 *
 * EVERY product card opens THIS screen, parents and single products alike:
 *
 *  - the parent row supplies the photo hero, name, description and specs;
 *  - variant chips (Pink / Lavender) swap the photo, SKU, price and live stock
 *    inline - selecting Lavender shows `prod_mpbow_lv`, its own asset;
 *  - a single product shows the same layout with no chip row;
 *  - a quantity stepper caps at the LIVE available count of the selected variant;
 *  - Add to cart writes the cart and nothing else (A-14 c1).
 *
 * Strictly zero internal costs: like every customer screen this reads `price`
 * only, and the PDP adds description/specs prose which carry no numbers from the
 * owner columns.
 */
@Composable
fun CustomerProductDetailScreen(
    store: MomoStore,
    sku: String,
    onBack: () -> Unit,
    onViewCart: () -> Unit,
) {
    val data by store.data.collectAsState()
    val session by store.session.collectAsState()

    // The row the route was opened with. A variant SKU is allowed here: it selects
    // its own variant on its parent's page rather than refusing.
    val requested = data.product(sku)
    if (requested == null || !requested.active) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = "Product", onBack = onBack)
            Column(
                Modifier.fillMaxSize().padding(MomoSpace.Lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "This product is no longer available.",
                    style = MomoType.BodyDefault,
                    color = MomoColors.TextSecondary,
                )
                Spacer(Modifier.height(MomoSpace.Md))
                MomoButton(label = "Back to shop", onClick = onBack)
            }
        }
        return
    }

    // Resolve the DISPLAY root: a variant shows its parent's page.
    val root = requested.parentSku?.let { data.product(it) } ?: requested
    // The variants selectable on this page. A parent lists its children; a
    // standalone product is its own single "variant".
    val variants: List<Product> = if (root.isParent) {
        data.variantsOf(root.sku).filter { it.price > 0 }
    } else {
        listOf(root)
    }
    // The variant the chips have selected, defaulting to the requested row so a
    // deep link onto MPBOW-LV opens showing Lavender.
    var selectedSku by remember(sku) {
        mutableStateOf(
            if (variants.any { it.sku == requested.sku }) requested.sku else variants.firstOrNull()?.sku,
        )
    }
    val selected = variants.firstOrNull { it.sku == selectedSku } ?: root
    val quantity = remember(selectedSku) { mutableIntStateOf(1) }
    val existingQuantity = data.cart(session?.email.orEmpty())
        .firstOrNull { it.sku == selected.sku }?.qty ?: 0
    val roomFor = (selected.available - existingQuantity).coerceAtLeast(0)
    val canAdd = selected.isPurchasable && roomFor > 0

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(
            title = root.name,
            onBack = onBack,
            contextPill = selected.sku,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(MomoSpace.Lg)),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            // The photo hero, following the selected variant.
            MomoCard {
                ProductImage(
                    photoRef = selected.photoRef ?: root.photoRef,
                    contentDescription = "${root.name} - ${variantLabel(selected, root)}",
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    cornerRadius = MomoRadius.Card,
                )
            }

            MomoCard {
                Text(root.name, style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Text(
                    selected.sku,
                    style = MomoType.DataCaption,
                    color = MomoColors.TextSecondary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                Column(verticalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                    MetaChip("Category: ${root.category}")
                    MetaChip("Character: ${root.character}")
                }
                Spacer(Modifier.height(MomoSpace.Sm))
                // D.2: the description and the spec line - the parent context the
                // old flow skipped. Owner-only columns are never read.
                if (root.description.isNotBlank()) {
                    Text(
                        root.description,
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextPrimary,
                    )
                }
                if (root.specs.isNotBlank()) {
                    Text(
                        root.specs,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(MomoSpace.Sm))
                Text(
                    MomoMoney.format(selected.price),
                    style = MomoType.DataMonospace,
                    color = MomoColors.BrandDeep,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(MomoBadges.forStock(selected.stockStatus))
                    Spacer(Modifier.size(MomoSpace.Sm))
                    Text(
                        // E.3: the low-stock count in the burnt-rust ink, so an
                        // urgent figure reads as urgent rather than blending into
                        // the badge beside it.
                        "${selected.available} available",
                        style = MomoType.DataCaption,
                        color = if (selected.stockStatus == StockStatus.LOW_STOCK) {
                            MomoColors.LowStockInk
                        } else {
                            MomoColors.TextPrimary
                        },
                    )
                }
            }

            // D.2: the variant chips, when there is a choice to make.
            if (variants.size > 1) {
                MomoCard {
                    Text("Choose a colourway", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                    Spacer(Modifier.height(MomoSpace.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                        variants.forEach { variant ->
                            val isSelected = variant.sku == selectedSku
                            val soldOut = variant.available <= 0
                            Box(
                                Modifier
                                    .heightIn(min = 44.dp)
                                    .background(
                                        if (isSelected) MomoColors.SurfaceMutedVariant else MomoColors.SurfaceCard,
                                        RoundedCornerShape(MomoRadius.Input),
                                    )
                                    .border(
                                        if (isSelected) 2.dp else 1.dp,
                                        when {
                                            isSelected -> MomoColors.BrandDeep
                                            soldOut -> MomoColors.SurfaceBorder
                                            else -> MomoColors.BorderSubtle
                                        },
                                        RoundedCornerShape(MomoRadius.Input),
                                    )
                                    .clickable(enabled = !soldOut) {
                                        selectedSku = variant.sku
                                        quantity.intValue = 1
                                    }
                                    .padding(horizontal = MomoSpace.Md, vertical = MomoSpace.Sm),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${variantLabel(variant, root)} \u00B7 ${MomoMoney.format(variant.price)}",
                                    style = MomoType.LabelChip,
                                    color = if (soldOut) MomoColors.TextDisabled else MomoColors.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }

            // D.2: the quantity stepper, capped at the LIVE available count.
            MomoCard {
                Text("Quantity", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepperButton(
                        label = "\u2212",
                        enabled = quantity.intValue > 1,
                        onClick = { if (quantity.intValue > 1) quantity.intValue -= 1 },
                    )
                    Text(
                        quantity.intValue.toString(),
                        style = MomoType.DataMonospace,
                        color = MomoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = MomoSpace.Lg),
                    )
                    StepperButton(
                        label = "+",
                        enabled = quantity.intValue < selected.available,
                        onClick = { quantity.intValue += 1 },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (selected.available <= 0) {
                            "None left"
                        } else {
                            "${selected.available} available"
                        },
                        style = MomoType.CaptionBold,
                        color = if (selected.stockStatus == StockStatus.LOW_STOCK) {
                            MomoColors.LowStockInk
                        } else {
                            MomoColors.SuccessText
                        },
                    )
                }
            }

            MomoButton(
                label = "Add to cart",
                onClick = {
                    // A-14 c1: adding writes the CART and nothing else. Repeat taps
                    // bump the row, still capped at the live count.
                    repeat(quantity.intValue) {
                        store.addToCart(selected.sku)
                    }
                    MomoMessages.show("${CartOps.ADDED_TO_CART} ${root.name}")
                },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth(),
            )
            MomoButton(
                label = "View cart",
                onClick = onViewCart,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}

/** The customer-facing variant label: Pink / Lavender from the SKU suffix. */
private fun variantLabel(product: Product, root: Product): String =
    OrderOps.variantName(product).ifBlank {
        if (product.sku == root.sku) product.name else product.sku
    }

/** The PDP's stepper button: the same bordered shape the Cart row uses. */
@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 44.dp, height = MomoDimens.MiniActionHeight + 10.dp)
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
