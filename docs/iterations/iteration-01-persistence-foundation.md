# Iteration 01: Persistence Foundation

## 目标

将运营闭环转化为可执行 MySQL schema，并用持久化 Repository 替换司机、token、包裹、扫描批次和派送结果的生产内存路径，同时保持现有司机 App API 兼容。

## 交付范围

- 状态机、ERD/Data Dictionary、索引和约束评审基线。
- Flyway V1 schema 与开发种子数据。
- JDBC/JPA 数据访问、事务领域服务和环境化连接配置。
- 现有 auth、delivery、scan API 的持久化改造。
- 单元、Repository 集成、Controller 回归及真实 MySQL smoke test。

## 技术方法

- MySQL 8 + Flyway；schema 变更只通过版本化 migration。
- 状态、事件和 outbox 同事务；乐观锁和幂等唯一键。
- 保留 `memory` profile 作为演示回退，默认持久化 profile 使用 MySQL。
- 测试按 unit、integration、regression 分层，真实库测试使用独立测试数据并清理。

## 完成标准

- V1 migration 可在空库成功执行，二次执行无漂移。
- 现有 API 返回契约保持兼容，数据在应用重启后仍存在。
- 禁止非法状态转换、重复扫描和重复上游事件。
- 所有自动化测试通过，并保存真实 MySQL 验证证据。
- 开发、测试、部署、备份和回滚文档完成。

