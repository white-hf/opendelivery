# Iteration 00: Domain Baseline and Database Bootstrap

## Goal

Define the last-mile domain before translating demo collections into tables. Separate ingestion batches, manifests, dispatch waves, and scan sessions; document upstream-to-ACK closure; and create the empty MySQL database.

## Scope and Evidence

The iteration reviewed modules/APIs/storage, documented actors, lifecycle, exceptions, ownership, and target architecture, then created `opendelivery` with `utf8mb4_0900_ai_ci`. Business tables were deliberately deferred until state and ownership review. Completion evidence is recorded in the Iteration 00 summary.

## Next Step

Iteration 01 owns the state matrix, data dictionary, Flyway schema, persistence implementation, and real-MySQL validation.

