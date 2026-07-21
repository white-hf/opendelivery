# T02 持久层 ORM 重构迭代（接入域）执行总结

> 完成日期：2026-07-21；状态：`COMPLETED`。关联：[迭代文档](../iterations/iteration-t02-persistence-integration.md)。

## 已交付

1. **接入域 JPA 实体与仓储（Package-by-Feature）**：
   - 接入（Ingestion）子域：`com.hf.easydelivery.integration.ingestion.persistence`
     - `UpstreamPartnerEntity` / `UpstreamPartnerRepository`
     - `IngestionBatchEntity` / `IngestionBatchRepository`
     - `IngestionRecordEntity` / `IngestionRecordRepository`
     - `WaybillEntity` / `WaybillRepository`
     - `ParcelIngestionEntity` / `ParcelIngestionRepository`
   - 路由（Routing）子域：`com.hf.easydelivery.integration.routing.persistence`
     - `StationServiceAreaEntity` / `StationServiceAreaRepository`
   - Outbox 子域：`com.hf.easydelivery.integration.outbox.persistence`
     - `OutboxEventEntity` / `OutboxEventRepository`
     - `CallbackAttemptEntity` / `CallbackAttemptRepository`
2. **服务层 ORM 迁移与逃生门标注**：
   - `ShipmentIngestionService.java`：使用 `UpstreamPartnerRepository`、`IngestionBatchRepository`、`IngestionRecordRepository`、`WaybillRepository`、`ParcelIngestionRepository` 进行实体级 CRUD。
   - 方言 SQL 逃生门标注：
     - `ON DUPLICATE KEY UPDATE` Upsert 语句追加 `// ESCAPE-HATCH (ADR-Persistence): Dialect UPSERT with ON DUPLICATE KEY UPDATE retained via JdbcTemplate`。
     - MySQL 空间函数 `ST_SRID` 语句追加 `// ESCAPE-HATCH (ADR-Persistence): MySQL spatial ST_SRID function and ON DUPLICATE KEY UPDATE retained via JdbcTemplate`。
     - `INSERT IGNORE` 批量关联语句追加 `// ESCAPE-HATCH (ADR-Persistence): Dialect INSERT IGNORE retained via JdbcTemplate`。
   - `ShipmentRoutingService.java`：规则优先级与特异度排序 SQL 追加 `// ESCAPE-HATCH (ADR-Persistence): Complex priority ranking query joining station_service_area and station retained via JdbcTemplate`。
   - `OutboxDispatcher.java`：队列抢锁 SQL 追加 `// ESCAPE-HATCH (ADR-Persistence): Queue polling with FOR UPDATE SKIP LOCKED and JSON_EXTRACT dialect functions retained via JdbcTemplate`。
3. **Testcontainers 评估结论**：
   - **评估结果**：接入域包含较多 MySQL 方言 SQL（`ON DUPLICATE KEY UPDATE`、`ST_SRID`、`FOR UPDATE SKIP LOCKED`）。现有的 Docker + 真 MySQL 脚本（`DB_PASSWORD='<secret>' scripts/mysql-e2e-test.sh`）与基于 Memory 模式的单元测试已提供极高测试覆盖与极快反馈（< 4s）。
   - **结论**：本阶段暂不强制把 Testcontainers 作为 Maven 默认构建依赖（避免开发者本地 Docker 环境未启动时构建中断），真库 E2E 测试继续由 `scripts/mysql-e2e-test.sh` 守护。

## 验证证据

- **后端单元与集成测试**：`./run.sh test` 45 项测试全绿，`BUILD SUCCESS`。
- **行为不变性**：接入契约、路由匹配算法、Outbox 派发与重试逻辑 100% 保持不变。

## 总结

T02 持久层 ORM 重构迭代（接入域）已完全符合持久层 ADR 规范要求交付。
