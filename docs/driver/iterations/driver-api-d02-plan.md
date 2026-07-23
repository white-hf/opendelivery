# Driver API D02 迭代设计：司机装车扫码与设备幂等加固

> 状态：`REVIEWED`（2026-07-23）。已确认 Driver App 契约保持 `/delivery/scan/**` 路径及 JSON 结构兼容。

## 1. 范围与目标

D02 迭代专注于司机装车扫码（LOAD Scan Session / Event）过程的分类校验、设备重试强幂等、提交后只读锁定及断网恢复能力。

### 包含范围 (In Scope)
1. **扫码结果四大精准分类 (Scan Result Classification)**：
   * `EXPECTED`（正常应扫件）：属于该司机当前任务且处于 `ASSIGNED` 状态，成功装车。
   * `WRONG_TASK`（错任务件）：系统中存在该包裹，但分配给了其他司机/其他任务。
   * `UNKNOWN`（未知包裹）：系统查无此 `tracking_no`。
   * `DUPLICATE`（重复扫码）：在此批次中已经被成功扫描过，不重复计数。
2. **设备级重试强幂等 (Device Event Idempotency)**：
   * 依靠 `device_event_id` 全局唯一识别。多次重复推送同一个事件，服务端返回完全相同的首次处理结果，不重复计件。
3. **批次提交后只读锁定 (Post-Submission Read-Only Snapshot)**：
   * 批次状态更新为 `SUBMITTED` 后即锁定。后续扫码请求将被拒绝（返回 `SCAN.BATCH.LOCKED`）。
4. **防越权门禁**：
   * 校验批次所属 `driver_id` 与 JWT Token 中的 `driverId`，防越权。

### 非目标 (Out of Scope)
* 破损标记（`DAMAGED` 异样件记录归属于 Operations 运营端入站/站内盘点功能，不在司机装车扫码处理）。
* 不修改 Android App 现有 API 契约与路径。

## 2. API 契约与规则

* `POST /delivery/scan/batch`：创建扫码批次。
* `POST /delivery/ext/scan`：逐件扫码校验与记录（带 `device_event_id`）。
* `POST /delivery/scan/batch/report`：批次报告生成与统计。
* `PUT /delivery/ext/scan/batch/{scanBatchId}`：提交批次审核/锁定封盘。
* `GET /delivery/to-be-picked-up/brief/{driverId}`：查询未装车摘要。

## 3. DoD (Definition of Done)

* [x] D02 迭代文档确认 (`REVIEWED`)
* [x] 源码实现、设备幂等与封盘锁定加固
* [x] 扫码分类、设备幂等与锁定单元/集成测试通过
* [x] `./run.sh test` 全量验证通过
* [x] 编写 D02 执行总结 (`COMPLETE`)
