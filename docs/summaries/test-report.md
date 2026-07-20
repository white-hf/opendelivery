# 测试报告

## 范围与环境

测试覆盖 Java 17、Spring Boot 3.3、MySQL 8.0.43、认证、权限隔离、上游接入、manifest、派车、扫描交接、妥投、POD 元数据、状态事件和 outbox。真实数据库为本机 `opendelivery`。

## 结果

| 测试层 | 数量/结果 | 证据 |
|---|---|---|
| Java 自动化 | 30 通过 | Common 5、App 25；含管理员站点可见性、FeatureCollection、三语言 API、账户回退、locale 白名单与资源键完整性 |
| Operations Web | 19 通过 | API、权限、payload、三语键集合、Polygon 自动闭合、混合 FeatureCollection 解析与辅助要素统计；类型检查、构建、lint 通过 |
| Maven 构建 | 成功 | 所有模块编译并生成可执行 JAR |
| MySQL E2E | 通过 | 上游→到站→波次→扫描→审核→妥投 |
| 持久化 | 通过 | 应用重启后 `DELIVERED` 保留 |
| 空库迁移 | 通过 | Flyway V1 创建 24 张业务表及 history |
| I02–I04 存量迁移 | 通过 | 真实库依次升级至 V3 路由、V4 运营身份、V5 入站工作台 |
| 多城市入站 E2E | 通过 | 正常/破损/多货/重复、差异 Case、关闭门禁、跨站 403、事件与 custody/outbox |
| 调度与装车 E2E | 通过 | 候选、草稿、发布、司机本人扫描/提交、自批拒绝、主管交接、未扫描撤回 |
| R01 空间区域 | 自动化通过、运营复验中 | Google Maps 主视图与图层侧栏；新增/查看地图和版本/修改为新草稿/校验/发布/停用/重新启用闭环；真实 MySQL 验证 V1→V2→INACTIVE→ACTIVE 且保留 2 个版本；三站、共享边界、重叠及跨站门禁保持通过；E2E 数据与审计已清理 |
| R01.1 多语言 | 后端/Web 基础通过 | Flyway V9–V10；法语认证、中文偏好、英文回退、空账户偏好回退法语站点默认；测试后恢复所有配置 |

E2E 最终断言：1 次派送尝试、7 条状态事件、6 条 outbox 事件，测试数据清理成功。
同时验证其他司机无法妥投该任务，以及重复提交相同 `idempotency_key` 不会产生第二次尝试。Docker 镜像构建与 Compose 配置解析通过。

## 残余测试风险

尚未在真实第三方上游、生产对象存储、高并发、网络分区和大批量数据上验证。R01 自动化门禁已完成，只剩运营人员对 Google Maps 的浏览器视觉验收。执行命令为 `DB_PASSWORD=... OPS_PASSWORD=... ./scripts/delivery-area-e2e.sh`；凭据只通过环境变量传入。Google Loader 已返回 HTTP 200，Web 测试/类型检查/lint/构建通过，密钥未进入 Git diff。区域、Manifest 和 Dispatch 工作台已按页面动态拆包；Ant Design 等共享依赖仍使初始块约 1.08 MB 并产生非阻塞告警，后续继续做 vendor 拆分和性能预算。outbox 重试逻辑仍需真实回调 sandbox 做 ACK/死信演练。
