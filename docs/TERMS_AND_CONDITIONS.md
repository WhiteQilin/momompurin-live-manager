# Terms and Conditions — Momompurin Live Manager

**Effective date:** 10 November 2026 (draft 2, revised after the 07 November consultation)
**Application:** Momompurin Live Manager (Android application, Phase 1)
**Operator:** Momompurin Online Shop (sole proprietorship), Umingan, Pangasinan, Philippines

These Terms and Conditions ("Terms") govern the use of the Momompurin Live
Manager application and the shop operations it administers. The application is
a business administration tool for the shop owner; customers who buy from
Momompurin Online Shop transact through the shop's sales channels — TikTok
Live and TikTok Shop, Facebook Marketplace, and Carousell — under those
platforms' own terms, with the shop's policies below applying to each order.

The operational rules in Sections 7–10 are not generic app-store boilerplate:
they are the shop's documented selling cycle, as the owner actually runs it —
the 24-hour payment deadline with owner-decided extensions, the Keep pile,
cancelled-order restoration, the couriers she books, and the freebies she
includes at packing.

## 1. Acceptance of terms

By installing, accessing, or using the application, the owner-account holder
accepts these Terms. Customers ordering from the shop accept the
shop-operations terms in Sections 7–10 by placing a claim or order.

## 2. Use licence

The application is licensed, not sold, to the shop owner for use in operating
Momompurin Online Shop. The licence does not permit redistribution, resale, or
use of the application to operate any other business.

## 3. Accounts and security

The application has a single administrator account. The holder must keep the
credential confidential, use a strong password, and notify the developer of
any suspected compromise. The application records stock-movement activity in
an append-only log; administrative actions are attributable.

## 4. Acceptable use

The application must not be used to process data the user has no right to
process, to circumvent the cloud backend's access controls, or to extract
other users' personal information. Attempts to reverse-engineer access
controls other than for permitted security review of one's own deployment are
prohibited.

## 5. Intellectual property

The application's code, design, and documentation are the work of its student
development team and are licensed to the shop for the purposes in Section 2.
Sanrio character merchandise sold through the shop are official or licensed
reseller goods; the application itself is not affiliated with, endorsed by, or
connected to Sanrio Co., Ltd.

## 6. Availability, backups, and service changes

The application targets continuous availability during live-selling sessions,
with an offline mode for network interruptions, automated daily database
snapshots, and one-click CSV/JSON export. The application is provided at
design-stage maturity: the owner should verify invoices and shipping records
before acting on them. Features may change as the system develops; material
changes to these Terms will be announced on the shop's Facebook page.

## 7. Live claims and the 24-hour payment deadline

These rules are the shop's documented selling cycle (v2.4, §4.2):

- A claim made during a TikTok Live session, or an order placed on TikTok
  Shop, Facebook Marketplace, or Carousell, reserves the item(s) for that
  buyer at the price stated at claim time.
- **24-hour payment window.** Buyers have 24 hours to pay after receiving the
  invoice. The clock runs from the time the owner records the invoice as
  received by the buyer. If an invoice is revised, the 24 hours start again
  from receipt of the revised invoice.
- **After 24 hours, the owner decides — the app never cancels automatically.**
  An unpaid order past its window is flagged "Overdue" in the Unpaid Invoices
  and Orders lists and appears in Notifications, where the owner chooses
  between two actions:
  - **Confirm Cancel & Restock** — the reservation is released and the exact
    item quantities return to Available Stock one time only; or
  - **Extend by (input hours)** — the owner grants extra hours, recorded
    against that order.
- An unpaid order that is not extended comes up for **cancellation review one
  week after its 24-hour window ended**.
- **Verified Customer.** A buyer is marked as a Verified Customer as soon as
  they pay an order for the first time.

## 8. The Keep pile

These rules implement the shop's documented holding practice (v2.4, §5):

- **Keep** is the owner's hold status for paid items waiting to ship. A buyer
  with a paid order may ask for items to be kept for later dispatch instead of
  shipping immediately.
- Kept items stay **allocated to that order** — they cannot be sold to another
  buyer while held.
- A paid order may **stack**: additional claimed items join the same Keep pile
  so the buyer pays one shipping fee for one parcel.
- Kept items are packed, with freebies at the owner's discretion, when the
  buyer decides they are ready for shipping.

## 9. Shipping and delivery

These rules mirror the shop's fulfilment flow (v2.4, §6):

- **Couriers.** J&T Express is the primary courier for most direct bookings.
  Lalamove and Grab are used occasionally for same-day local delivery. Some
  buyers choose TikTok Shop Checkout for cheaper platform shipping rates,
  accepting the platform's own delivery times.
- **Customer Pickup** is a direct pickup arrangement with no courier booking
  and no tracking number; the order follows the same status sequence as
  shipped orders.
- **Tracking numbers are validated at entry.** The application checks the
  typed or scanned tracking number against the chosen courier's format before
  the order can be marked In Transit, so a mistyped waybill number is caught
  before the parcel leaves.
- **Delivery status sequence:** To Prep → Preparing / Packed → For Pickup →
  In Transit (Out for Delivery) → Received. The owner marks each transition
  manually; the app does not poll courier systems in Phase 1.
- **Stock is deducted at handover.** Items stay allocated to the order until
  the parcel is handed to the courier, and at that point they are permanently
  deducted from inventory. If shipping is unpaid or something goes wrong
  before dispatch, the order is kept on hold (items paid) or cancelled
  (unpaid).
- **Freebies** are added at packing at the owner's discretion and are not a
  purchase obligation.
- Delivery times are the courier's, not the shop's; the shop is not liable for
  courier delays but will assist in tracing a shipment using the tracking
  number on record. The customer is responsible for the accuracy of the
  delivery address and mobile number provided.

## 10. Returns, refunds, and cancellations

- The customer may cancel an unpaid reservation at any time before payment.
- After payment, cancellation is accepted before dispatch.
- **Cancelled orders are restocked exactly once** (Confirm Cancel & Restock
  returns the precise item quantities to Available Stock one time only), and
  remain in Order History, where the owner can **Restore Order** if the
  cancellation was a mistake.
- **Refunds** follow the owner's inspection of the returned item. Items that
  fail the quality inspection are refunded; suppliers usually allow the owner
  to keep failed items rather than paying return shipping, and those items do
  not re-enter Available Stock automatically.
- Refunds are returned through the original payment channel.

## 11. Personal data

Customer personal information is processed under the shop's Privacy Policy,
which forms part of these Terms and is published alongside them. The Privacy
Policy states what is collected, why, where it is stored, and the data
subject's rights under RA 10173. In development and testing builds, no real
customer personal information is used — synthetic datasets only.

## 12. Limitation of liability

To the extent permitted by law, the application's developers are not liable
for indirect or consequential losses arising from use of the application. The
shop's liability for any order is limited to the amount paid for that order.
Nothing in these Terms limits liability that cannot be limited under
Philippine law, including under the Consumer Act of the Philippines (RA 7394).

## 13. Termination

The shop may suspend or terminate application access for breach of these
Terms, and may decline a customer's future orders for abuse of the claims
process (for example, repeated unpaid reservations — the "joy-miner" pattern
the 24-hour rule exists to catch). Sections 7–11 survive the completion of any
order.

## 14. Governing law and disputes

These Terms are governed by the laws of the Republic of the Philippines.
Disputes are raised first through the shop's contact channels; unresolved
consumer disputes may be brought to the DTI or the appropriate forum under
Philippine law.

## 15. Contact

Momompurin Online Shop — through the shop's Facebook page or TikTok account.
Email (to be confirmed with the owner before publication):
`[owner email — pending]`.

---
*Draft 2 prepared for academic review (IAS PBL Milestone 1). Sections 7–10 are
localised to the shop's documented selling cycle v2.4 (24-hour payment deadline
with owner-decided extension, Keep pile stacking, J&T / Lalamove / Grab /
TikTok Shop / Customer Pickup, the five-state delivery sequence, one-time
restock and Order-History restore), per the 07 November consultation feedback
that the first draft was too generic. Structure adapted from the nisrulz
app-privacy-policy-generator Terms template.*
