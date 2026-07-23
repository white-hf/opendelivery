# Driver API D01 Iteration Plan: Driver Own Tasks & Parcel List Closure

> Status: `REVIEWED` (2026-07-22). Confirmed Driver App contract remains compatible with `/delivery/parcels/tasks` and `/delivery/parcels/delivering` paths and JSON formats.

## 1. Scope and Goals

The D01 iteration focuses on parcel list accuracy, multi-wave aggregation, and authorization gates for the Driver App.

### In Scope
1. **Authorization Gate**:
   * Verify that request parameter `driver_id` matches JWT Token `driverId`.
   * Reject mismatches with 403 (`AUTH.UNAUTHORIZED` / `UnauthorizedException`).
2. **Multi-Wave Parcel Aggregation**:
   * Driver App remains transparent to waves/tasks.
   * Query SQL removes single-date/single-wave hardcoding, automatically returning all active wave parcels assigned to the driver flattened.
3. **Task Lifecycle Gate**:
   * Implicitly enforce `t.status IN ('PUBLISHED', 'ACCEPTING', 'IN_PROGRESS')`.
   * Parcels from cancelled/revoked tasks automatically disappear from the driver's list.
4. **Database & Query Performance**:
   * Ensure driving table composite indexes so multi-table joins execute via primary key point lookups.

### Out of Scope
* No changes to Android App API paths or contracts.
* No long localized response strings (rely on `biz_code` for local App rendering).
* Operations features (scheduled auto-close, end-of-day reconciliation belong to Operations O-stream).

## 2. DoD (Definition of Done)

* [x] D01 design document reviewed (`REVIEWED`)
* [ ] Source code implementation & authorization gate
* [ ] Multi-wave aggregation & revocation isolation unit/integration tests
* [ ] Full `./run.sh test` suite passing
* [ ] D01 execution summary written
