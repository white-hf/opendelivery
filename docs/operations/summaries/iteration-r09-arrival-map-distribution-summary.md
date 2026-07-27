# R09 到仓地图聚合与笼板包裹分布修复总结

> 状态：`COMPLETED`（2026-07-27）；对应计划：[R09 到仓地图分布修复](../iterations/iteration-r09-arrival-map-distribution.md)。

## 修复内容

- 到仓工作台维护独立的地图区域展开状态，复用订单准备页的区域聚合数字和点位展开行为。
- 重新构建并重启 Operations API，确认 `/arrival-trips/42` 返回 400 条包裹且包含 `longitude`、`latitude`、`area_code`、`area_id`。
- 到仓详情补充返回 `driver_id` 与 `stop_sequence`，已排线包裹现在可复用订单准备页的顺序水滴标记。
- 全车次视图统计整车次包裹；选中笼板后只统计该笼板包裹。
- 点击区域聚合数字或区域面可展开该区域的包裹点位，再次点击可取消展开。
- 切换车次或笼板时清理过期区域和包裹选择，避免地图显示旧数据。
- 过滤缺失经纬度的包裹，防止无效坐标影响地图渲染。

## 验证

- TypeScript 类型检查通过。
- Vite 生产构建通过。
- Arrival coverage 相关 6 个单元测试通过。
- 由于当前仓库已有基线失败，完整 Vitest/lint 未标记为全绿：已有失败包括 `dispatch-reassign` 导航断言、三语言 key 集合不一致、API 站点请求头断言，以及全局既有 lint 错误；本修复未修改这些无关文件。
- 后端 API 与 Flyway schema 未改变。
- 根因是运行中的旧 JAR 未包含已存在的坐标/区域查询字段；数据库关联和数据本身完整。
