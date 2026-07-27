# 架构设计原则

## 分层与依赖方向

```text
API/Controller → Application Use Case → Domain → Persistence
                                  ↘ Query Service → Query Repository/Read Adapter
```

- Controller 只处理协议、鉴权上下文、输入校验和响应封装。
- Application 层编排用例、事务、权限和跨领域协调。
- Domain 层维护状态机、不变量、策略和领域事件，不依赖 SQL、HTTP 或 UI。
- Command Persistence 通过 Entity + Repository 维护生命周期。
- Query Persistence 返回专用 DTO；复杂查询集中在 Query Repository。

## 边界与模块化

- 按业务能力组织模块，而不是按技术文件夹堆放所有代码。
- 跨模块依赖通过明确的接口、事件或 DTO；禁止直接访问其他模块的表和内部 Entity。
- 外部系统通过 Anti-Corruption Layer 转换为内部 canonical model。
- 数据库是事实存储，不是跨模块共享业务逻辑的替代品。

## 架构决策方法

每个重要技术选择都记录：背景、选项、决策、取舍、影响、迁移和回滚。先定义边界和非目标，再选择框架；避免为了“完全统一”牺牲可验证性和运营稳定性。
