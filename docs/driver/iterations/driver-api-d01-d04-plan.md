# Driver API D01–D03 数据库闭环计划

> 状态：`REVIEWED`（2026-07-21）。已确认 Driver App 的正式契约是 `/auth/**` 与 `/delivery/**`，不迁移到 `/driver/v1/**`。

## 执行原则

保持 Android 已使用的路径、字段和响应兼容；迭代重点是数据库闭环、本人权限、幂等、并发和自动化测试。每个切片仍须按文档评审、开发、验证、总结执行。

| 迭代 | 当前基础 | 必须交付 | 验收 Gate | 状态 |
|---|---|---|---|---|
| D01 本人任务 | `/delivery/parcels/tasks`、`/delivering` 已接 JDBC | 加固多任务日选择、本人/任务状态门禁，不改 App 契约 | 多任务日、他人任务、撤销任务、三语言 | PLANNED |
| D02 本人扫描 | `/delivery/scan/**`、LOAD Session/Event 已落库 | 加固结果分类、破损、设备幂等、提交快照和离线恢复 | 正确/错任务/未知/重复/破损；提交后不可写 | PLANNED |
| D03 派送/POD | `/delivery`、`/delivery/retry` 已落库 | 加固证据门禁、幂等、存储抽象及失败/重派闭环 | 必需证据、hash 去重、本人权限、并发补偿 | PLANNED |

退库不属于 Driver API 迭代。司机只负责把失败包裹实物带回仓库；运营通过退库接收功能将 `DELIVERY_FAILED` 和 DRIVER custody 转为 `RETURNED_TO_STATION` 和 STATION custody。

## 完成定义

每个迭代必须具备中英文契约、成功与失败状态、401/403/409、幂等与并发测试、三语言测试、真实 MySQL 证据和执行总结。不得改写已执行的 Flyway 迁移。
