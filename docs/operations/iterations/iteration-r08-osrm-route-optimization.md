# Iteration Specification: R08 OSRM Single-Driver Dispatch Route Optimization & Visual Sequence Waterdrop Markers

> Status: `COMPLETED` (2026-07-25 Completed & Verified)
> Target Domain: Operations / Map Dispatch Planning
> Baseline PRD Reference: `docs/prd/operations-web-specification.md`

## 1. Background & Business Value

In end-to-end parcel delivery operations, dispatchers need to route parcels for each driver in an optimal physical delivery sequence. 
Relying on full-city batch routing is high-risk: a single routing anomaly invalidates the entire station's dispatch layout and causes massive database write lock contention.

This iteration introduces **Single-Driver On-Demand Route Sequence Generation using local OSRM-Server (`http://localhost:5001`)** alongside **Frontend Performance-Optimized Waterdrop Number Markers**. 
Dispatchers can sequence each driver's parcels individually, visually verify the route on the map with waterdrop sequence markers (e.g. `#1`, `#2`, ...), and persist `stop_sequence` safely without global database lockups.

---

## 2. Technical Scope & Architecture Deltas

### 2.1 Backend Operations API (Java Spring Boot)
- **OSRM Integration Client**: Add lightweight REST client to invoke OSRM TSP Trip service: `GET http://localhost:5001/trip/v1/driving/{station_lon,lat};{parcel_1_lon,lat};{parcel_2_lon,lat}...`
- **Single-Driver Route Optimization Endpoint**:
  - `POST /ops/v1/planning/waves/{waveId}/drivers/{driverId}/optimize-route`
  - Calculates TSP order via OSRM, fallback to distance-matrix sorting if OSRM is offline.
  - Updates `driver_task_item.stop_sequence` in a single JDBC batch transaction (`JdbcTemplate.batchUpdate`).

### 2.2 Operations Web UI (React + Google Maps)
- **Map Control Bar Entry**:
  - Add **`🧭 OSRM 智能规划派送路线`** button on Dispatch Workspaces (3.1 & 3.2).
- **Waterdrop Number Marker Rendering (`PlanningMap.tsx`)**:
  - Render SVG Waterdrop Markers with centered sequence numbers (`#1`, `#2`, ... `#N`) for sequenced parcels.
  - Limit dense DOM marker allocation to targeted single driver view (< 300 pins) to maintain **60 FPS map performance**.
  - Show directional polyline connect paths for the active driver's stop sequence.

---

## 3. Database & Schema Verification

- Uses existing database column: `driver_task_item.stop_sequence` (No new database table required).
- Persists wave route identifiers using existing `dispatch_wave.route_code` and `driver_task.task_code`.

---

## 4. Definition of Done (DoD) & Acceptance Criteria

1. **OSRM Call Resilience**: System gracefully falls back to local spatial distance sorting if OSRM endpoint (`http://localhost:5001`) is unreachable or returns non-200.
2. **Single-Driver Scope**: Clicking OSRM route optimization only recalculates `stop_sequence` for the specific targeted driver, leaving other drivers' tasks untouched.
3. **Waterdrop Marker Visualization**: Map displays clear waterdrop markers with sequence numbers and route polylines when focusing on a driver.
4. **Performance**: Map stays smooth (60 FPS) without Marker vibration during lasso selection and route rendering.
5. **Test Coverage**: Passes JUnit unit tests and frontend TypeScript type checking.
