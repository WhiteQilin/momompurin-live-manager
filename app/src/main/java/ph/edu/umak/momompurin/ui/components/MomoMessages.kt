package ph.edu.umak.momompurin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * A confirmation message the whole app can raise (A.5 / D.1).
 *
 * ## The defect this replaces
 *
 * Every screen used to render its own success text INSIDE the scrolling content -
 * `notice = "Added to cart."` as a green line under the product grid, `"Private note
 * saved."` under the note field, and so on. On a long screen that line is below the
 * fold, so the customer taps Add to cart, sees nothing change, and taps again. Two
 * separate tester reports describe exactly that ("there is no pop-up text or prompt
 * whatsoever").
 *
 * ## Why a shared flow rather than per-screen state
 *
 * A Snackbar belongs to the SCAFFOLD, not to the screen, so it must sit above the
 * bottom navigation bar and stay put while the content scrolls. A single host in
 * [ph.edu.umak.momompurin.ui.nav.MomoNavHost] plus this bus means every screen
 * raises the same control in the same place, and no screen can regress to an
 * inline line by forgetting to render it.
 */
object MomoMessages {

    /** One message: the text, an optional action label, and its callback. */
    data class Message(
        val text: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null,
        /** True for a refusal, so the host can colour it as a problem. */
        val isError: Boolean = false,
    )

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 8)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    /** Raise a confirmation. Safe to call from a click handler. */
    fun show(text: String) {
        _messages.tryEmit(Message(text))
    }

    /** Raise a confirmation carrying one action (e.g. "Open" after a PDF export). */
    fun show(text: String, actionLabel: String, action: () -> Unit) {
        _messages.tryEmit(Message(text, actionLabel, action))
    }

    /** Raise a refusal, so it is not mistaken for a success. */
    fun error(text: String) {
        _messages.tryEmit(Message(text, isError = true))
    }
}

/**
 * The one Snackbar host, anchored above the bottom navigation bar.
 *
 * [bottomPadding] is what clears the bar: the host is drawn in the scaffold's
 * bottom slot, so it needs the bar's own height plus the system navigation inset,
 * which the caller supplies because only it knows whether a bar is showing.
 *
 * A floating Material snackbar is the platform's own "something happened" control,
 * so it carries the checkmark, the surface and the elevation a user already
 * recognises - which is the recommendation in the bug report.
 */
@Composable
fun MomoSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            val isError = data.visuals.message.startsWith("\u26A0")
            Row(
                Modifier
                    .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Sm)
                    .fillMaxWidth()
                    .background(
                        if (isError) MomoColors.DangerSurface else MomoColors.SuccessSurface,
                        RoundedCornerShape(MomoRadius.Input),
                    )
                    .border(
                        1.dp,
                        if (isError) {
                            MomoColors.DangerText.copy(alpha = 0.35f)
                        } else {
                            MomoColors.SuccessText.copy(alpha = 0.30f)
                        },
                        RoundedCornerShape(MomoRadius.Input),
                    )
                    .padding(horizontal = MomoSpace.Md, vertical = MomoSpace.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
            ) {
                Text(
                    if (isError) "\u26A0" else "\u2713",
                    style = MomoType.CaptionBold,
                    color = if (isError) MomoColors.DangerText else MomoColors.SuccessText,
                )
                Text(
                    data.visuals.message.removePrefix("\u26A0 "),
                    style = MomoType.BodySmall,
                    color = MomoColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (data.visuals.actionLabel != null) {
                    val actionText: String = data.visuals.actionLabel ?: ""
                    // Bug 4 (real-phone report): this was a plain Box + Text with NO
                    // click handler, so tapping "Open" on the export confirmation did
                    // nothing at all - the snackbar looked like a button and behaved
                    // like a label. It now performs the snackbar's action, which is
                    // what makes showSnackbar return ActionPerformed and run the
                    // caller's open-export callback.
                    Box(
                        Modifier
                            .heightIn(min = MomoDimens.TouchTarget)
                            .background(
                                MomoColors.SurfaceCard,
                                RoundedCornerShape(MomoRadius.Input),
                            )
                            .border(
                                1.dp,
                                MomoColors.BorderSubtle,
                                RoundedCornerShape(MomoRadius.Input),
                            )
                            .clickable { data.performAction() }
                            .padding(horizontal = MomoSpace.Md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            actionText,
                            style = MomoType.LabelChip,
                            color = MomoColors.BrandDeep,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Wires [MomoMessages] into a [SnackbarHostState] for the lifetime of the caller.
 *
 * The host state is created once per scaffold, so a message raised on any screen
 * appears in the same place rather than under that screen's content.
 */
@Composable
fun rememberMomoSnackbar(): SnackbarHostState {
    val state = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        MomoMessages.messages.collect { message ->
            val label: String? = message.actionLabel
            val result = state.showSnackbar(
                message = if (message.isError) "\u26A0 ${message.text}" else message.text,
                actionLabel = label,
                withDismissAction = label == null,
                duration = if (label == null) {
                    androidx.compose.material3.SnackbarDuration.Short
                } else {
                    // An action the user must be able to reach (Open the PDF) gets
                    // the longer window, because Short is ~4s and the file system
                    // may still be flushing the download.
                    androidx.compose.material3.SnackbarDuration.Long
                },
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                message.action?.invoke()
            }
        }
    }
    return state
}
