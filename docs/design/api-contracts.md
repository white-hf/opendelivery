# OpenDelivery API 完整契约

## 1. 范围与状态标识

本文列出当前代码的全部 HTTP API，并给出 MOV 必须新增的 API 目录。`CURRENT` 表示已有实现，`MOV` 表示已规划、尚未实现；开发不得把规划接口描述为可调用。正式实现时应同步维护 OpenAPI 3.1 文件并以契约测试校验。

## 2. 通用协议

- Base URL 示例：`https://delivery.example.com`；JSON 使用 UTF-8。
- 时间使用 ISO-8601；新 API 必须带 offset，如 `2026-07-19T13:20:00-03:00`。
- Driver：`Authorization: Bearer <access-token>`；Operations 目标同为 Bearer + RBAC，当前兼容 `X-Ops-Api-Key`；Integration 当前使用 `X-Upstream-Api-Key`，目标为 Partner HMAC。
- 新写 API：`Idempotency-Key`（1–160 字符）；更新 API：`If-Match: <version>`。
- 新列表默认 `limit=50`，最大 200，返回 opaque `next_cursor`；禁止无限列表。

当前响应包装：

```json
{"biz_code":"COMMON.QUERY.SUCCESS","biz_message":"Success","biz_data":{}}
```

新 API 成功使用正确的 2xx，失败使用 4xx/5xx，同时保留稳定 `biz_code`。常见映射：400 `VALIDATION.ERROR`，401 `AUTH.UNAUTHORIZED`，403 `AUTH.FORBIDDEN`，404 `RESOURCE.NOT_FOUND`，409 `STATE.CONFLICT/IDEMPOTENCY_KEY_REUSED`，413 `PAYLOAD.TOO_LARGE`，429 `RATE_LIMITED`，500 `INTERNAL.ERROR`。

## 3. Driver Authentication API（CURRENT）

### `POST /auth/register`

开发/初始注册。生产 MOV 应由运营人员创建司机，不开放匿名注册。

| Body 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `credential_id` | string | 是 | 登录账号，唯一 |
| `password` | string | 是 | 明文仅在 TLS 请求中传输 |
| `name` | string | 是 | 司机姓名 |

成功 `biz_data=null`；重复账号返回业务冲突码。

### `POST /auth/login`

Body：`credential_id:string`、`password:string` 均必填。成功：

```json
{"biz_code":"COMMON.QUERY.SUCCESS","biz_message":"Login Success","biz_data":{"driver_id":"1","token_type":"Bearer","access_token":"<jwt>","refresh_token":"<opaque>","expires_in":"7200"}}
```

无效凭证返回 `AUTH.INVALID.CREDENTIALS`；不得说明账号是否存在。

### `POST /auth/refresh`

Body：`refresh_token:string` 必填。服务端验证 hash 和有效期，旋转 access/refresh，响应字段同登录但无 `driver_id`。无效或已使用 Token 返回 401。

### `POST /auth/logout`

Header：Bearer 必填。撤销当前会话；重复退出幂等。成功 `biz_data=null`。

## 4. Driver Task API（CURRENT）

以下查询中的 `driver_id` 是旧 App 兼容字段，必须与 JWT subject 相同；新 API 应移除该字段。

### `GET /delivery/parcels/tasks?criteria={value}&driver_id={id}`

返回本人尚未装车的 `DeliveringListData[]`。`criteria` 当前仅兼容传入，尚未执行过滤。

### `GET /delivery/parcels/delivering?driver_id={id}`

返回本人 `OUT_FOR_DELIVERY` 包裹数组。

### `GET /delivery/to-be-picked-up/brief/{driverId}`

返回：`total_number:int`、`address:string`、`scan_batch_id:long`、`scan_batch_status:int`、`scanned_item_quantity:int`。

### `DeliveringListData`

| 字段 | 类型 | 说明 |
|---|---|---|
| `order_id/order_sn` | long/string | 内部订单 ID/展示号 |
| `tracking_no` | string | 扫描码 |
| `goods_type/express_type` | int | 旧 App 枚举 |
| `route_no` | int | 旧 App 数字线路 |
| `assign_time/delivery_by` | string | 分配/承诺时间 |
| `state/scan_status/need_retry/is_detained` | int | 旧 App 状态标志 |
| `name/mobile/phone_extension` | string | 收件信息，敏感 |
| `address/zipcode/unit_number/buzz_code/building_id` | string | 地址信息 |
| `lat/lng` | string | 目标坐标 |
| `postscript` | string | 配送说明 |
| `warehouse_id` | int | 站点 ID |
| `time_range/since_last_updated` | int | 旧 App 时间标志 |
| `dispatch_type` | object | 兼容对象 `{SZ,SG,DT,SP}` |

这些字段命名保留 Android 契约；V2 API 将改用明确字符串枚举并补齐字段语义。

## 5. Driver Scan API（CURRENT）

### `POST /delivery/scan/batch`

Body：`driver_id:int`、`operator_role:int`、`scan_as:int`。创建本人 LOAD session，返回 `scan_batch_id:long`。MOV 后改为 `POST /driver/v1/tasks/{taskId}/scan-sessions`，以 `session_type` 字符串代替数字。

### `POST /delivery/ext/scan`

Body：

```json
{"tracking_no":"PKG-1001","scan_batch_id":81,"device_event_id":"01J..."}
```

`tracking_no`、`scan_batch_id` 必填；新客户端必须传全局唯一 `device_event_id`。成功返回 `orderId:long`、`trackingNo:string`、`routeNo:int`。可能错误：batch 不存在/非 OPEN、包裹未知、错任务、非本人 session。

### `POST /delivery/scan/batch/report`

Body：`scan_batch_id:long`。返回 `scan_time`、`assigned_parcels_count`、`scanned_parcels_count`、`unscanned_parcels_count`、`unscanned_parcels[{tracking_no,route_no}]`、`returned_parcels_count`、`returned_parcels[]`。

### `PUT /delivery/ext/scan/batch/{scanBatchId}`

Body：`status:string`。当前用于 Review；MOV 必须限制合法命令而非任意状态字符串，并校验主管权限/版本。成功返回 `{status}`。

### `GET /delivery/ext/scan/batch/reports`

Query：`warehouse:int`、`driver_id:int`、`start_date:string(YYYY-MM-DD)`。返回数组：`scan_batch_id,name,dispatch_nos,driver_id,unscanned_parcels,scanned_parcels,returned_parcels,total_return_parcels,scan_time,scan_batch_status`。

## 6. Driver Delivery API（CURRENT）

### `POST /delivery` — `multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `order_id` | long | 是 | 本人活动 task item 对应订单 |
| `longitude/latitude` | double | 是 | 尝试位置 |
| `delivery_result` | int | 是 | 旧枚举；实现映射成功/失败 |
| `failed_reason` | int | 失败时 | 旧失败原因码 |
| `recipient_name` | string | 否 | 实际接收人 |
| `idempotency_key` | string | 新客户端是 | 重试返回首次 Attempt |
| `pod_images[]` | file[] | 按策略 | POD 图片 |

服务端校验 JWT 司机拥有任务，写 attempt、POD、status event 和 outbox。成功 `biz_data=null`。限制文件类型/大小的正式数值在 I05 确认。

### `POST /delivery/retry` — `multipart/form-data`

字段：`order_id:long`、`longitude:double`、`latitude:double`、`driver_id:string` 必填，`pod_img[]` 可选。`driver_id` 必须等于 JWT subject。该兼容接口仅恢复失败任务；MOV 后由运营规则决定是否可重试，不允许司机无限重试。

## 7. Integration API（CURRENT）

### `POST /integration/v1/partners/{partnerCode}/shipments`

Header：当前 `X-Upstream-Api-Key`；目标 Partner HMAC。Body：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `externalEventId` | string | 是 | Partner 内幂等 ID |
| `externalWaybillNo` | string | 是 | Partner 运单号 |
| `externalVersion` | string | 否 | 上游更新版本 |
| `recipientName` | string | 是 | 收件人 |
| `recipientPhone` | string | 否 | 电话 |
| `addressLine1` | string | 是 | 主地址 |
| `addressLine2` | string | 否 | 补充地址 |
| `city/postalCode` | string | 是 | 城市/邮编 |
| `province/countryCode` | string | 否 | 省/ISO 国家码；默认 CA |
| `serviceCode` | string | 否 | 服务产品 |
| `deliveryWindowStart/End` | datetime | 否 | 配送时间窗 |
| `promisedDate` | date | 否 | 承诺日期 |
| `routingHint.stationCode` | string | 否 | 上游可选提示；系统验证但不直接信任 |
| `externalManifestNo` | string | 否 | 到站清单号 |
| `trackingNumbers` | string[] | 是 | 至少一件，值非空 |

示例：

```json
{"externalEventId":"evt-100","externalWaybillNo":"wb-100","recipientName":"Alex","addressLine1":"10 Main St","city":"Halifax","province":"NS","postalCode":"B3H1A1","countryCode":"CA","serviceCode":"STANDARD","trackingNumbers":["PKG-100-A","PKG-100-B"]}
```

返回 `ingestionRecordId:long`、`duplicate:boolean`、`parcelCount:int` 和 `routing{status,stationCode?,reasonCode?}`。上游不需要提供内部站点；系统标准化地址并匹配服务范围。`ROUTED` 后才建立目标 Manifest；`UNROUTABLE/AMBIGUOUS` 创建 Case。重复 event ID 返回首次记录且不重复建单。当前代码仍要求 `targetStationCode`，将在 I02 迁移，迁移完成前本节是目标契约而非 CURRENT 行为。

## 8. Operations API（CURRENT）

### `POST /ops/v1/manifests/{manifestNo}/receipts`

Header `X-Ops-Api-Key`；Body `{"trackingNumber":"PKG-100-A"}`。成功返回 `parcelId:long`、`duplicate:boolean`、`status:"AT_STATION"`。仅允许 Manifest 预期且 Parcel 为 `RECEIVED`；重复收货幂等。

### `POST /ops/v1/waves`

Body：`stationCode:string`、`waveCode:string`、`serviceDate:date`、`routeCode:string?`、`driverId:long`、`trackingNumbers:string[]`。当前接口在一个命令中创建并发布，返回 `waveId`、`taskId`、`parcelCount`、`status:"PUBLISHED"`。MOV 拆分草稿和发布，以便执行门禁/审批。

### `GET /ops/v1/cases`

返回全部开放 Case，字段为 `caseNo,caseType,priority,status,ownerType,ownerId,slaDueAt`。当前无分页和站点过滤，仅限试验；I06 必须修复。

## 9. MOV 新增 API 目录（PLANNED）

### 9.1 多城市站点路由（I02）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /ops/v1/stations` | status,cursor | 多城市站点列表 |
| `GET/POST/PUT /ops/v1/station-service-areas` | city/province/country/postalPrefix/station/version | 服务范围配置 |
| `POST /ops/v1/waybills/{id}/route` | version | 执行/重试系统路由 |
| `POST /ops/v1/waybills/{id}/routing-override` | stationId,reason,version | 人工指定并审计 |

### 9.2 运营身份与准备（I03）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `POST /ops/v1/auth/login` | credential/password | operator access/refresh token |
| `POST /ops/v1/auth/refresh` | refreshToken | 旋转 Token |
| `GET /ops/v1/me` | — | 用户、角色、默认站点 |
| `GET /ops/v1/stations/{id}/readiness` | businessDate | Partner/司机/未闭环检查 |
| `GET /ops/v1/drivers` | stationId,status,cursor | 分页司机摘要 |

### 9.3 入站（I04）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /ops/v1/manifests` | stationId,status,date,cursor | Manifest 分页摘要 |
| `GET /ops/v1/manifests/{id}` | — | 计数、items、差异 |
| `POST /ops/v1/manifests/{id}/scan-events` | trackingNo,deviceEventId,occurredAt | 分类后的收货结果 |
| `POST /ops/v1/manifests/{id}/discrepancies/{itemId}/decisions` | decision,reason,version | 解决/隔离/转站 |
| `POST /ops/v1/manifests/{id}/close` | version,carryoverCaseIds | 关闭或返回门禁失败 |

### 9.4 调度与交接（I05）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /ops/v1/dispatch-candidates` | stationId,date,route,cursor | 可派 Parcel |
| `POST /ops/v1/waves/drafts` | station/date/route | 创建草稿 |
| `PUT /ops/v1/waves/{id}/items` | add/remove parcel IDs,version | 修改草稿 |
| `POST /ops/v1/waves/{id}/publish` | driverId,version | 原子发布及逐件校验 |
| `POST /ops/v1/waves/{id}/cancel` | reason,version | 未开始波次撤回 |
| `POST /driver/v1/tasks/{id}/scan-sessions` | sessionType | 创建本人 session |
| `POST /driver/v1/scan-sessions/{id}/events` | trackingNo,deviceEventId,location,time | 幂等扫描 |
| `POST /driver/v1/scan-sessions/{id}/submit` | version | 提交差异报告 |
| `POST /ops/v1/scan-sessions/{id}/decision` | APPROVE/REJECT,reason,version | 责任交接 |

### 9.5 失败与回站（I06）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /driver/v1/failure-reasons` | serviceCode | 允许原因和证据要求 |
| `POST /driver/v1/task-items/{id}/attempts` | outcome,reason,location,POD,idempotencyKey | Attempt 结果 |
| `POST /driver/v1/tasks/{id}/closeout` | version | 返回未闭环清单 |
| `POST /ops/v1/return-sessions/{id}/decision` | decision,reason,version | custody 回站及下一动作 |
| `POST /ops/v1/parcels/{id}/reschedule` | date,route,reason,version | 次日重新进入可派队列 |

### 9.6 Case、回传和日终（I07–I08）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /ops/v1/cases` | station,status,owner,overdue,cursor | 分页 Case |
| `POST /ops/v1/cases/{id}/assign` | ownerId,version | 领取/分配 |
| `POST /ops/v1/cases/{id}/actions` | action,note,decision,version | 状态变化和审计 |
| `GET /ops/v1/outbox-events` | partner,status,date,cursor | 回传队列 |
| `GET /ops/v1/outbox-events/{id}` | — | payload、attempts、ACK |
| `POST /ops/v1/outbox-events/{id}/replay` | reason | 新发送 attempt |
| `POST /ops/v1/reconciliations` | stationId,businessDate | 计算/重算日终 |
| `GET /ops/v1/reconciliations/{id}` | — | 平衡、明细和不平项 |
| `POST /ops/v1/reconciliations/{id}/sign-off` | carryoverReason,caseIds,version | 主管签字 |

## 10. 兼容、错误与测试要求

旧 `/delivery/**` 在 `0.5` 保持兼容；V2 Driver API 验证稳定后发布弃用日期。禁止复用字段改变语义；只增加可选字段或发布新版本。每个 API 必须具备成功、校验、401、403、404、状态冲突、重复幂等和并发版本测试；Integration 还需签名、防重放、乱序和契约样例测试。
