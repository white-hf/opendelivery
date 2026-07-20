# 开发工作流

## 交付顺序

涉及业务行为的变更依次更新 PRD、系统设计、迭代计划、代码、测试证据和执行总结。数据库结构只能通过 `db/migration` 中新的 Flyway 文件演进；已发布 migration 不得修改。

## 本地准备

使用 Java 17 和仓库内 Maven。配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`、`UPSTREAM_API_KEY`、`OPERATIONS_API_KEY`；不得把值提交到仓库。开发演示数据需手工执行 `scripts/db/002_development_seed.sql`。

## 验证门禁

```bash
./run.sh test
./run.sh build
DB_PASSWORD='<secret>' scripts/mysql-e2e-test.sh
```

提交前检查 migration 可在空库执行、API 兼容、非法状态被拒绝、测试数据已清理、文档中英文同步。生产行为默认 MySQL；`memory` profile 仅用于隔离测试。

