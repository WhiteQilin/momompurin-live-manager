package ph.edu.umak.momompurin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The app is light-only: the Figma token collection has one mode and the design
 * provides no dark palette. No dynamic colour either - the brand palette is the
 * design, not a device preference.
 */
private val MomoColorScheme = lightColorScheme(
    primary = MomoColors.ButtonPrimaryFill,
    onPrimary = MomoColors.TextPrimary,
    primaryContainer = MomoColors.BrandPrimary,
    onPrimaryContainer = MomoColors.TextPrimary,
    secondary = MomoColors.BrandSecondary1Shade1,
    onSecondary = MomoColors.InkInverse,
    background = MomoColors.SurfaceCanvas,
    onBackground = MomoColors.TextPrimary,
    surface = MomoColors.SurfaceCard,
    onSurface = MomoColors.TextPrimary,
    surfaceVariant = MomoColors.SurfaceMutedVariant,
    onSurfaceVariant = MomoColors.TextSecondary,
    outline = MomoColors.BorderSubtle,
    outlineVariant = MomoColors.SurfaceBorder,
    error = MomoColors.DangerInk,
    onError = MomoColors.InkInverse,
    errorContainer = MomoColors.DangerSurface,
    onErrorContainer = MomoColors.DangerText,
)

@Composable
fun MomompurinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MomoColorScheme,
        typography = MomoTypography,
        content = content,
    )
}
