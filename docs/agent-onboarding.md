# Agent 入职导读：OpenDelivery 运营系统与后端

> 用途：让任何开发 agent 在阅读约 1–2 小时后，能独立承接任意待开发迭代并与他人并行开发。先读本文，再按"开始一个迭代"清单行动。

## 0. 系统一句话

Last-mile 物流：Java 17 / Spring Boot 3.3 Maven 多模块后端 + React 运营 Web，双产品（Driver API 服务司机本人；Operations API/Web 服务站点运营），共享 MySQL 8 `opendelivery`（Flyway 已到 V13）。当前目标：**三城市（YHZ/YYZ/YVR，一城一站）连续 5 个营业日试运营（0.5 MOV Gate）**。

## 1. 阅读地图（按此顺序）

**第一步：规则（必读，不可跳过）**

| 文档 | 读什么 |
|---|---|
| `AGENTS.md`（仓库根） | 构建/测试命令、代码风格、数据访问约定 |
| `docs/document-governance.md` | 强制交付顺序：产品/契约文档 → 迭代文档 `DRAFT→REVIEWED` → 开发 → 测试 → 执行总结；中英双语同步 |

**第二步：产品与业务**

| 文档 | 读什么 |
|---|---|
| `docs/prd/operations-system-product-model.md` | 产品模型、营业日作业流（上游推送→区域归属→到仓批次→板/笼装载→派送规划→司机扫描→审批→日终）、设计原则"系统推导优先、人工只处理例外" |
| `docs/prd/operations-web-specification.md` | 页面级规格（含 4.11 到仓批次工作台）与通用交互规则 |
| `docs/roadmap/version-roadmap.md` | 版本边界：0.5 MOV 承诺范围、P0 阻断项、I09+ 暂停 |

**第三步：计划与现状**

| 文档 | 读什么 |
|---|---|
| `docs/iterations/two-product-execution-plan.md` | 双产品边界、迭代定义、优先依赖链 `O03→D01→D02→O04→O05→D03→D04→O06→O07→联合 E2E` |
| `docs/operations/summaries/iteration-r03-c-unit-linkage-summary.md` | 到仓域最新交付与口径决策（异常口径、区域投影、recompute 语义） |
| `docs/shared/summaries/iteration-t01-persistence-orm-summary.md` | 持久层重构现状、逃生门清单、T02 输入 |
| `docs/driver/summaries/driver-api-current-state-audit.md` | Driver API 真实现状（`/driver/v1/**` 已删除，`/auth/**`、`/delivery/**` 是正式契约） |
| `docs/operations/summaries/failed-parcel-return-receipt-summary.md` | 退库接收已交付范围 |

**第四步：设计与契约（按需精读你迭代涉及的部分）**

| 文档 | 读什么 |
|---|---|
| `docs/design/api-contracts.md` | Integration/Driver/Operations 全部 API 契约与错误码 |
| `docs/design/data-model.md` | 表结构、索引、投影模式、容量原则 |
| `docs/design/state-machines-and-operations.md` | 包裹/任务/批次/custody 状态机 |
| `docs/design/persistence-architecture.md` | 持久层 ADR（JPA+逃生门），写数据访问代码前必读 |

## 2. 代码架构

```
easydelivery-app/      应用入口；config/（站点上下文/拦截器）；
                       integration/（上游接入：ingestion/ routing/ outbox/）；
                       operations/（运营域，Package-by-Feature 包结构）：
                         ├── arrival/      到仓实物与 Manifest 入站收货（含 operations.arrival.persistence）
                         ├── station/      站点与服务范围管理（Station/ServiceArea）
                         ├── shared/       跨域共享组件（如 AreaMembershipService、公共 DTO）
                         ├── area/         站内 GeoJSON 配送区域
                         ├── controltower/ 控制塔读模型与看板
                         ├── returns/      退库接收与失败件处置
                         └── planning/     派送规划（串行等待 R04 合入后再包重构）
easydelivery-common/   共享 DTO、AppResponse/BizException、JDBC 投递主链、token/driver repository
easydelivery-auth|delivery|scan/  司机侧契约模块（/auth/**、/delivery/**）
easydelivery-operations-web/      React 19 + antd + React Query + i18next（en/fr/zh）；workflows/ 页面；Vitest + Playwright
scripts/               真库 E2E（*-e2e.sh）与种子（db/00x_*.sql）
```

关键模式：

- 运营控制器按域拆分（`ArrivalOpsController` 等各自申明 `@RequestMapping("/ops/v1")` 保持 URL 路径 100% 不变）；服务按域独立。
- 站点上下文：`OperationsAccess.selectedStationId()/requireStation()`，请求头 `X-Station-Code`；**每个查询/写操作都必须带站点隔离**。
- 持久层按本域归口：实体与 JPA 仓储放各子域 `persistence`（如 `operations.arrival.persistence`）；逃生门 SQL 保留 `JdbcTemplate` 并注释（见 ADR）。
- 审计：写操作插 `operation_audit_log`（action_code、resource、reason、after_json、request_id）。
- 错误契约：`AppResponse` 信封 + `BizException(code,message)`；前端按 `biz_code` 处理。

## 3. 测试与验证方式

- Java：`./tools/apache-maven-3.9.8/bin/mvn test`（纯单测，无库依赖；新共享逻辑配 policy 类单测）。
- 前端：`cd easydelivery-operations-web && pnpm typecheck && pnpm vitest run && pnpm lint && pnpm build`。
- 真实 MySQL E2E：仿照 `scripts/arrival-batch-e2e.sh`——起应用、ops 登录（向用户索取 `DB_PASSWORD`/`OPS_PASSWORD`）、`X-Station-Code` 切换、全流程断言、**结尾必须自动清理测试数据、断言不得依赖共享库为空**。
- Playwright：`easydelivery-operations-web/tests/e2e/`，只读渲染用例不污染共享库。

## 4. 红线约束（违反即返工）

1. 文档先行：无 `REVIEWED` 迭代文档不动产品代码；产品/契约/表/跨产品事件必须同步更新对应设计文档（中英）。
2. 运营端不得替司机扫描或派送；Driver API 身份只从 token 取；到仓事实不改包裹状态与 custody。
3. Flyway 只新增文件，下一个版本号先查 `db/migration` 最新号（当前 V13，下一可用 V14）；禁止回改已发布迁移。
4. 幂等（重复请求不产生二次效果）、审计、三语言（前端所有新文案进 `i18n.ts` 三个 locale）、乐观锁是 DoD 默认项。
5. 性能：集合操作必须集合级 SQL，禁止逐行循环写库；新查询核对索引左前缀；反范式投影与同事务同步。
6. 不执行 `git commit/push` 等任何 git 写操作，除非用户明确要求。

## 5. 并行开发与包重构规则（多 agent）

- **认领制**：开工前在迭代文档头部把状态改为 `IN_PROGRESS` 并署名 agent 名与日期，避免撞车；完成写执行总结并置 `COMPLETED`。
- **重构串行规则**：R04 正在修改 `MapPlanningService` 与规划 UI。`planning` 包代码归位需严格等 R04 合入后再执行。首期重构仅进行 `integration` 拆包 + `arrival`（含 T01 实体）/`station`/`shared` 代码归位，零冲突风险。
- **高频冲突文件**：`OperationsController.java`（拆分子控制器后按域隔离）、`src/i18n.ts`、`docs/design/api-contracts.md`、`data-model.md`、Flyway 版本号、共享 seed/脚本。约定：i18n 一律追加新 `Object.assign(translations['xx'], {...})` 块不改既有行；Flyway 先取号再写文件。
- **行为不变式**：重构类迭代以既有单测+E2E 全绿为证据（API URL 路径必须 100% 保持一致）；功能迭代以新断言全绿且旧断言不动为证据。

## 6. 当前迭代队列与接管推进（2026-07-21）

由 **Lead Architect** 全权接管全部迭代队列：

| 负责人 | 迭代（按执行顺序） | 说明 |
|---|---|---|
| **Lead Architect（接管）** | ① O04 扫描监督（COMPLETED）→ ② R04 派送规划效率（COMPLETED）→ ③ O05 交接审批（COMPLETED）→ ④ T02 持久层·接入域（COMPLETED）→ ⑤ O06 派送与回仓监督（COMPLETED）→ ⑥ O07 日终关站（COMPLETED）→ ⑦ O08 基础配置与异常中心（COMPLETED）→ ⑧ T03 持久层·履约与派送规划域（COMPLETED）→ ⑨ T04 持久层·扫描交接与日终域（COMPLETED）→ ⑩ 联合 E2E | O04、R04、O05、T02、O06、O07、O08、T03、T04 均已全部交付并完成全链路验证 |
| 暂停/后续 | D01–D04（用户指示暂停）、联合 E2E、T03/T04 | D 线不动；联合 E2E 在全线上上线后执行 |

冲突面提示：R04 动 `MapPlanningService` 与规划 UI；O04 新增监督服务与 UI，两者在 `OperationsController`、`i18n.ts`、契约文档处按 §5 约定协调；R04 可能占用 Flyway V14，后来者先查号再写。

## 7. 开始一个迭代（checklist）

1. 读对应产品 README + 本文 §1 相关文档；精读将修改的服务、表、页面（先 `Glob/Grep` 定位再读）。
2. 更新 PRD/契约/数据模型 → 起草或更新迭代文档 → 请用户置 `REVIEWED`。
3. 开发：后端服务/迁移 → 单测 → 前端页面/三语言 → 前端三件套。
4. 真库 E2E（新脚本或扩展既有脚本，自清数据）→ 需要时起栈跑 Playwright。
5. 执行总结（中英）→ 更新对应 README 索引 → 向用户汇报验证证据。
