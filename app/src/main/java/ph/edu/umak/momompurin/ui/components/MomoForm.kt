package ph.edu.umak.momompurin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * The form primitives the claim, payment, shipping and invoice screens share.
 *
 * They exist so the same visual language reaches every new screen without each one
 * re-deriving a border, an error colour or a focus behaviour. Two rules are built in:
 *
 *  - **A required-but-empty field is highlighted, not silently accepted** (I-01 c2).
 *    [MomoField] takes `highlightMissing`, so a screen can mark the exact fields the
 *    criterion names.
 *  - **Reflow, never truncate** (A-04 c3). Labels and values wrap; only identifiers
 *    that must stay readable character-by-character are allowed to be monospace and
 *    single-line.
 */

/** A labelled single-line text field with an optional error/missing state. */
@Composable
fun MomoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    highlightMissing: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions =
        androidx.compose.foundation.text.KeyboardActions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    /** A1.6: the password field's Show/Hide state comes in through here. */
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
    /**
     * B1.0 draws the label ABOVE the box as a plain caption, not as Material's
     * floating label. Defaults to false so existing call sites keep their look;
     * the coded-frame screens pass true.
     */
    labelOutside: Boolean = false,
) {
    val invalid = error != null || highlightMissing
    Column(modifier.fillMaxWidth()) {
        if (labelOutside) {
            Text(
                label,
                style = MomoType.CaptionSmall,
                color = MomoColors.BrandDarkest,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = if (labelOutside) {
                null
            } else {
                { Text(label, style = MomoType.CaptionSmall) }
            },
            placeholder = {
                // With the label already printed ABOVE the box, repeating it inside
                // the empty field is duplication, not help: the reader sees
                // "Search order number or customer" twice, four pixels apart. So a
                // labelOutside field shows a placeholder only when the caller gave
                // one that says something DIFFERENT (e.g. label "Mobile number",
                // placeholder "0917 555 0101"). Otherwise the box is empty, which is
                // also the honest state of an unfilled field.
                val text = when {
                    placeholder != null -> placeholder
                    else -> null
                }
                if (text != null) Text(text, style = MomoType.CaptionSmall)
            },
            singleLine = singleLine,
            enabled = enabled,
            readOnly = readOnly,
            isError = invalid,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(MomoRadius.Input),
            // Material3 centers the trailing slot on the field's own middle only
            // when the slot is a plain control. Wrapping the caller's composable in
            // a Box with explicit center alignment guarantees the trailing content
            // shares the field's vertical center (A1.0's "Show" sits mid-height in
            // the password box) - and because a Text with `heightIn(min=48dp)`
            // paints its glyphs at the TOP of that box, the wrap also stops "Show"
            // from floating to the box's top edge.
            trailingIcon = trailing?.let { slot ->
                {
                    Box(
                        modifier = Modifier
                            .heightIn(min = MomoDimens.TouchTarget)
                            .padding(horizontal = MomoSpace.Xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        slot()
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MomoColors.BrandSecondary1Shade1,
                // B1.0's input boxes carry a 1.5dp Border/Primary (#2B140E) rule -
                // the dark outline the coded frame draws on every field.
                unfocusedBorderColor = MomoColors.BorderPrimary,
                errorBorderColor = MomoColors.DangerInk,
                focusedContainerColor = MomoColors.SurfaceCard,
                unfocusedContainerColor = MomoColors.SurfaceCard,
                disabledContainerColor = MomoColors.SurfaceMuted,
                disabledTextColor = MomoColors.TextDisabled,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
        if (error != null) {
            Text(
                error,
                style = MomoType.CaptionSmall,
                color = MomoColors.DangerInk,
                modifier = Modifier.padding(start = MomoSpace.Md, top = MomoSpace.Xs),
            )
        } else if (supporting != null) {
            Text(
                supporting,
                style = MomoType.CaptionSmall,
                color = MomoColors.TextSecondary,
                modifier = Modifier.padding(start = MomoSpace.Md, top = MomoSpace.Xs),
            )
        }
    }
}

/**
 * The one search field every list screen uses (UAT fix, item 11).
 *
 * A search box that looks exactly like a data-entry field makes the user wonder
 * whether they are meant to type a value into it, so the magnifier is the
 * affordance that says "this narrows the list" before anything is typed. The
 * height is pinned to the 48-52dp band the report asked for: Material's default
 * OutlinedTextField is 56dp, which on a 412dp-wide screen pushes the first list
 * row below the fold on all four tabs.
 *
 * The label sits INSIDE the box as the placeholder rather than above it. On a
 * list screen the label's only job is to say what can be typed, and printing it
 * twice (caption above + placeholder inside) was the duplication the old
 * `labelOutside` comment already complained about.
 */
@Composable
fun MomoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(label, style = MomoType.BodySmall, color = MomoColors.TextSecondary)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MomoColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = if (value.isEmpty()) {
            null
        } else {
            {
                // Clearing a search is the action a user reaches for next, and
                // without it they must select-and-delete by hand.
                Box(
                    Modifier
                        .heightIn(min = MomoDimens.TouchTarget)
                        .padding(horizontal = MomoSpace.Xs)
                        .clickable { onValueChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MomoColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(MomoRadius.Input),
        textStyle = MomoType.BodyDefault,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MomoColors.BrandSecondary1Shade1,
            unfocusedBorderColor = MomoColors.BorderPrimary,
            focusedContainerColor = MomoColors.SurfaceCard,
            unfocusedContainerColor = MomoColors.SurfaceCard,
        ),
        // The 48-52dp band: Material sizes the DECORATION box from the text
        // style, so pinning the container height here keeps the field slim
        // without shrinking its touch target below the accessible minimum.
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 52.dp),
    )
}

/** One option in a [MomoPicker]. [enabled] implements D-05 c5's greyed-out values. */
data class PickerOption(
    val value: String,
    val label: String = value,
    val enabled: Boolean = true,
    /** D-05 c5: why an option cannot be chosen, shown beside it. */
    val disabledReason: String? = null,
)

/**
 * A labelled dropdown limited to the values a screen passes in.
 *
 * D-01 c3 / D-05 c4: a field's dropdown offers **only** that field's valid values,
 * so the caller builds the list. A disabled option is shown greyed with its reason
 * rather than hidden, which is what D-05 c5 asks for.
 */
@Composable
fun MomoPicker(
    label: String,
    selected: String?,
    options: List<PickerOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose",
    supporting: String? = null,
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selected }?.label ?: placeholder

    Column(modifier.fillMaxWidth()) {
        Text(label, style = MomoType.CaptionSmall, color = MomoColors.TextSecondary)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = A11y.minTouchTarget)
                    .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Input))
                    .border(
                        width = if (error != null) 2.dp else 1.dp,
                        color = if (error != null) MomoColors.DangerInk else MomoColors.SurfaceBorder,
                        shape = RoundedCornerShape(MomoRadius.Input),
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = MomoSpace.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedLabel,
                    style = MomoType.BodyDefault,
                    color = if (selected == null) MomoColors.TextDisabled else MomoColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MomoColors.TextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    option.label,
                                    style = MomoType.BodyDefault,
                                    color = if (option.enabled) {
                                        MomoColors.TextPrimary
                                    } else {
                                        MomoColors.TextDisabled
                                    },
                                )
                                if (!option.enabled && option.disabledReason != null) {
                                    Text(
                                        option.disabledReason,
                                        style = MomoType.CaptionSmall,
                                        color = MomoColors.TextDisabled,
                                    )
                                }
                            }
                        },
                        enabled = option.enabled,
                        onClick = {
                            expanded = false
                            if (option.enabled) onSelect(option.value)
                        },
                    )
                }
            }
        }
        if (error != null) {
            Text(
                error,
                style = MomoType.CaptionSmall,
                color = MomoColors.DangerInk,
                modifier = Modifier.padding(start = MomoSpace.Md, top = MomoSpace.Xs),
            )
        } else if (supporting != null) {
            Text(
                supporting,
                style = MomoType.CaptionSmall,
                color = MomoColors.TextSecondary,
                modifier = Modifier.padding(start = MomoSpace.Md, top = MomoSpace.Xs),
            )
        }
    }
}

/**
 * The bottom-anchored primary action bar (J-08 c4).
 *
 * Two buttons side by side, MIN-aligned with an explicit 16dp gap — never
 * SPACE_BETWEEN with a FILL child, which collapses the gap to zero.
 */
@Composable
fun ActionBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    notice: String? = null,
    noticeIsError: Boolean = false,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MomoColors.SurfaceCream)
            .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Md),
        verticalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
    ) {
        if (notice != null) {
            Text(
                notice,
                style = MomoType.CaptionSmall,
                color = if (noticeIsError) MomoColors.DangerInk else MomoColors.SuccessText,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = MomoSpace.Lg,
                alignment = Alignment.Start,
            ),
        ) {
            if (secondaryLabel != null && onSecondary != null) {
                MomoButton(
                    label = secondaryLabel,
                    onClick = onSecondary,
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
            }
            MomoButton(
                label = primaryLabel,
                onClick = onPrimary,
                primary = true,
                enabled = primaryEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The one button shape from the `Hi-Fi / Button` master (`3572:1891`): 46dp tall,
 *  radius 12, Primary gold #FFB600 / Secondary white with a Subtle border /
 *  Disabled #EBE6DF. Ink: BrandDarkest / InkStrong / ButtonDisabledContent.
 *
 *  [compact] uses A2.5/A2.6's 44dp in-card action height instead of the standalone
 *  46dp - the master's two sizes for the same shape. Radius stays 12 either way. */
@Composable
fun MomoButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    destructive: Boolean = false,
    compact: Boolean = false,
    labelFontSize: androidx.compose.ui.unit.TextUnit? = null,
    labelHorizontalPadding: androidx.compose.ui.unit.Dp? = null,
) {
    val fill = when {
        !enabled -> MomoColors.ButtonDisabledFill
        destructive -> MomoColors.DangerBase
        primary -> MomoColors.ButtonPrimaryFill
        else -> MomoColors.SurfaceCard
    }
    val ink = when {
        !enabled -> MomoColors.ButtonDisabledContent
        destructive -> MomoColors.InkInverse
        primary -> MomoColors.BrandDarkest
        else -> MomoColors.InkStrong
    }
    Box(
        modifier
            .heightIn(min = if (compact) MomoDimens.ButtonHeightCompact else MomoDimens.ButtonHeight)
            .background(fill, RoundedCornerShape(MomoRadius.Input))
            .then(
                if (!primary && enabled && !destructive) {
                    Modifier.border(1.dp, MomoColors.BorderSubtle, RoundedCornerShape(MomoRadius.Input))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = labelHorizontalPadding ?: if (compact) MomoSpace.Md else MomoSpace.Lg,
                vertical = if (compact) 10.dp else 14.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = labelFontSize?.let { size ->
                MomoType.ButtonAction.merge(androidx.compose.ui.text.TextStyle(fontSize = size))
            } ?: MomoType.ButtonAction,
            color = ink,
            // A label is ONE line or an ellipsis - never a stack of fragments.
            // This used to be maxLines = 2 inside a fixed 46dp box, so a squeezed
            // "Confirm cancel" lost the bottom half of its second line.
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small read-only key/value line used across the detail and invoice screens. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MomoColors.TextPrimary,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = MomoSpace.Xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MomoType.CaptionSmall,
            color = MomoColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (emphasise) MomoType.DataMonospace else MomoType.DataCaption,
            color = valueColor,
            modifier = Modifier.weight(1.2f),
        )
    }
}

/**
 * A compact BORDERED action for use inside a card row.
 *
 * Nielsen #1 (visibility of system status) / Norman's signifier: an inline
 * "Record payment" in bare link-coloured text does not read as a control, and it
 * gives the eye no target boundary to aim at, so a thumb lands on the card and
 * navigates instead of acting. This renders the same label as a 34dp-tall
 * bordered pill so it is unmistakably a button and its own hit target.
 *
 * It is deliberately NOT [MomoButton]: that master is the screen-level action at
 * 44-46dp, and stacking full-size masters inside every row would make the cards
 * noisy. Same radius and border token, smaller height, tighter padding.
 */
@Composable
fun MiniAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A destructive action (cancel, remove) tints its border and label. */
    destructive: Boolean = false,
    /** A primary action (the one the row exists for) gets the gold fill. */
    emphasised: Boolean = false,
    enabled: Boolean = true,
) {
    val ink = when {
        !enabled -> MomoColors.ButtonDisabledContent
        destructive -> MomoColors.DangerInk
        else -> MomoColors.BrandDeep
    }
    val fill = when {
        !enabled -> MomoColors.ButtonDisabledFill
        emphasised -> MomoColors.ButtonPrimaryFill
        else -> MomoColors.SurfaceCard
    }
    Box(
        modifier
            .heightIn(min = MomoDimens.MiniActionHeight)
            .background(fill, RoundedCornerShape(MomoRadius.Input))
            .then(
                if (emphasised && enabled) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp,
                        if (destructive && enabled) MomoColors.DangerInk else MomoColors.BorderSubtle,
                        RoundedCornerShape(MomoRadius.Input),
                    )
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = MomoSpace.Md, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MomoType.LabelChip,
            color = if (emphasised && enabled) MomoColors.BrandDarkest else ink,
            // One line, no wrap, no fragmenting. In a FlowRow the button keeps its
            // own width and the ROW wraps instead of the label splitting.
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
