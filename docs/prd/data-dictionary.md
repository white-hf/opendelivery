# 🗄️ EasyDelivery 系统核心数据字典规范 (Data Dictionary PRD)

## 📌 一、 概述与通用规范

本文档为 EasyDelivery 系统的物理数据库字典基线。规范要求：
1. 所有核心业务实体表必须支持**生产环境影子测试与数据隔离**（包含 `is_test` 字段）；
2. 所有表默认包含主键 `id BIGINT AUTO_INCREMENT`；
3. `is_test` 默认值为 `0`（`0`-真实生产业务件，`1`-生产巡检/压测影子件）；
4. 所有软删除或逻辑隔离字段统一命名为 `status` 或 `is_test`。

---

## 📑 二、 核心物理表结构规范

### 1. `waybill` (运单表)
| 字段名 | 类型 | 允许为空 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | NO | AUTO_INCREMENT | 主键 ID |
| `external_waybill_no` | VARCHAR(64) | NO | | 上游外部运单号 |
| `is_test` | TINYINT | NO | `0` | **影子测试标记** (0-真实, 1-影子测试件) |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | 创建时间 |

### 2. `parcel` (包裹表)
| 字段名 | 类型 | 允许为空 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | NO | AUTO_INCREMENT | 主键 ID |
| `waybill_id` | BIGINT | NO | | 关联运单 ID |
| `tracking_no` | VARCHAR(64) | NO | | 末端快递追踪号（唯一索引） |
| `current_station_id` | BIGINT | NO | | 当前归属站点 ID |
| `status` | VARCHAR(32) | NO | `'READY_FOR_DISPATCH'` | 包裹履约状态 |
| `is_test` | TINYINT | NO | `0` | **影子测试标记** (0-真实件, 1-影子测试件) |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | 创建时间 |

> 索引要求：`CREATE INDEX idx_parcel_station_test ON parcel(current_station_id, is_test, status);`

### 3. `dispatch_wave` (派送波次表)
| 字段名 | 类型 | 允许为空 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | NO | AUTO_INCREMENT | 主键 ID |
| `station_id` | BIGINT | NO | | 站点 ID |
| `wave_code` | VARCHAR(64) | NO | | 波次编号 |
| `service_date` | DATE | NO | | 服务日期 |
| `status` | VARCHAR(32) | NO | `'DRAFT'` | 波次状态 |
| `is_test` | TINYINT | NO | `0` | **影子波次标记** (0-真实波次, 1-测试波次) |

### 4. `driver` (司机信息表)
| 字段名 | 类型 | 允许为空 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | NO | AUTO_INCREMENT | 主键 ID |
| `home_station_id` | BIGINT | NO | | 归属主网点 ID |
| `credential_id` | VARCHAR(64) | NO | | 登录工号/证件号 |
| `driver_name` | VARCHAR(64) | NO | | 司机姓名 |
| `status` | VARCHAR(32) | NO | `'ACTIVE'` | 司机账号状态 |
| `is_test_driver` | TINYINT | NO | `0` | **测试专用司机标记** (0-真实司机, 1-巡检/测试司机) |

### 5. `driver_task` (司机派送任务表)
| 字段名 | 类型 | 允许为空 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | NO | AUTO_INCREMENT | 主键 ID |
| `wave_id` | BIGINT | NO | | 关联波次 ID |
| `driver_id` | BIGINT | NO | | 关联司机 ID |
| `station_id` | BIGINT | NO | | 站点 ID |
| `task_code` | VARCHAR(64) | NO | | 任务单号 |
| `is_test` | TINYINT | NO | `0` | **影子任务标记** (0-真实任务, 1-测试任务) |

### 6. `driver_task_item` (司机任务明细项表)
| 字段名 | 类型 | 允许为空 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | NO | AUTO_INCREMENT | 主键 ID |
| `task_id` | BIGINT | NO | | 关联司机任务 ID |
| `parcel_id` | BIGINT | NO | | 关联包裹 ID |
| `stop_sequence` | INT | NO | `1` | 派送顺序号 |
| `item_status` | VARCHAR(32) | NO | `'ASSIGNED'` | 任务项状态 |
| `active_slot` | TINYINT | YES | `1` | 激活槽位（唯一约束辅助列） |
| `is_test` | TINYINT | NO | `0` | **影子任务项标记** |

### 7. `scan_session` (交接扫描批次表)
| 字段名 | 类型 | 允许为空 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | NO | AUTO_INCREMENT | 主键 ID |
| `driver_id` | BIGINT | NO | | 司机 ID |
| `station_id` | BIGINT | NO | | 站点 ID |
| `status` | VARCHAR(32) | NO | `'DRAFT'` | 批次状态 (`APPROVED`等) |
| `is_test` | TINYINT | NO | `0` | **影子批次标记** |

### 8. `operational_case` (运营异常工单表)
| 字段名 | 类型 | 允许为空 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | NO | AUTO_INCREMENT | 主键 ID |
| `parcel_id` | BIGINT | NO | | 包裹 ID |
| `case_type` | VARCHAR(32) | NO | | 工单类型 |
| `status` | VARCHAR(32) | NO | `'OPEN'` | 工单状态 |
| `is_test` | TINYINT | NO | `0` | **影子工单标记** |

---

## 🛠️ 三、 数据防污染与 SQL 审计规则

1. **写操作规则**：
   - 生产环境发起自动化测试时，在根实体 `waybill` 及 `parcel` 插入时指定 `is_test = 1`；
   - 派生子表（`driver_task_item`, `scan_event`）自动继承父级实体的 `is_test` 属性。

2. **读操作规则（防脏数据统计）**：
   - 所有面向运营大屏 (Control Tower)、报表导出、KPI 履约率计算的 SELECT SQL 语句，**必须显式增加 `AND p.is_test = 0` 条件**；
   - 司机端移动 API 查询在途包裹时，测试司机 (`is_test_driver = 1`) 只能查询到 `is_test = 1` 的影子任务；真实司机只能查询到 `is_test = 0` 的真实任务。
