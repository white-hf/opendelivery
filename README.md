# EasyDelivery — Open Last Mile Operations Platform

EasyDelivery is an open-source Last Mile system designed around the real operating SOP: receive upstream orders, route them to a station, receive the linehaul, plan a delivery wave, assign areas and drivers, scan and approve handover, monitor delivery, process failures/returns, and close the business day. The product goal is a simple, auditable operating loop for station teams—not a collection of disconnected screens.

This repository is one part of the wider EasyDelivery open-source system. Related products include the Driver App and its backend APIs, address parsing/geocoding services, upstream partner connectors, and operational data services. Together they provide the order-to-delivery workflow for multi-city, single-station operations.

---

## 1. System Architecture & Domain Services

The repository is partitioned into domain-driven multi-module architectures:

- **`driver/` (Port 9000)**: Driver Execution APIs
  - `easydelivery-auth`: Driver authentication, credential validation, and JWT session handling.
  - `easydelivery-delivery`: Driver task execution, parcel list query, and proof-of-delivery (POD) photo evidence uploads.
  - `easydelivery-scan`: Parcel barcode scanning, batch creation, discrepancy scan reports, and batch reviews.
  - `easydelivery-driver-api`: Spring Boot entry point for Driver API.
- **`operations/` (Port 9001 & Frontend 5173)**: Operations Workbench & Management Engine
  - `easydelivery-ops-api`: Operations API engine handling station arrival, order readiness, OSRM route optimization, map dispatch planning, exceptions case center, and control tower monitoring.
  - `easydelivery-operations-web`: React 18 + Vite + Ant Design + Google Maps沉浸式全屏地图调度工作台.
- **`easydelivery-common`**: Shared JPA entities, DTOs, response schemas, exception handlers, Flyway schema migrations, and OSRM TSP integration components.

The Operations Web is intentionally organized by the daily SOP. The control tower identifies the next operational action; arrival, readiness, dispatch, scanning, handover, delivery, exception, and closeout workspaces execute that action with station-scoped data and audit trails.

---

## 2. Technical Stack

- **Language & Runtime**: Java 17
- **Framework**: Spring Boot 3.3.0, Spring Data JPA, Spring JDBC
- **Frontend Workbench**: React 18, Vite, TypeScript, Ant Design, Google Maps JS API
- **Route Optimization Engine**: Open Source Routing Machine (OSRM Server at `http://localhost:5001`)
- **Database**: MySQL 8.0 with Flyway migrations (`spring.jpa.hibernate.ddl-auto=none`)
- **PRD & Governance**: PRD baseline (`docs/prd/`) and Iteration specifications (`docs/operations/iterations/`)

---

## 3. Helper Script (`run.sh`) Commands

Use the bundled `./run.sh` script to manage builds, tests, and local services:

```bash
# Run unit tests across all Maven modules
./run.sh test

# Build production JAR artifacts (skipping tests)
./run.sh build

# Start Operations API service on Port 9001
DB_PASSWORD='<secret>' ./run.sh run

# Start Driver API service on Port 9000
DB_PASSWORD='<secret>' ./run.sh run-driver

# Build and launch Docker containers
./run.sh docker-build
./run.sh docker-up
```

---

## 4. Key Feature Highlights

### 🧭 OSRM Single-Driver Dispatch Route Optimization (R08)
- **Pure Parcel TSP Graph Optimization**: Calculates optimal delivery sequence across 1-N assigned areas per driver using local OSRM-Server (`http://localhost:5001/trip/v1/driving/...`).
- **Resilient Fallback**: Automatically falls back to spatial nearest-neighbor geometric sorting if the OSRM service is unreachable.
- **Visual Sequence Waterdrop Markers**: Renders performance-optimized SVG Waterdrop Markers (`#1`, `#2`, `#3`...) with directional polyline connecting paths on Google Maps.
- **Zero-Break API Contract**: Exposes sequence via existing `route_no` and `stop_sequence` fields, enabling driver mobile Apps to order task lists without API breaking changes.

### 📊 Modernized Control Tower & SOP Pipeline
- 7-Stage automated operations pipeline flow (Data Ingestion -> Arrival -> Readiness -> Dispatch -> Delivery -> Exceptions -> Day Close).
- Modern gradient Hero KPI cards with vector badges and Outfit/Inter numerical typography.

---

## 5. Operations Web UI Quick Start

```bash
# Navigate to web UI directory
cd operations/easydelivery-operations-web

# Install dependencies and start Vite dev server
pnpm install
pnpm dev
```
Open [http://localhost:5173/](http://localhost:5173/) in your browser to access the Operations Workbench.

---

## 6. PRD Governance & Documentation

Governance documentation is organized under `docs/`:
- **[PRD Baselines](./docs/prd/)**: Long-lived product, architecture, workflow, and page specifications. Start with the [Operations Web Specification](./docs/prd/operations-web-specification.md) and [Operations System Product Model](./docs/prd/operations-system-product-model.md).
- **[Iteration Specs](./docs/operations/iterations/)**: Reviewed sprint scope, API deltas, boundaries, and definition of done. See the [R08 route optimization iteration](./docs/operations/iterations/iteration-r08-osrm-route-optimization.md) as an example.
- **[Delivery Summaries](./docs/operations/summaries/)**: Completed iteration outcomes and verification evidence.
- **[Design Documents](./docs/design/)**: Architecture, API contracts, data model, persistence ADR, and reusable engineering principles.

When a capability changes, update the relevant PRD baseline first, add or update a reviewed iteration specification, then implement and record verification in the iteration summary. The [agent onboarding guide](./docs/agent-onboarding.md) explains the repository workflow and constraints.

---

## 7. Security & Secrets Management

Do not commit production secrets or environment tokens. Supply `DB_PASSWORD`, `JWT_SECRET`, and `UPSTREAM_API_KEY` via environment variables.
