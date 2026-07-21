# O05 Handover Approval Iteration

> Status: `COMPLETED` (Completed on 2026-07-21); product: Operations; maps to O05 of the two-product plan, core gate for joint release. Reference: [Execution Summary](../summaries/iteration-o05-handover-approval-summary.en.md).

## Operational outcome

Supervisors review and approve drivers' submitted load scans batch-wide: only parcels covered by the correct driver's valid scans may move custody from station to driver; discrepancies get decisions, rejections reopen, and concurrent approvals have a single winner. The existing `/ops/v1/scan-sessions/{id}/approve` sketch is hardened into the full gate.

## Scope

1. **Batch-wide gate**: approval of any session is allowed only after all required sessions of the wave are submitted (or exception-closed by a supervisor); sessions with zero valid scans cannot be approved; gate failures name the missing sessions/drivers.
2. **Discrepancy decisions**: per piece or per class for missing/wrong-task/unsubmitted — release (exception close), return for rescan (session SUBMITTED → OPEN, the driver rescans personally), or offline-handover note; every decision audited.
3. **Atomic custody transfer**: approval in one transaction — session → APPROVED, task items → LOADED, parcel custody → DRIVER, plus `custody_event`/`parcel_status_event`/`operation_audit_log`/`outbox_event`; any failure rolls everything back, never a half state.
4. **Single-winner concurrency**: pessimistic lock on the session row + precondition check (only SUBMITTED is approvable); concurrent approvals/retries either return the first result idempotently or 409 — never a double custody transfer.
5. **Safe reassignment**: wrong-task parcels found before approval can be reassigned to the correct driver's same-day task (reusing the planning `reassign` transaction and checks), leaving custody-semantics traces.
6. **Frontend**: handover-approval workbench (sessions by wave, gate status, discrepancy list, approve/return/reassign actions, results), three languages; entry via "Handover approval".

## Non-goals

- No changes to driver scanning/submission (D02); no RETURN/station-return approval (O06); no e-signature (O07's sign-off is separate).
- No partial approval: an approval covers the session's valid set; discrepancies are decided, not split-approved.

## Dependencies

- V1 scan/custody/outbox main chain, V6 handover gates, planning waves/tasks (post-R04 outputs), O04 supervision basis (same numbers).

## Migration and compatibility

- No new tables expected; if per-piece decision records need persistence, add a Flyway migration (grab the next number). The approve endpoint keeps its path while semantics upgrade to the full gate; incompatibilities for old callers are flagged in the contract doc.

## Risks

- Custody conservation breaking is a 0.5 P0: the identity "released = validly scanned AND approved" is a hard check, always E2E-tested.
- Concurrent approval/partial failure: row lock + precondition + rollback tests.

## Tests and DoD

- Java: gate matrix, state machine, single-winner concurrency, rollback-without-half-state.
- Real-MySQL E2E: wave → two driver tasks → scan submit → gate blocks → decisions → approve → custody/event/outbox conservation assertions → cross-station 403; self-cleaning.
- Web: Vitest + Playwright; contract (approve semantics upgrade), state-machine doc, web-spec (4.6 refresh) synced bilingually.
