package ph.edu.umak.momompurin.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ph.edu.umak.momompurin.data.entity.Account
import ph.edu.umak.momompurin.data.entity.CancelledBy
import ph.edu.umak.momompurin.data.entity.Fulfillment
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.data.entity.ProductOption
import ph.edu.umak.momompurin.data.entity.Role
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.data.entity.StockAdjustment

/**
 * The single in-process source of truth, and the app's one mutation point.
 *
 * Every write goes through [update], so a screen never observes a half-applied
 * change: the ops layer builds the whole next snapshot and this class swaps it in
 * one step. A refused operation returns its input untouched, which is how
 * "and change nothing" holds for every guarded transition.
 */
class MomoStore(seed: MomoData = SeedData.build()) {

    private val _data = MutableStateFlow(seed)
    val data: StateFlow<MomoData> = _data.asStateFlow()

    // ---------------------------------------------------------------- session --

    /**
     * The signed-in account. `null` means the Sign In screen.
     *
     * A-01 c3 / A-12 c3: this is set from the ACCOUNT found by the email, never
     * from a role the user picked, so there is no role switch to store.
     */
    private val _session = MutableStateFlow<Account?>(null)
    val session: StateFlow<Account?> = _session.asStateFlow()

    fun signIn(account: Account) {
        _session.value = account
    }

    /**
     * A-01 c7 / A-10 c7 / A-17 c7: end the session.
     *
     * The dataset is NOT reseeded. In the pivot a placed order, a recorded payment
     * and a stock movement are the shop's records, not a signed-in user's cache -
     * wiping them on sign-out would erase sales the owner just made. What sign-out
     * clears is the session itself.
     */
    fun signOut() {
        _session.value = null
    }

    // ------------------------------------------------------------- tools ---------

    /**
     * A-10 c8 / B-02: the Owner's read-only OFFLINE MODE switch.
     *
     * This is a TEST CONTROL the owner flips to walk the read-only criteria. It is
     * deliberately NOT the customer's offline state:
     *
     *  - it is only ever settable from an Owner screen, and only consulted for
     *    Owner writes (see [isOffline] and [ownerReadOnly]);
     *  - Customer Mode never reads it, so an owner who leaves the switch on cannot
     *    block a customer's checkout. That coupling was the defect: a customer
     *    signed in on the same handset inherited the owner's test switch.
     *
     * The customer's own offline state comes from the device's real connectivity,
     * read in the UI layer through [ph.edu.umak.momompurin.ui.components.NetworkStatus]
     * and passed to [OrderOps.placeOrder] as its own `offline` argument.
     */
    private val _offlineMode = MutableStateFlow(false)
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    fun setOfflineMode(enabled: Boolean) {
        _offlineMode.value = enabled
    }

    /** The active cart of the signed-in customer; empty for the owner. */
    fun cart(): List<ph.edu.umak.momompurin.data.entity.CartLine> {
        val email = _session.value?.email ?: return emptyList()
        return _data.value.cart(email)
    }

    fun snapshot(): MomoData = _data.value

    /** The reference instant the seed was built around. */
    val now: Long get() = SeedData.NOW_ANCHOR

    /**
     * Apply a transform, then SEAL any activity-log rows it appended (IAS R.5).
     *
     * Sealing here rather than in every ops function is what makes the chain
     * structural: no write path can append a row and forget its digest, because
     * the append and the seal happen in the same step. Rows that were already
     * sealed keep their digests byte-identical - [AuditChain.append] only computes
     * the new tail - so a legitimate append can never look like tampering.
     */
    fun update(transform: (MomoData) -> MomoData) {
        _data.update { current ->
            val next = transform(current)
            val appended = next.activityLog.drop(current.activityLog.size)
            if (appended.isEmpty()) {
                next
            } else {
                val sealedTail = appended.fold(current.activityLog) { acc, entry ->
                    AuditChain.append(acc, entry)
                }
                next.copy(activityLog = sealedTail)
            }
        }
    }

    /**
     * IAS R.5: recompute the whole audit chain and report whether it holds.
     *
     * Backs the badge on Edit History. A tampered, deleted or re-ordered row makes
     * this false and names the first break, so the owner sees a broken chain rather
     * than a history that silently reads as intact.
     */
    fun verifyAuditChain(): AuditChain.Verification =
        AuditChain.verify(_data.value.activityLog)

    /** IAS R.5: the one-line badge the Edit History screen renders. */
    fun auditBadge(): String = AuditChain.badgeText(verifyAuditChain())

    /**
     * A-10 c8 / B-02: whether OWNER writes are refused right now.
     *
     * Owner-only by construction: a customer session never returns true here, so
     * the owner's test switch cannot reach a customer screen even on a shared
     * handset. Every owner write below gates on this, not on [isOffline].
     */
    val ownerReadOnly: Boolean get() = _offlineMode.value && _session.value?.role == Role.OWNER

    /**
     * The legacy accessor, kept for call sites that mean "the owner's switch".
     *
     * It is [ownerReadOnly] rather than the raw flag so a customer session can
     * never be read-only by accident - the class of bug the split exists to close.
     */
    val isOffline: Boolean get() = ownerReadOnly

    /**
     * The device's REAL connectivity, mirrored into the store by the UI layer.
     *
     * `null` means "not yet probed", which the customer screens treat as online so
     * a cold start never flashes an offline error over a working connection.
     *
     * This is a separate channel from [offlineMode] on purpose. The owner's switch
     * is a test control; this is what the customer's handset actually reports.
     * Conflating them was the defect: an owner leaving the switch on used to block
     * a customer on the same device, and a customer on a plane saw a working shop.
     */
    private val _deviceOnline = MutableStateFlow<Boolean?>(null)
    val deviceOnline: StateFlow<Boolean?> = _deviceOnline.asStateFlow()

    fun setDeviceOnline(online: Boolean) {
        _deviceOnline.value = online
    }

    /** True only when the device has positively reported no connection. */
    val customerOffline: Boolean get() = _deviceOnline.value == false

    /**
     * A-10 c6: write a backup of the whole dataset and return the file.
     *
     * The caller supplies the directory because only the UI layer holds the
     * Android [android.content.Context]; keeping the file I/O here means the
     * screen cannot invent a path the app has no permission to write.
     *
     * Returns null on any I/O failure so the caller can show a failure rather than
     * a success over a file that does not exist.
     */
    fun writeBackup(dir: java.io.File): java.io.File? {
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "momompurin-backup.json")
            file.writeText(exportJson())
            file
        }.getOrNull()
    }

    /**
     * A minimal, human-readable snapshot: enough to restore the working set.
     *
     * This emits the ACTUAL ROWS, not a tally. The previous version wrote
     * `"accounts": 6, "products": 10, …` - a file that reported how much data there
     * was while containing none of it, so "Backup saved." described a backup that
     * could not restore anything.
     *
     * JSON is built by hand rather than with a serializer because the entity types
     * have no @Serializable annotations and adding them for a one-way export would
     * be more surface than this needs. Every string is escaped by [esc].
     */
    private fun exportJson(): String {
        val d = _data.value
        return buildString {
            appendLine("{")
            appendLine("  \"exportedAt\": \"${esc(
                ph.edu.umak.momompurin.ui.theme.MomoDate.formatDateTime(System.currentTimeMillis())
            )}\",")

            appendLine("  \"accounts\": [")
            d.accounts.forEachIndexed { i, a ->
                append("    {")
                append("\"email\":\"${esc(a.email)}\",")
                append("\"role\":\"${a.role.name}\",")
                append("\"name\":\"${esc(a.name)}\",")
                append("\"handle\":\"${esc(a.handle)}\",")
                append("\"mobile\":\"${esc(a.mobile)}\",")
                append("\"province\":\"${esc(a.province)}\",")
                append("\"city\":\"${esc(a.city)}\",")
                append("\"barangay\":\"${esc(a.barangay)}\",")
                append("\"addressLine\":\"${esc(a.addressLine)}\",")
                append("\"reviewFlag\":${a.reviewFlag?.let { "\"${esc(it)}\"" } ?: "null"},")
                append("\"createdAt\":${a.createdAt}")
                append("}${if (i < d.accounts.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"products\": [")
            d.products.forEachIndexed { i, p ->
                append("    {")
                append("\"sku\":\"${esc(p.sku)}\",")
                append("\"name\":\"${esc(p.name)}\",")
                append("\"price\":${p.price},")
                append("\"category\":\"${esc(p.category)}\",")
                append("\"character\":\"${esc(p.character)}\",")
                append("\"description\":\"${esc(p.description)}\",")
                append("\"specs\":\"${esc(p.specs)}\",")
                append("\"physical\":${p.physical},")
                append("\"reserved\":${p.reserved},")
                append("\"isElectronic\":${p.isElectronic},")
                append("\"active\":${p.active},")
                append("\"isParent\":${p.isParent},")
                append("\"parentSku\":${p.parentSku?.let { "\"${esc(it)}\"" } ?: "null"},")
                append("\"photoRef\":${p.photoRef?.let { "\"${esc(it)}\"" } ?: "null"},")
                append("\"packSize\":\"${esc(p.packSize)}\",")
                append("\"packCost\":${p.packCost},")
                append("\"packQty\":${p.packQty},")
                append("\"markupRate\":${p.markupRate},")
                append("\"unitCost\":${p.unitCost}")
                append("}${if (i < d.products.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"orders\": [")
            d.orders.forEachIndexed { i, o ->
                append("    {")
                append("\"orderId\":\"${esc(o.orderId)}\",")
                append("\"customerEmail\":\"${esc(o.customerEmail)}\",")
                append("\"placedAt\":${o.placedAt},")
                append("\"stage\":\"${o.stage.name}\",")
                append("\"paymentStatus\":\"${o.paymentStatus.name}\",")
                append("\"packingStatus\":\"${o.packingStatus.name}\",")
                append("\"shippingStatus\":\"${o.shippingStatus.name}\",")
                append("\"fulfillment\":\"${o.fulfillment.name}\",")
                append("\"invoiceNo\":\"${esc(o.invoiceNo)}\",")
                append("\"itemSubtotal\":${o.itemSubtotal},")
                append("\"shippingFee\":${o.shippingFee},")
                append("\"amountPaid\":${o.amountPaid},")
                append("\"recipientName\":\"${esc(o.recipientName)}\",")
                append("\"mobile\":\"${esc(o.mobile)}\",")
                append("\"addressBlock\":\"${esc(o.addressBlock)}\",")
                append("\"courier\":\"${esc(o.courier)}\",")
                append("\"trackingNo\":\"${esc(o.trackingNo)}\",")
                append("\"packedAt\":${o.packedAt ?: "null"},")
                append("\"invoicedAt\":${o.invoicedAt ?: "null"},")
                append("\"paymentDeadline\":${o.paymentDeadline ?: "null"},")
                append("\"cancellation\":${o.cancellation?.let {
                    "{\"by\":\"${it.by.name}\",\"reason\":\"${esc(it.reason)}\",\"at\":${it.at}}"
                } ?: "null"},")
                append("\"combinedShipmentNo\":${o.combinedShipmentNo?.let { "\"${esc(it)}\"" } ?: "null"}")
                append("}${if (i < d.orders.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"orderLines\": [")
            d.orderLines.forEachIndexed { i, l ->
                append("    {")
                append("\"lineId\":\"${esc(l.lineId)}\",")
                append("\"orderId\":\"${esc(l.orderId)}\",")
                append("\"sku\":\"${esc(l.sku)}\",")
                append("\"productName\":\"${esc(l.productName)}\",")
                append("\"variantName\":\"${esc(l.variantName)}\",")
                append("\"qty\":${l.qty},")
                append("\"unitPrice\":${l.unitPrice}")
                append("}${if (i < d.orderLines.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"payments\": [")
            d.payments.forEachIndexed { i, p ->
                append("    {")
                append("\"paymentId\":\"${esc(p.paymentId)}\",")
                append("\"orderId\":\"${esc(p.orderId)}\",")
                append("\"amount\":${p.amount},")
                append("\"method\":\"${esc(p.method)}\",")
                append("\"paidAt\":${p.paidAt}")
                append("}${if (i < d.payments.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"invoices\": [")
            d.invoices.forEachIndexed { i, v ->
                append("    {")
                append("\"invoiceNo\":\"${esc(v.invoiceNo)}\",")
                append("\"orderId\":\"${esc(v.orderId)}\",")
                append("\"issuedAt\":${v.issuedAt},")
                append("\"total\":${v.total}")
                append("}${if (i < d.invoices.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"productOptions\": [")
            d.productOptions.forEachIndexed { i, o ->
                append("    {")
                append("\"optionId\":\"${esc(o.optionId)}\",")
                append("\"kind\":\"${o.kind.name}\",")
                append("\"value\":\"${esc(o.value)}\",")
                append("\"sortOrder\":${o.sortOrder},")
                append("\"active\":${o.active}")
                append("}${if (i < d.productOptions.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"stockAdjustments\": [")
            d.stockAdjustments.forEachIndexed { i, a ->
                append("    {")
                append("\"adjId\":\"${esc(a.adjId)}\",")
                append("\"sku\":\"${esc(a.sku)}\",")
                append("\"oldPhysical\":${a.oldPhysical},")
                append("\"newPhysical\":${a.newPhysical},")
                append("\"reason\":${a.reason?.let { "\"${esc(it)}\"" } ?: "null"},")
                append("\"actor\":\"${esc(a.actor)}\",")
                append("\"adjustedAt\":${a.adjustedAt}")
                append("}${if (i < d.stockAdjustments.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"settings\": [")
            d.settings.forEachIndexed { i, s ->
                append("    {")
                append("\"settingName\":\"${esc(s.settingName)}\",")
                append("\"value\":\"${esc(s.value)}\",")
                append("\"updatedAt\":${s.updatedAt}")
                append("}${if (i < d.settings.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ],")

            appendLine("  \"activityLog\": [")
            d.activityLog.forEachIndexed { i, e ->
                append("    {")
                append("\"logId\":\"${esc(e.logId)}\",")
                append("\"actor\":\"${esc(e.actor)}\",")
                append("\"action\":\"${esc(e.action)}\",")
                append("\"entityType\":\"${esc(e.entityType)}\",")
                append("\"entityId\":\"${esc(e.entityId)}\",")
                append("\"detail\":${e.detail?.let { "\"${esc(it)}\"" } ?: "null"},")
                append("\"loggedAt\":${e.loggedAt}")
                append("}${if (i < d.activityLog.lastIndex) "," else ""}")
                appendLine()
            }
            appendLine("  ]")

            appendLine("}")
        }
    }

    /** Escapes a value for the JSON above; a quote in a name must not break the file. */
    private fun esc(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }

    /** Restore the canonical dataset (used by the Test controls and by tests). */
    fun resetToSeed() {
        _data.value = SeedData.build()
    }

    // -------------------------------------------------------------- accounts ----

    /**
     * A-01 / A-12: sign in, then open the session.
     *
     * Returns the failure message so the screen can show exactly one of the
     * criterion's strings, or null on success.
     */
    fun attemptSignIn(email: String, password: String): String? {
        return when (val result = AccountOps.signIn(_data.value, email, password)) {
            is AccountOps.SignInResult.Failure -> result.message
            is AccountOps.SignInResult.Success -> {
                _session.value = result.account
                null
            }
        }
    }

    /** A-11: register, then sign in as the new customer (A-11 c8). */
    fun register(form: AccountOps.Registration, now: Long = System.currentTimeMillis()): String? {
        return when (val result = AccountOps.register(_data.value, form, now)) {
            is AccountOps.RegisterResult.Failure -> result.message
            is AccountOps.RegisterResult.Success -> {
                update { it.copy(accounts = it.accounts + result.account) }
                _session.value = result.account
                null
            }
        }
    }

    /** A-17 c2/c3: save the profile, keeping the session on the updated row. */
    fun updateProfile(
        fullName: String,
        handle: String,
        mobile: String,
        province: String,
        city: String,
        barangay: String,
        addressLine: String,
    ): String? {
        val account = _session.value ?: return AccountOps.INCOMPLETE_FIELDS
        return when (
            val result = AccountOps.updateProfile(
                account, fullName, handle, mobile, province, city, barangay, addressLine,
            )
        ) {
            is AccountOps.ProfileResult.Failure -> result.message
            is AccountOps.ProfileResult.Success -> {
                val updated = result.account
                update { data ->
                    data.copy(
                        accounts = data.accounts.map {
                            if (it.email.equals(updated.email, ignoreCase = true)) updated else it
                        },
                    )
                }
                _session.value = updated
                null
            }
        }
    }

    /** A-17 c4-c6: change the password, keeping the session live. */
    fun changePassword(current: String, newPassword: String, confirm: String): String? {
        val account = _session.value ?: return AccountOps.PASSWORD_MISMATCH
        return when (val result = AccountOps.changePassword(account, current, newPassword, confirm)) {
            is AccountOps.ProfileResult.Failure -> result.message
            is AccountOps.ProfileResult.Success -> {
                val updated = result.account
                update { data ->
                    data.copy(
                        accounts = data.accounts.map {
                            if (it.email.equals(updated.email, ignoreCase = true)) updated else it
                        },
                    )
                }
                _session.value = updated
                null
            }
        }
    }

    /** A-08 c4/c5: the owner's private note. */
    fun setReviewFlag(email: String, flag: String?) =
        update { OrderOps.setReviewFlag(it, email, flag) }

    // ----------------------------------------------------------------- cart -----

    /** A-14 c1: adding to the cart changes NO product. */
    fun addToCart(sku: String) {
        val email = _session.value?.email ?: return
        val product = _data.value.product(sku) ?: return
        update { data ->
            data.copy(
                carts = data.carts + (email.lowercase() to CartOps.add(
                    data.cart(email), sku, product.available,
                )),
            )
        }
    }

    /** A-14 c4: "+", capped at the live available count. Returns the message to show. */
    fun increaseCartQty(sku: String): String? {
        val email = _session.value?.email ?: return null
        val product = _data.value.product(sku) ?: return null
        val change = CartOps.increase(_data.value.cart(email), sku, product.available)
        update { data -> data.copy(carts = data.carts + (email.lowercase() to change.cart)) }
        return change.message
    }

    /** A-14 c4: "-". */
    fun decreaseCartQty(sku: String) {
        val email = _session.value?.email ?: return
        update { data ->
            data.copy(
                carts = data.carts + (email.lowercase() to CartOps.decrease(data.cart(email), sku)),
            )
        }
    }

    /** A-14 c6: Remove. */
    fun removeFromCart(sku: String) {
        val email = _session.value?.email ?: return
        update { data ->
            data.copy(
                carts = data.carts + (email.lowercase() to CartOps.remove(data.cart(email), sku)),
            )
        }
    }

    /** A-14 c2: the row's checkbox. */
    fun setCartSelected(sku: String, selected: Boolean) {
        val email = _session.value?.email ?: return
        update { data ->
            data.copy(
                carts = data.carts + (
                    email.lowercase() to CartOps.setSelected(data.cart(email), sku, selected)
                    ),
            )
        }
    }

    /**
     * A-14 c5: re-check every row against live stock.
     *
     * Called when the Cart opens, so a unit another customer took while this
     * customer browsed turns the row Sold Out on sight rather than at checkout.
     */
    fun refreshCart() {
        val email = _session.value?.email ?: return
        update { data ->
            data.copy(
                carts = data.carts + (
                    email.lowercase() to CartOps.normaliseSelection(data, data.cart(email))
                    ),
            )
        }
    }

    /** A-14 c7: empty the cart (used by the tests and by Remove on the last row). */
    fun clearCart() {
        val email = _session.value?.email ?: return
        update { data -> data.copy(carts = data.carts + (email.lowercase() to emptyList())) }
    }

    // --------------------------------------------------------------- checkout ---

    /** A-15 c6-c7: place the order from the ticked rows. */
    fun placeOrder(
        fulfillment: Fulfillment,
        recipientName: String,
        mobile: String,
        province: String,
        city: String,
        barangay: String,
        addressLine: String,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.PlaceResult {
        val account = _session.value
            ?: return OrderOps.PlaceResult.Refused("Sign in again to place your order.", _data.value)
        val rows = CartOps.refresh(_data.value, _data.value.cart(account.email))
        val selected = CartOps.selectedLines(rows)
        val result = OrderOps.placeOrder(
            data = _data.value,
            request = OrderOps.CheckoutRequest(
                customerEmail = account.email,
                lines = selected,
                fulfillment = fulfillment,
                recipientName = recipientName,
                mobile = mobile,
                province = province,
                city = city,
                barangay = barangay,
                addressLine = addressLine,
            ),
            // A-15 c5 / D.4: the CUSTOMER's offline state is the device's real
            // connectivity, never the owner's test switch. An owner who left the
            // switch on must not block a customer's checkout on a shared handset.
            offline = customerOffline,
            now = now,
        )
        // Route the swap through `update`, not a bare `_data.value =`: placing an
        // order appends an activity-log row, and only `update` seals it. Writing
        // the dataset directly left that row with a blank hash, which made
        // AuditChain.verify() report "was never sealed" and turned the Edit History
        // badge red after the very first customer order (v2.3 HCI audit, R.5).
        if (result is OrderOps.PlaceResult.Placed) {
            update { result.data }
        }
        return result
    }

    // ----------------------------------------------------- order transitions ----
    //
    // Every write below is refused while the OWNER's offline switch is on. The
    // refusal lives here rather than in each screen because "read-only" is a claim
    // the UI makes on every tab: a screen that forgot its own `enabled = !offline`
    // would silently break the promise, and Stock's Add/Edit/Adjust/Deactivate were
    // exactly that hole before. `ownerReadOnly` is false for a customer session, so
    // these gates can never block Customer Mode.

    /** A-04 c6: Confirm & Send Invoice. */
    fun sendInvoice(
        orderId: String,
        shippingFeeText: String,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.InvoiceResult {
        if (ownerReadOnly) {
            return OrderOps.InvoiceResult.Refused(OFFLINE_READ_ONLY, _data.value)
        }
        return commit(OrderOps.sendInvoice(_data.value, orderId, shippingFeeText, now))
    }

    /** A-04 c7 / A-16 c6: cancel, by either party. */
    fun cancelOrder(
        orderId: String,
        by: CancelledBy,
        reason: String,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.CancelResult {
        if (ownerReadOnly) {
            return OrderOps.CancelResult.Refused(OFFLINE_READ_ONLY, _data.value)
        }
        return commit(OrderOps.cancel(_data.value, orderId, by, reason, now))
    }

    /** A-04 c9: restore a cancelled order. */
    fun restoreOrder(
        orderId: String,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.CancelResult {
        if (ownerReadOnly) {
            return OrderOps.CancelResult.Refused(OFFLINE_READ_ONLY, _data.value)
        }
        return commit(OrderOps.restore(_data.value, orderId, now))
    }

    /** A-05 c2-c4: record a payment. */
    fun recordPayment(
        orderId: String,
        amountText: String,
        method: String,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.PaymentResult {
        if (ownerReadOnly) {
            return OrderOps.PaymentResult.Refused(OFFLINE_READ_ONLY, _data.value)
        }
        return commit(OrderOps.recordPayment(_data.value, orderId, amountText, method, now))
    }

    /** A-05 c6: Unmark Paid, before dispatch. */
    fun unmarkPaid(
        orderId: String,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.UnmarkResult {
        if (ownerReadOnly) {
            return OrderOps.UnmarkResult.Refused(OFFLINE_READ_ONLY, _data.value)
        }
        return commit(OrderOps.unmarkPaid(_data.value, orderId, now))
    }

    /**
     * A-05 c6: remove one recorded payment row and recompute the order.
     *
     * The second half of the trapped-state fix: an order whose rows already sum to
     * its total has a genuinely zero balance, so unmarking alone cannot free it.
     * Voiding the mistaken row is the way out, and it recomputes Paid / Balance /
     * paymentStatus from the survivors.
     */
    fun voidPayment(
        orderId: String,
        paymentId: String,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.UnmarkResult {
        if (ownerReadOnly) return OrderOps.UnmarkResult.Refused(OFFLINE_READ_ONLY, _data.value)
        return commit(OrderOps.voidPayment(_data.value, orderId, paymentId, now))
    }

    /**
     * A-05 c4: validate a payment without writing it.
     *
     * The "Mark as Paid" dialog is shown over the result of this call, so `Close`
     * aborts with zero database writes - the screen never has to unwind a payment
     * it already committed.
     */
    fun checkPayment(
        orderId: String,
        amountText: String,
        method: String,
    ): OrderOps.PaymentCheck = OrderOps.previewPayment(_data.value, orderId, amountText, method)

    /** A-06 c5-c7: combine held orders. */
    fun combineOrders(
        orderIds: List<String>,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.CombineResult {
        if (ownerReadOnly) {
            return OrderOps.CombineResult.Refused(OFFLINE_READ_ONLY, _data.value)
        }
        return commit(OrderOps.combine(_data.value, orderIds, now))
    }

    /** A-07 c3-c5: mark packed. */
    fun markPacked(
        orderId: String,
        checkedLineIds: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.PackResult {
        if (ownerReadOnly) {
            return OrderOps.PackResult.Refused(OFFLINE_READ_ONLY, _data.value)
        }
        return commit(OrderOps.markPacked(_data.value, orderId, checkedLineIds, now))
    }

    /** A-07 c6-c9: route, tracking number and status. */
    fun updateShipping(
        orderId: String,
        courier: String?,
        trackingNo: String?,
        status: ShippingStatus?,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.ShipResult {
        // A-10 c8: a dispatch moves BOTH physical and reserved stock, so it is a
        // write like any other and offline must refuse it.
        if (ownerReadOnly) return OrderOps.ShipResult.Refused(OFFLINE_READ_ONLY, _data.value)
        return commit(
            OrderOps.updateShipping(_data.value, orderId, courier, trackingNo, status, now),
        )
    }

    /** A-04 c5: edit an unsettled order. */
    fun editOrderQuantities(
        orderId: String,
        quantities: Map<String, Int>,
        now: Long = System.currentTimeMillis(),
    ): OrderOps.EditResult {
        if (ownerReadOnly) {
            return OrderOps.EditResult.Refused(OFFLINE_READ_ONLY, _data.value)
        }
        return commit(OrderOps.editQuantities(_data.value, orderId, quantities, now))
    }

    /**
     * Swaps in whatever dataset the transition produced.
     *
     * A refusal carries its input unchanged, so this is safe to call for both
     * outcomes: a blocked transition writes nothing because the value it hands
     * back IS the current one. Written once so no transition can forget the swap -
     * a forgotten swap would show a success message over unchanged data, which is
     * exactly the defect this project has hit before.
     *
     * IAS R.5: the swap goes through [update], never a bare `_data.value =`, because
     * every transition here - send invoice, record payment, cancel, combine, pack,
     * adjust - appends an activity-log row, and `update` is what seals it. Assigning
     * the dataset directly left those rows with a blank hash, so AuditChain.verify()
     * reported "was never sealed" and the Edit History badge went red the moment an
     * owner sent the first invoice (v2.3 HCI audit R.5 check).
     */
    private fun <T : OrderOps.HasData> commit(result: T): T {
        if (result.data !== _data.value) update { result.data }
        return result
    }

    // ------------------------------------------------------------- catalogue ----

    /**
     * A-03 c1-c2: save a product.
     *
     * The validation messages are the criteria's, and a refused save writes
     * nothing: `duplicateSkuError` returns the message and the caller shows it
     * without this method being reached at all.
     */
    fun saveProduct(product: Product, changedFields: String, now: Long = System.currentTimeMillis()) {
        // A-10 c8: offline is read-only for the catalogue too.
        if (isOffline) return
        update { data ->
            val existing = data.products.any { it.sku.equals(product.sku, ignoreCase = true) }
            data.copy(
                products = if (existing) {
                    data.products.map {
                        if (it.sku.equals(product.sku, ignoreCase = true)) product else it
                    }
                } else {
                    data.products + product
                },
                activityLog = data.activityLog + CatalogueOps.log(
                    action = "Saved ${product.sku}",
                    sku = product.sku,
                    detail = changedFields,
                    now = now,
                    index = data.activityLog.size + 1,
                ),
            )
        }
    }

    /** A-03 c6: adjust stock. A blank reason is allowed and recorded as absent. */
    fun adjustStock(
        sku: String,
        newPhysical: Int,
        reason: String?,
        now: Long = System.currentTimeMillis(),
    ) {
        // A-03 c6 is a stock MOVEMENT; offline must refuse it like any other write.
        if (isOffline) return
        update { data ->
            val product = data.product(sku) ?: return@update data
            val updated = product.copy(physical = newPhysical)
            data.copy(
                products = data.products.map { if (it.sku == product.sku) updated else it },
                stockAdjustments = data.stockAdjustments + StockAdjustment(
                    adjId = "ADJ-%04d".format(data.stockAdjustments.size + 1),
                    sku = product.sku,
                    oldPhysical = product.physical,
                    newPhysical = newPhysical,
                    reason = reason?.takeIf { it.isNotBlank() },
                    adjustedAt = now,
                ),
                activityLog = data.activityLog + CatalogueOps.log(
                    action = "Adjusted ${product.sku} to $newPhysical on hand",
                    sku = product.sku,
                    detail = "reason: ${reason?.takeIf { it.isNotBlank() } ?: "not recorded"}",
                    now = now,
                    index = data.activityLog.size + 1,
                ),
            )
        }
    }

    /** A-03 c7: deactivate, never delete (past orders keep resolving). */
    fun setProductActive(sku: String, active: Boolean, now: Long = System.currentTimeMillis()) {
        if (isOffline) return
        update { data ->
            val product = data.product(sku) ?: return@update data
            val updated = product.copy(
                active = active,
                deactivatedAt = if (active) null else now,
            )
            data.copy(
                products = data.products.map { if (it.sku == product.sku) updated else it },
                activityLog = data.activityLog + CatalogueOps.log(
                    action = if (active) "Reactivated ${product.sku}" else "Deactivated ${product.sku}",
                    sku = product.sku,
                    detail = if (active) "hidden from catalog off" else "hidden from the customer catalog",
                    now = now,
                    index = data.activityLog.size + 1,
                ),
            )
        }
    }

    /** A-10 c4: replace the managed character / category lists. */
    fun saveProductOptions(options: List<ProductOption>) {
        if (isOffline) return
        update { it.copy(productOptions = options) }
    }

    /**
     * A-10 c4 / A.6: remove ONE managed option, refusing while products still use it.
     *
     * A removable chip that silently deleted an in-use value would leave those
     * products carrying a category no longer offered - the product form could not
     * reproduce it, and the Shop's chip (which is intersected with the catalogue)
     * would vanish while the rows still said it. So the removal is refused and the
     * refusal NAMES the products, which is what makes the warning actionable.
     *
     * Returns the message to show, or null on success.
     */
    fun removeProductOption(value: String, kind: ph.edu.umak.momompurin.data.entity.OptionKind): String? {
        if (isOffline) return OFFLINE_READ_ONLY
        val inUse = _data.value.products.filter { product ->
            val field = when (kind) {
                ph.edu.umak.momompurin.data.entity.OptionKind.CATEGORY -> product.category
                ph.edu.umak.momompurin.data.entity.OptionKind.CHARACTER -> product.`character`
            }
            field.equals(value.trim(), ignoreCase = true)
        }
        if (inUse.isNotEmpty()) {
            val names = inUse.take(3).joinToString(", ") { it.sku }
            val more = if (inUse.size > 3) " and ${inUse.size - 3} more" else ""
            return "\"$value\" is used by $names$more. Change those products first."
        }
        update { data ->
            data.copy(
                productOptions = CatalogueOps.removeOption(data.productOptions, value, kind),
            )
        }
        return null
    }

    /** A-10 c2: write a shop setting. Validation happens at the caller. */
    fun setSetting(name: String, value: String, now: Long = System.currentTimeMillis()) {
        if (isOffline) return
        update { data ->
            val old = data.setting(name)
            val replaced = if (old == null) {                data.settings + ph.edu.umak.momompurin.data.entity.Setting(name, value)
            } else {
                data.settings.map { if (it.settingName == name) it.copy(value = value, updatedAt = now) else it }
            }
            data.copy(
                settings = replaced,
                activityLog = data.activityLog + CatalogueOps.log(
                    action = "Changed $name",
                    sku = name,
                    detail = "${old ?: "\u2014"} -> $value",
                    now = now,
                    index = data.activityLog.size + 1,
                ),
            )
        }
    }

    companion object {
        /** A-10 c8: the message a write refused by Offline Mode shows. */
        const val OFFLINE_READ_ONLY =
            "Read-only while offline. Turn Offline Mode off to save."

        /** A-10 c6: a successful local backup. */
        const val BACKUP_SAVED = "Backup saved."

        /** A-10 c3: a rate outside 0-100. */
        const val INVALID_RATE = "Enter a rate between 0 and 100."
    }
}
