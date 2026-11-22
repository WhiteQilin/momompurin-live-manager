package ph.edu.umak.momompurin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ph.edu.umak.momompurin.ui.nav.MomoNavHost
import ph.edu.umak.momompurin.ui.theme.MomompurinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MomompurinApp
        setContent {
            MomompurinTheme {
                // The session lives in the process-wide store, so it survives
                // activity recreation without a second persistence layer.
                MomoNavHost(store = app.store)
            }
        }
    }
}
