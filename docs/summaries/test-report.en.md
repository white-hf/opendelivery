# Test Report

Environment: Java 17, Spring Boot 3.3, MySQL 8.0.43. Scope covered authentication, authorization isolation, upstream intake, manifest, dispatch, scan handover, delivery, POD metadata, status events, outbox, restart persistence, and migration.

Results: 22 Java tests and 11 Operations Web tests passed; Maven clean verify, Web typecheck/build/lint succeeded. The real schema reached V8. R01 verified SRID 4326, valid geometry/indexing, draft/validate/publish, overlap rejection, driver preference, persisted parcel matching and cross-station rejection; temporary E2E data was removed. Multi-city inbound and dispatch evidence remains green.

Residual risk: real third-party callbacks, production object storage, concurrency, network partitions and large batches remain untested. R01 still needs shared-edge, parcel matching and three-station isolation E2E. The Web build reports a non-blocking bundle-size warning; R02 must introduce route-level splitting when adding the map SDK. Callback retry/dead-letter still requires a partner sandbox drill.
