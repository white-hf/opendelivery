# R03 Physical Arrival Iteration

## Operable outcome

Before drivers collect parcels, Operations records which vehicle arrived, when it arrived, and which pallets/cages/bags it carried. Each physical unit exposes its covered waves, tasks, and drivers. A unit may cover multiple drivers; arrival registration is neither piece-level receipt nor custody transfer.

## Slices and order

| Slice | Work | Acceptance evidence |
|---|---|---|
| R03-A Trip | Create arrival trips with partner, vehicle/seal, ETA/ATA, and status | Station-unique reference, cross-station denial, state tests |
| R03-B Handling Unit | Add pallet/cage/bag labels under a trip | Idempotent labels and 1-N task/driver fixtures |
| R03-C Coverage | Link parcels or derive plan coverage; show expected/scanned/exceptions | Aggregate-detail parity; unknown pieces never alter inventory |
| R03-D Workspace | Arrival page with trip list, arrival action, and unit drawer | Three-locale browser regression and clean station switching |

## States and boundary

Trip: `EXPECTED → ARRIVED → UNLOADING → READY_FOR_SCAN → CLOSED`, or `CANCELLED`. Handling unit: `EXPECTED → ARRIVED → OPENED → CLEARED`. Arrival proves only that a container reached the station. R04 determines whether a piece belongs to the scanning driver; driver custody transfers only after R05 approval.

## Definition of Done

Flyway upgrades cleanly; every query enforces station context; create/arrive/open/close actions are audited; retries are idempotent; API fields and states are documented; Java unit, real MySQL, Vitest, Playwright, and three-station isolation gates pass.
