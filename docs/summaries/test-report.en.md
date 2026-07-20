# Test Report

Environment: Java 17, Spring Boot 3.3, MySQL 8.0.43. Scope covered authentication, authorization isolation, upstream intake, manifest, dispatch, scan handover, delivery, POD metadata, status events, outbox, restart persistence, and migration.

Results: 27 Java tests and 16 Operations Web tests passed; Maven clean verify and Web test/typecheck/lint/build succeeded. The real schema reached V10. The real-MySQL R01 gate published six areas across YHZ/YYZ/YVR, accepted shared edges, rejected area overlap and cross-station reads, then removed its area and audit fixtures. R01 map automation also covers polygon closure and Feature parsing. Localization and prior spatial evidence remain green.

Residual risk: R01 automation is complete; browser visual acceptance remains pending. Run the repeatable gate with `DB_PASSWORD=... OPS_PASSWORD=... ./scripts/delivery-area-e2e.sh`; credentials are environment-only. Areas, Manifests, and Dispatch are now route-split; the map area chunk is about 185 kB and no longer loads initially. Shared dependencies such as Ant Design still leave an approximately 1.08 MB initial chunk and a non-blocking warning, so vendor splitting and a performance budget remain follow-up work. Real partner callbacks, object storage, concurrency, network partitions, and large batches remain untested.
