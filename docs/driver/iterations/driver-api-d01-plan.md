# Driver API D01 迭代设计：司机本人任务与包裹列表闭环

> 状态：`REVIEWED`（2026-07-22）。已确认 Driver App 契约保持 `/delivery/parcels/tasks` 和 `/delivery/parcels/delivering` 路径及 JSON 结构兼容。

## 1. 范围与目标

D01 迭代专注于司机 App 端包裹列表（待装车/派送中）的数据正确性、多波次汇总以及越权拦截门禁。

### 包含范围 (In Scope)
1. **本人权限防越权 (Authorization Gate)**：
   * 校验请求参数中的 `driver_id` 与 JWT Token 中的身份 `driverId` 是否一致。
   * 不匹配时拦截并返回 403 (`AUTH.UNAUTHORIZED` / `UnauthorizedException`)。
2. **多波次包裹平铺汇总 (Multi-Wave Parcel Aggregation)**：
   * 司机端不暴露波次/任务概念。
   * 查询 SQL 去除单日/单波次硬编码限制，自动将司机名下所有有效波次的包裹（含历史留存与今日新增）平铺汇总返回。
3. **任务状态隐式读门禁 (Task Lifecycle Gate)**：
   * 隐式校验任务状态 `t.status IN ('PUBLISHED', 'ACCEPTING', 'IN_PROGRESS')`。
   * 运营取消/撤销的任务包裹自动在司机端隐式消失。
4. **数据库索引与查询性能加固**：
   * 补齐驱动表复合索引，保证多表 JOIN 在大数据量下走主键点查（PK Point Lookup）。

### 非目标 (Out of Scope)
* 不改动 Android App API 契约与路径。
* 不返回多语言长文本（通过稳定 `biz_code` 由 App 本地渲染）。
* 不包含运营系统功能（如定时自动关单、日终关站清算属于 Operations 运营端 O 线迭代，不在 Driver API 执行）。

## 2. API 契约与规则

### 2.1 待装车列表 `GET /delivery/parcels/tasks`
* **参数**：`criteria=UNSCANNED` (必需), `driver_id={id}` (必需)
* **鉴权**：校验 `driver_id` == Token 中的 `driverId`。
* **状态匹配**：`t.status IN ('PUBLISHED', 'ACCEPTING', 'IN_PROGRESS')` AND `p.status = 'ASSIGNED'` AND `ti.item_status = 'ASSIGNED'`。

### 2.2 派送中列表 `GET /delivery/parcels/delivering`
* **参数**：`driver_id={id}` (必需)
* **鉴权**：校验 `driver_id` == Token 中的 `driverId`。
* **状态匹配**：`t.status IN ('PUBLISHED', 'ACCEPTING', 'IN_PROGRESS')` AND `p.status = 'OUT_FOR_DELIVERY'` AND `ti.item_status = 'OUT_FOR_DELIVERY'`。

## 3. 数据库与索引要求

使用 `driver_task` (驱动表 `(driver_id, status)`) 锁死活跃任务，再关联 `driver_task_item`、`parcel` 及 `waybill`，确保大数据量下查询延迟在毫秒级。

## 4. DoD (Definition of Done)

* [x] D01 迭代文档确认 (`REVIEWED`)
* [x] 源码实现与越权门禁加固
* [x] 多波次汇总与撤销隔离单元/集成测试通过
* [x] `./run.sh test` 全量验证通过
* [x] 编写 D01 执行总结 (`COMPLETE`)
