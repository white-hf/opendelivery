# T03 Persistence ORM Refactoring Iteration (Dispatch & Delivery Context)

> Status: `COMPLETED` (Completed on 2026-07-21); product: Shared Platform / Operations; executed per Wave 3 of [Persistence ADR](../../design/persistence-architecture.en.md). Reference: [Execution Summary](../summaries/iteration-t03-persistence-delivery-summary.en.md).

## Background & Objective

Following the completion of T01 (Arrival Context) and T02 (Integration Context) persistence refactoring, this iteration establishes Spring Data JPA entities and repositories for the Dispatch Planning & Delivery Supervision domains, covering `dispatch_wave`, `driver_task`, `driver_task_item`, `driver_task_area`, `delivery_attempt`, and `proof_of_delivery`.

## Scope

1. **JPA Entities & Repositories** (Package-by-Feature):
   - `com.hf.easydelivery.operations.dispatch.persistence`:
     - `DispatchWaveEntity` + `DispatchWaveRepository`
     - `DriverTaskEntity` + `DriverTaskRepository`
     - `DriverTaskItemEntity` + `DriverTaskItemRepository`
     - `DriverTaskAreaEntity` + `DriverTaskAreaRepository`
   - `com.hf.easydelivery.operations.supervision.persistence`:
     - `DeliveryAttemptEntity` + `DeliveryAttemptRepository`
     - `ProofOfDeliveryEntity` + `ProofOfDeliveryRepository`
2. **Service Refactoring & Escape Hatches**:
   - Refactor `DispatchOperationsService.java` and `MapPlanningService.java` to leverage Spring Data JPA for entity CRUD.
   - Retain `JdbcTemplate` for batch inserts, complex aggregations, and row locks with explicit `// ESCAPE-HATCH (ADR-Persistence)` comments.

## Zero Behavior Change & Verification

- Preserves 100% existing REST endpoint paths.
- Run `./run.sh test` to verify all backend unit tests remain green.
- Run frontend toolchain checks.
