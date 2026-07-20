# Test Report

Environment: Java 17, Spring Boot 3.3, MySQL 8.0.43. Scope covered authentication, authorization isolation, upstream intake, manifest, dispatch, scan handover, delivery, POD metadata, status events, outbox, restart persistence, and migration.

Results: 19 Java tests and 11 Operations Web tests passed; Maven clean verify, Web typecheck/build/lint succeeded. The real schema reached V8. R01 verified SRID 4326, valid geometry, a spatial index, point intersection, draft/validate/publish APIs and overlap rejection; temporary E2E data was removed. Multi-city inbound and dispatch evidence remains green, including discrepancy gates, cross-station rejection, owner scans, supervisor custody handover and delivery conservation.

Residual risk: real third-party callbacks, production object storage, concurrency, network partitions and large batches remain untested. R01 still needs shared-edge, parcel matching and three-station isolation E2E. The Web build reports a non-blocking bundle-size warning; R02 must introduce route-level splitting when adding the map SDK. Callback retry/dead-letter still requires a partner sandbox drill.
