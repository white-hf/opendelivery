# R12 Link a Trip to an Existing Wave

> Status: `COMPLETED` (2026-07-27)

## Goal

Allow operators to explicitly link an existing Wave to a real Trip after the line-haul arrival batch becomes available.

## Rules

- Do not change the existing Wave-creation API.
- Add a separate link endpoint and require an explicit save action.
- The Trip must belong to the selected station; missing or cross-station Trips are rejected.
- Record an operation audit entry.
- Never create a Trip or silently persist a dropdown selection.
- Allow clearing the link by sending a null Trip No.

## API

```http
PATCH /ops/v1/planning/waves/{waveId}/arrival-trip
Content-Type: application/json

{"arrivalBatchNo":"YHZ-01-20260727-02","reason":"Trip created after wave"}
```

Send `arrivalBatchNo: null` to clear the link. The endpoint returns the updated wave summary.

## Acceptance

- Saving a selected Trip updates `arrival_trip_id` for the existing Wave.
- Navigating steps without saving does not change the database.
- A saved association remains visible after refresh.
- Existing Wave creation, Driver App, and other API contracts remain unchanged.
