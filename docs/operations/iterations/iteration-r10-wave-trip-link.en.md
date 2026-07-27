# R10 Persisted Wave-to-Arrival-Trip Link

> Status: `COMPLETED` (2026-07-27); type: Operations dispatch-planning bug fix.

## Goal

When Step 1 selects a Trip No, the wave must persist that inbound-trip link. Only an unselected Trip No may trigger automatic trip creation. Returning to Step 2 must restore the same handling-unit plan.

## Implementation

- V22 adds the indexed `dispatch_wave.arrival_trip_id` foreign key.
- Wave creation validates station and service date ownership.
- Every frontend wave-creation path submits the selected Trip No.
- Wave details restore the arrival trip from the persisted link.

## Verification

- Flyway V22 applied successfully to the local database.
- Operations API Maven build passed.
- Operations Web typecheck and arrival coverage tests passed.
