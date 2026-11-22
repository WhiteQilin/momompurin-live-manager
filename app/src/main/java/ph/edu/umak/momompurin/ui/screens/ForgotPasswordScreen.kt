package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import ph.edu.umak.momompurin.data.AccountOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * UAT fix (item 13): Reset Customer Password.
 *
 * The customer proves identity with the email they registered and the mobile
 * number on the same account, then picks a new password. The work - including the
 * owner refusal and the PBKDF2 digest - lives in
 * [AccountOps.resetCustomerPassword]; this screen only collects and reports.
 *
 * ## Why it is customer-only
 *
 * The owner's account can only be changed from inside an authenticated session.
 * An email-plus-phone claim is strong enough for a customer account whose data is
 * their own orders, and not strong enough for the account that owns the shop's
 * stock, prices and sales records. The refusal is stated on the screen itself
 * (below), so an owner does not walk the whole form before discovering the rule.
 */
@Composable
fun ForgotPasswordScreen(
    store: MomoStore,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    val transformation = if (showPassword) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Reset password", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Lg),
        ) {
            Text(
                "Enter the email and mobile number on your account, then choose a " +
                    "new password.",
                style = MomoType.CaptionSmall,
                color = MomoColors.TextSecondary,
            )
            Spacer(Modifier.height(MomoSpace.Lg))

            MomoField(
                value = email,
                onValueChange = { email = it; error = null },
                label = "Email",
                labelOutside = true,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))

            MomoField(
                value = mobile,
                onValueChange = { mobile = it; error = null },
                label = "Mobile number",
                labelOutside = true,
                supporting = "11 digits, for example 0917 555 0101",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MomoSpace.Xl))
            Text(
                "New password",
                style = MomoType.TitleSmall,
                color = MomoColors.TextPrimary,
            )
            Spacer(Modifier.height(MomoSpace.Md))

            MomoField(
                value = newPassword,
                onValueChange = { newPassword = it; error = null },
                label = "New password",
                labelOutside = true,
                supporting = "At least 8 characters",
                keyboardType = KeyboardType.Password,
                visualTransformation = transformation,
                imeAction = ImeAction.Next,
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
                onValueChange = { confirm = it; error = null },
                label = "Confirm new password",
                labelOutside = true,
                keyboardType = KeyboardType.Password,
                visualTransformation = transformation,
                imeAction = ImeAction.Done,
                trailing = {
                    LinkText(
                        label = if (showPassword) "Hide" else "Show",
                        onClick = { showPassword = !showPassword },
                        style = MomoType.LabelChip,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(Modifier.height(MomoSpace.Md))
                Text(
                    error!!,
                    style = MomoType.CaptionBold,
                    color = MomoColors.DangerInk,
                )
            }
            if (success != null) {
                Spacer(Modifier.height(MomoSpace.Md))
                Text(
                    success!!,
                    style = MomoType.CaptionBold,
                    color = MomoColors.SuccessText,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xl))
            MomoButton(
                label = "Reset password",
                onClick = {
                    error = null
                    success = null
                    val failure = store.resetCustomerPassword(
                        email = email,
                        mobile = mobile,
                        newPassword = newPassword,
                        confirm = confirm,
                    )
                    if (failure == null) {
                        success = AccountOps.PASSWORD_RESET
                        // Clears the secrets from the form; the screen remains so
                        // the confirmation is readable rather than flashed away.
                        newPassword = ""
                        confirm = ""
                    } else {
                        error = failure
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MomoSpace.Lg))
            // The policy is stated UP FRONT rather than only after a failed
            // attempt: an owner who reads this knows not to walk the form at all.
            Text(
                AccountOps.OWNER_RECOVERY_RESTRICTED,
                style = MomoType.CaptionSmall,
                color = MomoColors.TextSecondary,
            )
            Spacer(Modifier.height(MomoSpace.Sm))
            Column(Modifier.fillMaxWidth()) {
                LinkText(
                    label = "Back to sign in",
                    onClick = onBack,
                    style = MomoType.CaptionBold,
                )
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }
}
