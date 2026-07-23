# R04-B Pre-Arrival Inbound & Outbound Planning Fusion Iteration

> Status: `REVIEWED` (Approved on 2026-07-22); Product Owner: Operations; Combines R03-C handling unit architecture and R04 dispatch efficiency. Deeply merges Inbound pallet planning and Outbound wave planning under 【Step 3: Dispatch Planning】 to align data flow with real-world physical timelines.

## 1. Background & Industry Alignment

### The Physical-Digital Temporal Mismatch:
The current 【Step 4: Inbound Manifest】 contains "create inbound trip" and "add pallet" forms. In real-world shipping (such as Amazon Logistics or DHL), **"Handling Unit Pre-Sort Planning (Inbound Packing Plan)" must be finalized BEFORE the line-haul truck departs, and before the upstream sorting machines kick off (Data Precedes Physical Cargo)**.
If the station waits until the truck physically arrives (Step 4) to manually create pallets in the system, the upstream sorting facility cannot pull the Area-to-Pallet mapping to pre-sort and package the cage, leading to a serious process bottleneck.

### Proposed Optimization:
Merge **"Inbound Pallet Planning"** and **"Outbound Driver Assignment"** into a unified workspace under **【Step 3: Dispatch Planning】** (Pre-arrival Planning Phase). Consequently, **【Step 4: Inbound Manifest】** is transformed into a pure "Physical Execution View", containing only truck arrival confirmation and barcode scanning/reconciliation.

---

## 2. Iteration Scope

### ① Unified Backbone Initiation
* **Silent Auto-Creation**: When the supervisor creates or loads a Dispatch Wave in 【Step 3】, the system automatically instantiates a corresponding `Arrival Trip` (Inbound Trip) and pre-populates 10 default `PALLET` Handling Units (`U01~U10`) in the background, eliminating redundant manual batch forms.
* **Unified Coding Standard**: The Arrival Trip ID, Dispatch Plan ID, and Wave ID share a single consistent identifier (`{stationCode}-{yyyyMMdd}-{seq}`), seamlessly bridging inbound and outbound states.

### ② 【Step 3: Dispatch Planning】 Dual-Channel Sidebar Panel
* **Tab 1: 🚚 Driver & Outbound Assignment**:
  * Keeps clean outbound features: `[ ⚡ Auto-Assign ]` (bulk-assigning parcels to area-responsible drivers in one click), driver dropdown with total assigned piece counts, and reactive map filtering (selecting a driver isolates green/blue pins, removing other drivers' cargo clutter).
* **Tab 2: 📦 干线板笼规划 / Inbound Pallet Planning**:
  * **One-click pre-sorting (Auto area-fill)**: Invokes `/ops/v1/handling-units/{unitId}/area-fill` to automatically pack all station parcels into pallets `U01~U10` based on default area configurations.
  * **Pallet List & Coverage Observation**: Displays pallets, capacity utilization (e.g., `Pallet U01: 45 / 100 pcs`), and linked Area selectors.
  * **Map Bounding Highlights**: Clicking a pallet highlights its physical area (Area Polygon) and cargo markers in the map with a high-visibility purple border.

### ③ 【Step 4: Inbound Manifest】 Execution-Only Simplification
* **Form Removal**: Removes all manual trip creation inputs, pallet additions, and pre-arrival configurations.
* **Streamlined Receiving**: Directly displays the pre-planned trip and pallets. The operator simply clicks **`[ 🚚 Confirm Truck Arrival ]`** (which calls `PUT /ops/v1/trips/{tripId}/arrive`), converting the trip to `ARRIVED` state, and immediately proceeds to scanning.

---

## 3. Non-Goals

* This iteration focuses on UI interaction sequence restructuring and API linking. It does not alter underlying `handling_unit_parcel` or `arrival_trip` entity relationship schemas (retaining V12/V13 integrity).
* Does not modify Driver App `LOAD` handoff guardrails.

---

## 4. Dependencies

* Relies on `V12` and `V13` physical arrival and upstream unit linkage database contracts.
* Relies on the existing `/ops/v1/handling-units/{unitId}/area-fill` API.

---

## 5. Migration & Compatibility

* **Backward Compatibility**: Existing REST endpoints and validation logic remain fully intact. The backend automates the wave-trip coupling under the hood. Existing unit and integration tests are fully preserved.

---

## 6. Testing & Definition of Done (DoD)

* **DoD 1**: Step 3 workspace supports Tab toggles merging Pallet and Driver assignments, stripping all text guidelines.
* **DoD 2**: Step 4 workspace removes all manual Trip/Pallet creation, loading planned datasets directly upon arrival.
* **DoD 3**: Frontend builds cleanly with zero TypeScript compiler errors (`npm run build` is successful).
* **DoD 4**: Backend tests compile and pass (`./run.sh test` succeeds).
* **DoD 5**: Bilingual (ZH/EN) product spec updated and archived in accordance with document governance rules.
