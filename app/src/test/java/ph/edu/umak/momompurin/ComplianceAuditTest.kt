package ph.edu.umak.momompurin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static audits that read the SHIPPED source rather than the runtime.
 *
 * These exist because the pivot's riskiest failure mode is not a broken screen -
 * it is a leftover from the old live-selling app that still compiles; or a label
 * that reads right but is wired to nothing. Nothing else in the suite reads the
 * text of a label, so the source tree itself is the subject here.
 *
 * Every check reads CODE, never comments. This codebase documents each rule by
 * quoting the criterion it serves, so a comment saying "the pivot removes
 * 'Open a Live'" would otherwise fail the very audit it explains.
 */
class ComplianceAuditTest {

    private val sourceRoot = File("src/main/java/ph/edu/umak/momompurin")
    private val screensDir = File(sourceRoot, "ui/screens")
    private val navDir = File(sourceRoot, "ui/nav")

    private fun allKotlinFiles(): List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * The file with its COMMENTS removed.
     *
     * Block comments first, then line comments. Not a full Kotlin lexer, but
     * sufficient here: no string literal in this project contains a slash-slash
     * or a slash-star sequence, so the simple pass cannot corrupt a real literal.
     */
    private fun stripComments(source: String): String {
        val withoutBlock = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
            .replace(source) { match -> "\n".repeat(match.value.count { it == '\n' }) }
        return withoutBlock.lines()
            .joinToString("\n") { line ->
                val idx = line.indexOf("//")
                if (idx >= 0) line.substring(0, idx) else line
            }
    }

    private fun code(file: File): String = stripComments(file.readText())

    private fun allCode(): String = allKotlinFiles().joinToString("\n") { code(it) }

    // =========================================================================
    // The live-selling vocabulary is gone
    // =========================================================================

    @Test
    fun the_legacy_live_selling_screens_no_longer_exist() {
        val forbidden = listOf(
            "LiveClaimScreen.kt",
            "LiveSearchScreen.kt",
            "SessionClaimsScreen.kt",
            "PosScreen.kt",
            "KeepConsolidationScreen.kt",
            "NotificationsScreen.kt",
        )
        forbidden.forEach { name ->
            assertFalse(
                "$name was removed in the pivot and must not come back",
                File(screensDir, name).exists(),
            )
        }
    }

    @Test
    fun no_route_serves_a_removed_screen() {
        val routes = code(File(navDir, "Routes.kt"))
        listOf("LIVE_CLAIM", "LIVE_SEARCH", "LIVE_SESSION", "const val POS", "live/claim")
            .forEach { token ->
                assertFalse("Routes.kt must not declare $token", routes.contains(token))
            }
    }

    @Test
    fun the_nav_host_no_longer_imports_a_removed_screen() {
        val navHost = code(File(navDir, "MomoNavHost.kt"))
        listOf(
            "LiveClaimScreen", "LiveSearchScreen", "SessionClaimsScreen",
            "PosScreen", "KeepConsolidationScreen",
        ).forEach { screen ->
            assertFalse("MomoNavHost must not import $screen", navHost.contains(screen))
        }
    }

    @Test
    fun the_banned_ui_labels_are_absent_from_every_screen() {
        // The old manual-entry entry points and the retired payment shades.
        val text = allCode()
        listOf(
            "Open a Live",
            "New Claim",
            "Regular POS",
            "Scan barcode",
            "Paid-and-Kept",
            "Paid with Balance",
        ).forEach { phrase ->
            assertFalse("banned label must not be rendered: $phrase", text.contains(phrase))
        }
    }

    @Test
    fun no_payment_reference_field_exists_anywhere() {
        // A-15 c8: payment is arranged off-app, so no reference field may exist.
        val text = allCode()
        listOf("payment reference", "Payment reference", "reference number", "PayMongo")
            .forEach { phrase ->
                assertFalse("must not collect a payment reference: $phrase", text.contains(phrase))
            }
    }

    @Test
    fun checkout_collects_no_payment_and_states_the_fee_separately() {
        val checkout = code(File(screensDir, "CheckoutScreen.kt"))
        // A-15 c1/c8: no payment control, no amount field, no "pay now".
        listOf("Pay now", "amount paid", "Payment method", "Save payment")
            .forEach { token ->
                assertFalse("Checkout must not collect $token", checkout.contains(token))
            }
        // A-15 c4: the notice names the arrangement.
        assertTrue(checkout.contains("Shipping fees are handled separately"))
    }

    // =========================================================================
    // All 17 components are wired
    // =========================================================================

    @Test
    fun all_seventeen_components_have_a_reachable_screen() {
        val navHost = code(File(navDir, "MomoNavHost.kt"))
        val componentToComposable = mapOf(
            "A-01" to "SignInScreen",
            "A-02" to "DashboardScreen",
            "A-03" to "StockScreen",
            "A-04" to "OrdersScreen",
            "A-05" to "PaymentScreen",
            "A-06" to "HeldOrdersScreen",
            "A-07" to "PackingChecklistScreen",
            "A-08" to "ManageCustomersScreen",
            "A-09" to "ReportsScreen",
            "A-10" to "SettingsScreen",
            "A-11" to "RegistrationScreen",
            "A-12" to "SignInScreen",
            "A-13" to "ShopScreen",
            "A-14" to "CartScreen",
            "A-15" to "CheckoutScreen",
            "A-16" to "CustomerOrdersScreen",
            "A-17" to "AccountScreen",
        )
        assertEquals(17, componentToComposable.size)
        componentToComposable.forEach { (component, composable) ->
            assertTrue(
                "$component must be reachable via $composable on the graph",
                navHost.contains(composable),
            )
        }
    }

    @Test
    fun every_expected_composable_is_declared_in_the_screens_package() {
        val declared = allKotlinFiles()
            .filter { it.parentFile == screensDir }
            .flatMap { file ->
                Regex("^fun ([A-Z][A-Za-z0-9]+)\\(", RegexOption.MULTILINE)
                    .findAll(code(file))
                    .map { it.groupValues[1] }
                    .toList()
            }
            .toSet()

        setOf(
            "SignInScreen", "RegistrationScreen", "DashboardScreen", "OrdersScreen",
            "OrderDetailScreen", "PaymentScreen", "PackingChecklistScreen",
            "ShippingScreen", "StockScreen", "ProductDetailScreen",
            "ProductFormScreen", "StockAdjustScreen", "ManageCustomersScreen",
            "CustomerProfileScreen", "HeldOrdersScreen", "ReportsScreen",
            "SettingsScreen", "EditHistoryScreen", "MoreScreen", "ShopScreen",
            "VariantsScreen", "CartScreen", "CheckoutScreen",
            "CustomerOrdersScreen", "CustomerOrderDetailScreen",
            "CustomerHeldScreen", "CustomerCancelledScreen", "AccountScreen",
            "EditProfileScreen", "ChangePasswordScreen", "InvoiceViewScreen",
        ).forEach { name ->
            assertTrue("$name must be declared in ui/screens", declared.contains(name))
        }
    }

    @Test
    fun the_two_bottom_bars_carry_the_criteria_s_navigation() {
        val routes = code(File(navDir, "Routes.kt"))
        // A-02: Home, Orders, Stock, Customers, More.
        listOf("Home", "Orders", "Stock", "Customers", "More")
            .forEach { assertTrue("owner bar must carry $it", routes.contains("\"$it\"")) }
        // A-13..A-17: Shop, Cart, Orders, Account.
        listOf("Shop", "Cart", "Account")
            .forEach { assertTrue("customer bar must carry $it", routes.contains("\"$it\"")) }
        // And the retired Live tab is gone.
        assertFalse(routes.contains("\"Live\""))
    }

    // =========================================================================
    // A-13 c6: customer screens never render internal costs
    // =========================================================================

    @Test
    fun no_customer_screen_renders_a_pack_cost_unit_cost_or_markup() {
        listOf(
            "ShopScreen.kt", "VariantsScreen.kt", "CartScreen.kt",
            "CheckoutScreen.kt", "CustomerOrdersScreen.kt", "AccountScreen.kt",
        ).forEach { name ->
            val file = File(screensDir, name)
            if (!file.exists()) return@forEach
            val text = code(file)
            // The OWNER-only fields A-13 c6 forbids on a customer surface.
            listOf("packCost", "unitCost", "markupRate", "packQty", "packSize")
                .forEach { field ->
                    assertFalse("$name must not read $field (A-13 c6)", text.contains(field))
                }
        }
    }

    @Test
    fun the_shop_screen_shows_only_the_price() {
        val shop = code(File(screensDir, "ShopScreen.kt"))
        assertTrue(
            "the Shop must show the price",
            shop.contains("MomoMoney.format(product.price)"),
        )
        // Cost-side labels must not be rendered.
        listOf("Unit cost", "Pack cost", "Suggested retail", "3% freebie")
            .forEach { label ->
                assertFalse("the Shop must not show $label", shop.contains(label))
            }
    }

    // =========================================================================
    // A-01 c3 / A-12 c3: no role switch exists
    // =========================================================================

    @Test
    fun the_sign_in_screen_has_no_role_switch() {
        val signIn = code(File(screensDir, "SignInScreen.kt"))
        listOf("Role.OWNER", "Role.CUSTOMER", "SegmentedButton", "roleToggle")
            .forEach { token ->
                assertFalse("SignInScreen must not offer $token", signIn.contains(token))
            }
        // B.1: the neutral heading. Naming the Owner role on the shared screen was
        // the exposure the tester flagged, so the criterion's sentence was replaced
        // by one that says the same thing to the signer-in and nothing to anyone
        // else. The test asserts the NEW sentence so a reversion fails the build.
        assertTrue(
            "the heading must be the neutral sentence",
            signIn.contains("Sign in to your account"),
        )
        assertFalse(
            "the heading must not name the Owner role (B.1)",
            signIn.contains("Sign in with your Customer or Owner account"),
        )
    }

    @Test
    fun the_registration_screen_offers_no_role_admin_or_staff_option() {
        val registration = code(File(screensDir, "RegistrationScreen.kt"))
        listOf("Role.", "admin", "staff", "Owner")
            .forEach { token ->
                assertFalse("Registration must not offer $token", registration.contains(token))
            }
    }

    // =========================================================================
    // A-08 c5: the private flag never reaches a customer screen
    // =========================================================================

    @Test
    fun only_the_owner_customer_profile_reads_the_review_flag() {
        val readers = allKotlinFiles()
            .filter { it.parentFile == screensDir }
            .filter { code(it).contains("reviewFlag") }
            .map { it.name }

        assertEquals(
            "the review flag may only be read by the owner's customer profile",
            listOf("ManageCustomersScreen.kt"),
            readers,
        )
    }

    // =========================================================================
    // Colour discipline: no hex literals outside the theme package
    // =========================================================================

    @Test
    fun no_screen_declares_a_hex_colour() {
        val hexPattern = Regex("Color\\(0x[0-9A-Fa-f]{8}\\)")
        allKotlinFiles()
            .filterNot { it.path.contains("theme") }
            .forEach { file ->
                assertFalse(
                    "${file.name} must name a MomoColors token, not a literal colour",
                    hexPattern.containsMatchIn(code(file)),
                )
            }
    }

    // =========================================================================
    // The exact user-facing strings the workbook quotes
    // =========================================================================

    @Test
    fun every_criterion_quoted_message_is_present_verbatim() {
        val text = allCode()
        listOf(
            // A-01 / A-12
            "Enter your email address.",
            "Enter a valid email address.",
            "Enter your password.",
            "Incorrect email or password.",
            // A-11
            "Complete the required fields.",
            "Enter a valid mobile number with 11 digits.",
            "An account with this email already exists.",
            "Use at least 8 characters.",
            // A-17
            "Check your current password and the new password.",
            "Changes saved.",
            // A-03
            "Product code already exists.",
            "Product saved.",
            // A-04
            "Enter a valid shipping fee.",
            "Order cancelled. Reserved stock returned.",
            "Packed orders cannot be cancelled.",
            "Paid orders cannot be cancelled.",
            "Order restored.",
            "No orders found.",
            // A-05
            "Enter an amount greater than zero and not above the balance.",
            "This order will be marked as Paid.",
            "Payment recorded.",
            // A-06
            "Select held orders for the same customer.",
            "No held orders.",
            // A-07
            "Check all items before marking the order as packed.",
            "Shipping details copied.",
            "Enter the tracking number first.",
            // A-08
            "No customers found.",
            // A-09
            "The end date cannot be earlier than the start date.",
            // A-10
            "Enter a rate between 0 and 100.",
            "Backup saved.",
            // A-13
            "No products found.",
            // A-14
            "Added to cart.",
            "Your cart is empty.",
            "Sold Out",
            // A-15
            "Complete the delivery details.",
            "Connect to the internet to place your order.",
            "Order placed.",
            // A-16
            "No orders yet.",
            "Enter a reason for cancelling.",
        ).forEach { message ->
            assertTrue("message must appear verbatim: $message", text.contains(message))
        }
    }

    @Test
    fun the_insufficient_stock_message_has_the_criterion_s_shape() {
        // A-14 c4/c6: "Insufficient stock (Only N left)."
        assertTrue(
            allCode().contains("Insufficient stock (Only \$available left)."),
        )
    }

    @Test
    fun the_not_enough_stock_message_names_the_sku() {
        // A-15 c6: "Not enough stock for <SKU>."
        assertTrue(allCode().contains("Not enough stock for \$sku."))
    }

    @Test
    fun the_additional_shipping_fee_notice_is_named_verbatim() {
        // A-15 c4 names the dialog.
        assertTrue(allCode().contains("Additional Shipping Fee Notice"))
    }

    @Test
    fun the_fulfillment_choices_are_the_two_the_criteria_name() {
        val entities = code(File(sourceRoot, "data/entity/Entities.kt"))
        assertTrue(entities.contains("SHIP_NOW(\"Ship Now\")"))
        assertTrue(entities.contains("HOLD_FOR_COMBINED(\"Hold For Combined Shipping\")"))
    }

    // =========================================================================
    // The stock invariant
    // =========================================================================

    @Test
    fun available_is_a_derived_property_never_a_stored_field() {
        val entities = code(File(sourceRoot, "data/entity/Entities.kt"))
        assertTrue(
            "Available must be derived from physical - reserved",
            entities.contains("val available: Int get() = (physical - reserved)"),
        )
        // There must be no `available` constructor parameter to seed separately.
        assertFalse(entities.contains("val available: Int ="))
    }

    @Test
    fun dispatch_subtracts_both_physical_and_reserved_in_one_write() {
        val orderOps = code(File(sourceRoot, "data/OrderOps.kt"))
        assertTrue(
            "dispatch must reduce physical by the shipped qty",
            orderOps.contains("physical = (product.physical - qty)"),
        )
        assertTrue(
            "dispatch must reduce reserved by the same shipped qty",
            orderOps.contains("reserved = (product.reserved - qty)"),
        )
        // Both reductions must be adjacent in one product.copy(...), so a future
        // edit cannot keep one side and quietly drop the other.
        val bothInOneCopy = orderOps.contains(
            "product.copy(\n                        physical = (product.physical - qty)",
        ) || orderOps.contains(
            "product.copy(\n                    physical = (product.physical - qty)",
        )
        assertTrue(
            "the two reductions must be one atomic product.copy",
            bothInOneCopy,
        )
    }

    // =========================================================================
    // The manifest keeps its security controls
    // =========================================================================

    @Test
    fun the_manifest_still_refuses_backups_and_cleartext() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
    }
}
