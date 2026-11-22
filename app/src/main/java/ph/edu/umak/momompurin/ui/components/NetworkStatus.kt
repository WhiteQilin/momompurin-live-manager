package ph.edu.umak.momompurin.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoRadius
import ph.edu.umak.momompurin.ui.theme.MomoSpace
import ph.edu.umak.momompurin.ui.theme.MomoType

/**
 * The device's real connectivity, as a Compose value (A.7 / D.4).
 *
 * ## Why this exists separately from the Owner's Offline Mode switch
 *
 * The Owner's switch is a TEST CONTROL: it makes the owner's writes read-only so
 * the offline criteria can be walked. It says nothing about whether the handset is
 * on a network. Before this split, the customer's Checkout read the OWNER's switch,
 * so an owner who left it on blocked a customer's checkout on the same device -
 * and a customer genuinely on a plane could still order, because nothing ever
 * asked the platform.
 *
 * [rememberNetworkStatus] subscribes to `ConnectivityManager` and reports what the
 * device actually has. `null` while the first callback is in flight, so a cold
 * start never flashes an offline error over a working connection.
 *
 * ## Validation is not "is there a transport"
 *
 * A network can be present and still not reach the internet (a captive portal, a
 * Wi-Fi network with no uplink). `NET_CAPABILITY_VALIDATED` is the platform's own
 * "this network was probed and works" flag, so this reports offline for a joined
 * network that cannot actually carry a request - which is the state a customer
 * would otherwise experience as an app that silently does nothing.
 */
@Composable
fun rememberNetworkStatus(): Boolean? {
    val context = LocalContext.current
    var online by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
            ?: return@DisposableEffect onDispose { }

        fun currentlyOnline(): Boolean {
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        // Seed from the current state so the first frame is already correct.
        online = currentlyOnline()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = currentlyOnline()
            }

            override fun onLost(network: Network) {
                online = currentlyOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                online = networkCapabilities
                    .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        // registerNetworkCallback can throw on a device whose manager is restricted;
        // an unregistered listener must not take the screen down, so the seed value
        // above stands as the answer.
        runCatching { manager.registerNetworkCallback(request, callback) }

        onDispose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
    return online
}

/**
 * A.7's persistent strip: "Offline — Read-Only".
 *
 * Deliberately compact and always visible rather than a toast: the owner needs to
 * know WHY every Save is refusing, at the moment they look at a disabled control.
 * A dismissible message would leave them staring at dead buttons with no reason on
 * screen.
 */
@Composable
fun OwnerOfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MomoColors.WarningSurface)
            .border(1.dp, MomoColors.LowStockInk.copy(alpha = 0.35f))
            .padding(horizontal = MomoSpace.Lg, vertical = MomoSpace.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MomoSpace.Sm),
    ) {
        Text(
            "\u26A0",
            style = MomoType.CaptionBold,
            color = MomoColors.LowStockInk,
        )
        Text(
            "Offline \u2014 Read-Only",
            style = MomoType.CaptionBold,
            color = MomoColors.LowStockInk,
        )
        Spacer(Modifier.width(MomoSpace.Xs))
        Text(
            "Writes are blocked until you turn Offline mode off.",
            style = MomoType.CaptionSmall,
            color = MomoColors.TextSecondary,
        )
    }
}

/**
 * A.7 / D.4: the customer's full-screen offline state.
 *
 * A customer with no connection cannot browse, so a disabled Checkout button would
 * be a dead end with no explanation. This states the condition and gives the one
 * action that can help, which is Nielsen #9 (help users recover from errors)
 * applied to a state rather than a mistake.
 */
@Composable
fun CustomerOfflineState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MomoColors.SurfaceCanvas)
            .padding(MomoSpace.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier
                .size(96.dp)
                .background(MomoColors.SurfaceCard, RoundedCornerShape(MomoRadius.Pill))
                .border(2.dp, MomoColors.BorderSubtle, RoundedCornerShape(MomoRadius.Pill)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\u26A0", style = MomoType.DisplayLarge, color = MomoColors.LowStockInk)
        }
        Spacer(Modifier.size(MomoSpace.Xl))
        Text(
            "No Internet Connection",
            style = MomoType.HeadingMedium,
            color = MomoColors.TextPrimary,
        )
        Spacer(Modifier.size(MomoSpace.Sm))
        Text(
            "Momompurin needs a connection to load the shop and place orders. " +
                "Check your Wi-Fi or mobile data, then try again.",
            style = MomoType.BodyDefault,
            color = MomoColors.TextSecondary,
        )
        Spacer(Modifier.size(MomoSpace.Xl))
        MomoButton(
            label = "Try Again",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
