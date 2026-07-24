# Iteration Specification: Iteration O09 - Operations Control Tower & Baseline Monitoring Refinement

> **Document Status**: `REVIEWED`  
> **Lead Architect**: Lead Architect  
> **Date**: 2026-07-24  
> **Domain**: Operations Domain (`docs/operations/`)  

---

## 1. Iteration Goal

In accordance with the approved operations control tower design and `docs/prd/operations-control-tower.md` specifications, this iteration refactors and upgrades the **Operations Web Home Screen - Today Operations Control Tower Dashboard**.

The core objectives of this iteration are:
1. **Clean up redundant features**: Remove unpractical countdown clocks and physical warehouse floor rendering, keeping the dashboard 100% aligned with the system's 9 standard primary navigation IA.
2. **Refactor Today SOP Pipeline**: Clearly display the 7-stage workflow status (`COMPLETED` / `IN_PROGRESS` / `BLOCKED` / `NOT_STARTED`) from Data Ingestion to Day Close, supporting click-to-drilldown into dedicated feature workbenches.
3. **Implement Key Operational Realities**:
   * **Manifest EDI Discrepancy**: Display Expected vs Scanned count, highlighting Missing, Wrong Station, and Damaged parcels with automatic operational case generation.
   * **On-Road Performance SPH Baseline Supervision**: Evaluate driver delivery efficiency variance based on historical area Baseline SPH (Stops/Parcels Per Hour), highlighting lagging and stalled drivers alongside POD photo audit.
4. **Driver Capacity Summary**: Provide a clear overview of active drivers, vehicle types, hard capacity limits (`driver_shift.parcel_capacity`), and assigned parcel ratios.

---

## 2. Scope & Non-Goals

### 2.1 In-Scope

1. **PRD & Design Docs Alignment**:
   * Update `docs/prd/operations-control-tower.md`.
   * Update `docs/design/api-contracts.md` (add/enhance control tower aggregation APIs).
2. **Backend API Delivery (`/ops/v1/control-tower/**`)**:
   * `GET /ops/v1/control-tower/summary`: Provides 7-stage SOP status, KPI metrics, and blocking case counts.
   * `GET /ops/v1/control-tower/driver-capacity`: Provides active driver capacity limits and assigned loads.
   * `GET /ops/v1/control-tower/on-road-supervision`: Provides on-road driver efficiency metrics comparing **Actual SPH vs. Area Baseline SPH**, status rating (`NORMAL` / `LAGGING` / `STALLED`), and **Missing POD count**.
   * `GET /ops/v1/control-tower/inbound-discrepancy`: Provides Inbound Manifest expected vs scanned discrepancy counts.
3. **Frontend Operations Web Refactoring (`TodayOperations`)**:
   * Deliver the high-fidelity Today SOP Stepper Pipeline component.
   * Deliver the Baseline SPH On-Road Supervision data table.
   * Deliver the Manifest EDI Discrepancy drawer with direct links to Inbound and Case Center.
   * Support trilingual (`en-CA` / `fr-CA` / `zh-CN`) i18n keys.
4. **Testing & E2E Validation**:
   * Backend unit tests (Control Tower Service & SPH calculation logic).
   * Write MySQL E2E script `scripts/control-tower-o09-e2e.sh`.

### 2.2 Non-Goals

* ❌ **No Schema/Flyway Changes**: Fully utilizes existing 32 MySQL tables without modifying database schemas.
* ❌ **No Dispatch Planning Workbench Changes**: Map-first dispatch planning and wave freezing remain in the dedicated `/planning` page.
* ❌ **No High-Frequency Real-time GPS Streams**: SPH calculation relies on `delivery_attempt` timestamps.

---

## 3. DoD (Definition of Done)

1. PRD and API contract docs updated in both EN and ZH.
2. Backend `/ops/v1/control-tower/**` endpoints implemented with 100% unit test coverage.
3. Operations Web frontend renders SOP pipeline, SPH supervision, and i18n keys green.
4. `scripts/control-tower-o09-e2e.sh` passes against real MySQL.
5. Summary report delivered.
