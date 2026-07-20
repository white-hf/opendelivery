# MOV 必备功能解决方案

本文把运营评审中“必须新增”的能力转换为可开发、可验收的 Feature 规格。优先级以多城市、每城一个独立站点的 MOV 实际运营为准。

## 0. 地址标准化、站点路由与多城市上下文（P0）

上游提供地址和服务要求，不要求理解内部站点。新增 `shipment-routing` 逻辑模块：代码负责标准化、规则匹配、优先级、冲突和重路由门禁；`station_service_area` 只保存可配置服务范围；Waybill 只保存 `routing_status/resolved_station_id/routing_reason_code/routed_at` 当前结果。无匹配或多匹配创建 Case；人工指定写通用审计。各运营查询携带站点上下文，所有履约命令校验 Parcel、Manifest、Wave、Task、Driver 站点一致。实物到站后不得自动改站，MOV 不做站间转运。

**验收**：至少三个城市配置各自唯一站点；上游不传站点可确定性路由；无/多匹配进入异常；跨站点收货、派车、交接和回站全部拒绝；每站可独立关站。

## 1. 运营身份、权限与审计（P0）

新增运营用户、角色、用户站点权限和审计日志。MOV 角色为 `INBOUND_OPERATOR`、`DISPATCHER`、`SUPERVISOR`、`EXCEPTION_AGENT`、`INTEGRATION_OPERATOR`、`ADMIN`。登录后发放短期 Access Token 和可轮换 Refresh Token；服务端同时校验角色与站点。所有发布、改派、接受差异、重放和关站动作记录 before/after、操作者、请求 ID、时间和原因。

**验收**：用户不能读取或操作未授权站点；主管操作可按日期和对象检索；全局 `X-Ops-Api-Key` 仅保留迁移期内部兼容。

## 2. 到站与差异工作台（P0）

页面/接口提供预计到站 Manifest、实时收货统计和差异列表。扫描结果分类为 `RECEIVED/MISSING/EXTRA/WRONG_STATION/DAMAGED/UNKNOWN`。主管可执行补数等待、隔离、转站或带原因接受；关闭 Manifest 时系统再次计算差异。

**数据/API**：复用 manifest/item/case/custody，增加 Manifest 查询、差异决策和关闭接口。**验收**：少货不进入库存；重复设备事件不重复计数；每项差异能追溯决定人。

## 3. 调度与发布门禁（P0）

波次先保存为 `DRAFT`，发布是独立命令。发布事务锁定候选 Parcel，验证站点、状态、custody、任务占用和司机状态，然后生成任务。发布后调整必须撤回未开始任务或创建显式改派事件。

**验收**：并发发布不能把同一 Parcel 分给两名司机；校验失败返回逐件原因；已开始任务不能静默覆盖。

## 4. 双边交接与回站（P0）

LOAD/RETURN Scan Session 都经历 `OPEN → SUBMITTED → APPROVED/REJECTED`。司机提交扫描结果，站点主管处理差异并审批；批准事务同时更新 task item、parcel current projection 和 custody event。RETURN 批准后决定 `RESCHEDULED`、`RETURN_PENDING` 或异常调查。

**验收**：未批准不能改变 custody；领出数等于妥投 + 失败仍持有 + 已交回；所有责任转移有双方和时间。

## 5. 失败派送与异常工单（P0）

配置最小失败原因：无人接听、地址错误、拒收、无法进入、破损、客户取消、其他。规则映射为 `RETRY_NEXT_DAY`、`RETURN_STATION` 或 `MANUAL_REVIEW`。高风险原因自动创建 Operational Case，分配 station queue 和 SLA。Case 动作包括领取、备注、请求资料、决定、解决和关闭。

**验收**：失败无原因不能提交；达到重试上限不能继续由司机自行重试；超 SLA 在工作台突出显示。

## 6. 回传监控与补偿（P0）

运营页面显示待发送、重试、死信和已确认事件。重放创建新 attempt，不修改历史；同一领域事件保持稳定外部幂等 ID。Partner ACK 分为技术接收与业务接受，业务拒绝必须进入工单。

**验收**：应用重启不丢事件；死信可按权限重放；重复回调不会造成上游重复状态。

## 7. 日终关站（P0）

按站点和营业日生成 reconciliation：库存平衡、司机仍持有、开放任务、Manifest 差异、未同步 POD、开放工单和未 ACK 回调。全部为零则自动通过；否则主管逐项关联 Case 或填写结转原因后签字。

**验收**：不能删除历史关站记录；同一站点/日期只产生一份最终记录；次日工作台自动展示结转项。

## 8. Partner Adapter（P1，MOV 后）

定义 `supports/parseInbound/validate/mapOutbound` 接口，每个 Partner 独立实现协议、签名和映射。原始报文先落地，转换为 Canonical Shipment 后进入同一履约服务。简单字段/枚举采用版本化配置，复杂规则使用代码和契约测试。

首个 MOV 仅使用 `GenericCanonicalJsonAdapter`；第二个真实上游接入时再实现适配框架，避免提前构建无验证的通用平台。
