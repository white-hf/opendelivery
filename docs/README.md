# OpenDelivery 文档索引

每份核心文档均提供中文主版本和 `.en.md` 英文版本。

## 按产品进入

- [司机 App 与 Driver API](driver/README.md)
- [Operations Web 与 Operations API](operations/README.md)
- [共享平台、数据库与联合 E2E](shared/README.md)
- [双产品文档与开发评审流程](document-governance.md)

## 历史与共享规格

- [产品与运营一体化规格](prd/product-and-operations-specification.md)：业务运作、角色、MOV 闭环和验收。
- [MOV 必备功能解决方案](prd/mandatory-capability-solutions.md)：把运营缺口转换为可开发 Feature。
- [运营 Web 产品与交互规格](prd/operations-web-specification.md)：信息架构、页面、权限和页面状态。
- [顶层系统设计](design/last-mile-system-design.md)：模块边界、依赖、流程、时序、安全与可靠性。
- [运营 Web 前端技术设计](design/operations-web-technical-design.md)：技术栈、工程结构、状态、安全、测试与部署。
- [数据模型与字段字典](design/data-model.md)：24 张当前表、全部字段、查询与容量治理。
- [完整 API 契约](design/api-contracts.md)：全部当前 API 与 MOV 规划 API。
- [产品版本路线图](roadmap/version-roadmap.md)：`0.2` 到 `2.0` 及发布门禁。
- [MOV 迭代执行计划](iterations/iteration-02-to-08-mov-plan.md)：I02–I08 一周纵向迭代。
- [版本迭代主执行计划](iterations/master-execution-plan.md)：I02–I15日历、任务、依赖、测试和版本Gate。

## 辅助材料

- 早期背景：`prd/last-mile-driver-platform.md`、`prd/operations-closure-review.md`
- 状态机：`design/state-machines-and-operations.md`
- 已完成迭代：`iterations/iteration-00-domain-baseline.md`、`iterations/iteration-01-persistence-foundation.md`
- 总结与证据：`summaries/iteration-00-summary.md`、`summaries/iteration-01-summary.md`、`summaries/test-report.md`
- 运行治理：`developer-workflow.md`、`testing-strategy.md`、`release-process.md`、`runbooks/`

产品决策先进入对应产品中心；跨产品事实进入共享设计。所有开发强制执行“文档落地 → Review → 开发 → 测试 → Summary”，中英文必须在同一变更中同步。
