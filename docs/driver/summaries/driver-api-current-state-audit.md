# Driver API 代码与数据库现状审查

## 结论

司机后台不是空白实现。原 App 契约覆盖认证、任务、装车扫描、派送和失败回仓，MySQL V1–V7 已落地核心表，`JdbcDriverRepository`、`JdbcTokenStore`、`JdbcDeliveryOperations` 和 `FailureReturnService` 已连接真实数据层。但当前状态是“主链存在、契约分裂、门禁与测试不足”，D01–D04 必须以加固和收敛为主，不能把历史完成总结直接当作当前 DoD。

## 已实现能力

| 能力 | 代码/API | 数据库状态 | 判断 |
|---|---|---|---|
| 认证 | `/auth/register/login/refresh/logout/locale` | `driver/auth_session`，Token 仅存 hash | 基本可用；公开注册策略待确认 |
| 本人任务 | `/delivery/parcels/tasks`、`/delivering` | `driver_task/task_item` JDBC 查询 | 已有包裹列表；缺任务级 V1 契约 |
| LOAD Session | 创建、恢复、扫码、报告、提交 | `scan_session/scan_event` | 主链存在，需修复结果分类和快照语义 |
| 交接 | Driver 只可提交；Operations 批准 | Parcel/Task/Custody/Status/Outbox 事务 | 已有闭环，归入 O05 联合验收 |
| 派送/POD | 旧 multipart `/delivery`；新 Attempt `/driver/v1/.../attempts` | `delivery_attempt/proof_of_delivery` | 两套契约并存，需统一 V1 |
| 失败/重试 | 原因规则、次数、证据标志、地址 Case | V7 规则及 Attempt 字段 | 主逻辑已实现，POD 实体证据未原子绑定 |
| 回仓 | RETURN Session、本人扫描/提交 | Session/Event；批准后 custody 回站 | 主链存在，缺完整分类、进度与并发测试 |
| 多语言 | 三语言消息、司机 locale | `driver.preferred_locale` | 已实现基础契约 |

## 必须开发或修复

1. **契约收敛**：把旧 `/delivery/**` 的 App 兼容层与规范 `/driver/v1/**` 分开；新增任务、LOAD、派送/POD V1 契约和弃用策略，禁止字段继续使用 `driver_id` 作为授权来源。
2. **D01 任务模型**：提供任务摘要、任务明细、应扫清单、任务状态和版本；当前只返回扁平包裹，且“创建扫描批次”隐式选择最近任务，在多任务日会选错。
3. **D02 扫描事实**：数据库枚举尚无 `DAMAGED`；LOAD 扫描当前提前把 Task Item 改为 `LOADED`，需要明确其仅为扫描投影；未知事件应先做 `deviceEventId` 幂等检查；重复扫描应返回并保存稳定分类；提交要保存不可变统计快照并禁止提交后继续写入。
4. **D03 派送证据**：规范 Attempt API 已校验失败原因，但照片仅是 boolean 声明；旧 multipart API 可在没有必需 POD 时先把包裹标为妥投。需要受控上传、类型/大小/hash 校验、Attempt 与 POD 原子门禁、对象存储接口及补偿策略。
5. **D04 回仓**：补 `UNKNOWN/DUPLICATE/WRONG_TASK` 一致语义、漏扫计算、提交快照、重复提交、运营拒绝后重开和设备离线乱序处理。
6. **并发与审计**：扫描、Attempt、Session 提交和审批增加行锁/版本冲突测试；Driver Event 审计需记录 driver、device、idempotency key，敏感地址/POD 不进入日志。
7. **自动测试**：当前 Java 自动化仅有 4 个 Driver API 应用测试，历史 I05/I06 证据主要来自脚本执行总结。需要 repository/service/controller 单测、真实 MySQL 集成测试、模拟 App 契约测试和三城市联合 E2E。

## 可复用但需重新验收

V1–V7 表结构、活动任务唯一约束、Token hash/刷新轮换、本人权限检查、扫码/Attempt 幂等键、custody/status/outbox 写入和失败回仓服务均可复用。重新验收不代表推倒重写；只有通过新 D01–D04 DoD 的能力才标记完成。
