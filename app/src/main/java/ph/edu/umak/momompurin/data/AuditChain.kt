package ph.edu.umak.momompurin.data

import ph.edu.umak.momompurin.data.entity.ActivityLogEntry
import java.security.MessageDigest

/**
 * The SHA-256 hash chain that makes the activity log append-only (IAS R.5).
 *
 * ## Why a chain rather than a table of rows
 *
 * An `activityLog` list proves nothing on its own: anyone who can write the store
 * can rewrite a row, or drop one, and every screen still renders a plausible
 * history. A hash chain makes a rewrite *detectable*: each row's digest covers its
 * own content **and the previous row's digest**, so
 *
 *  - editing row 3 changes its digest, which no longer matches the `prevHash` of
 *    row 4, so verification fails from row 4 onward;
 *  - deleting row 3 leaves row 4 pointing at a digest that is no longer in the
 *    chain at all;
 *  - re-ordering rows breaks the same link.
 *
 * This is tamper *evidence*, not tamper *prevention* - an attacker who recomputes
 * every subsequent digest can forge a clean chain. That is the honest property a
 * single-device local store can offer, and the one the audit badge claims.
 *
 * ## Determinism
 *
 * The digest input is the exact pipe-delimited string
 * `prevHash|logId|actor|action|entityType|entityId|detail|loggedAt`, with a null
 * detail rendered as the empty string. Nothing else may enter it: a field added to
 * the hash would invalidate every stored row on the next release.
 */
object AuditChain {

    /** The separator. A pipe cannot appear in a generated id and is escaped in prose. */
    private const val SEP = "|"

    /** The exact pre-image of a row's digest. Public so a test can assert it. */
    fun preImage(entry: ActivityLogEntry, prevHash: String): String = buildString {
        append(prevHash)
        append(SEP); append(entry.logId)
        append(SEP); append(entry.actor)
        append(SEP); append(entry.action)
        append(SEP); append(entry.entityType)
        append(SEP); append(entry.entityId)
        append(SEP); append(entry.detail.orEmpty())
        append(SEP); append(entry.loggedAt)
    }

    /** SHA-256 hex of [preImage]. */
    fun hashOf(entry: ActivityLogEntry, prevHash: String): String =
        sha256(preImage(entry, prevHash))

    /**
     * Seal a whole log in order, returning rows whose `prevHash` and `hash` are
     * consistent.
     *
     * Idempotent on an already-sealed list: re-sealing recomputes the same digests
     * because every input is derived from the row itself.
     */
    fun seal(entries: List<ActivityLogEntry>): List<ActivityLogEntry> {
        var prev = ActivityLogEntry.GENESIS
        return entries.map { entry ->
            val hash = hashOf(entry, prev)
            val sealed = entry.copy(prevHash = prev, hash = hash)
            prev = hash
            sealed
        }
    }

    /**
     * Append one row to an already-sealed log, sealing only the new row.
     *
     * Cheaper than re-sealing the whole list on every write, and it keeps the
     * existing rows' digests byte-identical - which matters because a re-seal that
     * *changed* an old digest would make a legitimate append look like tampering.
     */
    fun append(
        entries: List<ActivityLogEntry>,
        entry: ActivityLogEntry,
    ): List<ActivityLogEntry> {
        val prev = entries.lastOrNull()?.hash?.takeIf { it.isNotEmpty() }
            ?: entries.lastOrNull()?.let { hashOf(it, it.prevHash) }
            ?: ActivityLogEntry.GENESIS
        return entries + entry.copy(prevHash = prev, hash = hashOf(entry, prev))
    }

    /** The outcome of a verification pass, so the badge can state the reason. */
    data class Verification(
        val valid: Boolean,
        val entries: Int,
        /** The first row that failed, or null when [valid]. */
        val failedAt: String? = null,
        /** Why it failed: a re-hash mismatch, a broken link, or an unsealed row. */
        val reason: String? = null,
    )

    /**
     * Recompute the chain and report the first break.
     *
     * Three distinct failures are reported separately because they mean different
     * things to a reader: an unsealed row (a write path forgot to seal), a digest
     * that no longer matches its own content (the row was edited), and a link that
     * does not match the previous digest (a row was removed or re-ordered).
     */
    fun verify(entries: List<ActivityLogEntry>): Verification {
        var prev = ActivityLogEntry.GENESIS
        entries.forEachIndexed { index, entry ->
            if (entry.prevHash != prev) {
                return Verification(
                    valid = false,
                    entries = entries.size,
                    failedAt = entry.logId,
                    reason = "Entry ${index + 1} (${entry.logId}) does not link to the " +
                        "entry before it.",
                )
            }
            if (entry.hash.isEmpty()) {
                return Verification(
                    valid = false,
                    entries = entries.size,
                    failedAt = entry.logId,
                    reason = "Entry ${index + 1} (${entry.logId}) was never sealed.",
                )
            }
            val expected = hashOf(entry, prev)
            if (entry.hash != expected) {
                return Verification(
                    valid = false,
                    entries = entries.size,
                    failedAt = entry.logId,
                    reason = "Entry ${index + 1} (${entry.logId}) was modified after " +
                        "it was written.",
                )
            }
            prev = entry.hash
        }
        return Verification(valid = true, entries = entries.size)
    }

    /** The one-line badge text the Edit History screen shows. */
    fun badgeText(verification: Verification): String = when {
        verification.valid ->
            "Audit chain verified \u00B7 ${verification.entries} entries (SHA-256)"
        else ->
            "Audit chain broken at ${verification.failedAt ?: "an entry"} \u00B7 " +
                "${verification.entries} entries"
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
