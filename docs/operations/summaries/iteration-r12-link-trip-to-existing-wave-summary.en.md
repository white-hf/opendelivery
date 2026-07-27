# R12 Link a Trip to an Existing Wave — Delivery Summary

Status: `COMPLETED` (2026-07-27)

## Delivered

- Added `PATCH /ops/v1/planning/waves/{waveId}/arrival-trip` to explicitly link or clear an inbound linehaul trip for an existing wave.
- The service locks and validates the wave in the selected station context. A trip must belong to the same station; unknown or cross-station trip numbers are rejected.
- The association is persisted through the JPA `DispatchWaveEntity`. Existing wave creation and driver `/delivery/**` contracts remain unchanged.
- Link and clear operations write `operation_audit_log` and require an operator reason.
- The operations UI allows clearing a selection. Selecting a trip for an existing wave only changes local state until “Save Trip association” is clicked; changing steps never silently writes the database.

## Verification

- `./run.sh build` passed.
- Operations Web `pnpm run typecheck` passed.
- Manual acceptance: refresh keeps a saved trip, an unsaved selection does not change the database, and clearing leaves `arrival_trip_id` null.
