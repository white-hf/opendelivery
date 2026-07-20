# I06 失败、回站与重派交付总结

I06 已完成失败规则、Driver Attempt V2、任务 closeout、RETURN Session、站点审批和同站重派闭环。失败原因定义照片/备注要求、下一动作和最大尝试次数；Attempt 以司机和幂等键去重。地址错误创建 Case，不自动改变站点。

RETURN Session 仅允许任务司机创建、扫码和提交。批准前包裹仍由司机持有；主管在本站批准后才写 custody 并选择 `REDISPATCH` 或 `RETURN_UPSTREAM`。重派件回到 `READY_FOR_DISPATCH/STATION`，但开放地址 Case 仍阻断候选库存。

真实 MySQL 已升级至 V7。E2E 验证地址错误证据、重复 Attempt、地址 Case、本人回站扫描/提交、主管批准、custody 回站与重派阻断。Maven 全模块测试通过。

