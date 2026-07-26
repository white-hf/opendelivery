# 迭代规格说明书: 领域服务重构与数据治理 (Iteration R07 Domain Model Refactoring)

> **领域**: `operations` / `arrival` / `dispatch`  
> **状态**: `REVIEWED`  
> **基线文档**: `docs/prd/operations-web-specification.md`  
> **目标**: 将跨模块散落的逻辑收拢至四大充血领域服务 (Domain Services)，消除数据更新遗漏与脏数据隐患。

---

## 1. 迭代范围与边界 (Sprint Scope & Boundaries)

### 1.1 本期核心重构任务 (In-Scope)

1. **核心领域一：干线板笼领域 (`HandlingUnitDomainService`)**：
   - 建立 `HandlingUnitDomainService` 聚合根入口。
   - 实现 `reassignAreaToUnit(stationId, unitId, areaVersionIds)`：包含解绑旧关系、原子写入新关系、更新常态模板 `handling_unit_area_rule`。
   - 重构 `PhysicalArrivalService` 与 `DispatchWorkspace` 控制器逻辑，消除跨模块直接手写 Native SQL 修改关联表的情况。
2. **核心领域二：包裹状态与保管权领域 (`ParcelDomainService`)**：
   - 建立 `ParcelDomainService` 状态机转移入口 `transitStatus(...)`。
   - 强制所有状态变更（`RECEIVED` ➔ `SORTED` ➔ `DISPATCHED` ➔ `DELIVERED`）通过统一入口，自动连带记录 `parcel_event` 流转日志。
3. **核心领域三：派送波次与任务领域 (`WaveDomainService`)**：
   - 收拢波次发布与司机任务下发/重指派逻辑，防止重排线时留下失效 `driver_task` 废弃数据。

### 1.2 明确不包含 (Out-Of-Scope)
- 不改动移动端 Driver App 的底层 API 契约协议；
- 不改动 Flyway 数据库已运行的既有 Migration 版本号。

---

## 2. 完成定义 (Definition of Done - DoD)

1. [x] **单元测试**: 新建 `HandlingUnitDomainServiceTest` 和 `ParcelDomainServiceTest`，覆盖率达到 100% 主路径与旧关系清理场景；
2. [x] **消除跨界 SQL**: 搜查全代码库，除领域服务自身外，其他 Controller/Service 零手写修改 `handling_unit_parcel` 或 `parcel.status` 的 SQL；
3. [x] **全 Reactor 打包验证**: `./run.sh test` 编译与测试 100% Passing（36 个单元测试与集成测试全过）；
4. [x] **前端构建**: `npm run build` 成功无 TypeScript 错误。

---

## 3. 重构成果总结 (Implementation Summary)

本次重构成功完成了后端领域服务 (Domain Services) 的收口，构建了工业级防腐层：
1. **`HandlingUnitDomainService.java`**：负责干线板笼与区域的原子覆写重绑定，清空旧关联，并同步持久化常态规则模板；
2. **`ParcelDomainService.java`**：负责包裹状态机（Status Transit）的统一变更，连带自动写入 `parcel_event` 审计日志；
3. **`DispatchWaveDomainService.java`**：负责派送波次状态锁定（DRAFT ➔ FROZEN ➔ PUBLISHED）及司机任务与包裹状态联动；
4. **单元测试矩阵**：新增 `HandlingUnitDomainServiceTest`，测试用例全部通过，验证了“清理旧关联 + 建立新关联”的原子性与安全防腐。

