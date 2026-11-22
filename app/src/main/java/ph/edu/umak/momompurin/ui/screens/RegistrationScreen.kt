package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import ph.edu.umak.momompurin.data.AccountOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.components.MomoMessages
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * A-11: public Customer Registration.
 *
 * A-11 c7 is the structural rule: this screen offers NO role, admin or staff
 * control, and [AccountOps.register] hard-codes [ph.edu.umak.momompurin.data.entity.Role.CUSTOMER].
 * There is no code path from here to an Owner account.
 *
 * The fields are grouped the way the criteria list them - identity, contact,
 * structured delivery address, then password - so a tester walking A-11's GWT can
 * fill them top to bottom.
 *
 * C.1: the delivery address is the cascading Province -> City -> Barangay picker,
 * not three free-text fields. C.2: the password is entered twice, each field with
 * its own Show/Hide, and a mismatch blocks the registration inline before the
 * store is ever asked.
 */
@Composable
fun RegistrationScreen(
    store: MomoStore,
    onRegistered: () -> Unit,
    onBack: () -> Unit,
) {
    var fullName by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var barangay by remember { mutableStateOf("") }
    var addressLine by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // UAT fix (item 14): the RA 10173 confirmation, shown once the form is valid.
    var showPrivacy by remember { mutableStateOf(false) }

    // Patch 1: the email is checked against the live accounts on every keystroke.
    //
    // The uniqueness rule used to be enforced only inside AccountOps.register, so
    // a customer learned their email was taken after filling the whole form and
    // tapping through the privacy dialog. Reading `store.data` here recomposes as
    // soon as another account exists, and asking [AccountOps.duplicateEmail] rather
    // than re-implementing the comparison is what keeps this warning and the ops
    // layer's refusal from ever disagreeing about which addresses are taken.
    val data by store.data.collectAsState()
    val emailDuplicate = remember(data.accounts, email) {
        AccountOps.duplicateEmail(data, email)
    }

    // C.2: the mismatch is its own inline error under its own field, checked on
    // every keystroke, so a tester sees it the moment the two diverge rather than
    // only after tapping Create account.
    val confirmMismatch = confirmPassword.isNotEmpty() && confirmPassword != password

    // UAT fix (item 14): LIVE per-field validation.
    //
    // Each warning appears as soon as the field has CONTENT that is wrong, rather
    // than only after a submit. The emptiness test is skipped while the field is
    // blank so a form the user has not reached yet does not open covered in red -
    // "touched" here means "has been typed into", which Compose cannot observe
    // without a focus tracker, so content is the honest proxy.
    val emailInvalid = email.isNotBlank() && !AccountOps.isEmail(email)
    // A-11 c4 / A-17 c3: 11 digits. The other rule the report asks for - that it
    // starts with 09 - is checked too, because a PH mobile that does not is a
    // typo the length test alone accepts.
    val mobileDigits = AccountOps.normaliseMobile(mobile)
    val mobileInvalid = mobile.isNotBlank() && !AccountOps.isMobile(mobile)
    val mobileWrongPrefix = mobileDigits.length == 11 && !mobileDigits.startsWith("09")
    val passwordTooShort = password.isNotEmpty() &&
        password.length < AccountOps.MIN_PASSWORD_LENGTH

    fun submit() {
        error = null
        // C.2: the mismatch blocks BEFORE the store call, so the criterion's flow
        // is visible at the field rather than as a generic failure banner. Every
        // other rule is already on screen as a live warning, so reaching here with
        // one broken means the user tapped through them.
        if (confirmMismatch) {
            error = "Passwords do not match."
            return
        }
        if (emailInvalid) {
            error = AccountOps.INVALID_EMAIL
            return
        }
        // Patch 1: a taken address blocks BEFORE the privacy dialog, so the
        // customer is not asked to consent to saving details for an account that
        // cannot be created. The refusal is the same string the ops layer returns,
        // so the message cannot drift from the documented A-11 c5 wording.
        if (emailDuplicate) {
            error = AccountOps.DUPLICATE_EMAIL
            return
        }
        if (mobileInvalid || mobileWrongPrefix) {
            error = AccountOps.INVALID_MOBILE
            return
        }
        if (password.length < AccountOps.MIN_PASSWORD_LENGTH) {
            error = AccountOps.SHORT_PASSWORD
            return
        }
        // UAT fix (item 14): a valid form asks for the RA 10173 confirmation
        // BEFORE creating anything, so consent and the commit are the same tap and
        // neither can happen without the other.
        showPrivacy = true
    }

    /** The single place an account is actually created, after consent. */
    fun createAccount() {
        showPrivacy = false
        error = store.register(
            AccountOps.Registration(
                fullName = fullName,
                handle = handle,
                email = email,
                mobile = mobile,
                province = province,
                city = city,
                barangay = barangay,
                addressLine = addressLine,
                password = password,
            ),
        )
        // A-11 c8: on success the Shop opens signed in as the new customer.
        if (error == null) {
            // UAT fix (item 14): the welcome the report asks for, raised through
            // the app-wide snackbar so it appears over the Shop the customer lands
            // on rather than on the form that is being dismissed.
            MomoMessages.show("Account created! Welcome to Momompurin.")
            onRegistered()
        }
    }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "Create an account", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                // This screen has no bottom navigation, so it owns the system
                // navigation-bar inset itself: without it, the last control
                // ("Already have an account? Log in") lands under the gesture pill.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Lg),
        ) {
            Text(
                "Your details are saved so you do not retype them at checkout.",
                style = MomoType.CaptionSmall,
                color = MomoColors.TextSecondary,
            )
            Spacer(Modifier.height(MomoSpace.Lg))

            MomoField(
                value = fullName,
                onValueChange = { fullName = it; error = null },
                label = "Full name",
                labelOutside = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))

           // A-11 c1: "Messenger handle and/or TikTok handle" - optional, and the
            // label says so rather than leaving the tester guessing.
            MomoField(
                value = handle,
                onValueChange = { handle = it; error = null },
                label = "Messenger or TikTok handle",
                labelOutside = true,
                supporting = "Optional, for example @elena",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))

            MomoField(
                value = email,
                onValueChange = { email = it; error = null },
                label = "Email",
                labelOutside = true,
                keyboardType = KeyboardType.Email,
                // UAT fix (item 14): the warning appears as the text goes wrong,
                // not after a submit.
                //
                // Patch 1: the taken-address warning is the same live treatment, and
                // it outranks the format warning - an address that is both malformed
                // and taken is impossible, because [AccountOps.duplicateEmail]
                // refuses to call a malformed address "taken" at all.
                error = when {
                    emailInvalid -> AccountOps.INVALID_EMAIL
                    emailDuplicate -> AccountOps.DUPLICATE_EMAIL
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MomoSpace.Md))

            MomoField(
                value = mobile,
                onValueChange = { mobile = it; error = null },
                label = "Mobile number",
                labelOutside = true,
                supporting = "11 digits, for example 0917 555 0199",
                keyboardType = KeyboardType.Phone,
                // UAT fix (item 14): two separate corrections, because "not 11
                // digits" and "does not start with 09" need different fixes.
                error = when {
                    mobileInvalid -> AccountOps.INVALID_MOBILE
                    mobileWrongPrefix -> "A mobile number starts with 09."
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MomoSpace.Xl))
            Text(
                "Delivery address",
                style = MomoType.TitleSmall,
                color = MomoColors.TextPrimary,
            )
            Spacer(Modifier.height(MomoSpace.Md))

            // C.1: the cascading picker. The three fields and their clear-on-change
            // rules live in the component, so this screen cannot re-derive them
            // wrongly.
            ph.edu.umak.momompurin.ui.components.PhAddressCascade(
                province = province,
                city = city,
                barangay = barangay,
                onChange = { p, c, b ->
                    province = p
                    city = c
                    barangay = b
                    error = null
                },
            )
            Spacer(Modifier.height(MomoSpace.Md))
            MomoField(
                value = addressLine,
                onValueChange = { addressLine = it; error = null },
                // UAT fix (item 10): the cascading picker above already supplies
                // province/city/barangay, so this field is the street-level part -
                // and the label now says so.
                label = "Street Name, Building, House No.",
                labelOutside = true,
                supporting = "For example 12 Mabini St., Unit 4B",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MomoSpace.Xl))
            Text(
                "Password",
                style = MomoType.TitleSmall,
                color = MomoColors.TextPrimary,
            )
            Spacer(Modifier.height(MomoSpace.Md))

            MomoField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "Password",
                labelOutside = true,
                supporting = "At least 8 characters",
                // UAT fix (item 14): the length rule is stated the moment it is
                // broken, not on submit.
                error = if (passwordTooShort) AccountOps.SHORT_PASSWORD else null,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
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

            // C.2: the second field, with its OWN Show/Hide and its own mismatch
            // error - the criterion's "Both should have their own Show/Hide
            // controls, ideally".
            MomoField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = "Confirm password",
                labelOutside = true,
                error = if (confirmMismatch) "Passwords do not match." else null,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showConfirm) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                imeAction = ImeAction.Done,
                trailing = {
                    LinkText(
                        label = if (showConfirm) "Hide" else "Show",
                        onClick = { showConfirm = !showConfirm },
                        style = MomoType.LabelChip,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(Modifier.height(MomoSpace.Md))
                Text(
                    error!!,
                    style = MomoType.CaptionSmall,
                    color = MomoColors.DangerInk,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(MomoSpace.Xl))

            MomoButton(
                label = "Create account",
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MomoSpace.Lg))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Already have an account?",
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
                Spacer(Modifier.width(MomoSpace.Xs))
                LinkText(label = "Log in", onClick = onBack)
            }

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

    // UAT fix (item 14): the RA 10173 consent dialog. It is a full confirmation
    // rather than a checkbox because the wording is a statement about DATA the
    // customer is handing over - it has to be read once, at the moment it applies.
    // `Review` returns to the form with everything typed still in place.
    if (showPrivacy) {
        MomoConfirmDialog(
            title = "Confirm your details",
            message = "Confirm your details are accurate and that you agree to " +
                "Momompurin saving your contact and delivery information for order " +
                "fulfillment under RA 10173 (Data Privacy Act of 2012).",
            confirmLabel = "Agree & Create Account",
            dismissLabel = "Review",
            onConfirm = { createAccount() },
            onDismiss = { showPrivacy = false },
        )
    }
}
