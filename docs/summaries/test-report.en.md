# Test Report

Environment: Java 17, Spring Boot 3.3, MySQL 8.0.43. Scope covered authentication, authorization isolation, upstream intake, manifest, dispatch, scan handover, delivery, POD metadata, status events, outbox, restart persistence, and migration.

Results: 27 Java tests and 14 Operations Web tests passed; Maven clean verify and Web test/typecheck/lint/build succeeded. The real schema reached V10. R01.1 verified French auth, Chinese preference, English fallback, nullable account preference falling back to the French station default, and key-set equality; all test configuration was restored. R01 spatial evidence remains green.

Residual risk: real third-party callbacks, production object storage, concurrency, network partitions and large batches remain untested. R01 still needs shared-edge, parcel matching and three-station isolation E2E. The Web build reports a non-blocking bundle-size warning; R02 must introduce route-level splitting when adding the map SDK. Callback retry/dead-letter still requires a partner sandbox drill.
