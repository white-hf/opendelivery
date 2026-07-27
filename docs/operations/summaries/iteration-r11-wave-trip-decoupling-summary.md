# R11 Wave 与 Trip 解耦执行总结

状态：`IN_PROGRESS`（2026-07-27）

## 已完成

- `MapPlanningService` 不再根据 Wave 自动创建 `arrival_trip` 或默认 `handling_unit`。
- 未填写 Trip No 时，Wave 使用独立日期序列编码，不再从已有 Trip 推导 Wave Code。
- 填写 Trip No 仍执行当前站点和营业日校验；未填写时 `arrival_trip_id` 保持为空。
- 运营端 Trip 选择改为真正可选：不再默认选择第一条 Trip，也不再根据 Wave Code 自动匹配 Trip。
- `/delivery/**` 和 Operations 现有 API 字段未改变。

## 验证

- Operations API Maven 构建通过。
- Operations Web `pnpm typecheck` 通过。

## 待补

- 增加后端“不生成 Trip/板笼”的数据库断言测试。
- 增加 Wave 无 Trip 继续板笼和司机规划的 E2E 测试。

## 追加变更：到仓运输信息修改

- 新增 `PATCH /ops/v1/arrival-trips/{tripId}/transport`，可修改车牌、封签号、预计到仓时间和备注。
- 已到达但未关闭/取消的车次仍可修正运输信息，关闭或取消后只读。
- 修改必须提供原因，服务端使用 JPA 锁定实体并写入操作审计日志。
- 到货清单车次详情增加“修改车辆/时间”入口，支持中途换车/预计时间变更。
