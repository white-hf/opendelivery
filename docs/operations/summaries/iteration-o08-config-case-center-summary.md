# O08 基础配置与异常中心迭代执行总结

> 完成日期：2026-07-21；状态：`COMPLETED`。关联：[迭代文档](../iterations/iteration-o08-config-case-center.md)。

## 已交付

1. **Outbox 运维与死信重放**：
   - 增加 API `GET /ops/v1/outbox?status=` 与 `POST /ops/v1/outbox/{eventId}/replay`。
   - 支持分页查询 Outbox 回调事件状态（`PENDING`、`RETRY`、`DEAD_LETTER`、`ACKNOWLEDGED`），支持运营主管将死信/失败事件一键重置为 `PENDING` 并设置 `next_attempt_at = CURRENT_TIMESTAMP(3)` 触发即时重发，同时留存审计日志。
2. **异常 Case 工作流与动作日志**：
   - 增加 API `POST /ops/v1/cases/{caseId}/actions`，支持录入处理动作记录（`case_action`）并更新 Case 状态（如 `RESOLVED`、`CLOSED`）。
3. **操作审计检索**：
   - 增加 API `GET /ops/v1/audit-logs?resourceType=`，提供 `operation_audit_log` 审计记录检索。
4. **前端异常中心与运维工作台 UI**：
   - 交付 `CaseCenterWorkspace.tsx` 页面，提供 Operational Cases 列表与动作录入 Drawer、Outbox 回调事件列表与死信重发二次确认 Popconfirm、操作审计检索表。支持 `zh-CN`、`en-CA`、`fr-CA` 三语言。

## 验证证据

- **后端单元与集成测试**：`./run.sh test` 45 项测试全绿，`BUILD SUCCESS`。
- **前端工具链**：`pnpm typecheck`、`pnpm vitest run`（25 项前端测试全绿）、`pnpm lint`、`pnpm build`（Vite 生产构建成功）。

## 总结

O08 基础配置与异常中心迭代已按规范完全交付，成功建立了 0.5 MOV 试运营主数据维护、Outbox 运维与异常补偿闭环。
