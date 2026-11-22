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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.PhAddresses
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoDimens
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * One level of the cascading PH address picker (C.1).
 *
 * ## The interface this replaces
 *
 * A stock Material `DropdownMenu` - narrow, purple-tinted, anchored to the field,
 * and a plain scrolling list. On a real phone that made Barangay unusable: a
 * province can hold over a thousand barangays, and finding one meant dragging a
 * narrow popup through the whole list. The tester's report asked for a searchable
 * selection dialog or bottom sheet specifically for that case, and for the
 * "Other (type manually)" row to have a visible follow-through.
 *
 * ## What it is now
 *
 * A full-width [ModalBottomSheet] in the app's own palette: cream surface, white
 * cards, a search field at the top, 48dp+ rows, a checkmark on the current choice,
 * and "Other (type manually)" pinned at the bottom. Typing filters the list.
 *
 * Choosing "Other" switches this level to a text field (see [PhAddressCascade]),
 * which renders a labelled MomoField directly beneath the picker - so the place to
 * type is visible in the flow, not implied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CascadingAddressField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onManualChange: (String) -> Unit,
    manual: Boolean,
    modifier: Modifier = Modifier,
    /** Why the field cannot be used yet, e.g. "Select a province first". */
    disabledReason: String? = null,
    supporting: String? = null,
    error: String? = null,
    /**
     * Maps a stored value to the text the field prints, and the option list to
     * label/value pairs. The province level needs this because it stores
     * `"Metro Manila"` but prints `"Metro Manila (NCR)"` (C.1).
     */
    labelFor: (String) -> String = { it },
    /**
     * The label shown while this level is in manual mode. Bug 3: the manual field
     * must announce itself as a text box, e.g. "Enter custom barangay", instead of
     * repeating the dropdown's own label.
     */
    manualLabel: String? = null,
    /** The hint inside the manual field, e.g. "Type your barangay". */
    manualHint: String? = null,
) {
    val enabled = disabledReason == null
    var sheetOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Text(label, style = MomoType.CaptionSmall, color = MomoColors.BrandDarkest)
        Box(Modifier.padding(top = 6.dp)) {
            if (manual) {
                // Reuses MomoField so the typography, border and error treatment
                // match every other input on the screen.
                //
                // Bug 3 (real-phone report): the manual fallback used to look like a
                // second dropdown with the same label, so a user who picked "Other"
                // had no visual cue that this was now a place to TYPE. The label and
                // the hint say so outright, and the picker stays reachable below.
                MomoField(
                    value = value,
                    onValueChange = onManualChange,
                    label = manualLabel ?: label,
                    labelOutside = true,
                    placeholder = manualHint,
                    supporting = supporting,
                    error = error,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = A11y.minTouchTarget)
                        .background(
                            if (enabled) MomoColors.SurfaceCard else MomoColors.SurfaceMuted,
                            RoundedCornerShape(MomoRadius.Input),
                        )
                        .border(
                            width = if (error != null) 2.dp else 1.dp,
                            color = when {
                                error != null -> MomoColors.DangerInk
                                !enabled -> MomoColors.SurfaceBorder
                                else -> MomoColors.BorderPrimary
                            },
                            shape = RoundedCornerShape(MomoRadius.Input),
                        )
                        .clickable(enabled = enabled) { sheetOpen = true }
                        .padding(horizontal = MomoSpace.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (value.isBlank()) {
                            disabledReason ?: "Choose"
                        } else {
                            labelFor(value)
                        },
                        style = MomoType.BodyDefault,
                        color = when {
                            !enabled -> MomoColors.TextDisabled
                            value.isBlank() -> MomoColors.TextSecondary
                            else -> MomoColors.TextPrimary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = if (enabled) MomoColors.TextSecondary else MomoColors.TextDisabled,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (error != null) {
                    Text(
                        error,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.DangerInk,
                        modifier = Modifier
                            .padding(start = MomoSpace.Md, top = MomoSpace.Xs),
                    )
                } else if (supporting != null) {
                    Text(
                        supporting,
                        style = MomoType.CaptionSmall,
                        color = MomoColors.TextSecondary,
                        modifier = Modifier
                            .padding(start = MomoSpace.Md, top = MomoSpace.Xs),
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = MomoColors.SurfaceCream,
            contentColor = MomoColors.TextPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            AddressPickerSheet(
                label = label,
                value = value,
                options = options,
                labelFor = labelFor,
                onPick = { picked ->
                    sheetOpen = false
                    onSelect(picked)
                },
            )
        }
    }
}

/**
 * The sheet's body: title, search field, the filtered list, and the manual row.
 *
 * Split out so the search state resets every time the sheet opens - a filter left
 * over from a previous open would hide most of a fresh list with no visible reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressPickerSheet(
    label: String,
    value: String,
    options: List<String>,
    labelFor: (String) -> String,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    // "Other (type manually)" is pinned below the list rather than filtered with it:
    // a user who cannot find their barangay must always be able to reach the escape
    // hatch, even while a search term is active.
    val manualOption = options.firstOrNull { PhAddresses.isManual(it) }
    val choosable = options.filterNot { PhAddresses.isManual(it) }
    val filtered = if (query.isBlank()) {
        choosable
    } else {
        choosable.filter { labelFor(it).contains(query, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = MomoSpace.Sm),
    ) {
        Text(
            "Select $label",
            style = MomoType.TitleSmall,
            color = MomoColors.BrandDeep,
            modifier = Modifier.padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Sm),
        )
        MomoField(
            value = query,
            onValueChange = { query = it },
            label = "Search ${label.lowercase()}",
            labelOutside = true,
            trailing = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MomoColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
            modifier = Modifier.padding(horizontal = MomoSpace.Lg),
        )

        if (filtered.isEmpty()) {
            Text(
                "No match for \u201C$query\u201D.",
                style = MomoType.BodySmall,
                color = MomoColors.TextSecondary,
                modifier = Modifier.padding(MomoSpace.Lg),
            )
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(top = MomoSpace.Sm),
        ) {
            items(filtered) { option ->
                AddressRow(
                    text = labelFor(option),
                    selected = option == value,
                    onClick = { onPick(option) },
                )
            }
        }

        if (manualOption != null) {
            // The manual row sits on its own hairline so it reads as a different
            // kind of choice, not as one more barangay.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MomoSpace.Lg)
                    .background(MomoColors.BorderSubtle)
                    .padding(top = 1.dp),
            ) {}
            AddressRow(
                text = labelFor(manualOption),
                selected = false,
                accent = true,
                onClick = { onPick(manualOption) },
            )
        }
    }
}

/** One 48dp+ row: full-width tap, checkmark on the current value. */
@Composable
private fun AddressRow(
    text: String,
    selected: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MomoDimens.TouchTarget)
            .background(
                if (selected) MomoColors.SurfaceMutedVariant else MomoColors.SurfaceCard,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
    ) {
        Text(
            text,
            style = MomoType.BodyDefault,
            color = when {
                accent -> MomoColors.TextLinkAndBrand
                selected -> MomoColors.BrandDeep
                else -> MomoColors.TextPrimary
            },
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MomoColors.BrandDeep,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The whole Province -> City -> Barangay cascade in one control (C.1).
 *
 * The three fields and their cascade rules live together so no screen can wire
 * them inconsistently. [onChange] hands back the complete triple after every
 * interaction, already cleared correctly:
 *
 *  - a new province clears the city and the barangay;
 *  - a new city clears the barangay;
 *  - City is disabled until a province is set; Barangay until a city is.
 *
 * "Other (type manually)" at any level switches that level to a text field. A manual
 * parent has no mapped children, so the level below it starts in manual mode too -
 * which is what keeps a customer outside the curated map from being gated.
 */
@Composable
fun PhAddressCascade(
    province: String,
    city: String,
    barangay: String,
    onChange: (province: String, city: String, barangay: String) -> Unit,
    modifier: Modifier = Modifier,
    provinceError: String? = null,
    cityError: String? = null,
    barangayError: String? = null,
) {
    // Manual mode is per-level and DERIVED: a value the map does not know can only
    // have come from typing, so it is manual by definition. It also has an EXPLICIT
    // arm (Bug 3): picking "Other (type manually)" clears the value, and a cleared
    // value alone would just show "Choose" again with no cue that typing is now the
    // expected interaction. `manualJustPicked` holds the level whose Other row was
    // tapped, until the user types or picks a real option, so the labelled text
    // field appears immediately where the dropdown was.
    var manualJustPicked by remember { mutableStateOf<String?>(null) }
    fun clearManualFlag(level: String) {
        if (manualJustPicked == level) manualJustPicked = null
    }

    val manualProvince = (manualJustPicked == "province") ||
        (province.isNotBlank() && !PhAddresses.knowsProvince(province))
    val manualCity = (manualJustPicked == "city") ||
        (city.isNotBlank() && !PhAddresses.knowsCity(province, city))
    val manualBarangay = (manualJustPicked == "barangay") ||
        (barangay.isNotBlank() && !PhAddresses.knowsBarangay(province, city, barangay))

    val cityOptions = if (manualProvince) {
        listOf(PhAddresses.MANUAL)
    } else {
        PhAddresses.options(PhAddresses.cities(province))
    }
    val barangayOptions = if (manualProvince || manualCity) {
        listOf(PhAddresses.MANUAL)
    } else {
        PhAddresses.options(PhAddresses.barangays(province, city))
    }

    Column(modifier.fillMaxWidth()) {
        CascadingAddressField(
            label = "Province",
            value = province,
            options = PhAddresses.options(PhAddresses.provinces()),
            onSelect = { picked ->
                if (PhAddresses.isManual(picked)) {
                    // Bug 3: flip this level into manual mode immediately - the
                    // labelled "Enter custom province" field appears in place of the
                    // dropdown the moment Other is tapped, instead of a cleared
                    // dropdown that reads "Choose" again.
                    manualJustPicked = "province"
                    onChange("", "", "")
                } else {
                    clearManualFlag("province")
                    val (p, c, b) = PhAddresses.clearBelow(picked)
                    onChange(p, c, b)
                }
            },
            onManualChange = { typed ->
                clearManualFlag("province")
                onChange(typed, city, barangay)
            },
            manual = manualProvince,
            error = provinceError,
            // C.1: "explicitly including Metro Manila (NCR) under Province".
            labelFor = PhAddresses::provinceLabel,
            // Bug 3: picking "Other" must visibly become a place to type.
            manualLabel = "Enter custom province",
            manualHint = "Type your province",
        )

        Box(Modifier.padding(top = MomoSpace.Md)) {
            CascadingAddressField(
                label = "City",
                value = city,
                options = cityOptions,
                onSelect = { picked ->
                    if (PhAddresses.isManual(picked)) {
                        // Bug 3: manual mode immediately, as with Province.
                        manualJustPicked = "city"
                        onChange(province, "", "")
                    } else {
                        clearManualFlag("city")
                        // A new city also invalidates any barangay manual mode: the
                        // old barangay was for the previous city.
                        clearManualFlag("barangay")
                        val (p, c, b) = PhAddresses.clearBarangay(province, picked)
                        onChange(p, c, b)
                    }
                },
                onManualChange = { typed ->
                    clearManualFlag("city")
                    onChange(province, typed, barangay)
                },
                manual = manualCity,
                disabledReason = if (province.isBlank()) "Select a province first" else null,
                error = cityError,
                manualLabel = "Enter custom city",
                manualHint = "Type your city or municipality",
            )
        }

        Box(Modifier.padding(top = MomoSpace.Md)) {
            CascadingAddressField(
                label = "Barangay",
                value = barangay,
                options = barangayOptions,
                onSelect = { picked ->
                    if (PhAddresses.isManual(picked)) {
                        manualJustPicked = "barangay"
                        onChange(province, city, "")
                    } else {
                        clearManualFlag("barangay")
                        onChange(province, city, picked)
                    }
                },
                onManualChange = { typed ->
                    clearManualFlag("barangay")
                    onChange(province, city, typed)
                },
                manual = manualBarangay,
                disabledReason = if (city.isBlank()) "Select a city first" else null,
                error = barangayError,
                // The report named this level specifically: barangay lists are the
                // long ones, so the manual escape must be obvious here.
                manualLabel = "Enter custom barangay",
                manualHint = "Type your barangay",
            )
        }
    }
}
