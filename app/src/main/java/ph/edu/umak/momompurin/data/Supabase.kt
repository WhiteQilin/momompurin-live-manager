package ph.edu.umak.momompurin.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Supabase project binding for Momompurin Live Manager (Phase 1).
 *
 * The anon key is a publishable credential. Supabase documents it as safe to
 * ship in a client binary, and it must ship: the app has no server of its own
 * to proxy requests through. Its safety is therefore not a property of this
 * file but of the Row Level Security policies in supabase/migrations.
 *
 * Consequence for review: any change that adds a table must add a policy in
 * the same pull request. .github/workflows/security.yml fails the build when a
 * table in the public schema has relrowsecurity = false.
 */
object Supabase {
    const val URL = "https://momompurin-prod.supabase.co"

    /** Publishable. Rotating it does not remediate an exposure; policies do. */
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIn0." +
        "REPLACE_WITH_PROJECT_ANON_KEY"

    val client: SupabaseClient by lazy {
        createSupabaseClient(URL, ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
