import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Supabase credentials, read from local.properties so no key is ever committed.
 * Empty when absent, which is the supported local-first configuration: the seeded
 * store is the runtime source of truth and the app needs no network to run the
 * walkthrough (Phase 2 ruling; also what J-05's offline-first operation needs).
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val supabaseUrl: String = (localProps.getProperty("SUPABASE_URL") ?: "").trim()
val supabaseAnonKey: String = (localProps.getProperty("SUPABASE_ANON_KEY") ?: "").trim()

/**
 * Release signing, read from local.properties so neither the keystore nor its
 * passwords are ever committed.
 *
 * The .jks itself lives OUTSIDE the repository (C:/Users/Gabri/.momompurin-keys)
 * and the repo's .gitignore already excludes *.jks, *.keystore and
 * local.properties. When the four properties are absent - a fresh clone, or CI
 * without the secret - the release build falls back to unsigned rather than
 * failing, so `assembleRelease` still compiles for anyone without the key.
 */
val releaseStoreFile: String? = localProps.getProperty("RELEASE_STORE_FILE")
val releaseStorePassword: String? = localProps.getProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = localProps.getProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = localProps.getProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "ph.edu.umak.momompurin"
    // 36 because supabase-kt's auth module depends on androidx.browser 1.10.0,
    // which requires its consumers to compile against API 36. The manifest
    // keeps tools:targetApi="34" for the runtime contract, which is unaffected.
    compileSdk = 36

    defaultConfig {
        applicationId = "ph.edu.umak.momompurin"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Empty values are the supported default: the app runs from the seeded store.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    signingConfigs {
        // Only declared when the four properties exist, so a clone without the key
        // does not get a half-configured signing config that fails at packaging.
        if (hasReleaseSigning) {
            create("release") {
                // A .properties value drops backslashes, so accept either separator
                // and hand Gradle a real File rather than a resolved relative path.
                storeFile = File(releaseStoreFile!!.replace('\\', '/'))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign with the local release key when it is present; otherwise this
            // stays the unsigned build it was before.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Required for the SUPABASE_* fields above.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // The remote data layer the repository shipped with (data/Supabase.kt).
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
