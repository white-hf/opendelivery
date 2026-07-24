# 迭代规格书：Iteration O09 - 运营控制塔与 SPH 基准监控重构 (Operations Control Tower & Baseline Monitoring Refinement)

> **文档状态**：`REVIEWED`  
> **负责人**：Lead Architect  
> **日期**：2026-07-24  
> **所属产品**：Operations Domain (`docs/operations/`)  

---

## 1. 迭代目标 (Goal)

依据已批准的运营控制塔设计方案与 `docs/prd/operations-control-tower.md` 规范，对现有的 **运营 Web 首页【今日运营】控制塔 (Control Tower Dashboard)** 进行重构升级。

本次迭代的核心目标是：
1. **彻底清理无实战价值的冗余功能**：删除物理卡点倒计时等虚浮功能，保持控制塔首页 100% 契合系统 9 大标准一级导航信息架构。
2. **重构【今日运营作业流程流水线】(Today SOP Pipeline)**：直观展现从数据接入至日终关站的 7 阶段流转状态（`COMPLETED` / `IN_PROGRESS` / `BLOCKED` / `NOT_STARTED`），支持点击节点自动带入筛选条件跳转专业工作台。
3. **落实两大真实运营刚需**：
   * **到仓预报 vs. 实收数字差额**（Manifest EDI Discrepancy）：展现干线预报、PDA 实收、隐蔽少货（Missing）、错站件（Wrong Station）与破损件（Damaged），并关联工单追查。
   * **在途派送 SPH 基准值监控**（On-Road Performance SPH Baseline）：基于配送区域历史基准 SPH（Stops/Parcels Per Hour）评估司机派送效率偏差，精准标识“效率滞后”与“长时间无打卡”异常，并提供 POD 拍照缺失抽检。
4. **提供简易司机运力概览**：在首页直观展现司机出勤状态、硬容量上限 (`driver_shift.parcel_capacity`) 与占用率。

---

## 2. 迭代范围与非目标 (Scope & Non-Goals)

### 2.1 包含范围 (In-Scope)

1. **PRD 及设计文档更新**：
   * 更新 `docs/prd/operations-control-tower.md`（确保控制塔首页及 7 阶段流水线标准对齐）。
   * 更新 `docs/design/api-contracts.md`（新增/增强控制塔聚合 API，如 SPH 基准值与流水线汇总）。
2. **后端 API 交付 (`/ops/v1/control-tower/**`)**：
   * `GET /ops/v1/control-tower/summary`：提供 7 阶段 SOP 状态、KPI 核心指标及阻断性 Case 统计。
   * `GET /ops/v1/control-tower/driver-capacity`：提供当日出勤司机列表、车辆类型、容量硬上限（`parcel_capacity`）及已分配件数。
   * `GET /ops/v1/control-tower/on-road-supervision`：提供在途司机效率监控数据，包含 **实际 SPH vs. 区域基准 SPH** 偏差计算、状态评级（`NORMAL` / `LAGGING` / `STALLED`）及 **POD 照片缺失计数**。
   * `GET /ops/v1/control-tower/inbound-discrepancy`：提供干线 Manifest 预报件数、实收件数与少货/错站/破损差额汇总。
3. **前端 Operations Web 页面重构 (`TodayOperations`)**：
   * 交付高保真【今日运营作业流程流水线 (SOP Stepper Pipeline)】组件。
   * 交付基于基准 SPH 对比的在途监控数据表格，高亮效率滞后司机。
   * 交付预报 vs 实收隐蔽差额抽屉，支持直接直达【到仓接收】与【异常中心】工作台。
   * 支持多语言（`en-CA` / `fr-CA` / `zh-CN`）国际化文案。
4. **测试与 E2E 验证**：
   * 后端单测（控制塔 Service & SPH 算法计算单测）。
   * 编写 MySQL E2E 脚本 `scripts/control-tower-o09-e2e.sh` 验证数据正确性。

### 2.2 非目标 (Non-Goals)

* ❌ **不修改现有数据库 Schema/Flyway**：完全基于现有的 32 张表（`driver_shift` / `inbound_manifest` / `delivery_attempt` / `proof_of_delivery` / `operational_case` 等）进行 SQL 聚合与计算，无需新增/修改 Flyway 迁移文件。
* ❌ **不更改【派送计划】独立工作台**：派送计划地图划单与波次冰冻发布继续保持在独立的 `/planning` 页面，不在控制塔首页堆叠重度地图。
* ❌ **不做实时高频 GPS 轨迹流收集**：SPH 计算基于 `delivery_attempt` 真实打卡落库时间戳，不引入 WebSocket 实时轨迹。

---

## 3. 详细设计与 API 契约 Deltas (Detailed Design & API)

### 3.1 在途派送 SPH 基准值评估算法 (SPH Baseline Algorithm)

系统根据 `delivery_area.area_level` 或区域编码预设基准 SPH（例如：`YYZ-Downtown = 20.0 件/h`, `YYZ-Suburbs = 12.0 件/h`）。

$$\text{Actual SPH} = \frac{\text{Delivered Count} + \text{Failed Attempts}}{\text{Active Hours (Current Time - Task Started Time)}}$$

$$\text{Efficiency Variance} = \frac{\text{Actual SPH} - \text{Baseline SPH}}{\text{Baseline SPH}} \times 100\%$$

* **状态判定逻辑**：
  * **`NORMAL` (正常)**：`Efficiency Variance >= -15.0%`
  * **`LAGGING` (效率滞后)**：`Efficiency Variance < -25.0%`（前端标红警示）
  * **`STALLED` (无打卡/停滞)**：`Active Hours >= 2.0` 且 `Total Attempts == 0`（前端标黄警示）

### 3.2 控制塔聚合 API 契约定义

#### `GET /ops/v1/control-tower/summary`
* **请求头**：`X-Station-Code: YYZ-01`
* **查询参数**：`serviceDate=2026-07-24`
* **响应**：
```json
{
  "code": "SUCCESS",
  "data": {
    "stationCode": "YYZ-01",
    "serviceDate": "2026-07-24",
    "pipelineStages": [
      {"stage": "INGESTION", "status": "COMPLETED", "expectedCount": 1250, "actualCount": 1250},
      {"stage": "INBOUND", "status": "BLOCKED", "expectedCount": 1250, "actualCount": 1100, "discrepancyCount": 150},
      {"stage": "PLANNING", "status": "COMPLETED", "expectedCount": 1100, "actualCount": 1100},
      {"stage": "LOAD_HANDOVER", "status": "COMPLETED", "expectedCount": 1100, "actualCount": 850},
      {"stage": "ON_ROAD", "status": "IN_PROGRESS", "expectedCount": 850, "deliveredCount": 580, "failedCount": 12},
      {"stage": "RETURN_HANDOVER", "status": "NOT_STARTED", "expectedCount": 12, "actualCount": 0},
      {"stage": "DAY_CLOSE", "status": "NOT_STARTED", "varianceCount": 0}
    ],
    "kpiSummary": {
      "totalIngested": 1250,
      "totalInboundReceived": 1100,
      "missingInboundCount": 150,
      "dispatchedCount": 850,
      "deliveredCount": 580,
      "deliveryRatePercent": 68.2,
      "blockingCaseCount": 2
    }
  }
}
```

#### `GET /ops/v1/control-tower/on-road-supervision`
* **响应**：
```json
{
  "code": "SUCCESS",
  "data": {
    "drivers": [
      {
        "driverId": 101,
        "driverName": "张师傅",
        "areaCode": "YYZ-Downtown",
        "dispatchedCount": 120,
        "deliveredCount": 80,
        "failedCount": 2,
        "activeHours": 4.0,
        "actualSph": 20.5,
        "baselineSph": 20.0,
        "efficiencyVariancePercent": 2.5,
        "missingPodCount": 0,
        "supervisionStatus": "NORMAL"
      },
      {
        "driverId": 102,
        "driverName": "李师傅",
        "areaCode": "YYZ-Suburbs",
        "dispatchedCount": 80,
        "deliveredCount": 30,
        "failedCount": 1,
        "activeHours": 4.0,
        "actualSph": 7.5,
        "baselineSph": 12.0,
        "efficiencyVariancePercent": -37.5,
        "missingPodCount": 3,
        "supervisionStatus": "LAGGING"
      }
    ]
  }
}
```

---

## 4. DoD (Definition of Done) 与验收标准

1. **文档同步**：
   - 更新 PRD `docs/prd/operations-control-tower.md` 及 API 契约文档 `docs/design/api-contracts.md`（包含中英双语）。
2. **后端与单测**：
   - 完成 `/ops/v1/control-tower/**` 接口开发。
   - 编写 SPH 效率计算与数据聚合单元测试，覆盖率 100%。
3. **前端 Operations Web**：
   - 首页 `TodayOperations` 正确渲染 7 阶段 SOP 流水线与基于基准 SPH 的司机效率表格。
   - 补充 `en-CA` / `fr-CA` / `zh-CN` 三语言翻译 Key。
   - 执行 `pnpm typecheck && pnpm vitest run && pnpm lint && pnpm build` 绿色通过。
4. **E2E 真实数据库验证**：
   - 执行 `scripts/control-tower-o09-e2e.sh`，验证多城市（YYZ/YVR/YHZ）站点数据隔离、SPH 准确计算与隐蔽少货断言通过，自动清理测试数据。
5. **执行总结**：
   - 输出 `docs/operations/summaries/iteration-o09-control-tower-refinement-summary.md` 交付报告。

---

## 5. 中英双语同步 (Bilingual Alignment)

同步生成对应的英文 spec 文件 `docs/operations/iterations/iteration-o09-control-tower-refinement.en.md`，保证双语文档对齐。
