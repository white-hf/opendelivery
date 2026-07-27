# R12 已有 Wave 后补关联 Trip 执行总结

状态：`COMPLETED`（2026-07-27）

## 交付内容

- 新增 `PATCH /ops/v1/planning/waves/{waveId}/arrival-trip`，用于已有 Wave 显式关联或清除到仓干线车次。
- 服务端使用当前站点上下文和行锁校验 Wave；Trip 只能关联同一站点的 `external_trip_no`，跨站点或不存在时拒绝。
- 关联结果通过 JPA `DispatchWaveEntity` 持久化，保留现有创建 Wave API 和司机端 `/delivery/**` 契约不变。
- 关联/清除均写入 `operation_audit_log`，要求操作原因。
- 运营页面的 Trip 下拉允许清除；已有 Wave 选择 Trip 后只有点击“保存 Trip 关联”才写库，切换步骤不会隐式保存。

## 验证

- `./run.sh build` 通过。
- Operations Web `pnpm run typecheck` 通过。
- 手工验收重点：已有 Wave 选择后刷新仍显示 Trip；未点击保存时数据库关联不变；清除后 `arrival_trip_id` 为空。
