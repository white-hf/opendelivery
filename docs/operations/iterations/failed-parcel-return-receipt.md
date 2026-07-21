# 失败包裹退库接收迭代

> 状态：`REVIEWED`（2026-07-21）。产品决定：司机负责把实物带回仓库，运营人员负责确认退库；司机端不创建或提交退库扫描会话。

## 问题与目标

派送失败后，`/delivery` 将包裹置为 `DELIVERY_FAILED`，实物及 custody 仍属于司机。系统必须给运营人员一个按站点隔离的待退库清单，并在实际收到包裹后完成账实交接。

## 业务流程

1. 司机通过现有 `/delivery` 上报失败；可继续使用 `/delivery/retry` 发起同一任务的再次派送。
2. 确认不再当场重派时，司机线下携带包裹回仓。
3. 运营在“派送监控/退库接收”按追踪号定位包裹，核对司机和失败时间。
4. 运营填写退库原因并确认实物已收到。
5. 系统在一个事务内执行 `DELIVERY_FAILED → RETURNED_TO_STATION`、custody `DRIVER → STATION`、任务明细 `FAILED → RETURNED`，并写状态事件、custody 事件、审计日志和上游 outbox。
6. 后续重派或退上游是独立运营决定，不在接收动作中自动执行。

## API 与验收

- `GET /ops/v1/failed-returns?serviceDate=YYYY-MM-DD`：仅返回当前站点、司机持有且状态为失败的包裹。
- `POST /ops/v1/failed-returns/{parcelId}/receive`：输入 `reasonCode`、`note`；成功返回退库状态和 custody。
- 重复接收返回当前结果且标记 `duplicate=true`；跨站、非失败或非司机 custody 请求必须拒绝。
- INBOUND、SUPERVISOR、ADMIN 可操作；所有写操作必须可审计。

## 非目标

不新增 `/driver/v1/**`，不让司机修改退库状态，不在退库时自动创建新任务，也不修改已执行的 Flyway 迁移。
