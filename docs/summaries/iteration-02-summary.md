# I02 多城市路由基础交付总结

## 交付范围

I02 已完成“多城市、每城市一个站点”的路由最小闭环。上游 Canonical Shipment 继续提交收件地址和服务代码，`targetStationCode` 已改为可选提示。系统标准化国家、省、市和邮编，按有效服务范围的最长邮编前缀、优先级选择唯一活动站点。

数据库迁移 `V3__multi_city_routing.sql` 为 Station 增加城市、省、国家字段和一城一站唯一约束；新增 `station_service_area`；Waybill 增加 `routing_status`、`resolved_station_id`、`routing_reason_code`、`routed_at` 及队列索引。只持久化当前业务结果，不保存算法候选过程。

## 运营闭环

- 唯一匹配：Waybill 标记 `ROUTED`，Parcel 绑定站点，可继续生成入站 Manifest。
- 无匹配/冲突：Waybill 标记 `UNROUTABLE/AMBIGUOUS`，Parcel 进入 `ADDRESS_EXCEPTION`，自动创建 `ROUTING_EXCEPTION` Case。
- 人工处理：运营人员选择活动站点并填写原因；系统更新 Waybill/Parcel、写 Case Action 并解决 Case。
- 配置接口：支持查询/创建站点及服务范围、重新路由和人工覆盖。

## 验证结果

`./run.sh test` 通过 9 项测试；`./run.sh build` 成功。真实 MySQL `opendelivery` 已从 Flyway baseline 2 升级至 V3。E2E 验证 Toronto 自动路由至 `YYZ-01`，Charlottetown 无覆盖生成 Case，人工覆盖至 `YHZ-01` 后 Case 变为 `RESOLVED`。

## 后续

I03 将实现运营用户、角色、默认站点上下文与通用审计；跨站点履约命令必须拒绝。运营 Web 页面与更完整的规则编辑能力按主执行计划继续交付。

