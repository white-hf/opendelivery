# T05 查询层与 ORM 命令侧分层重构

> 状态：`COMPLETED`（2026-07-27）；T05-A/B/C/D 已完成，范围：Operations API 与 Driver API 查询分层，不改变业务 API 行为。

## 目标

将服务中的查询 SQL 迁移到独立 Query Repository/Read Adapter；命令写入统一使用 JPA Entity + Repository；Controller 不直接访问持久层。

## 技术方案

- 简单单表读取：Spring Data 派生查询、JPQL、Projection DTO。
- 地图、空间函数、聚合报表：`persistence.query` 下的 JdbcTemplate/native SQL，并标注 escape-hatch 原因。
- 命令侧：Entity 映射现有表，Repository 负责保存、状态修改和锁读取；实体之间按 id 引用。
- 查询响应：专用 Query DTO，禁止直接序列化 Entity。

## 迭代拆分

1. T05-A：Dispatch Wave 查询和命令分层（已完成，见执行总结）。
2. T05-B：Planning Parcel/Driver Capacity 查询 Repository（已完成，见执行总结）。
3. T05-C：Case、Handover、Day Close 查询迁移（已完成，见执行总结）。
4. T05-D：Driver API 任务和派送查询迁移（已完成，见执行总结）。

## 验收标准

- Controller 和 Application Service 不再包含 SQL 字符串。
- 每个 Query Repository 有分页、索引和执行计划说明。
- JPA 命令测试、查询集成测试、三城市 E2E 保持通过。
- 记录无法迁移的 SQL escape hatch 及原因。
