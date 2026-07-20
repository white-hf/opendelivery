# I05 调度与装车交付总结

I05 已完成候选库存、Wave 草稿、发布、未扫描撤回和司机装车交接闭环。候选查询按当前站点过滤，并排除未路由、非站点 custody、非可派状态和存在开放 Case 的包裹。草稿创建时锁定并校验每件；数据库活动槽约束防止同件进入两个活动任务。发布前再次校验，避免草稿期间库存变化。

司机 LOAD Session 只允许本人创建、扫码、查看报告和提交。司机提交值只能是 `SUBMITTED`，直接 `APPROVED` 返回 401。运营主管只能批准本站已提交 Session。批准前 Parcel 保持 `ASSIGNED/STATION`；批准后原子变为 `OUT_FOR_DELIVERY/DRIVER`，同时更新 Task Item/Task、逐件写 custody、状态事件与 outbox。未发生扫码的已发布 Wave 可撤回并恢复 `READY_FOR_DISPATCH`。

真实 MySQL 已升级至 V6。E2E 验证两件发布、司机本人扫描、司机自批拒绝、主管批准、两条 custody/状态事件，以及另一 Wave 撤回后库存恢复。Maven 全模块测试通过。

下一迭代 I06 实现失败原因、派送失败、司机回站 Session、退件审批和重派。

