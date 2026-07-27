# R11 Wave and Trip Decoupling

> Status: `IN_PROGRESS` (2026-07-27)

## Goal

Treat Trip as a line-haul arrival fact and Wave as a station dispatch plan. Wave creation must not depend on a Trip, and the system must never create an automatic or temporary Trip.

## Scope

- Keep the existing Wave API request and response fields; `arrivalBatchNo` is optional.
- When a Trip No is supplied, validate station ownership only; otherwise keep `arrival_trip_id` null.
- Remove automatic `arrival_trip` and default `handling_unit` creation from Wave creation.
- The Operations UI must not select a default Trip or infer one from Wave Code. Trips are created only from Arrival, then optionally linked to a Wave.
- Add tests for Wave creation, planning, and driver assignment without a Trip.

## Definition of done

- A Wave without Trip succeeds and creates no arrival or handling-unit rows.
- A valid Trip can be linked and a cross-station Trip is rejected.
- A Wave without Trip can continue area, unit, and driver planning.
- Existing API paths, fields, and Driver App contracts remain unchanged.
