# T02 持久层与计算卸载优化计划 (Database Computation Offloading Plan)

> 文档状态：`REVIEWED` (2026-07-23)  
> 目标：将数据库（MySQL）承担的 GeoSpatial 空间计算、JSON 解析、`SELECT MAX()+1` 序列计算以及实时 `COUNT(*)` 聚合统计全面卸载至 Java 内存层，降低 DB 负担并提升高并发性能。

---

## 1. 优化点与落地范围

| 序号 | 优化项 | 现有实现 | T02 优化落地方案 | 涉及模块 |
|---|---|---|---|---|
| **1** | **GeoJSON 空间计算** | MySQL `ST_Contains` 函数 | 引入 **JTS Topology Suite**，在 Java 内存中完成 `Polygon.contains(Point)` | `easydelivery-common`, `easydelivery-ops-api` |
| **2** | **JSON 解析强类型化** | MySQL `JSON_EXTRACT` / `JSON_OBJECT` | JPA `AttributeConverter` + Jackson，强类型 DTO 映射 | `easydelivery-common` |
| **3** | **事件 Sequence 算号优化** | `SELECT COALESCE(MAX(sequence_no), 0) + 1` | 消除写前读，基于数据库自增主键 / Java 状态机递增推导 | `operations/easydelivery-ops-api` |
| **4** | **看板与关站统计内存化** | 数据库多次 `COUNT(*)` 扫描 | Java 侧 `Stream` 过滤与分组统计内存计算 | `operations/easydelivery-ops-api` |

---

## 2. DoD (Definition of Done)

* [x] T02 优化计划文档落盘 (`REVIEWED`)
* [ ] 在 `easydelivery-common` 中引入 JTS 依赖并完成空间/JSON 转换工具编写
* [ ] 完成 `ShipmentRoutingService`、`ShipmentIngestionService` 及 `ControlTowerService` 计算卸载重构
* [ ] 运行 `./run.sh test` 全量 JUnit 测试 100% 通过
* [ ] 编写 T02 执行总结文档 (`COMPLETE`)
