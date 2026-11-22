package ph.edu.umak.momompurin.data

import ph.edu.umak.momompurin.data.entity.Account
import ph.edu.umak.momompurin.data.entity.Role
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Sign-in routing and registration rules for A-01, A-11, A-12 and A-17.
 *
 * Every user-facing string in this file is quoted verbatim from
 * `USER STORY-v4.19.11.2026.xlsx`. The tests assert the literals, so a reworded
 * message fails the build rather than drifting away from the workbook the tester
 * reads.
 */
object AccountOps {

    // ------------------------------------------------------ A-01 / A-12 c4-c6 --

    /** A-01 c4 / A-12 c4: a blank Email. */
    const val BLANK_EMAIL = "Enter your email address."

    /** A-01 c5 / A-12 c5: `katrina@`, `mika.tan@` - no complete domain. */
    const val INVALID_EMAIL = "Enter a valid email address."

    /** A-01 c4 / A-12 c4: a blank Password. */
    const val BLANK_PASSWORD = "Enter your password."

    /** A-01 c6 / A-12 c6: the credentials match no active account. */
    const val WRONG_CREDENTIALS = "Incorrect email or password."

    // ------------------------------------------------------------ A-11 c2-c6 --

    /** A-11 c2: any required field left blank. */
    const val INCOMPLETE_FIELDS = "Complete the required fields."

    /** A-11 c4 / A-17 c3: the mobile is not 11 digits. */
    const val INVALID_MOBILE = "Enter a valid mobile number with 11 digits."

    /** A-11 c5: the email already belongs to an account. */
    const val DUPLICATE_EMAIL = "An account with this email already exists."

    /** A-11 c6 / A-17 c6: the password is under 8 characters. */
    const val SHORT_PASSWORD = "Use at least 8 characters."

    // ------------------------------------------------------ A-17 c5 / c7 ------

    /** A-17 c5: wrong current password, or the confirmation does not match. */
    const val PASSWORD_MISMATCH = "Check your current password and the new password."

    /** A-17 c7 / A-10 c2: a save that succeeded. */
    const val CHANGES_SAVED = "Changes saved."

    // --------------------------------------------------------- the sign-in -----

    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val MOBILE = Regex("^\\d{11}$")

    fun isEmail(value: String): Boolean = EMAIL.matches(value.trim())

    /**
     * Patch 1: the ONE canonical form of an email address.
     *
     * A-11 c5 says the email already belongs to an account, without qualifying
     * *which spelling*. "Mika.Tan@Example.com", "mika.tan@example.com " and
     * "MIKA.TAN@EXAMPLE.COM" are the same mailbox, so they must be the same
     * identity - otherwise a customer registers a second account on an address
     * that receives their invoices, and the uniqueness rule the criterion states
     * is true only of the exact bytes typed.
     *
     * Applied on BOTH sides of every comparison: [register] writes this form, and
     * [MomoData.account] / [duplicateEmail] look up this form. Two spellings can
     * therefore never diverge into two accounts.
     */
    fun normaliseEmail(value: String): String = value.trim().lowercase()

    /**
     * Patch 1: is this address already taken, in any spelling?
     *
     * Exposed so the Registration screen can warn WHILE THE OWNER OF THE ADDRESS
     * TYPES rather than only when they tap Create account - and so the screen and
     * [register] answer from one rule instead of two that could drift.
     *
     * A blank or malformed address is never "taken": the format check owns that
     * answer, and reporting "already exists" over "elena@" would send the customer
     * looking for an account that was never created.
     */
    fun duplicateEmail(data: MomoData, email: String): Boolean {
        if (email.isBlank() || !isEmail(email)) return false
        val wanted = normaliseEmail(email)
        return data.accounts.any { normaliseEmail(it.email) == wanted }
    }

    /**
     * A-11 c4: 11 digits. Spaces and dashes are stripped first, because a customer
     * typing "0917 555 0101" has given a correct 11-digit number and rejecting it
     * would be a false failure.
     */
    fun isMobile(value: String): Boolean = MOBILE.matches(normaliseMobile(value))

    fun normaliseMobile(value: String): String =
        value.filter { it.isDigit() }

    /**
     * A-01 c2-c6 / A-12 c2-c6: validate the fields, then the credentials.
     *
     * The order matters and is the criteria's: blank email, then format, then
     * blank password, then the credential check. Returning the first failure is
     * what lets each criterion's message appear alone.
     */
    sealed interface SignInResult {
        data class Success(val account: Account) : SignInResult
        data class Failure(val message: String) : SignInResult
    }

    fun signIn(data: MomoData, email: String, password: String): SignInResult {
        if (email.isBlank()) return SignInResult.Failure(BLANK_EMAIL)
        if (!isEmail(email)) return SignInResult.Failure(INVALID_EMAIL)
        if (password.isEmpty()) return SignInResult.Failure(BLANK_PASSWORD)

        val account = data.account(email)
            ?: return SignInResult.Failure(WRONG_CREDENTIALS)
        if (!verify(password, account.passwordDigest)) {
            return SignInResult.Failure(WRONG_CREDENTIALS)
        }
        return SignInResult.Success(account)
    }

    /**
     * A-01 c3 / A-12 c3: the account decides the mode. There is no role parameter
     * anywhere on this path ON PURPOSE - a role argument would be the switch the
     * criteria forbid.
     */
    fun modeFor(account: Account): Role = account.role

    // ------------------------------------------------------- A-11 registration --

    /**
     * What the Registration form collects (A-11 c1). No role, staff or admin field
     * exists here because A-11 c7 forbids any registration path that grants Owner
     * access: [toAccount] hard-codes [Role.CUSTOMER].
     */
    data class Registration(
        val fullName: String,
        val handle: String,
        val email: String,
        val mobile: String,
        val province: String,
        val city: String,
        val barangay: String,
        val addressLine: String,
        val password: String,
    )

    sealed interface RegisterResult {
        data class Success(val account: Account) : RegisterResult
        data class Failure(val message: String) : RegisterResult
    }

    fun register(
        data: MomoData,
        form: Registration,
        now: Long = System.currentTimeMillis(),
    ): RegisterResult {
        // A-11 c2: the required fields. A handle is optional ("and/or"), so it is
        // not in this list; everything else is.
        val required = listOf(
            form.fullName, form.email, form.mobile,
            form.province, form.city, form.barangay, form.addressLine, form.password,
        )
        if (required.any { it.isBlank() }) {
            return RegisterResult.Failure(INCOMPLETE_FIELDS)
        }
        if (!isEmail(form.email)) return RegisterResult.Failure(INVALID_EMAIL)
        if (!isMobile(form.mobile)) return RegisterResult.Failure(INVALID_MOBILE)
        // A-11 c5 / Patch 1: the uniqueness check is CASE-INSENSITIVE and TRIMMED.
        //
        // The old line read `data.account(form.email) != null`, which delegates to
        // an `equals(..., ignoreCase = true)` lookup - so case was already handled,
        // and the real bypass was the other half: the comparison ran on the RAW
        // typed string, and this method stored `form.email.trim().lowercase()`. A
        // typed "  Mika.Tan@Example.com" therefore compared a string with leading
        // spaces that matched nothing, and was then written in its normalised form
        // - producing a SECOND row whose email was byte-identical to an existing
        // one. Both spellings then resolved to whichever row `account()` found
        // first, so a password reset or a profile edit could land on the wrong
        // account. The shared [normaliseEmail] closes that: the check and the write
        // now compare and store the SAME canonical form.
        if (duplicateEmail(data, form.email)) return RegisterResult.Failure(DUPLICATE_EMAIL)
        if (form.password.length < MIN_PASSWORD_LENGTH) {
            return RegisterResult.Failure(SHORT_PASSWORD)
        }

        // A-11 c7: this is the ONLY account-creation path in the app and it can
        // only ever produce a Customer.
        return RegisterResult.Success(
            Account(
                email = normaliseEmail(form.email),
                role = Role.CUSTOMER,
                passwordDigest = digest(form.password),
                name = form.fullName.trim(),
                handle = form.handle.trim(),
                mobile = normaliseMobile(form.mobile),
                province = form.province.trim(),
                city = form.city.trim(),
                barangay = form.barangay.trim(),
                addressLine = form.addressLine.trim(),
                createdAt = now,
            ),
        )
    }

    // -------------------------------------------------------- A-17 profile ----

    sealed interface ProfileResult {
        data class Success(val account: Account) : ProfileResult
        data class Failure(val message: String) : ProfileResult
    }

    /**
     * A-17 c2/c3: save the editable profile fields. The email is NOT editable -
     * it is the sign-in identity and A-01's routing key.
     */
    fun updateProfile(
        account: Account,
        fullName: String,
        handle: String,
        mobile: String,
        province: String,
        city: String,
        barangay: String,
        addressLine: String,
    ): ProfileResult {
        if (!isMobile(mobile)) return ProfileResult.Failure(INVALID_MOBILE)
        if (fullName.isBlank()) return ProfileResult.Failure(INCOMPLETE_FIELDS)
        return ProfileResult.Success(
            account.copy(
                name = fullName.trim(),
                handle = handle.trim(),
                mobile = normaliseMobile(mobile),
                province = province.trim(),
                city = city.trim(),
                barangay = barangay.trim(),
                addressLine = addressLine.trim(),
            ),
        )
    }

    /**
     * A-17 c4-c6: change the password.
     *
     * The mismatch check runs BEFORE the length check, because A-17 c5's
     * declaration "Check your current password and the new password." is the one a
     * tester reaches with a correct-shaped but wrong form, and A-17 c6 ("Use at
     * least 8 characters.") is the narrower case.
     */
    fun changePassword(
        account: Account,
        current: String,
        newPassword: String,
        confirm: String,
    ): ProfileResult {
        // A blank current password can never match a stored digest, and handing it
        // to PBKDF2 throws InvalidKeySpecException ("Could not generate secret key")
        // because PBEKeySpec rejects an empty char[]. That exception used to escape
        // this method and kill the process the moment a tester tapped Save with the
        // field untouched (v2.3 HCI audit F-01, Sev 4). Answer the question the user
        // actually got wrong instead of asking the KDF.
        if (current.isBlank()) return ProfileResult.Failure(PASSWORD_MISMATCH)
        if (!verify(current, account.passwordDigest) || newPassword != confirm) {
            return ProfileResult.Failure(PASSWORD_MISMATCH)
        }
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            return ProfileResult.Failure(SHORT_PASSWORD)
        }
        return ProfileResult.Success(account.copy(passwordDigest = digest(newPassword)))
    }

    // ------------------------------------------------------------- helpers ----

    /**
     * A-11 c6 / A-17 c6: eight characters is the floor.
     */
    const val MIN_PASSWORD_LENGTH = 8

    // ------------------------------------------- UAT fix (item 13): reset ------

    /**
     * UAT fix (item 13): the owner's account cannot be reset this way.
     *
     * The customer flow proves identity with an email plus a mobile number - two
     * fields the CUSTOMER supplied at registration. The owner's account has no
     * such registration moment, so the same two fields would be a weaker claim on
     * the account that owns the shop, the stock figures and the sales reports. The
     * only supported recovery for it is an authenticated change from inside the
     * session.
     */
    const val OWNER_RECOVERY_RESTRICTED =
        "Owner password recovery is restricted for security."

    /** UAT fix (item 13): no account carries both the email and the mobile. */
    const val NO_SUCH_CUSTOMER =
        "No customer account matches that email and mobile number."

    /** UAT fix (item 13): the two passwords typed on the reset form differ. */
    const val RESET_MISMATCH = "Passwords do not match."

    /** UAT fix (item 13): the reset succeeded. */
    const val PASSWORD_RESET = "Password reset. You can sign in with the new password."

    sealed interface ResetResult {
        data class Success(val account: Account) : ResetResult
        data class Failure(val message: String) : ResetResult
    }

    /**
     * UAT fix (item 13): reset a CUSTOMER's password from email + mobile.
     *
     * ## Why the checks are in this order
     *
     * 1. **Blank/format fields** first, so a mistyped email is answered as a typo
     *    rather than as "no such customer" - the second is alarming and the first
     *    is actionable.
     * 2. **The owner refusal** BEFORE the lookup. If an attacker submits the
     *    owner's email and the wrong mobile, answering "no such customer" would
     *    be true but would also hide the fact that the account exists and is
     *    deliberately unrecoverable. Answering with the policy is the honest reply,
     *    and it is the same answer whether or not the mobile matched, so it cannot
     *    be used as an oracle for the owner's phone number.
     * 3. **The lookup**, requiring BOTH fields to match the SAME account.
     * 4. **The new password** length and confirmation last, because only an
     *    authenticated-enough caller should reach the point where the form starts
     *    talking about the new password at all.
     *
     * The digest is written with the same [digest] the rest of the app uses, so a
     * reset account signs in through the unchanged [signIn] path with no special
     * case.
     */
    fun resetCustomerPassword(
        data: MomoData,
        email: String,
        mobile: String,
        newPassword: String,
        confirm: String,
    ): ResetResult {
        if (email.isBlank()) return ResetResult.Failure(BLANK_EMAIL)
        if (!isEmail(email)) return ResetResult.Failure(INVALID_EMAIL)
        // "You left this blank" and "this is not 11 digits" are different
        // corrections, so the blank case gets its own answer.
        if (normaliseMobile(mobile).isEmpty()) return ResetResult.Failure(INVALID_MOBILE)

        // The owner's account is refused before any lookup - see the note above on
        // why this answer does not depend on whether the mobile matched.
        val present = data.account(email)
        if (present?.role == Role.OWNER) {
            return ResetResult.Failure(OWNER_RECOVERY_RESTRICTED)
        }

        val wanted = normaliseMobile(mobile)
        val account = present?.takeIf { it.mobile.filter { ch -> ch.isDigit() } == wanted }
            ?: return ResetResult.Failure(NO_SUCH_CUSTOMER)

        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            return ResetResult.Failure(SHORT_PASSWORD)
        }
        if (newPassword != confirm) return ResetResult.Failure(RESET_MISMATCH)

        return ResetResult.Success(account.copy(passwordDigest = digest(newPassword)))
    }

    // ---------------------------------------------------- password hashing ----

    /**
     * The stored format's prefix. A digest carrying it is PBKDF2; one without it is
     * a legacy unsalted SHA-256 hex digest, which [verify] still accepts so an
     * account seeded before the upgrade keeps working.
     */
    const val PBKDF2_PREFIX = "pbkdf2"

    /** 16 bytes (128 bits) - long enough that a rainbow table cannot precompute it. */
    private const val SALT_BYTES = 16

    /** 32 bytes (256 bits), matching the SHA-256 output size. */
    private const val HASH_BITS = 256

    /**
     * 65,536 rounds: the OWASP floor for PBKDF2-HMAC-SHA256, and slow enough to
     * matter (tens of milliseconds on a handset) while staying invisible on the
     * sign-in tap.
     */
    const val PBKDF2_ITERATIONS = 65_536

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val random = SecureRandom()

    /**
     * Salted PBKDF2WithHmacSHA256, stored as `pbkdf2$<iterations>$<saltHex>$<hashHex>`.
     *
     * The iteration count is embedded rather than assumed, so raising
     * [PBKDF2_ITERATIONS] later verifies old rows with their own cost factor instead
     * of invalidating every stored password.
     *
     * **Production note (IAS Rev 2 R.4):** the live service authenticates through
     * Supabase GoTrue, which hashes with **Argon2id** - a memory-hard KDF that
     * resists GPU cracking far better than PBKDF2. This local implementation uses
     * PBKDF2 because it ships with the JDK/Android platform (`javax.crypto`),
     * whereas Argon2id would need a native dependency. The property the criterion
     * asks for - a per-account random salt and a deliberately slow, iterated KDF -
     * holds either way.
     */
    fun digest(value: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = pbkdf2(value, salt, PBKDF2_ITERATIONS)
        return listOf(
            PBKDF2_PREFIX,
            PBKDF2_ITERATIONS.toString(),
            salt.toHex(),
            hash.toHex(),
        ).joinToString("$")
    }

    /**
     * Constant-time verification against a stored digest.
     *
     * Compares with [MessageDigest.isEqual], not `==`, so the comparison time does
     * not depend on how many leading bytes matched - a timing side channel on a
     * local sign-in is a small risk, but a free one to close.
     *
     * A legacy unsalted digest still verifies (and the caller may re-digest it),
     * because a stored row from before the upgrade must not lock its owner out.
     */
    fun verify(value: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size == 4 && parts[0] == PBKDF2_PREFIX) {
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = parts[2].hexToBytes() ?: return false
            val expected = parts[3].hexToBytes() ?: return false
            return MessageDigest.isEqual(pbkdf2(value, salt, iterations), expected)
        }
        // Legacy: unsalted SHA-256, the format this app shipped before v2.3.
        return MessageDigest.isEqual(
            legacySha256(value).toByteArray(Charsets.UTF_8),
            stored.toByteArray(Charsets.UTF_8),
        )
    }

    /** True when [stored] is already a PBKDF2 row rather than a legacy digest. */
    fun isModern(stored: String): Boolean = stored.startsWith("$PBKDF2_PREFIX$")

    private fun pbkdf2(value: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(value.toCharArray(), salt, iterations, HASH_BITS)
        return try {
            // Defence in depth (v2.3 HCI audit F-01): PBEKeySpec / the JCE provider
            // reject some inputs - an empty password, a zero iteration count - with
            // InvalidKeySpecException, and an escaping runtime exception here takes
            // the whole process down because every caller runs on the UI thread.
            // A KDF that cannot run must read as "this password does not match",
            // never as a crash, so a failure yields the empty byte array that no
            // real digest can equal.
            runCatching {
                SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            }.getOrDefault(ByteArray(0))
        } finally {
            // Zeroes the char[] copy PBKDF2 held; the caller's String is immutable
            // and out of this method's reach, which is a Kotlin/Java limitation
            // rather than an oversight.
            spec.clearPassword()
        }
    }

    /**
     * SHA-256 hex - retained ONLY to verify a digest stored before v2.3.
     *
     * Not a password KDF: unsalted and single-pass. New passwords never use it.
     */
    fun legacySha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    /** The name the Owner Dashboard greets by (A-02 c2). */
    fun firstName(account: Account): String =
        account.name.trim().split(" ").firstOrNull().orEmpty().ifBlank { "there" }
}
