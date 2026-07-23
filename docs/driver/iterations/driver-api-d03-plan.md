# Driver API D03 迭代设计：司机派送履约（妥投/失败/POD/重试/幂等）

> 状态：`REVIEWED`（2026-07-23）。已确认 Driver App 契约保持 `POST /delivery` 与 `POST /delivery/retry` 路径及 JSON/Form 结构兼容，无须 App 改动接口参数。

## 1. 范围与目标

D03 迭代专注于司机进行末端派送履约（POD 妥投 / 失败记录 / 重试派送）的凭证门禁、服务端防重幂等与照片存储去重。

### 包含范围 (In Scope)
1. **凭证与原因门禁 (Evidence Gate)**：
   * 妥投成功（`delivery_result = 0`）：必须上传 POD 照片凭证及 GPS 坐标。无照片拒绝并返回 `POD.EVIDENCE.REQUIRED`。
   * 派送失败（`delivery_result != 0`）：必须提供失败原因 `failed_reason`。包裹状态变更为 `DELIVERY_FAILED`。
2. **服务端智能幂等防护 (Server-Side Idempotency)**：
   * 优先使用请求中的 `idempotency_key`；若 App 未传递，服务端基于 `order_id` + `driver_id` + `delivery_result` 自动合成确定性幂等键。
   * 重复提交时返回幂等成功响应，不引发数据库重复插入或并发状态污染。
3. **重新派送支持 (`POST /delivery/retry`)**：
   * 允许对处于 `DELIVERY_FAILED` 状态的包裹重新发起派送，状态恢复为 `OUT_FOR_DELIVERY`。
4. **POD 照片存储与 SHA-256 去重**：
   * 接入 `PodStorage` 组件，计算 SHA-256 哈希落库去重。
5. **防越权门禁**：
   * 校验包裹归属（`p.current_custody_id == driverId`），禁止越权更新他人包裹状态。

### 非目标 (Out of Scope)
* 不强制 App 端修改现有请求参数或升级协议。
* 失败件退库交接（`RETURNED_TO_STATION`）属于 Operations 运营端接收功能。

## 2. API 契约与规则

* `POST /delivery` (multipart/form-data)：提交妥投/失败 POD 履约。
* `POST /delivery/retry` (multipart/form-data)：发起重新派送尝试。

## 3. DoD (Definition of Done)

* [x] D03 迭代文档确认 (`REVIEWED`)
* [x] 源码实现、凭证门禁、幂等去重与重试加固
* [x] 凭证门禁、强幂等与越权拦截单元/集成测试通过
* [x] `./run.sh test` 全量验证通过
* [x] 编写 D03 执行总结 (`COMPLETE`)
