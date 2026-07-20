# 数据库初始化 Runbook

## 目的

创建 OpenDelivery 所需的空 MySQL 数据库。业务表由领域模型评审后的 Flyway migration 管理。

## 执行

不要将密码写入脚本或源码。通过交互输入或批准的 Secret 机制提供：

```bash
mysql -h127.0.0.1 -uuniuni_hf -p < scripts/db/001_create_database.sql
```

## 验证

```sql
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'opendelivery';
```

MySQL 8 预期字符集为 `utf8mb4`，排序规则为 `utf8mb4_0900_ai_ci`。

## 回滚

数据库删除具有破坏性，因此不自动化。删除前必须获得明确批准，并确认 schema 中没有需要保留的数据。
