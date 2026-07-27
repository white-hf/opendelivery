# R12 已有 Wave 后补关联 Trip

> 状态：`COMPLETED`（2026-07-27）

## 目标

允许运营在 Wave 创建后，待干线到仓批次产生时，明确保存 Wave 与真实 Trip 的可选关联。

## 规则

- 不改变现有创建 Wave API。
- 新增独立关联接口，必须明确点击保存。
- Trip 必须属于当前站点；不存在或跨站点时拒绝。
- 关联操作写入操作审计日志。
- 不自动创建 Trip，不自动保存下拉选择。
- 允许清除关联，清除后 `arrival_trip_id` 为空。

## API

```http
PATCH /ops/v1/planning/waves/{waveId}/arrival-trip
Content-Type: application/json

{"arrivalBatchNo":"YHZ-01-20260727-02","reason":"后补干线批次"}
```

清除时将 `arrivalBatchNo` 传为 `null`。接口成功后返回更新后的 Wave 摘要。

## 验收

- 已有 Wave 选择 Trip 后点击“保存关联”，数据库 `arrival_trip_id` 更新。
- 切换步骤但未点击保存，不改变数据库。
- 关联成功后刷新页面仍显示 Trip。
- 现有 Wave 创建、司机 App 和其他 API 协议不变。
