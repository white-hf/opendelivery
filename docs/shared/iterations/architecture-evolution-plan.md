# 架构拆分方案：双后端服务解耦与模块重构 (Architecture Evolution Plan)

> 文档状态：`REVIEWED` (2026-07-23)  
> 目标：将混在单一进程中的 Driver API（司机端）、Operations API（运营端）以及 Operations Web（前端）拆分为结构清晰、职责独立的三大服务，同时保持 100% 协议与测试兼容。

---

## 1. 痛点与拆分目标

### 1.1 现存问题
1. **进程混合**：`easydelivery-app` 包含了 Driver API（`/auth/**`, `/delivery/**`, `/delivery/scan/**`）和 Operations API（`/operations/**`）。运营后台的大复杂查询和报表导出可能拖垮司机 App 端。
2. **前后端混合**：`easydelivery-operations-web` 与 Java 后端放在同一 Monorepo，但缺乏明确的前后端代理契约与构建分离。

### 1.2 目标架构
* **三服务解耦**：
  1. `easydelivery-driver-api`：专服务司机移动端（端口 9000，维持 Android 契约不变）。
  2. `easydelivery-ops-api`：专服务运营管理后台（端口 9001，专注于后台逻辑与报表）。
  3. `easydelivery-ops-web`：React/Vite 前端，通过 Vite/Nginx 代理区分 `/api/driver` 和 `/api/ops`。
* **共享底层**：`easydelivery-common` 存放共享 JPA Entity、Flyway 迁移、DTO、Response 及加密鉴权工具。

---

## 2. 模块重构计划 (Maven Topology)

```
easydelivery-backend/
├── easydelivery-common/        [基础 JAR] DTO, Repository, Flyway, Common Exceptions
├── easydelivery-auth/          [司机模块] 依赖 common
├── easydelivery-delivery/      [司机模块] 依赖 common
├── easydelivery-scan/          [司机模块] 依赖 common
├── easydelivery-driver-api/    [进程 1] 整合 auth/delivery/scan，暴露 9000 端口
├── easydelivery-ops-api/       [进程 2] 包含所有 operations 服务，暴露 9001 端口
├── easydelivery-app/           [兼容入口] 保留原组合启动类，保证旧打包与单进程部署兼容
└── easydelivery-operations-web/[前端]
```

---

## 3. DoD (Definition of Done)

* [x] 架构拆分方案与规则文档落盘 (`REVIEWED`)
* [x] 拆分与创建 `easydelivery-driver-api` 和 `easydelivery-ops-api` 独立 Spring Boot 启动模块
* [x] 更新 `run.sh` 脚本，支持单启动与多启动模式
* [x] 保证全量 `./run.sh test` JUnit 测试 100% 通过
* [x] 编写执行总结与运维发布说明 (`COMPLETE`)
