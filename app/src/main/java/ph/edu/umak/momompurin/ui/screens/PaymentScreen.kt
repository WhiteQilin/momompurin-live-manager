package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.OrderOps
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoMessages
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import ph.edu.umak.momompurin.ui.theme.MomoDate

/**
 * A-05 Verify Payments and Invoices - Record payment.
 *
 * There is NO payment-reference field: the pivot settles payment off-app, and
 * A-15 c8 says so explicitly. The method, the amount and the balance are the whole
 * form.
 *
 * ## The two-step commit (A.3, the bug that failed A-05)
 *
 * The old screen called `recordPayment` on "Save payment" and then showed the
 * "This order will be marked as Paid." dialog over a store that had ALREADY taken
 * the money - so tapping `Close` left a Paid order the customer never confirmed.
 * The flow is now:
 *
 *  1. **Save payment** validates through [OrderOps.previewPayment], which writes
 *     nothing, and opens the dialog over the untouched store;
 *  2. **Mark as Paid** commits through [MomoStore.recordPayment] - the only path
 *     that writes;
 *  3. **Close** dismisses the dialog. Zero writes. The amount stays in the field so
 *     the owner can correct it.
 *
 * ## Voiding a payment (A.3, the trapped-state fix)
 *
 * Each recorded row carries a **Void** action (with confirmation) for orders that
 * have not shipped. Removing the row recomputes Paid, Balance and the payment
 * status from the survivors, so an order whose rows previously summed to its total
 * - the "Balance P0.00 + Partially Paid" trap - can always be corrected.
 */
@Composable
fun PaymentScreen(
    store: MomoStore,
    orderId: String,
    onBack: () -> Unit,
) {
    val data by store.data.collectAsState()
    val order = data.order(orderId)
    val offline by store.offlineMode.collectAsState()
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("GCash") }
    var notice by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // The pending payment the dialog is deciding about. Distinct from `notice`:
    // a refusal and a confirmation are different states, and conflating them is
    // how "Close" ended up meaning "already saved".
    var pendingPayment by remember { mutableStateOf<OrderOps.PaymentPreview?>(null) }
    var voidingPaymentId by remember { mutableStateOf<String?>(null) }

    if (order == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = "Record payment", onBack = onBack)
            Text("That order no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    // A-05 c1: the total, the amount already paid and the balance.
    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Record payment", onBack = onBack, contextPill = order.orderId)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                KeyValueRow("Total", MomoMoney.format(order.total), emphasise = true)
                KeyValueRow("Paid", MomoMoney.format(order.amountPaid))
                KeyValueRow(
                    "Balance",
                    MomoMoney.format(order.balance),
                    emphasise = true,
                    valueColor = if (order.balance > 0) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            // A-05 c2: the method is one of exactly three.
            MomoCard {
                Text("Payment method", style = MomoType.TitleSmall, color = MomoColors.TextPrimary)
                Spacer(Modifier.height(MomoSpace.Sm))
                PAYMENT_METHODS.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .background(
                                if (method == option) {
                                    MomoColors.SurfaceMutedVariant
                                } else {
                                    MomoColors.SurfaceCard
                                },
                                RoundedCornerShape(MomoRadius.Input),
                            )
                            .border(
                                if (method == option) 2.dp else 1.dp,
                                if (method == option) MomoColors.BrandDeep else MomoColors.BorderSubtle,
                                RoundedCornerShape(MomoRadius.Input),
                            )
                            .clickable { method = option }
                            .padding(horizontal = MomoSpace.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .background(
                                    if (method == option) {
                                        MomoColors.BrandDeep
                                    } else {
                                        MomoColors.SurfaceCard
                                    },
                                    RoundedCornerShape(999.dp),
                                )
                                .border(2.dp, MomoColors.BrandDeep, RoundedCornerShape(999.dp)),
                        )
                        Text(
                            option,
                            style = MomoType.BodyDefault,
                            color = MomoColors.TextPrimary,
                            modifier = Modifier.padding(start = MomoSpace.Md),
                        )
                    }
                    Spacer(Modifier.height(MomoSpace.Sm))
                }
            }

            MomoField(
                value = amount,
                onValueChange = { amount = it; notice = null },
                label = "Amount received",
                labelOutside = true,
                keyboardType = KeyboardType.Decimal,
                supporting = "Not more than the balance",
                modifier = Modifier.fillMaxWidth(),
            )

            if (notice != null) {
                Text(
                    notice!!,
                    style = MomoType.CaptionBold,
                    color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            MomoButton(
                label = "Save payment",
                onClick = {
                    // A.3: validate WITHOUT writing. The dialog that follows is a
                    // real decision over an untouched store, so Close is a real
                    // abort.
                    when (val check = store.checkPayment(orderId, amount, method)) {
                        is OrderOps.PaymentCheck.Refused -> {
                            notice = check.message
                            isError = true
                        }
                        is OrderOps.PaymentCheck.Ready -> {
                            notice = null
                            pendingPayment = check.preview
                        }
                    }
                },
                enabled = !offline && order.balance > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            if (offline) {
                Text(
                    "Read-only while offline.",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.DangerInk,
                )
            }

            // The recorded payments, newest first, each voidable pre-dispatch.
            val payments = data.paymentsFor(orderId)
            if (payments.isNotEmpty()) {
                MomoCard {
                    Text(
                        "Payments received",
                        style = MomoType.TitleSmall,
                        color = MomoColors.TextPrimary,
                    )
                    Spacer(Modifier.height(MomoSpace.Sm))
                    payments.sortedByDescending { it.paidAt }.forEach { payment ->
                        Row(Modifier.fillMaxWidth().padding(vertical = MomoSpace.Xs)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    payment.method,
                                    style = MomoType.BodyDefault,
                                    color = MomoColors.TextPrimary,
                                )
                                Text(
                                    MomoDate.formatDateTime(payment.paidAt),
                                    style = MomoType.CaptionSmall,
                                    color = MomoColors.TextSecondary,
                                )
                            }
                            Text(
                                MomoMoney.format(payment.amount),
                                style = MomoType.DataCaption,
                                color = MomoColors.SuccessText,
                            )
                            // A.3: Void, pre-dispatch only. The confirmation lives
                            // in the dialog below, so a stray tap cannot delete a
                            // payment record.
                            if (order.shippingStatus ==
                                ph.edu.umak.momompurin.data.entity.ShippingStatus.NOT_SHIPPED
                            ) {
                                MiniAction(
                                    label = "Void",
                                    destructive = true,
                                    enabled = !offline,
                                    onClick = { voidingPaymentId = payment.paymentId },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

    // ---------------------------------------------------------------- dialogs --

    // A-05 c4's dialog. Confirming COMMITS; dismissing (Close, back, scrim tap)
    // writes nothing at all - the preview object is simply dropped.
    pendingPayment?.let { preview ->
        MomoConfirmDialog(
            title = "Mark as Paid",
            message = if (preview.clearsBalance) {
                "${OrderOps.MARK_AS_PAID_PROMPT} " +
                    "${MomoMoney.format(preview.amount)} received, balance " +
                    "${MomoMoney.format(preview.newBalance)}."
            } else {
                "${MomoMoney.format(preview.amount)} received, balance " +
                    "${MomoMoney.format(preview.newBalance)}."
            },
            confirmLabel = if (preview.clearsBalance) "Mark as Paid" else "Save payment",
            dismissLabel = "Close",
            onConfirm = {
                pendingPayment = null
                when (val result = store.recordPayment(orderId, amount, method)) {
                    is OrderOps.PaymentResult.Recorded -> {
                        notice = result.message
                        isError = false
                        amount = ""
                        MomoMessages.show(result.message)
                    }
                    is OrderOps.PaymentResult.Refused -> {
                        notice = result.message
                        isError = true
                    }
                }
            },
            onDismiss = {
                // The whole abort: drop the preview, keep the amount in the field,
                // change nothing in the store.
                pendingPayment = null
            },
        )
    }

    // The void confirmation (A.3). Confirming recomputes the order from the
    // remaining rows; dismissing keeps the payment.
    voidingPaymentId?.let { paymentId ->
        val payment = data.payments.firstOrNull { it.paymentId == paymentId }
        MomoConfirmDialog(
            title = "Remove payment",
            message = payment?.let {
                "Remove the ${MomoMoney.format(it.amount)} ${it.method} payment on " +
                    "${order.orderId}? Paid, Balance and the payment status will be " +
                    "recalculated."
            } ?: "Remove this payment?",
            confirmLabel = "Remove",
            dismissLabel = "Keep",
            destructive = true,
            onConfirm = {
                voidingPaymentId = null
                when (val result = store.voidPayment(orderId, paymentId)) {
                    is OrderOps.UnmarkResult.Applied -> {
                        notice = OrderOps.PAYMENT_VOIDED
                        isError = false
                        MomoMessages.show(OrderOps.PAYMENT_VOIDED)
                    }
                    is OrderOps.UnmarkResult.Refused -> {
                        notice = result.message
                        isError = true
                        MomoMessages.error(result.message)
                    }
                }
            },
            onDismiss = { voidingPaymentId = null },
        )
    }
}

/** A-05 c2: the only three methods the app offers. */
private val PAYMENT_METHODS = listOf("GCash", "Maya", "Bank Transfer")
