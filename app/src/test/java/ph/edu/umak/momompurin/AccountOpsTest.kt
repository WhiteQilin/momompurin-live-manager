package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.umak.momompurin.data.AccountOps
import ph.edu.umak.momompurin.data.MomoData
import ph.edu.umak.momompurin.data.SeedData
import ph.edu.umak.momompurin.data.entity.Role

/**
 * A-01 Sign In, A-11 Registration, A-12 Sign In (Customer) and A-17's password
 * rules - every criterion whose observable effect is a message or a routing
 * decision.
 *
 * Both Sign In components share ONE screen, so they share one set of rules; the
 * tests are grouped by component so a failure names the criterion that broke.
 */
class AccountOpsTest {

    private val data = SeedData.build()

    // =========================================================================
    // A-01 / A-12: the shared Sign In screen
    // =========================================================================

    @Test
    fun a01_owner_signs_in_and_is_routed_to_owner_mode() {
        // A-01 c3's GWT: katrinajj254@gmail.com / katrina@ -> Owner Mode.
        val result = AccountOps.signIn(data, "katrinajj254@gmail.com", "katrina@")
        assertTrue(result is AccountOps.SignInResult.Success)
        val account = (result as AccountOps.SignInResult.Success).account
        assertEquals(Role.OWNER, AccountOps.modeFor(account))
    }

    @Test
    fun a12_customer_signs_in_on_the_same_screen_and_is_routed_to_customer_mode() {
        // A-12 c3's GWT: mika.tan@example.com / customer123 -> Customer Mode.
        val result = AccountOps.signIn(data, "mika.tan@example.com", "customer123")
        assertTrue(result is AccountOps.SignInResult.Success)
        val account = (result as AccountOps.SignInResult.Success).account
        assertEquals(Role.CUSTOMER, AccountOps.modeFor(account))
    }

    @Test
    fun a01_blank_email_shows_its_own_message() {
        val result = AccountOps.signIn(data, "", "katrina@")
        assertEquals(
            AccountOps.BLANK_EMAIL,
            (result as AccountOps.SignInResult.Failure).message,
        )
        assertEquals("Enter your email address.", AccountOps.BLANK_EMAIL)
    }

    @Test
    fun a01_invalid_email_format_is_refused_before_the_password_check() {
        // `katrina@` has no complete domain (A-01 c5).
        val result = AccountOps.signIn(data, "katrina@", "katrina@")
        assertEquals(
            AccountOps.INVALID_EMAIL,
            (result as AccountOps.SignInResult.Failure).message,
        )
        assertEquals("Enter a valid email address.", AccountOps.INVALID_EMAIL)
    }

    @Test
    fun a12_invalid_customer_email_format_is_also_refused() {
        // A-12 c5's own example: `mika.tan@`.
        val result = AccountOps.signIn(data, "mika.tan@", "customer123")
        assertEquals(
            AccountOps.INVALID_EMAIL,
            (result as AccountOps.SignInResult.Failure).message,
        )
    }

    @Test
    fun a01_blank_password_shows_its_own_message() {
        val result = AccountOps.signIn(data, "katrinajj254@gmail.com", "")
        assertEquals(
            AccountOps.BLANK_PASSWORD,
            (result as AccountOps.SignInResult.Failure).message,
        )
        assertEquals("Enter your password.", AccountOps.BLANK_PASSWORD)
    }

    @Test
    fun a01_wrong_password_is_the_credentials_message_not_a_field_error() {
        // A-01 c6: katrinajj254@gmail.com with "katrina" (no @).
        val result = AccountOps.signIn(data, "katrinajj254@gmail.com", "katrina")
        assertEquals(
            AccountOps.WRONG_CREDENTIALS,
            (result as AccountOps.SignInResult.Failure).message,
        )
        assertEquals("Incorrect email or password.", AccountOps.WRONG_CREDENTIALS)
    }

    @Test
    fun a12_wrong_customer_password_is_the_same_message() {
        val result = AccountOps.signIn(data, "mika.tan@example.com", "customer")
        assertEquals(
            AccountOps.WRONG_CREDENTIALS,
            (result as AccountOps.SignInResult.Failure).message,
        )
    }

    @Test
    fun an_unknown_email_is_refused_with_the_credentials_message() {
        // Never "no such account": that would let an attacker enumerate addresses.
        val result = AccountOps.signIn(data, "nobody@example.com", "whatever")
        assertEquals(
            AccountOps.WRONG_CREDENTIALS,
            (result as AccountOps.SignInResult.Failure).message,
        )
    }

    @Test
    fun the_email_match_is_case_insensitive_but_the_password_is_not() {
        assertTrue(
            AccountOps.signIn(data, "MIKA.TAN@EXAMPLE.COM", "customer123")
                is AccountOps.SignInResult.Success,
        )
        assertTrue(
            AccountOps.signIn(data, "mika.tan@example.com", "Customer123")
                is AccountOps.SignInResult.Failure,
        )
    }

    // =========================================================================
    // A-11: Registration
    // =========================================================================

    private fun validForm() = AccountOps.Registration(
        fullName = "Elena Cruz",
        handle = "@elena",
        email = "elena.cruz@example.com",
        mobile = "0917 555 0199",
        province = "Metro Manila",
        city = "Makati",
        barangay = "Poblacion",
        addressLine = "12 Mabini St.",
        password = "elena2026",
    )

    @Test
    fun a11_a_valid_form_creates_a_customer() {
        val result = AccountOps.register(data, validForm())
        assertTrue(result is AccountOps.RegisterResult.Success)
        val account = (result as AccountOps.RegisterResult.Success).account
        assertEquals("Elena Cruz", account.name)
        assertEquals("@elena", account.handle)
        // A-11 c7: hard-coded Customer - this path cannot create an Owner.
        assertEquals(Role.CUSTOMER, account.role)
    }

    @Test
    fun a11_registration_can_never_grant_owner_access() {
        // There is no role field on the form at all; the only path hard-codes it.
        val attempts = listOf(
            validForm(),
            validForm().copy(email = "another@example.com", fullName = "Owner Two"),
        )
        attempts.forEach { form ->
            val result = AccountOps.register(data, form)
            assertEquals(
                Role.CUSTOMER,
                (result as AccountOps.RegisterResult.Success).account.role,
            )
        }
    }

    @Test
    fun a11_a_blank_required_field_is_refused() {
        val result = AccountOps.register(data, validForm().copy(fullName = ""))
        assertEquals(
            AccountOps.INCOMPLETE_FIELDS,
            (result as AccountOps.RegisterResult.Failure).message,
        )
        assertEquals("Complete the required fields.", AccountOps.INCOMPLETE_FIELDS)
    }

    @Test
    fun a11_the_handle_is_optional() {
        // A-11 c1 says "Messenger and/or TikTok handle", so a blank handle is legal.
        val result = AccountOps.register(data, validForm().copy(handle = ""))
        assertTrue(result is AccountOps.RegisterResult.Success)
    }

    @Test
    fun a11_an_incomplete_email_is_refused() {
        val result = AccountOps.register(data, validForm().copy(email = "elena.cruz@"))
        assertEquals(
            AccountOps.INVALID_EMAIL,
            (result as AccountOps.RegisterResult.Failure).message,
        )
    }

    @Test
    fun a11_a_mobile_that_is_not_11_digits_is_refused() {
        // A-11 c4's own example: "0917555" (7 digits).
        assertEquals(
            AccountOps.INVALID_MOBILE,
            (AccountOps.register(data, validForm().copy(mobile = "0917555"))
                as AccountOps.RegisterResult.Failure).message,
        )
        assertEquals("Enter a valid mobile number with 11 digits.", AccountOps.INVALID_MOBILE)
    }

    @Test
    fun a11_an_eleven_digit_mobile_written_with_spaces_is_accepted() {
        // "0917 555 0199" is a correct 11-digit number; rejecting it would be a
        // false failure a tester could not explain.
        assertTrue(
            AccountOps.register(data, validForm().copy(mobile = "0917 555 0199"))
                is AccountOps.RegisterResult.Success,
        )
    }

    @Test
    fun a11_a_duplicate_email_is_refused() {
        val result = AccountOps.register(data, validForm().copy(email = "mika.tan@example.com"))
        assertEquals(
            AccountOps.DUPLICATE_EMAIL,
            (result as AccountOps.RegisterResult.Failure).message,
        )
        assertEquals("An account with this email already exists.", AccountOps.DUPLICATE_EMAIL)
    }

    @Test
    fun a11_a_short_password_is_refused() {
        // A-11 c6's own example: "elena26" (7 characters).
        val result = AccountOps.register(data, validForm().copy(password = "elena26"))
        assertEquals(
            AccountOps.SHORT_PASSWORD,
            (result as AccountOps.RegisterResult.Failure).message,
        )
        assertEquals("Use at least 8 characters.", AccountOps.SHORT_PASSWORD)
    }

    @Test
    fun a11_exactly_eight_characters_is_accepted() {
        // The floor is 8, so 8 itself must pass.
        assertTrue(
            AccountOps.register(data, validForm().copy(password = "elena202"))
                is AccountOps.RegisterResult.Success,
        )
    }

    @Test
    fun a11_a_registered_customer_lands_in_the_owner_customer_list() {
        // A-08 c6: the new account appears with no extra step.
        val registered = (AccountOps.register(data, validForm()) as
            AccountOps.RegisterResult.Success).account
        val after = data.copy(accounts = data.accounts + registered)
        assertNotNull(after.account("elena.cruz@example.com"))
        assertTrue(after.customers().any { it.name == "Elena Cruz" })
    }

    @Test
    fun a11_the_new_account_starts_with_no_orders() {
        // A-16 c9's "No orders yet." walk needs a genuinely order-free account.
        assertTrue(data.ordersFor("elena.cruz@example.com").isEmpty())
    }

    // =========================================================================
    // A-17: profile and password
    // =========================================================================

    @Test
    fun a17_a_valid_mobile_saves_and_an_invalid_one_does_not() {
        val mika = data.account("mika.tan@example.com")!!
        val saved = AccountOps.updateProfile(
            mika, "Mika Tan", "@mikat", "0917 555 0101", "Metro Manila", "Makati",
            "Poblacion", "15 Mabini St., Unit 6A, Makati",
        )
        assertTrue(saved is AccountOps.ProfileResult.Success)
        assertEquals(
            "15 Mabini St., Unit 6A, Makati",
            (saved as AccountOps.ProfileResult.Success).account.addressLine,
        )

        val rejected = AccountOps.updateProfile(
            mika, "Mika Tan", "@mikat", "0917555", "Metro Manila", "Makati",
            "Poblacion", "12 Mabini St.",
        )
        assertEquals(
            AccountOps.INVALID_MOBILE,
            (rejected as AccountOps.ProfileResult.Failure).message,
        )
    }

    @Test
    fun a17_a_correct_password_change_succeeds() {
        val mika = data.account("mika.tan@example.com")!!
        val result = AccountOps.changePassword(mika, "customer123", "customer2026", "customer2026")
        assertTrue(result is AccountOps.ProfileResult.Success)
        // A-17 c7: the new password works at the next sign-in.
        val updated = (result as AccountOps.ProfileResult.Success).account
        val dataAfter = data.copy(accounts = data.accounts.map { if (it.email == updated.email) updated else it })
        assertTrue(
            AccountOps.signIn(dataAfter, "mika.tan@example.com", "customer2026")
                is AccountOps.SignInResult.Success,
        )
    }

    @Test
    fun a17_a_wrong_current_password_is_the_mismatch_message() {
        val mika = data.account("mika.tan@example.com")!!
        val result = AccountOps.changePassword(mika, "customer12", "customer2026", "customer2026")
        assertEquals(
            AccountOps.PASSWORD_MISMATCH,
            (result as AccountOps.ProfileResult.Failure).message,
        )
        assertEquals(
            "Check your current password and the new password.",
            AccountOps.PASSWORD_MISMATCH,
        )
    }

    @Test
    fun a17_a_mismatched_confirmation_is_the_same_message() {
        val mika = data.account("mika.tan@example.com")!!
        val result = AccountOps.changePassword(mika, "customer123", "customer2026", "customer2025")
        assertEquals(
            AccountOps.PASSWORD_MISMATCH,
            (result as AccountOps.ProfileResult.Failure).message,
        )
    }

    @Test
    fun a17_a_short_new_password_is_refused() {
        val mika = data.account("mika.tan@example.com")!!
        val result = AccountOps.changePassword(mika, "customer123", "cust26", "cust26")
        assertEquals(
            AccountOps.SHORT_PASSWORD,
            (result as AccountOps.ProfileResult.Failure).message,
        )
    }

    @Test
    fun the_success_message_is_the_one_the_criteria_quote() {
        assertEquals("Changes saved.", AccountOps.CHANGES_SAVED)
    }

    // =========================================================================
    // v2.3 HCI audit F-01 (Sev 4): the untouched Current-password field
    // =========================================================================

    /**
     * An untouched Current-password field must be answered, not hashed.
     *
     * PBEKeySpec rejects an empty char[], so the old code threw
     * InvalidKeySpecException out of `pbkdf2` on the UI thread and the process died
     * - a tester tapping Save on a fresh Change Password screen crashed the app.
     * The guard now returns the mismatch message before the KDF is reached.
     */
    @Test
    fun f01_blank_current_password_is_a_clean_failure_not_a_crash() {
        val mika = data.account("mika.tan@example.com")!!
        val result = AccountOps.changePassword(mika, "", "customer2026", "customer2026")
        assertEquals(
            AccountOps.PASSWORD_MISMATCH,
            (result as AccountOps.ProfileResult.Failure).message,
        )
    }

    /** Whitespace is blank too - a space bar tap must not reach the KDF either. */
    @Test
    fun f01_whitespace_current_password_is_a_clean_failure() {
        val mika = data.account("mika.tan@example.com")!!
        val result = AccountOps.changePassword(mika, "   ", "customer2026", "customer2026")
        assertTrue(result is AccountOps.ProfileResult.Failure)
    }

    /**
     * Defence in depth: even if a caller reaches `verify` with an empty value, the
     * KDF's own failure is swallowed into "no match" rather than thrown. This
     * asserts the runCatching wrapper, not the guard above.
     */
    @Test
    fun f01_verify_with_an_empty_value_returns_false_instead_of_throwing() {
        val mika = data.account("mika.tan@example.com")!!
        assertFalse(AccountOps.verify("", mika.passwordDigest))
    }

    // =========================================================================
    // Helpers the screens lean on
    // =========================================================================

    @Test
    fun the_digest_is_not_the_plaintext_and_verifies() {
        // IAS Rev 2 R.4: salted PBKDF2. Two digests of the same password differ in
        // their random salt, so EQUALITY is no longer the stability contract -
        // verify() is. The stored row carries its salt, its iteration count and a
        // 64-hex-char hash inside the pbkdf2$ wrapper.
        val digest = AccountOps.digest("customer123")
        assertTrue("a digest must not contain the password", !digest.contains("customer123"))
        assertTrue("must be a pbkdf2 row", AccountOps.isModern(digest))
        val parts = digest.split("$")
        assertEquals(4, parts.size)
        assertEquals("pbkdf2", parts[0])
        assertEquals(AccountOps.PBKDF2_ITERATIONS.toString(), parts[1])
        assertEquals(32, parts[2].length)          // 16-byte salt, hex-encoded
        assertEquals(64, parts[3].length)          // 256-bit hash, hex-encoded
        // Two digests of the same password are DIFFERENT (distinct salts)...
        assertTrue(
            "two digests must not share a salt",
            AccountOps.digest("customer123") != digest,
        )
        // ...but both verify.
        assertTrue(AccountOps.verify("customer123", digest))
        assertFalse(AccountOps.verify("wrong", digest))
    }

    @Test
    fun the_seeded_digest_verifies_the_seeded_password() {
        // A compiled-in literal once drifted from the seed it was meant to match;
        // building both through the same function removes that failure mode. The
        // seed's rows are PBKDF2 now, so the assertion is verify(), not equality -
        // equality would be false BY DESIGN because every digest carries a fresh
        // random salt.
        val owner = data.owner()!!
        assertTrue(AccountOps.isModern(owner.passwordDigest))
        assertTrue(AccountOps.verify(SeedData.OWNER_PASSWORD, owner.passwordDigest))
        val mika = data.account("mika.tan@example.com")!!
        assertTrue(AccountOps.verify(SeedData.CUSTOMER_PASSWORD, mika.passwordDigest))
    }

    @Test
    fun a_legacy_unsalted_digest_still_verifies() {
        // The v2.2 rows were plain SHA-256. A password stored before the upgrade
        // must keep working: verify() falls back to the legacy comparison.
        val legacy = AccountOps.legacySha256("customer123")
        assertTrue(AccountOps.verify("customer123", legacy))
        assertFalse(AccountOps.verify("nope", legacy))
    }
}
