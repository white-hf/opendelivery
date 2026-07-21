# R04 派送规划效率迭代执行总结

> 完成日期：2026-07-21；状态：`COMPLETED`。关联：[迭代文档](../iterations/iteration-r04-planning-efficiency.md)。

## 已交付

1. **运力默认可用**：
   - 司机无 `driver_shift` 行时默认视为 `AVAILABLE`，默认容量取 `station.default_capacity`（缺省 200）。
   - 班次管理界面改为例外登记模式（仅记录 `UNAVAILABLE` 或自定义容量）。
   - 包含新增 Flyway 迁移 `V14__dispatch_planning_defaults.sql`。
2. **波次编码自动生成**：
   - `WaveRequest.waveCode` 降为可选参数。
   - 留空时自动关联到仓批次号 `{arrivalBatchNo}-W{seq}`（或 `{tripNo}-W{seq}`，无批次时 `W{yyyyMMdd}-{seq}`），保证同站唯一。
3. **司机偏好一键默认分配**：
   - 增加 API `POST /ops/v1/planning/waves/{waveId}/assign-defaults`。
   - 依据 `driver_area_preference` 自动将区域内未分配包裹挂入对应司机任务（`WHOLE_AREA`），严格校验司机容量与重复分配。
4. **待规划/增量视图**：
   - 增加 API `GET /ops/v1/planning/unplanned?serviceDate=`，按区域展示待规划包裹数量与明细。
5. **Web 规划工作台 UI**：
   - `DispatchWorkspace.tsx` 页面全面升级，支持编码自动生成提示与一键默认分配。

## 验证证据

- **后端单元与集成测试**：`./run.sh test` 45 项测试全绿，含 `MapPlanningPolicyTest` 与 API 契约，`BUILD SUCCESS`。
- **前端工具链**：`pnpm typecheck`、`pnpm vitest run`（25 项前端测试全绿）、`pnpm lint`、`pnpm build`（Vite 生产构建成功）。

## 总结

R04 派送规划效率迭代已按规范完全交付。成功消除了每日手动建档与逐个区域选司机的重复人工操作。
