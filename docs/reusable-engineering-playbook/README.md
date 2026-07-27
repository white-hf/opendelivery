# 可复用工程研发手册

本目录不包含 OpenDelivery 的业务规则、表名、端口或产品范围，可复制到其他后端、Web 或多产品协作项目中作为工程基线。

## 使用方式

新项目启动时先复制本目录，再根据项目实际情况补充业务 PRD、系统设计、数据模型和 API 契约。项目根目录的 `AGENTS.md` 负责覆盖本手册与仓库相关的命令、权限和安全要求。

## 文档地图

1. [架构设计原则](architecture-principles.md)：分层、依赖方向、领域边界、命令/查询分离。
2. [开发原则](development-principles.md)：编码、数据访问、幂等、审计、并发和安全。
3. [测试与验证](testing-and-validation.md)：单元、集成、E2E、性能和测试数据隔离。
4. [容错与可恢复设计](resilience-and-recovery.md)：超时、重试、幂等、降级、补偿和回滚。
5. [非功能技术基线](non-functional-baseline.md)：性能、可观测性、配置、数据安全和容量。
6. [敏捷研发流程](delivery-process.md)：从问题定义到发布、复盘和持续迭代。
7. [文档规范](documentation-standard.md)：PRD、设计、迭代、测试、总结和 ADR 的职责。

## 最小落地清单

- 根目录 `AGENTS.md`。
- `docs/prd/`、`docs/design/`、`docs/iterations/`、`docs/summaries/`。
- 一个当前架构 ADR、一个测试策略和一个发布/回滚流程。
- 每个迭代有明确 DoD、验证证据和未完成项。
