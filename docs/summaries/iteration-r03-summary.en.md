# R03 Physical Arrival Execution Summary

## Delivered

- Flyway V12 adds `arrival_trip`, `handling_unit`, and `handling_unit_parcel` with station uniqueness, state indexes, and foreign keys.
- Trips progress through expected, arrived, unloading, ready-to-scan, and closed; units progress through expected, arrived, opened, and cleared. State skipping is rejected.
- Arrival detail aggregates parcel, driver, and wave coverage. Manual tracking links reject unknown and cross-station parcels.
- The Operations Web provides trip list/create/progression, handling-unit creation, and a detail drawer while retaining upstream-manifest discrepancies.
- Arrival facts never mutate parcel status or custody; driver responsibility transfers only in R05 approval.

## Verification

All 34 Java, 19 Vitest, and 3 Playwright checks pass; lint and production build pass. Real MySQL upgraded V11→V12. API checks exercised trip/unit progression and received 403 when YYZ attempted to read a YHZ resource; temporary records were removed.

## Open scope

R03-C still needs automatic mapping from upstream unit identifiers and realistic three-city, multi-unit/multi-driver fixtures. Operators can currently form coverage by entering tracking numbers. R04 driver-only scanning follows this closure.
