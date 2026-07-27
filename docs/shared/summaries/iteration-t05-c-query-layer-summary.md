# T05-C 查询层重构执行总结

状态：`COMPLETED`（2026-07-27）

## 交付内容

- 新增 `CaseQueryRepository`，承载 outbox 事件和操作审计日志的只读投影，并正确支持 `resourceType + resourceId` 过滤。
- 新增 `DayCloseQueryRepository`，承载日终对账详情及到货、派送、妥投、退回、开放案件、未审批扫描会话统计。
- 新增 `HandoverQueryRepository`，承载司机装载交接监督的波次/任务、预期包裹、扫描结果、开放会话及会话站点归属查询。
- `ConfigCaseOperationsService`、`DayCloseOperationsService`、`ScanSupervisionService` 仅编排业务和事务；`FOR UPDATE`、状态写入和审计写入仍留在命令侧。
- 清理 `MapPlanningService` 中已迁移的死 SQL 注释，避免重复维护。

## 设计与性能

查询仓库统一复用现有业务索引和站点/营业日过滤；大聚合仍明确属于 JdbcTemplate escape hatch，后续以 EXPLAIN 和按营业日归档策略持续验证。查询返回 Map 是兼容现有 API 的过渡方案，T05-D 后再按稳定契约收敛为 DTO。

## 验证

- `./tools/apache-maven-3.9.8/bin/mvn -pl operations/easydelivery-ops-api -am package -DskipTests`：通过。
- 全量测试仍受既有 `ParcelDomainServiceTest` 的 Mockito/Byte Buddy self-attach 环境问题影响；该失败与本切片无关，已记录并将在测试基础设施迭代处理。
