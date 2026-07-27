# R11 Wave and Trip Decoupling Summary

Status: `IN_PROGRESS` (2026-07-27)

## Completed

- `MapPlanningService` no longer creates an `arrival_trip` or default `handling_unit` from a Wave.
- Waves without Trip use an independent date sequence and no longer infer the Wave Code from an existing Trip.
- A supplied Trip No is still validated for station and business-date ownership; when absent, `arrival_trip_id` remains null.
- The Operations UI treats Trip as optional: it no longer selects the first Trip or infers one from the Wave Code.
- Existing `/delivery/**` and Operations API fields remain unchanged.

## Verification

- Operations API Maven build passed.
- Operations Web `pnpm typecheck` passed.

## Remaining

- Add database assertions proving no Trip/unit is generated.
- Add E2E coverage for area/unit/driver planning without a Trip.

## Addendum: Arrival transport editing

- Added `PATCH /ops/v1/arrival-trips/{tripId}/transport` to edit vehicle plate, seal number, expected arrival time, and note.
- Trips remain editable after arrival until closed or cancelled; closed/cancelled trips are read-only.
- A reason is required; the service locks the entity through JPA and writes an operation audit entry.
- The Arrival workbench now exposes “Edit vehicle/time” for mid-route vehicle replacement or ETA changes.
