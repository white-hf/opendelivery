# T05-C Query-Layer Refactoring Summary

Status: `COMPLETED` (2026-07-27)

## Delivered

- Added `CaseQueryRepository` for read-only outbox and audit projections, including the `resourceType + resourceId` filter.
- Added `DayCloseQueryRepository` for reconciliation details and inbound, dispatched, delivered, returned, open-case, and unapproved-session metrics.
- Added `HandoverQueryRepository` for wave/task, expected-item, scan-result, open-session, and station-ownership reads used by driver load handover supervision.
- `ConfigCaseOperationsService`, `DayCloseOperationsService`, and `ScanSupervisionService` now orchestrate business and transactions; `FOR UPDATE`, state writes, and audit writes remain on the command side.
- Removed the obsolete commented SQL from `MapPlanningService` after migration.

## Design and performance

Repositories reuse existing station/business-date indexes. Large cross-domain aggregates remain explicit JdbcTemplate escape hatches and will be verified with EXPLAIN and archival strategy. Map responses are a compatibility bridge; T05-D will converge stable contracts to DTOs.

## Verification

- `./tools/apache-maven-3.9.8/bin/mvn -pl operations/easydelivery-ops-api -am package -DskipTests`: passed.
- The full suite remains affected by the pre-existing Mockito/Byte Buddy self-attach environment failure in `ParcelDomainServiceTest`; unrelated to this slice and tracked for test-infrastructure work.
