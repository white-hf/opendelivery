# 工程与架构原则（新成员必读）

本文是 OpenDelivery 及后续同类项目的通用工程入口。新成员先阅读本文，再按项目的 `AGENTS.md`、产品文档和领域文档深入。

## 1. 先理解业务，再写代码

先确认用户、业务闭环、状态机、数据来源、权限边界和验收标准。产品行为变更必须先更新 PRD/系统设计，再创建 `REVIEWED` 迭代文档。

## 2. 分层与依赖方向

```text
Controller → Application Service → Domain → Persistence
                         ↘ Query Service → Query Repository/Read Adapter
```

- Controller 只处理 HTTP、鉴权上下文和响应封装。
- Application Service 编排事务和用例。
- Domain 保存状态机、策略和不变量，不依赖 SQL。
- Command Persistence 使用 JPA Entity + Repository。
- Query Persistence 使用 Projection DTO；复杂查询 SQL 只能位于 Query Repository/Read Adapter。

## 3. 数据访问原则

- Flyway 是唯一 schema 来源，禁止 Hibernate 自动建表。
- 实体通过 id 引用其他实体，避免隐式懒加载和 N+1。
- `JdbcTemplate/native SQL` 只作为明确记录原因的 escape hatch：空间函数、集合级写入、方言 upsert、聚合/报表查询。
- 每个大查询必须有索引、分页、数据量和执行计划说明。

## 4. 交付质量门禁

每个迭代都必须包含：中英文文档、单元测试、接口/集成测试、必要的真库 E2E、执行总结和回滚说明。新功能默认要求幂等、审计、权限校验、站点隔离、乐观锁和多语言。

## 5. 复用方式

项目特有的端口、表名、状态和业务流程放在项目 PRD/设计文档中；以上分层、数据访问、测试和交付规则可直接复用到新项目。

详细规范：[持久层 ADR](../design/persistence-architecture.md)，执行计划：[T05 查询层重构](iterations/iteration-t05-query-layer-refactoring.md)。
