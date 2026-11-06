# Repository notes

Coursework for IAS Milestone 1, Group 8 — III-BCSAD, University of Makati.

The application source in `app/` is the Phase 1 client for Momompurin Live
Manager. The Supabase migrations in `supabase/migrations/` are the schema the
application ships against, and the two files are ordered: `0001` is the state
that was distributed, `0002` is the hardening migration.

Every record in the proof-of-concept harness is synthetic and shaped like the
client's workbook column names. No real customer data is present anywhere in
this repository, including in the transcript under `../out/`.
