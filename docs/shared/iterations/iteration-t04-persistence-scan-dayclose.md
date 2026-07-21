# T04 持久层 ORM 重构迭代（扫描交接与日终域）

> 状态：`COMPLETED`（2026-07-21 完成）；归属：共享平台/运营系统；按 [持久层 ADR](../../design/persistence-architecture.md) Wave 4 执行。已关联 [执行总结](../summaries/iteration-t04-persistence-scan-dayclose-summary.md)。

## 背景与目的

本迭代针对扫描监督、交接审批与日终关站域建立 Spring Data JPA 实体与 Repository，涵盖 `scan_session`、`scan_event`、`daily_reconciliation` 及 `driver_hold_approval` 表。

## 重构范围

1. **JPA Entity 与 Repository 建立**：
   - `com.hf.easydelivery.operations.reconciliation.persistence`:
     - `ScanSessionEntity` + `ScanSessionRepository`
     - `ScanEventEntity` + `ScanEventRepository`
   - `com.hf.easydelivery.operations.dayclose.persistence`:
     - `DailyReconciliationEntity` + `DailyReconciliationRepository`
     - `DriverHoldApprovalEntity` + `DriverHoldApprovalRepository`
2. **Service 重构与 Escape Hatch**：
   - 重构 `DayCloseOperationsService.java` 与 `DeliverySupervisionService.java`，标准主表 CRUD 改用 Spring Data JPA。
   - 对复合多表聚合查询保留 `JdbcTemplate` 并添加 `// ESCAPE-HATCH (ADR-Persistence)` 注释。

## 零行为变更与验证

- 保持 API 路径 100% 相同。
- `./run.sh test` 确保 45 项单测全绿。
