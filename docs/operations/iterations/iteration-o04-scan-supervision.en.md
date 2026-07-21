# O04 Scan Supervision Iteration

> Status: `COMPLETED` (Completed on 2026-07-21); product: Operations; maps to O04 of the two-product plan. Delivered by Second Agent (Agent-Architect). Reference: [Execution Summary](../summaries/iteration-o04-scan-supervision-summary.en.md).

## Operational outcome

Operations watches load-scan progress and exceptions by wave/task/driver in real time — without scanning, submitting, or reassigning on behalf of drivers. Every number shares the same `scan_session`/`scan_event` facts, strictly station-isolated.

## Scope

1. **Supervision aggregate**: `GET /ops/v1/scan-supervision?serviceDate=&waveId=` returns three levels (wave → task → driver): expected (ASSIGNED items), valid (distinct EXPECTED scans), wrong task (WRONG_TASK), unknown (UNKNOWN), duplicate (DUPLICATE), extra (EXTRA), missing (expected − valid), unsubmitted (OPEN sessions).
2. **Session drill-down**: `GET /ops/v1/scan-sessions?taskId=&status=` plus per-session detail (event list, per-class counts, submitted time, device idempotency conflicts).
3. **Exception drill-down**: wrong-task/unknown/duplicate events with tracking number, scanning driver, and the correct-task hint (from `driver_task_item` membership).
4. **Single counting truth**: uses the same fact basis as R03-C's unit `scanned_piece_count` (LOAD session + distinct EXPECTED) — the two surfaces must never disagree.
5. **Frontend**: scan-supervision workbench (three-level drill-down, progress display, exception markers), three languages; entry via "Driver scan" navigation.

## Non-goals

- No scanning, submitting, closing, or reassigning for drivers (D02/D03 and O05 concerns).
- No approval (O05); no change to `scan_event` classification or the Driver API; DAMAGED folds in after D02.
- No realtime push; pages refresh by query.

## Dependencies

- V1 scan main chain (`scan_session`/`scan_event`), `driver_task(_item)`, R03-C coverage basis.
- The plan allows O04 in parallel once D02 stabilizes; this iteration only reads existing facts and does not depend on D02 work.

## Migration and compatibility

- No new tables, no Flyway; read-only endpoints only; existing operations roles + station context apply.

## Risks

- Scan semantics may shift with D02 hardening → counting lives in exactly one supervision service, so change lands in one place.
- Aggregate performance at large stations → uses the existing `(task_id,item_status)` and `(session_id,scanned_at)` indexes; `serviceDate` is required to bound scans.

## Tests and DoD

- Java: aggregate-basis unit tests (each class, missing formula, empty wave).
- Real-MySQL E2E: two driver tasks + sessions + events; assert three-level counts, exception list, cross-station 403; self-cleaning on the shared database.
- Web: Vitest + Playwright rendering regression; contract and web-spec (new supervision page) synced bilingually.
