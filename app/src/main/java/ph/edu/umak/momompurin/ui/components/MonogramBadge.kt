package ph.edu.umak.momompurin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * The standard product thumbnail: a dark-brown rounded square carrying the SKU in
 * white M PLUS 1 Code.
 *
 * Ruled in over photographic thumbnails (14.11.2026) because the Hi-Fi file has no
 * per-product imagery - only icons and the mascot. A monogram is honest: it is the
 * SKU the owner actually uses, it renders at any size, it needs no network, and it
 * cannot fall out of date the way a photo does when a product is renamed or
 * repackaged. The real image filename is still recorded on the product
 * ([ph.edu.umak.momompurin.data.entity.Product.imageRef]) for F-01 c4 / H-02, so a
 * media browser can show it where one exists.
 *
 * Sizing follows the SKU's length: a 2-character code can render larger than a
 * 5-character one without shrinking to illegibility. That is why the font size is
 * derived rather than fixed.
 */
@Composable
fun MonogramBadge(
    sku: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val label = sku.trim().uppercase()
    // 48dp -> ~17sp for 3 chars; shrink as the code lengthens.
    val fontSize = when {
        label.length <= 2 -> (size.value * 0.42f).sp
        label.length == 3 -> (size.value * 0.34f).sp
        label.length == 4 -> (size.value * 0.27f).sp
        else -> (size.value * 0.22f).sp
    }
    Box(
        modifier
            .size(size)
            .background(MomoColors.BrandDeep, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            // The SKU is monospaced so a tester can read it character by character,
            // the same reason order numbers and tracking codes use this family.
            style = MomoType.DataMonospace.copy(fontSize = fontSize, lineHeight = fontSize * 1.1f),
            color = MomoColors.InkInverse,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
