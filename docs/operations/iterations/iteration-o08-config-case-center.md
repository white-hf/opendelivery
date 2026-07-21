# O08 基础配置与异常中心迭代

> 状态：`COMPLETED`（2026-07-21 完成）；归属：运营产品；对应双产品计划 O08。已关联 [执行总结](../summaries/iteration-o08-config-case-center-summary.md)。

## 可运营结果

试运营主数据（司机、站点）可维护；异常 Case 有 owner 与 SLA，处理全程留痕；上游回传可查询、死信可重放；任何操作可审计检索。

## 范围

1. **主数据**：司机管理页（启停、所属站、默认容量——与 R04 默认运力口径一致）、站点与服务范围维护入口（区域域已有，补司机与站点只读/启停）。
2. **Case 工作流**：`operational_case`/`case_action` 全生命周期——创建（系统/人工）、领取、转派、动作记录、解决、关闭；owner 与 SLA 到期标识；Case 与包裹/批次/清单互钻。
3. **Outbox 运维**：`outbox_event`/`callback_attempt` 分页查询（按 Partner/站点/状态过滤）、payload 脱敏查看、死信重放（高风险动作二次确认+审计）。
4. **审计检索**：`operation_audit_log` 按资源类型/ID、操作人、时间范围只读检索；敏感字段脱敏。
5. **RBAC 校验**：配置类动作限 ADMIN/主管角色；矩阵进测试。
6. **前端**：异常中心（Case 队列+时间线+SLA 标识）、集成监控（outbox 查询/重放）、系统管理（司机/站点）、审计检索，三语言。

## 非目标

- 不改 Outbox dispatcher 的重试/租约机制（仅查询与重放入口）；不做组织层级与多站点汇总。
- 不做配置中心（Partner 密钥仍部署管理，页面只读）。

## 依赖

- V1 Case/Outbox/审计主链、I03 运营身份与角色（已交付）。

## 迁移与兼容

- Case 如补 `sla_due_at`/owner 列或 `case_action` 扩展，新增 Flyway（先取号）；只增不改。

## 风险

- 死信重放造成重复回调：重放走 dispatcher 既有幂等键；重复重放安全（测试覆盖）。
- 审计/payload 含 PII：查询结果统一脱敏函数，页面不回显完整电话/地址。

## 测试与 DoD

- Java：SLA 计算、重放幂等、RBAC 矩阵、脱敏。
- 真实 MySQL E2E：造死信→查询→重放→ACK；Case 全流程；审计检索；跨站 403；自清。
- Web：Vitest + Playwright；契约与 web-spec（4.7/4.10 刷新）中英同步。
