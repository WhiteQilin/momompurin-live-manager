package ph.edu.umak.momompurin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * The one confirmation dialog the app uses.
 *
 * Fixed rules, from the design system and the criteria:
 *  - 320dp wide, always centred (never a bottom sheet) - the popups in this app are
 *    centred modal cards;
 *  - dim scrim at 0.45 over the viewport;
 *  - two buttons separated by an explicit 16dp gap, laid out with MIN alignment.
 *    A SPACE_BETWEEN row with a FILL child collapses the gap to zero, which is the
 *    trap this avoids.
 *
 * A-01 c4 (sign out), A-02 c6 (unsaved changes), and A-03 c6 (irreversible action)
 * all route through here so the three never drift apart.
 */
@Composable
fun MomoConfirmDialog(
    title: String,
    message: String? = null,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Tints the confirm action for an irreversible or destructive step. */
    destructive: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Card))
                .padding(horizontal = 20.dp, vertical = MomoSpace.Xxl),
            verticalArrangement = Arrangement.spacedBy(MomoSpace.Md),
        ) {
            Text(
                text = title,
                style = MomoType.TitleSmall,
                color = MomoColors.TextPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MomoType.BodyDefault,
                    color = MomoColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // ---------------------------------------------------------------
            // Buttons: side by side when both labels are short, stacked when not.
            //
            // The dialog is a fixed 320dp card. Keep short labels side by side,
            // but give longer labels the full content width; the one-line buttons
            // use compact 13sp text and reduced horizontal padding to stay legible.
            // ---------------------------------------------------------------
            val stacked = confirmLabel.length > 15 || dismissLabel.length > 15 ||
                confirmLabel.length + dismissLabel.length > 20
            val buttonTextStyle = MomoType.ButtonAction.merge(
                TextStyle(fontSize = 13.sp),
            )
            val buttonContentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)

            if (stacked) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = MomoSpace.Sm),
                    verticalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
                ) {
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(MomoRadius.Input),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (destructive) {
                                MomoColors.DangerBase
                            } else {
                                MomoColors.ButtonPrimaryFill
                            },
                            contentColor = if (destructive) {
                                MomoColors.InkInverse
                            } else {
                                MomoColors.BrandDarkest
                            },
                        ),
                        contentPadding = buttonContentPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MomoDimens.ButtonHeight),
                    ) {
                        Text(
                            confirmLabel,
                            style = buttonTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(MomoRadius.Input),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MomoColors.SurfaceCard,
                            contentColor = MomoColors.InkStrong,
                        ),
                        contentPadding = buttonContentPadding,
                        border = BorderStroke(1.dp, MomoColors.BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MomoDimens.ButtonHeight),
                    ) {
                        Text(
                            dismissLabel,
                            style = buttonTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = MomoSpace.Sm),
                    // MIN + explicit gap. Never SPACE_BETWEEN with a FILL child.
                    horizontalArrangement = Arrangement.spacedBy(
                        space = MomoSpace.Lg,
                        alignment = Alignment.Start,
                    ),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        // The Button master's radius (3572:1893): 12, not a pill.
                        shape = RoundedCornerShape(MomoRadius.Input),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MomoColors.SurfaceCard,
                            contentColor = MomoColors.InkStrong,
                        ),
                        contentPadding = buttonContentPadding,
                        border = BorderStroke(1.dp, MomoColors.BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = MomoDimens.ButtonHeight),
                    ) {
                        Text(
                            dismissLabel,
                            style = buttonTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(MomoRadius.Input),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (destructive) {
                                MomoColors.DangerBase
                            } else {
                                MomoColors.ButtonPrimaryFill
                            },
                            contentColor = if (destructive) {
                                MomoColors.InkInverse
                            } else {
                                MomoColors.BrandDarkest
                            },
                        ),
                        contentPadding = buttonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = MomoDimens.ButtonHeight),
                    ) {
                        Text(
                            confirmLabel,
                            style = buttonTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A-04 c1: "every interactive control with a touch target at least the approved
 * minimum size". 48dp is the accessible minimum; the palette's spacing scale tops
 * out at 24dp, so this constant is a sizing rule rather than a spacing token.
 */
object A11y {
    val minTouchTarget = 48.dp
}

/** A-02 c6: the unsaved-changes guard. */
@Composable
fun UnsavedChangesDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    MomoConfirmDialog(
        title = "You have unsaved changes. Do you want to discard them?",
        confirmLabel = "Discard",
        dismissLabel = "Keep Editing",
        onConfirm = onDiscard,
        onDismiss = onKeepEditing,
        destructive = true,
    )
}

/** A-03 c6: the irreversible-action guard. */
@Composable
fun IrreversibleActionDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    MomoConfirmDialog(
        title = "Are you sure? This cannot be undone.",
        confirmLabel = "Confirm",
        dismissLabel = "Cancel",
        onConfirm = onConfirm,
        onDismiss = onCancel,
        destructive = true,
    )
}

/** A-01 c4: the sign-out confirmation. */
@Composable
fun SignOutDialog(
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    MomoConfirmDialog(
        title = "Are you sure you want to sign out?",
        confirmLabel = "Yes",
        dismissLabel = "No",
        onConfirm = onYes,
        onDismiss = onNo,
    )
}
