# R11 Wave 与 Trip 解耦

> 状态：`IN_PROGRESS`（2026-07-27）

## 目标

明确 Trip 是干线运输/到仓事实，Wave 是站点派送计划。Wave 创建不依赖 Trip，系统不得自动创建 Trip 或临时 Trip。

## 修改范围

- Wave API 保持现有请求和响应字段，`arrivalBatchNo` 可为空。
- 传入 Trip No 时只校验当前站点归属；不传时 `arrival_trip_id` 保持为空。
- 删除 Wave 创建流程中自动创建 `arrival_trip` 和默认 `handling_unit` 的逻辑。
- 运营 UI 不默认选择 Trip，不通过 Wave Code 推导 Trip；Trip 由到货清单统一创建，Wave 后续可选关联。
- 增加不关联 Trip 的 Wave 创建、规划和司机分配测试。

## 验收标准

- 不传 Trip 创建 Wave 成功，且不会新增 `arrival_trip` 或 `handling_unit`。
- 有效 Trip 可选关联，跨站点 Trip 被拒绝。
- 未关联 Trip 的 Wave 可以继续区域、板笼和司机规划。
- 现有 API 路径、字段和司机 App 协议不变。
