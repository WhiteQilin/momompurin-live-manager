package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.R
import ph.edu.umak.momompurin.data.AccountOps
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.ui.components.MomoButton
import ph.edu.umak.momompurin.ui.components.MomoField
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * The SHARED Sign In screen - A-01 and A-12 are the same screen seen from the two
 * roles, which is exactly what A-01 c1 and A-12 c1 require.
 *
 * There is deliberately NO role toggle. A-01 c3 / A-12 c3 say the account read
 * from the email decides the mode, and A-01's GWT "No role switch is shown" is a
 * criterion - so this composable takes no role parameter and cannot render one.
 *
 * Each validation message appears under its OWN field, so a tester can tell a
 * blank email from a blank password at a glance rather than hunting one banner.
 */
@Composable
fun SignInScreen(
    store: MomoStore,
    onSignedIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        emailError = null
        passwordError = null
        error = null

        if (email.isBlank()) {
            emailError = AccountOps.BLANK_EMAIL
            return
        }
        if (!AccountOps.isEmail(email)) {
            emailError = AccountOps.INVALID_EMAIL
            return
        }
        if (password.isEmpty()) {
            passwordError = AccountOps.BLANK_PASSWORD
            return
        }
        val failure = store.attemptSignIn(email, password)
        if (failure != null) error = failure else onSignedIn()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MomoColors.SurfaceCanvas)
            .verticalScroll(rememberScrollState())
            .imePadding()
            // No bottom navigation here, so this screen owns the system nav inset.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = MomoSpace.Xxl)
            .padding(top = 16.dp, bottom = MomoSpace.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(MomoSpace.Xl))

        // A-01 c1's first label: the shop name.
        Text(
            "Momompurin",
            style = MomoType.DisplayLarge,
            color = MomoColors.BrandDeep,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MomoSpace.Lg))
        Box(
            Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MomoColors.SurfaceMutedVariant),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_signin_2890_3695),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(120.dp),
            )
        }

        Spacer(Modifier.height(MomoSpace.Xxl))

        // B.1: the neutral heading. Naming the Owner role told every customer that
        // this screen is also the owner's door, which is the exposure the tester
        // flagged; "Sign in to your account" says the same thing to the person
        // signing in and nothing to anyone else. "New here? Create an account"
        // below is retained, and there is still no role switch (A-01 c3).
        Text(
            "Sign in to your account",
            style = MomoType.TitleSmall,
            color = MomoColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(MomoSpace.Xl))

        MomoField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
                error = null
            },
            label = "Email",
            labelOutside = true,
            error = emailError,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(MomoSpace.Md))

        MomoField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
                error = null
            },
            label = "Password",
            labelOutside = true,
            error = passwordError,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            // A-01 c2 / A-12 c7: the Show/Hide toggle and its label change.
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
            Spacer(Modifier.height(MomoSpace.Sm))
            Text(
                error!!,
                style = MomoType.CaptionSmall,
                color = MomoColors.DangerInk,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(MomoSpace.Xl))

        MomoButton(
            label = "Log in",
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(MomoSpace.Lg))

        // A-01 c1's "Create an account" - the entry point to A-11.
        //
        // The two runs sit on ONE baseline rather than on their box centres: they
        // carry different type styles (12sp regular vs 13sp bold), so centring the
        // boxes leaves the smaller "New here?" visibly lower than the larger link.
        // alignByBaseline puts both glyph baselines on the same line, which is what
        // the eye reads as "level".
        //
        // The tap target is padded on the Row instead of per-item, so the 48dp hit
        // area never inflates one run's box and re-staggers the two.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MomoDimens.TouchTarget),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "New here?",
                style = MomoType.CaptionSmall,
                color = MomoColors.TextSecondary,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(MomoSpace.Xs))
            LinkText(
                label = "Create an account",
                onClick = onCreateAccount,
                // Natural height inside the Row's own 48dp band: the link keeps its
                // touch area from the Row, and its glyphs stay on the shared baseline.
                minTouchTarget = false,
                modifier = Modifier.alignByBaseline(),
            )
        }

        Spacer(Modifier.height(MomoSpace.Xxl))
    }
}

/**
 * A ripple-less tap target, for a control that reads as a text link.
 *
 * The 48dp hit area belongs to the wrapping [Box], NOT to the [Text]. Putting
 * `heightIn` on the Text is a trap this project has now hit twice: a Text paints
 * its glyphs at the TOP of a taller node, so it floats up inside whatever is
 * centring it. That is what pinned the password "Show" toggle against the field's
 * top border.
 *
 * So: the Box owns the target and centres its content; the Text keeps its natural
 * height. Pass [minTouchTarget] = false where an enclosing Row already supplies the
 * 48dp band and the run must sit on a shared baseline instead of a centred box.
 */
@Composable
fun LinkText(
    label: String,
    onClick: () -> Unit,
    style: TextStyle = MomoType.CaptionBold,
    minTouchTarget: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(if (minTouchTarget) Modifier.heightIn(min = MomoDimens.TouchTarget) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = MomoSpace.Xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = style,
            color = MomoColors.TextLinkAndBrand,
            maxLines = 1,
            softWrap = false,
        )
    }
}
