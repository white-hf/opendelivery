# Driver API D03 Iteration Plan: Driver Delivery Fulfillment

> Status: `REVIEWED` (2026-07-23). Confirmed Driver App contract remains compatible with `POST /delivery` and `POST /delivery/retry` paths and Form formats, without requiring App interface changes.

## 1. Scope and Goals

The D03 iteration focuses on driver end-of-mile delivery fulfillment (POD upload, delivery failure, retry delivery), evidence gating, server-side idempotency, and photo SHA-256 deduplication.

### In Scope
1. **Evidence & Reason Gate**:
   * Successful delivery (`delivery_result = 0`): Requires POD photo evidence and GPS coordinates. Rejects missing photo with `POD.EVIDENCE.REQUIRED`.
   * Failed delivery (`delivery_result != 0`): Requires `failed_reason`. Parcel status becomes `DELIVERY_FAILED`.
2. **Server-Side Smart Idempotency**:
   * Uses request `idempotency_key` if provided; otherwise automatically constructs fallback key based on `order_id` + `driver_id` + `delivery_result`.
   * Duplicate submissions return idempotent success without duplicate DB insertion or status corruption.
3. **Retry Delivery Support (`POST /delivery/retry`)**:
   * Allows re-initiating delivery for `DELIVERY_FAILED` parcels, restoring status to `OUT_FOR_DELIVERY`.
4. **POD Photo Storage & SHA-256 Deduplication**:
   * Integrates `PodStorage` component with SHA-256 hash deduplication.
5. **Authorization Gate**:
   * Validates parcel custody (`current_custody_id == driverId`), rejecting cross-driver status updates.

### Out of Scope
* No mandatory changes to Android App request parameters.
* Station return handling (`RETURNED_TO_STATION`) belongs to Operations inbound feature.

## 2. DoD (Definition of Done)

* [x] D03 design document reviewed (`REVIEWED`)
* [ ] Source code implementation, evidence gate, idempotency & retry support
* [ ] Evidence gate, idempotency & authorization unit/integration tests
* [ ] Full `./run.sh test` suite passing
* [ ] D03 execution summary written
