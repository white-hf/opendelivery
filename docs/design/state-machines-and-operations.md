# 状态机与运营控制设计

## 1. 设计原则

状态变化必须由命令触发、由服务端校验前置条件，并在同一事务内更新当前状态、追加不可变事件和写入 outbox。客户端不能直接指定任意目标状态。每个命令携带幂等键和期望版本，重复请求返回首次结果，并发冲突返回可重试错误。

## 2. 包裹生命周期

Waybill 路由先于 Parcel 入站：`PENDING → ROUTED`；无匹配进入 `UNROUTABLE`，多匹配进入 `AMBIGUOUS`，人工处理后可回到 `ROUTED`。路由服务只保存当前结果；失败创建 Case，人工指定写审计。只有 `ROUTED` 且 resolved station 有效的运单才能生成该站 Manifest。实物到站后禁止自动改站。

| 当前状态 | 命令 | 目标状态 | 运营前置条件 |
|---|---|---|---|
| `RECEIVED` | 到站确认 | `AT_STATION` | 上游数据有效，实物扫描匹配站点 |
| `AT_STATION` | 完成分拣 | `SORTED` | 地址/区域有效，无阻断异常 |
| `SORTED` | 加入可派池 | `READY_FOR_DISPATCH` | 服务日与产品规则满足 |
| `READY_FOR_DISPATCH` | 发布司机任务 | `ASSIGNED` | 唯一有效任务、司机/容量有效 |
| `ASSIGNED` | 完成交接 | `OUT_FOR_DELIVERY` | 应扫差异为零或主管接受 |
| `OUT_FOR_DELIVERY` | 妥投 | `DELIVERED` | POD 策略满足，位置/时间有效 |
| `OUT_FOR_DELIVERY` | 派送失败 | `DELIVERY_FAILED` | 原因码及必要证据完整 |
| `DELIVERY_FAILED` | 批准重约 | `RESCHEDULED` | 未超重试上限，服务日期确定 |
| `RESCHEDULED` | 再次分配 | `ASSIGNED` | 新任务已发布 |
| `DELIVERY_FAILED` | 发起退回 | `RETURN_PENDING` | 不可重试或客户要求退回 |
| `RETURN_PENDING` | 司机交回 | `RETURNED_TO_STATION` | 回站扫描并完成 custody 交接 |
| `RETURNED_TO_STATION` | 交回上游 | `RETURNED_TO_UPSTREAM` | 上游/承运人交接确认 |

`CANCELLED`、`LOST`、`DAMAGED` 和 `ADDRESS_EXCEPTION` 由受控异常命令进入。已在司机 custody 的取消件必须先拦截并交回，不能直接从系统消失。

## 3. Custody 状态

责任方类型为 `UPSTREAM`、`STATION`、`DRIVER`、`RETURN_CARRIER` 或 `UNKNOWN`。每次转移追加 `custody_event`，包含 from/to、业务原因、关联任务、操作者和时间；`parcel.current_custody_*` 是便于查询的投影。转移前必须验证当前责任方，防止双重持有。

## 4. 任务和扫描状态

任务：`DRAFT → PUBLISHED → ACCEPTING → IN_PROGRESS → CLOSED`，可在受控条件下进入 `CANCELLED`。发布后才对司机可见；交接完成才进入执行；任务关闭要求每个任务明细均为已交付、已失败并交回、已改派或已取消。

扫描会话：`OPEN → SUBMITTED → APPROVED`，有差异时可进入 `REJECTED` 后重新扫描。扫描事件分 `EXPECTED` 匹配、`EXTRA` 多扫、`DUPLICATE` 重复、`WRONG_TASK` 串任务和 `UNKNOWN` 无数据。扫描记录本身不直接改变包裹状态。

## 5. 异常和回传状态

异常工单：`OPEN → ASSIGNED → WAITING_EXTERNAL/IN_PROGRESS → RESOLVED → CLOSED`。每个开放工单必须有类型、优先级、owner、SLA 截止时间和最后动作时间；超时自动升级。

回传：`PENDING → SENDING → ACKNOWLEDGED`；暂时失败进入 `RETRY`，超过上限进入 `DEAD_LETTER`，经业务批准可 `WAIVED`。只有 `ACKNOWLEDGED` 或 `WAIVED` 才算上游闭环。

## 6. 日终平衡

站点按业务日计算：

MOV 按每个站点独立计算：

`期初库存 + 到站 + 司机交回 - 派出 - 妥投 - 退上游 = 期末库存`

数据库现有 `transfer_in/out` 字段在 MOV 固定为 0，仅作为未来兼容字段，不代表已支持站间转运。

系统同时核对开放任务、司机未交回、POD 未完成、扫描差异、异常超时和回传死信。差异生成 `daily_reconciliation` 明细；主管只能带原因结转，不能删除差异。
