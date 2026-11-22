package ph.edu.umak.momompurin.ui.nav

import androidx.annotation.DrawableRes
import ph.edu.umak.momompurin.R

/**
 * The pivot's route list.
 *
 * Two authenticated modes share ONE Sign In screen (A-01 c1, A-12 c1), so the
 * graph has two bottom bars rather than two graphs.
 *
 *   Owner     Home, Orders, Stock, Customers, More   (A-02 .. A-10)
 *   Customer  Shop, Cart, Orders, Account            (A-13 .. A-17)
 *
 * The legacy live-selling routes (Live, Open a Live, Regular POS, New Claim, the
 * barcode scanner) are gone: the pivot replaces manual claim entry with customer
 * self-service ordering, so those screens have no component to serve.
 */
object Routes {
    // ---------------------------------------------------------------- auth -----
    const val SIGN_IN = "sign-in"
    /** UAT fix (item 13): the customer-only password reset entry point. */
    const val FORGOT_PASSWORD = "forgot-password"
    const val REGISTER = "register"

    // --------------------------------------------------------------- owner -----
    const val OWNER_HOME = "owner/home"
    const val OWNER_ORDERS = "owner/orders"
    const val OWNER_STOCK = "owner/stock"
    const val OWNER_CUSTOMERS = "owner/customers"
    const val OWNER_MORE = "owner/more"

    /**
     * A-02 c2: the same Orders screen, opened already filtered to the tile tapped.
     *
     * A distinct PATH segment rather than `?filter=` on [OWNER_ORDERS]: a route
     * with an optional query is a different pattern from the bare one, so
     * `owner/orders` would stop resolving. `owner/orders` is a prefix of this, so
     * `OwnerTab.forRoute` still lights the Orders tab.
     */
    const val OWNER_ORDERS_FILTERED = "owner/orders/filter/{filter}"

    const val OWNER_ORDER_DETAIL = "owner/orders/{orderId}"
    const val OWNER_PAYMENT = "owner/orders/{orderId}/payment"
    const val OWNER_INVOICE = "owner/orders/{orderId}/invoice"
    const val OWNER_PACK = "owner/orders/{orderId}/pack"
    const val OWNER_SHIPPING = "owner/orders/{orderId}/shipping"
    const val OWNER_PRODUCT_DETAIL = "owner/stock/{sku}"
    const val OWNER_PRODUCT_FORM = "owner/stock/{sku}/edit"
    const val OWNER_STOCK_VARIANTS = "owner/stock/{parentSku}/variants"
    const val OWNER_STOCK_ADJUST = "owner/stock/{sku}/adjust"
    const val OWNER_CUSTOMER_PROFILE = "owner/customers/{email}"
    const val OWNER_HELD = "owner/held"
    const val OWNER_REPORTS = "owner/reports"
    const val OWNER_SETTINGS = "owner/settings"
    const val OWNER_EDIT_HISTORY = "owner/edit-history"

    // ------------------------------------------------------------ customer -----
    const val SHOP = "shop"
    const val CART = "cart"
    const val CHECKOUT = "checkout"
    const val MY_ORDERS = "my-orders"
    const val MY_ORDER_DETAIL = "my-orders/{orderId}"
    const val MY_ORDER_INVOICE = "my-orders/{orderId}/invoice"
    const val MY_HELD = "my-orders/held"
    const val MY_CANCELLED = "my-orders/cancelled"
    const val MY_ACCOUNT = "account"
    const val MY_EDIT_PROFILE = "account/profile"
    const val MY_CHANGE_PASSWORD = "account/password"
    const val SHOP_VARIANTS = "shop/{parentSku}"
    const val CUSTOMER_PRODUCT_DETAIL = "customer/product/{sku}"

    // ----------------------------------------------------------- builders ------

    fun ownerOrderDetail(orderId: String) = "owner/orders/$orderId"
    fun ownerOrdersFiltered(filter: String) = "owner/orders/filter/$filter"
    fun ownerPayment(orderId: String) = "owner/orders/$orderId/payment"
    fun ownerInvoice(orderId: String) = "owner/orders/$orderId/invoice"
    fun ownerPack(orderId: String) = "owner/orders/$orderId/pack"
    fun ownerShipping(orderId: String) = "owner/orders/$orderId/shipping"
    fun ownerProductDetail(sku: String) = "owner/stock/$sku"
    fun ownerProductForm(sku: String) = "owner/stock/$sku/edit"
    fun ownerStockVariants(parentSku: String) = "owner/stock/$parentSku/variants"
    fun ownerStockAdjust(sku: String) = "owner/stock/$sku/adjust"
    fun ownerCustomerProfile(email: String) = "owner/customers/$email"
    fun myOrderDetail(orderId: String) = "my-orders/$orderId"
    fun myOrderInvoice(orderId: String) = "my-orders/$orderId/invoice"
    fun shopVariants(parentSku: String) = "shop/$parentSku"
    fun customerProductDetail(sku: String) = "customer/product/$sku"
}

/** The Owner's five tabs, in A-02's order. */
enum class OwnerTab(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int,
) {
    HOME(Routes.OWNER_HOME, "Home", R.drawable.ic_home),
    ORDERS(Routes.OWNER_ORDERS, "Orders", R.drawable.ic_orders),
    STOCK(Routes.OWNER_STOCK, "Stock", R.drawable.ic_stock),
    CUSTOMERS(Routes.OWNER_CUSTOMERS, "Customers", R.drawable.ic_contacts),
    MORE(Routes.OWNER_MORE, "More", R.drawable.ic_more);

    companion object {
        /** The tab a route belongs to, so a detail screen keeps its tab lit. */
        fun forRoute(route: String?): OwnerTab? = when {
            route == null -> null
            route.startsWith("owner/held") ||
                route.startsWith("owner/reports") ||
                route.startsWith("owner/settings") ||
                route.startsWith("owner/edit-history") ||
                route == Routes.OWNER_MORE -> MORE
            route.startsWith("owner/orders") -> ORDERS
            route.startsWith("owner/stock") -> STOCK
            route.startsWith("owner/customers") -> CUSTOMERS
            route == Routes.OWNER_HOME -> HOME
            else -> null
        }
    }
}

/** The Customer's four tabs, in A-13's order. */
enum class CustomerTab(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int,
) {
    SHOP(Routes.SHOP, "Shop", R.drawable.ic_shop),
    CART(Routes.CART, "Cart", R.drawable.ic_cart),
    ORDERS(Routes.MY_ORDERS, "Orders", R.drawable.ic_orders),
    ACCOUNT(Routes.MY_ACCOUNT, "Account", R.drawable.ic_user_square);

    companion object {
        fun forRoute(route: String?): CustomerTab? = when {
            route == null -> null
            route == Routes.CHECKOUT || route == Routes.CART -> CART
            route.startsWith("shop") || route.startsWith("customer/product/") -> SHOP
            route.startsWith("my-orders") -> ORDERS
            route.startsWith("account") -> ACCOUNT
            else -> null
        }
    }
}
