# O08 Configuration & Case Center Iteration

> Status: `COMPLETED` (Completed on 2026-07-21); product: Operations; maps to O08 of the two-product plan. Reference: [Execution Summary](../summaries/iteration-o08-config-case-center-summary.en.md).

## Operational outcome

Pilot master data (drivers, stations) is maintainable; exception Cases have owners and SLAs with a full audit trail; upstream callbacks are queryable and dead letters replayable; every operation is searchable in the audit log.

## Scope

1. **Master data**: driver management page (activate/deactivate, home station, default capacity — consistent with R04 default-capacity semantics), station and service-area maintenance entries (areas exist; drivers/stations become viewable and toggleable).
2. **Case workflow**: full `operational_case`/`case_action` lifecycle — create (system/manual), claim, reassign, action records, resolve, close; owner and SLA-due markers; two-way drill between Cases and parcels/batches/manifests.
3. **Outbox operations**: paginated `outbox_event`/`callback_attempt` queries (filter by partner/station/status), masked payload viewing, dead-letter replay (high-risk action with second confirmation + audit).
4. **Audit search**: read-only `operation_audit_log` search by resource type/ID, actor, and time range; sensitive fields masked.
5. **RBAC checks**: configuration actions restricted to ADMIN/supervisor roles; the matrix goes into tests.
6. **Frontend**: case center (queue + timeline + SLA markers), integration monitor (outbox query/replay), administration (drivers/stations), audit search — three languages.

## Non-goals

- No change to the Outbox dispatcher's retry/lease mechanics (query and replay entry only); no organization hierarchy or multi-station rollup.
- No configuration center (partner secrets stay deployment-managed, read-only in UI).

## Dependencies

- V1 Case/Outbox/audit main chain, I03 operations identity and roles (delivered).

## Migration and compatibility

- If Case gains `sla_due_at`/owner columns or `case_action` extensions, add a Flyway migration (grab the next number); add-only.

## Risks

- Replay causing duplicate callbacks: replay goes through the dispatcher's existing idempotency keys; repeated replays are safe (test-covered).
- Audit/payload PII: one shared masking function for query results; pages never echo full phones/addresses.

## Tests and DoD

- Java: SLA computation, replay idempotency, RBAC matrix, masking.
- Real-MySQL E2E: dead letter → query → replay → ACK; full Case lifecycle; audit search; cross-station 403; self-cleaning.
- Web: Vitest + Playwright; contract and web-spec (4.7/4.10 refresh) synced bilingually.
