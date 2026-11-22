package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.AuditChain
import ph.edu.umak.momompurin.data.CatalogueOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.entity.OptionKind
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoMessages
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import ph.edu.umak.momompurin.ui.theme.MomoDate
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import java.io.File

/**
 * A-09 View Sales and Stock Reports.
 *
 * A-09 c3's rule is the decisive one: Sales is the FULL value of the orders placed
 * in the range, unpaid ones included. A-09 c4's reversed range is refused and the
 * PREVIOUS report stays on screen - not blanked - which is why the accepted range
 * is held separately from the fields being typed.
 */
@Composable
fun ReportsScreen(store: MomoStore, onBack: () -> Unit) {
    val data by store.data.collectAsState()
    var tab by remember { mutableStateOf(ReportTab.SALES) }
    var startText by remember { mutableStateOf(MomoDate.formatDate(store.now - 30L * 86_400_000L)) }
    var endText by remember { mutableStateOf(MomoDate.formatDate(store.now)) }
    var error by remember { mutableStateOf<String?>(null) }

    // The range actually applied. A rejected range leaves this untouched, which is
    // how "keep the previous report" holds (A-09 c5).
    var appliedStart by remember { mutableStateOf(store.now - 30L * 86_400_000L) }
    var appliedEnd by remember { mutableStateOf(store.now) }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Reports", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MomoSpace.Lg)
                .padding(top = MomoSpace.Lg, bottom = MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                StageChip(
                    label = "Sales",
                    selected = tab == ReportTab.SALES,
                    onClick = { tab = ReportTab.SALES },
                )
                StageChip(
                    label = "Stock",
                    selected = tab == ReportTab.STOCK,
                    onClick = { tab = ReportTab.STOCK },
                )
            }

            MomoCard {
                Text("Date range", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))
                MomoField(
                    value = startText,
                    onValueChange = { startText = it; error = null },
                    label = "Start date",
                    labelOutside = true,
                    supporting = "For example 1 Nov 2026",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MomoSpace.Md))
                MomoField(
                    value = endText,
                    onValueChange = { endText = it; error = null },
                    label = "End date",
                    labelOutside = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(MomoSpace.Sm))
                    Text(
                        error!!,
                        style = MomoType.CaptionBold,
                        color = MomoColors.DangerInk,
                    )
                }
                Spacer(Modifier.height(MomoSpace.Md))
                MomoButton(
                    label = "Apply",
                    onClick = {
                        val start = momoParseDate(startText)
                        val end = momoParseDate(endText)
                        if (start == null || end == null) {
                            error = "Enter both dates as day month year, for example 1 Nov 2026."
                            return@MomoButton
                        }
                        val problem = OrderOps.rangeError(start, end)
                        if (problem != null) {
                            // A-09 c4: the message, and the previous report stands.
                            error = problem
                        } else {
                            error = null
                            appliedStart = start
                            appliedEnd = end + 86_400_000L - 1
                        }
                    },
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (tab) {
                ReportTab.SALES -> {
                    // A-09 c1/c3/c6: the four figures, two decimals each.
                    val report = data.salesReport(appliedStart, appliedEnd)
                    MomoCard {
                        Text(
                            "Sales",
                            style = MomoType.TitleSmall,
                            color = MomoColors.TextPrimary,
                        )
                        Spacer(Modifier.height(MomoSpace.Sm))
                        KeyValueRow("Orders placed", report.orderCount.toString())
                        KeyValueRow(
                            "Sales",
                            MomoMoney.format(report.sales),
                            emphasise = true,
                        )
                        KeyValueRow(
                            "Payments received",
                            MomoMoney.format(report.paymentsReceived),
                        )
                        KeyValueRow(
                            "Outstanding balance",
                            MomoMoney.format(report.outstanding),
                            valueColor = MomoColors.DangerInk,
                        )
                        Spacer(Modifier.height(MomoSpace.Xs))
                        Text(
                            "Sales counts the full value of every order placed in the " +
                                "range, including unpaid ones.",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                }

                ReportTab.STOCK -> {
                    MomoCard {
                        Text(
                            "Stock",
                            style = MomoType.TitleSmall,
                            color = MomoColors.TextPrimary,
                        )
                        Spacer(Modifier.height(MomoSpace.Sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Md)) {
                            Text(
                                "Product",
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Text("On hand", style = MomoType.CaptionSmall, color = MomoColors.TextSecondary)
                            Spacer(Modifier.height(0.dp))
                            Text(
                                "  Reserved",
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                            Text(
                                "  Available",
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(MomoSpace.Xs))
                        MomoDivider()
                        data.stockRows().filter { !it.isParent }.forEach { product ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = MomoSpace.Xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    product.sku,
                                    style = MomoType.DataCaption,
                                    color = MomoColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    product.physical.toString(),
                                    style = MomoType.DataCaption,
                                    color = MomoColors.TextPrimary,
                                )
                                Text(
                                    "  ${product.reserved}",
                                    style = MomoType.DataCaption,
                                    color = MomoColors.TextPrimary,
                                )
                                Text(
                                    "  ${product.available}",
                                    style = MomoType.DataCaption,
                                    color = MomoColors.BrandDeep,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}

private enum class ReportTab { SALES, STOCK }

/** Parses "19 Nov 2026" / "1 Nov 2026" into an epoch-day start. */
private fun momoParseDate(text: String): Long? {
    val formats = listOf("d MMM yyyy", "d MMMM yyyy", "yyyy-MM-dd")
    for (pattern in formats) {
        val parsed = runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                isLenient = false
            }.parse(text.trim())
        }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return null
}

/**
 * A-10 Manage Settings - Shop settings, Lists, Edit history, Back up data, Log out,
 * plus the temporary Test controls for Read-Only Offline.
 *
 * ## A.6: removable option chips
 *
 * The Lists card renders Categories and Characters as chips with an `×`. The
 * removal is guarded twice: a confirmation dialog first, and a refusal naming the
 * products still using the value ([MomoStore.removeProductOption] checks) - so the
 * "accidentally added, permanently stuck" defect is gone without ever letting a
 * removal orphan a product row.
 *
 * ## A.5: confirmations as snackbars
 *
 * Every save on this screen reports through [MomoMessages] rather than an inline
 * `notice` line at the bottom of a scroll: the tester's report was that the "added"
 * text sat "at the very bottom" where it was invisible. The snackbar floats above
 * the navigation bar, so it is in view no matter where the list is scrolled.
 */
@Composable
fun SettingsScreen(
    store: MomoStore,
    onBack: () -> Unit,
    onOpenEditHistory: () -> Unit,
    onSignOut: () -> Unit,
) {
    val data by store.data.collectAsState()
    val offline by store.offlineMode.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var markup by remember(data.settings) { mutableStateOf(data.setting("profit_markup") ?: "30") }
    var freebie by remember(data.settings) { mutableStateOf(data.setting("freebie_rate") ?: "3") }
    var newOption by remember { mutableStateOf("") }
    var newCharacter by remember { mutableStateOf("") }
    var showLogout by remember { mutableStateOf(false) }

    // A.6: the option pending removal, keyed by kind. Two states because the
    // categories and characters are separate lists with the same shape.
    var removingCategory by remember { mutableStateOf<String?>(null) }
    var removingCharacter by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Manage Settings", onBack = onBack)

        // A.7's notice is drawn by the HEADER now (Bug 5): the NavHost provides it
        // through LocalHeaderNotice and MomoTopBar above renders it directly under
        // the yellow band. A second copy here would duplicate it on every tab.

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            // ------------------------------------------- A-10 c1-c3: rates -----
            MomoCard {
                Text("Shop settings", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))
                MomoField(
                    value = markup,
                    onValueChange = { markup = it },
                    label = "Markup rate",
                    labelOutside = true,
                    supporting = "Percent, 0 to 100",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MomoSpace.Md))
                MomoField(
                    value = freebie,
                    onValueChange = { freebie = it },
                    label = "Freebie rate",
                    labelOutside = true,
                    supporting = "Percent, 0 to 100",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MomoSpace.Md))
                MomoButton(
                    label = "Save",
                    onClick = {
                        // A-10 c3: out of range is refused and nothing is written.
                        val markupValue = CatalogueOps.parseRate(markup)
                        val freebieValue = CatalogueOps.parseRate(freebie)
                        if (markupValue == null || freebieValue == null) {
                            MomoMessages.error(CatalogueOps.INVALID_RATE)
                        } else {
                            store.setSetting("profit_markup", fmtRate(markupValue))
                            store.setSetting("freebie_rate", fmtRate(freebieValue))
                            MomoMessages.show("Settings saved.")
                        }
                    },
                    // A.7: offline disables the write, visually as well as at the
                    // store's gate.
                    enabled = !offline,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                Text(
                    "Packaging tiers: ${data.setting("pack_size_tiers").orEmpty()}",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            }

            // ------------------------------------------ A-10 c4: the lists -----
            MomoCard {
                Text("Lists", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))

                MomoField(
                    value = newOption,
                    onValueChange = { newOption = it },
                    label = "New category",
                    labelOutside = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                MomoButton(
                    label = "Add category",
                    onClick = {
                        when (
                            val result = CatalogueOps.addOption(
                                data.productOptions, OptionKind.CATEGORY, newOption,
                            )
                        ) {
                            is CatalogueOps.OptionResult.Added -> {
                                store.saveProductOptions(result.options)
                                MomoMessages.show("\"${result.value}\" added.")
                                newOption = ""
                            }
                            is CatalogueOps.OptionResult.Refused -> {
                                MomoMessages.error(result.message)
                            }
                        }
                    },
                    primary = false,
                    enabled = !offline,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(MomoSpace.Md))
                // A.6: the managed category list as removable chips, so what was
                // saved is exactly what is shown - and every chip can come back out.
                OptionChipList(
                    label = "Categories",
                    values = data.productOptions
                        .filter { it.kind == OptionKind.CATEGORY && it.active }
                        .sortedBy { it.sortOrder }
                        .map { it.value },
                    enabled = !offline,
                    onRemove = { removingCategory = it },
                )

                Spacer(Modifier.height(MomoSpace.Md))
                // A-10 c4 names BOTH lists, so a character is its own control.
                MomoField(
                    value = newCharacter,
                    onValueChange = { newCharacter = it },
                    label = "New character",
                    labelOutside = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                MomoButton(
                    label = "Add character",
                    onClick = {
                        when (
                            val result = CatalogueOps.addOption(
                                data.productOptions, OptionKind.CHARACTER, newCharacter,
                            )
                        ) {
                            is CatalogueOps.OptionResult.Added -> {
                                store.saveProductOptions(result.options)
                                MomoMessages.show("\"${result.value}\" added.")
                                newCharacter = ""
                            }
                            is CatalogueOps.OptionResult.Refused -> {
                                MomoMessages.error(result.message)
                            }
                        }
                    },
                    primary = false,
                    enabled = !offline,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(MomoSpace.Sm))
                OptionChipList(
                    label = "Characters",
                    values = data.productOptions
                        .filter { it.kind == OptionKind.CHARACTER && it.active }
                        .sortedBy { it.sortOrder }
                        .map { it.value },
                    enabled = !offline,
                    onRemove = { removingCharacter = it },
                )
            }

            // --------------------------------------- A-10 c5-c7: the tools -----
            MomoCard {
                // One wrapping group so the three tools keep a constant gap and a
                // square left edge. Two hand-made rows left the second ragged and
                // the block read as two unrelated sets of actions.
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
                    verticalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
                ) {
                    MiniAction(label = "Edit history", onClick = onOpenEditHistory)
                    MiniAction(
                        label = "Back up data",
                        onClick = {
                            // A-10 c6: a real file, and its path on screen.
                            val dir = context.getExternalFilesDir(null)
                                ?: context.filesDir
                            val file = dir.let { store.writeBackup(it) }
                            if (file != null) {
                                MomoMessages.show(
                                    "${MomoStore.BACKUP_SAVED} ${file.name}",
                                )
                            } else {
                                MomoMessages.error("Backup failed. The storage was unavailable.")
                            }
                        },
                        emphasised = true,
                        // A.7: a backup is a write.
                        enabled = !offline,
                    )
                    MiniAction(
                        label = "Log out",
                        destructive = true,
                        onClick = { showLogout = true },
                    )
                }
            }

            // The temporary Test controls, for Read-Only Offline testing.
            MomoCard {
                Text(
                    "Test controls",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Text(
                    "Temporary. Used to walk the read-only offline criteria.",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Offline mode",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = offline,
                        onCheckedChange = { store.setOfflineMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MomoColors.BrandDarkest,
                            checkedTrackColor = MomoColors.BrandPrimary,
                            uncheckedThumbColor = MomoColors.SurfaceCard,
                            uncheckedTrackColor = MomoColors.SurfaceBorder,
                            uncheckedBorderColor = MomoColors.TextSecondary,
                        ),
                    )
                }
                if (offline) {
                    Text(
                        "Every write action is blocked; loaded screens stay readable.",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

    if (showLogout) {
        MomoConfirmDialog(
            title = "Log out",
            message = "You will be returned to the Sign In screen.",
            confirmLabel = "Log out",
            dismissLabel = "Cancel",
            destructive = true,
            onConfirm = {
                showLogout = false
                onSignOut()
            },
            onDismiss = { showLogout = false },
        )
    }

    // -------------------------------------------------- A.6 removal dialogs --

    // The confirmation doubles as the in-use warning: the store's refusal names the
    // products if the owner confirms past this point, so both layers of the guard
    // state themselves to the owner rather than one hiding behind the other.
    removingCategory?.let { value ->
        MomoConfirmDialog(
            title = "Remove \"$value\"",
            message = "Remove this category from the managed list? A product still " +
                "using it will block the removal.",
            confirmLabel = "Remove",
            dismissLabel = "Keep",
            destructive = true,
            onConfirm = {
                removingCategory = null
                val failure = store.removeProductOption(value, OptionKind.CATEGORY)
                if (failure == null) {
                    MomoMessages.show("\"$value\" removed.")
                } else {
                    MomoMessages.error(failure)
                }
            },
            onDismiss = { removingCategory = null },
        )
    }
    removingCharacter?.let { value ->
        MomoConfirmDialog(
            title = "Remove \"$value\"",
            message = "Remove this character from the managed list? A product still " +
                "using it will block the removal.",
            confirmLabel = "Remove",
            dismissLabel = "Keep",
            destructive = true,
            onConfirm = {
                removingCharacter = null
                val failure = store.removeProductOption(value, OptionKind.CHARACTER)
                if (failure == null) {
                    MomoMessages.show("\"$value\" removed.")
                } else {
                    MomoMessages.error(failure)
                }
            },
            onDismiss = { removingCharacter = null },
        )
    }
}

/**
 * A.6: one removable-chip row.
 *
 * A chip is a value plus an `×`, and the `×` is a real control (48dp target) rather
 * than a glyph the owner has to hit precisely. Disabled while offline, because a
 * removal is a write.
 */
@Composable
private fun OptionChipList(label: String, values: List<String>, enabled: Boolean, onRemove: (String) -> Unit) {
    Text(
        label,
        style = MomoType.CaptionSmall,
        color = MomoColors.TextSecondary,
    )
    Spacer(Modifier.height(MomoSpace.Xs))
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
        verticalArrangement = Arrangement.spacedBy(MomoSpace.Xs),
    ) {
        values.forEach { value ->
            Row(
                Modifier
                    .heightIn(min = 32.dp)
                    .background(MomoColors.SurfaceMutedVariant, RoundedCornerShape(MomoRadius.Pill))
                    .border(
                        1.dp,
                        MomoColors.BorderSubtle,
                        RoundedCornerShape(MomoRadius.Pill),
                    )
                    .padding(start = MomoSpace.Md, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value,
                    style = MomoType.LabelChip,
                    color = MomoColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                Box(
                    Modifier
                        .size(24.dp)
                        .clickable(enabled = enabled) { onRemove(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "\u00D7",
                        style = MomoType.CaptionBold,
                        color = if (enabled) MomoColors.DangerInk else MomoColors.TextDisabled,
                    )
                }
            }
        }
    }
    if (values.isEmpty()) {
        Text(
            "Nothing in this list yet.",
            style = MomoType.CaptionSmall,
            color = MomoColors.TextDisabled,
        )
    }
}

private fun fmtRate(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * A-10 c5: the edit history.
 *
 * Newest first, with the date, the time and the account of each change - the three
 * fields A-10 c5 names.
 *
 * IAS R.5: the SHA-256 verification badge sits at the TOP, before the entries, so
 * the chain's state is the first thing read rather than something discovered by
 * scrolling to the end of a broken history.
 */
@Composable
fun EditHistoryScreen(store: MomoStore, onBack: () -> Unit) {
    val data by store.data.collectAsState()
    val entries = data.activityLog.sortedByDescending { it.loggedAt }
    val verification = remember(data.activityLog) { store.verifyAuditChain() }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Edit history", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MomoSpace.Lg)
                .padding(top = MomoSpace.Lg, bottom = MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            // IAS R.5: the chain badge. Green when the chain verifies, rose when a
            // row has been edited, deleted or re-ordered - naming the first break.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (verification.valid) MomoColors.SuccessSurface else MomoColors.DangerSurface,
                        RoundedCornerShape(MomoRadius.Input),
                    )
                    .border(
                        1.dp,
                        if (verification.valid) MomoColors.SuccessText else MomoColors.DangerText,
                        RoundedCornerShape(MomoRadius.Input),
                    )
                    .padding(MomoSpace.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
            ) {
                Text(
                    if (verification.valid) "\u2713" else "\u26A0",
                    style = MomoType.CaptionBold,
                    color = if (verification.valid) MomoColors.SuccessText else MomoColors.DangerText,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        AuditChain.badgeText(verification),
                        style = MomoType.CaptionBold,
                        color = if (verification.valid) MomoColors.SuccessText else MomoColors.DangerText,
                    )
                    if (!verification.valid && verification.reason != null) {
                        Text(
                            verification.reason!!,
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                Text(
                    "No changes recorded yet.",
                    style = MomoType.BodyDefault,
                    color = MomoColors.TextSecondary,
                )
            }
            entries.forEach { entry ->
                MomoCard {
                    Text(
                        entry.action,
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextPrimary,
                    )
                    Text(
                        entry.actor,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                    Text(
                        MomoDate.formatDateTime(entry.loggedAt),
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                    if (!entry.detail.isNullOrBlank()) {
                        Text(
                            entry.detail!!,
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}
