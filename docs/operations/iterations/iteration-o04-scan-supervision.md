# O04 扫描监督迭代

> 状态：`COMPLETED`（2026-07-21 完成）；归属：运营产品；对应双产品计划 O04。由 Second Agent (Agent-Architect) 交付。已关联 [执行总结](../summaries/iteration-o04-scan-supervision-summary.md)。

## 可运营结果

运营按批次（波次）/任务/司机实时查看装车扫描进度与异常，不代替司机扫描、提交或改派。所有数字与 `scan_session`/`scan_event` 事实同源，跨站严格隔离。

## 范围

1. **监督聚合查询**：`GET /ops/v1/scan-supervision?serviceDate=&waveId=` 按波次→任务→司机三级返回：应扫（任务 ASSIGNED 件数）、有效（EXPECTED 扫描去重件数）、错扫（WRONG_TASK）、未知（UNKNOWN）、重复（DUPLICATE）、多扫（EXTRA）、漏扫（应扫−有效）、未提交（OPEN session 数）。
2. **Session 钻取**：`GET /ops/v1/scan-sessions?taskId=&status=` 与单 session 详情（事件列表、分类计数、提交时间、设备幂等冲突数）。
3. **异常钻取**：错扫/未知/重复事件清单，展示追踪号、实际扫描司机、正确任务提示（按 `driver_task_item` 归属判定）。
4. **口径统一**：与 R03-C 到仓单元 `scanned_piece_count` 使用同一事实口径（LOAD session + EXPECTED 去重），两处不得算出不同数字。
5. **前端**：扫描监督工作台（波次→任务→session 三级钻取、进度展示、异常标记），三语言；导航从"司机扫描"进入。

## 非目标

- 不替司机扫描、提交、关闭或改派（司机操作属 D02/D03，改派属 O05）。
- 不做审批（O05）；不改 `scan_event` 分类与 Driver API；DAMAGED 分类待 D02 落地后并入。
- 不做实时推送，页面按查询刷新。

## 依赖

- V1 扫描主链（`scan_session`/`scan_event`）、`driver_task(_item)`、R03-C 覆盖口径。
- 双产品计划允许 O04 在 D02 稳定后并行：本迭代只读现有事实，不依赖 D02 改造。

## 迁移与兼容

- 无新表、无 Flyway；仅新增只读端点；权限沿用运营角色 + 站点上下文。

## 风险

- 扫描口径随 D02 加固变化 → 口径计算集中在监督服务一处，变化只改这里。
- 大站点聚合性能 → 走 `(task_id,item_status)`、`(session_id,scanned_at)` 既有索引；`serviceDate` 必填限界。

## 测试与 DoD

- Java：聚合口径单测（各分类、漏扫公式、空批次）。
- 真实 MySQL E2E：造两个司机任务+session+事件，断言三级计数、异常清单、跨站 403；共享库安全自清。
- Web：Vitest + Playwright 渲染回归；契约与 web-spec（新增扫描监督页）中英同步。
