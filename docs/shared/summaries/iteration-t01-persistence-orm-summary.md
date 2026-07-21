# T01 持久层 ORM 重构执行总结

> 完成日期：2026-07-21；状态：`COMPLETED`（到仓域试点）。关联：[迭代文档](../iterations/iteration-t01-persistence-orm.md)、[持久层 ADR](../../design/persistence-architecture.md)。

## 已交付

- **JPA 基础设施**：`spring-boot-starter-data-jpa` 引入；`spring.jpa.hibernate.ddl-auto=none`（Flyway 唯一 schema 来源）、`open-in-view=false`；memory profile 增补排除 `HibernateJpaAutoConfiguration`/`JpaRepositoriesAutoConfiguration`（内存模式与既有单测不受影响）。
- **持久层 ADR**：`docs/design/persistence-architecture.md`（中英）——命令侧 JPA、四类逃生门（集合级 `INSERT…SELECT`、方言 upsert、空间函数、报表读模型）、实体按 id 引用不建导航、`@Version`/`@Lock` 规则、逐上下文迁移手册。
- **到仓域迁移（参考实现）**：`operations/persistence/` 新增 `ArrivalTripEntity`/`HandlingUnitEntity`（`@Version`、DB 管理列只读）与 `ArrivalTripRepository`/`HandlingUnitRepository`（`@Lock(PESSIMISTIC_WRITE)` 锁读）；`PhysicalArrivalService` 全部实体 CRUD 与状态机改走仓储（悲观锁读、乐观锁写）。
- **约定落地**：`AGENTS.md` 新增 Data Access & Persistence 一节。
- **行为不变式**：API、事务边界、审计、幂等、错误码零变化，由同一 E2E 脚本证明。

## 逃生门清单（保留 JdbcTemplate，均有注释）

`trips()`/`detail()` 覆盖聚合报表；批次号当日序号 `DATE()` 查询；默认单元 `INSERT IGNORE`；手输追踪号 `INSERT IGNORE`；上游标签自动关联 `INSERT…SELECT`；area-fill `INSERT…SELECT`；区域校验 COUNT；审计写入（`operation_audit_log` 属运营核心上下文，随 T03 迁移）。`HandlingUnitParcel` 关联表因只有集合级操作未实体化，后续有需要再补。

## 验证证据

- `mvn clean package`：44 项 Java 测试全绿（含 memory profile 的 `ApplicationApiTest`），BUILD SUCCESS。
- 真实 MySQL `opendelivery`：`DB_PASSWORD=… OPS_PASSWORD=… scripts/arrival-batch-e2e.sh` 与迁移前**逐字节相同**地三站全绿（行为不变式）。
- Playwright 到仓 3 项（新增 en/zh 工作台 + 控制塔入口）通过。

## 决策记录

- `@Version` 正式启用：JDBC 时代 `version` 列未被用于并发控制，JPA 化后按数据模型本意生效；并发状态推进序列化于悲观锁，写回带乐观校验。
- 实体间不建关联导航（`HandlingUnit.tripId` 按 id 引用），杜绝延迟加载/N+1。
- 状态字段保持 String 映射（DB CHECK 为准），避免映射漂移。

## T02 输入

下一上下文：接入域（`ShipmentIngestionService`、`ShipmentRoutingService`、`OutboxDispatcher`——含 `ON DUPLICATE KEY UPDATE` 密集区，验证逃生门规则覆盖率）；评估 Testcontainers 库级 Java 测试引入；按 ADR 手册执行。

## 回滚

纯重构无 schema/API 变化；回退即还原 `PhysicalArrivalService` 与依赖装配，JPA 依赖与配置可保留无害。
