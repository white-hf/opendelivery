# I04 多城市入站交付总结

## 交付范围

I04 已完成可实际操作的 Manifest 入站闭环。运营人员只能查看当前站点 Manifest，可分页查询摘要和查看 Item 详情；开始收货后记录实际到达时间。扫码以 `deviceEventId` 幂等，分类正常、破损、多货、错站和重复。

V5 新增 `inbound_scan_event`，并为 Case 增加 Manifest/Item 关联。正常和破损实收在同一事务中更新 Parcel、库存站点、custody、状态事件与 outbox；破损、多货、错站自动建 Case。关闭时未扫描预期件转为少货并建 Case。存在开放差异时默认拒绝关闭，只有解决差异或明确允许 Case 结转后才能关单。

## 验证

真实 MySQL 已升级至 Flyway V5。E2E 完成两件预期包裹、一件多货：正常件入库、破损件隔离并建 Case、多货建 Case、重复设备事件不重复写入、开放 Case 阻止关闭、人工决策后关闭成功。另验证状态事件、custody 和 outbox 各写入一次，以及 Halifax 用户读取 Toronto Manifest 返回 HTTP 403。

`mvn clean verify` 全模块通过。

## 后续

I05 将把当前一次性发布波次接口拆为草稿、候选库存、发布与撤回命令，并实现司机装车扫描和主管差异批准。

