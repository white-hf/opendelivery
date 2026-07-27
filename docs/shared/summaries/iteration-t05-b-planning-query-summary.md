# T05-B Planning 查询层执行总结

## 已完成

- 新增 `PlanningQueryRepository`，承载派送地图包裹和司机容量查询。
- `MapPlanningService.shifts()`、`mapParcels()` 改为调用 Query Repository。
- 保持站点隔离、波次筛选、视口筛选、SLA 筛选、异常字段和分页上限不变。

## 验证

- Operations API `mvn package -DskipTests` 通过。
- 查询 SQL 已集中在 `persistence` 查询包；完整 Maven 测试仍受环境 Mockito/Byte Buddy attach 基线问题阻塞。

## 后续

- T05-C：Case、Handover、Day Close 查询迁移。
- 后续将 Map 返回值替换为类型化 Query DTO，并补充查询 Repository 集成测试和执行计划基线。
