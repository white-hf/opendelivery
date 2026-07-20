# R02 Map Planning Execution Summary

## Delivered

- Flyway V11 adds driver-day shifts/capacity, wave `FROZEN`, freeze actor/time, immutable task-area references, and query indexes.
- Planning APIs cover parcel/exception layers, shift upsert, empty batches, multi-driver parcel/whole-area assignment, draft reassignment, freeze preflight, and publish.
- Hard gates enforce station ownership, published area version, attendance, total daily capacity across batches, unique active parcel task, draft editing, and frozen-only publish.
- The map-first Web workspace provides Google parcel points, selection, exception colours, fixed batch actions, capacity, parcel/area assignment, parcel drawer, and reassignment.
- `004_r02_experience_seed.sql` creates six drivers, 72 parcels, and four areas in each of YHZ/YYZ/YVR. Each station has 69 geocodes and 66 matches plus missing-coordinate and unmatched-area exceptions.

## Evidence

- Maven: 28 tests pass, including pre-arrival planning, state gates, and hard capacity policy.
- Web: 19 Vitest tests, TypeScript, ESLint, and production build pass.
- Real MySQL: Flyway 10→11 succeeded; all three fixture counts passed; YHZ completed `DRAFT→assign 3→FROZEN→PUBLISHED`, and planning publish did not change custody.

## Open Before Closure

R02 still requires operator browser acceptance and unattended E2E for complete three-station commands, cross-station denial, and concurrent rollback. R02 is not marked fully complete until that evidence exists.
