# R10 波次与到仓车次持久化关联

> 状态：`COMPLETED`（2026-07-27）；类型：运营派送计划缺陷修复。

## 目标

步骤 1 选择 Trip No 后，波次必须持久化关联该到仓车次；未选择时才自动创建车次。切换页面后，步骤 2 仍显示同一批次的板笼。

## 实现

- V22 增加 `dispatch_wave.arrival_trip_id` 外键和索引。
- 创建波次校验车次属于当前站点和营业日。
- 前端所有创建波次入口传递选中的 Trip No。
- 波次详情按持久化关联恢复到仓批次。

## 验证

- Flyway V22 在本地数据库成功执行。
- Operations API Maven 构建通过。
- Operations Web typecheck 与到仓覆盖测试通过。
