package ph.edu.umak.momompurin.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import ph.edu.umak.momompurin.R
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the customer-visible invoice to a real image or PDF file (A-05 c7).
 *
 * Two contracts this file keeps:
 *
 *  - **the customer-visible content only.** The invoice draws the shop, the
 *    invoice and order numbers, the customer, the item lines, the subtotal, the
 *    shipping fee, the total, the amount paid and the balance. Cost, unit cost and
 *    markup are never drawn - A-13 c6 keeps them off every customer surface.
 *  - **a real file a viewer can open.** PNG via [Bitmap.compress] and PDF via
 *    [PdfDocument], both drawn on an Android [Canvas] with the app's own typefaces
 *    and palette.
 *
 * Colours come from [MomoColors] converted with [toArgb], so this file carries no
 * literal colour value and the "no hex outside the theme" rule holds for Canvas
 * code too.
 */
object InvoiceRenderer {

    /** The logical width the layout is authored against (the 412dp frame). */
    private const val LOGICAL_WIDTH = 412f

    /** A4 at 72 dpi. */
    private const val PDF_WIDTH = 595
    private const val PDF_HEIGHT = 842

    /** A crisp phone-width image. */
    private const val PNG_WIDTH = 1080

    /** One drawn row, as data so the content contract is testable on a plain JVM. */
    data class Line(val label: String, val amount: String?, val bold: Boolean = false)

    /**
     * The exact rows an invoice renders, in order.
     *
     * A string that is not in this list cannot reach a shared file, which is how
     * "no internal cost reaches the customer" stays a property of the data rather
     * than of a rendering filter someone could drop.
     */
    fun contentLines(data: MomoData, order: Order): List<Line> {
        val rows = mutableListOf<Line>()
        rows += Line(data.setting("business_name") ?: "Momompurin", null, bold = true)
        rows += Line("Invoice ${order.invoiceNo.ifBlank { "\u2014" }}", null)
        rows += Line("Order ${order.orderId}", null)
        rows += Line(order.recipientName, null)
        rows += Line(order.addressBlock, null)
        rows += Line("", null)
        data.linesFor(order.orderId).forEach { line ->
            val suffix = line.variantName.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            rows += Line(
                "${line.productName}$suffix \u00D7 ${line.qty}",
                MomoMoney.format(line.lineTotal),
            )
        }
        rows += Line("", null)
        rows += Line("Item subtotal", MomoMoney.format(order.itemSubtotal))
        rows += Line("Shipping fee", MomoMoney.format(order.shippingFee))
        rows += Line("Total", MomoMoney.format(order.total), bold = true)
        rows += Line("Paid", MomoMoney.format(order.amountPaid))
        rows += Line("Balance", MomoMoney.format(order.balance), bold = true)
        return rows
    }

    /**
     * IAS R.1: the RA 10173 privacy statement, as four fixed lines.
     *
     * One function so the on-screen view, the exported PDF and the shared image
     * carry the SAME statement - a footer that differs per surface would let a
     * shared screenshot omit the notice the PDF carries. The lines are constants,
     * not interpolated, because a privacy notice is legal text: wording drifts are
     * exactly what a "version" line is meant to prevent.
     */
    fun privacyFooter(): List<String> = listOf(
        "This invoice was issued by Momompurin for the order shown above.",
        "Personal data on it is processed to fulfil that order.",
        "Questions or a deletion request: message the Momompurin Facebook page.",
        "Policy version v1.0.0 \u2014 RA 10173 Data Privacy Act of 2012",
    )

    /**
     * Where an export landed, so the caller can open it.
     *
     * [uriString] is a `content://` URI when the file went through MediaStore or
     * FileProvider, and null when it could only be written to app-private storage
     * (the legacy path). A null URI cannot be opened by another app, which is
     * exactly the "Access is denied" the old export produced - so the caller treats
     * null as a failure to open, not as a failure to save.
     */
    data class ExportResult(
        val fileName: String,
        val uriString: String?,
        val message: String,
        val ok: Boolean,
    )

    /**
     * A.4: write `MP-####.pdf` where the USER can reach it.
     *
     * The old export wrote to `/Android/data/<pkg>/files/invoices/`, a directory
     * only the app can read. Locating it in a file manager gave "Access is denied",
     * and the path was five levels deep - both reported by the tester. On API 29+
     * the file now goes to **MediaStore Downloads**, under `Downloads/Momompurin/`,
     * which every file manager and PDF viewer can open with no permission prompt
     * (MediaStore.Downloads is app-attributed, so WRITE_EXTERNAL_STORAGE is not
     * needed). Below API 29 the app-private file still works, but it is handed out
     * through FileProvider as a `content://` URI so the Open action can launch a
     * viewer without any filesystem grant.
     */
    fun exportPdf(context: Context, data: MomoData, order: Order, dir: File? = null): ExportResult {
        val name = "${order.invoiceNo.ifBlank { order.orderId }}.pdf"

        val rows = contentLines(data, order) + privacyFooter().map { Line(it, null) }
        val scale = PDF_WIDTH / LOGICAL_WIDTH
        val title = paint(context, 18f * scale, bold = true, color = MomoColors.BrandDeep)
        val body = paint(context, 11f * scale, bold = false, color = MomoColors.TextPrimary)
        val boldBody = paint(context, 11f * scale, bold = true, color = MomoColors.TextPrimary)
        val footerPaint = paint(context, 9f * scale, bold = false, color = MomoColors.InkMuted)

        val leftMargin = 40f * scale
        val rightMargin = 40f * scale
        // Bug 1: the ONE width every string is laid out against. A single value
        // shared by the label and the amount column is what makes "nothing crosses
        // the right edge" checkable rather than a hope.
        val contentWidth = (PDF_WIDTH - leftMargin - rightMargin).toInt()
        // An amount needs its own column, so a row with money wraps in what is left.
        val amountColumn = (contentWidth * 0.32f).toInt()
        val labelWidthWithAmount = contentWidth - amountColumn

        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, 1).create())
        val canvas = page.canvas
        var y = 60f * scale
        rows.forEachIndexed { index, row ->
            val p = if (row.bold) boldBody else body
            val drawPaint = if (index == 0) title else p
            val textWidth = if (row.amount != null) labelWidthWithAmount else contentWidth
            // A row with no amount is a sentence and may run the full content width;
            // a row with money keeps room for its figure on the right.
            val used = drawWrapped(canvas, row.label, leftMargin, y, drawPaint, textWidth)
            row.amount?.let {
                canvas.drawText(it, PDF_WIDTH - rightMargin - p.measureText(it), y, p)
            }
            // Advance by the wrapped height, never a fixed 22dp: a two-line address
            // used to draw its next row straight through its own second line.
            y += used.coerceAtLeast((22f * scale).toInt()).toFloat()
        }
        // A-15 c8: how payment is arranged, stated on the invoice itself.
        y += 16f * scale
        y += drawWrapped(
            canvas,
            "Payment by GCash, Maya or Bank Transfer, arranged with the owner.",
            leftMargin,
            y,
            body,
            contentWidth,
        ).toFloat()
        // IAS R.1: the privacy statement, as the last four lines of the document.
        y += 28f * scale
        privacyFooter().forEach { line ->
            val used = drawWrapped(canvas, line, leftMargin, y, footerPaint, contentWidth)
            y += used.coerceAtLeast((14f * scale).toInt()).toFloat()
        }
        document.finishPage(page)

        if (y > PDF_HEIGHT) {
            document.close()
            return ExportResult(
                fileName = name,
                uriString = null,
                message = "The invoice did not fit on one page.",
                ok = false,
            )
        }

        val write = runCatching {
            saveToPublicDownloads(context, name, "application/pdf") { out ->
                document.writeTo(out)
            }
        }.getOrElse { failure ->
            runCatching { document.close() }
            return ExportResult(
                fileName = name,
                uriString = null,
                message = "Could not save the PDF: ${failure.message ?: "storage unavailable"}",
                ok = false,
            )
        }
        document.close()
        return ExportResult(
            fileName = name,
            uriString = write.second,
            message = write.third ?: "PDF saved to $name",
            ok = write.first,
        )
    }

    /**
     * Bug 1 (real-phone report): the invoice text ran past the picture's edge.
     *
     * A single `Canvas.drawText` never wraps - it draws the whole string at one
     * baseline, straight through the right margin and off the page. Every long
     * sentence on this document is long enough to do that: the delivery address, the
     * payment sentence, and all four RA 10173 privacy lines. The fix is to lay each
     * one out with [StaticLayout] at the exact available width, so the text engine
     * breaks it at word boundaries and the caller advances by however many lines the
     * layout actually produced.
     *
     * [StaticLayout] is Android's own line-breaking engine (the one `TextView` uses),
     * so the result is real typography rather than a hand-rolled character count.
     *
     * @return the height the text occupied, so the caller can advance its cursor.
     */
    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        width: Int,
    ): Int {
        if (text.isEmpty() || width <= 0) return 0
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, TextPaint(paint), width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.15f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    /** How tall [text] would be when wrapped at [width], without drawing it. */
    private fun wrappedHeight(text: String, paint: Paint, width: Int): Int {
        if (text.isEmpty() || width <= 0) return 0
        return StaticLayout.Builder
            .obtain(text, 0, text.length, TextPaint(paint), width)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.15f)
            .build()
            .height
    }

    /** Writes `MP-####.png` at 1080 px across, with the privacy footer, and lands it in Downloads. */
    fun exportImage(context: Context, data: MomoData, order: Order, dir: File? = null): ExportResult {
        val name = "${order.invoiceNo.ifBlank { order.orderId }}.png"

        val rows = contentLines(data, order)
        val footer = privacyFooter()
        val scale = PNG_WIDTH / LOGICAL_WIDTH
        val rowHeight = 46f * scale
        val footerHeight = 24f * scale

        val title = paint(context, 20f * scale, bold = true, color = MomoColors.BrandDeep)
        val body = paint(context, 12f * scale, bold = false, color = MomoColors.TextPrimary)
        val boldBody = paint(context, 12f * scale, bold = true, color = MomoColors.TextPrimary)
        val footerPaint = paint(context, 10f * scale, bold = false, color = MomoColors.InkMuted)

        val leftMargin = 40f * scale
        val rightMargin = 40f * scale
        // Bug 1: every string is laid out against this one width, so nothing can
        // cross the right margin. An amount keeps its own column, so a row that
        // carries money wraps in what is left of the content width.
        val contentWidth = (PNG_WIDTH - leftMargin - rightMargin).toInt()
        val amountColumn = (contentWidth * 0.32f).toInt()
        val labelWidthWithAmount = contentWidth - amountColumn

        // Bug 1 (the half that made the overflow visible): the bitmap height is now
        // MEASURED from the wrapped layouts before the bitmap exists, instead of
        // assumed from a per-row constant. A three-line address or a footer that
        // wraps to eight lines grows the image rather than being clipped off its
        // bottom edge.
        val headerHeight = 70f * scale
        val rowHeights = rows.mapIndexed { index, row ->
            val p = if (index == 0) title else if (row.bold) boldBody else body
            val w = if (row.amount != null) labelWidthWithAmount else contentWidth
            wrappedHeight(row.label, p, w).coerceAtLeast(rowHeight.toInt())
        }
        val paymentSentence = "Payment by GCash, Maya or Bank Transfer, arranged with the owner."
        val paymentHeight = wrappedHeight(paymentSentence, body, contentWidth)
        val footerHeights = footer.map { wrappedHeight(it, footerPaint, contentWidth) }
        val separatorBlock = (10f * scale + 24f * scale).toInt()
        val height = (
            headerHeight + rowHeights.sum() + paymentHeight + separatorBlock +
                footerHeights.sum() + 40f * scale
            ).toInt().coerceAtLeast(400)

        val bitmap = Bitmap.createBitmap(PNG_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(MomoColors.SurfaceCanvas.toArgb())

        var y = 70f * scale
        rows.forEachIndexed { index, row ->
            val p = if (row.bold) boldBody else body
            val drawPaint = if (index == 0) title else p
            val textWidth = if (row.amount != null) labelWidthWithAmount else contentWidth
            drawWrapped(canvas, row.label, leftMargin, y, drawPaint, textWidth)
            row.amount?.let {
                canvas.drawText(it, PNG_WIDTH - rightMargin - p.measureText(it), y, p)
            }
            // The advance comes from the height MEASURED for this row above, so the
            // draw pass and the sizing pass can never disagree.
            y += rowHeights[index].toFloat()
        }
        // A-15 c8: how payment is arranged.
        y += 8f * scale
        y += drawWrapped(canvas, paymentSentence, leftMargin, y, body, contentWidth).toFloat()

        // IAS R.1: the privacy statement under a hairline separator.
        val separator = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MomoColors.BorderSubtle.toArgb()
            strokeWidth = 1f * scale
        }
        y += 10f * scale
        canvas.drawLine(leftMargin, y, PNG_WIDTH - rightMargin, y, separator)
        y += 24f * scale
        footer.forEachIndexed { index, line ->
            drawWrapped(canvas, line, leftMargin, y, footerPaint, contentWidth)
            y += footerHeights[index].toFloat()
        }

        val write = runCatching {
            saveToPublicDownloads(context, name, "image/png") { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.getOrElse { failure ->
            bitmap.recycle()
            return ExportResult(
                fileName = name,
                uriString = null,
                message = "Could not save the image: ${failure.message ?: "storage unavailable"}",
                ok = false,
            )
        }
        bitmap.recycle()
        return ExportResult(
            fileName = name,
            uriString = write.second,
            message = write.third ?: "Image saved to $name",
            ok = write.first,
        )
    }

    /**
     * A.4: write bytes to a public location and return (ok, uri, message).
     *
     * API 29+ goes through `MediaStore.Downloads` with `RELATIVE_PATH` set to
     * `Download/Momompurin`, which is visible in every file manager as
     * `Downloads/Momompurin/<name>` and needs NO storage permission because the
     * row is owned by this app. API 26-28 has no MediaStore Downloads, so the file
     * lands in app-private storage and is exposed through FileProvider - the uri
     * still opens in a viewer, which is what the "Open" action needs.
     *
     * On a failure to obtain the MediaStore row (a full volume, a restricted
     * profile) the API-29 path falls back to app-private + FileProvider too, so an
     * export that could not publish still produces an openable file rather than an
     * error.
     */
    private fun saveToPublicDownloads(
        context: Context,
        name: String,
        mime: String,
        write: (java.io.OutputStream) -> Unit,
    ): Triple<Boolean, String?, String?> {
        // Bug 4: a private copy is ALWAYS written first, so the Open action has a
        // FileProvider fallback to try when a viewer refuses the MediaStore URI.
        // Cheap (the document is a few KB) and it makes the fallback real rather
        // than theoretical.
        val privateCopy = runCatching {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "invoices")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use(write)
            file
        }.getOrNull()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS + "/Momompurin",
                )
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values,
            )
            if (uri != null) {
                // `write` already consumed the private copy; a document is small
                // enough that re-reading it is simpler than buffering every export
                // in memory, so the bytes are handed over from that file.
                val copied = privateCopy != null && runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        privateCopy.inputStream().use { it.copyTo(out) }
                    }
                }.isSuccess
                if (!copied) {
                    // The MediaStore row exists but the copy failed: drop the empty
                    // row so a broken 0-byte download is not left behind.
                    runCatching { resolver.delete(uri, null, null) }
                } else {
                    return Triple(true, uri.toString(), "Saved to Downloads/Momompurin/$name")
                }
            }
            // insert() returned null or the copy failed: fall through to private.
        }

        // Legacy or fallback: app-private file, exposed through FileProvider.
        val file = privateCopy ?: return Triple(
            false,
            null,
            "Could not save the file: storage unavailable",
        )
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull()
        return Triple(
            true,
            uri?.toString(),
            if (uri != null) {
                "Saved to Downloads/Momompurin/$name"
            } else {
                "Saved to app storage: ${file.absolutePath}"
            },
        )
    }

    private fun paint(
        context: Context,
        size: Float,
        bold: Boolean,
        color: androidx.compose.ui.graphics.Color,
    ): Paint {
        val font = if (bold) R.font.comfortaa_bold else R.font.comfortaa_regular
        val face = ResourcesCompat.getFont(context, font) ?: Typeface.DEFAULT
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = face
            this.color = color.toArgb()
        }
    }
}
