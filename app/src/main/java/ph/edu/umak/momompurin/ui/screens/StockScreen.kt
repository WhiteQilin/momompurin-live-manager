package ph.edu.umak.momompurin.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.CatalogueOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.SearchPipeline
import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.data.entity.StockStatus
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoPicker
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.components.ProductImage
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * A-03 Manage Products and Inventory - the stock list.
 *
 * Available is DISPLAYED, never stored: `Physical`, `Reserved` and `Available` are
 * one derived figure, so the three columns cannot drift apart. The All / Low stock
 * / Out of stock tabs are a partition of the same list.
 */
@Composable
fun StockScreen(
    store: MomoStore,
    onOpenProduct: (String) -> Unit,
    onOpenVariants: (String) -> Unit,
    onAddProduct: () -> Unit,
) {
    val data by store.data.collectAsState()
    val offline by store.offlineMode.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(StockFilter.ALL) }

    // Phase 2.1: the ALGOCOM pipeline over the catalogue, queried per keystroke.
    val pipeline = remember(data.products) { SearchPipeline.build(data) }

    val rows = remember(data.products, pipeline, query, filter) {
        val matched = pipeline.search(query)
        matched
            .filter { product ->
                product.parentSku == null && (!product.isParent ||
                    data.variantsOf(product.sku).isNotEmpty())
            }
            .filter { product ->
                when (filter) {
                    StockFilter.ALL -> true
                    StockFilter.LOW -> data.stockSummary(product).available in
                        1..ph.edu.umak.momompurin.data.MomoData.LOW_STOCK_LEVEL
                    StockFilter.OUT -> data.stockSummary(product).available == 0
                }
            }
            .sortedBy { it.sku }
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Stock")

        // A.7's offline banner is owned by MomoNavHost and drawn once above the
        // NavHost, so every Owner screen - this one included - carries it exactly
        // once. Stock used to render its own copy as well, which stacked two
        // identical banners on this screen and on Product Detail (v2.3 HCI audit
        // F-02). Do not reintroduce a per-screen banner here.

        Column(Modifier.padding(start = MomoSpace.Lg, end = MomoSpace.Lg, top = MomoSpace.Lg)) {
            // UAT fix (item 11): the magnifier-led 48-52dp search field, shared
            // with Shop, Orders and Customers so the four tabs match.
            ph.edu.umak.momompurin.ui.components.MomoSearchField(
                value = query,
                onValueChange = { query = it },
                label = "Search product or code",
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Md),
            horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
        ) {
            StageChip(
                label = "All",
                selected = filter == StockFilter.ALL,
                onClick = { filter = StockFilter.ALL },
            )
            StageChip(
                label = "Low stock",
                selected = filter == StockFilter.LOW,
                onClick = { filter = StockFilter.LOW },
            )
            StageChip(
                label = "Out of stock",
                selected = filter == StockFilter.OUT,
                onClick = { filter = StockFilter.OUT },
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                bottom = MomoSpace.Xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            item {
                MomoButton(
                    label = "+ Add product",
                    onClick = onAddProduct,
                    // A.7: a write control, disabled while offline - the screen
                    // half of the guarantee the store's gate enforces.
                    enabled = !offline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        "No products found.",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextSecondary,
                    )
                }
            }

            items(rows, key = { it.sku }) { product ->
                StockRow(
                    product = product,
                    variants = data.variantsOf(product.sku),
                    summary = data.stockSummary(product),
                    onClick = {
                        if (product.isParent) onOpenVariants(product.sku)
                        else onOpenProduct(product.sku)
                    },
                )
            }
        }
    }
}

private fun saveProductPhoto(context: Context, uri: Uri): String? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = generateSequence(1) { it * 2 }
        .takeWhile { bounds.outWidth / it > 800 || bounds.outHeight / it > 800 }
        .lastOrNull() ?: 1
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    val scale = minOf(1f, 800f / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        ).also { if (it !== decoded) decoded.recycle() }
    } else decoded
    val directory = java.io.File(context.filesDir, "product_photos")
    check(directory.exists() || directory.mkdirs())
    val file = java.io.File(directory, "${java.util.UUID.randomUUID()}.jpg")
    file.outputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
    }
    bitmap.recycle()
    file.absolutePath
}.getOrNull()

private enum class StockFilter { ALL, LOW, OUT }

/**
 * One product row.
 *
 * A-03 c5's three figures are shown together on every row, because the tester's
 * first check is `CHW 16 / 4 / 12` and a row that hides one of them makes the
 * arithmetic unverifiable.
 */
@Composable
private fun StockRow(
    product: Product,
    variants: List<Product>,
    summary: ph.edu.umak.momompurin.data.ProductStockSummary,
    onClick: () -> Unit,
) {
    MomoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                photoRef = product.photoRef,
                contentDescription = product.name,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.size(MomoSpace.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    style = MomoType.TitleSmall,
                    color = if (product.active) MomoColors.TextPrimary else MomoColors.TextDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    product.sku,
                    style = MomoType.DataCaption,
                    color = MomoColors.TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (product.isParent) {
                    Text(
                        "${variants.size} variants",
                        style = MomoType.LabelChip,
                        color = MomoColors.TextSecondary,
                    )
                } else {
                    Text(
                        MomoMoney.format(product.price),
                        style = MomoType.DataMonospace,
                        color = MomoColors.BrandDeep,
                    )
                }
                if (!product.isParent) {
                    StatusBadge(MomoBadges.forStock(product.stockStatus))
                } else {
                    Text(
                        "Variants",
                        style = MomoType.LabelChip,
                        color = MomoColors.TextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(MomoSpace.Sm))

        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Lg)) {
            StockFigure(label = "On hand", value = summary.physical)
            StockFigure(label = "Reserved", value = summary.reserved)
            StockFigure(
                label = "Available",
                value = summary.available,
                emphasise = true,
            )
        }
        if (product.isParent) {
            Spacer(Modifier.height(MomoSpace.Sm))
            Text(
                "${variants.size} variants · View variants",
                style = MomoType.ButtonAction,
                color = MomoColors.BrandDeep,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            if (product.isElectronic) {
                Spacer(Modifier.height(MomoSpace.Xs))
                Text(
                    "Electronic",
                    style = MomoType.LabelChip,
                    color = MomoColors.InfoText,
                )
            }
            if (!product.active) {
                Spacer(Modifier.height(MomoSpace.Xs))
                Text(
                    "Deactivated \u00B7 hidden from the shop",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextDisabled,
                )
            }
        }
    }
}

@Composable
private fun StockFigure(label: String, value: Int, emphasise: Boolean = false) {
    Column {
        Text(
            label,
            style = MomoType.CaptionSmall,
            color = MomoColors.TextSecondary,
        )
        Text(
            value.toString(),
            style = if (emphasise) MomoType.DataMonospace else MomoType.DataCaption,
            color = if (emphasise) MomoColors.BrandDeep else MomoColors.TextPrimary,
        )
    }
}

/**
 * A-03 c4-c7: one product's detail with its edit, adjust and deactivate controls.
 */
@Composable
fun ProductDetailScreen(
    store: MomoStore,
    sku: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
) {
    val data by store.data.collectAsState()
    val offline by store.offlineMode.collectAsState()
    val product = data.product(sku)
    var showDeactivate by remember { mutableStateOf(false) }

    if (product == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = sku, onBack = onBack)
            Text("That product no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(
            title = product.name,
            onBack = onBack,
            contextPill = product.sku,
        )

        // A.7's banner is drawn once by MomoNavHost above the NavHost; Product
        // Detail must not add a second copy (v2.3 HCI audit F-02).

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                    ProductImage(
                        photoRef = product.photoRef,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                    Spacer(Modifier.height(MomoSpace.Md))
                    KeyValueRow("Product code", product.sku)
                    KeyValueRow("Category", product.category.ifBlank { "\u2014" })
                KeyValueRow("Character", product.`character`.ifBlank { "\u2014" })
                KeyValueRow("Price", MomoMoney.format(product.price), emphasise = true)
                KeyValueRow("Packaging size", product.packSize)
                if (product.isElectronic) KeyValueRow("Electronic", "Yes")
                KeyValueRow("Status", if (product.active) "Active" else "Deactivated")
            }

            // A-03 c5: the three stock figures and the arithmetic they satisfy.
            MomoCard {
                Text("Stock", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Xl)) {
                    StockFigure(label = "On hand", value = product.physical)
                    StockFigure(label = "Reserved", value = product.reserved)
                    StockFigure(label = "Available", value = product.available, emphasise = true)
                }
                Spacer(Modifier.height(MomoSpace.Xs))
                Text(
                    "${product.physical} \u2212 ${product.reserved} = ${product.available}",
                    style = MomoType.DataCaption,
                    color = MomoColors.TextSecondary,
                )
            }

            // A-03 c2: the price chain, shown as the four figures the criterion
            // quotes rather than as a single opaque SRP.
            if (product.packCost > 0) {
                val preview = CatalogueOps.pricePreview(
                    packCost = product.packCost,
                    packQty = product.packQty,
                    packSize = product.packSize,
                    markupPercent = product.markupRate * 100,
                    // The stored markup is this product's own; the freebie is the
                    // shop setting, so the detail card and the edit form agree.
                    freebiePercent = CatalogueOps.rateSetting(
                        data.setting("freebie_rate"),
                        CatalogueOps.DEFAULT_FREEBIE_PERCENT,
                    ),
                )
                MomoCard {
                    Text(
                        "Price calculation",
                        style = MomoType.TitleSmall,
                        color = MomoColors.TextPrimary,
                    )
                    Spacer(Modifier.height(MomoSpace.Sm))
                    KeyValueRow("Pack cost", MomoMoney.format(product.packCost))
                    KeyValueRow("Pieces per pack", product.packQty.toString())
                    KeyValueRow("Unit cost", MomoMoney.format(preview.unitCost))
                    KeyValueRow("3% freebie", MomoMoney.format(preview.freebie))
                    KeyValueRow("Packaging", MomoMoney.format(preview.packagingCents))
                    KeyValueRow(
                        "Suggested retail price",
                        MomoMoney.format(preview.suggestedPrice),
                        emphasise = true,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                // A.7: every one of these is a write control; offline disables all
                // three rather than only the ones the last audit caught.
                MiniAction(label = "Edit product", onClick = onEdit, emphasised = true, enabled = !offline)
                MiniAction(label = "Adjust stock", onClick = onAdjust, enabled = !offline)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                MiniAction(
                    label = if (product.active) "Deactivate" else "Reactivate",
                    onClick = {
                        if (product.active) {
                            showDeactivate = true
                        } else {
                            store.setProductActive(product.sku, true)
                        }
                    },
                    destructive = product.active,
                    enabled = !offline,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

    // A-03 c7: deactivation confirms, then hides the product from the catalog.
    if (showDeactivate) {
        MomoConfirmDialog(
            title = "Deactivate ${product.sku}",
            message = "The product is hidden from the customer catalog. Its past " +
                "orders keep their records.",
            confirmLabel = "Deactivate",
            dismissLabel = "Cancel",
            destructive = true,
            onConfirm = {
                showDeactivate = false
                store.setProductActive(product.sku, false)
            },
            onDismiss = { showDeactivate = false },
        )
    }
}

/**
 * A-03 c1-c3: the add / edit product form.
 *
 * The live price preview is the point of the form: the owner types a pack cost and
 * a piece count and SEES the four derived figures before saving, so the chain in
 * A-03 c2 is verifiable rather than implied.
 */
@Composable
fun ProductFormScreen(
    store: MomoStore,
    sku: String,
    onBack: () -> Unit,
) {
    val data by store.data.collectAsState()
    val existing = if (sku == "NEW") null else data.product(sku)

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var code by remember { mutableStateOf(existing?.sku.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category.orEmpty()) }
    var character by remember { mutableStateOf(existing?.`character`.orEmpty()) }
    var price by remember {
        mutableStateOf(existing?.let { ph.edu.umak.momompurin.ui.theme.MomoMoney.plain(it.price) }.orEmpty())
    }
    var packCost by remember {
        mutableStateOf(existing?.let { ph.edu.umak.momompurin.ui.theme.MomoMoney.plain(it.packCost) }.orEmpty())
    }
    var packQty by remember { mutableStateOf(existing?.packQty?.toString() ?: "1") }
    var packSize by remember { mutableStateOf(existing?.packSize ?: "XS") }
    var electronic by remember { mutableStateOf(existing?.isElectronic ?: false) }
    var photoRef by remember { mutableStateOf(existing?.photoRef) }
    var notice by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var photoError by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoError = null
        if (uri != null) {
            photoRef = saveProductPhoto(context, uri) ?: run {
                photoError = "Could not save that photo. Please choose another image."
                photoRef
            }
        }
    }

    val packCostCents = ph.edu.umak.momompurin.ui.theme.MomoMoney.parse(packCost) ?: 0L
    val packQtyInt = packQty.toIntOrNull() ?: 0
    // A-10 c3: the form quotes the OWNER's saved rates, not fixed 30 / 3.
    // Hardcoding them here made both Settings fields write-only.
    val markupPercent = CatalogueOps.rateSetting(
        data.setting("profit_markup"), CatalogueOps.DEFAULT_MARKUP_PERCENT,
    )
    val freebiePercent = CatalogueOps.rateSetting(
        data.setting("freebie_rate"), CatalogueOps.DEFAULT_FREEBIE_PERCENT,
    )
    val preview = if (packCostCents > 0 && packQtyInt > 0) {
        CatalogueOps.pricePreview(
            packCostCents, packQtyInt, packSize, markupPercent, freebiePercent,
        )
    } else {
        null
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(
            title = if (existing == null) "Add product" else "Edit product",
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MomoSpace.Lg)
                .padding(top = MomoSpace.Lg, bottom = MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductImage(
                    photoRef = photoRef,
                    contentDescription = name.ifBlank { "Product photo preview" },
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.size(MomoSpace.Md))
                Column(Modifier.weight(1f)) {
                    MomoButton(
                        label = if (photoRef.isNullOrBlank()) "Add photo" else "Change photo",
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        primary = false,
                        compact = true,
                    )
                    if (photoError != null) {
                        Text(photoError!!, style = MomoType.CaptionSmall, color = MomoColors.DangerInk)
                    }
                }
            }
            MomoField(
                value = name,
                onValueChange = { name = it; notice = null },
                label = "Product name",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MomoField(
                value = code,
                onValueChange = { code = it; notice = null },
                label = "Product code",
                labelOutside = true,
                // A-03 c3: a code identifies the row, so an edit keeps it. The field
                // used to accept a new code and then save the OLD one anyway -
                // "Product saved." over an unchanged SKU, while a colliding code
                // still raised "Product code already exists." for a change that
                // would never be applied.
                readOnly = existing != null,
                supporting = if (existing != null) "The code cannot be changed." else null,
                modifier = Modifier.fillMaxWidth(),
            )
            // A-10 c4 -> A-03: the category and character come from the managed
            // lists, so an option the owner adds in Settings is immediately
            // choosable here. Free-text fields made A-10 c4's "reflected in Add
            // Product" unreachable - there was nothing to reflect it into.
            MomoPicker(
                label = "Category",
                selected = category.takeIf { it.isNotBlank() },
                options = data.productOptions
                    .filter { it.kind == ph.edu.umak.momompurin.data.entity.OptionKind.CATEGORY && it.active }
                    .sortedBy { it.sortOrder }
                    .map { ph.edu.umak.momompurin.ui.components.PickerOption(it.value) },
                onSelect = { category = it; notice = null },
                modifier = Modifier.fillMaxWidth(),
            )
            MomoPicker(
                label = "Character",
                selected = character.takeIf { it.isNotBlank() },
                options = data.productOptions
                    .filter { it.kind == ph.edu.umak.momompurin.data.entity.OptionKind.CHARACTER && it.active }
                    .sortedBy { it.sortOrder }
                    .map { ph.edu.umak.momompurin.ui.components.PickerOption(it.value) },
                onSelect = { character = it; notice = null },
                modifier = Modifier.fillMaxWidth(),
            )
            MomoField(
                value = price,
                onValueChange = { price = it; notice = null },
                label = "Price",
                labelOutside = true,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Pack", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
            MomoField(
                value = packCost,
                onValueChange = { packCost = it; notice = null },
                label = "Pack cost",
                labelOutside = true,
                supporting = "For example 380.00",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            MomoField(
                value = packQty,
                onValueChange = { packQty = it; notice = null },
                label = "Pieces per pack",
                labelOutside = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                listOf("XS", "S", "M", "L", "XL").forEach { tier ->
                    StageChip(
                        label = tier,
                        selected = packSize == tier,
                        onClick = { packSize = tier },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Electronic",
                    style = MomoType.BodyDefault,
                    color = MomoColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = electronic,
                    onCheckedChange = { electronic = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MomoColors.BrandDarkest,
                        checkedTrackColor = MomoColors.BrandPrimary,
                    ),
                )
            }

            // A-03 c2: the live preview of the four derived figures.
            if (preview != null) {
                MomoCard {
                    KeyValueRow("Unit cost", MomoMoney.format(preview.unitCost))
                    KeyValueRow("3% freebie", MomoMoney.format(preview.freebie))
                    KeyValueRow("Packaging", MomoMoney.format(preview.packagingCents))
                    KeyValueRow(
                        "Suggested retail price",
                        MomoMoney.format(preview.suggestedPrice),
                        emphasise = true,
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

            MomoButton(
                label = "Save product",
                // A.7: a save is a write; offline disables it alongside the
                // store's gate.
                enabled = !store.isOffline,
                onClick = {
                    // A-03 c3: the two refusals, in the criteria's order.
                    val required = CatalogueOps.requiredFieldsError(name, code)
                    if (required != null) {
                        notice = required
                        isError = true
                        return@MomoButton
                    }
                    // Only a NEW product can collide: an edit keeps its own code.
                    if (existing == null) {
                        val duplicate =
                            CatalogueOps.duplicateCodeError(code, data.products, null)
                        if (duplicate != null) {
                            notice = duplicate
                            isError = true
                            return@MomoButton
                        }
                    }

                    val priceCents = ph.edu.umak.momompurin.ui.theme.MomoMoney.parse(price)
                        ?: preview?.suggestedPrice
                        ?: 0L
                    val saved = (existing ?: Product(
                        sku = code.trim().uppercase(),
                        name = name.trim(),
                        price = priceCents,
                    )).copy(
                        sku = if (existing == null) code.trim().uppercase() else existing.sku,
                        name = name.trim(),
                        price = priceCents,
                        category = category.trim(),
                        character = character.trim(),
                        photoRef = photoRef,
                        packCost = packCostCents,
                        packQty = packQtyInt.coerceAtLeast(1),
                        packSize = packSize,
                        isElectronic = electronic,
                        unitCost = preview?.unitCost ?: 0L,
                    )
                    store.saveProduct(saved, changedFields = "product details")
                    // UAT fix (item 7): the confirmation goes through the floating
                    // snackbar host, not a line of text above the button. On this
                    // form the button is at the bottom of a scrolling page, so the
                    // inline notice appeared BELOW the fold on every save - the
                    // tester tapped Save, saw nothing, and tapped again.
                    isError = false
                    ph.edu.umak.momompurin.ui.components.MomoMessages.show(
                        CatalogueOps.PRODUCT_SAVED,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}

/**
 * A-03 c6: adjust the On hand count, with an OPTIONAL reason.
 *
 * The reason is deliberately not required - the client made it optional and both
 * halves are criteria: A-03 c6 adjusts with "Restock from supplier" and also
 * without a reason at all.
 */
@Composable
fun StockAdjustScreen(
    store: MomoStore,
    sku: String,
    onBack: () -> Unit,
) {
    val data by store.data.collectAsState()
    val offline by store.offlineMode.collectAsState()
    val product = data.product(sku)
    var physical by remember(product?.physical) {
        mutableStateOf(product?.physical?.toString().orEmpty())
    }
    var reason by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    if (product == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = "Adjust stock", onBack = onBack)
            Text("That product no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Adjust stock", onBack = onBack, contextPill = product.sku)

        // A.7's banner is drawn once by MomoNavHost above the NavHost; this screen
        // must not add a second copy (v2.3 HCI audit F-02).

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                KeyValueRow("Product", "${product.sku} \u00B7 ${product.name}")
                KeyValueRow("On hand now", product.physical.toString())
                KeyValueRow("Reserved", product.reserved.toString())
                KeyValueRow("Available now", product.available.toString(), emphasise = true)
            }

            MomoField(
                value = physical,
                onValueChange = { physical = it; notice = null },
                label = "New On hand",
                labelOutside = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            MomoField(
                value = reason,
                onValueChange = { reason = it; notice = null },
                label = "Reason",
                labelOutside = true,
                supporting = "Optional",
                modifier = Modifier.fillMaxWidth(),
            )

            // The arithmetic the owner is about to cause, shown before saving.
            val newPhysical = physical.toIntOrNull()
            if (newPhysical != null) {
                Text(
                    "Available will become " +
                        ((newPhysical - product.reserved).coerceAtLeast(0)),
                    style = MomoType.CaptionBold,
                    color = MomoColors.TextSecondary,
                )
            }

            if (notice != null) {
                Text(
                    notice!!,
                    style = MomoType.CaptionBold,
                    color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            MomoButton(
                label = "Save adjustment",
                enabled = !offline,
                onClick = {
                    // `validAdjustment` returns NULL when the count is VALID, so
                    // `let { validAdjustment(it) } ?: "error"` always produced the
                    // error: every adjustment was refused and A-03 c6 could not be
                    // walked at all. Distinguish "not a number" from "no problem"
                    // explicitly instead of leaning on null as both.
                    val value = physical.toIntOrNull()
                    val problem = if (value == null) {
                        "Enter a whole number for On hand."
                    } else {
                        CatalogueOps.validAdjustment(value)
                    }
                    if (problem != null) {
                        notice = problem
                        isError = true
                    } else {
                        store.adjustStock(sku, value!!, reason)
                        notice = "Stock adjusted."
                        isError = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}
