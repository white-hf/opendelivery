# R07-Config 司机与站点服务范围系统配置中心迭代

> 状态：`COMPLETED`（2026-07-23 完成交付）；归属：运营产品；承接系统管理基础（I03）与路线规划（R02/R04），交付运营端 Web 的司机账号管理与站点服务范围配置中心。

## 背景与业务痛点

在站点日常运营中，调度员与站长需要根据能力实时调整配送站点的运营基础配置：
1. **司机管理**：新司机入职、旧司机离职或临时停用时，此前无法在 Operations Web 直接创建/修改司机账号及状态，只能靠后台 SQL 操作。
2. **服务范围配置**：当站点新增派送 Postal Code（邮编前缀）覆盖时，缺乏可视化管理界面，导致上游入站运单无法自动匹配路由。

## 范围与核心变更

1. **后端 API 扩展 (`OperationsController.java` / `DriverOperationsService.java` / `StationOperationsService.java`)**：
   - 司机管理接口：
     - `GET /ops/v1/system/drivers`：服务端分页与状态筛选查询司机列表。
     - `POST /ops/v1/system/drivers`：创建新司机（生成 credential_id、绑定 home_station_id 与初始化密码）。
     - `PUT /ops/v1/system/drivers/{id}/status`：启用/停用司机 (`ACTIVE` / `INACTIVE`)。
   - 站点服务范围接口：
     - `GET /ops/v1/system/stations/{stationId}/service-areas`：获取站点服务范围规则（城市、邮编前缀）。
     - `POST /ops/v1/system/stations/{stationId}/service-areas`：新增服务范围匹配规则。
2. **前端界面开发 (`SystemConfigWorkspace.tsx`)**：
   - 包含「司机管理」与「服务范围配置」两大 Tab。
   - 司机列表支持新增司机 Modal 弹窗、启停 Toggle 开关。
   - 服务范围支持新建覆盖规则。

## 规则与安全

- 司机密码不进行明文回显，仅输入时加密传输。
- 改变服务范围配置自动清空相关路由缓存。

## DoD

- Java JUnit 5：编写 `SystemConfigPolicyTest` 测试司机创建校验与服务范围规则处理。
- 前端：TypeScript 类型检查通过，`pnpm build` 无报错。
