# R04 Dispatch-Planning Efficiency Iteration

> Status: `COMPLETED` (Completed on 2026-07-21); product: Operations; builds on R03-C arrival batches to eliminate repetitive manual steps per station workflow. Reference: [Execution Summary](../summaries/iteration-r04-planning-efficiency-summary.en.md).

## Background and current gaps

The station work flow requires: dispatch planning can start after the first upstream push, before truck arrival, and continue incrementally with later pushes; plan codes reuse the arrival batch number as one identifier; driver capacity defaults to available with exceptions only; a driver bound to areas (1-N) receives that batch's parcels of those areas by default, with the map used only for occasional adjustments.

Current gaps (verified in code): a missing `driver_shift` row means `UNAVAILABLE` and assignment fails outright — operators must file a shift per driver per day; `waveCode` is manually required; driver assignment needs manual map selection every time (the API supports `WHOLE_AREA` but there is no default assignment from driver area preferences); parcels arriving in later pushes have no to-plan view.

## Scope

1. **Default-available capacity**: a missing `driver_shift` row counts as AVAILABLE with a station-level default capacity; only exceptions such as leave or sickness are recorded (`UNAVAILABLE` with reason). The shift UI changes from "file per driver per day" to "mark exceptions".
2. **Auto-generated plan codes**: `WaveRequest.waveCode` becomes optional; the default is `{arrivalBatchNo}-W{seq}` (or `W{yyyyMMdd}-{seq}` without a batch), keeping one identifier with the arrival batch; manual override stays possible.
3. **Driver default assignment**: using `driver_area_preference` (driver bound to 1-N areas), unassigned parcels of those areas in the batch are attached to the driver's task by default (`WHOLE_AREA`); existing capacity and occupancy checks still apply; map `reassign` remains for adjustments.
4. **Incremental handling**: after later upstream pushes, unplanned parcels appear in a to-plan view (grouped by area); the increment can be default-assigned again or adjusted by hand, without touching published tasks.
5. **Planning workbench UI**: layout rebuilt around the work flow "to-plan → default assign → map fine-tuning → freeze/publish", minimizing clicks and page jumps; three languages.

## Non-goals

- No route optimization or stop-sequence suggestions (I14 scope).
- No change to publish gates or handover approval (O05); the Driver API contract is unchanged.
- No automatic multi-wave splitting or merging.

## Dependencies

- R03-C arrival batch number and automatic area membership; V8 `driver_area_preference`, V11 wave/task main chain.

## Migration and compatibility

- If the station-level default capacity needs persistence, add a new Flyway migration (add-only).
- `waveCode` drops from required to optional; clients passing an explicit code behave unchanged.

## Risks

- Default-available could schedule a driver who is on leave → the exception entry stays prominent on the planning page, and default assignment re-checks same-day exceptions before running.
- Default assignment deviating from real needs → map review before publish remains a required step; approval gates are unchanged.

## Tests and DoD

- Java: default-available without shift, exception blocking, unique auto-generated codes, capacity/cross-station/occupancy checks in default assignment, idempotent incremental re-assignment.
- Real MySQL with the three-city fixture verifying default-assignment results; Web: Vitest + Playwright planning-workbench regression; bilingual docs and execution summary per the governance flow.
