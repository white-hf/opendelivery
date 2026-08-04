# M06: Runtime and Schema Alignment

Status: COMPLETED

## Background

Recent logs showed the control-tower inbound discrepancy query selecting the nonexistent `operational_case.description` column. An older process also loaded a removed `dispatch_wave.created_by` mapping. The database is currently managed by Flyway V22; adding ad-hoc columns is not an acceptable workaround for stale code or artifacts.

## Scope

- Make the inbound discrepancy query use only fields present in the current schema and consumed by the response mapper.
- Verify that `DispatchWaveEntity` matches `dispatch_wave` without restoring a `created_by` mapping.
- Validate with a clean build and tests, and document that the API must be restarted with the latest artifact.

## Definition of Done

1. `GET /api/ops/v1/control-tower` returns 200 when inbound discrepancy rows exist and no longer raises `Unknown column c.description`.
2. `DispatchWaveEntity` has no `created_by` mapping and the database remains on Flyway V22.
3. Relevant modules compile and tests pass; the runbook requires stopping the old port-9001 process before starting the new build.

## Implementation Result

- Removed the unused and nonexistent `c.description` select item.
- Verified `DispatchWaveEntity` against the current `dispatch_wave` table without restoring `created_by`.
- Added the Operations API `mock-maker-subclass` test configuration to avoid Byte Buddy self-attach; all 9 common-module tests and 44 Operations API tests pass.
