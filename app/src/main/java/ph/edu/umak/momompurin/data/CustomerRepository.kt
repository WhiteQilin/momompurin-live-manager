package ph.edu.umak.momompurin.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    val id: String,
    @SerialName("fb_handle") val handle: String,
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    val province: String? = null,
    val city: String? = null,
    val barangay: String? = null,
    @SerialName("address_line") val addressLine: String? = null,
)

@Serializable
data class Order(
    val id: String,
    @SerialName("order_number") val number: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("live_session") val session: String,
    @SerialName("order_status") val status: String,
    @SerialName("keep_flag") val keep: Boolean = false,
    @SerialName("total_amount") val total: Double,
)

/**
 * Read path for the owner workspace.
 *
 * ---------------------------------------------------------------------------
 * THE DEFECT THIS FILE SHIPPED WITH
 * ---------------------------------------------------------------------------
 * The original implementation issued an unfiltered select and relied entirely
 * on the server to scope the result:
 *
 *     suspend fun customers(): List<Customer> =
 *         Supabase.client.from("customers").select().decodeList<Customer>()
 *
 * That is correct only if Row Level Security is enabled on public.customers.
 * It was not. The statement the client sent was literally
 *
 *     select * from public.customers
 *
 * executed as the anon role, which held table-wide SELECT. Any caller holding
 * the key compiled into the APK received the whole customer table.
 * poc/run_poc.mjs steps 2 and 4 hold the captured before/after.
 *
 * ---------------------------------------------------------------------------
 * THE HARDENED VERSION
 * ---------------------------------------------------------------------------
 * Three changes, because the client can never be the control:
 *
 *   1. supabase/migrations/0002 revokes the anon grant, enables RLS on every
 *      table, and attaches an owner-only policy. This is what protects the
 *      data; a modified APK can bypass anything written in this file.
 *   2. The owner claim is checked before a query is issued, so a build that is
 *      signed in as a non-owner fails loudly instead of silently receiving an
 *      empty list and being mistaken for "no data".
 *   3. Columns are named instead of selected with `*`, so a future column
 *      carrying sensitive personal information is not exposed to an old client
 *      build that never expected it.
 */
class CustomerRepository(private val session: OwnerSession) {

    private fun requireOwner() {
        check(session.isOwner) { "Owner claim required for this query" }
    }

    suspend fun customers(): List<Customer> {
        requireOwner()
        return Supabase.client.from("customers")
            .select(Columns.list("id", "fb_handle", "full_name", "phone",
                "province", "city", "barangay", "address_line")) {
                order("fb_handle", Order.ASCENDING)
            }
            .decodeList<Customer>()
    }

    suspend fun orders(liveSession: String): List<Order> {
        requireOwner()
        return Supabase.client.from("orders")
            .select(Columns.list("id", "order_number", "customer_id",
                "live_session", "order_status", "keep_flag", "total_amount")) {
                filter { eq("live_session", liveSession) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Order>()
    }
}

/**
 * Session state as the app understands it. `isOwner` is read from the JWT's
 * app_metadata, which Supabase Auth sets server-side at account creation; a
 * client cannot mint it. The same claim is what public.is_owner() reads in the
 * database policy, so the two cannot disagree.
 */
interface OwnerSession {
    val isOwner: Boolean
    val userId: String?
}
