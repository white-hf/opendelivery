# 持久层架构决策（ADR）：Spring Data JPA + 文档化逃生门

> 状态：CURRENT（T01 起生效）；适用范围：全部后端模块；关联迭代：[T01 持久层 ORM 重构](../shared/iterations/iteration-t01-persistence-orm.md)。

## 决策

数据访问分两层：

1. **命令侧 = Spring Data JPA**。实体的创建、读取、状态推进、删除通过 Spring Data Repository；不写 SQL 字符串。
2. **逃生门 = JdbcTemplate/native SQL**。仅用于四类场景，且必须在代码注释中说明原因：
   - 集合级 `INSERT…SELECT`（如默认单元生成、上游自动关联、area-fill）；
   - MySQL 方言 upsert（`INSERT IGNORE`、`ON DUPLICATE KEY UPDATE`）；
   - 空间函数（`ST_*`）与供应商特有语法；
   - 报表/聚合读模型（如到仓覆盖 `detail`、控制塔快照）。

禁止逐行循环执行逐条 INSERT/UPDATE 来完成集合级操作——必须用一条集合级 SQL；也禁止为了"纯 JPA"把方言 SQL 塞进 `@Query` 注解里散落各处。

## 硬性规则

- **Flyway 是唯一 schema 来源**：`spring.jpa.hibernate.ddl-auto=none`；实体只映射既有表，列名显式 `@Column`。
- **实体间不建关联导航**：一律按 id 引用（如 `HandlingUnit.tripId`），避免延迟加载、N+1 与 session 边界问题；关联完整性仍由数据库 FK 保证。
- **并发**：`version` 列映射 `@Version`（乐观锁）；需要锁定读取时用 Repository 的 `@Lock(PESSIMISTIC_WRITE)` 方法，替代 `SELECT … FOR UPDATE`。
- **数据库管理列**（`created_at`/`updated_at` 默认值与 `ON UPDATE`、生成列）映射为 `insertable=false, updatable=false`。
- **事务**：`@Transactional` 只标注在 Service 公有方法；JPA 与 JdbcTemplate 共用同一 `DataSource` 与事务管理器，逃生门 SQL 与同事务的 JPA 操作互相可见。
- **复合键**（如 `handling_unit_parcel`）用 `@IdClass` 映射；关联表实体化仅在它有自身属性（`link_source`）时。
- **无业务行为变化**：重构迭代以既有 E2E 脚本与单测全绿为行为不变式证据。

## 迁移步骤（每个上下文套用）

1. 为该上下文的表建实体（命名 `XxxEntity`，包跟随对应业务域放置，如 `<context>.<subdomain>.persistence`；T01 到仓域实体已迁归 `operations.arrival.persistence`）。
2. 建 Repository 接口；派生查询覆盖简单读取；锁读取加 `@Lock` 方法。
3. Service 改注入 Repository；实体 CRUD/状态推进改写；方言/集合级/报表 SQL 保留并注释逃生门原因。
4. 跑该上下文既有单测与 E2E 脚本，确认行为不变。
5. 在执行总结记录迁移的类、逃生门清单和遗留项。

## 为什么不是 jOOQ / MyBatis / 纯 JPA

- 纯 JPA 无法干净表达 upsert、空间函数与集合级 `INSERT…SELECT`，硬写 native 反而更乱；
- jOOQ 类型安全且方言保真，但代码生成显著增加构建复杂度，且不解决"缺实体/仓储层"的主诉；
- MyBatis 在北美团队普及度低，不利于后续招聘与协作。
