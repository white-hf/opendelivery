# T06 Driver API Contract and Integration Tests

> Status: `IN_PROGRESS` (2026-07-27); current slice: `T06-B`; T06-A is complete.

## Goal

Build on the T05 query split to freeze the Driver API compatibility contract, add database-profile integration coverage, and cover driver identity, empty data, scan-batch lifecycle, and delivery-result boundaries.

## Scope

- `/delivery/parcels/tasks` and `/delivery/parcels/delivering` query contracts.
- `/delivery/scan/**` scan-batch and driver ownership checks.
- `/delivery` and `/delivery/retry` success, failure, retry, and idempotency paths.
- Query Repository mapping unit tests without changing `/delivery/**` paths or fields.

## Definition of done

- Memory-profile regression tests remain green.
- JDBC-profile tests cover query-repository mapping and empty results.
- Cross-driver requests return unauthorized and failure responses retain stable business codes.
- Tests and API contract notes are updated in both languages.

## Slices

1. T06-A: test-runtime stability and existing Driver memory regression (completed).
2. T06-B: JDBC/MySQL query-mapping tests (empty-result repository unit coverage added; real MySQL verification pending).
3. T06-C: concurrency and idempotency protection (phase one complete: scan device-event conflict handling and delivery idempotency locking).
4. T06-D: internal Driver query-model cleanup (no external API contract changes).
