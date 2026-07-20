# Iteration 01 执行总结

## 完成内容

- 完成运营闭环评审，补齐班前检查、到站差异、发布门禁、司机 custody 交接、动态改派、收车退件、异常 SLA、日终关站和上游 ACK。
- 在 MySQL 8 建立 24 张业务表、34 个外键及面向任务、库存、接入、异常和 outbox 的索引。
- 引入 Spring JDBC、Flyway、MySQL Driver 和 HikariCP；生产默认 MySQL，`memory` profile 仅供隔离测试。
- 实现数据库司机 Repository、token 哈希会话、任务查询、扫描批次、交接审核、派送尝试、POD 文件/元数据、状态事件和 outbox。
- 实现上游 Canonical Shipment 推送、manifest 到站扫描、运营波次/司机任务创建、异常队列查询和回调 worker。
- 修复跨司机数据访问风险，服务端从 JWT 解析司机身份并校验请求中的 `driver_id`。

## 验证证据

- 7 个 JUnit/MockMvc 测试通过，0 failures，0 errors。
- 可执行 JAR 构建成功。
- 真实 MySQL E2E 从上游推送运行到妥投，结果为 `DELIVERED|1 delivery attempt|7 status events|6 outbox events`。
- E2E 同时验证跨司机妥投被拒绝、相同妥投幂等键不会产生第二次尝试，退出后无测试数据残留。
- E2E 使用独立测试运单并在退出时清理；应用重启后妥投状态仍存在。
- 开发种子数据已移出 Flyway production migration，避免自动创建演示凭据。
- 空库 Flyway dry-run 和 Docker 镜像 `opendelivery-backend:validation` 构建成功，Compose 配置校验通过。

## 已知边界

首期提供运营 API 而非完整 Web 运营台；路线优化、实时轨迹、COD/计费、客户门户、复杂合规件和对象存储云适配器属于后续产品迭代。当前 POD 使用可配置持久文件路径，生产应挂载受控对象/文件存储。
