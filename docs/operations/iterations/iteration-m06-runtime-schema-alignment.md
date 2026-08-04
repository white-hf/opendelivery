# M06：运行时与数据模型对齐

状态：COMPLETED

## 背景

近期运行日志暴露了控制塔入仓异常查询引用不存在的 `operational_case.description` 列；同时旧进程曾加载过已删除的 `dispatch_wave.created_by` 映射，导致启动后查询失败。数据库当前由 Flyway V22 管理，生产表结构不应通过临时加列掩盖代码或运行包版本不一致。

## 目标与范围

- 修正控制塔入仓异常查询，使其只读取当前表结构和实际使用的字段。
- 校验 `DispatchWaveEntity` 与 `dispatch_wave` 表一致，不恢复不存在的 `created_by` 字段。
- 通过干净编译和单元测试验证修复，并明确运行时必须重启到最新构建产物。

## 验收标准

1. `GET /api/ops/v1/control-tower` 在存在入仓异常记录时返回 200，不再出现 `Unknown column c.description`。
2. `DispatchWaveEntity` 不包含 `created_by` 映射，数据库保持 Flyway V22 结构。
3. 相关模块编译、测试通过；运行手册要求停止旧的 9001 进程后再启动新包。

## 实施结果

- 已移除未使用且不存在的 `c.description` 查询列。
- 已核对 `DispatchWaveEntity` 与当前 `dispatch_wave` 表，未恢复 `created_by`。
- 已为运营 API 测试补齐 `mock-maker-subclass`，避免 Byte Buddy self-attach；公共模块 9 个测试和运营模块 44 个测试全部通过。
