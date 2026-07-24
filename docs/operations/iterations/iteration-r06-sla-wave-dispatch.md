# R06 派送规划 SLA 时效波次与双排程策略迭代

> 状态：`REVIEWED`（2026-07-23 完成审查）；归属：运营产品；承接 R04 派送规划效率迭代，引入底层 SLA 时效维度与双模式极简排程策略。

## 背景与业务痛点

在实际末端配送作业中（如 UniUni、Dragonfly），包裹到站后存在不同的履约时效要求（如 `promised_date` 承诺派送日、`SAME_DAY` / `EXPRESS` 特快件与普通件）。
系统需具备既能满足 UniUni 集中快速“全清一键排程”，又能支持 Dragonfly“按 SLA 时效分批/早班加急排程”的业界标准能力。

## 范围与核心变更

1. **SLA 时效参数扩展与过滤**：
   - 后端 `MapPlanningService.java` 及查询 API (`/ops/v1/planning/parcels`) 增加 `slaFilter` 参数：`ALL`（全量）、`TODAY_DUE`（今日到期/加急）、`STANDARD`（常规件）。
   - 过滤条件绑定 `p.promised_date` 与 `w.service_code`。
2. **地图高亮与视觉防错**：
   - `PlanningMap.tsx` 支持在地图点位上标注特快件（亮紫红与⚡图标），Hover 气泡提示 `⚡ 特快件 (承诺: YYYY-MM-DD)`。
3. **波次新建体验优化**：
   - 创建波次后自动 `refetch()` 刷新下拉列表，防止显示原始数据库自增 ID（如 `990044`），秒级回显格式化 Label：`🌊 20260723-WAVE-01 (草稿)`。

## 规则与兼容性

- 默认 `slaFilter=ALL`，全清排程行为与现有逻辑完全兼容。
- 依赖基础架构：`parcel.promised_date` 与 `waybill.service_code`。

## 测试与 Verification

- 单元测试：`MapPlanningPolicyTest` 追加 SLA 过滤校验。
- 系统测试：真实连库启动与前端交互验证。
