"""Content for USER STORY-v6.21.11.2026.xlsx - Group 6's visual standard.

Every quoted string here is transcribed from the compiled v2.1 UI (see
`system_modelling_audit/ui_strings.txt`, generated from the Kotlin source) and
every seeded figure is read from `SeedData`, so the workbook cannot drift from
the APK the testers will hold.

Structure per epic block (mirrors GROUP6-User_Story_v5.20.11.2026.xlsx):
    row N      A=No. B=Epic C=Persona D=I want to E=So that F="1"
               G=criteria (Sample Data + lettered sections) H=remarks I=Status
    row N+1    F="Given When Then"
    row N+2..  F=one scenario each ("A. Title\\n\\nGiven ...\\nWhen ...\\nThen ...")
    row N+k    G="Passed" / G="Failed" / G="Total" + H="/ N"

The scenario count equals the criterion-block count because each lettered
section in column G corresponds 1-to-1 with its scenario row in column F.
"""

# Credentials and records the tester needs, quoted from SeedData.kt.
OWNER_EMAIL = "katrinajj254@gmail.com"
OWNER_PASSWORD = "katrina@"
CUSTOMER_PASSWORD = "customer123"

EPICS: list[dict] = []


def epic(no, title, persona, want, so_that, sample, sections, scenarios, summary=""):
    """One component block.

    `sections` is the criteria text (column G, after the Sample Data block);
    `scenarios` is a list of (title, given, when, then) tuples (column F).
    Both are kept in the same order so A maps to A.1, B to B.1, and so on.
    `summary` is the one line the Summary tab shows under the epic's name.
    """
    EPICS.append(
        dict(no=no, title=title, persona=persona, want=want, so_that=so_that,
             sample=sample, sections=sections, scenarios=scenarios, summary=summary)
    )


# ===========================================================================
# A-01  Sign in
# ===========================================================================
epic(
    no="A-01",
    title="Sign In",
    persona="As the Owner",
    want=f'sign in with my email "{OWNER_EMAIL}" and password "{OWNER_PASSWORD}"',
    so_that="so that only the Owner can open the Dashboard and the Owner tools",
    sample=(
        "Sample Data\n"
        f'• Owner account: Email "{OWNER_EMAIL}", Password "{OWNER_PASSWORD}", role "Owner"\n'
        '• Wrong password: "wrongpass"; blank field: Email or Password left empty'
    ),
    sections=(
        "A. Sign In Screen\n"
        "1. The system shall show the Sign in screen when the Momompurin app is opened, with:\n"
        '• "Momompurin" as the title [Display/Label]\n'
        '• "Sign in with your Customer or Owner account" [Display/Label]\n'
        "• Email [Field] and Password [Field]\n"
        '• "Show" [Checkbox/Toggle] on the Password field\n'
        '• "Log in" [Button]\n'
        '• "New here?" and "Create an account" [Navigation]\n'
        "2. The system shall NOT show any role switch on this screen. The account found by the "
        "email decides the mode, so there is no Owner/Customer selector anywhere.\n"
        "\nB. Sign In and Validation\n"
        '1. The system shall open the Dashboard and show the Owner menu when the email and '
        'password are correct and the account is an Owner account.\n'
        f'Sample input: Email "{OWNER_EMAIL}" and Password "{OWNER_PASSWORD}", then "Log in" '
        "[Button]. Result: Owner is signed in on the Dashboard.\n"
        "2. The system shall display \"Enter your email address.\" under Email [Field] when it is blank.\n"
        "Sample input: Email left blank, Password typed.\n"
        '3. The system shall display "Enter your password." under Password [Field] when it is blank.\n'
        "Sample input: Email typed, Password left blank.\n"
        '4. The system shall display "Incorrect email or password." [Message] and stay on the '
        "Sign in screen when either is wrong.\n"
        f'Sample input: Email "{OWNER_EMAIL}" with Password "wrongpass".\n'
        "\nC. Show Password\n"
        '1. The system shall show the typed password as plain text and change the label from '
        '"Show" to "Hide" when "Show" [Checkbox/Toggle] is tapped.\n'
        "2. The system shall mask the password again when \"Hide\" is tapped.\n"
    ),
    scenarios=[
        ("A. Open the app",
         "the Owner is on the phone's home screen",
         'the Owner taps the Momompurin app icon',
         'the system shall show the Sign in screen with "Momompurin", Email, Password, '
         '"Show" and "Log in".'),
        ("B. Show the password",
         'the Owner is on the Sign in screen and has typed Password "katrina@"',
         'the Owner taps "Show"',
         'the system shall show the password as text and change the label to "Hide".'),
        ("C. Hide the password again",
         'the password is shown as text and the label reads "Hide"',
         'the Owner taps "Hide"',
         'the system shall mask the password again and change the label back to "Show".'),
        ("D. Missing email",
         "the Owner is on the Sign in screen",
         'the Owner leaves Email blank, types Password "katrina@" and taps "Log in"',
         'the system shall display "Enter your email address." under Email and stay on the '
         "Sign in screen."),
        ("E. Missing password",
         "the Owner is on the Sign in screen",
         f'the Owner enters Email "{OWNER_EMAIL}", leaves Password blank and taps "Log in"',
         'the system shall display "Enter your password." under Password and stay on the '
         "Sign in screen."),
        ("F. Wrong password",
         "the Owner is on the Sign in screen",
         f'the Owner enters Email "{OWNER_EMAIL}" with Password "wrongpass" and taps "Log in"',
         'the system shall display "Incorrect email or password." and stay on the Sign in screen.'),
        ("G. No role switch",
         "the Owner is on the Sign in screen",
         "the Owner looks for an Owner/Customer selector",
         "the system shall show no role switch anywhere on the screen: the account decides "
         "the mode."),
        ("H. Successful Owner login",
         "the Owner is on the Sign in screen and every earlier error has been cleared",
         f'the Owner enters Email "{OWNER_EMAIL}" and Password "{OWNER_PASSWORD}" and taps "Log in"',
         "the system shall open the Dashboard with \"Today's sales\", the four tiles and the "
         "Owner bottom bar (Home, Orders, Stock, Customers, More), ready for A-02."),
    ],
    summary="Sign in with an email and password so only the Owner can open the Dashboard and Owner tools.",
)

# ===========================================================================
# A-02  Dashboard
# ===========================================================================
epic(
    no="A-02",
    title="Dashboard",
    persona="As the Owner",
    want="view the dashboard figures",
    so_that="so that I can see how the business is doing and what work needs attention",
    sample=(
        "Sample Data\n"
        '• Today\'s sales counts today\'s orders at FULL value, including unpaid ones.\n'
        '• Seeded tile counts: Pending orders "4", Unpaid or overdue "3", Held orders "4", '
        'To pack "3".\n'
        '• Recent orders show "ORD-1051 · Mika Tan" "₱70.00" and so on, newest first.\n'
        '• Low stock lists the items at or below 2 available, showing AVAILABLE not on-hand.'
    ),
    sections=(
        "A. Dashboard Screen\n"
        '1. The system shall show the Dashboard when "Home" [Navigation] is tapped, with:\n'
        '• "Today" and the current date [Display/Label]\n'
        '• the shop name "Momompurin" and the signed-in owner\'s email [Display/Label]\n'
        '• "Today\'s sales" [Display/Label] with the amount and the count of orders placed today\n'
        '• "Pending orders", "Unpaid or overdue", "Held orders" and "To pack" [Display/Label] tiles\n'
        '• "Low stock alerts" [Display/Label] with each item\'s available count\n'
        '• "Recent orders" [Table] with the order number, customer and total\n'
        '2. The system shall count Today\'s sales as the FULL value of every order placed today, '
        "including unpaid and partially paid ones.\n"
        "\nB. Dashboard Tiles\n"
        '1. The system shall open the Orders list already filtered to the four Incoming orders '
        'when "Pending orders" is tapped.\n'
        'Sample input: tap "Pending orders". Result: "ORD-1051", "ORD-1050", "ORD-1049" and '
        '"ORD-1048" are listed.\n'
        '2. The system shall open the Orders list filtered to the orders still owing money when '
        '"Unpaid or overdue" is tapped.\n'
        'Sample input: tap "Unpaid or overdue". Result: "ORD-1042", "ORD-1043" and "ORD-1045" '
        "are listed.\n"
        '3. The system shall open the Held Orders screen when "Held orders" is tapped.\n'
        '4. The system shall open the Orders list filtered to the three packable orders when '
        '"To pack" is tapped.\n'
        "\nC. Low Stock and Recent Orders\n"
        "1. The system shall list each product at or below 2 available with its AVAILABLE count, "
        'or show "No low-stock items." when there are none.\n'
        '2. The system shall open the product\'s Stock detail when a low-stock row is tapped.\n'
        '3. The system shall open that exact order when an order in Recent orders [Table] is tapped.\n'
    ),
    scenarios=[
        ("A. Open the Dashboard",
         "the Owner is signed in",
         'the Owner taps "Home" on the bottom bar',
         'the system shall show "Today\'s sales", the four tiles, "Low stock alerts" and '
         '"Recent orders".'),
        ("B. Today's sales includes unpaid orders",
         "the Owner is on the Dashboard",
         "the Owner compares \"Today's sales\" with today's order rows",
         'the system shall count each of today\'s orders at its FULL value, including the '
         'unpaid "ORD-1051" and "ORD-1050".'),
        ("C. Pending orders tile",
         "the Owner is on the Dashboard and the tile reads 4",
         'the Owner taps "Pending orders"',
         'the system shall open Orders filtered to "ORD-1051", "ORD-1050", "ORD-1049" and '
         '"ORD-1048" — exactly the four it counted.'),
        ("D. Unpaid or overdue tile",
         "the Owner is on the Dashboard and the tile reads 3",
         'the Owner taps "Unpaid or overdue"',
         'the system shall open Orders filtered to "ORD-1042", "ORD-1043" and "ORD-1045".'),
        ("E. Held orders tile",
         "the Owner is on the Dashboard and the tile reads 4",
         'the Owner taps "Held orders"',
         "the system shall open the Held Orders screen listing the four paid held orders."),
        ("F. To pack tile",
         "the Owner is on the Dashboard and the tile reads 3",
         'the Owner taps "To pack"',
         'the system shall open Orders filtered to "ORD-1042", "ORD-1043" and "ORD-1040".'),
        ("G. Home still works after a tile",
         'the Owner is on the list a tile opened',
         'the Owner taps "Home" on the bottom bar',
         "the system shall return to the Dashboard in one tap, with every other tab still "
         "clickable. The Owner is never trapped in the list."),
        ("H. Low stock alerts",
         "the Owner is on the Dashboard",
         'the Owner reads "Low stock alerts"',
         'the system shall list each item at or below 2 available using its AVAILABLE count, '
         'or show "No low-stock items."'),
        ("I. Recent orders open the order",
         "the Owner is on the Dashboard",
         'the Owner taps a row in "Recent orders"',
         "the system shall open that order's detail with the same number, customer and total."),
    ],
    summary="View the dashboard figures and open the list behind each one.",
)

# ===========================================================================
# A-03  Manage products and stock
# ===========================================================================
epic(
    no="A-03",
    title="Manage Products and Inventory",
    persona="As the Owner",
    want="add products, set their prices and adjust stock",
    so_that="so that the shop's catalog and stock counts stay correct",
    sample=(
        "Sample Data\n"
        '• CHW "Chiikawa Wipes": 32 on hand, 20 reserved, 12 available, pack cost "₱380.00", '
        '18 pieces per pack, size "XS", 30% markup\n'
        '• Calculator chain: unit cost "₱21.11", 3% freebie "₱0.63", packaging "₱5.00", '
        'suggested retail "₱34.77"\n'
        '• Low stock: SCWH "1 available"; Out of stock: P5T "0 available"\n'
        '• Free code for a new product: "CMEYE" (Cinnamoroll Eye Mask). Duplicate code to try: "CHW"\n'
        '• Variants: "MPBOW-PK" (Pink) and "MPBOW-LV" (Lavender) under parent "MPBOW"'
    ),
    sections=(
        "A. Stock List\n"
        '1. The system shall show the Stock list with a "Search product or code" [Field], the '
        '"All", "Low stock" and "Out of stock" [Tab] chips, and "+ Add product" [Button].\n'
        '2. The system shall filter the list as the search text is typed, matching the product '
        "name or the product code.\n"
        '3. The system shall show, for every product, its "On hand", "Reserved" and "Available" '
        '[Display/Label] counts.\n'
        "4. The system shall compute Available = On hand − Reserved, and show that arithmetic on "
        "the detail card.\n"
        "\nB. Price Calculator\n"
        '1. The system shall show a "Price calculation" [Display/Label] block on the product '
        'detail with "Pack cost", "Pieces per pack", "Unit cost", "3% freebie", "Packaging" and '
        '"Suggested retail price".\n'
        'Sample input: pack cost "₱380.00" over 18 pieces, size "XS", 30% markup. Result: unit '
        'cost "₱21.11", freebie "₱0.63", packaging "₱5.00", suggested retail "₱34.77".\n'
        "2. The system shall read the markup and freebie rates from the saved Settings rather "
        "than from fixed values, so changing them changes the next quote.\n"
        "\nC. Add and Edit a Product\n"
        '1. The system shall open the Add product form when "+ Add product" is tapped.\n'
        '2. The system shall display "Complete the required fields." when the name or code is blank.\n'
        '3. The system shall display "Product code already exists." when the code is already used.\n'
        'Sample input: Product code "CHW".\n'
        '4. The system shall save the product and show "Product saved." when every field is valid.\n'
        'Sample input: Product name "Cinnamoroll Eye Mask", Product code "CMEYE", Price "30".\n'
        "5. The system shall keep the product code read-only while editing an existing product, "
        "so a code that cannot change is never offered as if it could.\n"
        "\nD. Adjust Stock\n"
        '1. The system shall open the Adjust stock form with a "New On hand" [Field] and an optional '
        '"Reason" [Field].\n'
        '2. The system shall display "Enter an on hand count of zero or more." when the count is negative.\n'
        '3. The system shall save the adjustment and show "Stock adjusted." when the count is valid.\n'
        'Sample input: New On hand "40" with Reason "Recount"; then New On hand "32" with the reason blank.\n'
        "4. The system shall record every adjustment in Edit History, including those with no reason.\n"
        "\nE. Deactivate a Product\n"
        "1. The system shall hide a deactivated product from the Customer catalog while keeping "
        "it on every past order and invoice.\n"
    ),
    scenarios=[
        ("A. Open the Stock list",
         "the Owner is signed in",
         'the Owner taps "Stock" on the bottom bar',
         'the system shall show the product list with the search field and the "All", '
         '"Low stock" and "Out of stock" chips.'),
        ("B. Search by code",
         "the Owner is on the Stock list",
         'the Owner types "SCWH" into "Search product or code"',
         'the system shall show only "SCWH", hiding the other products.'),
        ("C. Stock tabs",
         "the Owner is on the Stock list",
         'the Owner taps "Low stock" and then "Out of stock"',
         'the system shall show "SCWH" under Low stock and "P5T" under Out of stock.'),
        ("D. Read the stock arithmetic",
         "the Owner is on the Stock list",
         'the Owner opens "CHW"',
         'the system shall show "On hand" 32, "Reserved" 20 and "Available" 12, with '
         '"32 − 20 = 12" stated on the card.'),
        ("E. The price calculator",
         "the Owner is on the CHW product detail",
         'the Owner reads "Price calculation"',
         'the system shall show unit cost "₱21.11", 3% freebie "₱0.63", packaging "₱5.00" and '
         'suggested retail "₱34.77".'),
        ("F. A negative stock count is refused",
         "the Owner is on Adjust stock for CHW",
         'the Owner types "-5" into "New On hand" and taps "Save adjustment"',
         'the system shall display "Enter an on hand count of zero or more." and change nothing.'),
        ("G. Adjust stock with a reason",
         "the Owner is on Adjust stock for CHW",
         'the Owner types "40" into "New On hand", "Recount" into "Reason" and taps "Save adjustment"',
         'the system shall show "Stock adjusted.", set On hand to 40 and record the adjustment '
         "with its reason in Edit History."),
        ("H. Adjust stock without a reason",
         "the Owner is on Adjust stock for CHW",
         'the Owner types "32" into "New On hand", leaves "Reason" blank and taps "Save adjustment"',
         'the system shall show "Stock adjusted." and record the adjustment with the reason '
         'noted as absent. A blank reason is allowed.'),
        ("I. Duplicate product code",
         "the Owner is on the Add product form",
         'the Owner enters Product name "Test Duplicate", Product code "CHW" and taps "Save product"',
         'the system shall display "Product code already exists." and save nothing.'),
        ("J. Add a new product",
         "the Owner is on the Add product form",
         'the Owner enters Product name "Cinnamoroll Eye Mask", Product code "CMEYE", '
         'Price "30" and taps "Save product"',
         'the system shall show "Product saved." and list "CMEYE" on the Stock list.'),
    ],
    summary="Add products, set their prices and adjust stock.",
)

# ===========================================================================
# A-04  Process Orders
# ===========================================================================
epic(
    no="A-04",
    title="Process Orders",
    persona="As the Owner",
    want="send invoices, edit and cancel orders",
    so_that="so that every order moves through the right steps and stock stays correct",
    sample=(
        "Sample Data\n"
        '• Incoming orders include "ORD-1051" (Mika Tan, ₱70.00) and "ORD-1049" (Lea Castro, '
        'held, ₱220.00)\n'
        '• Overdue: "ORD-1045" (Mika Tan, ₱400.00, past its 24-hour window)\n'
        '• Packed but unpaid, so it cannot be cancelled: "ORD-1043" (Jessa Reyes)\n'
        '• Paid, so it cannot be cancelled: "ORD-1040" (Jessa Reyes)\n'
        '• Bad fee text: "abc"; a real fee: "80"'
    ),
    sections=(
        "A. Orders List\n"
        '1. The system shall show the Orders list with a "Search order number or customer" '
        '[Field] and the "All", "Incoming", "Waiting for Payment", "To Pack", "Shipped", '
        '"Completed" and "Cancelled" [Tab] chips.\n'
        '2. The system shall show each order\'s number, customer, date and total, with a separate '
        "payment badge and stage badge, so an order can read \"Partially Paid\" and \"To Pack\" at once.\n"
        '3. The system shall filter to the tapped tab, where the seven tabs partition the stages '
        "so an order is in exactly one.\n"
        '4. The system shall display "No orders found." when the search matches nothing.\n'
        "\nB. Confirm and Send Invoice\n"
        '1. The system shall show a "Shipping fee" [Field] with "Leave 0.00 for a held order" and '
        'a "Confirm & Send Invoice" [Button] while the order is Order Placed.\n'
        '2. The system shall display "Enter a valid shipping fee." when the fee is not a number.\n'
        'Sample input: Shipping fee "abc".\n'
        "3. The system shall accept a blank fee as ₱0.00.\n"
        '4. The system shall show a confirmation dialog before sending the invoice.\n'
        "5. The system shall assign the invoice number \"MP-####\", set the stage to \"Waiting for "
        'Payment" and start the 24-hour payment window when the invoice is confirmed.\n'
        'Sample input: confirm "ORD-1051" with fee "80". Result: "MP-1051" is assigned.\n'
        "\nC. Edit an Unsettled Order\n"
        "1. The system shall allow the quantities of an Order Placed order to be edited, updating "
        "the totals and the reserved stock.\n"
        "2. The system shall refuse to edit an order that has already been invoiced.\n"
        "\nD. Cancel and Restore\n"
        '1. The system shall require a reason and a confirmation dialog before cancelling.\n'
        '2. The system shall display "Paid orders cannot be cancelled." for a Paid order.\n'
        '3. The system shall display "Packed orders cannot be cancelled." for a packed order.\n'
        '4. The system shall return the reserved stock EXACTLY ONCE when an order is cancelled, '
        "and record who cancelled, the reason and the time.\n"
        '5. The system shall display "Order restored." and reserve the stock again when a '
        'cancelled order is restored, returning it to Order Placed and clearing its invoice number '
        "and payment window.\n"
    ),
    scenarios=[
        ("A. Open the Orders list",
         "the Owner is signed in",
         'the Owner taps "Orders" on the bottom bar',
         'the system shall show the search field, the seven filter chips and every order with '
         "its payment and stage badges."),
        ("B. Filter to Incoming",
         "the Owner is on the Orders list",
         'the Owner taps "Incoming"',
         'the system shall list only the four Order Placed orders "ORD-1051", "ORD-1050", '
         '"ORD-1049" and "ORD-1048".'),
        ("C. Search an order",
         "the Owner is on the Orders list",
         'the Owner types "ORD-1042" into "Search order number or customer"',
         'the system shall show only "ORD-1042".'),
        ("D. Search with no match",
         "the Owner is on the Orders list",
         'the Owner types "zzz" into the search field',
         'the system shall display "No orders found."'),
        ("E. Invalid shipping fee",
         "the Owner is on the detail of \"ORD-1051\"",
         'the Owner types "abc" into "Shipping fee", taps "Confirm & Send Invoice" and confirms',
         'the system shall display "Enter a valid shipping fee." and send nothing.'),
        ("F. Confirm and send the invoice",
         "the Owner is on the detail of \"ORD-1051\"",
         'the Owner types "80" into "Shipping fee", taps "Confirm & Send Invoice" and confirms',
         'the system shall assign "MP-1051", set the stage to "Waiting for Payment" and start '
         "the 24-hour payment window."),
        ("G. Confirm a held order with no fee",
         "the Owner is on the detail of the held order \"ORD-1049\"",
         'the Owner leaves "Shipping fee" blank, taps "Confirm & Send Invoice" and confirms',
         'the system shall accept ₱0.00 as the fee and assign "MP-1049". A blank fee is valid.'),
        ("H. A paid order cannot be cancelled",
         "the Owner is on the detail of the paid order \"ORD-1040\"",
         "the Owner looks for a way to cancel it",
         'the system shall state "Paid orders cannot be cancelled." and offer no Cancel control.'),
        ("I. A packed order cannot be cancelled",
         "the Owner is on the detail of the packed order \"ORD-1043\"",
         'the Owner tries to cancel it',
         'the system shall state "Packed orders cannot be cancelled." and change nothing.'),
        ("J. Cancel the overdue order",
         "the Owner is on the detail of the overdue order \"ORD-1045\"",
         'the Owner taps "Cancel order", types "Overdue" as the reason and confirms',
         'the system shall show "Order cancelled. Reserved stock returned.", return the '
         "reserved stock exactly once, and record the owner, the reason and the time."),
        ("K. Restore the cancelled order",
         "the cancelled order \"ORD-1045\" is open",
         'the Owner taps "Restore"',
         'the system shall show "Order restored.", reserve the stock again, and return the order '
         'to "Order Placed" with its invoice number and payment window cleared.'),
    ],
    summary="Send invoices, edit and cancel orders, and keep stock correct.",
)

# ===========================================================================
# A-05  Payments and invoices
# ===========================================================================
epic(
    no="A-05",
    title="Verify Payments and Invoices",
    persona="As the Owner",
    want="record a payment, lock a paid order and open its invoice",
    so_that="so that the balance of every order is always right",
    sample=(
        "Sample Data\n"
        '• "ORD-1042" (Pia Santos): total "₱880.00", paid "₱600.00", balance "₱280.00"\n'
        '• "ORD-1035" (Jessa Reyes) is already In Transit with tracking "JT 2612 3456 7890"\n'
        '• Payment methods offered: "GCash", "Maya", "Bank Transfer"\n'
        '• Amounts to try: "9999" (over the balance), then "100" (a partial payment)'
    ),
    sections=(
        "A. Payment Screen\n"
        '1. The system shall show "Total", "Paid" and "Balance" [Display/Label] on the order detail.\n'
        '2. The system shall open the payment form when "Record payment" [Button] is tapped, with '
        'an "Amount received" [Field] and the "GCash", "Maya" and "Bank Transfer" [Tab] choices.\n'
        "3. The system shall reject an amount that is zero, negative or above the remaining balance.\n"
        'Sample input: Amount received "9999" against a balance of "₱280.00".\n'
        "4. The system shall keep the original order Total and lower only the Balance when a "
        "partial payment is saved.\n"
        'Sample input: Amount received "100". Result: Paid rises by ₱100.00, Balance falls by '
        "₱100.00 and Total is unchanged.\n"
        '5. The system shall show "This order will be marked as Paid." and mark the order Paid when '
        "a payment clears the balance, locking it from editing and cancelling.\n"
        "\nB. Unmark Paid\n"
        '1. The system shall allow "Unmark Paid" only before the order is dispatched, and record '
        "the change in Edit History.\n"
        '2. The system shall refuse to unmark paid after the order is In Transit or Received, '
        'because the money has already gone with the courier.\n'
        "\nC. Invoice\n"
        '1. The system shall show the full invoice when "View invoice" [Button] is tapped.\n'
        '2. The system shall export a PDF and save an image when "Export PDF" and "Share image" '
        "are tapped, without crashing.\n"
        "3. The system shall present the Customer's invoice as strictly read-only, with no edit or "
        "delete control anywhere on it.\n"
    ),
    scenarios=[
        ("A. Read the money on an order",
         "the Owner is on the detail of \"ORD-1042\"",
         "the Owner reads the Payment block",
         'the system shall show Total "₱880.00", Paid "₱600.00" and Balance "₱280.00".'),
        ("B. An amount over the balance is refused",
         "the Owner is on the payment form for \"ORD-1042\"",
         'the Owner types "9999" into "Amount received" and taps "Save payment"',
         'the system shall display "Enter an amount greater than zero and not above the balance." '
         "and record nothing."),
        ("C. A partial payment",
         "the Owner is on the payment form for \"ORD-1042\"",
         'the Owner types "100" into "Amount received" and taps "Save payment"',
         'the system shall show "Payment recorded.", raise Paid to ₱700.00 and lower Balance to '
         "₱180.00, leaving Total at ₱880.00."),
        ("D. View the invoice",
         "the Owner is on the detail of an invoiced order",
         'the Owner taps "View invoice"',
         "the system shall show the full invoice with the order number, customer, items and total."),
        ("E. Export the invoice",
         "the Owner is on the invoice screen",
         'the Owner taps "Export PDF"',
         "the system shall save the PDF and show the path it was saved to."),
        ("F. Share the invoice image",
         "the Owner is on the invoice screen",
         'the Owner taps "Share image"',
         "the system shall save the image and open the Android share sheet so it can be sent."),
        ("G. A dispatched order keeps Paid",
         "the Owner is on the detail of \"ORD-1035\", which is In Transit",
         'the Owner looks for "Unmark Paid"',
         'the system shall keep the tracking number "JT 2612 3456 7890" and not offer '
         '"Unmark Paid", because a dispatched order can no longer be unmarked as Paid.'),
    ],
    summary="Record a payment, lock a paid order and open its invoice.",
)

# ===========================================================================
# A-06  Hold and combine
# ===========================================================================
epic(
    no="A-06",
    title="Hold and Combine Orders",
    persona="As the Owner",
    want="combine a customer's paid held orders into one shipment",
    so_that="so that a customer who ordered twice pays one delivery",
    sample=(
        "Sample Data\n"
        '• "ORD-1038" (Anne Garcia): paid, held 7 days, contains the electronic "SCWH" — the '
        "1-week advisory SHOWS\n"
        '• "ORD-1039" (Bea Cruz): paid, held 3 days, contains "SCWH" — no advisory\n'
        '• "ORD-1036" (Mika Tan, ₱400.00) and "ORD-1037" (Mika Tan, ₱360.00): both paid, held '
        'for combined shipping, non-electronic — no advisory; combining gives "₱760.00"\n'
        '• Mixed customers to try: "ORD-1038" with "ORD-1036"'
    ),
    sections=(
        "A. Held Orders Screen\n"
        '1. The system shall list the PAID orders bought with "Hold For Combined Shipping" and '
        "show how many days each has been held.\n"
        "2. The system shall show a 1-week advisory ONLY when the order contains an electronic "
        "item AND has been held at least 7 days.\n"
        "3. The system shall show no advisory for a non-electronic order however long it is held, "
        "and none for an electronic order held under 7 days.\n"
        "\nB. Combine Orders\n"
        '1. The system shall let the Owner tick two or more held orders and tap "Combine Shipments" '
        '[Button].\n'
        '2. The system shall display "Select held orders for the same customer." when the ticked '
        "orders belong to different customers.\n"
        '3. The system shall show a confirmation dialog naming the combined total before combining.\n'
        '4. The system shall keep each original order and its own invoice intact after combining, '
        "joining them by a shared shipment number instead of replacing them.\n"
    ),
    scenarios=[
        ("A. Open Held Orders",
         "the Owner is signed in",
         'the Owner taps "Held orders" on the Dashboard',
         "the system shall list the four paid held orders with their days held."),
        ("B. The 1-week advisory shows",
         "the Owner is on Held Orders",
         'the Owner reads the row for "ORD-1038", held 7 days with an electronic item',
         "the system shall show the 1-week advisory on that row."),
        ("C. No advisory under 7 days",
         "the Owner is on Held Orders",
         'the Owner reads the row for "ORD-1039", held 3 days with an electronic item',
         "the system shall show no advisory on that row."),
        ("D. No advisory when not electronic",
         "the Owner is on Held Orders",
         'the Owner reads the row for "ORD-1036", held 10 days with no electronic item',
         "the system shall show no advisory on that row, however long it has been held."),
        ("E. Combine two orders of one customer",
         "the Owner is on Held Orders",
         'the Owner ticks "ORD-1036" and "ORD-1037", taps "Combine Shipments" and confirms',
         'the system shall show the combined total "₱760.00" and keep both original orders with '
         "their own totals and invoices."),
        ("F. Mixed customers are blocked",
         "the Owner is on Held Orders",
         'the Owner ticks "ORD-1038" and "ORD-1036" and taps "Combine Shipments"',
         'the system shall display "Select held orders for the same customer." and combine nothing.'),
    ],
    summary="Combine a customer's paid held orders into one shipment.",
)

# ===========================================================================
# A-07  Pack and ship
# ===========================================================================
epic(
    no="A-07",
    title="Pack and Ship Orders",
    persona="As the Owner",
    want="check off the items, mark an order packed and dispatch it",
    so_that="so that nothing ships short and stock leaves the shop exactly once",
    sample=(
        "Sample Data\n"
        '• "ORD-1040" (Jessa Reyes): paid, 3 items — CHW x2, HKEYE x2, MPBOW-LV x2 — the '
        "packing walk\n"
        '• Delivery routes: "J&T Express", "TikTok Shop", "Lalamove/Grab", "Pickup"\n'
        '• CHW before dispatch: 32 on hand, 20 reserved, 12 available. ORD-1040 ships CHW x2, so '
        'after dispatch it is 30 / 18 / 12 — Available is unchanged.'
    ),
    sections=(
        "A. Packing Checklist\n"
        '1. The system shall show every item on the order with a checkbox when "Pack order" '
        '[Button] is tapped.\n'
        '2. The system shall display "Check all items before marking the order as packed." when '
        '"Mark as packed" is tapped with any item unchecked.\n'
        '3. The system shall show a confirmation dialog and then set the order to Packed with a '
        "timestamp when every item is checked and \"Mark as packed\" is confirmed.\n"
        "\nB. Shipping Details\n"
        '1. The system shall display "Shipping details copied." after "Copy shipping details" '
        "[Button] places the recipient, mobile, address and total on the clipboard.\n"
        '2. The system shall offer the four delivery routes "J&T Express", "TikTok Shop", '
        '"Lalamove/Grab" and "Pickup".\n'
        '3. The system shall display "Enter the tracking number first." when "In Transit" is '
        "chosen with no tracking number.\n"
        "\nC. Dispatch and Receipt\n"
        "1. The system shall reduce BOTH On hand and Reserved by the shipped quantity on the FIRST "
        "dispatch only, so Available never double-subtracts.\n"
        'Sample input: CHW 32 / 20 / 12 dispatching 2. Result: 30 / 18 / 12.\n'
        '2. The system shall set the stage to "Shipped" on dispatch and to "Completed" when the '
        "order is marked Received, keeping the tracking number throughout.\n"
    ),
    scenarios=[
        ("A. Open the packing checklist",
         "the Owner is on the detail of \"ORD-1040\"",
         'the Owner taps "Pack order"',
         "the system shall list all three items with a checkbox each and a count of how many are "
         "checked."),
        ("B. Mark packed too early",
         "the Owner is on the packing checklist for \"ORD-1040\"",
         'the Owner taps "Mark as packed" with items still unchecked',
         'the system shall display "Check all items before marking the order as packed." and '
         "leave the order unpacked."),
        ("C. Mark packed after checking every item",
         "the Owner is on the packing checklist for \"ORD-1040\"",
         'the Owner checks all three items, taps "Mark as packed" and confirms',
         'the system shall show the order as "Packed" with the time it was packed.'),
        ("D. Copy the shipping details",
         "the Owner is on the shipping screen for \"ORD-1040\"",
         'the Owner taps "Copy shipping details"',
         'the system shall display "Shipping details copied." and put the recipient, mobile, '
         "address and total on the clipboard."),
        ("E. In Transit needs a tracking number",
         "the Owner is on the shipping screen with no tracking number",
         'the Owner taps "In Transit"',
         'the system shall display "Enter the tracking number first." and leave the status alone.'),
        ("F. Dispatch the order",
         "the Owner has typed tracking number \"JT9999\"",
         'the Owner taps "In Transit"',
         'the system shall set the order to "In Transit", reduce both On hand and Reserved by the '
         "shipped quantity once, and show the tracking number."),
    ],
    summary="Check off the items, mark an order packed and dispatch it.",
)

# ===========================================================================
# A-08  Manage customer records
# ===========================================================================
epic(
    no="A-08",
    title="Manage Customer Records",
    persona="As the Owner",
    want="find a customer and keep a private note about them",
    so_that="so that I can see a customer's record and their orders in one place",
    sample=(
        "Sample Data\n"
        '• Customers: "Mika Tan", "Pia Santos", "Anne Garcia", "Jessa Reyes", "Lea Castro", '
        '"Marie Chu", "Bea Cruz"\n'
        '• Search text to try: "Mika"; no-match text: "zzz"\n'
        '• A note to try: "Prefers Wednesday pickups"'
    ),
    sections=(
        "A. Customer List\n"
        '1. The system shall show the customer list with a "Search name or email" [Field].\n'
        "2. The system shall filter the list as the search text is typed, matching the name or "
        "the email address.\n"
        "3. The system shall show a clear empty state when the search matches no customer.\n"
        "\nB. Customer Profile\n"
        '1. The system shall open the customer\'s profile when a row is tapped, showing their '
        "contact details and their orders.\n"
        '2. The system shall open that order when one of the customer\'s orders is tapped.\n'
        "\nC. Private Review Note\n"
        '1. The system shall let the Owner add a note with "Add note" and "Save note" [Button].\n'
        '2. The system shall let the Owner remove the note again with "Remove note" [Button].\n'
        "3. The system shall never show this note in Customer Mode, because only the owner reads "
        "that field.\n"
    ),
    scenarios=[
        ("A. Open the customer list",
         "the Owner is signed in",
         'the Owner taps "Customers" on the bottom bar',
         "the system shall list every customer with the search field."),
        ("B. Search a customer",
         "the Owner is on the customer list",
         'the Owner types "Mika" into "Search name or email"',
         'the system shall show only "Mika Tan".'),
        ("C. Search with no match",
         "the Owner is on the customer list",
         'the Owner types "zzz" into the search field',
         "the system shall show a clear empty state instead of an empty list with no message."),
        ("D. Open a customer profile",
         "the Owner is on the customer list",
         'the Owner taps "Mika Tan"',
         "the system shall show her contact details and her orders."),
        ("E. Add a private note",
         "the Owner is on a customer's profile",
         'the Owner taps "Add note", types "Prefers Wednesday pickups" and taps "Save note"',
         "the system shall save and show the note on the profile."),
        ("F. Remove the note",
         "the Owner is on a customer's profile that has a note",
         'the Owner taps "Remove note"',
         "the system shall clear the note from the profile."),
    ],
    summary="Find a customer and keep a private note about them.",
)

# ===========================================================================
# A-09  Reports
# ===========================================================================
epic(
    no="A-09",
    title="View Sales and Stock Reports",
    persona="As the Owner",
    want="check the sales and stock reports for a date range",
    so_that="so that I can see what sold and what is left without counting by hand",
    sample=(
        "Sample Data\n"
        '• Report tabs: "Sales" and "Stock"\n'
        '• Date fields: "Start date" and "End date", applied with "Apply" [Button]\n'
        '• A valid range covers October to November 2026; a reversed range puts the end before '
        "the start.\n"
        '• The 13 seeded orders are inside the default range.'
    ),
    sections=(
        "A. Reports Screen\n"
        '1. The system shall show the Reports screen with a "Sales" [Tab] and a "Stock" [Tab].\n'
        '2. The system shall show "Start date" and "End date" [Field] with an "Apply" [Button].\n'
        "\nB. Sales Report\n"
        "1. The system shall list the orders placed inside the range with each order's value.\n"
        "2. The system shall count an order at its FULL value even when it is unpaid.\n"
        "3. The system shall exclude a cancelled order from the sales figures.\n"
        "\nC. Stock Report\n"
        '1. The system shall list every active product with its "On hand", "Reserved" and '
        '"Available" counts.\n'
        "\nD. Date Validation\n"
        '1. The system shall display "The end date cannot be earlier than the start date." and '
        "keep the previous report when the end date is before the start date.\n"
    ),
    scenarios=[
        ("A. Open Reports",
         "the Owner is signed in",
         'the Owner taps "More" then "Reports"',
         'the system shall show the "Sales" and "Stock" tabs with "Start date", "End date" and "Apply".'),
        ("B. Read the sales report",
         "the Owner is on the Sales tab",
         'the Owner reads the order list',
         "the system shall list each order placed inside the range with its value."),
        ("C. Unpaid orders still count",
         "the Owner is on the Sales tab",
         'the Owner compares the total with the order rows',
         "the system shall count every order at its full value, including the unpaid ones."),
        ("D. A cancelled order drops out",
         "the Owner has cancelled an order and is on the Sales tab",
         'the Owner re-applies the same range',
         "the system shall leave the cancelled order out of the sales figures."),
        ("E. Read the stock report",
         "the Owner is on the Stock tab",
         'the Owner reads a product row',
         'the system shall show its "On hand", "Reserved" and "Available" counts.'),
        ("F. A reversed range is refused",
         "the Owner is on Reports",
         "the Owner sets the end date before the start date and taps \"Apply\"",
         'the system shall display "The end date cannot be earlier than the start date." and keep '
         "the previous report."),
    ],
    summary="Check the sales and stock reports for a date range.",
)

# ===========================================================================
# A-10  Settings, tools and account
# ===========================================================================
epic(
    no="A-10",
    title="Manage Settings",
    persona="As the Owner",
    want="set the shop's rates and lists, read the edit history, back up, and sign out",
    so_that="so that the shop's rules and records are under my control",
    sample=(
        "Sample Data\n"
        '• Seeded rates: markup "30", freebie "3"; a rate to reject: "150"\n'
        '• A category to add: "Kawaii"; a character to add: "Cinnamoroll"\n'
        '• The offline switch lives here, and every write is read-only while it is on.\n'
        '• Backup writes "momompurin-backup.json" and reports "Backup saved."'
    ),
    sections=(
        "A. Settings\n"
        '1. The system shall show a "Markup rate" and a "Freebie rate" [Field] with a "Save" [Button].\n'
        '2. The system shall display "Enter a rate between 0 and 100." when a rate is outside that range.\n'
        "3. The system shall use the SAVED rates for the next price calculation, so changing a "
        "rate changes the quoted price.\n"
        "\nB. Managed Lists\n"
        '1. The system shall let the Owner add a category with "New category" and "Add category", '
        'and a character with "New character" and "Add character".\n'
        "2. The system shall list every managed option, including one just added.\n"
        "3. The system shall offer the managed categories and characters in the Add product form.\n"
        "\nC. Edit History\n"
        "1. The system shall list every stock change, product save, order edit, payment and "
        "cancellation, newest first, with what changed and when.\n"
        "\nD. Tools and Offline Mode\n"
        '1. The system shall write a local backup with "Back up data" and report "Backup saved."\n'
        '2. The system shall provide an "Offline mode" [Checkbox/Toggle].\n'
        "3. The system shall refuse every write while offline and say it is read-only, so no "
        "screen can quietly change data.\n"
        "\nE. Sign Out\n"
        '1. The system shall show a confirmation and then return to Sign in when "Log out" [Button] '
        "is tapped.\n"
        "2. The system shall clear the session and the whole screen stack, so the system Back "
        "gesture cannot re-enter Owner or Customer mode.\n"
    ),
    scenarios=[
        ("A. Open Settings",
         "the Owner is signed in",
         'the Owner taps "More" then "Settings"',
         'the system shall show "Markup rate", "Freebie rate" and "Save".'),
        ("B. A rate above 100 is refused",
         "the Owner is on Settings",
         'the Owner types "150" into "Markup rate" and taps "Save"',
         'the system shall display "Enter a rate between 0 and 100." and save nothing.'),
        ("C. A saved rate changes the next quote",
         "the Owner has saved a markup of 50",
         'the Owner opens the Add product form and enters a pack cost of "380" over 18 pieces',
         'the system shall quote a suggested retail price at 50%, not the old 30%.'),
        ("D. Add a category",
         "the Owner is on Settings",
         'the Owner types "Kawaii" into "New category" and taps "Add category"',
         'the system shall save it and list "Kawaii" among the categories.'),
        ("E. The new category reaches Add product",
         "the Owner has added the category \"Kawaii\"",
         'the Owner opens the Add product form and opens the Category control',
         'the system shall offer "Kawaii" as a choice.'),
        ("F. Add a character",
         "the Owner is on Settings",
         'the Owner types "Cinnamoroll" into "New character" and taps "Add character"',
         'the system shall save it and list "Cinnamoroll" among the characters.'),
        ("G. Read the edit history",
         "the Owner is on Settings",
         'the Owner taps "Edit history"',
         "the system shall list the recorded changes newest first with what changed and when."),
        ("H. Back up the data",
         "the Owner is on Settings",
         'the Owner taps "Back up data"',
         'the system shall write the backup file and report "Backup saved." with its path.'),
        ("I. Offline mode is read-only",
         "the Owner has switched \"Offline mode\" on",
         'the Owner tries to save a stock adjustment',
         "the system shall refuse the write and say it is read-only while offline. "
         "The Owner then switches \"Offline mode\" back OFF before moving on, so the "
         "Customer Module (A-11 to A-17) is never blocked in a continuous run."),
        ("J. Sign out",
         "the Owner has switched \"Offline mode\" back off and is signed in",
         'the Owner taps "Log out" and confirms',
         'the system shall return to Sign in with no bottom bar, and the system Back gesture '
         "shall not re-enter Owner mode."),
    ],
    summary="Set the shop's rates and lists, read the edit history, back up and sign out.",
)

# ===========================================================================
# A-11  Create an account
# ===========================================================================
epic(
    no="A-11",
    title="Registration",
    persona="As the Customer",
    want='create a new account with my details',
    so_that="so that I can order without asking the shop to make an account for me",
    sample=(
        "Sample Data\n"
        '• A code to reuse: "mika.tan@example.com" — already registered\n'
        '• A new email: "roselle.lim@example.com", Password "roselle123"\n'
        '• Bad inputs: Email "not-an-email", Mobile "123", Password "short"\n'
        '• A complete address: Province "Metro Manila", City "Makati", Barangay "Poblacion", '
        'Full address "1 Test St."'
    ),
    sections=(
        "A. Create an Account Screen\n"
        '1. The system shall open the Create an account screen when "Create an account" is tapped '
        'on Sign in, with "Full name", "Messenger or TikTok handle", "Email", "Mobile number", '
        '"Province", "City", "Barangay", "Full address", "Password" [Field] and a "Create account" '
        "[Button].\n"
        "2. The system shall create a CUSTOMER account only, and shall show no role selector, "
        "because public registration can never create an Owner.\n"
        "\nB. Validation\n"
        '1. The system shall display "Complete the required fields." when a required field is blank.\n'
        'Sample input: everything left blank, then "Create account".\n'
        '2. The system shall display "Enter a valid email address." when the email is malformed.\n'
        'Sample input: Email "not-an-email".\n'
        '3. The system shall display "Enter a valid mobile number with 11 digits." when the mobile '
        "is not 11 digits.\n"
        'Sample input: Mobile "123".\n'
        '4. The system shall display "Use at least 8 characters." when the password is too short.\n'
        '5. The system shall display "An account with this email already exists." when the email is '
        "already registered.\n"
        'Sample input: Email "mika.tan@example.com".\n'
        "\nC. Successful Registration\n"
        "1. The system shall create the account, sign the Customer in and open the Shop when every "
        "field is valid.\n"
    ),
    scenarios=[
        ("A. Open Create an account",
         "the Customer is on the Sign in screen",
         'the Customer taps "Create an account"',
         'the system shall open the Create an account screen with every field and a "Create account" button.'),
        ("B. No role selector",
         "the Customer is on the Create an account screen",
         "the Customer looks for an Owner/Customer choice",
         "the system shall show no role selector: public registration always creates a Customer."),
        ("C. Required fields",
         "the Customer is on the Create an account screen",
         'the Customer leaves the fields blank and taps "Create account"',
         'the system shall display "Complete the required fields." and create nothing.'),
        ("D. Invalid email",
         "the Customer has filled every field",
         'the Customer types "not-an-email" into Email and taps "Create account"',
         'the system shall display "Enter a valid email address." and create nothing.'),
        ("E. Invalid mobile",
         "the Customer has filled every field",
         'the Customer types "123" into "Mobile number" and taps "Create account"',
         'the system shall display "Enter a valid mobile number with 11 digits." and create nothing.'),
        ("F. Short password",
         "the Customer has filled every field",
         'the Customer types "short" into Password and taps "Create account"',
         'the system shall display "Use at least 8 characters." and create nothing.'),
        ("G. Email already registered",
         "the Customer has filled every field",
         'the Customer types "mika.tan@example.com" into Email and taps "Create account"',
         'the system shall display "An account with this email already exists." and create nothing.'),
        ("H. Successful registration",
         "the Customer has filled every field correctly",
         'the Customer taps "Create account"',
         "the system shall create the Customer account, sign them in and open the Shop."),
    ],
    summary="Create a Customer account with my own details.",
)

# ===========================================================================
# A-12  Customer sign in
# ===========================================================================
epic(
    no="A-12",
    title="Sign In",
    persona="As the Customer",
    want='sign in with my email "mika.tan@example.com" and password "customer123"',
    so_that="so that I can shop without giving the shop my details again",
    sample=(
        "Sample Data\n"
        '• Customer account: Email "mika.tan@example.com", Password "customer123"\n'
        '• The same Sign in screen serves both modes; the account decides which one opens.\n'
        '• Wrong password: "wrongpass"'
    ),
    sections=(
        "A. One Sign In Screen\n"
        "1. The system shall use the SAME Sign in screen for Customers and the Owner, with no role "
        "selector, because the account found by the email decides the mode.\n"
        '2. The system shall mask the password and change "Show" to "Hide" when the toggle is tapped.\n'
        "\nB. Routing by Account\n"
        '1. The system shall open the Shop with the Customer bottom bar (Shop, Cart, Orders, '
        "Account) when the credentials belong to a Customer.\n"
        '2. The system shall open the Dashboard with the Owner bottom bar when the credentials '
        "belong to the Owner.\n"
        '3. The system shall display "Incorrect email or password." and stay on Sign in when '
        "either is wrong.\n"
    ),
    scenarios=[
        ("A. Sign in as a Customer",
         "the Customer is on the Sign in screen",
         'the Customer enters "mika.tan@example.com" and "customer123" and taps "Log in"',
         'the system shall open the Shop with the Customer bottom bar (Shop, Cart, Orders, Account).'),
        ("B. The password is masked",
         "the Customer is on the Sign in screen",
         "the Customer types a password",
         "the system shall show dots instead of the letters until \"Show\" is tapped."),
        ("C. Wrong password",
         "the Customer is on the Sign in screen",
         'the Customer enters "mika.tan@example.com" with "wrongpass" and taps "Log in"',
         'the system shall display "Incorrect email or password." and stay on Sign in.'),
        ("D. No Customer bar on the auth screens",
         "no one is signed in",
         "the app is sitting on the Sign in screen",
         "the system shall show no bottom bar at all."),
    ],
    summary="Sign in as a Customer so I can shop without retyping my details.",
)

# ===========================================================================
# A-13  Browse the catalog
# ===========================================================================
epic(
    no="A-13",
    title="Browse Product Catalog",
    persona="As the Customer",
    want="search and filter the products and see what is in stock",
    so_that="so that I can find what I want and know what I can actually buy",
    sample=(
        "Sample Data\n"
        '• Out of stock: "P5T" (Pompompurin Tumbler, ₱750.00) — "Add to cart" is disabled\n'
        '• Low stock: "SCWH" (Sanrio Cosmos Watch) with 1 available\n'
        '• A variant parent: "Melody Bow Pouch" with "Pink" and "Lavender" variants\n'
        '• Search text to try: "watch"; a category chip: "Wipes"; a character chip: "Kuromi"'
    ),
    sections=(
        "A. Shop Grid\n"
        '1. The system shall show the Shop with a "Search products" [Field], category chips and '
        "character chips, and one card per sellable product.\n"
        "2. The system shall show each card's name, price, stock badge and an \"Add to cart\" "
        "[Button].\n"
        "3. The system shall display the stock badge from the live available count: \"In Stock\", "
        '"Low Stock" or "Out of Stock".\n'
        '4. The system shall DISABLE "Add to cart" on an Out of Stock product.\n'
        "\nB. Search and Chips\n"
        "1. The system shall filter the grid as the search text is typed, matching the product "
        "name or code.\n"
        "2. The system shall filter by the tapped category chip and the tapped character chip, "
        "applied together.\n"
        "\nC. No Internal Costs\n"
        "1. The system shall NEVER show a customer any internal cost: no pack cost, pieces per "
        "pack, packaging tier, markup rate or unit cost.\n"
        "\nD. Variants\n"
        '1. The system shall show one card for a product that has variants, quoting the cheapest '
        'variant as "from ₱—".\n'
        '2. The system shall open the variants when "View variants" [Button] is tapped, so the '
        "Customer can choose between them.\n"
    ),
    scenarios=[
        ("A. Open the Shop",
         "the Customer is signed in",
         'the Customer taps "Shop" on the bottom bar',
         'the system shall show the product grid with the search field and the category and '
         "character chips."),
        ("B. Search the catalog",
         "the Customer is on the Shop",
         'the Customer types "watch" into "Search products"',
         'the system shall show only "Sanrio Cosmos Watch".'),
        ("C. Filter by a category chip",
         "the Customer is on the Shop",
         'the Customer taps the "Wipes" category chip',
         "the system shall show only the products in that category."),
        ("D. Filter by a character chip",
         "the Customer is on the Shop",
         'the Customer taps the "Kuromi" character chip',
         "the system shall show only the products for that character."),
        ("E. An out-of-stock product",
         "the Customer is on the Shop",
         'the Customer looks at "P5T"',
         'the system shall show an "Out of Stock" badge and disable its "Add to cart" button.'),
        ("F. No internal costs are shown",
         "the Customer is on the Shop",
         "the Customer reads a product card",
         "the system shall show only the price, never the pack cost, pieces per pack, packaging "
         "tier, markup rate or unit cost."),
        ("G. Open the variants",
         "the Customer is on the Shop",
         'the Customer finds "Melody Bow Pouch" and taps "View variants"',
         'the system shall show the "Pink" and "Lavender" variants so one can be chosen.'),
    ],
    summary="Search and filter the products and see what is in stock.",
)

# ===========================================================================
# A-14  Manage the shopping cart
# ===========================================================================
epic(
    no="A-14",
    title="Manage Shopping Cart",
    persona="As the Customer",
    want="put products in a cart, choose how many and tick what I want",
    so_that="so that I can check out only the items I am ready to buy",
    sample=(
        "Sample Data\n"
        '• Adding to the cart reserves NO stock, so the stock badge does not move.\n'
        '• "SCWH" has 1 available, so its quantity cannot go above 1.\n'
        '• Removing the last row leaves the exact message "Your cart is empty."'
    ),
    sections=(
        "A. Adding to the Cart\n"
        '1. The system shall add the product and show "Added to cart." when "Add to cart" is tapped.\n'
        "2. The system shall NOT reserve any stock when a product is added, so the stock badge "
        "does not change.\n"
        "\nB. Cart Rows\n"
        '1. The system shall show each row\'s product, unit price, quantity and line total, with a '
        "checkbox, a \"+\" and a \"−\" [Button] and a \"Remove\" [Button].\n"
        "2. The system shall count only the TICKED rows in the subtotal.\n"
        '3. The system shall display "Insufficient stock (Only N left)." and refuse to go past the '
        "live available count.\n"
        'Sample input: "SCWH" at quantity 1, then "+". Result: "Insufficient stock (Only 1 left)."\n'
        "4. The system shall re-check every row against live stock when the Cart opens, so a unit "
        "another customer took turns the row Sold Out on sight.\n"
        "\nC. Emptying the Cart\n"
        '1. The system shall remove the row when "Remove" is tapped.\n'
        '2. The system shall display exactly "Your cart is empty." when the last row is removed, '
        "with a way to go back to browsing.\n"
    ),
    scenarios=[
        ("A. Add a product",
         "the Customer is on the Shop",
         'the Customer taps "Add to cart" on a product',
         'the system shall show "Added to cart." and leave the stock badge unchanged, because the '
         "cart reserves nothing."),
        ("B. Open the cart",
         "the Customer has at least one item in the cart",
         'the Customer taps "Cart" on the bottom bar',
         'the system shall show each row with its checkbox, quantity controls, line total and a '
         '"Remove" button.'),
        ("C. Untick a row",
         "the Customer is on the Cart with two rows ticked",
         'the Customer unticks one row',
         "the system shall leave that row out of the subtotal."),
        ("D. The quantity is capped",
         "the Customer has \"SCWH\" in the cart at quantity 1",
         'the Customer taps "+"',
         'the system shall display "Insufficient stock (Only 1 left)." and leave the quantity at 1.'),
        ("E. Decrease the quantity",
         "the Customer has a row at quantity 2",
         'the Customer taps "−"',
         "the system shall lower the quantity to 1 and lower the subtotal."),
        ("F. Remove the last row",
         "the Customer has one row in the cart",
         'the Customer taps "Remove"',
         'the system shall display exactly "Your cart is empty." with a way back to the Shop.'),
    ],
    summary="Put products in a cart, choose how many and tick what I want.",
)

# ===========================================================================
# A-15  Checkout
# ===========================================================================
epic(
    no="A-15",
    title="Checkout and Payment",
    persona="As the Customer",
    want="choose my delivery and place the order",
    so_that="so that my order reaches the shop with the right address",
    sample=(
        "Sample Data\n"
        '• The Customer\'s saved address prefills from their profile.\n'
        '• "Ship Now" sends it straight away; "Hold For Combined Shipping" keeps it until the '
        "owner combines it.\n"
        '• Payment is arranged off-app with the owner, so placing an order takes no payment.\n'
        '• Only the TICKED rows are ordered and cleared; unticked rows stay in the cart.'
    ),
    sections=(
        "A. Checkout Screen\n"
        '1. The system shall open Checkout when "Checkout" [Button] is tapped in the Cart.\n'
        "2. The system shall prefill the delivery details from the Customer's saved profile and "
        "still allow each field to be edited.\n"
        'Sample input: every delivery field is prefilled from the account.\n'
        "3. The system shall display \"Complete the delivery details.\" and place no order when a "
        "required delivery field is blank.\n"
        "\nB. Fulfillment Choice\n"
        '1. The system shall offer "Ship Now" [Tab] and "Hold For Combined Shipping" [Tab].\n'
        "\nC. Additional Shipping Fee Notice\n"
        '1. The system shall show an "Additional Shipping Fee Notice" [Popup/Modal] before '
        'placing, with "Agree & Place Order" and "Cancel" [Button].\n'
        "2. The system shall place nothing until \"Agree & Place Order\" is tapped.\n"
        "\nD. Placing the Order\n"
        "1. The system shall check availability for every line and reserve the stock atomically, "
        "so two checkouts cannot both take the last unit.\n"
        '2. The system shall display "Not enough stock for <code>." (the code is filled in) and place nothing when a line '
        "exceeds availability.\n"
        'Sample input: "SCWH" at quantity 2 while 1 is available.\n'
        "3. The system shall create the order as Order Placed with NO payment taken, because "
        "payment is arranged with the owner after the invoice is sent.\n"
        "4. The system shall clear ONLY the ticked rows from the cart, leaving the unticked ones "
        "in place.\n"
        "5. The system shall refuse to place an order while Offline Mode is on.\n"
    ),
    scenarios=[
        ("A. Open Checkout",
         "the Customer has items ticked in the cart",
         'the Customer taps "Checkout"',
         "the system shall open Checkout with the delivery details prefilled from their profile."),
        ("B. A blank delivery field",
         "the Customer is on Checkout",
         'the Customer clears a delivery field and taps "Agree & Place Order"',
         'the system shall display "Complete the delivery details." and place no order.'),
        ("C. Choose Hold For Combined Shipping",
         "the Customer is on Checkout",
         'the Customer taps "Hold For Combined Shipping"',
         "the system shall record that choice on the order so the owner can combine it later."),
        ("D. The shipping fee notice",
         "the Customer has chosen Ship Now",
         'the Customer taps "Agree & Place Order" the first time',
         'the system shall show the "Additional Shipping Fee Notice" and place nothing until it '
         "is agreed to."),
        ("E. Place the order",
         "the Customer has agreed to the notice",
         'the Customer taps "Agree & Place Order" to confirm',
         'the system shall place the order as "Order Placed" with no payment taken, reserve the '
         "stock and open the order."),
        ("F. Too few in stock",
         "the Customer has a product in the cart and another Customer has just taken the last unit",
         'the Customer taps "Agree & Place Order"',
         'the system shall display "Not enough stock for SCWH." and place nothing.'),
        ("G. Only ticked rows are ordered",
         "the Customer has two rows in the cart and ticks only one",
         'the Customer completes Checkout',
         "the system shall order and clear only the ticked row, leaving the other in the cart."),
    ],
    summary="Choose my delivery and place the order.",
)

# ===========================================================================
# A-16  Track orders
# ===========================================================================
epic(
    no="A-16",
    title="Track Orders",
    persona="As the Customer",
    want="see where my orders are and cancel one I no longer want",
    so_that="so that I know what is coming and can stop an order before it ships",
    sample=(
        "Sample Data\n"
        '• Sign in as "jessa.reyes@example.com" with Password "customer123" for the In Transit '
        'check and the no-held-orders check: Jessa owns "ORD-1035", which is "In Transit" with '
        'tracking "JT 2612 3456 7890", and she has NO held orders at all.\n'
        '• Sign in as "mika.tan@example.com" with Password "customer123" for the held checks: '
        'Mika owns the held pair "ORD-1036" + "ORD-1037" totalling "₱760.00".\n'
        '• A paid order cannot be cancelled; a cancelled order keeps who cancelled it, the reason '
        "and the time.\n"
        '• A cancel needs a reason: leaving it blank shows "Enter a reason for cancelling."'
    ),
    sections=(
        "A. My Orders\n"
        "1. The system shall list only the signed-in Customer's own orders, newest first.\n"
        "2. The system shall show THREE separate statuses on each order: payment, packing and "
        "shipping, never merged into one label.\n"
        "3. The system shall show the tracking number once the order is In Transit or Received.\n"
        "\nB. Held Orders\n"
        '1. The system shall list the Customer\'s paid held orders with a "Total held" figure.\n'
        "2. The system shall show no held-total card at all when there are no held orders, rather "
        'than a card reading "₱0.00".\n'
        "\nC. Cancelling\n"
        '1. The system shall require a reason and display "Enter a reason for cancelling." when it '
        "is blank, keeping the dialog open so the reason can be typed.\n"
        '2. The system shall display "Paid orders cannot be cancelled." and offer no Cancel for a '
        "paid order.\n"
        "3. The system shall return the reserved stock exactly once when an order is cancelled.\n"
        "\nD. History and Invoice\n"
        '1. The system shall show the Cancelled Orders history with who cancelled, why and when.\n'
        "2. The system shall open the invoice in a strictly read-only view.\n"
    ),
    scenarios=[
        ("A. Open My Orders",
         "the Customer is signed in",
         'the Customer taps "Orders" on the bottom bar',
         "the system shall list only that Customer's own orders, newest first."),
        ("B. Three separate statuses",
         "the Customer is on My Orders",
         "the Customer reads an order row",
         "the system shall show the payment, packing and shipping statuses separately."),
        ("C. A tracking number appears",
         "the Customer is signed in as \"jessa.reyes@example.com\", who owns the In Transit "
         "order \"ORD-1035\"",
         'the Customer opens "ORD-1035" from the Orders tab',
         'the system shall show the courier "J&T Express" and the tracking number '
         '"JT 2612 3456 7890".'),
        ("D. Held orders and their total",
         "the Customer is signed in as \"mika.tan@example.com\", who owns the held pair "
         "\"ORD-1036\" and \"ORD-1037\"",
         'the Customer opens the Held Orders view',
         'the system shall list both orders and show the "Total held" figure of "₱760.00".'),
        ("E. No held total when there are none",
         "the Customer is signed in as \"jessa.reyes@example.com\", who has NO held orders",
         'the Customer opens the Held Orders view',
         'the system shall show no "Total held" card at all, and instead say there are none.'),
        ("F. A blank cancel reason",
         "the Customer has an unpaid order open",
         'the Customer taps "Cancel order", leaves the reason blank and confirms',
         'the system shall display "Enter a reason for cancelling." and keep the dialog open so '
         "the reason can be typed."),
        ("G. Cancel with a reason",
         "the Customer has an unpaid order open",
         'the Customer taps "Cancel order", types "Changed my mind" and confirms',
         'the system shall cancel the order, return its reserved stock exactly once and record '
         "who cancelled, why and when."),
        ("H. A paid order cannot be cancelled",
         "the Customer has a paid order open",
         'the Customer looks for a way to cancel it',
         'the system shall state "Paid orders cannot be cancelled." and offer no Cancel control.'),
        ("I. The cancelled history",
         "the Customer has cancelled an order",
         'the Customer opens the Cancelled Orders view',
         "the system shall list it with who cancelled, the reason and the time."),
        ("J. The invoice is read-only",
         "the Customer has an invoiced order open",
         'the Customer taps "View invoice"',
         "the system shall show the invoice with no edit or delete control anywhere."),
    ],
    summary="See where my orders are and cancel one I no longer want.",
)

# ===========================================================================
# A-17  Manage account
# ===========================================================================
epic(
    no="A-17",
    title="Manage Account",
    persona="As the Customer",
    want="edit my details, change my password and sign out",
    so_that="so that my delivery details stay right and my account stays mine",
    sample=(
        "Sample Data\n"
        '• Signed-in Customer: "mika.tan@example.com" / "customer123"\n'
        '• The sign-in email cannot be changed here, and the field says so.\n'
        '• A new password must be at least 8 characters and must be typed twice.'
    ),
    sections=(
        "A. Account Screen\n"
        '1. The system shall show the Customer\'s name, handle and email with "Edit profile", '
        '"Change password", "My orders" and "Log out" [Button].\n'
        "\nB. Edit Profile\n"
        '1. The system shall open the profile form with "Full name", "Email", "Messenger or '
        'TikTok handle", "Mobile number", "Province", "City", "Barangay" and "Full address" [Field].\n'
        "2. The system shall keep Email read-only and say it cannot be changed here, and it shall "
        "LOOK read-only so the Customer is not left tapping a field that cannot move.\n"
        '3. The system shall save the changes with "Save changes" and show them immediately.\n'
        "\nC. Change Password\n"
        '1. The system shall mask the "Current password", "New password" and "Confirm new password" '
        "[Field] entries.\n"
        '2. The system shall display "Check your current password and the new password." when the '
        "current password is wrong or the two new ones differ.\n"
        '3. The system shall display "Use at least 8 characters." when the new password is too short.\n'
        "\nD. Sign Out\n"
        '1. The system shall show a confirmation and return to Sign in when "Log out" is tapped.\n'
        "2. The system shall clear the session and the stack so Back cannot re-enter Customer mode.\n"
    ),
    scenarios=[
        ("A. Open Account",
         "the Customer is signed in",
         'the Customer taps "Account" on the bottom bar',
         'the system shall show their name, handle, email and the four actions.'),
        ("B. Open Edit profile",
         "the Customer is on Account",
         'the Customer taps "Edit profile"',
         "the system shall show every profile field prefilled from the account."),
        ("C. The email cannot be changed",
         "the Customer is on Edit profile",
         "the Customer tries to edit the Email field",
         "the system shall keep it read-only, look visibly different from the editable fields, "
         "and say the sign-in address cannot be changed here."),
        ("D. Save a profile change",
         "the Customer is on Edit profile",
         'the Customer changes "Full name" and taps "Save changes"',
         "the system shall save it and show the new name on the Account screen."),
        ("E. Open Change password",
         "the Customer is on Account",
         'the Customer taps "Change password"',
         "the system shall show the three password fields, all masked."),
        ("F. A wrong current password",
         "the Customer is on Change password",
         'the Customer types a wrong current password and taps "Change password"',
         'the system shall display "Check your current password and the new password." and change '
         "nothing."),
        ("G. A short new password",
         "the Customer is on Change password",
         'the Customer types a correct current password and a 5-character new one',
         'the system shall display "Use at least 8 characters." and change nothing.'),
        ("H. Sign out",
         "the Customer is signed in",
         'the Customer taps "Log out" and confirms',
         "the system shall return to Sign in with no bottom bar, and Back shall not re-enter the app."),
    ],
    summary="Edit my details, change my password and sign out.",
)
