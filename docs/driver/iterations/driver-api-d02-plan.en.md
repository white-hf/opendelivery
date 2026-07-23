# Driver API D02 Iteration Plan: Driver Loading Scan & Device Idempotency

> Status: `REVIEWED` (2026-07-23). Confirmed Driver App contract remains compatible with `/delivery/scan/**` paths and JSON formats.

## 1. Scope and Goals

The D02 iteration focuses on driver loading scan (LOAD Scan Session / Event) classification, device event idempotency, post-submission locking, and offline recovery support.

### In Scope
1. **Four Scan Result Classifications**:
   * `EXPECTED`: Belongs to current driver's task & in `ASSIGNED` status. Successfully loaded.
   * `WRONG_TASK`: Parcel exists in system but assigned to another driver/task.
   * `UNKNOWN`: No parcel record with this `tracking_no`.
   * `DUPLICATE`: Already successfully scanned in this batch. Not recount.
2. **Device Event Idempotency**:
   * Globally unique `device_event_id`. Duplicate pushes return identical initial result.
3. **Post-Submission Read-Only Lock**:
   * Once batch status becomes `SUBMITTED`, scan attempts are rejected (`SCAN.BATCH.LOCKED`).
4. **Authorization Gate**:
   * Verify batch `driver_id` matches JWT Token `driverId`.

### Out of Scope
* Damage marking (`DAMAGED` belongs to Operations station inbound/inventory management).
* No changes to Android App API paths or contracts.

## 2. DoD (Definition of Done)

* [x] D02 design document reviewed (`REVIEWED`)
* [ ] Source code implementation, device idempotency & batch locking
* [ ] Scan classification & idempotency unit/integration tests
* [ ] Full `./run.sh test` suite passing
* [ ] D02 execution summary written
