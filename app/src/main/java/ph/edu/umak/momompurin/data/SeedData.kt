package ph.edu.umak.momompurin.data

import ph.edu.umak.momompurin.data.entity.Account
import ph.edu.umak.momompurin.data.entity.ActivityLogEntry
import ph.edu.umak.momompurin.data.entity.Fulfillment
import ph.edu.umak.momompurin.data.entity.Invoice
import ph.edu.umak.momompurin.data.entity.Order
import ph.edu.umak.momompurin.data.entity.OrderLine
import ph.edu.umak.momompurin.data.entity.OrderStage
import ph.edu.umak.momompurin.data.entity.OptionKind
import ph.edu.umak.momompurin.data.entity.PackingStatus
import ph.edu.umak.momompurin.data.entity.Payment
import ph.edu.umak.momompurin.data.entity.PaymentStatus
import ph.edu.umak.momompurin.data.entity.Product
import ph.edu.umak.momompurin.data.entity.ProductOption
import ph.edu.umak.momompurin.data.entity.Role
import ph.edu.umak.momompurin.data.entity.Setting
import ph.edu.umak.momompurin.data.entity.ShippingStatus
import ph.edu.umak.momompurin.data.entity.StockAdjustment
import java.util.Calendar

/**
 * The seeded dataset for the pivot, transcribed from
 * `USER STORY-v6.21.11.2026.xlsx` (A-01..A-17).
 *
 * ## The one rule that shapes this file
 *
 * `Reserved` is DERIVED, never typed. [reservedFromOrders] sums the quantities of
 * every order line whose order still HOLDS stock (placed, not cancelled, not yet
 * dispatched), and each product's `reserved` is that sum while its `physical` is
 * `availableTarget + reserved`.
 *
 * The previous seed typed `reserved` by hand and drifted: CHW carried
 * `reserved = 4` while its own order lines held 20 units, so cancelling
 * ORD-1045 (CHW x8) ran `reserved - 8` through a `coerceAtLeast(0)` and silently
 * clamped - the stock figure lied and no test caught it, because a clamp looks
 * exactly like a successful release. Deriving it makes that class of bug
 * structurally impossible: the sum and the rows are the same number.
 *
 * ## Seeded order map (every epic self-contained, no cross-epic jumps)
 *
 * | Order    | Customer    | Total   | Role in the story                        |
 * |----------|-------------|---------|------------------------------------------|
 * | ORD-1051 | Mika Tan    | P70.00  | A-04 edit + invalid fee + confirm P80.00 |
 * | ORD-1050 | Bea Cruz    | P450.00 | spare Incoming row                       |
 * | ORD-1049 | Lea Castro  | P220.00 | A-04 confirm a HELD order with P0.00 fee |
 * | ORD-1048 | Marie Chu   | P160.00 | spare Incoming row                       |
 * | ORD-1045 | Mika Tan    | P400.00 | A-04 overdue: cancel -> restore, stock   |
 * | ORD-1043 | Jessa Reyes | P90.00  | A-04 "Packed orders cannot be cancelled."|
 * | ORD-1042 | Pia Santos  | P880.00 | A-05 partial payment, P600 of P880       |
 * | ORD-1040 | Jessa Reyes | P570.00 | A-07 packing + dispatch stock arithmetic  |
 * | ORD-1039 | Bea Cruz    | P600.00 | A-06 held 3 days, electronic: NO advisory|
 * | ORD-1038 | Anne Garcia | P620.00 | A-06 held 7 days, electronic: advisory   |
 * | ORD-1037 | Mika Tan    | P360.00 | A-06 combine partner (+ORD-1036 = P760)  |
 * | ORD-1036 | Mika Tan    | P400.00 | A-06 held 10 days, non-electronic: none  |
 * | ORD-1035 | Jessa Reyes | P160.00 | A-05/A-16 already In Transit with tracking|
 *
 * [NOW_ANCHOR] is fixed at 19 November 2026, 10:00 AM so "today's date", every
 * held-days figure and the overdue window are stable on any run.
 */
object SeedData {

    private val calendar: Calendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.NOVEMBER, 19, 10, 0, 0)
    }
    val NOW_ANCHOR: Long = calendar.timeInMillis

    private fun daysAgo(days: Int, hour: Int = 20, minute: Int = 15): Long =
        (calendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -days)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis

    /**
     * A moment EARLIER TODAY, so "Today's sales" and "orders placed today" count
     * the same orders.
     *
     * A plain `hoursAgo(3)` from a 10:00 anchor lands at 07:00 the same day, but
     * `hoursAgo(6)` lands at 04:00 and anything longer crosses midnight - which
     * silently moved an order out of "Today" while the brief expected it in. This
     * helper pins the hour of the anchor day instead of subtracting from it.
     */
    private fun todayAt(hour: Int, minute: Int = 0): Long =
        (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis

    private fun daysFromNow(days: Int, hour: Int = 18): Long =
        (calendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

    // -----------------------------------------------------------------------
    // Accounts (A-01, A-11, A-12, A-17)
    // -----------------------------------------------------------------------

    /**
     * The owner plus the seeded customers.
     *
     * Digests are built by [AccountOps.digest] so the stored digest and the
     * sign-in comparison can never disagree - a compiled-in literal once drifted
     * from the seed it was supposed to match.
     */
    fun accounts(): List<Account> = listOf(
        Account(
            email = OWNER_EMAIL,
            role = Role.OWNER,
            passwordDigest = AccountOps.digest(OWNER_PASSWORD),
            name = "Katrina Bellen",
            mobile = "0917 555 0100",
            province = "Metro Manila",
            city = "Makati",
            barangay = "Poblacion",
            addressLine = "8 Mabini St.",
            createdAt = daysAgo(120, 9, 0),
        ),
        Account(
            email = "mika.tan@example.com",
            role = Role.CUSTOMER,
            passwordDigest = AccountOps.digest(CUSTOMER_PASSWORD),
            name = "Mika Tan",
            handle = "@mikat",
            mobile = "0917 555 0101",
            province = "Metro Manila",
            city = "Makati",
            barangay = "Poblacion",
            addressLine = "12 Mabini St., Unit 4B",
            createdAt = daysAgo(40, 20, 12),
        ),
        Account(
            email = "pia.santos@example.com",
            role = Role.CUSTOMER,
            passwordDigest = AccountOps.digest(CUSTOMER_PASSWORD),
            name = "Pia Santos",
            handle = "@pias",
            mobile = "0917 555 0142",
            province = "Metro Manila",
            city = "Makati",
            barangay = "Poblacion",
            addressLine = "12A Sampaguita St",
            createdAt = daysAgo(35, 20, 12),
        ),
        Account(
            email = "anne.garcia@example.com",
            role = Role.CUSTOMER,
            passwordDigest = AccountOps.digest(CUSTOMER_PASSWORD),
            name = "Anne Garcia",
            handle = "@annegarcia",
            mobile = "0917 555 0188",
            province = "Rizal",
            city = "Antipolo",
            barangay = "San Roque",
            addressLine = "7 Sumulong Hwy",
            createdAt = daysAgo(26, 19, 45),
        ),
        Account(
            email = "jessa.reyes@example.com",
            role = Role.CUSTOMER,
            passwordDigest = AccountOps.digest(CUSTOMER_PASSWORD),
            name = "Jessa Reyes",
            handle = "@jessareyes",
            mobile = "0919 555 0133",
            province = "Metro Manila",
            city = "Quezon City",
            barangay = "Batasan",
            addressLine = "44 Commonwealth Ave",
            createdAt = daysAgo(28, 21, 0),
        ),
        Account(
            email = "lea.castro@example.com",
            role = Role.CUSTOMER,
            passwordDigest = AccountOps.digest(CUSTOMER_PASSWORD),
            name = "Lea Castro",
            handle = "@leacastro",
            mobile = "0920 555 0166",
            province = "Cavite",
            city = "Bacoor",
            barangay = "Molino",
            addressLine = "18 Molino Blvd",
            createdAt = daysAgo(24, 20, 5),
        ),
        Account(
            email = "marie.chu@example.com",
            role = Role.CUSTOMER,
            passwordDigest = AccountOps.digest(CUSTOMER_PASSWORD),
            name = "Marie Chu",
            handle = "@mariechu",
            mobile = "0921 555 0155",
            province = "Metro Manila",
            city = "Mandaluyong",
            barangay = "Plainview",
            addressLine = "3 Luna St",
            createdAt = daysAgo(23, 19, 55),
        ),
        Account(
            email = "bea.cruz@example.com",
            role = Role.CUSTOMER,
            passwordDigest = AccountOps.digest(CUSTOMER_PASSWORD),
            name = "Bea Cruz",
            handle = "@beacruz",
            mobile = "0918 555 0177",
            province = "Metro Manila",
            city = "Pasig",
            barangay = "San Antonio",
            addressLine = "9 Rosal St",
            createdAt = daysAgo(33, 19, 30),
        ),
    )

    // -----------------------------------------------------------------------
    // Orders (A-02 c1-c2, A-04, A-05, A-06, A-07, A-16)
    // -----------------------------------------------------------------------

    /**
     * The seeded orders.
     *
     * The dashboard tiles are the partition [MomoData] documents, which is what
     * makes several figures true at once:
     *
     *   Pending orders     = stage Order Placed            -> 4 (1051, 1050, 1049, 1048)
     *   Unpaid or overdue  = balance > 0 past Incoming     -> 3 (1042, 1045, 1043)
     *   Held orders        = Hold For Combined, Paid       -> 4 (1039, 1038, 1037, 1036)
     *   To pack            = stage To Pack, not held       -> 3 (1042, 1043, 1040)
     *
     * ORD-1042 counts in two tiles on purpose ("To pack" and "Unpaid or overdue")
     * because packing and payment are independent tracks (A-04 c4).
     */
    fun orders(): List<Order> = listOf(
        // ---- Incoming queue: fresh Order Placed orders (A-04 c3) -------------
        // A-04: edit this one, reject a bad fee on it, then confirm at P80.00.
        Order(
            orderId = "ORD-1051", customerEmail = "mika.tan@example.com",
            placedAt = todayAt(8, 40), stage = OrderStage.ORDER_PLACED,
            paymentStatus = PaymentStatus.UNPAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            itemSubtotal = 7000,
            recipientName = "Mika Tan", mobile = "0917 555 0101",
            province = "Metro Manila", city = "Makati", barangay = "Poblacion",
            addressLine = "12 Mabini St., Unit 4B",
        ),
        Order(
            orderId = "ORD-1050", customerEmail = "bea.cruz@example.com",
            placedAt = todayAt(7, 15), stage = OrderStage.ORDER_PLACED,
            paymentStatus = PaymentStatus.UNPAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            itemSubtotal = 45000,
            recipientName = "Bea Cruz", mobile = "0918 555 0177",
            province = "Metro Manila", city = "Pasig", barangay = "San Antonio",
            addressLine = "9 Rosal St",
        ),
        // A-04 c5: a HELD incoming order, so the blank/P0.00 shipping fee path has
        // a subject of its own. Confirming ORD-1051 twice would be impossible
        // after its first confirm moves it out of Incoming.
        Order(
            orderId = "ORD-1049", customerEmail = "lea.castro@example.com",
            placedAt = daysAgo(1, 21, 5), stage = OrderStage.ORDER_PLACED,
            paymentStatus = PaymentStatus.UNPAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
            itemSubtotal = 22000,
            recipientName = "Lea Castro", mobile = "0920 555 0166",
            province = "Cavite", city = "Bacoor", barangay = "Molino",
            addressLine = "18 Molino Blvd",
        ),
        Order(
            orderId = "ORD-1048", customerEmail = "marie.chu@example.com",
            placedAt = daysAgo(1, 19, 30), stage = OrderStage.ORDER_PLACED,
            paymentStatus = PaymentStatus.UNPAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            itemSubtotal = 16000,
            recipientName = "Marie Chu", mobile = "0921 555 0155",
            province = "Metro Manila", city = "Mandaluyong", barangay = "Plainview",
            addressLine = "3 Luna St",
        ),

        // ---- A-04 c2/c8: overdue on the 24-hour window + cancel/restore ------
        // Invoiced 18 Nov so that on the 19 Nov anchor it is past due; the app
        // never cancels it by itself, which is the point of A-04 c2.
        Order(
            orderId = "ORD-1045", customerEmail = "mika.tan@example.com",
            placedAt = daysAgo(2, 20, 5), stage = OrderStage.WAITING_PAYMENT,
            paymentStatus = PaymentStatus.UNPAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            itemSubtotal = 40000,
            invoiceNo = "MP-1045",
            invoicedAt = daysAgo(1, 9, 0),
            paymentDeadline = daysFromNow(-1, 9),
            recipientName = "Mika Tan", mobile = "0917 555 0101",
            province = "Metro Manila", city = "Makati", barangay = "Poblacion",
            addressLine = "12 Mabini St., Unit 4B",
        ),

        // ---- A-04 c8: a PACKED but NOT paid order ----------------------------
        // The cancel guard checks Paid before Packed, so this row is
        // PARTIALLY_PAID on purpose: it is what makes "Packed orders cannot be
        // cancelled." reachable inside A-04 without first walking A-07.
        Order(
            orderId = "ORD-1043", customerEmail = "jessa.reyes@example.com",
            placedAt = daysAgo(3, 16, 20), stage = OrderStage.TO_PACK,
            paymentStatus = PaymentStatus.PARTIALLY_PAID,
            packingStatus = PackingStatus.PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            itemSubtotal = 9000,
            amountPaid = 4000,
            invoiceNo = "MP-1043",
            invoicedAt = daysAgo(3, 9, 10),
            paymentDeadline = daysFromNow(1, 9),
            packedAt = daysAgo(1, 14, 30),
            recipientName = "Jessa Reyes", mobile = "0919 555 0133",
            province = "Metro Manila", city = "Quezon City", barangay = "Batasan",
            addressLine = "44 Commonwealth Ave",
        ),

        // ---- ORD-1042: the canonical order (A-02 c5, A-05 c1) ----------------
        // MCKP 160.00 + SCWH 600.00 + freebie 0.00 = 760.00 subtotal
        // + 120.00 shipping = 880.00 ; paid 600.00 ; balance 280.00.
        Order(
            orderId = "ORD-1042", customerEmail = "pia.santos@example.com",
            placedAt = daysAgo(3, 21, 15), stage = OrderStage.TO_PACK,
            paymentStatus = PaymentStatus.PARTIALLY_PAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            itemSubtotal = 76000,
            shippingFee = 12000,
            amountPaid = 60000,
            invoiceNo = "MP-1042",
            invoicedAt = daysAgo(2, 11, 2),
            paymentDeadline = daysFromNow(1, 11),
            recipientName = "Pia Santos", mobile = "0917 555 0142",
            province = "Metro Manila", city = "Makati", barangay = "Poblacion",
            addressLine = "12A Sampaguita St",
        ),

        // ---- ORD-1040: the packing checklist's 3-item order (A-07 c1) --------
        Order(
            orderId = "ORD-1040", customerEmail = "jessa.reyes@example.com",
            placedAt = daysAgo(4, 19, 25), stage = OrderStage.TO_PACK,
            paymentStatus = PaymentStatus.PAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            itemSubtotal = 57000,
            amountPaid = 57000,
            invoiceNo = "MP-1040",
            invoicedAt = daysAgo(3, 9, 40),
            recipientName = "Jessa Reyes", mobile = "0919 555 0133",
            province = "Metro Manila", city = "Quezon City", barangay = "Batasan",
            addressLine = "44 Commonwealth Ave",
        ),

        // ---- Held: PAID orders bought "Hold For Combined Shipping" -----------
        // A-06 c3: held 3 days, contains SCWH (electronic) -> NO advisory yet.
        // This is the third leg of the advisory rule, seeded so all three states
        // are visible on one screen instead of only in a unit test.
        Order(
            orderId = "ORD-1039", customerEmail = "bea.cruz@example.com",
            placedAt = daysAgo(3, 8, 0), stage = OrderStage.TO_PACK,
            paymentStatus = PaymentStatus.PAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
            itemSubtotal = 60000,
            amountPaid = 60000,
            invoiceNo = "MP-1039",
            invoicedAt = daysAgo(4, 10, 5),
            recipientName = "Bea Cruz", mobile = "0918 555 0177",
            province = "Metro Manila", city = "Pasig", barangay = "San Antonio",
            addressLine = "9 Rosal St",
        ),
        // A-06 c2: held exactly 7 days with SCWH (electronic) -> advisory SHOWN.
        Order(
            orderId = "ORD-1038", customerEmail = "anne.garcia@example.com",
            placedAt = daysAgo(7, 9, 0), stage = OrderStage.TO_PACK,
            paymentStatus = PaymentStatus.PAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
            itemSubtotal = 62000,
            amountPaid = 62000,
            invoiceNo = "MP-1038",
            invoicedAt = daysAgo(8, 10, 5),
            recipientName = "Anne Garcia", mobile = "0917 555 0188",
            province = "Rizal", city = "Antipolo", barangay = "San Roque",
            addressLine = "7 Sumulong Hwy",
        ),
        // A-06 c6: ORD-1036 P400.00 + ORD-1037 P360.00 = P760.00 for Mika Tan.
        // Non-electronic at 10 days -> NO advisory however long it sits.
        Order(
            orderId = "ORD-1036", customerEmail = "mika.tan@example.com",
            placedAt = daysAgo(10, 9, 0), stage = OrderStage.TO_PACK,
            paymentStatus = PaymentStatus.PAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
            itemSubtotal = 40000,
            amountPaid = 40000,
            invoiceNo = "MP-1036",
            invoicedAt = daysAgo(11, 10, 15),
            recipientName = "Mika Tan", mobile = "0917 555 0101",
            province = "Metro Manila", city = "Makati", barangay = "Poblacion",
            addressLine = "12 Mabini St., Unit 4B",
        ),
        Order(
            orderId = "ORD-1037", customerEmail = "mika.tan@example.com",
            placedAt = daysAgo(8, 9, 0), stage = OrderStage.TO_PACK,
            paymentStatus = PaymentStatus.PAID,
            packingStatus = PackingStatus.NOT_PACKED,
            shippingStatus = ShippingStatus.NOT_SHIPPED,
            fulfillment = Fulfillment.HOLD_FOR_COMBINED,
            itemSubtotal = 36000,
            amountPaid = 36000,
            invoiceNo = "MP-1037",
            invoicedAt = daysAgo(9, 10, 40),
            recipientName = "Mika Tan", mobile = "0917 555 0101",
            province = "Metro Manila", city = "Makati", barangay = "Poblacion",
            addressLine = "12 Mabini St., Unit 4B",
        ),

        // ---- A-05 c6 / A-16 c3: an order that has ALREADY been dispatched ----
        // Real, because a tracked order must look like one: PACKED, IN_TRANSIT,
        // a courier and a tracking number. Its stock has left the shop, so
        // [holdsStock] excludes it and its lines reserve nothing - which is also
        // why unmarking it Paid must be refused (A-05 c6).
        Order(
            orderId = "ORD-1035", customerEmail = "jessa.reyes@example.com",
            placedAt = daysAgo(6, 15, 40), stage = OrderStage.SHIPPED,
            paymentStatus = PaymentStatus.PAID,
            packingStatus = PackingStatus.PACKED,
            shippingStatus = ShippingStatus.IN_TRANSIT,
            itemSubtotal = 16000,
            amountPaid = 16000,
            invoiceNo = "MP-1035",
            invoicedAt = daysAgo(6, 16, 5),
            packedAt = daysAgo(4, 10, 0),
            courier = "J&T Express",
            trackingNo = "JT 2612 3456 7890",
            recipientName = "Jessa Reyes", mobile = "0919 555 0133",
            province = "Metro Manila", city = "Quezon City", barangay = "Batasan",
            addressLine = "44 Commonwealth Ave",
        ),
    )

    /**
     * Order lines. Each block sums to its order's `itemSubtotal` exactly - a line
     * set that drifts would make the invoice disagree with the list.
     */
    fun orderLines(): List<OrderLine> = listOf(
        // ORD-1051: CHW x2 = P70.00
        OrderLine("L-1051-1", "ORD-1051", "CHW", "Chiikawa Wipes", qty = 2, unitPrice = 3500),

        // ORD-1050: MMBBS x1 = P450.00
        OrderLine("L-1050-1", "ORD-1050", "MMBBS", "My Melody Blind Box", qty = 1, unitPrice = 45000),

        // ORD-1049: MPBOW-PK x1 = P220.00
        OrderLine(
            "L-1049-1", "ORD-1049", "MPBOW-PK", "Melody Bow Pouch",
            variantName = "Pink", qty = 1, unitPrice = 22000,
        ),

        // ORD-1048: MCKP x1 = P160.00
        OrderLine("L-1048-1", "ORD-1048", "MCKP", "Melo Ku Charm Band", qty = 1, unitPrice = 16000),

        // ORD-1045: CHW x8 (280.00) + HKEYE x4 (120.00) = P400.00
        OrderLine("L-1045-1", "ORD-1045", "CHW", "Chiikawa Wipes", qty = 8, unitPrice = 3500),
        OrderLine("L-1045-2", "ORD-1045", "HKEYE", "Hello Kitty Eye Mask", qty = 4, unitPrice = 3000),

        // ORD-1043: W1 x2 = P90.00 (paid P40.00, balance P50.00)
        OrderLine("L-1043-1", "ORD-1043", "W1", "Hangyodon Wipes", qty = 2, unitPrice = 4500),

        // ORD-1042: MCKP 160.00 + SCWH 600.00 + freebie 0.00 = P760.00
        OrderLine("L-1042-1", "ORD-1042", "MCKP", "Melo Ku Charm Band", qty = 1, unitPrice = 16000),
        OrderLine("L-1042-2", "ORD-1042", "SCWH", "Sanrio Cosmos Watch", qty = 1, unitPrice = 60000),
        OrderLine("L-1042-3", "ORD-1042", "STKFR", "Freebie Sticker", qty = 1, unitPrice = 0),

        // ORD-1040: CHW x2 (70.00) + HKEYE x2 (60.00) + MPBOW-LV x2 (440.00) = P570.00
        OrderLine("L-1040-1", "ORD-1040", "CHW", "Chiikawa Wipes", qty = 2, unitPrice = 3500),
        OrderLine("L-1040-2", "ORD-1040", "HKEYE", "Hello Kitty Eye Mask", qty = 2, unitPrice = 3000),
        OrderLine(
            "L-1040-3", "ORD-1040", "MPBOW-LV", "Melody Bow Pouch",
            variantName = "Lavender", qty = 2, unitPrice = 22000,
        ),

        // ORD-1039: SCWH x1 = P600.00 (electronic, held 3 days)
        OrderLine("L-1039-1", "ORD-1039", "SCWH", "Sanrio Cosmos Watch", qty = 1, unitPrice = 60000),

        // ORD-1038: SCWH x1 (600.00) + HKEYE x1 (20.00) = P620.00
        OrderLine("L-1038-1", "ORD-1038", "SCWH", "Sanrio Cosmos Watch", qty = 1, unitPrice = 60000),
        OrderLine("L-1038-2", "ORD-1038", "HKEYE", "Hello Kitty Eye Mask", qty = 1, unitPrice = 2000),

        // ORD-1036: CHW x8 (280.00) + HKEYE x4 (120.00) = P400.00
        OrderLine("L-1036-1", "ORD-1036", "CHW", "Chiikawa Wipes", qty = 8, unitPrice = 3500),
        OrderLine("L-1036-2", "ORD-1036", "HKEYE", "Hello Kitty Eye Mask", qty = 4, unitPrice = 3000),

        // ORD-1037: W1 x4 (180.00) + HKEYE x6 (180.00) = P360.00
        OrderLine("L-1037-1", "ORD-1037", "W1", "Hangyodon Wipes", qty = 4, unitPrice = 4500),
        OrderLine("L-1037-2", "ORD-1037", "HKEYE", "Hello Kitty Eye Mask", qty = 6, unitPrice = 3000),

        // ORD-1035: MCKP x1 = P160.00 (already dispatched; reserves nothing)
        OrderLine("L-1035-1", "ORD-1035", "MCKP", "Melo Ku Charm Band", qty = 1, unitPrice = 16000),
    )

    // -----------------------------------------------------------------------
    // Products (A-03, A-13)
    // -----------------------------------------------------------------------

    /**
     * True while an order still HOLDS stock: placed, not cancelled, not dispatched.
     *
     * Written once and read by both [reservedFromOrders] and the stock invariant,
     * so "which orders reserve" cannot mean two different things.
     */
    fun holdsStock(order: Order): Boolean =
        order.stage != OrderStage.CANCELLED &&
            order.shippingStatus == ShippingStatus.NOT_SHIPPED

    /**
     * A-15 c7 / A-04 c7 / A-07 c9: the reserved quantity, DERIVED.
     *
     * This is the single source of the `reserved` column. It is the exact sum of
     * every line belonging to an order that still [holdsStock], so:
     *   - cancelling an order can always give back what it took (no clamp);
     *   - dispatching an order can always drop both sides (no clamp);
     *   - `On hand - Reserved = Available` is honest arithmetic rather than a
     *     display figure that happens to look plausible.
     */
    fun reservedFromOrders(): Map<String, Int> {
        val holding = orders().filter { holdsStock(it) }.map { it.orderId }.toSet()
        return orderLines()
            .filter { it.orderId in holding }
            .groupBy { it.sku }
            .mapValues { (_, lines) -> lines.sumOf { it.qty } }
    }

    /**
     * The products, with `reserved` derived and `physical` solved from the
     * available target each criterion pins.
     *
     * [AVAILABLE_TARGETS] is the only place a stock figure is chosen by hand now.
     * Everything else follows:
     *
     *     reserved = sum of holding order lines for the SKU
     *     physical = available target + reserved
     *
     * With the seeded orders that yields, for the rows the workbook quotes:
     *
     *   CHW     32 on hand / 20 reserved / 12 available   (A-03 c5, A-07 c9)
     *   HKEYE   25 on hand / 17 reserved /  8 available
     *   SCWH     4 on hand /  3 reserved /  1 available   (A-13 c5 Low Stock)
     *   MPBOW-PK 5 on hand /  1 reserved /  4 available
     *   MPBOW-LV 5 on hand /  2 reserved /  3 available
     *   MMBBS    2 on hand /  1 reserved /  1 available   (A-14 c5 last unit)
     *   P5T      0 on hand /  0 reserved /  0 available   (A-13 c5 Out of Stock)
     *   W1      14 on hand /  6 reserved /  8 available
     *   MCKP     7 on hand /  2 reserved /  5 available
     *   STKFR    1 on hand /  1 reserved /  0 available   (hidden freebie)
     *
     * The dispatch figure A-07 c9 quotes moves with it: ORD-1040 ships CHW x2, so
     * CHW goes 32/20/12 -> 30/18/12 and Available is UNCHANGED - which is the
     * invariant the criterion is actually about.
     */
    private val AVAILABLE_TARGETS: Map<String, Int> = mapOf(
        "CHW" to 12,
        "SCWH" to 1,
        "MMBBS" to 1,
        "MPBOW-PK" to 4,
        "MPBOW-LV" to 3,
        "P5T" to 0,
        "W1" to 8,
        "HKEYE" to 8,
        "MCKP" to 5,
        "STKFR" to 0,
    )

    /**
     * The product rows.
     *
     * Pack costs, pack quantities, packaging tiers and markup rates are real
     * numbers because A-03 c2's calculator is transparent: P380.00 / 18 pieces =
     * P21.11 unit cost, 3% freebie P0.63, Extra Small packaging P5.00, and a 30%
     * markup lands SRP at P34.77 - which is the P35.00 shelf price rounded up.
     *
     * CMEYE is deliberately ABSENT so A-03's "add Cinnamoroll Eye Mask" walk has
     * a genuinely free code.
     */
    fun products(): List<Product> {
        val reserved = reservedFromOrders()
        fun row(
            sku: String,
            name: String,
            price: Long,
            category: String,
            character: String,
            packSize: String = "XS",
            packCost: Long = 0L,
            packQty: Int = 1,
            unitCost: Long = 0L,
            isElectronic: Boolean = false,
            isParent: Boolean = false,
            parentSku: String? = null,
            photoRef: String? = null,
            description: String = "",
            specs: String = "",
        ): Product {
            val held = reserved[sku] ?: 0
            val target = AVAILABLE_TARGETS[sku] ?: 0
            return Product(
                sku = sku, name = name, price = price,
                category = category, character = character,
                description = description, specs = specs,
                physical = target + held, reserved = held,
                isElectronic = isElectronic,
                isParent = isParent,
                parentSku = parentSku,
                packSize = packSize, packCost = packCost, packQty = packQty,
                markupRate = 0.30, unitCost = unitCost,
                photoRef = photoRef,
            )
        }

        return listOf(
            // A-03 c5 + the GWT "Pricing calculation" row.
            row(
                "CHW", "Chiikawa Wipes", 3500, "Wipes", "Chiikawa",
                packSize = "XS", packCost = 38000, packQty = 18,
                unitCost = 2111, photoRef = "chw",
                description = "Soft, lint-free wipes with a faint sakura scent. " +
                    "Small enough for a pouch, sturdy enough for a screen.",
                specs = "18 wipes per pack",
            ),
            // A-13 c5 "Low Stock" row and A-06 c2's electronic advisory.
            row(
                "SCWH", "Sanrio Cosmos Watch", 60000, "Accessories", "Sanrio",
                packSize = "S", packCost = 21000, packQty = 4,
                unitCost = 5250, isElectronic = true, photoRef = "scwh",
                description = "A starry-dial quartz watch on a soft silicone strap. " +
                    "Splash resistant, with a one-year movement warranty.",
                specs = "Quartz movement, splash resistant",
            ),
            // A-14 c5: the last unit another customer buys, so the row reads Sold Out.
            row(
                "MMBBS", "My Melody Blind Box", 45000, "Blind Box", "My Melody",
                packSize = "S", packCost = 18000, packQty = 6,
                unitCost = 3000, photoRef = "mmbbs",
                description = "One sealed mystery figure from the My Melody series. " +
                    "Six designs to collect; the box picks for you.",
                specs = "1 sealed figure per box",
            ),
            // A-13 c4: a parent with two variants; not purchasable itself.
            Product(
                sku = "MPBOW", name = "Melody Bow Pouch", price = 0,
                category = "Pouch", character = "My Melody",
                description = "A quilted bow pouch with a satin lining and a wrist " +
                    "strap. Two colourways, each sold separately.",
                specs = "Quilted cotton, satin lining",
                physical = 0, reserved = 0, isParent = true,
                photoRef = "mpbow",
            ),
            row(
                "MPBOW-PK", "Melody Bow Pouch", 22000, "Pouch", "My Melody",
                packSize = "S", packCost = 11000, packQty = 6,
                unitCost = 1833, parentSku = "MPBOW", photoRef = "mpbow_pk",
                description = "A quilted bow pouch in soft pink, with a satin " +
                    "lining and a wrist strap.",
                specs = "Quilted cotton, satin lining",
            ),
            row(
                "MPBOW-LV", "Melody Bow Pouch", 22000, "Pouch", "My Melody",
                packSize = "S", packCost = 11000, packQty = 6,
                unitCost = 1833, parentSku = "MPBOW", photoRef = "mpbow_lv",
                description = "A quilted bow pouch in soft lavender, with a satin " +
                    "lining and a wrist strap.",
                specs = "Quilted cotton, satin lining",
            ),
            // A-13 c5: the Out-of-Stock row; Add to cart is disabled on it.
            row(
                "P5T", "Pompompurin Tumbler", 75000, "Drinkware", "Pompompurin",
                packSize = "L", packCost = 54700, packQty = 1,
                unitCost = 54700, photoRef = "p5t",
                description = "A double-walled stainless tumbler that keeps drinks " +
                    "cold for hours. Screw lid with a silicone seal.",
                specs = "500 ml, stainless steel",
            ),
            row(
                "W1", "Hangyodon Wipes", 4500, "Wipes", "Hangyodon",
                packSize = "XS", packCost = 22500, packQty = 10,
                unitCost = 2250, photoRef = "w1",
                description = "A pocket pack of gentle wipes with a light ocean " +
                    "scent. Resealable so the stack stays damp.",
                specs = "10 wipes per pack",
            ),
            row(
                "HKEYE", "Hello Kitty Eye Mask", 3000, "Eye Mask", "Hello Kitty",
                packSize = "XS", packCost = 10000, packQty = 10,
                unitCost = 1000, photoRef = "hkeye",
                description = "A contoured sleep mask that blocks light without " +
                    "pressing on the lashes. Adjustable elastic strap.",
                specs = "One size, adjustable strap",
            ),
            row(
                "MCKP", "Melo Ku Charm Band", 16000, "Accessories", "Kuromi",
                packSize = "S", packCost = 9000, packQty = 6,
                unitCost = 1500, photoRef = "mckp",
                description = "A stretch charm band with a dangling Kuromi charm. " +
                    "Fits most wrists and most phone straps.",
                specs = "Stretch band, one charm",
            ),
            // A-13 c6: priced at zero and typed "Freebie" so it never shows as a cost.
            row(
                "STKFR", "Freebie Sticker", 0, "Freebie", "Sanrio", photoRef = "stkfr",
                description = "A thank-you sticker tucked into every parcel.",
                specs = "One sticker",
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Payments / invoices (A-05)
    // -----------------------------------------------------------------------

    fun payments(): List<Payment> = listOf(
        // A-05 c1: ORD-1042 has P600.00 of its P880.00, leaving P280.00.
        Payment("PMT-1042-1", "ORD-1042", 60000, "GCash", daysAgo(2, 11, 1)),
        // A-05 c2: the partial payment on the packed order (P40 of P90).
        Payment("PMT-1043-1", "ORD-1043", 4000, "Maya", daysAgo(3, 10, 5)),
        // ORD-1040, the held orders and the dispatched order are settled in full.
        Payment("PMT-1040-1", "ORD-1040", 57000, "Bank Transfer", daysAgo(3, 9, 20)),
        Payment("PMT-1039-1", "ORD-1039", 60000, "GCash", daysAgo(4, 9, 50)),
        Payment("PMT-1038-1", "ORD-1038", 62000, "GCash", daysAgo(8, 9, 55)),
        Payment("PMT-1037-1", "ORD-1037", 36000, "Maya", daysAgo(9, 9, 45)),
        Payment("PMT-1036-1", "ORD-1036", 40000, "GCash", daysAgo(11, 9, 30)),
        Payment("PMT-1035-1", "ORD-1035", 16000, "GCash", daysAgo(6, 16, 20)),
    )

    fun invoices(): List<Invoice> = listOf(
        Invoice("MP-1045", "ORD-1045", daysAgo(1, 9, 0), 40000),
        Invoice("MP-1043", "ORD-1043", daysAgo(3, 9, 10), 9000),
        Invoice("MP-1042", "ORD-1042", daysAgo(2, 11, 2), 88000),
        Invoice("MP-1040", "ORD-1040", daysAgo(3, 9, 40), 57000),
        Invoice("MP-1039", "ORD-1039", daysAgo(4, 10, 5), 60000),
        Invoice("MP-1038", "ORD-1038", daysAgo(8, 10, 5), 62000),
        Invoice("MP-1037", "ORD-1037", daysAgo(9, 10, 40), 36000),
        Invoice("MP-1036", "ORD-1036", daysAgo(11, 10, 15), 40000),
        Invoice("MP-1035", "ORD-1035", daysAgo(6, 16, 5), 16000),
    )

    // -----------------------------------------------------------------------
    // Options (A-03, A-10 Lists)
    // -----------------------------------------------------------------------

    fun productOptions(): List<ProductOption> {
        val characters = listOf(
            "Chiikawa", "Cinnamoroll", "Hangyodon", "Hello Kitty",
            "Kuromi", "My Melody", "Pompompurin", "Sanrio",
        )
        val categories = listOf(
            "Accessories", "Blind Box", "Drinkware", "Eye Mask",
            "Freebie", "Pouch", "Wipes",
        )
        return characters.mapIndexed { i, v ->
            ProductOption("OPT-C-%02d".format(i + 1), OptionKind.CHARACTER, v, i)
        } + categories.mapIndexed { i, v ->
            ProductOption("OPT-K-%02d".format(i + 1), OptionKind.CATEGORY, v, i)
        }
    }

    // -----------------------------------------------------------------------
    // Settings (A-10 c1), adjustments (A-03 c6) and edit history (A-10 c5)
    // -----------------------------------------------------------------------

    fun settings(): List<Setting> = listOf(
        Setting("business_name", "Momompurin"),
        Setting("owner_email", OWNER_EMAIL),
        Setting("profit_markup", "30"),
        Setting("freebie_rate", "3"),
        Setting("pack_size_tiers", "XS=5.00, S=10.00, M=15.00, L=20.00, XL=35.00"),
        Setting("invoice_prefix", "MP-"),
        Setting("order_prefix", "ORD-"),
        Setting("payment_methods", "GCash, Maya, Bank Transfer"),
        Setting("courier_list", "J&T Express, TikTok Shop, Lalamove/Grab, Pickup"),
    )

    /** One adjustment WITH a reason and one WITHOUT (A-03 c6's optional reason). */
    fun stockAdjustments(): List<StockAdjustment> = listOf(
        StockAdjustment(
            adjId = "ADJ-0001", sku = "CHW", oldPhysical = 0, newPhysical = 16,
            reason = "Supplier delivery, pack of 18", adjustedAt = daysAgo(12, 9, 15),
        ),
        StockAdjustment(
            adjId = "ADJ-0002", sku = "HKEYE", oldPhysical = 10, newPhysical = 8,
            reason = null,
            adjustedAt = daysAgo(7, 15, 30),
        ),
    )

    /**
     * The seeded edit history, SEALED into the IAS R.5 hash chain.
     *
     * Sealing happens here rather than on first write so a fresh install already
     * has a verifiable chain: the Edit History badge must read "verified" before
     * the owner has changed anything, otherwise the control looks broken on the
     * first screen a tester opens.
     */
    fun activityLog(): List<ActivityLogEntry> = AuditChain.seal(
        listOf(
            ActivityLogEntry(
                logId = "LOG-0001", actor = OWNER_EMAIL,
                action = "Adjusted CHW to 16 on hand",
                entityType = "Product", entityId = "CHW",
                detail = "reason: Supplier delivery, pack of 18",
                loggedAt = daysAgo(12, 9, 15),
            ),
            ActivityLogEntry(
                logId = "LOG-0002", actor = OWNER_EMAIL,
                action = "Adjusted HKEYE to 8 on hand",
                entityType = "Product", entityId = "HKEYE",
                detail = "reason: not recorded",
                loggedAt = daysAgo(7, 15, 30),
            ),
            ActivityLogEntry(
                logId = "LOG-0003", actor = OWNER_EMAIL,
                action = "Saved CHW",
                entityType = "Product", entityId = "CHW",
                detail = "pack cost, pieces per pack, packaging size",
                loggedAt = daysAgo(14, 10, 0),
            ),
        ),
    )

    // -----------------------------------------------------------------------
    // Constants the criteria quote
    // -----------------------------------------------------------------------

    const val OWNER_EMAIL = "katrinajj254@gmail.com"
    const val OWNER_PASSWORD = "katrina@"
    const val CUSTOMER_PASSWORD = "customer123"

    /** A-04's Incoming tab: the four fresh orders. */
    val PENDING_ORDER_IDS = listOf("ORD-1051", "ORD-1050", "ORD-1049", "ORD-1048")

    /** A-06 c5/c6: the pair that combines into one P760.00 shipment. */
    val COMBINABLE_ORDER_IDS = listOf("ORD-1036", "ORD-1037")

    /** A-16 c6: Mika Tan's held total. */
    const val MIKA_HELD_TOTAL = 76000L

    /** A-04's single edited-and-invoiced order. */
    const val EDIT_AND_INVOICE_ORDER = "ORD-1051"

    /** A-04's held order, confirmed with a blank fee. */
    const val HELD_INVOICE_ORDER = "ORD-1049"

    /** A-04's overdue order, cancelled then restored. */
    const val OVERDUE_CANCEL_RESTORE_ORDER = "ORD-1045"

    /** A-04's already-packed order: the "Packed orders cannot be cancelled." subject. */
    const val PACKED_UNCANCELABLE_ORDER = "ORD-1043"

    /** A-05 c6 / A-16 c3: the already-dispatched, tracked order. */
    const val DISPATCHED_ORDER = "ORD-1035"

    /** A-07's three-item packing order. */
    const val PACKING_ORDER = "ORD-1040"

    /** The complete seeded dataset. */
    fun build(): MomoData = MomoData(
        accounts = accounts(),
        products = products(),
        orders = orders(),
        orderLines = orderLines(),
        payments = payments(),
        invoices = invoices(),
        productOptions = productOptions(),
        stockAdjustments = stockAdjustments(),
        settings = settings(),
        activityLog = activityLog(),
        // A-14 c1: a fresh install starts with an empty cart.
        carts = emptyMap(),
    )
}
