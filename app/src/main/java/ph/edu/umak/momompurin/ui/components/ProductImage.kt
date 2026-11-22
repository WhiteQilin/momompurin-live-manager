package ph.edu.umak.momompurin.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import java.io.File
import ph.edu.umak.momompurin.R
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius

/** A seeded product photo or a locally selected photo, with a consistent fallback. */
@Composable
fun ProductImage(
    photoRef: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MomoRadius.Input,
) {
    val context = LocalContext.current
    val localBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photoRef) {
        value = withContext(Dispatchers.IO) {
            photoRef
                ?.takeIf { it.isNotBlank() && File(it).isFile }
                ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        }
    }
    val resourceId = productPhotoResource(photoRef)
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier
            .clip(shape)
            .background(MomoColors.SurfaceCard)
            .border(1.dp, MomoColors.BorderSubtle, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            localBitmap != null -> Image(
                bitmap = localBitmap!!,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
            resourceId != null -> Image(
                painter = painterResource(resourceId),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
            else -> Icon(
                painter = painterResource(R.drawable.ic_shopping_bag),
                contentDescription = contentDescription,
                tint = MomoColors.BrandDeep,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private fun productPhotoResource(photoRef: String?): Int? = when (photoRef?.lowercase()) {
    "chw" -> R.drawable.prod_chw
    "w1" -> R.drawable.prod_w1
    "hkeye" -> R.drawable.prod_hkeye
    // D.2: each colourway carries its OWN photo. Lavender used to fall through to
    // the pink asset, so selecting the variant swapped nothing visible - the one
    // thing a variant picker must do.
    "mpbow" -> R.drawable.prod_mpbow
    "mpbow_pk" -> R.drawable.prod_mpbow
    "mpbow_lv" -> R.drawable.prod_mpbow_lv
    "mckp" -> R.drawable.prod_mckp
    "mmbbs" -> R.drawable.prod_mmbbs
    "p5t" -> R.drawable.prod_p5t
    "scwh" -> R.drawable.prod_scwh
    else -> null
}
