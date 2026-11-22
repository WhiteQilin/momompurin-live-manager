package ph.edu.umak.momompurin.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.components.MomoBadges
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoMessages
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.components.StatusBadge
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import ph.edu.umak.momompurin.ui.theme.MomoDate
import java.io.File

/**
 * The invoice view, shared by A-05 (owner) and A-16 (customer).
 *
 * A-16 c5 requires the customer's copy to be READ-ONLY with no edit or delete
 * control, and this screen has neither on any path: it renders text and the two
 * export actions only. A-05 c7's Export PDF and Share image live here so both roles
 * open the same document.
 *
 * ## The export + Open pair (A.4)
 *
 * [InvoiceRenderer.exportPdf] now writes into `Downloads/Momompurin/` through
 * MediaStore, and hands back a `content://` URI. This screen shows the floating
 * snackbar the criterion asks for - "Saved to Downloads/Momompurin/MP-####.pdf" -
 * with an **Open** action that fires `ACTION_VIEW` on that URI with
 * `FLAG_GRANT_READ_URI_PERMISSION`. The grant is what kills the old "Access is
 * denied": the receiving viewer gets read access to exactly one file, for exactly
 * one session, with no filesystem path involved.
 */
@Composable
fun InvoiceViewScreen(
    store: MomoStore,
    orderId: String,
    onBack: () -> Unit,
) {
    val data by store.data.collectAsState()
    val order = data.order(orderId)
    val context = LocalContext.current

    if (order == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = "Invoice", onBack = onBack)
            Text("That invoice no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    val shopName = data.setting("business_name") ?: "Momompurin"
    val invoiceNo = order.invoiceNo.ifBlank { "Not yet issued" }

    /**
     * The app-private copy of an export, exposed through FileProvider.
     *
     * This is the fallback for a viewer that cannot read the MediaStore row: the
     * export always writes this copy now, and it is cheap to hand out the same way
     * here. Null when the file is absent, which simply means there is nothing to
     * fall back to.
     */
    fun privateCopyUri(context: android.content.Context, fileName: String): android.net.Uri? {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "invoices")
        val file = File(dir, fileName)
        if (!file.exists()) return null
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull()
    }

    /**
     * Opens a saved export, reporting rather than crashing when nothing can.
     *
     * Bug 4 (real-phone report): tapping **Open** did nothing on a real Android
     * 14/15 phone. Three separate causes, all fixed here:
     *
     *  1. the snackbar's action was a label with no click handler, so the tap never
     *     reached this function at all (fixed in `MomoSnackbarHost`);
     *  2. without a `<queries>` entry Android 11+ hides PDF viewers from the
     *     intent, so it resolved to nothing (fixed in the manifest);
     *  3. a `content://` URI from MediaStore can be refused by a viewer that
     *     re-opens it in another process, so the private FileProvider copy is tried
     *     when the public URI fails.
     *
     * A chooser is used rather than a bare ACTION_VIEW so a phone with several
     * viewers lets the user pick, and `ActivityNotFoundException` is the ONLY case
     * that shows the "install a PDF viewer" message - a real failure to open must
     * not be disguised as a missing app.
     */
    fun openExport(uriString: String?, fileName: String, mime: String) {
        val primary = uriString?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
        // The MediaStore row first; the app-private FileProvider copy second.
        val candidates = listOfNotNull(
            primary,
            privateCopyUri(context, fileName),
        ).distinct()

        if (candidates.isEmpty()) {
            MomoMessages.error("The file was saved, but it could not be opened.")
            return
        }

        var lastFailure: Throwable? = null
        for (uri in candidates) {
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // resolveActivity is null when no viewer is installed OR when package
            // visibility hides it; the manifest <queries> block covers the latter.
            val resolvable = runCatching { context.packageManager.resolveActivity(view, 0) }.getOrNull()
            if (resolvable == null) {
                lastFailure = android.content.ActivityNotFoundException()
                continue
            }
            val launched = runCatching {
                context.startActivity(Intent.createChooser(view, "Open invoice PDF"))
            }
            if (launched.isSuccess) return
            lastFailure = launched.exceptionOrNull()
        }

        if (lastFailure is android.content.ActivityNotFoundException) {
            MomoMessages.error(
                "Saved to Downloads/Momompurin/ \u2014 install a PDF viewer to open.",
            )
        } else {
            MomoMessages.error("The file was saved, but it could not be opened.")
        }
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = invoiceNo, onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = MomoSpace.Lg,
                    end = MomoSpace.Lg,
                    top = MomoSpace.Lg,
                    // Clears the bottom navigation bar.
                    bottom = MomoSpace.BottomBarClearance,
                ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                Text(shopName, style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Text(
                    "Invoice $invoiceNo",
                    style = MomoType.DataMonospace,
                    color = MomoColors.TextPrimary,
                )
                Text(
                    "Order ${order.orderId}",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                KeyValueRow("Customer", order.recipientName)
                KeyValueRow("Mobile", order.mobile)
                KeyValueRow("Delivery address", order.addressBlock)
                KeyValueRow("Issued", order.invoicedAt?.let { MomoDate.formatDateTime(it) } ?: "\u2014")
            }

            MomoCard {
                Text("Items", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))
                data.linesFor(orderId).forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = MomoSpace.Xs)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.productName,
                                style = MomoType.BodyDefault,
                                color = MomoColors.TextPrimary,
                            )
                            Text(
                                listOfNotNull(
                                    line.variantName.takeIf { it.isNotBlank() },
                                    "\u00D7 ${line.qty}",
                                    MomoMoney.format(line.unitPrice),
                                ).joinToString(" \u00B7 "),
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                        Text(
                            MomoMoney.format(line.lineTotal),
                            style = MomoType.DataCaption,
                            color = MomoColors.TextPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(MomoSpace.Sm))
                MomoDivider()
                Spacer(Modifier.height(MomoSpace.Sm))
                KeyValueRow("Item subtotal", MomoMoney.format(order.itemSubtotal))
                KeyValueRow("Shipping fee", MomoMoney.format(order.shippingFee))
                KeyValueRow("Total", MomoMoney.format(order.total), emphasise = true)
                KeyValueRow("Paid", MomoMoney.format(order.amountPaid))
                KeyValueRow(
                    "Balance",
                    MomoMoney.format(order.balance),
                    emphasise = true,
                    valueColor = if (order.balance > 0) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            MomoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Payment",
                        style = MomoType.TitleSmall,
                        color = MomoColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(MomoBadges.forPayment(order.paymentStatus))
                }
            }

            // IAS R.1: the privacy statement, on screen exactly as it is rendered
            // into the exported PDF and the shared image. One source, three surfaces.
            MomoCard {
                InvoiceRenderer.privacyFooter().forEachIndexed { index, line ->
                    Text(
                        line,
                        style = if (index == InvoiceRenderer.privacyFooter().lastIndex) {
                            MomoType.CaptionBold
                        } else {
                            MomoType.CaptionSmall
                        },
                        color = MomoColors.TextSecondary,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(MomoSpace.Lg),
            horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A-05 c7: real file exports, drawn from the same order record.
            MiniAction(
                label = "Export PDF",
                emphasised = true,
                onClick = {
                    val result = InvoiceRenderer.exportPdf(context, data, order)
                    if (!result.ok) {
                        MomoMessages.error(result.message)
                    } else {
                        // A.4: the exact sentence the criterion quotes, with an
                        // immediate Open action over the granted content URI.
                        MomoMessages.show(
                            result.message,
                            "Open",
                        ) { openExport(result.uriString, result.fileName, "application/pdf") }
                    }
                },
            )
            MiniAction(
                label = "Share image",
                onClick = {
                    val result = InvoiceRenderer.exportImage(context, data, order)
                    if (!result.ok) {
                        MomoMessages.error(result.message)
                        return@MiniAction
                    }
                    val uri = result.uriString?.let {
                        runCatching { android.net.Uri.parse(it) }.getOrNull()
                    }
                    if (uri == null) {
                        MomoMessages.error(result.message)
                        return@MiniAction
                    }
                    runCatching {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "Invoice ${order.invoiceNo.ifBlank { order.orderId }}",
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share invoice"))
                    }.onFailure {
                        // A device with no share target must report, not crash.
                        MomoMessages.error("The image was saved, but no app could share it.")
                    }
                },
            )
        }
    }
}
