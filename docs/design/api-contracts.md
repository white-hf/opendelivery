# OpenDelivery API 完整契约

## 1. 范围与状态标识

本文列出当前代码的全部 HTTP API，并给出 MOV 必须新增的 API 目录。`CURRENT` 表示已有实现，`MOV` 表示已规划、尚未实现；开发不得把规划接口描述为可调用。正式实现时应同步维护 OpenAPI 3.1 文件并以契约测试校验。

## 2. 通用协议

- Base URL 示例：`https://delivery.example.com`；JSON 使用 UTF-8。
- 时间使用 ISO-8601；新 API 必须带 offset，如 `2026-07-19T13:20:00-03:00`。
- Driver：`Authorization: Bearer <access-token>`；Operations 目标同为 Bearer + RBAC，当前兼容 `X-Ops-Api-Key`；Integration 当前使用 `X-Upstream-Api-Key`，目标为 Partner HMAC。
- 新写 API：`Idempotency-Key`（1–160 字符）；更新 API：`If-Match: <version>`。
- 新列表默认 `limit=50`，最大 200，返回 opaque `next_cursor`；禁止无限列表。
- 运营和司机接口接受 `Accept-Language: en-CA|fr-CA|zh-CN`。响应 `biz_message` 本地化，但客户端只允许依赖稳定 `biz_code`；不支持的请求语言回退 `en-CA`。

语言设置接口：`PUT /auth/locale`（司机 Bearer，body `locale`，返回 `preferred_locale`）和 `PUT /ops/auth/me/locale`（运营 Bearer，返回 `preferredLocale`）。显式请求头优先于账户设置，之后为站点默认和 `en-CA`。

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

返回 `ingestionRecordId:long`、`duplicate:boolean`、`parcelCount:int`、`routingStatus`、`stationCode?` 和 `routingReasonCode`。上游不需要提供内部站点；`targetStationCode` 仅为可选提示。系统标准化地址并匹配服务范围。`ROUTED` 后才建立目标 Manifest；`UNROUTABLE/AMBIGUOUS` 创建 Case。重复 event ID 返回首次记录且不重复建单。同一幂等键不同报文的冲突检测仍是后续增强项。

## 8. Operations API（CURRENT）

### `POST /ops/v1/manifests/{manifestNo}/receipts`

Header 使用运营 `Authorization: Bearer <accessToken>`；迁移期可通过配置临时启用 `X-Ops-Api-Key`。Body `{"trackingNumber":"PKG-100-A"}`。成功返回 `parcelId:long`、`duplicate:boolean`、`status:"AT_STATION"`。仅允许 Manifest 预期且 Parcel 为 `RECEIVED`；重复收货幂等。

### `POST /ops/v1/waves`

Body：`stationCode:string`、`waveCode:string`、`serviceDate:date`、`routeCode:string?`、`driverId:long`、`trackingNumbers:string[]`。当前接口在一个命令中创建并发布，返回 `waveId`、`taskId`、`parcelCount`、`status:"PUBLISHED"`。MOV 拆分草稿和发布，以便执行门禁/审批。

### `GET /ops/v1/cases`

返回当前站点开放 Case，字段为 `caseNo,caseType,priority,status,ownerType,ownerId,slaDueAt`。分页与完整筛选在后续迭代补齐。

## 9. MOV 新增 API 目录（PLANNED）

### 9.1 多城市站点路由（I02）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /ops/v1/stations` | status,cursor | 多城市站点列表 |
| `POST /ops/v1/stations` | stationCode,name,city,province,country,timezone,address | 创建一城一站配置 |
| `GET /ops/v1/station-service-areas` | - | 查询服务范围 |
| `POST /ops/v1/station-service-areas` | stationCode,province,city,postalPrefix?,serviceCode?,priority? | 创建有效服务范围 |
| `POST /ops/v1/waybills/{id}/route` | - | 按当前地址与规则重新计算 |
| `GET/POST/PUT /ops/v1/station-service-areas` | city/province/country/postalPrefix/station/version | 服务范围配置 |
| `POST /ops/v1/waybills/{id}/route` | version | 执行/重试系统路由 |
| `POST /ops/v1/waybills/{id}/routing-override` | stationId,reason,version | 人工指定并审计 |

### 9.2 运营身份与准备（I03）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `POST /ops/auth/login` | username/password | operator access/refresh token |
| `POST /ops/auth/refresh` | refreshToken | 单次旋转 Token，旧 token 撤销 |
| `POST /ops/auth/logout` | Bearer token | 撤销会话 |
| `GET /ops/auth/me` | Bearer token | 用户、角色、默认站点 |
| `GET /ops/v1/readiness` | 管理员可选 `X-Station-Code` | 司机、Manifest、Case、未路由统计 |
| `GET/POST /ops/v1/users` | 用户/默认站点/角色 | 管理员查询或创建运营用户 |

### 9.3 入站（I04，CURRENT）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /ops/v1/manifests` | stationId,status,date,cursor | Manifest 分页摘要 |
| `GET /ops/v1/manifests/{id}` | — | 计数、items、差异 |
| `POST /ops/v1/manifests/{id}/start` | — | 进入 `RECEIVING` 并记录实际到达时间 |
| `POST /ops/v1/manifests/{id}/scan-events` | trackingNo,deviceEventId,occurredAt | 分类后的收货结果 |
| `POST /ops/v1/manifests/{id}/discrepancies/{itemId}/decisions` | decision,reason,version | 解决/隔离/转站 |
| `POST /ops/v1/manifests/{id}/close` | allowCaseCarryover | 关闭或返回差异门禁失败 |

扫码 `conditionCode` 支持 `NORMAL/DAMAGED`；输出为 `RECEIVED/DAMAGED/EXTRA/WRONG_STATION/DUPLICATE`。`deviceEventId` 在 Manifest 内唯一。正常/破损实收原子更新库存、custody、状态事件和 outbox；多货、错站、破损和关闭时少货均关联运营 Case。

### 9.4 调度与交接（I05，CURRENT）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /ops/v1/dispatch/candidates` | limit,afterId | 当前站点可派 Parcel |
| `POST /ops/v1/dispatch/waves` | waveCode,date,route,driverId,trackingNumbers | 创建含任务明细的草稿 |
| `POST /ops/v1/dispatch/waves/{id}/publish` | — | 锁定并逐件校验后发布 |
| `POST /ops/v1/dispatch/waves/{id}/revoke` | — | 未扫码波次撤回并恢复库存 |
| `POST /delivery/scan/batch` | driver_id,scan_as | 司机创建本人 LOAD session |
| `POST /delivery/ext/scan` | tracking_no,scan_batch_id,device_event_id | 本人幂等装车扫描 |
| `PUT /delivery/ext/scan/batch/{id}` | status=`SUBMITTED` | 司机提交；不得自批 |
| `POST /ops/v1/scan-sessions/{id}/approve` | — | 主管批准并转移 custody |

候选库存要求本站、站点 custody、已成功路由、无阻断 Case。活动任务唯一索引阻止同件重复分配。发布后仍由站点保管；只有主管批准已提交的装车 Session 后，Parcel 和 Task Item 才进入 `OUT_FOR_DELIVERY`，并逐件写 custody、状态事件和 outbox。

### 9.5 失败与回站（I06，CURRENT）

| Method/Path | 输入 | 输出/副作用 |
|---|---|---|
| `GET /driver/v1/failure-reasons` | — | 原因、照片/备注要求、下一动作、上限 |
| `POST /driver/v1/task-items/{id}/attempts` | outcome,reasonCode,note,photoEvidence,location,idempotencyKey | 幂等 Attempt |
| `GET /driver/v1/tasks/{id}/closeout` | — | 本人任务状态统计和可关闭标志 |
| `POST /driver/v1/tasks/{id}/return-sessions` | — | 创建本人 RETURN session |
| `POST /driver/v1/return-sessions/{id}/events` | trackingNo,deviceEventId | 本人幂等回站扫描 |
| `POST /driver/v1/return-sessions/{id}/submit` | — | 提交站点审核 |
| `POST /ops/v1/return-sessions/{id}/decision` | action,reason | custody 回站及重派/退上游 |

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
## 9. 区域配置 API（R01，CURRENT）

所有接口要求运营 Bearer Token、`X-Station-Code` 和 `X-Request-Id`；仅 `ADMIN`/`SUPERVISOR` 可访问。响应使用统一 `biz_code/biz_message/biz_data` 包装。

| 方法与路径 | 输入 | 输出/规则 |
|---|---|---|
| `GET /ops/v1/delivery-areas` | 无 | 当前站点全部活动/停用区域及最新工作版本、GeoJSON |
| `GET /ops/v1/delivery-areas/{areaId}/versions` | path ID | 全版本、校验结果、生效时间和审批人 |
| `POST /ops/v1/delivery-areas` | `areaCode`、`areaName`、`areaLevel?`、`geoJson`、`changeReason` | 创建区域和 V1 `DRAFT`，返回 `areaId/versionId/versionNo/status` |
| `PUT /ops/v1/delivery-areas/{areaId}` | `areaName`、`areaLevel`、`geoJson`、`changeReason` | 更新元数据并创建下一 `DRAFT`；`areaCode` 不可变，原发布版本继续服务至新版本发布 |
| `DELETE /ops/v1/delivery-areas/{areaId}` | `reason` | 逻辑停用，停止新匹配并停用司机偏好；不删除版本和历史分配 |
| `POST /ops/v1/delivery-areas/{areaId}/activate` | `reason` | 重新启用并恢复保留的已发布版本参与新匹配 |
| `POST /ops/v1/delivery-areas/{areaId}/versions` | `geoJson`、`changeReason` | 为现有区域创建下一草稿版本 |
| `POST .../{versionId}/validate` | 无 | 校验几何及同站同层重叠；成功转为 `VALIDATED` |
| `POST .../{versionId}/publish` | `reason` | 仅发布已校验版本，原发布版本转 `RETIRED` |
| `GET .../{areaId}/driver-preferences` | 无 | 区域的默认司机偏好及有效期 |
| `POST .../{areaId}/driver-preferences` | `driverId`、`priority?`、`effectiveFrom?`、`effectiveTo?`、`reason` | 幂等新增/更新本站有效司机偏好 |
| `POST /ops/v1/parcels/{parcelId}/area-match` | `longitude`、`latitude`、`providerCode`、`precisionCode`、`confidence?`、`normalizedAddress?`、`reason` | 保存地理编码，按本站已发布区域匹配并持久化具体版本；返回 `areaId/areaVersionId/source` |

GeoJSON 接受 `FeatureCollection`、`Feature`、`Polygon` 或 `MultiPolygon`，服务端统一保存为 WGS84 `MultiPolygon`；混合集合中的 Point/Line 等辅助要素忽略。点面匹配优先选择最高 `areaLevel`；无命中必须进入人工异常队列，不得猜测区域。典型错误：`AREA.GEOJSON.INVALID`、`AREA.OVERLAP`、`AREA.STATE.INVALID`、`AREA.MATCH.NOT.FOUND`、`AREA.COORDINATE.INVALID`。
# R02 地图规划 API

以下接口均要求运营 Bearer Token 和 `X-Station-Code`，资源 ID 跨站访问返回 403。日期使用 `YYYY-MM-DD`；写操作建议携带 `X-Request-Id`。

| 方法与路径 | 输入 | 输出/规则 |
|---|---|---|
| `GET /ops/v1/planning/parcels` | `serviceDate`；可选 `west/south/east/north/limit≤2000` | 包裹 ID/追踪号/状态/custody/地址、经纬度、区域版本、任务司机和 `MISSING_GEOCODE/UNMATCHED_AREA/OPEN_CASE` |
| `GET /ops/v1/planning/shifts` | `serviceDate` | 本站活动司机、出勤、容量和当日所有活动任务已分配数 |
| `PUT /ops/v1/planning/shifts` | `driverId,serviceDate,availabilityStatus,parcelCapacity,note` | upsert 班次；容量 1–1000，司机必须属于本站 |
| `POST /ops/v1/planning/waves` | `waveCode,serviceDate,routeCode?` | 创建无任务的 `DRAFT` 批次，同站编码唯一 |
| `GET /ops/v1/planning/waves/{id}` | — | 批次、司机任务容量汇总和区域快照 |
| `POST .../{id}/assignments` | `driverId,parcelIds[],areaVersionIds[],reason` | 支持逐件和整区；事务校验可计划状态、区域/司机站点、出勤、当日总容量和活动任务唯一 |
| `POST .../{id}/parcels/{parcelId}/reassign` | `driverId,reason` | 仅草稿；原 item 标为 `REASSIGNED`，新任务新增活动 item 并审计 |
| `POST .../{id}/freeze` | `reason` | 非空任务、出勤和当日总容量预检通过后进入 `FROZEN` |
| `POST .../{id}/publish` | `reason` | 仅 `FROZEN→PUBLISHED`；生成应扫清单，包裹改为 `ASSIGNED`，custody 不变 |
