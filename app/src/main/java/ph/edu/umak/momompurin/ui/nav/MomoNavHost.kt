package ph.edu.umak.momompurin.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ph.edu.umak.momompurin.data.MomoStore
import ph.edu.umak.momompurin.data.entity.Role
import ph.edu.umak.momompurin.ui.screens.AccountScreen
import ph.edu.umak.momompurin.ui.screens.CartScreen
import ph.edu.umak.momompurin.ui.screens.ChangePasswordScreen
import ph.edu.umak.momompurin.ui.screens.CheckoutScreen
import ph.edu.umak.momompurin.ui.screens.CustomerOrdersScreen
import ph.edu.umak.momompurin.ui.screens.CustomerProfileScreen
import ph.edu.umak.momompurin.ui.screens.CustomerOrderDetailScreen
import ph.edu.umak.momompurin.ui.screens.DashboardScreen
import ph.edu.umak.momompurin.ui.screens.DashboardFilter
import ph.edu.umak.momompurin.ui.screens.EditHistoryScreen
import ph.edu.umak.momompurin.ui.screens.HeldOrdersScreen
import ph.edu.umak.momompurin.ui.screens.ManageCustomersScreen
import ph.edu.umak.momompurin.ui.screens.MoreScreen
import ph.edu.umak.momompurin.ui.screens.OrderDetailScreen
import ph.edu.umak.momompurin.ui.screens.OrdersScreen
import ph.edu.umak.momompurin.ui.screens.OwnerVariantsScreen
import ph.edu.umak.momompurin.ui.screens.CustomerProductDetailScreen
import ph.edu.umak.momompurin.ui.screens.PackingChecklistScreen
import ph.edu.umak.momompurin.ui.screens.PaymentScreen
import ph.edu.umak.momompurin.ui.screens.ProductDetailScreen
import ph.edu.umak.momompurin.ui.screens.ProductFormScreen
import ph.edu.umak.momompurin.ui.screens.RegistrationScreen
import ph.edu.umak.momompurin.ui.screens.ReportsScreen
import ph.edu.umak.momompurin.ui.screens.SettingsScreen
import ph.edu.umak.momompurin.ui.screens.ShippingScreen
import ph.edu.umak.momompurin.ui.screens.ShopScreen
import ph.edu.umak.momompurin.ui.screens.CustomerCancelledScreen
import ph.edu.umak.momompurin.ui.screens.CustomerHeldScreen
import ph.edu.umak.momompurin.ui.screens.EditProfileScreen
import ph.edu.umak.momompurin.ui.screens.InvoiceViewScreen
import ph.edu.umak.momompurin.ui.screens.SignInScreen
import ph.edu.umak.momompurin.ui.screens.StockAdjustScreen
import ph.edu.umak.momompurin.ui.screens.StockScreen
import ph.edu.umak.momompurin.ui.screens.VariantsScreen
import ph.edu.umak.momompurin.ui.theme.MomoColors
import ph.edu.umak.momompurin.ui.theme.MomoSpace

/**
 * The navigation graph for the pivot's 17 components.
 *
 * Two authenticated modes share ONE Sign In screen (A-01 c1, A-12 c1). Which
 * bottom bar shows is decided by the signed-in ACCOUNT's role - never by a user
 * choice - and the graph is entered at [Routes.SIGN_IN] whenever there is no
 * session, so no authenticated screen is reachable without signing in.
 *
 * ## A.8: the bar survives a screen-off/screen-on cycle
 *
 * The old form computed the bar's visibility from `currentRoute`, which is null
 * for one composition after the activity is recreated on wake (the NavHost has
 * not re-attached its back stack entry yet). `forRoute(null)` returned null and
 * the whole bar vanished until the next navigation - the "unpredictable
 * disappearing bottom bar" the tester hit by locking the phone.
 *
 * The fix is to make the bar's visibility depend on things that are ALWAYS
 * current:
 *
 *  - the SESSION (role decides which bar, or none on the auth screens);
 *  - the back stack ENTRY's destination, read through
 *    [currentBackStackEntryAsState] but resolved with a route-prefix match that
 *    tolerates a null route by keeping the LAST known route in a `remember`
 *    slot, so one null frame cannot unmount the bar.
 */
@Composable
fun MomoNavHost(
    store: MomoStore,
    navController: NavHostController = rememberNavController(),
) {
    val session by store.session.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // A.8: hold the last non-null route. On the wake frame where the destination
    // has not re-attached, `currentRoute` is null and this keeps the previous
    // value, so the bar does not blink out. A real navigation always writes a
    // non-null route through here before the user can read the screen.
    val lastKnownRoute = remember { mutableStateOf<String?>(null) }
    if (currentRoute != null) {
        lastKnownRoute.value = currentRoute
    }
    val effectiveRoute: String? = currentRoute ?: lastKnownRoute.value

    val startDestination = remember {
        when (store.session.value?.role) {
            Role.OWNER -> Routes.OWNER_HOME
            Role.CUSTOMER -> Routes.SHOP
            null -> Routes.SIGN_IN
        }
    }

    fun go(route: String) {
        navController.navigate(route)
    }

    // The bottom bar belongs to the signed-in role, and never to the auth
    // screens. A-01 c3 / A-12 c3 mean there is exactly one bar at a time.
    // A.8: `effectiveRoute` keeps the bar mounted through the null-destination
    // frame after a screen-off/screen-on cycle, so locking the phone can no
    // longer hide it.
    //
    // Computed here, above the layout, because TWO things depend on it: the bar
    // itself, and the snackbar host - which must clear the bar when one is
    // showing and the gesture inset when none is (v2.3 HCI audit F-17).
    val onAuthScreen = effectiveRoute == Routes.SIGN_IN || effectiveRoute == Routes.REGISTER
    val activeOwnerTab = if (!onAuthScreen && session?.role == Role.OWNER) {
        OwnerTab.forRoute(effectiveRoute)
    } else {
        null
    }
    val activeCustomerTab = if (!onAuthScreen && session?.role == Role.CUSTOMER) {
        CustomerTab.forRoute(effectiveRoute)
    } else {
        null
    }
    val bottomBarVisible = activeOwnerTab != null || activeCustomerTab != null

    /**
     * Switch to a bottom-bar tab, from ANY screen, in ONE tap.
     *
     * A-02 c1 / A-13 require every tab to be reachable in one tap and to stay
     * reachable while a detail screen is open, so this always leaves the tapped
     * tab as the ONLY entry: the stack is cleared to its root and the tab pushed.
     *
     * The previous form - `popUpTo(home) { saveState = true }` on a navigate to
     * `home` with `restoreState = true` - trapped the user on Orders: the pop
     * saved the Orders entry under the Home route, and `restoreState` then
     * restored that saved stack straight back on top of Home, so tapping Home
     * re-rendered Orders and looked like a dead control. Stock kept working
     * because its popped state was not keyed to the route being navigated to,
     * which is exactly the asymmetry that made the bug look intermittent.
     *
     * Clearing instead of saving costs a tab's scroll position on switch, which
     * is a fair trade for "a tab is never a dead end" - and it keeps Back from
     * walking through every tab ever visited.
     */
    fun switchTab(route: String) {
        navController.navigate(route) {
            // 0 is the graph root id; clearing it empties the whole stack, the
            // same idiom signOut() below already relies on.
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    /** A sign-out clears the whole stack so the back gesture cannot re-enter. */
    fun signOut() {
        store.signOut()
        navController.navigate(Routes.SIGN_IN) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    // A.7 / D.4: the device's real connectivity, mirrored into the store so
    // Customer Mode's checkout gate reads the network rather than the owner's
    // test switch.
    val networkOnline = ph.edu.umak.momompurin.ui.components.rememberNetworkStatus()
    androidx.compose.runtime.LaunchedEffect(networkOnline) {
        networkOnline?.let { store.setDeviceOnline(it) }
    }

    // A.5 / D.1: the one snackbar host for the whole app.
    val snackbarHostState =
        ph.edu.umak.momompurin.ui.components.rememberMomoSnackbar()

    Column(Modifier.fillMaxSize().background(MomoColors.SurfaceCanvas)) {
        // A.7 + Bug 5 (real-phone report): the owner's offline notice is provided to
        // every screen's HEADER rather than drawn above the NavHost.
        //
        // It used to be a sibling of the NavHost, so it consumed WindowInsets.statusBars
        // itself AND MomoTopBar consumed the same inset underneath it. The notice took
        // the inset twice: the screen title was displaced downward and, on `More`,
        // the space between the two insets read as a large empty yellow block. Now the
        // header owns the inset once and draws the notice directly under its own
        // band, so the strip is part of the header's block and nothing is displaced.
        //
        // Provided here (not per screen) so a screen cannot forget it, and as a
        // CompositionLocal so no screen can draw a second copy (v2.3 HCI audit F-02).
        val offline by store.offlineMode.collectAsState()
        val showOfflineNotice = offline && session?.role == Role.OWNER &&
            effectiveRoute != Routes.SIGN_IN && effectiveRoute != Routes.REGISTER
        val headerNotice: (@Composable () -> Unit)? = if (showOfflineNotice) {
            {
                ph.edu.umak.momompurin.ui.components.OwnerOfflineBanner(
                    Modifier.padding(horizontal = MomoSpace.Lg),
                )
            }
        } else {
            null
        }

        Box(Modifier.weight(1f)) {
            androidx.compose.runtime.CompositionLocalProvider(
                ph.edu.umak.momompurin.ui.components.LocalHeaderNotice provides headerNotice,
            ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
                // =================================================== auth =====
                composable(Routes.SIGN_IN) {
                    SignInScreen(
                        store = store,
                        onSignedIn = {
                            // A-01 c3 / A-12 c3: the account decides the landing.
                            val route = when (store.session.value?.role) {
                                Role.OWNER -> Routes.OWNER_HOME
                                else -> Routes.SHOP
                            }
                            navController.navigate(route) {
                                popUpTo(Routes.SIGN_IN) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onCreateAccount = { go(Routes.REGISTER) },
                    )
                }
                composable(Routes.REGISTER) {
                    RegistrationScreen(
                        store = store,
                        onRegistered = {
                            // A-11 c8: lands on the Shop, already signed in.
                            navController.navigate(Routes.SHOP) {
                                popUpTo(Routes.SIGN_IN) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // ===================================== A-02 Owner Dashboard ===
                composable(Routes.OWNER_HOME) {
                    DashboardScreen(
                        store = store,
                        onOpenOrders = { filter ->
                            // A-02 c2: the tile opens the list it counted.
                            go(Routes.ownerOrdersFiltered(filter.wire))
                        },
                        onOpenHeld = { go(Routes.OWNER_HELD) },
                        onOpenOrder = { go(Routes.ownerOrderDetail(it)) },
                        onOpenStock = { go(Routes.OWNER_STOCK) },
                    )
                }

                // ====================================== A-04 Process Orders ===
                composable(Routes.OWNER_ORDERS) {
                    OrdersScreen(
                        store = store,
                        onOpenOrder = { go(Routes.ownerOrderDetail(it)) },
                    )
                }
                // A-02 c2: the same screen pre-filtered by the tile tapped.
                composable(
                    route = Routes.OWNER_ORDERS_FILTERED,
                    arguments = listOf(navArgument("filter") { type = NavType.StringType }),
                ) { entry ->
                    OrdersScreen(
                        store = store,
                        onOpenOrder = { go(Routes.ownerOrderDetail(it)) },
                        initialFilter = DashboardFilter.fromWire(
                            entry.arguments?.getString("filter"),
                        ),
                    )
                }
                composable(
                    route = Routes.OWNER_ORDER_DETAIL,
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                ) { entry ->
                    OrderDetailScreen(
                        store = store,
                        orderId = entry.arguments?.getString("orderId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenPayment = { go(Routes.ownerPayment(it)) },
                        onOpenInvoice = { go(Routes.ownerInvoice(it)) },
                        onOpenPack = { go(Routes.ownerPack(it)) },
                        onOpenShipping = { go(Routes.ownerShipping(it)) },
                    )
                }

                // ================================= A-05 Payments & invoices ===
                composable(
                    route = Routes.OWNER_PAYMENT,
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                ) { entry ->
                    PaymentScreen(
                        store = store,
                        orderId = entry.arguments?.getString("orderId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.OWNER_INVOICE,
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                ) { entry ->
                    InvoiceViewScreen(
                        store = store,
                        orderId = entry.arguments?.getString("orderId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }

                // ==================================== A-07 Pack and ship ======
                composable(
                    route = Routes.OWNER_PACK,
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                ) { entry ->
                    PackingChecklistScreen(
                        store = store,
                        orderId = entry.arguments?.getString("orderId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.OWNER_SHIPPING,
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                ) { entry ->
                    ShippingScreen(
                        store = store,
                        orderId = entry.arguments?.getString("orderId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }

                // ============================ A-03 Manage products & stock ===
                composable(Routes.OWNER_STOCK) {
                    StockScreen(
                        store = store,
                        onOpenProduct = { go(Routes.ownerProductDetail(it)) },
                        onOpenVariants = { go(Routes.ownerStockVariants(it)) },
                        onAddProduct = { go(Routes.ownerProductForm("NEW")) },
                    )
                }
                composable(
                    route = Routes.OWNER_STOCK_VARIANTS,
                    arguments = listOf(navArgument("parentSku") { type = NavType.StringType }),
                ) { entry ->
                    OwnerVariantsScreen(
                        store = store,
                        parentSku = entry.arguments?.getString("parentSku").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenProduct = { go(Routes.ownerProductDetail(it)) },
                    )
                }
                composable(
                    route = Routes.OWNER_PRODUCT_DETAIL,
                    arguments = listOf(navArgument("sku") { type = NavType.StringType }),
                ) { entry ->
                    val sku = entry.arguments?.getString("sku").orEmpty()
                    ProductDetailScreen(
                        store = store,
                        sku = sku,
                        onBack = { navController.popBackStack() },
                        onEdit = { go(Routes.ownerProductForm(sku)) },
                        onAdjust = { go(Routes.ownerStockAdjust(sku)) },
                    )
                }
                composable(
                    route = Routes.OWNER_PRODUCT_FORM,
                    arguments = listOf(navArgument("sku") { type = NavType.StringType }),
                ) { entry ->
                    ProductFormScreen(
                        store = store,
                        sku = entry.arguments?.getString("sku").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.OWNER_STOCK_ADJUST,
                    arguments = listOf(navArgument("sku") { type = NavType.StringType }),
                ) { entry ->
                    StockAdjustScreen(
                        store = store,
                        sku = entry.arguments?.getString("sku").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }

                // ============================ A-08 Manage customer records ====
                composable(Routes.OWNER_CUSTOMERS) {
                    ManageCustomersScreen(
                        store = store,
                        onOpenCustomer = { go(Routes.ownerCustomerProfile(it)) },
                    )
                }
                composable(
                    route = Routes.OWNER_CUSTOMER_PROFILE,
                    arguments = listOf(navArgument("email") { type = NavType.StringType }),
                ) { entry ->
                    CustomerProfileScreen(
                        store = store,
                        email = entry.arguments?.getString("email").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenOrder = { go(Routes.ownerOrderDetail(it)) },
                    )
                }

                // ====================================== A-06 Hold and combine ==
                composable(Routes.OWNER_HELD) {
                    HeldOrdersScreen(
                        store = store,
                        onBack = { navController.popBackStack() },
                        onOpenOrder = { go(Routes.ownerOrderDetail(it)) },
                    )
                }

                // ========================================= A-09 Reports ========
                composable(Routes.OWNER_REPORTS) {
                    ReportsScreen(store = store, onBack = { navController.popBackStack() })
                }

                // ======================================== A-10 Settings =======
                composable(Routes.OWNER_SETTINGS) {
                    SettingsScreen(
                        store = store,
                        onBack = { navController.popBackStack() },
                        onOpenEditHistory = { go(Routes.OWNER_EDIT_HISTORY) },
                        onSignOut = { signOut() },
                    )
                }
                composable(Routes.OWNER_EDIT_HISTORY) {
                    EditHistoryScreen(store = store, onBack = { navController.popBackStack() })
                }

                // ========================================== A-10 More ==========
                composable(Routes.OWNER_MORE) {
                    MoreScreen(
                        store = store,
                        onOpenHeld = { go(Routes.OWNER_HELD) },
                        onOpenReports = { go(Routes.OWNER_REPORTS) },
                        onOpenSettings = { go(Routes.OWNER_SETTINGS) },
                        onOpenEditHistory = { go(Routes.OWNER_EDIT_HISTORY) },
                        onSignOut = { signOut() },
                    )
                }

                // ================================== A-13 Browse product catalog =
                composable(Routes.SHOP) {
                    ShopScreen(
                        store = store,
                        onOpenVariants = { go(Routes.shopVariants(it)) },
                        onOpenProductDetail = { go(Routes.customerProductDetail(it)) },
                        onOpenCart = { go(Routes.CART) },
                        // E.1: an order alert opens the order detail directly.
                        onOpenOrder = { go(Routes.myOrderDetail(it)) },
                    )
                }
                composable(
                    route = Routes.SHOP_VARIANTS,
                    arguments = listOf(navArgument("parentSku") { type = NavType.StringType }),
                ) { entry ->
                    VariantsScreen(
                        store = store,
                        parentSku = entry.arguments?.getString("parentSku").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.CUSTOMER_PRODUCT_DETAIL,
                    arguments = listOf(navArgument("sku") { type = NavType.StringType }),
                ) { entry ->
                    CustomerProductDetailScreen(
                        store = store,
                        sku = entry.arguments?.getString("sku").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onViewCart = { go(Routes.CART) },
                    )
                }

                // ===================================== A-14 Manage shopping cart
                composable(Routes.CART) {
                    CartScreen(
                        store = store,
                        onCheckout = { go(Routes.CHECKOUT) },
                        onBrowse = { go(Routes.SHOP) },
                    )
                }

                // ================================== A-15 Checkout and payment ==
                composable(Routes.CHECKOUT) {
                    CheckoutScreen(
                        store = store,
                        onBack = { navController.popBackStack() },
                        onPlaced = { orderId ->
                            navController.navigate(Routes.myOrderDetail(orderId)) {
                                popUpTo(Routes.SHOP)
                            }
                        },
                    )
                }

                // ====================================== A-16 Track orders ======
                composable(Routes.MY_ORDERS) {
                    CustomerOrdersScreen(
                        store = store,
                        onOpenOrder = { go(Routes.myOrderDetail(it)) },
                        onOpenHeld = { go(Routes.MY_HELD) },
                        onOpenCancelled = { go(Routes.MY_CANCELLED) },
                    )
                }
                composable(
                    route = Routes.MY_ORDER_DETAIL,
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                ) { entry ->
                    CustomerOrderDetailScreen(
                        store = store,
                        orderId = entry.arguments?.getString("orderId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenInvoice = { go(Routes.myOrderInvoice(it)) },
                    )
                }
                composable(
                    route = Routes.MY_ORDER_INVOICE,
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                ) { entry ->
                    InvoiceViewScreen(
                        store = store,
                        orderId = entry.arguments?.getString("orderId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.MY_HELD) {
                    CustomerHeldScreen(store = store, onBack = { navController.popBackStack() })
                }
                composable(Routes.MY_CANCELLED) {
                    CustomerCancelledScreen(store = store, onBack = { navController.popBackStack() })
                }

                // ===================================== A-17 Manage account =====
                composable(Routes.MY_ACCOUNT) {
                    AccountScreen(
                        store = store,
                        onEditProfile = { go(Routes.MY_EDIT_PROFILE) },
                        onChangePassword = { go(Routes.MY_CHANGE_PASSWORD) },
                        onSignOut = { signOut() },
                        onOpenOrders = { go(Routes.MY_ORDERS) },
                    )
                }
                composable(Routes.MY_EDIT_PROFILE) {
                    EditProfileScreen(store = store, onBack = { navController.popBackStack() })
                }
                composable(Routes.MY_CHANGE_PASSWORD) {
                    ChangePasswordScreen(store = store, onBack = { navController.popBackStack() })
                }
            }

            // A.5 / D.1: the one floating snackbar for the whole app, overlaid on
            // the content and pinned to its bottom edge - so it always floats ABOVE
            // the bottom navigation bar, clear of the gesture handle and above the
            // thumb that just tapped the control it is confirming (v2.3 HCI audit
            // F-17). It clears the bar's height when a bar is mounted, and the
            // system navigation inset when none is (auth screens).
            ph.edu.umak.momompurin.ui.components.MomoSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (bottomBarVisible) {
                            Modifier.padding(bottom = MomoSpace.Sm)
                        } else {
                            Modifier
                                .navigationBarsPadding()
                                .padding(bottom = MomoSpace.Sm)
                        },
                    ),
            )
            }
        }

        if (activeOwnerTab != null) {
            MomoBottomBar(
                tabs = ownerBarTabs,
                activeRoute = activeOwnerTab.route,
                onSelect = { tab -> switchTab(tab.route) },
            )
        } else if (activeCustomerTab != null) {
            // D.2: the Cart tab carries the LIVE item count on every customer
            // screen, read from the same cart the Shop's header badge reads - so
            // the two can never disagree.
            val cartCount = store.data.collectAsState().value
                .cart(session?.email.orEmpty())
                .sumOf { it.qty }
            MomoBottomBar(
                tabs = customerBarTabs.map { tab ->
                    if (tab.route == Routes.CART) {
                        tab.copy(badgeCount = cartCount)
                    } else {
                        tab
                    }
                },
                activeRoute = activeCustomerTab.route,
                onSelect = { tab -> switchTab(tab.route) },
            )
        }

        // A.5 / D.1: the floating snackbar belongs ABOVE the bottom navigation bar
        // - see the overlay inside the content Box above. It used to be composed
        // here, as the Column's last child, which laid it out BELOW the bar in the
        // system gesture strip: the thumb covered the confirmation and the handle
        // shared its space (v2.3 HCI audit F-17). Do not move it back out here.
    }
}
