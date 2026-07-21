# R03-C Arrival Linkage Close-out Execution Summary

> Completed: 2026-07-21; status: `COMPLETED`. Closes O03 (arrival linkage) of the two-product plan.

## Delivered

- **Ingestion contract**: the canonical request gains optional `handlingUnits` (per-waybill unit declarations) and `deliveryLatitude/deliveryLongitude`; `trackingNumbers` stays the parcel truth — extra/missing unit entries never block ingestion.
- **Flyway V13** (applied on real MySQL, V12→V13): `parcel.upstream_unit_no` (index `(upstream_unit_no,current_station_id)`) and the denormalized `parcel.current_area_version_id` projection (index `(current_station_id,current_area_version_id)`, with backfill); `handling_unit_parcel` CHECK extended with `'AREA_PLAN'`.
- **Automatic area membership**: after routing, ingestion computes `parcel_area_assignment` from coordinates (GEO_POLYGON) and syncs the projection in the same transaction; new `POST /ops/v1/parcels/area-recompute` (defaults to unmatched station parcels; explicit `parcelIds` for targeted re-match).
- **Arrival batches**: `POST /ops/v1/arrival-trips` auto-generates the batch number (`{stationCode}-{yyyyMMdd}-{seq}`, retrying on collision) plus 10 default PALLET units.
- **Auto-linkage**: creating a handling unit links same-station parcels with a matching `upstream_unit_no`; during ingestion, parcels link immediately when the unit already exists (both directions proven by E2E); cross-station parcels are never linked.
- **Area fill**: `POST /ops/v1/handling-units/{id}/area-fill` links every parcel of published areas (`AREA_PLAN`); one parcel cannot be planned on two units of the same batch; cross-station/unpublished areas rejected with 409.
- **Coverage observation**: batch detail returns per-unit `declared/linked/scanned/exception` counters, `driver_count/wave_count`, parcel detail, and unlinked declarations; the frontend batch workbench (four counters, drill-down, area-fill wizard, unlinked warning strip, aggregate≠detail error) in three languages.
- **Three-city fixture and E2E**: `scripts/db/005_r03c_arrival_fixture_seed.sql` (second driver + two published areas per station) and `scripts/arrival-batch-e2e.sh` (self-cleaning, tolerant of shared dev data).

## Verification evidence

- `mvn test`: 44 Java tests pass (new `ArrivalLinkagePolicyTest`, 6 tests).
- Web: 25 Vitest, typecheck, ESLint, production build pass (new `arrivalCoverage` aggregate=detail gate tests).
- Playwright: two new arrival-workbench specs (en/zh rendering with auto batch-number guidance) pass; the control-tower arrival-entry spec was updated for the new button copy and passes. The map-journey spec failure is pre-existing fixture drift (demo data `promised_date=2026-07-20`, coupled to the business date), unrelated to this slice; it returns once the fixture is decoupled from the calendar.
- Real MySQL 8 `opendelivery`: V12→V13 applied; `scripts/arrival-batch-e2e.sh` green on all three stations — sequence increments, 10 default units, auto membership, area fill of 6, both auto-link directions, cross-driver unit (driver_count=2), cross-unit task, exception=1 (cross-station declaration), aggregate=detail, 403 cross-station read, 409 unknown tracking / foreign area, targeted recompute restoring the projection; all test data auto-cleaned.

## Decisions on record

- Exception count = upstream-declared parcels unlinkable to the unit (cross-station/unrouted); DAMAGED observations fold in once D02 scan classification exists.
- The area projection column is a deliberate denormalization (performance review): history stays in `parcel_area_assignment`, synced in-transaction at all three write sites; `MapPlanningService`'s existing paa joins remain as a follow-up optimization.
- area-recompute defaults to unmatched parcels (safe on shared databases); full re-matching uses explicit `parcelIds`, so the default path is not E2E-covered on the shared database.
- No unlink UI (later iteration); out-of-order upstream amendments/cancellations are I10 scope.

## Rollback

Only additive contract fields, columns, indexes, and APIs; retiring the new endpoints disables the feature. V13 columns are harmless if left behind; older builds ignore them.
