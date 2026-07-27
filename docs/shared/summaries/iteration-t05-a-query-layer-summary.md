# T05-A 查询层重构执行总结

## 已完成

- 新增 `DispatchWaveQueryRepository`，承载波次列表和波次详情的跨表投影查询。
- `MapPlanningService.waveSummary()` 改为调用查询 Repository；`DispatchOperationsService.waves()` 改为调用查询 Repository。
- 波次命令继续使用 `DispatchWaveEntity` + `DispatchWaveRepository`。
- 查询 SQL 不再新增在上述业务 Service 中；查询层返回页面投影 Map，后续切片将收敛为 Query DTO。

## 验证

- Operations API `mvn package -DskipTests` 通过。
- 完整 Maven 测试受当前环境 Mockito/Byte Buddy attach 失败阻塞，与本切片无关。

## 遗留

- T05-B 继续迁移 Planning Parcel/Driver Capacity 查询，并将 Map 返回值替换为类型化 DTO。
