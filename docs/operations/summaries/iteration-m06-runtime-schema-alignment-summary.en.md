# M06 Delivery Summary: Runtime and Schema Alignment

Status: COMPLETED

## Delivered

The control-tower inbound discrepancy query no longer selects the nonexistent `operational_case.description` column, preventing SQL grammar failures in `GET /api/ops/v1/control-tower`. `DispatchWaveEntity` was verified to have no `created_by` mapping, and the database remains on the Flyway V22 schema without temporary columns.

## Validation

- `mvn -pl operations/easydelivery-ops-api -am -DskipTests compile`: passed.
- Common-module tests: 9 passed.
- Operations API tests: 44 passed. The module now uses the same `mock-maker-subclass` setup as the other modules and no longer depends on local JVM self-attach.

## Runtime Note

Old processes may continue to emit the historical errors. Deployment must stop the stale Java process occupying port 9001 and start the latest build so the fix is actually loaded.
