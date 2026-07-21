# T01 持久层 ORM 重构迭代（到仓域试点）

> 状态：`REVIEWED`（2026-07-21，用户授权直接规划落地）；归属：共享平台（双产品后端）；技术重构，无业务行为变化。

## 背景

后端 19 个类、57 处直接使用 `JdbcTemplate` 拼接 SQL，没有实体/仓储层：SQL 字符串不可类型检查，实体关系隐式，事务与锁语义散落在各 Service。目标按北美主流 Spring 栈建立持久层标准，并给后续上下文提供可复制的迁移模式。

## 框架选型

| 候选 | 优势 | 对本代码库的问题 |
|---|---|---|
| Spring Data JPA（Hibernate） | 北美企业标准；仓储抽象；`@Version` 乐观锁、`@Lock` 悲观锁；实体生命周期 | MySQL 方言 upsert（`INSERT IGNORE`/`ON DUPLICATE KEY UPDATE`）、空间函数（`ST_*`）、集合级 `INSERT…SELECT` 支持弱，需本地 SQL 兜底 |
| jOOQ | 类型安全 SQL、方言保真、增量接入成本低 | 非 ORM 心智；代码生成增加构建复杂度；不解决"实体/仓储缺失"的主诉 |
| MyBatis | SQL 可控 | 北美团队普及度低 |

**决策**：命令侧（实体生命周期）采用 Spring Data JPA；集合级 `INSERT…SELECT`、upsert、空间函数与报表查询保留 `JdbcTemplate`/native SQL，作为文档化逃生门。理由：用户主诉是"没有 ORM 层"，实体/仓储正是 JPA 强项；该组合是北美企业常规架构；对现有事务/锁/幂等语义改动最小、风险最低。

## 范围

1. 引入 `spring-boot-starter-data-jpa`；`ddl-auto=none`（Flyway 是唯一 schema 来源）、`open-in-view=false`。
2. 到仓域实体与仓储：`ArrivalTrip`、`HandlingUnit`、`HandlingUnitParcel`（复合键）+ Spring Data Repository；`version` 列映射 `@Version`；数据库管理列（`created_at/updated_at`）映射为只读。
3. `PhysicalArrivalService` 重构：实体 CRUD 与状态机走 Repository（悲观锁读取、乐观锁写回）；`detail/trips` 报表查询与集合级 `INSERT…SELECT`（默认单元生成、上游自动关联、area-fill）保留 `JdbcTemplate`——作为文档化逃生门的首批实例。
4. 行为不变式：API、事务边界、审计、幂等与错误码零变化；`scripts/arrival-batch-e2e.sh` 全量回归通过；既有 44 项单测通过。
5. 迁移手册：实体命名、`@Version`/`@Lock` 用法、逃生门规则、逐上下文迁移步骤，供 T02–T04 套用（见 [持久层架构决策](../../design/persistence-architecture.md)）。

## 非目标

- 不迁移其他上下文（integration、operations 核心、common 身份与投递）。
- 不引入 Testcontainers/H2 库级 Java 测试（T02 评估）；不改 Flyway、不改 API。
- 不做读写分离或 CQRS；不把业务规则搬进实体（富血模型不在本期）。

## 批次路线图

- **T01**：到仓域试点（本迭代）。
- **T02**：接入域（`ShipmentIngestionService`、`ShipmentRoutingService`、`OutboxDispatcher`）。
- **T03**：运营核心（inbound、dispatch、planning、areas、control-tower、failed-returns）。
- **T04**：身份与 common（operator session/user、auth interceptor、token store、driver repository、delivery operations）。

## 风险

- JPA 与 `JdbcTemplate` 混用事务：两者共用同一 `DataSource` 与事务管理器，逃生门 SQL 在同一事务内可见；迁移手册显式禁止跨数据源。
- 实体映射漂移：`ddl-auto=none` + 列名显式 `@Column` + 真库 E2E 兜底。
- 延迟加载与 N+1：实体间不建关联导航，一律按 id 引用。

## 测试与 DoD

- `mvn` 全量测试通过；`DB_PASSWORD=… OPS_PASSWORD=… scripts/arrival-batch-e2e.sh` 真库回归通过（行为不变式）。
- 文档：本迭代文档、持久层 ADR、`AGENTS.md` 数据访问约定同步更新。
- 执行总结记录迁移前后对照、逃生门清单与 T02 输入。
