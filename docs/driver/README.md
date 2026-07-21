# 司机产品文档中心

本目录是司机 App 与 Driver API 团队的唯一当前入口。司机产品负责本人认证、本人任务、装车扫描、派送/POD、失败和回仓；不包含运营规划、监督和审批 UI。

## 当前文档

| 类型 | 当前文档 |
|---|---|
| 产品 | [司机平台 PRD](../prd/last-mile-driver-platform.md) |
| 当前实现审查 | [Driver API 代码与数据库现状](summaries/driver-api-current-state-audit.md) |
| 当前迭代 | [D01–D04 数据库闭环计划](iterations/driver-api-d01-d04-plan.md) |
| API | [全系统 API 契约：司机章节](../design/api-contracts.md#94-调度与交接i05current) |
| 架构/数据 | [系统设计](../design/last-mile-system-design.md)、[数据模型](../design/data-model.md)、[状态机](../design/state-machines-and-operations.md) |
| App 契约 | [司机 App 多语言契约](../design/driver-app-localization-contract.md) |
| 历史交付 | [I05 装车](../summaries/iteration-05-summary.md)、[I06 失败回仓](../summaries/iteration-06-summary.md) |

## 团队规则

新增司机文档分别进入 `prd/`、`design/`、`iterations/`、`summaries/`、`testing/` 或 `runbooks/`。功能开发必须遵循“产品/契约更新 → 迭代文档评审 → 开发 → 自动测试 → 执行总结”。运营端不得调用 Driver API 代司机执行命令。
