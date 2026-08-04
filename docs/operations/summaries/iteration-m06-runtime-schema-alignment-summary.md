# M06 交付总结：运行时与数据模型对齐

状态：COMPLETED

## 交付内容

控制塔入仓异常查询不再选择不存在的 `operational_case.description` 列，避免 `GET /api/ops/v1/control-tower` 因 SQL grammar error 失败。当前 `DispatchWaveEntity` 已确认没有 `created_by` 映射，数据库保持 Flyway V22 结构，不新增临时字段。

## 验证

- `mvn -pl operations/easydelivery-ops-api -am -DskipTests compile`：通过。
- 公共模块测试：9 个通过。
- 运营模块测试：44 个通过。运营 API 已使用与其他模块一致的 `mock-maker-subclass`，不再依赖本机 JVM self-attach。

## 运行注意

日志中仍可能看到旧进程产生的错误。部署时必须停止占用 9001 的旧 Java 进程，并使用最新构建产物启动；否则修复后的代码不会被加载。
