# T02 Persistence ORM Refactoring Iteration (Integration Context) Execution Summary

> Completion Date: 2026-07-21; Status: `COMPLETED`. Reference: [Iteration Document](../iterations/iteration-t02-persistence-integration.en.md).

## Delivered Scope

1. **Integration Context JPA Entities & Repositories (Package-by-Feature)**:
   - Ingestion subdomain: `com.hf.easydelivery.integration.ingestion.persistence`
     - `UpstreamPartnerEntity` / `UpstreamPartnerRepository`
     - `IngestionBatchEntity` / `IngestionBatchRepository`
     - `IngestionRecordEntity` / `IngestionRecordRepository`
     - `WaybillEntity` / `WaybillRepository`
     - `ParcelIngestionEntity` / `ParcelIngestionRepository`
   - Routing subdomain: `com.hf.easydelivery.integration.routing.persistence`
     - `StationServiceAreaEntity` / `StationServiceAreaRepository`
   - Outbox subdomain: `com.hf.easydelivery.integration.outbox.persistence`
     - `OutboxEventEntity` / `OutboxEventRepository`
     - `CallbackAttemptEntity` / `CallbackAttemptRepository`
2. **Service Layer Refactoring & Escape-Hatch Annotations**:
   - `ShipmentIngestionService.java`: Refactored to use Spring Data Repositories for entity CRUD operations.
   - Escape hatch comments added for dialect SQL:
     - `ON DUPLICATE KEY UPDATE` statements tagged with `// ESCAPE-HATCH (ADR-Persistence): Dialect UPSERT with ON DUPLICATE KEY UPDATE retained via JdbcTemplate`.
     - Spatial `ST_SRID` queries tagged with `// ESCAPE-HATCH (ADR-Persistence): MySQL spatial ST_SRID function and ON DUPLICATE KEY UPDATE retained via JdbcTemplate`.
     - `INSERT IGNORE` queries tagged with `// ESCAPE-HATCH (ADR-Persistence): Dialect INSERT IGNORE retained via JdbcTemplate`.
   - `ShipmentRoutingService.java`: Ranked priority queries tagged with `// ESCAPE-HATCH (ADR-Persistence): Complex priority ranking query joining station_service_area and station retained via JdbcTemplate`.
   - `OutboxDispatcher.java`: Queue claiming query tagged with `// ESCAPE-HATCH (ADR-Persistence): Queue polling with FOR UPDATE SKIP LOCKED and JSON_EXTRACT dialect functions retained via JdbcTemplate`.
3. **Testcontainers Evaluation Conclusion**:
   - **Assessment**: The integration context relies heavily on MySQL dialect operations (`ON DUPLICATE KEY UPDATE`, `ST_SRID`, `FOR UPDATE SKIP LOCKED`). Existing Docker + real MySQL scripts (`DB_PASSWORD='<secret>' scripts/mysql-e2e-test.sh`) and memory profile unit tests provide fast feedback (< 4s).
   - **Decision**: Testcontainers is not mandated as a default Maven build dependency to avoid breaking builds when local Docker daemon is inactive. Real DB E2E testing remains guarded by `scripts/mysql-e2e-test.sh`.

## Verification Evidence

- **Backend Unit Tests**: `./run.sh test` 45 tests green, `BUILD SUCCESS`.
- **Behavior Invariant**: Ingestion API contract, routing match algorithm, and outbox retry logic remain 100% identical.

## Summary

T02 Persistence ORM Refactoring for the Integration context has been fully delivered per the Persistence ADR standards.
