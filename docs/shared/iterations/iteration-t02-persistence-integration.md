# T02 持久层 ORM 重构迭代（接入域）

> 状态：`COMPLETED`（2026-07-21 完成）；归属：共享平台（技术重构，无业务行为变化）；按 [持久层 ADR](../../design/persistence-architecture.md) 第二波执行。已关联 [执行总结](../summaries/iteration-t02-persistence-integration-summary.md)。

## 背景

T01 已建立"命令侧 JPA + 四类逃生门"模式并完成到仓域试点。接入域（`ShipmentIngestionService`、`ShipmentRoutingService`、`OutboxDispatcher`）是 `ON DUPLICATE KEY UPDATE`/方言 SQL 最密集的上下文，用来验证逃生门规则在高方言占比下的覆盖率与可读性。

## 范围

1. **实体/仓储**：`waybill`、`parcel`、`ingestion_batch`、`ingestion_record`、`upstream_partner`、`station_service_area`、`outbox_event`、`callback_attempt`（按 ADR 手册：`<context>.persistence` 包、按 id 引用、`@Version`、DB 管理列只读）。
2. **服务迁移**：三个服务的实体 CRUD 改走仓储；upsert、`INSERT…SELECT`、报表类保留 `JdbcTemplate` 并注释逃生门原因（预计本域逃生门占比高，如实记录清单）。
3. **Testcontainers 评估**：评估库级 Java 测试（Testcontainers MySQL）引入成本与收益，决定是否纳入并记录理由（纳入则先在接入域仓储试用）。
4. **行为不变式**：`scripts/mysql-e2e-test.sh` 与 `scripts/arrival-batch-e2e.sh` 迁移前后同样全绿；接入契约、路由算法、outbox 机制零变化。

## 非目标

- 不改接入 API 契约、路由规则、outbox 重试/租约机制。
- 不动 operations 核心与 common 身份/投递域（T03/T04）。

## 依赖

- T01 ADR、到仓域参考实现、V13 schema。

## 迁移与兼容

- 无 schema 变化、无 Flyway；`ddl-auto=none` 保持不变。

## 风险

- `parcel` 实体列多且被多域共享 → 本迭代只映射读取与接入侧写入所需字段语义，跨域写路径（如派送状态机）仍走原 JDBC，T03/T04 统一收编；在总结中显式记录该临时双轨。
- `ON DUPLICATE KEY UPDATE` 的"读改写"语义 → 逃生门保留原 SQL，不强行 JPA 化。

## 测试与 DoD

- `mvn` 全量单测通过；两个真库 E2E 全绿（行为不变式）。
- 执行总结：迁移类清单、逃生门清单、Testcontainers 结论、T03 输入；按需更新 ADR 与 `AGENTS.md`。
