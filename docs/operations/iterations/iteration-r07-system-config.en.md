# R07-Config Driver and Station Service Area Configuration Center Iteration

> Status: `COMPLETED` (Completed on 2026-07-23); Ownership: Operations Product; Extends I03 system administration to provide Web UI capabilities for Driver and Station Service Area configuration.

## Scope & Core Changes

1. **Backend APIs**:
   - `GET/POST/PUT /ops/v1/system/drivers`: Driver listing, creation, and status toggle.
   - `GET/POST /ops/v1/system/stations/{stationId}/service-areas`: Station coverage rule management.
2. **Frontend UI**:
   - `SystemConfigWorkspace.tsx`: Multi-tab administration console for drivers and station coverage rules.

## DoD

- JUnit 5 tests in `SystemConfigPolicyTest`.
- Clean `pnpm build` frontend bundle.
