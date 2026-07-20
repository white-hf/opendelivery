# MySQL 部署 Runbook

## 配置

应用账号仅授予 `opendelivery.*` 所需权限。通过环境变量提供连接与密钥，POD 路径必须位于持久卷。生产不执行 `scripts/db/002_development_seed.sql`。

## 首次部署

1. 创建 `opendelivery`，使用 `utf8mb4_0900_ai_ci`。
2. 授权应用账号并验证 TLS/网络策略。
3. 备份后启动单实例应用，让 Flyway 执行 migration。
4. 查询 `flyway_schema_history` 和表数量，确认 migration 成功。
5. 创建伙伴、站点、运营账号/密钥和真实司机，再开放流量。

## 健康检查

检查应用启动日志、Hikari 连接、Flyway 版本、outbox 最老事件、死信、磁盘和 POD 持久卷。数据库连接失败时不要切换到内存 profile；应停止写流量并恢复依赖。

## 备份与恢复

在 migration 前做一致性备份，并定期执行恢复演练。恢复后核对 parcel 当前状态与状态事件尾部、任务明细、custody、POD 元数据和 outbox ACK，避免只验证表存在。

