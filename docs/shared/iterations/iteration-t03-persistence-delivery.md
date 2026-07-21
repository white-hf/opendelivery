# T03 持久层 ORM 重构迭代（履约与派送规划域）

> 状态：`COMPLETED`（2026-07-21 完成）；归属：共享平台/运营系统；按 [持久层 ADR](../../design/persistence-architecture.md) Wave 3 执行。已关联 [执行总结](../summaries/iteration-t03-persistence-delivery-summary.md)。

## 背景与目的

在完成 T01（到仓域）和 T02（接入域）持久层规范重构后，本迭代针对运营派送规划与履约管理域进行 Spring Data JPA 实体与 Repository 的建立与重构，涉及 `dispatch_wave`、`driver_task`、`driver_task_item`、`driver_task_area`、`delivery_attempt` 及 `proof_of_delivery` 表。

## 重构范围

1. **JPA Entity 与 Repository 建立**（Package-by-Feature 结构）：
   - `com.hf.easydelivery.operations.dispatch.persistence`:
     - `DispatchWaveEntity` + `DispatchWaveRepository`
     - `DriverTaskEntity` + `DriverTaskRepository`
     - `DriverTaskItemEntity` + `DriverTaskItemRepository`
     - `DriverTaskAreaEntity` + `DriverTaskAreaRepository`
   - `com.hf.easydelivery.operations.supervision.persistence`:
     - `DeliveryAttemptEntity` + `DeliveryAttemptRepository`
     - `ProofOfDeliveryEntity` + `ProofOfDeliveryRepository`
2. **Service 重构与 Escape Hatch**：
   - 重构 `DispatchOperationsService.java` 与 `MapPlanningService.java`，标准单体/主表 CRUD 改用 Spring Data JPA 接口。
   - 对批量插入、复合统计、悲观锁写（`FOR UPDATE`）保留 `JdbcTemplate` 并显式添加 `// ESCAPE-HATCH (ADR-Persistence)` 注释。

## 零行为变更与验证

- API URL 路径 100% 保持现状。
- 运行 `./run.sh test` 确保全 Reactor 单测全绿。
- 运行前端工具链确保打包与校验干净。
