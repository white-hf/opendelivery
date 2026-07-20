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

Response: `ingestionRecordId`, `duplicate`, `parcelCount`, `routingStatus`, `stationCode?`, and `routingReasonCode`. Upstreams need not know internal stations; `targetStationCode` is only an optional hint. Only `ROUTED` creates the station Manifest; `UNROUTABLE/AMBIGUOUS` creates a Case. Same-key/different-body conflict detection remains a later enhancement.

## 8. Operations (CURRENT)

- Operations endpoints use `Authorization: Bearer <accessToken>`; a configurable legacy API-key switch exists only for migration. `POST /ops/v1/manifests/{manifestNo}/receipts`: body `trackingNumber`; response `parcelId`, `duplicate`, `status:"AT_STATION"`. Expected RECEIVED items only; duplicate receipt is idempotent.
- `POST /ops/v1/waves`: body `stationCode`, `waveCode`, `serviceDate`, optional `routeCode`, `driverId`, nonempty `trackingNumbers`; response `waveId`, `taskId`, `parcelCount`, `status:"PUBLISHED"`. MOV splits draft and publish.
- `GET /ops/v1/cases`: selected-station open cases with `caseNo,caseType,priority,status,ownerType,ownerId,slaDueAt`. Pagination remains planned.

## 9. MOV API Catalog (PLANNED)

### I02 Multi-City Station Routing

| Method/path | Input | Result |
|---|---|---|
| `GET /ops/v1/stations` | status,cursor | multi-city station page |
| `POST /ops/v1/stations` | stationCode,name,city,province,country,timezone,address | create one-city station |
| `GET /ops/v1/station-service-areas` | - | list service areas |
| `POST /ops/v1/station-service-areas` | stationCode,province,city,postalPrefix?,serviceCode?,priority? | create active area |
| `POST /ops/v1/waybills/{id}/route` | - | rerun current address through rules |
| `GET/POST/PUT /ops/v1/station-service-areas` | coverage/station/version | service-area configuration |
| `POST /ops/v1/waybills/{id}/route` | version | execute/retry system routing |
| `POST /ops/v1/waybills/{id}/routing-override` | stationId,reason,version | audited manual assignment |

### I03 Identity and Readiness

| Method/path | Input | Result |
|---|---|---|
| `POST /ops/auth/login` | username/password | operator access/refresh |
| `POST /ops/auth/refresh` | refreshToken | one-time rotation and old-token revocation |
| `POST /ops/auth/logout` | Bearer token | revoke session |
| `GET /ops/auth/me` | Bearer token | user/roles/default station |
| `GET /ops/v1/readiness` | optional admin `X-Station-Code` | driver/manifest/case/unrouted checks |
| `GET/POST /ops/v1/users` | user/default station/role | admin list/create operators |

### I04 Inbound (CURRENT)

| Method/path | Input | Result |
|---|---|---|
| `GET /ops/v1/manifests` | station,status,date,cursor | manifest page |
| `GET /ops/v1/manifests/{id}` | — | counts/items/discrepancies |
| `POST /ops/v1/manifests/{id}/start` | — | enter `RECEIVING` and record arrival |
| `POST /ops/v1/manifests/{id}/scan-events` | trackingNo,deviceEventId,time | classified receipt |
| `POST /ops/v1/manifests/{id}/discrepancies/{itemId}/decisions` | decision,reason,version | resolve/quarantine/redirect |
| `POST /ops/v1/manifests/{id}/close` | allowCaseCarryover | close or discrepancy gate error |

Scan `conditionCode` supports `NORMAL/DAMAGED`; outcomes are `RECEIVED/DAMAGED/EXTRA/WRONG_STATION/DUPLICATE`. `deviceEventId` is unique within a Manifest. Normal and damaged receipts atomically update inventory, custody, status events, and outbox. Extra, wrong-station, damaged, and close-time missing pieces link to Operations Cases.

### I05 Dispatch and Handover (CURRENT)

| Method/path | Input | Result |
|---|---|---|
| `GET /ops/v1/dispatch/candidates` | limit,afterId | selected-station candidates |
| `POST /ops/v1/dispatch/waves` | waveCode,date,route,driverId,trackingNumbers | draft with task items |
| `POST /ops/v1/dispatch/waves/{id}/publish` | — | lock, revalidate, and publish |
| `POST /ops/v1/dispatch/waves/{id}/revoke` | — | revoke unscanned wave and restore inventory |
| `POST /delivery/scan/batch` | driver_id,scan_as | driver-owned LOAD session |
| `POST /delivery/ext/scan` | tracking_no,scan_batch_id,device_event_id | owner-only idempotent load scan |
| `PUT /delivery/ext/scan/batch/{id}` | status=`SUBMITTED` | driver submits but cannot approve |
| `POST /ops/v1/scan-sessions/{id}/approve` | — | supervisor approval and custody handover |

Candidate inventory must be at the selected station, in station custody, routed successfully, and free of blocking Cases. The active-task unique index prevents duplicate allocation. Publication retains station custody. Only supervisor approval of a submitted Load Session moves Parcel and Task Item to `OUT_FOR_DELIVERY` and writes custody, status event, and outbox per piece.

### I06 Failure and Return (CURRENT)

| Method/path | Input | Result |
|---|---|---|
| `GET /driver/v1/failure-reasons` | — | reason, evidence, next action, limit |
| `POST /driver/v1/task-items/{id}/attempts` | outcome,reasonCode,note,photoEvidence,location,key | idempotent Attempt |
| `GET /driver/v1/tasks/{id}/closeout` | — | owner task counts and closeability |
| `POST /driver/v1/tasks/{id}/return-sessions` | — | owner RETURN session |
| `POST /driver/v1/return-sessions/{id}/events` | trackingNo,deviceEventId | owner-only idempotent return scan |
| `POST /driver/v1/return-sessions/{id}/submit` | — | submit for station review |
| `POST /ops/v1/return-sessions/{id}/decision` | action,reason | station custody and redispatch/upstream return |

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
## 9. Delivery Area APIs (R01, CURRENT)

Every route requires an operator bearer token, `X-Station-Code`, and `X-Request-Id`; only `ADMIN` and `SUPERVISOR` may use them. Responses use the standard `biz_code/biz_message/biz_data` envelope.

| Method/path | Input | Result/rule |
|---|---|---|
| `GET /ops/v1/delivery-areas` | none | Station areas with the preferred published/latest version and GeoJSON |
| `GET /ops/v1/delivery-areas/{areaId}/versions` | path ID | Complete version, validation, effective-time and approver history |
| `POST /ops/v1/delivery-areas` | `areaCode`, `areaName`, optional `areaLevel`, `geoJson`, `changeReason` | Create area and V1 `DRAFT`; return IDs/version/status |
| `POST /ops/v1/delivery-areas/{areaId}/versions` | `geoJson`, `changeReason` | Create the next draft version |
| `POST .../{versionId}/validate` | none | Validate geometry and same-station/level overlap; move to `VALIDATED` |
| `POST .../{versionId}/publish` | `reason` | Publish a validated version and retire its predecessor |
| `GET .../{areaId}/driver-preferences` | none | Default driver preferences and effective dates |
| `POST .../{areaId}/driver-preferences` | `driverId`, optional `priority/effectiveFrom/effectiveTo`, `reason` | Idempotently save an active same-station driver preference |
| `POST /ops/v1/parcels/{parcelId}/area-match` | coordinates, provider/precision, optional confidence/address, `reason` | Save geocode, spatially match a published version, and persist `areaId/areaVersionId/source` |

GeoJSON may be a `Feature`, `Polygon`, or `MultiPolygon` and is normalized to a WGS84 `MultiPolygon`. Matching chooses the highest `areaLevel`; no match must become an operator exception rather than a guessed assignment. Expected errors include `AREA.GEOJSON.INVALID`, `AREA.OVERLAP`, `AREA.STATE.INVALID`, `AREA.MATCH.NOT.FOUND`, and `AREA.COORDINATE.INVALID`.
