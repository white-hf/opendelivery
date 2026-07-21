# R03-C 到仓关联观察收尾执行总结

> 完成日期：2026-07-21；状态：`COMPLETED`。关闭双产品计划 O03（到仓关联收尾）。

## 已交付

- **接入契约扩展**：Canonical 接入请求新增可选 `handlingUnits`（运单级板/笼声明）与 `deliveryLatitude/deliveryLongitude`；`trackingNumbers` 仍是包裹真值，unit 清单多余/缺失不影响入库。
- **Flyway V13**（真实 MySQL V12→V13 已执行）：`parcel.upstream_unit_no`（索引 `(upstream_unit_no,current_station_id)`）与 `parcel.current_area_version_id` 反范式投影（索引 `(current_station_id,current_area_version_id)`，含存量回填）；`handling_unit_parcel` CHECK 扩展 `'AREA_PLAN'`。
- **自动区域归属**：ingestion 路由成功后按坐标自动计算 `parcel_area_assignment`（GEO_POLYGON），同事务同步投影；新增 `POST /ops/v1/parcels/area-recompute`（缺省本站未匹配包裹；显式 `parcelIds` 定向重算）。
- **到仓批次**：`POST /ops/v1/arrival-trips` 批次号可留空自动生成（`{stationCode}-{yyyyMMdd}-{seq}`，冲突递增重试），并默认生成 10 个 PALLET 单元。
- **自动映射**：创建 Handling Unit 时自动关联本站同 `upstream_unit_no` 包裹；ingestion 时本站已有同名 Unit 立即关联（两个方向均有 E2E 证据）；跨站包裹不关联。
- **区域自动填充**：`POST /ops/v1/handling-units/{id}/area-fill` 按已发布区域批量关联（`AREA_PLAN`），同一批次内一件包裹不重复挂板；跨站/未发布区域 409。
- **覆盖观察**：批次详情返回每 Unit 的 `declared/linked/scanned/exception` 四计数、`driver_count/wave_count`、parcel 明细与未关联声明清单；前端批次工作台（四计数、明细钻取、区域填充向导、未关联警示、聚合≠明细错误提示），三语言。
- **三城市 fixture 与 E2E**：`scripts/db/005_r03c_arrival_fixture_seed.sql`（每站第二名司机+两个已发布区域）、`scripts/arrival-batch-e2e.sh`（全流程脚本，自带清理，兼容共享库已有数据）。

## 验证证据

- `mvn test`：44 项 Java 测试通过（新增 `ArrivalLinkagePolicyTest` 6 项：批次号格式/序号上限/默认单元编号/标签映射真值规则/非法声明拒绝）。
- Web：25 项 Vitest、typecheck、ESLint、生产构建通过（新增 `arrivalCoverage` 聚合=明细门禁测试）。
- Playwright：新增到仓工作台 2 项用例（en/zh 渲染与自动批次号指引）通过；控制塔到仓入口用例按新文案（Create arrival batch）同步后通过。地图旅程用例失败属既有 fixture 漂移（演示数据 `promised_date=2026-07-20`，与营业日耦合），与本次改动无关，后续与营业日解耦后恢复。
- 真实 MySQL 8 `opendelivery`：V12→V13 迁移成功；`scripts/arrival-batch-e2e.sh` 三站全绿——自动批次号序号递增、每批 10 默认单元、自动区域归属、area-fill 6 件、上游标签双向自动关联、板跨司机（driver_count=2）、任务跨板（任务含 2 个 Unit）、异常=1（跨站声明）、聚合=明细、跨站读 403、未知追踪号/跨站区域 409、定向重算投影恢复；测试数据已全部自动清理。

## 口径与决策记录

- 异常计数 = 上游声明但未能关联到该 Unit 的包裹（跨站/未路由）；DAMAGED 观察随 D02 扫描分类落地后并入（`scan_event` 现无 DAMAGED 分类）。
- 区域投影列是反范式优化（性能评审意见）：`parcel_area_assignment` 保留历史，三处写入点同事务同步投影；`MapPlanningService` 既有 paa JOIN 保留为后续优化项。
- area-recompute 缺省只处理未匹配包裹（共享库安全）；全量重算用显式 `parcelIds`，故缺省路径未在共享库 E2E 覆盖。
- 无解除关联界面（后续迭代）；上游声明变更/取消的乱序处理属 I10。

## 回滚

仅新增契约字段、表列、索引与 API；下线方式为停止使用新端点。V13 列可保留无害；如需回退应用，旧版本不读取新列，行为兼容。
