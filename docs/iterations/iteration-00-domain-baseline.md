# Iteration 00: Domain Baseline and Database Bootstrap

## 背景

现有演示代码没有正式业务边界和持久化模型。直接把内存集合映射成表会固化错误抽象，尤其会混淆上游接入批次、到站清单、派送波次和司机扫描批次。

## 迭代目标

建立 Last Mile 端到端业务基线、目标架构及 MySQL 数据库容器，为下一迭代的逻辑模型和迁移脚本提供已记录的输入。

## 本迭代范围

- 审阅现有模块、API、DTO、内存存储和测试。
- 定义上游推送/拉取到状态回传的闭环。
- 定义角色、批次语义、包裹状态机、异常路径和数据归属。
- 创建 `opendelivery` 数据库并验证字符集、排序规则和账号访问。

## 非范围

- 本迭代不创建未经评审的业务表。
- 不修改现有 API 行为，不切换生产数据源。
- 不假设具体上游字段、SLA、POD 策略或路线规划责任。

## 交付物

- `docs/prd/last-mile-driver-platform.md`
- `docs/design/last-mile-system-design.md`
- `scripts/db/001_create_database.sql`
- `docs/summaries/iteration-00-summary.md`

## 完成标准

- 文档明确说明运单来源、四类批次、司机任务来源和包裹状态闭环。
- 现状差距与待业务确认项可追踪。
- `opendelivery` 存在，使用 `utf8mb4`，账号可连接并可见。
- 下一迭代以数据模型评审和核心 schema 为唯一入口。

## 后续迭代建议

Iteration 01 设计 ERD、数据字典、状态转换矩阵、Canonical Shipment Contract 和 Flyway V1 migration；经确认后再实现 JPA Repository 与集成测试。

