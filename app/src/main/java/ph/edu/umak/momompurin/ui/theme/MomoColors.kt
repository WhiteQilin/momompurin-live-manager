package ph.edu.umak.momompurin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Every colour in the application, resolved from the Figma variable collection
 * `Momompurin · Tokens` (VariableCollectionId:2343:1291, 69 variables, one mode).
 *
 * No composable may declare a literal colour. A screen that needs a colour names
 * a token here; if the token does not exist, the value is added to this file so
 * the palette stays closed and reviewable. See ANDROID_PROTOTYPE_HANDOFF.md s.3.1
 * for the source table these constants were transcribed from.
 */
object MomoColors {

    // ---------------------------------------------------------------- brand ----
    val BrandPrimary = Color(0xFFFED161)
    val BrandDeep = Color(0xFF6F2511)
    val BrandDarkest = Color(0xFF461204)
    val BrandSecondary1Shade1 = Color(0xFFFF9900)
    val BrandSecondary1Shade2 = Color(0xFFFFB600)
    val BrandSecondary2Shade1 = Color(0xFF6F2511)
    val BrandSecondary2Shade2 = Color(0xFF461204)

    // ------------------------------------------------------------------ ink ----
    val InkStrong = Color(0xFF2D2321)
    val InkMuted = Color(0xFF6E5D5A)
    val InkInverse = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF2B140E)
    /**
     * Secondary body ink.
     *
     * Was #6E5D57, a warm taupe that measures 4.42:1 on the cream canvas - under
     * the 4.5:1 AA floor, so every metadata line, SKU caption and helper sentence
     * in the app was marginally unreadable for a low-vision reader. #5E4633 keeps
     * the same warm brown hue one shade deeper and measures 6.26:1 on cream and
     * 7.14:1 on white, clearing AA on both surfaces it is drawn over.
     */
    val TextSecondary = Color(0xFF5E4633)
    val TextDisabled = Color(0xFFA89E9B)
    /**
     * Link and "tap me" ink.
     *
     * Was #B85D00, which measures 4.16:1 on the cream canvas - just under the
     * 4.5:1 AA floor, so the one colour reserved for *interactive text* was the
     * one colour a low-vision reader could not reliably read. #A85200 keeps the
     * same warm amber hue at 4.95:1.
     */
    val TextLinkAndBrand = Color(0xFFA85200)

    // -------------------------------------------------------------- surface ----
    val SurfaceCream = Color(0xFFFDF3E7)
    val SurfaceWhite = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF5F0EB)
    val SurfaceBorder = Color(0xFFE4E4DC)
    /**
     * The screen canvas. The REAL coded frames (A2.0, B1.0, J2.0, N8.0, F3.0…)
     * all fill their root with Surface/Cream (#FDF3E7); the yellow #FFF0C2 the
     * earlier build used came from the Design Reference group and is retired.
     * The token keeps its name so call sites and the window theme are untouched.
     */
    val SurfaceCanvas = Color(0xFFFDF3E7)
    val SurfaceCard = Color(0xFFFFFFFF)
    val SurfaceMutedVariant = Color(0xFFFFF8E7)
    val BorderPrimary = Color(0xFF2B140E)
    /**
     * The card hairline.
     *
     * Was #E6DCB8, a pale sand that measured 1.31:1 against the white card it
     * outlines - a boundary nobody could see, which is not a boundary. #D8C8A0
     * measures 1.55:1 on white and 1.45:1 on cream: still a hairline rather than a
     * frame, but a card now reads as a distinct surface (E.3).
     */
    val BorderSubtle = Color(0xFFD8C8A0)

    // -------------------------------------------------------------- accents ----
    val AccentRed = Color(0xFFFF7071)
    val AccentPink = Color(0xFFFF9ADD)
    val AccentBlue = Color(0xFF61C2FE)
    val AccentAquamarine = Color(0xFF72E2BB)
    val AccentPurple = Color(0xFFB476F6)
    val AccentPinkStrong = Color(0xFFFF5286)
    val NavActiveInk = Color(0xFF9E1458)

    // --------------------------------------------------- status pairs (fill/ink) --
    val SuccessFill = Color(0xFFDEF5EA)
    val SuccessInk = Color(0xFF11683B)
    val WarnFill = Color(0xFFFFF0C2)
    val WarnInk = Color(0xFF7A4B00)
    val DangerFill = Color(0xFFFFE9F2)
    /**
     * Error ink: inline validation, destructive labels, danger chips.
     *
     * Was #DD3350, which measured 3.90:1 on [DangerFill] and 4.11:1 on
     * [SurfaceCanvas] - under the 4.5:1 AA floor on two of the three surfaces it is
     * drawn on, so every inline error sentence and every "Overdue" chip was
     * marginal for a low-vision reader (v2.3 HCI audit F-05/F-06). #C22442 keeps
     * the same rose hue two shades deeper and measures 5.02:1 on DangerFill,
     * 5.28:1 on cream and 5.79:1 on white - AA on all three, and still clearly a
     * warning rather than the brand gold.
     */
    val DangerInk = Color(0xFFC22442)
    val InfoFill = Color(0xFFE0ECFF)
    val InfoInk = Color(0xFF1A4B8C)
    val NeutralFill = Color(0xFFF5F0EB)
    val NeutralInk = Color(0xFF6E5D5A)

    // --------------------------------------------------- second status ramp -----
    //
    // These four families are the ones the STATUS BADGES actually render from
    // (`MomoBadges`), so their values decide whether the app reads as warm and
    // kawaii or as stock Material. The originals were Material's own cold palette
    // - #E3F2FD is a cool blue-grey, #E8F5E9 a cool mint - which sat visibly
    // against the cream canvas as though they came from a different app.
    //
    // Each surface keeps its hue family (green = good, amber = attention, rose =
    // problem, blue = informational) but is pulled toward the canvas's warm,
    // slightly cream undertone, and each ink is darkened for AA contrast on that
    // surface. Chosen by eye against the cream #FDF3E7 card background.
    val SuccessText = Color(0xFF1F6A3D)
    val SuccessBase = Color(0xFF2E7D32)
    /** Warm sage - green that belongs next to cream rather than beside it. */
    val SuccessSurface = Color(0xFFE4F2E2)
    val WarningText = Color(0xFF8A4500)
    val WarningBase = Color(0xFFE67E22)
    /** Warm amber wash; the same family as the brand gold, one step paler. */
    val WarningSurface = Color(0xFFFFEFD1)
    /**
     * The "1 available" figure on a low-stock row (E.3).
     *
     * It used to be [WarningText] #8A4500, which measures 5.60:1 on white - fine on
     * its own, but the same hue as the Low Stock badge beside it, so the count read
     * as part of the badge rather than as a separate urgent figure. A deep burnt
     * rust separates the two roles and still clears AA at 6.04:1 on white and
     * 5.61:1 on cream.
     */
    val LowStockInk = Color(0xFF9A4A00)
    val DangerText = Color(0xFF8A2430)
    val DangerBase = Color(0xFFD32F2F)
    /** Soft rose rather than pink - a warning, not a decoration. */
    val DangerSurface = Color(0xFFFCE6E4)
    val InfoText = Color(0xFF1B4A7A)
    val InfoBase = Color(0xFF1976D2)
    /** Warm dust-blue, desaturated to sit beside cream without turning cold. */
    val InfoSurface = Color(0xFFE3EDF6)

    // -------------------------------------------------------------- buttons ----
    val ButtonPrimaryFill = Color(0xFFFFB600)
    val ButtonPrimaryPressed = Color(0xFFE5A300)
    val ButtonSecondaryFill = Color(0xFFFFFFFF)
    val ButtonDisabledFill = Color(0xFFEBE6DF)
    val ButtonDisabledContent = Color(0xFFA89E9B)

    // ------------------------------------------------ Hi-Fi dashboard band ----
    // Values transcribed from Figma frame 3395:3654 node tree. They were not in
    // the original 69-variable collection because that collection predates the
    // Hi-Fi pass; each is a gradient stop / translucent chip the Hi-Fi draws
    // literally, so they are registered here rather than inlined at the call site.

    /** "Ready for your next live?" card gradient, top stop (#ffdb7e). */
    val CalloutTop = Color(0xFFFFDB7E)

    /** ...and its bottom stop (#ffd269). */
    val CalloutBottom = Color(0xFFFFD269)

    /** A "Needs your attention" row's chip fill: white at 93% over the amber card. */
    val AlertChip = Color(0xEDFFFFFF)

    /** ...and its hairline border (#ff9c86). */
    val AlertChipBorder = Color(0xFFFF9C86)

    /** The amber alert card the rows sit on (#fee6a5). */
    val AlertCard = Color(0xFFFEE6A5)

    // ------------------------------------------- Hi-Fi per-queue accents ------
    // The six snapshot tiles each carry a coloured 2dp border. The colours are the
    // Sticker Sheet accents, named here by the queue they identify so a tile and
    // its colour cannot drift apart.
    val QueueClaimsAccent = Color(0xFF61C2FE)   // Accent/Blue
    val QueueKeptAccent = Color(0xFFB476F6)     // Accent/Purple
    val QueueInvoicesAccent = Color(0xFFFF9ADD) // Accent/Pink
    val QueueToPackAccent = Color(0xFFFED570)   // warm yellow
    val QueuePartialAccent = Color(0xFFFF7071)  // Accent/Red
    val QueuePickupAccent = Color(0xFF72E2BB)   // Accent/Aquamarine

    /** Alert icon tiles inside the attention rows. */
    val AlertTileOverdue = Color(0xFF530000)
    val AlertTileWarning = Color(0xFF27ADFF)
    val AlertTilePaused = Color(0xFFFF9191)
}
