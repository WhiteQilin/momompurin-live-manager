package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import ph.edu.umak.momompurin.data.AccountOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.ui.components.KeyValueRow
import ph.edu.umak.momompurin.ui.components.MiniAction
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoDivider
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * A-17 Manage Account - the Customer Mode landing for the Account tab.
 *
 * A-08 c5 is enforced by what this screen READS: it renders name, email, mobile
 * and the delivery address, and never touches `reviewFlag`, so the owner's private
 * note has no path into Customer Mode at all.
 */
@Composable
fun AccountScreen(
    store: MomoStore,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onOpenOrders: () -> Unit,
) {
    val account = store.session.value
    var showLogout by remember { mutableStateOf(false) }

    if (account == null) {
        Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
            MomoTopBar(title = "Account")
            Text("Sign in to view your account.", Modifier.padding(MomoSpace.Lg))
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Account")

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MomoSpace.Lg)
                .padding(top = MomoSpace.Lg, bottom = MomoSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            MomoCard {
                Text(
                    account.name,
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Text(
                    account.email,
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
                if (account.handle.isNotBlank()) {
                    Text(
                        account.handle,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                    )
                }
            }

            MomoCard {
                Text(
                    "Contact and delivery",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                KeyValueRow("Mobile number", account.mobile.ifBlank { "\u2014" })
                KeyValueRow("Delivery address", account.fullAddress.ifBlank { "\u2014" })
            }

            // One wrapping group rather than two hand-made rows: at this width the
            // four actions do not fit on one line, and hard-coding the split left
            // the second row ragged. FlowRow wraps where it must and keeps a
            // constant gap, so the block reads as one set of actions.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
                verticalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
            ) {
                // UAT fix (item 12): "Edit profile" is now "Edit account", and
                // Change password lives INSIDE it - one place for everything that
                // changes the account, rather than two sibling entries whose
                // difference the user has to guess.
                MiniAction(label = "Edit account", onClick = onEditProfile, emphasised = true)
                MiniAction(label = "My orders", onClick = onOpenOrders)
                MiniAction(
                    label = "Log out",
                    onClick = { showLogout = true },
                    destructive = true,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

    // A-01 c7 / A-17 c7: a confirmation, then the session ends.
    if (showLogout) {
        MomoConfirmDialog(
            title = "Log out",
            message = "You will be returned to the Sign In screen.",
            confirmLabel = "Log out",
            dismissLabel = "Cancel",
            destructive = true,
            onConfirm = {
                showLogout = false
                onSignOut()
            },
            onDismiss = { showLogout = false },
        )
    }
}

/**
 * A-17 c2-c3: Edit profile.
 *
 * The email is shown but NOT editable: it is the sign-in identity and the key
 * A-01's routing reads, so letting it change here would silently move the account
 * to a different address.
 */
@Composable
fun EditAccountScreen(store: MomoStore, onBack: () -> Unit) {
    val account = store.session.value
    var fullName by remember { mutableStateOf(account?.name.orEmpty()) }
    var handle by remember { mutableStateOf(account?.handle.orEmpty()) }
    var mobile by remember { mutableStateOf(account?.mobile.orEmpty()) }
    var province by remember { mutableStateOf(account?.province.orEmpty()) }
    var city by remember { mutableStateOf(account?.city.orEmpty()) }
    var barangay by remember { mutableStateOf(account?.barangay.orEmpty()) }
    var addressLine by remember { mutableStateOf(account?.addressLine.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Edit account", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MomoSpace.Lg),
        ) {
            MomoField(
                value = fullName,
                onValueChange = { fullName = it; message = null },
                label = "Full name",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))
            MomoField(
                value = account?.email.orEmpty(),
                onValueChange = {},
                label = "Email",
                labelOutside = true,
                readOnly = true,
                supporting = "Your sign-in address cannot be changed here.",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))
            MomoField(
                value = handle,
                onValueChange = { handle = it; message = null },
                label = "Messenger or TikTok handle",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))
            MomoField(
                value = mobile,
                onValueChange = { mobile = it; message = null },
                label = "Mobile number",
                labelOutside = true,
                supporting = "11 digits",
                keyboardType = KeyboardType.Phone,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))
            // C.1: the cascading picker, prefilled from the saved profile. A value
            // the map does not carry (an address saved before v2.3, or typed under
            // "Other") falls into manual mode automatically.
            ph.edu.umak.momompurin.ui.components.PhAddressCascade(
                province = province,
                city = city,
                barangay = barangay,
                onChange = { p, c, b ->
                    province = p
                    city = c
                    barangay = b
                    message = null
                },
            )
            Spacer(Modifier.height(MomoSpace.Md))
            MomoField(
                value = addressLine,
                onValueChange = { addressLine = it; message = null },
                // UAT fix (item 10): the field asks for the street-level part of
                // the address, and the label now says exactly that instead of
                // repeating "Full address" above an already-broken-down cascade.
                label = "Street Name, Building, House No.",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (message != null) {
                Spacer(Modifier.height(MomoSpace.Md))
                Text(
                    message!!,
                    style = MomoType.CaptionBold,
                    color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xl))
            MomoButton(
                label = "Save changes",
                onClick = {
                    val failure = store.updateProfile(
                        fullName, handle, mobile, province, city, barangay, addressLine,
                    )
                    isError = failure != null
                    // A-17 c7: the success string the criterion quotes.
                    //
                    // Patch 5: routed through the app-wide snackbar. This screen is a
                    // long scrolling form, so an inline confirmation under the last
                    // field - which is where the button already is - was the one
                    // place the owner was ALREADY looking when they tapped Save,
                    // meaning a successful save and a no-op looked identical. The
                    // snackbar floats above the content and is the same control
                    // every other confirmation in the app uses.
                    val text = failure ?: AccountOps.CHANGES_SAVED
                    message = null
                    if (failure != null) {
                        ph.edu.umak.momompurin.ui.components.MomoMessages.error(text)
                    } else {
                        ph.edu.umak.momompurin.ui.components.MomoMessages.show(text)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // UAT fix (item 12): Change password is part of editing the account,
            // not a peer of it. It keeps its own fields and its own Save, because
            // a password change is a separate confirmation from a profile edit -
            // but the user finds it where they were already looking.
            Spacer(Modifier.height(MomoSpace.Xxl))
            MomoDivider()
            Spacer(Modifier.height(MomoSpace.Xl))
            ChangePasswordSection(store = store)

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}

/**
 * UAT fix (item 12): the Change password form, embedded in Edit account.
 *
 * Extracted from the former standalone [ChangePasswordScreen] so both the
 * embedded form and any future deep link share ONE implementation - the masking
 * rule (VisualTransformation, not KeyboardType) and the validation order are
 * exactly the ones that screen had.
 */
@Composable
private fun ChangePasswordSection(store: MomoStore) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val transformation = if (showPassword) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }

    Text(
        "Change password",
        style = MomoType.TitleSmall,
        color = MomoColors.TextPrimary,
    )
    Spacer(Modifier.height(MomoSpace.Xs))
    Text(
        "Choose a password of at least 8 characters.",
        style = MomoType.CaptionSmall,
        color = MomoColors.TextSecondary,
    )
    Spacer(Modifier.height(MomoSpace.Md))

    MomoField(
        value = current,
        onValueChange = { current = it; message = null },
        label = "Current password",
        labelOutside = true,
        keyboardType = KeyboardType.Password,
        visualTransformation = transformation,
        trailing = {
            LinkText(
                label = if (showPassword) "Hide" else "Show",
                onClick = { showPassword = !showPassword },
                style = MomoType.LabelChip,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(MomoSpace.Md))
    MomoField(
        value = newPassword,
        onValueChange = { newPassword = it; message = null },
        label = "New password",
        labelOutside = true,
        keyboardType = KeyboardType.Password,
        visualTransformation = transformation,
        trailing = {
            LinkText(
                label = if (showPassword) "Hide" else "Show",
                onClick = { showPassword = !showPassword },
                style = MomoType.LabelChip,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(MomoSpace.Md))
    MomoField(
        value = confirm,
        onValueChange = { confirm = it; message = null },
        label = "Confirm new password",
        labelOutside = true,
        keyboardType = KeyboardType.Password,
        visualTransformation = transformation,
        trailing = {
            LinkText(
                label = if (showPassword) "Hide" else "Show",
                onClick = { showPassword = !showPassword },
                style = MomoType.LabelChip,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (message != null) {
        Spacer(Modifier.height(MomoSpace.Md))
        Text(
            message!!,
            style = MomoType.CaptionBold,
            color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
        )
    }

    Spacer(Modifier.height(MomoSpace.Md))
    MomoButton(
        label = "Update password",
        onClick = {
            val failure = store.changePassword(current, newPassword, confirm)
            isError = failure != null
            // Patch 5: the snackbar, for the same reason as Save changes above -
            // and it matters more here, because the form CLEARS its fields on
            // success: an inline line that vanished with the mutation was
            // indistinguishable from the form simply having reset.
            val text = failure ?: AccountOps.CHANGES_SAVED
            message = null
            if (failure != null) {
                ph.edu.umak.momompurin.ui.components.MomoMessages.error(text)
            } else {
                ph.edu.umak.momompurin.ui.components.MomoMessages.show(text)
            }
            if (failure == null) {
                current = ""
                newPassword = ""
                confirm = ""
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A-17 c4-c6: Change password.
 *
 * The confirmation is checked before the length, so "Check your current password
 * and the new password." is what a wrong current password produces even when the
 * new one is also too short - which is the order the criteria imply.
 */
@Composable
fun ChangePasswordScreen(store: MomoStore, onBack: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    // KeyboardType.Password only changes the IME's behaviour - it does NOT mask the
    // glyphs. Masking in Compose comes from VisualTransformation alone, and
    // MomoField defaults to None, so all three password fields rendered in plain
    // text while the Sign In and Registration fields masked correctly.
    var showPassword by remember { mutableStateOf(false) }
    val transformation = if (showPassword) {
        androidx.compose.ui.text.input.VisualTransformation.None
    } else {
        androidx.compose.ui.text.input.PasswordVisualTransformation()
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Change password", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MomoSpace.Lg),
        ) {
            Text(
                "Choose a password of at least 8 characters.",
                style = MomoType.CaptionSmall,
                color = MomoColors.TextSecondary,
            )
            Spacer(Modifier.height(MomoSpace.Lg))

            MomoField(
                value = current,
                onValueChange = { current = it; message = null },
                label = "Current password",
                labelOutside = true,
                keyboardType = KeyboardType.Password,
                visualTransformation = transformation,
                trailing = {
                    LinkText(
                        label = if (showPassword) "Hide" else "Show",
                        onClick = { showPassword = !showPassword },
                        style = MomoType.LabelChip,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))
            MomoField(
                value = newPassword,
                onValueChange = { newPassword = it; message = null },
                label = "New password",
                labelOutside = true,
                keyboardType = KeyboardType.Password,
                visualTransformation = transformation,
                trailing = {
                    LinkText(
                        label = if (showPassword) "Hide" else "Show",
                        onClick = { showPassword = !showPassword },
                        style = MomoType.LabelChip,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))
            MomoField(
                value = confirm,
                onValueChange = { confirm = it; message = null },
                label = "Confirm new password",
                labelOutside = true,
                keyboardType = KeyboardType.Password,
                visualTransformation = transformation,
                trailing = {
                    LinkText(
                        label = if (showPassword) "Hide" else "Show",
                        onClick = { showPassword = !showPassword },
                        style = MomoType.LabelChip,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (message != null) {
                Spacer(Modifier.height(MomoSpace.Md))
                Text(
                    message!!,
                    style = MomoType.CaptionBold,
                    color = if (isError) MomoColors.DangerInk else MomoColors.SuccessText,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xl))
            MomoButton(
                label = "Save changes",
                onClick = {
                    val failure = store.changePassword(current, newPassword, confirm)
                    isError = failure != null
                    // Patch 5: the snackbar, matching the embedded form. This screen
                    // is only reachable from a saved deep link, so it must not be the
                    // one place a save confirmation still renders inline.
                    val text = failure ?: AccountOps.CHANGES_SAVED
                    message = null
                    if (failure != null) {
                        ph.edu.umak.momompurin.ui.components.MomoMessages.error(text)
                    } else {
                        ph.edu.umak.momompurin.ui.components.MomoMessages.show(text)
                    }
                    if (failure == null) {
                        current = ""
                        newPassword = ""
                        confirm = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}
