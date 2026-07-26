# 🚀 迭代规约：R07 - 生产环境影子全流程测试与数据隔离机制 (Iteration Spec R07)

> **状态**: `REVIEWED` (评审通过，已完成 Sprint 规划)  
> **迭代目标**: 落地生产环境全流程影子测试架构，实现数据物理表染色与防污染隔离，保证全链路 E2E 探针测试不影响真实报表与真实司机。

---

## 📌 一、 迭代任务拆解 (Task Decomposition)

### 任务 1：Flyway 数据库 DDL 升级与染色字段扩展
- **编号**: `TASK-R07-DB-01`
- **内容**: 编写 Flyway 脚本 `V0.8__add_is_test_shadow_flag.sql`；
- **涉及表**: `waybill`, `parcel`, `dispatch_wave`, `driver`, `driver_task`, `driver_task_item`, `scan_session`, `operational_case`；
- **DoD**: 运行 `./run.sh test` 顺利执行 Flyway 迁移，数据库字段与索引全量建立。

### 任务 2：运营控制塔 (Control Tower) 与看板 SQL 过滤改造
- **编号**: `TASK-R07-OPS-01`
- **内容**: 修改 `ControlTowerService.java` 及看板统计 SQL，增加 `AND p.is_test = 0` 过滤条件；
- **DoD**: 创建 `is_test = 1` 的包裹后，控制塔与在途监控指标保持不增长。

### 任务 3：地图排线与司机自动指派隔离逻辑
- **编号**: `TASK-R07-OPS-02`
- **内容**: 修改 `MapPlanningService.java`，确保正常波次指派跳过影子测试包裹，影子波次仅能派发给 `is_test_driver = 1` 的测试司机；
- **DoD**: 单元与集成测试验证影子包裹绝不指派给普通司机。

### 任务 4：生产影子全流程 E2E 自动化测试用例与验证套件
- **编号**: `TASK-R07-TST-01`
- **内容**: 在 `easydelivery-ops-api` 编写包含 `is_test = 1` 影子包裹全流程流转的集成测试套件；
- **DoD**: 集成测试套件运行通过，模拟全流程建单、指派、扫码交接、妥投关单闭环。

---

## 📑 二、 API 与契约 Delta 说明

| 模块 | 路径 | 变动类型 | 变更说明 |
| :--- | :--- | :--- | :--- |
| **Operations API** | `POST /ops/v1/planning/waves` | Header / Body | 支持可选 `X-Shadow-Test: true` Header 或入参 `isTest: true` |
| **Driver API** | `GET /delivery/parcels/tasks` | Query Param | 当司机为 `is_test_driver = 1` 时自动仅返回影子测试任务 |

---

## 🏁 三、 迭代 DoD (完成定义) 标准

1. [x] **架构与 PRD 落地**: 数据字典与 E2E 方案 PRD 已全量更新并落盘；
2. [x] **数据库平滑迁移**: Flyway Migration (`V19__shadow_testing_isolation.sql`) 顺利升级且单元测试全量通过；
3. [x] **数据隔离验证**: 影子件 (`is_test = 1`) 被控制塔统计 (`ControlTowerService`) 与真实司机端 (`JdbcDeliveryOperations`) 物理隔离过滤；
4. [x] **全流程测试通过**: `./run.sh test` 全量单元与集成测试 100% 成功。

