package ph.edu.umak.momompurin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ph.edu.umak.momompurin.R
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.Role
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney

/**
 * One derived alert (E.1).
 *
 * ## Derived, not stored
 *
 * There is no `notifications` table. Every alert is a predicate over the data the
 * app already holds, so a bell can never show an alert for an order that has since
 * been paid, nor miss one that just became overdue. A stored queue would need its
 * own invalidation and would be wrong between writes.
 *
 * [orderId] is what makes the row actionable: tapping it opens the order that needs
 * attention rather than a generic list.
 */
data class MomoAlert(
    val title: String,
    val detail: String,
    val orderId: String? = null,
    /** True for money or stock that is going wrong, so the row reads as urgent. */
    val urgent: Boolean = false,
)

/**
 * E.1: the alerts the OWNER should see.
 *
 * Three sources, each an existing criterion:
 *  - A-04: orders sitting in Incoming that have not been invoiced;
 *  - A-05: orders past their 24-hour payment window (A-04 c2 / A-05 c5);
 *  - A-02 c3: products at or below the low-stock level.
 */
fun ownerAlerts(data: MomoData, now: Long): List<MomoAlert> {
    val alerts = mutableListOf<MomoAlert>()

    data.orders
        .filter { it.stage == OrderStage.ORDER_PLACED }
        .sortedByDescending { it.placedAt }
        .forEach { order ->
            alerts += MomoAlert(
                title = "New order ${order.orderId}",
                detail = "${data.customerName(order.customerEmail)} placed " +
                    "${MomoMoney.format(order.total)}. Send the invoice.",
                orderId = order.orderId,
            )
        }

    data.orders
        .filter { it.isOverdue(now) }
        .sortedBy { it.paymentDeadline ?: Long.MAX_VALUE }
        .forEach { order ->
            alerts += MomoAlert(
                title = "Overdue ${order.orderId}",
                detail = "${MomoMoney.format(order.balance)} still unpaid past the " +
                    "24-hour window.",
                orderId = order.orderId,
                urgent = true,
            )
        }

    data.lowStockProducts().forEach { product ->
        alerts += MomoAlert(
            title = "Low stock ${product.sku}",
            detail = "${product.name} has ${product.available} available.",
            urgent = product.available == 0,
        )
    }
    return alerts
}

/**
 * E.1: the alerts the CUSTOMER should see, limited to their OWN orders.
 *
 * Every predicate reads `ordersFor(email)`, so another customer's order can never
 * appear - the filter is the query, not a post-hoc check.
 */
fun customerAlerts(data: MomoData, email: String, now: Long): List<MomoAlert> {
    val mine = data.ordersFor(email)
    val alerts = mutableListOf<MomoAlert>()

    mine.filter { it.invoiceNo.isNotBlank() && it.balance > 0 && it.stage != OrderStage.CANCELLED }
        .forEach { order ->
            alerts += MomoAlert(
                title = "Invoice ${order.invoiceNo} is ready",
                detail = "${MomoMoney.format(order.balance)} is due on ${order.orderId}.",
                orderId = order.orderId,
                urgent = order.isOverdue(now),
            )
        }

    mine.filter { it.paymentStatus == PaymentStatus.PAID && it.stage != OrderStage.CANCELLED }
        .forEach { order ->
            alerts += MomoAlert(
                title = "${order.orderId} is Paid",
                detail = "Thank you. The owner will pack it next.",
                orderId = order.orderId,
            )
        }

    mine.filter {
        it.packingStatus == ph.edu.umak.momompurin.data.entity.PackingStatus.PACKED &&
            it.stage != OrderStage.CANCELLED
    }.forEach { order ->
        alerts += MomoAlert(
            title = "${order.orderId} is packed",
            detail = if (order.shippingStatus == ShippingStatus.IN_TRANSIT) {
                "On the way" + order.trackingNo.takeIf { it.isNotBlank() }
                    ?.let { " \u00B7 $it" }.orEmpty()
            } else {
                "Waiting for the courier."
            },
            orderId = order.orderId,
        )
    }

    mine.filter { it.shippingStatus == ShippingStatus.IN_TRANSIT }.forEach { order ->
        alerts += MomoAlert(
            title = "${order.orderId} is in transit",
            detail = listOfNotNull(
                order.courier.takeIf { it.isNotBlank() },
                order.trackingNo.takeIf { it.isNotBlank() },
            ).joinToString(" \u00B7 ").ifBlank { "With the courier." },
            orderId = order.orderId,
        )
    }

    mine.filter { it.shippingStatus == ShippingStatus.RECEIVED }.forEach { order ->
        alerts += MomoAlert(
            title = "${order.orderId} received",
            detail = "Enjoy your Momompurin items.",
            orderId = order.orderId,
        )
    }

    mine.filter { it.stage == OrderStage.CANCELLED }.forEach { order ->
        alerts += MomoAlert(
            title = "${order.orderId} cancelled",
            detail = order.cancellation?.let { "${it.by.label}: ${it.reason}" }
                ?: "Cancelled.",
            orderId = order.orderId,
        )
    }

    return alerts
}

/**
 * E.1: the bell, with an unread count.
 *
 * The count is the number of alerts, not a separate read-state: every alert here is
 * a live condition, so "unread" and "still true" are the same thing and a dismissed
 * alert would be a lie about the data.
 */
@Composable
fun NotificationBell(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bell),
            contentDescription = if (count > 0) {
                "Notifications, $count new"
            } else {
                "Notifications"
            },
            tint = MomoColors.BrandDarkest,
            modifier = Modifier.size(24.dp),
        )
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 2.dp)
                    .size(18.dp)
                    .background(MomoColors.DangerBase, RoundedCornerShape(MomoRadius.Pill))
                    .border(1.5.dp, MomoColors.BrandPrimary, RoundedCornerShape(MomoRadius.Pill)),
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
 * E.1: the alert sheet.
 *
 * A centred card rather than a bottom sheet, matching every other modal in the app
 * (the design system has one popup shape). Scrollable, because the owner's list can
 * legitimately run long on a busy day.
 */
@Composable
fun NotificationSheet(
    alerts: List<MomoAlert>,
    onOpenOrder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Card))
                .padding(MomoSpace.Xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notifications",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .heightIn(min = 40.dp)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = MomoSpace.Sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Close", style = MomoType.LabelChip, color = MomoColors.BrandDeep)
                }
            }
            Spacer(Modifier.height(MomoSpace.Sm))

            if (alerts.isEmpty()) {
                Text(
                    "Nothing needs your attention right now.",
                    style = MomoType.BodyDefault,
                    color = MomoColors.TextSecondary,
                    modifier = Modifier.padding(vertical = MomoSpace.Xl),
                )
            } else {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
                ) {
                    alerts.forEach { alert ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (alert.urgent) {
                                        MomoColors.DangerSurface
                                    } else {
                                        MomoColors.SurfaceMuted
                                    },
                                    RoundedCornerShape(MomoRadius.Input),
                                )
                                .clickable(enabled = alert.orderId != null) {
                                    alert.orderId?.let {
                                        onDismiss()
                                        onOpenOrder(it)
                                    }
                                }
                                .padding(MomoSpace.Md),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                if (alert.urgent) "\u26A0" else "\u2022",
                                style = MomoType.CaptionBold,
                                color = if (alert.urgent) {
                                    MomoColors.DangerText
                                } else {
                                    MomoColors.BrandDeep
                                },
                            )
                            Spacer(Modifier.size(MomoSpace.Sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    alert.title,
                                    style = MomoType.BodySmall,
                                    color = MomoColors.TextPrimary,
                                )
                                Text(
                                    alert.detail,
                                    style = MomoType.CaptionSmall,
                                    color = MomoColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The alerts a signed-in role should see, in one call. */
fun alertsFor(data: MomoData, role: Role, email: String, now: Long): List<MomoAlert> =
    when (role) {
        Role.OWNER -> ownerAlerts(data, now)
        Role.CUSTOMER -> customerAlerts(data, email, now)
    }

/** The orders an alert may point at, so the caller can resolve an id safely. */
fun MomoAlert.orderOrNull(data: MomoData): Order? =
    orderId?.let { data.order(it) }
