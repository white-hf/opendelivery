# M06：全面英文 UI 硬编码清理与国际化修复 (Full English UI Clean-up & i18n Fix)

状态：COMPLETED

## 目标与背景

在 `en-CA` (英文) 语言模式下，前端 Operations Web 部分工作台页面仍存在硬编码中文（如表格列名、操作按钮、提示框、Placeholder、抽屉标题等）。本迭代旨在补充完善 `i18n.ts` 的翻译字典，并替换各工作台组件中的硬编码中文，确保英文环境体验纯正无残留。

## 实现范围

1. **`i18n.ts` 词条扩充**：
   - 补充 `en-CA`、`fr-CA`、`zh-CN` 对应的新词条（包括区域管理、干线车次到仓、司机扫描监督、派送与改派、到货清单、异常工单、日终关站及包裹详情等）。
2. **工作台组件硬编码清理**：
   - `AreaWorkspace.tsx`：清除区域状态、责任司机、添加按钮、弹窗输入框 Placeholder 及提示等硬编码。
   - `ArrivalWorkspace.tsx`：清除车次标题、运输编辑抽屉表单 Label/Placeholder、校验 Alert、移动端操作栏及表格列等硬编码。
   - `ScanSupervisionWorkspace.tsx`：清除波次选择下拉框、状态 Tag、查看操作按钮等硬编码。
   - `DispatchWorkspace.tsx` & `DispatchReassignWorkspace.tsx`：清除选中计数、图层控制、改派筛选及改派确认按钮等硬编码。
   - `ManifestWorkspace.tsx` & `CaseCenterWorkspace.tsx`：清除统计卡片、标记按钮、责任人缺省 Tag 及工单处理按钮等硬编码。
   - `DayCloseWorkspace.tsx` & `ShipmentDetailDrawer.tsx`：清除卡口校验列、校验状态 Tag、签署按钮及详情抽屉 Title/Label 等硬编码。

## 验收标准 (DoD)

1. 在 `en-CA` 语言模式下，所有页面不再包含中文硬编码。
2. 在 `zh-CN` 及 `fr-CA` 语言模式下，现有界面功能与文案不受影响。
3. 执行前端类型检查 `pnpm run typecheck` 以及构建 `pnpm run build` 均无报错通过。

## 实施结果

已完成工作台硬编码清理、三种启动语言词条对齐、司机包裹导航权限回归和站点请求头测试修正。26 个 Vitest 测试、类型检查和生产构建均通过。
