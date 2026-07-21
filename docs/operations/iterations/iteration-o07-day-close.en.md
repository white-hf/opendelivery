# O07 Day Close Iteration

> Status: `COMPLETED` (Completed on 2026-07-21); product: Operations; maps to O07 of the two-product plan, 0.5 MOV daily close loop. Reference: [Execution Summary](../summaries/iteration-o07-day-close-summary.en.md).

## Operational outcome

Each station reconciles and signs off every business day independently: station inventory, driver holdings, task close-out, Cases, and callback discrepancies are all explainable; sign-off is impossible while hard gates fail; results are recomputable and read-only once signed.

## Scope

1. **Reconciliation compute**: `POST /ops/v1/day-close/recalculate {serviceDate}` computes per station into `daily_reconciliation` (recomputable until signed): station inventory pieces (STATION custody), driver holdings (DRIVER custody per driver), task close-out (CLOSED vs open), unclosed LOAD approvals, unresolved Cases, Outbox dead letters / un-ACKed callbacks, unclosed Manifest discrepancies.
2. **Hard gates**: no unexplained driver holdings (conservation: taken out = delivered + handed back + approved holds), no unclosed approvals, no unresolved P0 Cases, zero dead letters or all Case-linked; each failing item links to its resolution entry.
3. **Sign-off**: `POST /ops/v1/day-close/sign` records signer, time, and the compute snapshot; the day becomes read-only — corrections go through carryover or audited actions, never history rewrites.
4. **Carryover**: gate-failing items may carry to the next day with a reason and a linked Case each; carryover is audited.
5. **Drill-down**: every metric drills into detail (reusing O04 scan, O06 in-transit/failure, Case, and Outbox queries).
6. **Frontend**: day-close page (metric cards + gate status + drill-down + recalculate + sign + carryover), three languages; entry via "Day close". Runbook gains the close-out procedure.

## Non-goals

- No cross-station/headquarter rollup or multi-timezone extension (station timezone already applies); no automatic carryover (human confirms); no financial reconciliation.

## Dependencies

- O05 custody semantics, O04/O06 counting bases, Case/Outbox main chain, `daily_reconciliation` (V1); web-spec 4.9 as the page draft.

## Migration and compatibility

- If `daily_reconciliation` needs new columns (sign-off snapshot / gate-detail JSON), add a Flyway migration (grab the next number); add-only.

## Risks

- Recalculate racing sign-off: pessimistic lock on the station+date row; recompute after signing → 409.
- Metric definitions diverging from O04/O06 → must go through the same source query services; no parallel counting logic.

## Tests and DoD

- Java: recompute idempotency, gate matrix, read-only after signing, lock conflicts.
- Real-MySQL E2E: a full day (ingest → plan → scan → approve → deliver/fail → callback) → reconcile → gate blocks → resolve → sign → conservation assertions; self-cleaning.
- Web: Vitest + Playwright; contract, web-spec (4.9 refresh), Runbook synced bilingually.
