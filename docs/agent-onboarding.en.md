# Agent Onboarding: OpenDelivery Operations System & Backend

> Purpose: give any development agent ~1–2 hours of reading after which it can independently own any pending iteration and develop in parallel with others. Read this first, then follow the "Starting an iteration" checklist.

## 0. The system in one sentence

Last-mile logistics: a Java 17 / Spring Boot 3.3 Maven multi-module backend plus a React operations web app, split into two products (Driver API serving drivers themselves; Operations API/Web serving station staff), sharing one MySQL 8 `opendelivery` database (Flyway at V13). Current goal: **three cities (YHZ/YYZ/YVR, one station each) operating five consecutive business days (the 0.5 MOV Gate)**.

## 1. Reading map (in this order)

**Step 1: rules (mandatory)**

| Document | What to take away |
|---|---|
| `AGENTS.md` (repo root) | Build/test commands, code style, data-access conventions |
| `docs/document-governance.md` | Mandatory delivery order: product/contract docs → iteration doc `DRAFT→REVIEWED` → develop → test → execution summary; bilingual (zh/en) sync |

**Step 2: product and business**

| Document | What to take away |
|---|---|
| `docs/prd/operations-system-product-model.md` | Product model, the business-day work flow (upstream push → area membership → arrival batch → unit loading → dispatch planning → driver scan → approval → day close), and the design principle "the system derives, humans handle exceptions" |
| `docs/prd/operations-web-specification.md` | Page-level specs (incl. 4.11 arrival batch workbench) and common interaction rules |
| `docs/roadmap/version-roadmap.md` | Version boundaries: 0.5 MOV committed scope, P0 blockers, I09+ paused |

**Step 3: plan and current state**

| Document | What to take away |
|---|---|
| `docs/iterations/two-product-execution-plan.md` | Product boundaries, iteration definitions, priority chain `O03→D01→D02→O04→O05→D03→D04→O06→O07→joint E2E` |
| `docs/operations/summaries/iteration-r03-c-unit-linkage-summary.md` | Latest arrival-domain delivery and its decisions (exception definition, area projection, recompute semantics) |
| `docs/shared/summaries/iteration-t01-persistence-orm-summary.md` | Persistence refactoring state, escape-hatch list, T02 inputs |
| `docs/driver/summaries/driver-api-current-state-audit.md` | Driver API ground truth (`/driver/v1/**` deleted; `/auth/**`, `/delivery/**` are the real contracts) |
| `docs/operations/summaries/failed-parcel-return-receipt-summary.md` | Failed-return receipt scope already delivered |

**Step 4: design and contracts (read deeply only what your iteration touches)**

| Document | What to take away |
|---|---|
| `docs/design/api-contracts.md` | All Integration/Driver/Operations API contracts and error codes |
| `docs/design/data-model.md` | Tables, indexes, projection pattern, capacity principles |
| `docs/design/state-machines-and-operations.md` | Parcel/task/batch/custody state machines |
| `docs/design/persistence-architecture.md` | Persistence ADR (JPA + escape hatches) — mandatory before writing data-access code |

## 2. Code architecture

```
easydelivery-app/      entry point; config/ (station context/interceptors);
                       integration/ (upstream ingestion: ingestion/ routing/ outbox/);
                       operations/ (operations domain, Package-by-Feature):
                         ├── arrival/      physical arrival & manifest inbound receipt (incl. operations.arrival.persistence)
                         ├── station/      station & service area management (Station/ServiceArea)
                         ├── shared/       cross-domain shared components (e.g. AreaMembershipService, shared DTOs)
                         ├── area/         station delivery areas (GeoJSON)
                         ├── controltower/ control tower read model & dashboard
                         ├── returns/      failed parcel return receipt
                         └── planning/     dispatch planning (deferred until R04 merges)
easydelivery-common/   shared DTOs, AppResponse/BizException, JDBC delivery main chain, token/driver repositories
easydelivery-auth|delivery|scan/  driver-side contract modules (/auth/**, /delivery/**)
easydelivery-operations-web/      React 19 + antd + React Query + i18next (en/fr/zh); workflows/ pages; Vitest + Playwright
scripts/               real-MySQL E2E (*-e2e.sh) and seeds (db/00x_*.sql)
```

Key patterns:

- Operations controllers are split by domain (`ArrivalOpsController`, etc. each declaring `@RequestMapping("/ops/v1")` keeping URL paths 100% unchanged); services are domain-independent.
- Station context: `OperationsAccess.selectedStationId()/requireStation()`, header `X-Station-Code`; **every query and write must be station-scoped**.
- Persistence is domain-scoped: entities and JPA repos live in `<domain>.persistence` (e.g. `operations.arrival.persistence`); four escape-hatch SQL categories stay on `JdbcTemplate` with comments (see ADR).
- Auditing: writes insert `operation_audit_log` (action_code, resource, reason, after_json, request_id).
- Error contract: `AppResponse` envelope + `BizException(code,message)`; the frontend switches on `biz_code`.

## 3. Testing and verification

- Java: `./tools/apache-maven-3.9.8/bin/mvn test` (pure unit tests, no database; add policy-class tests for new shared logic).
- Frontend: `cd easydelivery-operations-web && pnpm typecheck && pnpm vitest run && pnpm lint && pnpm build`.
- Real-MySQL E2E: follow `scripts/arrival-batch-e2e.sh` — boot the app, ops login (ask the user for `DB_PASSWORD`/`OPS_PASSWORD`), switch `X-Station-Code`, end-to-end assertions; **scripts must clean up their own data and must not assume an empty shared database**.
- Playwright: `easydelivery-operations-web/tests/e2e/`; read-only rendering specs must not pollute the shared database.

## 4. Hard constraints (violations mean rework)

1. Docs first: no product code without a `REVIEWED` iteration doc; product/contract/schema/cross-product changes must sync the matching design docs (bilingual).
2. Operations never scans or delivers on behalf of drivers; Driver API identity comes only from the token; arrival facts never change parcel status or custody.
3. Flyway is add-only — check the latest number under `db/migration` first (currently V13, next is V14); never edit a published migration.
4. Idempotency (repeated requests have no second effect), auditing, three languages (every new UI string goes into all three locales in `i18n.ts`), and optimistic locking are default DoD items.
5. Performance: set-based SQL for set work — never row-by-row loops; check index leftmost-prefix for new queries; keep denormalized projections in sync within the same transaction.
6. No `git commit/push` or any git mutation unless the user explicitly asks.

## 5. Parallel development and package refactoring rules (multiple agents)

- **Claiming**: before starting, mark the iteration doc header `IN_PROGRESS` with your agent name and date; on completion write the execution summary and mark `COMPLETED`.
- **Refactoring serialization rule**: R04 is actively modifying `MapPlanningService` and planning UI. Moving `planning` package code must strictly wait until R04 merges. Phase 1 refactoring only includes `integration` splitting + `arrival` (incl. T01 entities)/`station`/`shared` code moves, with zero collision risk.
- **High-conflict files**: `OperationsController.java` (isolated by domain after splitting sub-controllers), `src/i18n.ts`, `docs/design/api-contracts.md`, `data-model.md`, Flyway version numbers, shared seeds/scripts. Rules: always append a new `Object.assign(translations['xx'], {...})` block for i18n instead of editing existing lines; grab the next Flyway number before writing the file.
- **Behavior invariant**: refactoring iterations prove themselves with existing tests + E2E green (API URL paths must remain 100% identical); feature iterations prove themselves with new assertions green and old ones untouched.

## 6. Current Iteration Queue & Takeover (2026-07-21)

Full queue taken over by **Lead Architect**:

| Owner | Iterations (in order) | Notes |
|---|---|---|
| **Lead Architect (Takeover)** | ① O04 Scan Supervision (COMPLETED) → ② R04 Dispatch Planning Efficiency (COMPLETED) → ③ O05 Handover Approval (COMPLETED) → ④ T02 Persistence (COMPLETED) → ⑤ O06 Delivery Supervision (COMPLETED) → ⑥ O07 Day Close (COMPLETED) → ⑦ O08 Config & Case Center (COMPLETED) → ⑧ T03 Persistence (COMPLETED) → ⑨ T04 Persistence (COMPLETED) → ⑩ Joint E2E | O04, R04, O05, T02, O06, O07, O08, T03, T04 delivered and verified end-to-end |
| Paused/later | D01–D04 (paused by user), joint E2E, T03/T04 | Driver stream untouched; joint E2E after full rollout |

Conflict surface: R04 touches `MapPlanningService` and the planning UI; O04 adds a supervision service and UI — coordinate in `OperationsController`, `i18n.ts`, and contract docs per §5; R04 may consume Flyway V14, so always check the latest number first.

## 7. Starting an iteration (checklist)

1. Read the product README and the §1 documents above; locate then read the services, tables, and pages you will change (`Glob/Grep` first).
2. Update PRD/contracts/data model → draft or update the iteration doc → ask the user to mark it `REVIEWED`.
3. Develop: backend services/migration → unit tests → frontend pages/i18n → frontend checks.
4. Real-MySQL E2E (new script or extend an existing one, self-cleaning) → boot the stack for Playwright when needed.
5. Execution summary (bilingual) → update the README index → report verification evidence to the user.
