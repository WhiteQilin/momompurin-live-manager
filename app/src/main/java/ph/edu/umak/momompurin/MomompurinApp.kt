package ph.edu.umak.momompurin

import android.app.Application
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.PhAddresses

/**
 * Process-wide singletons. Kept to two fields on purpose: a dependency-injection
 * framework is not warranted at this size, and a tester running the walkthrough
 * benefits from state that survives activity recreation.
 */
class MomompurinApp : Application() {

    val store: MomoStore by lazy { MomoStore() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // The complete PSGC address tree ships as an asset rather than as Kotlin
        // source, so the object needs a way to open it. Registering the reader here
        // - once, at process start - keeps `PhAddresses` free of any Context field
        // and lets a Gradle unit test load the same file from disk instead.
        //
        // Nothing is parsed at this point: `PhAddresses` parses on first use, so
        // startup is not charged for a screen most sessions never open.
        PhAddresses.installAssetLoader {
            assets.open(PhAddresses.ASSET_NAME).bufferedReader().use { it.readText() }
        }
    }

    companion object {
        lateinit var instance: MomompurinApp
            private set
    }
}
