# R02 Map Planning Iteration

## Background and Outcome

Operations must plan delivery before physical arrival using station, service date, parcel geography, and daily driver capacity. R02 delivers a three-city pilot workflow, not a demo-grade single-driver form.

## Operational Loop

1. Routed orders are geocoded and matched to an immutable published area version. Missing coordinates, unmatched areas, and address conflicts become visible exceptions.
2. An operator selects station and service date, records driver availability/capacity, and creates an empty batch draft.
3. The map combines areas, parcels, exceptions, and driver tasks. Operators may assign a whole area or split selected parcels.
4. Each assignment transaction validates station, date, plannable state, unique active task, and capacity. Any failure rolls back the complete command with an actionable reason.
5. Freeze runs the full preflight and prevents normal edits. Publish is allowed only from `FROZEN` and creates expected scan lists without transferring physical custody.
6. Before freeze, reassignment requires a reason and audits source task, destination task, parcel, actor, and before/after values.

## Data and Technical Design

- `driver_shift` stores station-day availability and a capacity snapshot, unique per driver/date.
- `dispatch_wave` gains `FROZEN` and freeze metadata; publish transitions only from `FROZEN`.
- `driver_task_area` references immutable area versions, while `driver_task_item` remains the parcel-level fact and unique-active-task gate.
- Map queries return coordinates, area, task, driver, and exception codes; details load on demand and recipient data is never sent to the map provider.
- Index station/date/status, task state, and spatial columns. Viewport/cursor queries return at most 2,000 points.

## Deliverables

- Shift/capacity, empty draft, whole-area and parcel assignment, unassign/reassign, freeze, and publish APIs.
- A map-first workspace with parcel distribution, areas, exceptions, capacity, parcel drawer, and fixed action bar.
- Repeatable, independently removable `DEMO-R02-*` fixtures for YHZ, YYZ, and YVR with realistic areas, shifts, addresses, coordinates, normal parcels, and explainable exceptions.
- Unit, integration, and three-station E2E tests plus synchronized bilingual API, data model, test, and execution documents.

## Definition of Done

- YHZ, YYZ, and YVR data and commands are isolated; cross-station IDs are rejected.
- A parcel cannot occupy two active tasks; over-capacity and unavailable-driver plans cannot freeze.
- Whole-area assignment, split, reassignment, freeze, and publish pass against real MySQL without partial writes under failure.
- Published tasks expose only each driver's expected scan list and parcels retain station custody.
- Maven, Web tests/typecheck/lint/build, and automated E2E pass; browser experience acceptance is recorded separately.
