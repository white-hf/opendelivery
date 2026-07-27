# T05-A Query-Layer Refactoring Summary

## Delivered

- Added `DispatchWaveQueryRepository` for cross-table wave list and detail projections.
- `MapPlanningService.waveSummary()` and `DispatchOperationsService.waves()` now delegate to the query repository.
- Wave commands continue to use `DispatchWaveEntity` and `DispatchWaveRepository`.
- No new query SQL is added to those business services; the next slice will replace map projections with typed query DTOs.

## Validation

- Operations API `mvn package -DskipTests` passed.
- Full Maven tests are blocked by the existing Mockito/Byte Buddy attach failure in this environment, unrelated to this slice.

## Follow-up

- T05-B migrates Planning Parcel/Driver Capacity queries and replaces Map responses with typed DTOs.
