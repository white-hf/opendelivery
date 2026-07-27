# T06 Driver API 契约与集成测试

> 状态：`IN_PROGRESS`（2026-07-27）；当前切片：`T06-B`；T06-A 已完成。

## 目标

在 T05 查询分层基础上，固定 Driver API 的兼容契约，补齐数据库 Profile 的集成验证，并覆盖司机身份、空数据、批次生命周期和派送结果边界。

## 范围

- `/delivery/parcels/tasks`、`/delivery/parcels/delivering` 查询契约。
- `/delivery/scan/**` 扫描批次和司机归属校验。
- `/delivery`、`/delivery/retry` 成功、失败、重派和幂等路径。
- Query Repository 的映射单元测试；不改变 `/delivery/**` 路径和字段。

## 验收标准

- memory profile 回归测试保持通过。
- JDBC profile 至少覆盖查询 Repository 的字段映射和空结果。
- 跨司机请求返回未授权；异常结果返回稳定业务码。
- 所有测试和 API 契约说明同步更新中英文文档。

## 切片

1. T06-A：测试运行时稳定性与现有 Driver memory 回归（已完成）。
2. T06-B：JDBC/MySQL 查询映射测试（已补充 Query Repository 空结果映射单元测试，真实 MySQL 验证待接入）。
3. T06-C：并发与幂等保护（已完成第一阶段：扫描设备事件冲突处理、派送幂等锁定）。
4. T06-D：Driver 内部查询模型整理（不改变任何对外 API 协议）。
