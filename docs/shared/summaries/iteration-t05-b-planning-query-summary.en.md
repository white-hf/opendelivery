# T05-B Planning Query-Layer Summary

## Delivered

- Added `PlanningQueryRepository` for planning-map parcels and driver-capacity queries.
- `MapPlanningService.shifts()` and `mapParcels()` now delegate to the query repository.
- Station isolation, wave/viewport/SLA filters, exception fields, and limit caps remain unchanged.

## Validation

- Operations API `mvn package -DskipTests` passed.
- Query SQL is centralized under the persistence query package; full Maven tests remain blocked by the baseline Mockito/Byte Buddy attach failure in this environment.

## Follow-up

- T05-C migrates Case, Handover, and Day Close queries.
- Later slices will replace Map responses with typed query DTOs and add query-repository integration tests and execution-plan baselines.
