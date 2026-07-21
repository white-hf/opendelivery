# R03-C 到仓关联观察收尾迭代（v2）

> 状态：`REVIEWED`（2026-07-21 评审通过）；归属：运营产品；关闭双产品计划中 O03（到仓关联收尾）。v2 按站点实际作业流修订：到仓批次成为运营主轴，区域自动填充成为板/笼关联的主力通道。

## 作业流定位

站点实际时序：上游推送订单（可多次）→ 系统路由与区域归属计算 → 站点规划到仓批次与板/笼装载（按区域自动关联，地图核对）→ 上游仓库按规划分拣打包、卡车到站 → 运营确认到库 → 司机扫描分拣。派送规划与到仓规划并行（效率改造属 R04）。本迭代负责其中"批次/板笼/包裹关联与观察"。

## 范围

1. **到仓批次**：`arrival_trip` 即批次，`external_trip_no` 即批次号；创建时缺省自动生成（`{stationCode}-{yyyyMMdd}-{两位序号}`），可人工覆盖；批次号是当次运作统一标识，派送计划编码复用它（R04 落地）。
2. **默认板/笼**：创建批次时自动生成 10 个 PALLET 单元（编号 `{批次号}-U01..U10`），可继续增补，避免运营逐个新增。
3. **自动区域归属**：ingestion 路由成功后自动计算 `parcel_area_assignment`（匹配顺序沿用 PRD：人工覆盖 > 有效 Polygon/MultiPolygon > 邮编/城市回退 > 未分区异常）；新增批量重算端点 `POST /ops/v1/parcels/area-recompute`（缺省本站当日未匹配包裹）。
4. **区域自动填充**：`POST /ops/v1/handling-units/{unitId}/area-fill {areaVersionIds[], reason?}` 把所选区域当前包裹批量关联到 Unit，`link_source='AREA_PLAN'`；跨站或未发布区域 409。地图点选与手输追踪号（`OPERATOR`）保留为偶尔补充。
5. **上游 Unit 标识**：接入请求可选 `handlingUnits`（`externalUnitNo` 必填、`unitType` 可选、`trackingNumbers` 该板件清单）；`parcel.upstream_unit_no`（V13）落事实；创建 Unit 或 ingestion 时双向自动关联本站同标签包裹（`link_source='UPSTREAM'`）。
6. **覆盖观察**：Trip 详情每个 Unit 返回 `declared/linked/scanned/exception_piece_count`、`driver_count`、`wave_count` 与 parcel 明细（追踪号/任务/司机/件状态/`link_source`）及未关联声明清单；**聚合恒等于明细汇总**。异常口径：上游声明但未能关联到该 Unit 的包裹（含跨站与未路由）；DAMAGED 观察随 D02 扫描分类落地后并入异常计数。
7. **三城市 fixture**：YHZ/YYZ/YVR 每站 2+ 司机、已发布区域与区域归属包裹；批次默认 10 板；区域填充形成"一板跨两司机任务"（板跨司机）与"一任务跨两板"（任务跨板）；脚本校验聚合=明细、跨站隔离、未知追踪号拒绝。
8. **前端工作台**：以批次为中心按作业流布局（建批次 → 默认单元 → 选区域自动填充 → 地图核对 → 确认到达），覆盖计数与明细钻取，三语言。

## 非目标

- 不替司机扫描；到仓事实不写成包裹状态或 custody 变化（D02/O05 职责）。
- 不由上游声明自动创建 Trip/Unit；实物登记仍是运营动作。
- 不处理上游 unit 声明的变更/取消乱序（I10 范围）；不提供解除关联界面（后续迭代）。
- 不做派送规划效率改造（计划编码自动生成、默认运力、司机默认区域分配属 R04）。

## 依赖

- 现有 V12 到仓表、V8 区域模型（`parcel_area_assignment`、`delivery_area_version`）、`driver_task_item`、`scan_event` 主链。
- `trackingNumbers` 是包裹真值；unit 声明与其不一致只影响关联与异常计数，不影响入库。

## 迁移与兼容（V13）

- `parcel` 新增 `upstream_unit_no VARCHAR(100) NULL`（索引 `(upstream_unit_no,current_station_id)`，兼顾全局标签统计与站级查找）与 `current_area_version_id BIGINT NULL`（当前区域反范式投影，索引 `(current_station_id,current_area_version_id)`；`parcel_area_assignment` 仍为归属历史，迁移含存量回填）。
- `handling_unit_parcel` 的 `ck_unit_parcel_source` CHECK 扩展加入 `'AREA_PLAN'`。
- 只新增 V13，不回改历史迁移；`handlingUnits` 可选，旧报文行为不变；`external_trip_no` 缺省自动生成不影响已有手工编号调用方。

## 风险

- 区域未发布或归属未计算 → area-fill 结果为空时明确提示，运营走手工补充；未分区异常包裹进入既有异常通道。
- 上游标签或区域归属与实物不符 → 关联只是观察，运营实物核对为准，不动库存与责任。
- 同名标签跨站重复 → 关联强制本站范围，fixture 与 E2E 覆盖该场景。

## 测试与 DoD

- Java：ingestion 自动区域归属、批量重算、area-fill 幂等与跨站拒绝、`handlingUnits` 携带/不携带、双向自动关联、重复 event 幂等。
- 真实 MySQL：V12→V13 升级、fixture 装载、聚合=明细、三站隔离。
- Web：Vitest 计数渲染与明细钻取；Playwright 批次工作台回归。
- 契约/数据模型/PRD 中英文档同步；执行总结记录命令与证据。
