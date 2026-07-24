# R06 SLA Wave Dispatch and Dual Scheduling Strategy Iteration

> Status: `REVIEWED` (Completed on 2026-07-23); Ownership: Operations Product; Extends R04 planning efficiency by introducing SLA time-window dimensions and dual-mode dispatch capabilities.

## Background & Problem Statement

In real last-mile delivery operations (such as UniUni and Dragonfly), incoming parcels carry different SLA delivery commitments (`promised_date`, `SAME_DAY`/`EXPRESS` vs. Standard).
The system must provide enterprise-standard capabilities supporting both UniUni-style bulk clearance and Dragonfly-style SLA-prioritized early morning wave dispatching.

## Scope & Core Changes

1. **SLA Filtering Enhancement**:
   - Backend `MapPlanningService.java` and query API (`/ops/v1/planning/parcels`) support `slaFilter` parameter: `ALL`, `TODAY_DUE` (promised today or express), `STANDARD`.
   - SQL filtering bound to `p.promised_date` and `w.service_code`.
2. **Map Visual Indicators**:
   - `PlanningMap.tsx` highlights express parcels with distinct magenta points and ⚡ icons, displaying promised date tooltips on hover.
3. **Wave Creation UX Fix**:
   - Refetch wave list immediately upon creation to prevent fallback displaying raw auto-increment IDs (e.g. `990044`), showing formatted labels `🌊 20260723-WAVE-01 (DRAFT)`.

## Verification

- Unit Tests: `MapPlanningPolicyTest` updated with SLA filter coverage.
- Full system verification with live MySQL database connection.
