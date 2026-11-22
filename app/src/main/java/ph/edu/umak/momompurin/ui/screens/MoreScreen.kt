package ph.edu.umak.momompurin.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.ui.components.MomoCard
import ph.edu.umak.momompurin.ui.components.MomoConfirmDialog
import ph.edu.umak.momompurin.ui.components.MomoTopBar
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * A-10's hub, reached from the More tab.
 *
 * A-06, A-09 and A-10 all live behind this tab - the criteria route them exactly
 * that way ("tap More and then Hold and combine orders", "... and then Reports",
 * "... and then Settings"). Log out is offered here as well as inside Settings,
 * because A-01's GWT walks `More > Log out` and A-10's walks Settings > Log out;
 * both are criterion-named controls, so both must exist.
 */
@Composable
fun MoreScreen(
    store: MomoStore,
    onOpenHeld: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditHistory: () -> Unit,
    onSignOut: () -> Unit,
) {
    val offline by store.offlineMode.collectAsState()
    val owner = store.session.value
    var showLogout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        MomoTopBar(title = "More")

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
                    owner?.name ?: "Owner",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Text(
                    owner?.email.orEmpty(),
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            }

            MoreRow(
                title = "Hold and combine orders",
                subtitle = "Paid orders kept for one shipment",
                onClick = onOpenHeld,
            )
            MoreRow(
                title = "Reports",
                subtitle = "Sales and stock for a date range",
                onClick = onOpenReports,
            )
            MoreRow(
                title = "Settings",
                subtitle = "Shop settings, lists, backups",
                onClick = onOpenSettings,
            )
            MoreRow(
                title = "Edit history",
                subtitle = "Every change, with who and when",
                onClick = onOpenEditHistory,
            )

            // Read-Only Offline: the same switch Settings carries, here as well,
            // because the criteria say "More > Test controls > Offline mode".
            MomoCard {
                Text(
                    "Test controls",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextPrimary,
                )
                Spacer(Modifier.height(MomoSpace.Sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Offline mode",
                            style = MomoType.BodyDefault,
                            color = MomoColors.TextPrimary,
                        )
                        Text(
                            "Read-only while offline",
                            style = MomoType.CaptionSmall,
                            color = MomoColors.TextSecondary,
                        )
                    }
                    Switch(
                        checked = offline,
                        onCheckedChange = { store.setOfflineMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MomoColors.BrandDarkest,
                            checkedTrackColor = MomoColors.BrandPrimary,
                            uncheckedThumbColor = MomoColors.SurfaceCard,
                            uncheckedTrackColor = MomoColors.SurfaceBorder,
                            uncheckedBorderColor = MomoColors.TextSecondary,
                        ),
                    )
                }
            }

            MoreRow(
                title = "Log out",
                subtitle = "End the session and return to Sign In",
                onClick = { showLogout = true },
                destructive = true,
            )

            Spacer(Modifier.height(MomoSpace.Xxl))
        }
    }

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

/** One navigation row on the More hub: title, sub-line, and a chevron. */
@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    MomoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MomoType.TitleSmall,
                    color = if (destructive) MomoColors.DangerInk else MomoColors.TextPrimary,
                )
                Text(
                    subtitle,
                    style = MomoType.CaptionSmall,
                    color = MomoColors.TextSecondary,
                )
            }
            // A trailing Text carrying its own heightIn paints its glyph at the TOP
            // of the slot, so every "›" rode a few dp high. The minimum-height box
            // belongs on a wrapper whose content is centred, never on the Text.
            Box(
                Modifier
                    .heightIn(min = 32.dp)
                    .padding(horizontal = MomoSpace.Sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "\u203A",
                    style = MomoType.TitleSmall,
                    color = MomoColors.TextSecondary,
                )
            }
        }
    }
}
