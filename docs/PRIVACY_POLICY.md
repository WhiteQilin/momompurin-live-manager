# Privacy Policy — Momompurin Live Manager

**Effective date:** 07 November 2026 (draft for review)
**Application:** Momompurin Live Manager (Android application, Phase 1)
**Data controller:** Momompurin Online Shop (sole proprietorship), Umingan, Pangasinan, Philippines

This Privacy Policy describes how Momompurin Live Manager ("the application")
collects, uses, stores, and protects personal information. It is written in
accordance with Republic Act No. 10173, the Data Privacy Act of 2012 (DPA),
and its implementing rules. Momompurin Online Shop is the personal
information controller for all data processed by the application.

The application is operated by the shop owner for her own business
administration. It has no customer-facing login: customers do not create
accounts, and the application collects nothing directly from a customer's
device. All customer information described below is entered into the
application by the owner, from details customers already provided when they
claimed or ordered items through the shop's sales channels.

## 1. Information the application processes

**Customer information** (entered by the owner when recording a claim or
order):

- Facebook handle or username (used as the customer's identifier)
- Full name, mobile number, and delivery address (province, city, barangay,
  street) — collected to prepare invoices and courier dispatch
- Order history: items claimed, quantities, amounts, live session, and order
  status (claimed, reserved, kept, packed, shipped, cancelled, returned)

**Invoice and fulfilment artefacts:**

- Generated invoices, which may embed the above customer details
- Payment screenshots and packing photos, stored in private cloud object
  storage and shared, when sharing is needed, through expiring (15-minute)
  access links

**Owner account information:**

- One administrator account (email and password) authenticating against the
  application's cloud backend. The password is stored only as an Argon2id
  hash; the application never stores or transmits it in readable form.

**Technical and diagnostic information:**

- The application does not include advertising, analytics trackers, or
  location services. It collects no device identifiers beyond what the cloud
  backend requires to authenticate the owner's session.

## 2. Why the information is processed

Personal information is processed only to operate the shop's order-to-
fulfilment workflow:

1. To record live claims accurately and keep inventory correct against
   reservations;
2. To generate invoices, packing checklists, and shipping records;
3. To contact customers about their orders when fulfilment requires it;
4. To keep a tamper-evident record of stock movements (an append-only log
   used to detect and correct errors).

The application does not sell customer information, does not use it for
advertising, and does not profile customers. Processing rests on the DPA's
contract and legitimate-interest bases: the information is necessary to
deliver and account for goods the customer ordered.

## 3. Where information is stored

Data is stored in the application's managed cloud database (Supabase,
PostgreSQL) and private object storage (Cloudflare R2), both hosted by
established cloud providers. Access is controlled by Row Level Security so
that only the owner's authenticated account can read or write customer data;
no anonymous or public access path exists. Transport between the application
and the cloud is encrypted with TLS 1.3. Local caches on the owner's phone
are encrypted (AES-256). Development and testing builds use synthetic
customer data only — real customer PII never enters a test environment.

## 4. Sharing and disclosure

Customer information is shared only when fulfilling an order requires it:

- **Courier services** — the recipient name, address, and mobile number on a
  waybill or booking;
- **The customer themself** — their own invoice or order status, shared
  through an access-controlled link that hides internal costs and other
  buyers' information;
- **Cloud providers** — Supabase and Cloudflare, strictly as infrastructure
  processors under their own data-protection terms, not for their own use.

Personal information may also be disclosed when required by law, regulation,
or an order of a competent court or government agency.

## 5. Retention and disposal

Order and invoice records are retained for as long as the business needs
them for accounting, warranty, and dispute purposes, consistent with the
BIR's record-keeping requirements; customer contact details are retained
only while an ongoing business relationship or pending order makes them
useful. A customer may ask for their information to be deleted earlier
(Section 6); the owner will comply unless a legal obligation requires the
record to be kept. The append-only stock log is retained for integrity
auditing but contains SKU and quantity data, not customer PII.

## 6. Your rights as a data subject

Under the DPA, any customer whose information the application holds may:

- **Be informed** — ask what personal information is processed, why, and how;
- **Access** — request a copy of their own order and contact records;
- **Correct** — ask for erroneous or outdated details to be corrected;
- **Object or withdraw consent** — ask that future processing stop, within
  the limits of an already-accepted order;
- **Erasure or blocking** — ask for deletion of information no longer needed
  for the purposes above;
- **Complain** — lodge a complaint with the owner in the first instance, or
  with the National Privacy Commission if unresolved.

Requests are actioned within thirty (30) days of receipt. Requests to access
or correct are verified against the order record (the claim or order number,
or the Facebook handle used at purchase) so that no one can extract another
buyer's data by social engineering.

## 7. Children

The application is a business administration tool used by the shop owner and
does not knowingly collect information directly from children. Orders placed
for a child (a gift purchased by an adult through the shop's channels) are
processed as the adult customer's order. If a data subject believes a
minor's personal information was collected improperly, contact the owner
(Section 10) and it will be deleted.

## 8. Security measures

Security controls implemented for this application are documented in the
project's Security Controls Checklist and its security repository, including:
Row Level Security with owner-only policies on every table; TLS 1.3 in
transit; AES-256 encryption for the local cache; Argon2id hashing of the
owner credential; private object storage with short-lived pre-signed links;
an append-only, hash-chained activity log; automated daily database
snapshots; secret scanning and static analysis in continuous integration;
and the exclusion of real customer PII from all development and testing
builds. Should a personal data breach occur, notification to the National
Privacy Commission and to affected data subjects will follow the DPA and NPC
 Circular 16-01 (72-hour breach notification) requirements.

## 9. Changes to this policy

Material changes will be announced on the shop's Facebook page before they
take effect, and the effective date above will be updated. The policy's
version history is kept with the application's documentation.

## 10. Contact

Questions, access requests, corrections, and complaints may be raised
through the shop's official channels: the Momompurin Facebook page or the
shop's TikTok account. Email (to be confirmed with the owner before
publication): `[owner email — pending]`.

---
*Draft prepared for academic review (IAS PBL Milestone 1). Structure adapted
from the nisrulz app-privacy-policy-generator template (Simple mode), with
RA 10173 substituted for the template's CCPA/GDPR sections.*
