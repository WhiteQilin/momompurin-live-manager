package ph.edu.umak.momompurin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.data.entity.StockStatus
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * Badge variants from the `Status Badge` component set (3253:7941) - 15 variants
 * across Type=Order and Type=Inventory, all 23dp tall (handoff s.3.4).
 *
 * Two rules the design makes non-negotiable:
 *  - colour is always paired with its text label, never used alone (D-01 c8);
 *  - the vocabulary is closed - a status not in s.3.4 does not exist.
 */
data class BadgeSpec(
    val label: String,
    val width: Dp,
    val fill: Color,
    val ink: Color,
)

/**
 * The pivot's closed status vocabularies.
 *
 * Colour is always paired with its text label, never used alone, and a status
 * that is not one of these does not exist - so a badge can never invent a state
 * the app cannot reach.
 */
object MomoBadges {

    // ------------------------------------------------- A-04 c4: payment ------
    // Unpaid / Partially Paid / Paid are the three A-05 defines.
    fun forPayment(status: PaymentStatus, overdue: Boolean = false): BadgeSpec = when {
        overdue -> BadgeSpec("Overdue", 71.dp, MomoColors.DangerSurface, MomoColors.DangerText)
        status == PaymentStatus.UNPAID ->
            BadgeSpec("Unpaid", 62.dp, MomoColors.WarningSurface, MomoColors.WarningText)
        status == PaymentStatus.PARTIALLY_PAID ->
            BadgeSpec("Partially Paid", 103.dp, MomoColors.InfoSurface, MomoColors.InfoText)
        else -> BadgeSpec("Paid", 47.dp, MomoColors.SuccessSurface, MomoColors.SuccessText)
    }

    // --------------------- UAT fix (item 8): awaiting the seller's approval -----
    /**
     * UAT fix (item 8): the badge a CUSTOMER sees before the invoice exists.
     *
     * The customer's order detail used to show `Order Placed` and an active
     * `View invoice` control, which invited a tap on a document that had not been
     * written yet. The approval badge is the customer-facing name for that same
     * stage, and it says what is happening rather than naming the seller's queue.
     */
    fun forApproval(): BadgeSpec =
        BadgeSpec("Waiting for Seller's Approval", 196.dp, MomoColors.InfoSurface, MomoColors.InfoText)

    // ---------------------------------------------- A-04 c3: the stage --------
    fun forStage(stage: OrderStage): BadgeSpec = when (stage) {
        OrderStage.ORDER_PLACED ->
            BadgeSpec("Order Placed", 92.dp, MomoColors.WarningSurface, MomoColors.WarningText)
        OrderStage.WAITING_PAYMENT ->
            BadgeSpec("Waiting for Payment", 121.dp, MomoColors.InfoSurface, MomoColors.InfoText)
        OrderStage.TO_PACK ->
            BadgeSpec("To Pack", 64.dp, MomoColors.WarningSurface, MomoColors.WarningText)
        // UAT fix (item 5): a packed parcel is no longer packing work. It shares
        // the To Pack family's warning colour because it is still pre-dispatch
        // work the owner owes someone, not a state the customer can act on.
        OrderStage.TO_SHIP ->
            BadgeSpec("To Ship", 64.dp, MomoColors.WarningSurface, MomoColors.WarningText)
        OrderStage.SHIPPED ->
            BadgeSpec("Shipped", 70.dp, MomoColors.InfoSurface, MomoColors.InfoText)
        OrderStage.COMPLETED ->
            BadgeSpec("Completed", 84.dp, MomoColors.SuccessSurface, MomoColors.SuccessText)
        OrderStage.CANCELLED ->
            BadgeSpec("Cancelled", 78.dp, MomoColors.DangerSurface, MomoColors.DangerText)
    }

    // ------------------------------------------- A-07 c4: the packing ---------
    fun forPacking(status: PackingStatus): BadgeSpec? = when (status) {
        PackingStatus.NOT_PACKED -> null
        PackingStatus.PACKED ->
            BadgeSpec("Packed", 62.dp, MomoColors.SuccessSurface, MomoColors.SuccessText)
    }

    // ------------------------------------------ A-07 c7: the shipping ---------
    fun forShipping(status: ShippingStatus): BadgeSpec? = when (status) {
        ShippingStatus.NOT_SHIPPED -> null
        ShippingStatus.FOR_PICKUP ->
            BadgeSpec("For Pickup", 82.dp, MomoColors.WarningSurface, MomoColors.WarningText)
        ShippingStatus.IN_TRANSIT ->
            BadgeSpec("In Transit", 84.dp, MomoColors.InfoSurface, MomoColors.InfoText)
        ShippingStatus.RECEIVED ->
            BadgeSpec("Received", 71.dp, MomoColors.SuccessSurface, MomoColors.SuccessText)
    }

    // ----------------------------------------- A-13 c5: the stock badge -------
    fun forStock(status: StockStatus): BadgeSpec = when (status) {
        StockStatus.IN_STOCK ->
            BadgeSpec("In Stock", 74.dp, MomoColors.SuccessSurface, MomoColors.SuccessText)
        StockStatus.LOW_STOCK ->
            BadgeSpec("Low Stock", 82.dp, MomoColors.WarningSurface, MomoColors.WarningText)
        StockStatus.OUT_OF_STOCK ->
            BadgeSpec("Out of Stock", 96.dp, MomoColors.DangerSurface, MomoColors.DangerText)
    }
}

/**
 * The uppercase pill for the same vocabularies, used where the design calls for
 * a state chip rather than a sentence-case badge.
 */
object StatePill {

    fun forPayment(status: PaymentStatus): String = when (status) {
        PaymentStatus.UNPAID -> "UNPAID"
        PaymentStatus.PARTIALLY_PAID -> "PARTIALLY PAID"
        PaymentStatus.PAID -> "PAID"
    }

    fun forStage(stage: OrderStage): String = when (stage) {
        OrderStage.ORDER_PLACED -> "ORDER PLACED"
        OrderStage.WAITING_PAYMENT -> "WAITING FOR PAYMENT"
        OrderStage.TO_PACK -> "TO PACK"
        OrderStage.TO_SHIP -> "TO SHIP"
        OrderStage.SHIPPED -> "SHIPPED"
        OrderStage.COMPLETED -> "COMPLETED"
        OrderStage.CANCELLED -> "CANCELLED"
    }

    fun forPacking(status: PackingStatus): String = when (status) {
        PackingStatus.NOT_PACKED -> "NOT PACKED"
        PackingStatus.PACKED -> "PACKED"
    }

    fun forShipping(status: ShippingStatus): String = when (status) {
        ShippingStatus.NOT_SHIPPED -> "NOT SHIPPED"
        ShippingStatus.FOR_PICKUP -> "FOR PICKUP"
        ShippingStatus.IN_TRANSIT -> "IN TRANSIT"
        ShippingStatus.RECEIVED -> "RECEIVED"
    }

    fun forStock(status: StockStatus): String = when (status) {
        StockStatus.IN_STOCK -> "IN STOCK"
        StockStatus.LOW_STOCK -> "LOW STOCK"
        StockStatus.OUT_OF_STOCK -> "OUT OF STOCK"
    }
}

/**
 * 23dp tall, pill-shaped, label always present.
 *
 * [BadgeSpec.width] is the width the design measured for each variant, applied
 * as a *minimum*: the label is never truncated, because a badge whose text reads
 * "Waiting for" instead of "Waiting for Payment" fails D-01 c8 (the label is the
 * meaning). Where the design width is enough, the badge is exactly that width.
 */
@Composable
fun StatusBadge(spec: BadgeSpec, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(23.dp)
            .widthIn(min = spec.width)
            .background(spec.fill, RoundedCornerShape(MomoRadius.Pill))
            .border(1.dp, spec.ink.copy(alpha = 0.18f), RoundedCornerShape(MomoRadius.Pill))
            .padding(horizontal = MomoSpace.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = spec.label,
            style = MomoType.LabelChip,
            color = spec.ink,
            maxLines = 1,
        )
    }
}

/** A neutral chip for metadata that is a value, not a status. */
@Composable
fun MetaChip(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MomoColors.SurfaceMutedVariant, RoundedCornerShape(MomoRadius.Pill))
            .padding(horizontal = MomoSpace.Md, vertical = MomoSpace.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MomoSpace.Xs),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MomoColors.TextSecondary, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MomoType.LabelChip, color = MomoColors.TextSecondary, maxLines = 1)
    }
}
