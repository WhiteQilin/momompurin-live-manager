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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.CartOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.ProductStockSummary
import ph.edu.umak.momompurin.data.SearchPipeline
import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.data.entity.StockStatus
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.ProductImage
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.components.NotificationBell
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * A-13 Browse Product Catalog.
 *
 * A-13 c6 is the structural rule: this screen reads `price` and nothing else off
 * a product. Pack cost, unit cost and markup are not formatted anywhere here, so
 * there is no filter that could be forgotten - the internal columns simply have
 * no rendering path.
 *
 * ## The search pipeline (Phase 2.1)
 *
 * Typing goes through [SearchPipeline.search], which is the compiled ALGOCOM
 * stack wired live:
 *
 *  1. exact / prefix SKU via the PrefixTrie (`CH`, `SCWH`, `MPBOW-PK`) - O(|p|+k);
 *  2. substring over name / SKU / category / character;
 *  3. the inverted trigram index + Jaccard via [TrigramProductMatcher] for the
 *     typo case, so `chikwa wips` still surfaces Chiikawa Wipes ranked first.
 *
 * The parent-resolution rule from A-13 c4 is preserved: a match on `MPBOW-PK`
 * surfaces the single MPBOW parent card, never a bare variant row.
 *
 * ## D.3: the scroll cue
 *
 * Both chip rows end in a cream fade on their right edge - the affordance the
 * tester asked for when the overflow chips were invisible.
 */
@Composable
fun ShopScreen(
    store: MomoStore,
    onOpenVariants: (String) -> Unit,
    onOpenCart: () -> Unit,
    onOpenProductDetail: (String) -> Unit = {},
    onOpenOrder: (String) -> Unit = {},
) {
    val data by store.data.collectAsState()
    val session by store.session.collectAsState()
    val cartCount = data.cart(session?.email.orEmpty()).sumOf { it.qty }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var character by remember { mutableStateOf<String?>(null) }

    // E.1: the customer's derived alerts - invoices awaiting payment, verified
    // payments, packed/in-transit updates, cancellations.
    var showAlerts by remember { mutableStateOf(false) }
    val alerts = ph.edu.umak.momompurin.ui.components.customerAlerts(
        data, session?.email.orEmpty(), store.now,
    )

    // D.4: the device's REAL connectivity, never the Owner's test switch. When it
    // has positively reported no connection the catalogue is replaced outright -
    // the customer cannot browse a shop that cannot take an order, and a disabled
    // grid with no explanation is the dead end the criterion names.
    val deviceOnline = store.deviceOnline.collectAsState().value
    if (deviceOnline == false) {
        ph.edu.umak.momompurin.ui.components.CustomerOfflineState(
            onRetry = { store.setDeviceOnline(true) },
        )
        return
    }

    // The pipeline is rebuilt on catalogue change; querying it is per-keystroke.
    val pipeline = remember(data.products) { SearchPipeline.build(data) }

    val results = remember(data.products, pipeline, query, category, character) {
        val base = pipeline.search(query)
        // A-13 c3: the chips narrow the searched set, on top of the text match.
        if (category == null && character == null) {
            base
        } else {
            base.filter { card ->
                val related = if (card.isParent) {
                    listOf(card) + data.variantsOf(card.sku)
                } else {
                    listOf(card)
                }
                related.any {
                    (category == null || it.category == category) &&
                        (character == null || it.character == character)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(
            title = "Shop",
            contextPill = "Momompurin",
            actions = {
                // E.1: the bell first, then the badged cart.
                ph.edu.umak.momompurin.ui.components.NotificationBell(
                    count = alerts.size,
                    onClick = { showAlerts = true },
                )
                // D.2: the persistent item-count badge lives ON the cart icon in
                // the top bar, so the count is visible from any screen without
                // reading a label.
                CartIconButton(count = cartCount, onClick = onOpenCart)
            },
        )

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
            item {
                // A-13 c2: search by product name or product code - now through the
                // trigram fallback, so a misspelled name still finds its row.
                MomoField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search products",
                    labelOutside = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // A-13 c3 + D.3: the filter chip rows, each fading at its right edge.
            item {
                FilterChipRow(
                    labels = data.categories(),
                    selected = category,
                    onSelect = { category = if (category == it) null else it },
                )
            }
            item {
                FilterChipRow(
                    labels = data.characters(),
                    selected = character,
                    onSelect = { character = if (character == it) null else it },
                )
            }

            if (results.isEmpty()) {
                // A-13 c2's zero-match state.
                item {
                    Text(
                        "No products found.",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextSecondary,
                        modifier = Modifier.padding(vertical = MomoSpace.Xl),
                    )
                }
            }

            items(results, key = { it.sku }) { product ->
                // A-13 c4: a parent quotes "from" its cheapest variant, because the
                // parent itself is priced at zero and is never bought directly.
                ShopProductCard(
                    product = product,
                    hasVariants = product.isParent,
                    priceOverride = if (product.isParent) data.parentPriceFrom(product.sku) else null,
                    stockSummary = if (product.isParent) data.stockSummary(product) else null,
                    variantCount = if (product.isParent) data.parentVariantCount(product.sku) else null,
                    onOpen = {
                        // D.2: EVERY card opens the unified PDP, parents included -
                        // the variant picker IS the PDP's variant chips, so
                        // "View variants" as a separate flow is gone.
                        onOpenProductDetail(product.sku)
                    },
                )
            }
        }
    }

    // E.1: the customer's alert sheet. Order alerts open the order detail.
    if (showAlerts) {
        ph.edu.umak.momompurin.ui.components.NotificationSheet(
            alerts = alerts,
            onOpenOrder = onOpenOrder,
            onDismiss = { showAlerts = false },
        )
    }
}

/**
 * The cart entry point with the live badge (D.2).
 *
 * A pill ON the icon, visible on every customer screen, rather than a "Cart (n)"
 * button whose number the eye has to parse: a badge is the convention e-commerce
 * users already read at a glance.
 */
@Composable
private fun CartIconButton(count: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = MomoSpace.Sm)
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Pill))
                .border(1.dp, MomoColors.BrandDeep, RoundedCornerShape(MomoRadius.Pill)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(
                    ph.edu.umak.momompurin.R.drawable.ic_cart,
                ),
                contentDescription = "Cart",
                tint = MomoColors.BrandDeep,
                modifier = Modifier.size(20.dp),
            )
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(MomoColors.DangerBase, RoundedCornerShape(MomoRadius.Pill)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (count > 9) "9+" else count.toString(),
                    style = MomoType.LabelChip,
                    color = MomoColors.InkInverse,
                )
            }
        }
    }
}

/**
 * A horizontally scrolling chip row with the D.3 fade on its overflowing edge.
 *
 * The fade is a fixed 40dp cream gradient overlaying the row's right edge. It does
 * not need scroll-state tracking: the last chip sits under it either way, and when
 * the row is scrolled to its end the final chip's own padding still reads through
 * the gradient. A conditional fade that vanished at scroll-end would flicker as the
 * user scrolled, which is worse than a constant, subtle cue.
 */
@Composable
private fun FilterChipRow(
    labels: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
        ) {
            labels.forEach { label ->
                val isSelected = label == selected
                Box(
                    Modifier
                        .heightIn(min = 32.dp)
                        .background(
                            if (isSelected) MomoColors.ButtonPrimaryFill else MomoColors.SurfaceCard,
                            RoundedCornerShape(MomoRadius.Pill),
                        )
                        .border(
                            1.dp,
                            if (isSelected) MomoColors.BrandDeep else MomoColors.BorderSubtle,
                            RoundedCornerShape(MomoRadius.Pill),
                        )
                        .clickable { onSelect(label) }
                        .padding(horizontal = MomoSpace.Md, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MomoType.LabelChip,
                        color = if (isSelected) MomoColors.BrandDarkest else MomoColors.TextSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        // D.3: the scroll cue. Drawn AFTER the row so it overlays it.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(width = 40.dp, height = 32.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to MomoColors.SurfaceCanvas.copy(alpha = 0.0f),
                        1f to MomoColors.SurfaceCanvas,
                    ),
                ),
        )
    }
}

/**
 * One product card.
 *
 * A-13 c1 lists what the card must carry (photo, name, price, stock status) and
 * A-13 c5 names the three stock labels. Every card - including a parent - opens the
 * unified PDP (D.2), so the card no longer carries its own Add to cart: the PDP's
 * variant chips + quantity stepper is where the purchase decision happens.
 */
@Composable
fun ShopProductCard(
    product: Product,
    hasVariants: Boolean,
    onOpen: () -> Unit,
    /** A-13 c4: a parent's own price is zero, so it quotes its cheapest variant. */
    priceOverride: Long? = null,
    /** Aggregate stock display for parents; ordinary products derive their own status. */
    stockSummary: ProductStockSummary? = null,
    /** Number of sellable variants on a parent, when available. */
    variantCount: Int? = null,
) {
    val status = stockSummary?.let { summary ->
        when {
            summary.available <= 0 -> StockStatus.OUT_OF_STOCK
            summary.available <= 2 -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }
    } ?: product.stockStatus
    MomoCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                photoRef = product.photoRef,
                contentDescription = product.name,
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.size(MomoSpace.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    product.sku,
                    style = MomoType.DataCaption,
                    color = MomoColors.TextSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(MomoSpace.Xs))
                // A-13 c1: the price, always two decimals. A parent shows the
                // cheapest variant prefixed "from", since its own price is zero.
                if (!hasVariants) {
                    Text(
                        MomoMoney.format(product.price),
                        style = MomoType.DataMonospace,
                        color = MomoColors.BrandDeep,
                    )
                }
                Spacer(Modifier.height(MomoSpace.Xs))
                StatusBadge(MomoBadges.forStock(status))
                if (hasVariants && variantCount != null) {
                    Text(
                        "$variantCount ${if (variantCount == 1) "variant" else "variants"} \u00B7 " +
                            "from ${MomoMoney.format(priceOverride ?: product.price)}",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
