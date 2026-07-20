# Test Report

Environment: Java 17, Spring Boot 3.3, MySQL 8.0.43. Scope covered authentication, authorization isolation, upstream intake, manifest, dispatch, scan handover, delivery, POD metadata, status events, outbox, restart persistence, and migration.

Results: five common unit tests and four app tests passed; Maven and Docker builds succeeded; Compose validated; empty-schema Flyway produced 24 baseline business tables plus history; and the real schema upgraded through V3 routing, V4 operator identity, and V5 inbound workbench. Multi-city inbound E2E passed normal, damaged, extra, duplicate retry, discrepancy Cases, close gate, cross-station 403, and status/custody/outbox assertions. The original delivery E2E still passed with one attempt, seven status events, and six outbox events.

Residual risk: real third-party callbacks, production object storage, high concurrency, network partitions, and large batches have not been exercised. The callback retry/dead-letter implementation requires a partner sandbox drill. Initial production rollout must be restricted to one partner/station with reconciliation and callback-latency monitoring.
