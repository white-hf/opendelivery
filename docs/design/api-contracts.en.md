# OpenDelivery Complete API Contract

## 1. Scope and Status

This document lists every current HTTP endpoint and the MOV API catalog. `CURRENT` means implemented; `MOV` means planned and not callable. Implementations must maintain an OpenAPI 3.1 artifact and contract tests.

## 2. Common Protocol

- Example base: `https://delivery.example.com`; UTF-8 JSON.
- New timestamps are ISO-8601 with offset.
- Driver uses `Authorization: Bearer`; Operations target Bearer + RBAC (current `X-Ops-Api-Key`); Integration currently uses `X-Upstream-Api-Key` and targets partner HMAC.
- New writes require `Idempotency-Key` (1–160); updates require `If-Match: <version>`.
- New lists default to 50, maximum 200, and return opaque `next_cursor`.

Current envelope:

```json
{"biz_code":"COMMON.QUERY.SUCCESS","biz_message":"Success","biz_data":{}}
```

New APIs use correct HTTP status while retaining stable business codes: 400 validation, 401 unauthorized, 403 forbidden, 404 missing, 409 state/idempotency conflict, 413 too large, 429 limited, 500 internal.

## 3. Driver Authentication (CURRENT)

### `POST /auth/register`

Body: required `credential_id:string`, `password:string`, `name:string`. Success has null data. Anonymous registration is development-only; MOV operator provisioning replaces it.

### `POST /auth/login`

Body: required `credential_id`, `password`. Success data: `driver_id`, `token_type:"Bearer"`, `access_token`, `refresh_token`, `expires_in:"7200"`. Invalid credentials return `AUTH.INVALID.CREDENTIALS` without account enumeration.

### `POST /auth/refresh`

Body: required `refresh_token`. Validates hash/expiry, rotates both tokens, and returns token type/access/refresh/expiry. Invalid or reused refresh returns 401.

### `POST /auth/logout`

Bearer required. Revokes the current session; repeat is idempotent; success data is null.

## 4. Driver Tasks (CURRENT)

The legacy `driver_id` must equal JWT subject; new APIs remove it.

- `GET /delivery/parcels/tasks?criteria={value}&driver_id={id}`: own unscanned tasks. `criteria` is currently accepted but not applied.
- `GET /delivery/parcels/delivering?driver_id={id}`: own `OUT_FOR_DELIVERY` parcels.
- `GET /delivery/to-be-picked-up/brief/{driverId}`: returns `total_number`, `address`, `scan_batch_id`, `scan_batch_status`, `scanned_item_quantity`.

`DeliveringListData` fields: `order_id/order_sn` identifiers; `tracking_no`; legacy `goods_type/express_type/route_no`; `assign_time/delivery_by`; legacy state flags `state/scan_status/need_retry/is_detained`; sensitive `name/mobile/phone_extension`; address `address/zipcode/unit_number/buzz_code/building_id`; target `lat/lng`; `postscript`; `warehouse_id`; legacy `time_range/since_last_updated`; and `dispatch_type {SZ,SG,DT,SP}`. V2 replaces ambiguous numeric enums but preserves Android compatibility until deprecation.

## 5. Driver Scanning (CURRENT)

### `POST /delivery/scan/batch`

Body: `driver_id:int`, `operator_role:int`, `scan_as:int`; creates the authenticated driver's LOAD session; response `scan_batch_id:long`. MOV replaces numeric modes with `POST /driver/v1/tasks/{taskId}/scan-sessions` and a string type.

### `POST /delivery/ext/scan`

Body: `tracking_no:string`, `scan_batch_id:long`, and new-client-required `device_event_id:string`. Response: `orderId`, `trackingNo`, `routeNo`. Errors cover missing/non-open session, unknown parcel, wrong task, and foreign session.

### `POST /delivery/scan/batch/report`

Body `scan_batch_id`. Response: `scan_time`, assigned/scanned/unscanned counts, `unscanned_parcels[{tracking_no,route_no}]`, returned count/list.

### `PUT /delivery/ext/scan/batch/{scanBatchId}`

Body `status:string`; response `{status}`. MOV replaces arbitrary status input with authorized commands and optimistic version.

### `GET /delivery/ext/scan/batch/reports`

Query `warehouse:int`, `driver_id:int`, `start_date:YYYY-MM-DD`. Returns `scan_batch_id,name,dispatch_nos,driver_id,unscanned_parcels,scanned_parcels,returned_parcels,total_return_parcels,scan_time,scan_batch_status`.

## 6. Driver Delivery (CURRENT)

### `POST /delivery` — multipart

Required `order_id:long`, `longitude/latitude:double`, `delivery_result:int`; failure requires `failed_reason:int`; optional `recipient_name`, new-client-required `idempotency_key`, and policy-dependent `pod_images[]`. The server authorizes task ownership and writes attempt, POD, status event, and Outbox. Exact file limits are finalized in I05.

### `POST /delivery/retry` — multipart

Required `order_id`, `longitude`, `latitude`, `driver_id`; optional `pod_img[]`. Body driver must equal JWT subject. MOV moves retry permission to operations rules and prevents unlimited driver retry.

## 7. Integration (CURRENT)

### `POST /integration/v1/partners/{partnerCode}/shipments`

Current auth: `X-Upstream-Api-Key`; target: partner HMAC. Body fields:

| Field | Type | Required | Meaning |
|---|---|---|---|
| `externalEventId` | string | yes | partner idempotency ID |
| `externalWaybillNo` | string | yes | partner shipment number |
| `externalVersion` | string | no | update version |
| `recipientName` | string | yes | recipient |
| `recipientPhone` | string | no | phone |
| `addressLine1` | string | yes | primary address |
| `addressLine2` | string | no | secondary address |
| `city/postalCode` | string | yes | city/postal code |
| `province/countryCode` | string | no | province/ISO country, CA default |
| `serviceCode` | string | no | service product |
| `deliveryWindowStart/End` | datetime | no | delivery window |
| `promisedDate` | date | no | promised date |
| `routingHint.stationCode` | string | no | optional hint; validated, never directly trusted |
| `externalManifestNo` | string | no | inbound manifest number |
| `trackingNumbers` | string[] | yes | at least one nonblank piece |

Response: `ingestionRecordId`, `duplicate`, `parcelCount`, and `routing{status,stationCode?,reasonCode?}`. The upstream need not know internal stations. Only `ROUTED` creates the station Manifest; `UNROUTABLE/AMBIGUOUS` creates a Case. Current code still requires `targetStationCode`; I02 migrates it, so this paragraph is the target contract until then. Same key/different body 409 remains a gap.

## 8. Operations (CURRENT)

- `POST /ops/v1/manifests/{manifestNo}/receipts`: body `trackingNumber`; response `parcelId`, `duplicate`, `status:"AT_STATION"`. Expected RECEIVED items only; duplicate receipt is idempotent.
- `POST /ops/v1/waves`: body `stationCode`, `waveCode`, `serviceDate`, optional `routeCode`, `driverId`, nonempty `trackingNumbers`; response `waveId`, `taskId`, `parcelCount`, `status:"PUBLISHED"`. MOV splits draft and publish.
- `GET /ops/v1/cases`: open cases with `caseNo,caseType,priority,status,ownerType,ownerId,slaDueAt`. Current endpoint lacks pagination/station scope and is experimental.

## 9. MOV API Catalog (PLANNED)

### I02 Multi-City Station Routing

| Method/path | Input | Result |
|---|---|---|
| `GET /ops/v1/stations` | status,cursor | multi-city station page |
| `GET/POST/PUT /ops/v1/station-service-areas` | coverage/station/version | service-area configuration |
| `POST /ops/v1/waybills/{id}/route` | version | execute/retry system routing |
| `POST /ops/v1/waybills/{id}/routing-override` | stationId,reason,version | audited manual assignment |

### I03 Identity and Readiness

| Method/path | Input | Result |
|---|---|---|
| `POST /ops/v1/auth/login` | credential/password | operator access/refresh |
| `POST /ops/v1/auth/refresh` | refreshToken | rotated tokens |
| `GET /ops/v1/me` | — | user/roles/default station |
| `GET /ops/v1/stations/{id}/readiness` | businessDate | partner/driver/carryover checks |
| `GET /ops/v1/drivers` | stationId,status,cursor | driver page |

### I04 Inbound

| Method/path | Input | Result |
|---|---|---|
| `GET /ops/v1/manifests` | station,status,date,cursor | manifest page |
| `GET /ops/v1/manifests/{id}` | — | counts/items/discrepancies |
| `POST /ops/v1/manifests/{id}/scan-events` | trackingNo,deviceEventId,time | classified receipt |
| `POST /ops/v1/manifests/{id}/discrepancies/{itemId}/decisions` | decision,reason,version | resolve/quarantine/redirect |
| `POST /ops/v1/manifests/{id}/close` | version,carryoverCaseIds | close or gate errors |

### I05 Dispatch and Handover

| Method/path | Input | Result |
|---|---|---|
| `GET /ops/v1/dispatch-candidates` | station,date,route,cursor | dispatchable parcels |
| `POST /ops/v1/waves/drafts` | station/date/route | draft |
| `PUT /ops/v1/waves/{id}/items` | add/remove IDs,version | revised draft |
| `POST /ops/v1/waves/{id}/publish` | driverId,version | validated atomic publication |
| `POST /ops/v1/waves/{id}/cancel` | reason,version | cancel unstarted wave |
| `POST /driver/v1/tasks/{id}/scan-sessions` | sessionType | own session |
| `POST /driver/v1/scan-sessions/{id}/events` | tracking,deviceEvent,location,time | idempotent scan |
| `POST /driver/v1/scan-sessions/{id}/submit` | version | discrepancy report |
| `POST /ops/v1/scan-sessions/{id}/decision` | approve/reject,reason,version | custody handover |

### I06 Failure and Return

| Method/path | Input | Result |
|---|---|---|
| `GET /driver/v1/failure-reasons` | serviceCode | allowed reasons/evidence |
| `POST /driver/v1/task-items/{id}/attempts` | outcome,reason,location,POD,key | attempt result |
| `POST /driver/v1/tasks/{id}/closeout` | version | unresolved list |
| `POST /ops/v1/return-sessions/{id}/decision` | decision,reason,version | station custody/next action |
| `POST /ops/v1/parcels/{id}/reschedule` | date,route,reason,version | dispatchable next day |

### I07–I08 Case, Callback, and Closeout

| Method/path | Input | Result |
|---|---|---|
| `GET /ops/v1/cases` | station,status,owner,overdue,cursor | case page |
| `POST /ops/v1/cases/{id}/assign` | ownerId,version | assignment |
| `POST /ops/v1/cases/{id}/actions` | action,note,decision,version | transition/audit |
| `GET /ops/v1/outbox-events` | partner,status,date,cursor | callback queue |
| `GET /ops/v1/outbox-events/{id}` | — | payload/attempts/ACK |
| `POST /ops/v1/outbox-events/{id}/replay` | reason | new send attempt |
| `POST /ops/v1/reconciliations` | stationId,businessDate | calculate/recalculate |
| `GET /ops/v1/reconciliations/{id}` | — | balance/details/variances |
| `POST /ops/v1/reconciliations/{id}/sign-off` | reason,caseIds,version | supervisor sign-off |

## 10. Compatibility and Tests

Legacy `/delivery/**` remains through `0.5`; publish a deprecation date only after V2 validation. Never reuse a field with changed semantics. Every API tests success, validation, 401, 403, 404, state conflict, duplicate idempotency, and concurrent version. Integration additionally tests signature, replay defense, ordering, and partner contract fixtures.
