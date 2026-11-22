package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.entity.Account
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoMessages
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType
import ph.edu.umak.momompurin.ui.theme.MomoMoney
import ph.edu.umak.momompurin.ui.theme.MomoDate

/**
 * A-08 Manage Customer Records - the list.
 *
 * A newly registered customer appears here with no extra step (A-08 c6) because
 * this list reads the same `accounts` collection Registration writes to.
 */
@Composable
fun ManageCustomersScreen(
    store: MomoStore,
    onOpenCustomer: (String) -> Unit,
) {
    val data by store.data.collectAsState()
    var query by remember { mutableStateOf("") }

    val customers = remember(data.accounts, query) {
        data.customers().filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.email.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Customers")

        // Bug 7: the same 16dp gap the other tabs use, so no tab's search field
        // sits flush against the yellow band.
        Column(
            Modifier.padding(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.TopContentGap,
            ),
        ) {
            MomoField(
                value = query,
                onValueChange = { query = it },
                label = "Search name or email",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MomoSpace.Lg,
                end = MomoSpace.Lg,
                top = MomoSpace.Lg,
                bottom = MomoSpace.Xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            if (customers.isEmpty()) {
                // A-08 c2's zero-match state.
                item {
                    Text(
                        "No customers found.",
                        style = MomoType.BodyDefault,
                        color = MomoColors.TextSecondary,
                        modifier = Modifier.padding(vertical = MomoSpace.Xl),
                    )
                }
            }
            items(customers, key = { it.email }) { customer ->
                MomoCard(Modifier.clickable { onOpenCustomer(customer.email) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                customer.name,
                                style = MomoType.TitleSmall,
                                color = MomoColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                customer.email,
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                            Text(
                                customer.mobile,
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                        // A-08 c5: the owner-only marker, in the OWNER's list.
                        if (customer.reviewFlag != null) {
                            Text(
                                "Note",
                                style = MomoType.LabelChip,
                                color = MomoColors.WarningText,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A-08 c3-c5: one customer's profile, with the clickable order history and the
 * owner-only review flag.
 *
 * The flag is read and written HERE only. No customer screen reads `reviewFlag`,
 * so A-08 c5's "never in the customer's own view" holds structurally.
 */
@Composable
fun CustomerProfileScreen(
    store: MomoStore,
    email: String,
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val data by store.data.collectAsState()
    val offline by store.offlineMode.collectAsState()
    val customer = data.account(email)
    var note by remember(customer?.reviewFlag) { mutableStateOf(customer?.reviewFlag.orEmpty()) }
    var editingNote by remember { mutableStateOf(customer?.reviewFlag != null) }

    if (customer == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = "Customer", onBack = onBack)
            Text("That customer no longer exists.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    val orders = data.ordersFor(email)

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = customer.name, onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                KeyValueRow("Name", customer.name)
                KeyValueRow("Email", customer.email)
                KeyValueRow("Mobile", customer.mobile.ifBlank { "\u2014" })
                if (customer.handle.isNotBlank()) {
                    KeyValueRow("Handle", customer.handle)
                }
                KeyValueRow("Delivery address", customer.fullAddress.ifBlank { "\u2014" })
            }

            // A-08 c4-c5: the private review flag.
            MomoCard {
                Text(
                    "Private note",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Text(
                    "Only you can see this.",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                if (editingNote) {
                    MomoField(
                        value = note,
                        onValueChange = { note = it },
                        label = "Note",
                        labelOutside = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(MomoSpace.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm)) {
                        MiniAction(
                            label = "Save note",
                            emphasised = true,
                            enabled = !offline,
                            onClick = {
                                store.setReviewFlag(email, note)
                                editingNote = note.isNotBlank()
                                // A.5: the confirmation the tester never saw. It
                                // floats above the navigation bar rather than
                                // rendering as an inline line under the field that
                                // could be (and was) missed.
                                MomoMessages.show("Private note saved.")
                            },
                        )
                        MiniAction(
                            label = "Remove note",
                            destructive = true,
                            enabled = !offline,
                            onClick = {
                                note = ""
                                store.setReviewFlag(email, null)
                                editingNote = false
                                MomoMessages.show("Private note removed.")
                            },
                        )
                    }
                } else {
                    // An "Add note" that becomes the editor, so the control the
                    // criteria name always exists.
                    MiniAction(
                        label = "Add note",
                        onClick = { editingNote = true },
                        emphasised = true,
                    )
                }
            }

            // A-08 c3: the order history, where each row opens the order.
            MomoCard {
                Text(
                    "Order history",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                if (orders.isEmpty()) {
                    Text(
                        "No orders yet.",
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                } else {
                    orders.forEachIndexed { index, order ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenOrder(order.orderId) }
                                .padding(vertical = MomoSpace.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${order.orderId} \u00B7 ${MomoMoney.format(order.total)}",
                                style = MomoType.BodyDefault,
                                color = MomoColors.BrandDeep,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                order.stage.label,
                                style = MomoType.CaptionSmall,
                                color = MomoColors.TextSecondary,
                            )
                        }
                        if (index != orders.lastIndex) MomoDivider()
                    }
                }
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}
