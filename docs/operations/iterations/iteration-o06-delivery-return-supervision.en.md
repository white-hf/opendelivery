# O06 Delivery & Return Supervision Iteration

> Status: `COMPLETED` (Completed on 2026-07-21); product: Operations; maps to O06 of the two-product plan. Reference: [Execution Summary](../summaries/iteration-o06-delivery-return-supervision-summary.en.md).

## Operational outcome

Operations tracks same-day out-for-delivery, failures, and station returns: per-task/driver progress is visible, every failed parcel has a next action (redispatch/station return/approved hold), and parcel status plus custody always stay conserved. The delivered failed-return receipt is one of this iteration's fact bases.

## Scope

1. **In-transit monitor**: `GET /ops/v1/delivery-monitor?serviceDate=` aggregates per task/driver: planned, out for delivery, delivered, failed, returned; timeliness (first attempt, latest event); delivery points with coordinates shown on the map.
2. **Failure queue**: failure-reason distribution; three lanes — redispatchable (under retry cap), awaiting station return (existing list), over-cap pending decision.
3. **Approved hold**: for failed parcels a driver cannot hand back the same day, a supervisor approves the hold with a reason, counting into the conservation formula (taken out = delivered + handed back + approved holds); audited.
4. **Same-station redispatch**: failed parcels redispatch to the correct driver's task the next day (or a later same-day wave) via the reassignment transaction — new task item, old item REASSIGNED; never changes station (cross-city address issues go to Cases).
5. **Frontend**: delivery-monitor page (in-transit map + task list, failure queue, hold/redispatch actions), three languages; the "Delivery monitoring" navigation becomes a laned workbench that keeps the return-receipt drawer.

## Non-goals

- No change to Driver delivery or `/delivery/retry` contracts; no realtime tracking or route optimization (I14); no RETURN session (D04).
- No cross-station transfers.

## Dependencies

- `delivery_attempt`/`proof_of_delivery`/return main chain (V7 + return-receipt slice), O05 custody semantics, O04 shared numbers.

## Migration and compatibility

- If approved holds need new tables/columns (task-item status or a hold record), add a Flyway migration (grab the next number); existing return endpoints unchanged.

## Risks

- Conservation-formula drift (P0): the E2E asserts "taken out = delivered + handed back + approved holds" as a hard gate for any new action.
- Redispatch racing in-transit events: the unique active-slot constraint (`uk_parcel_active_task`) is the backstop, conflict → 409.

## Tests and DoD

- Java: conservation formula, redispatch occupancy conflicts, hold auditing.
- Real-MySQL E2E: task → in transit → failure → three paths (hold / station return / redispatch) → formula assertion → cross-station 403; self-cleaning.
- Web: Vitest + Playwright; contract and web-spec synced bilingually.
