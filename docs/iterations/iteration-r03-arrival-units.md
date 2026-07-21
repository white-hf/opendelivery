# R03 到仓实物迭代

## 可运营结果

运营在司机领取前登记“哪辆车、何时、带来哪些板/笼”，并观察每个实物单元覆盖的派送批次、任务和司机。系统不假定一个板只属于一个司机，也不把到仓登记误作逐件接收或 custody 转移。

## 切片与顺序

| 切片 | 任务 | 验收证据 |
|---|---|---|
| R03-A Trip | 建立到仓 Trip，记录上游、车牌/封签、ETA/ATA、状态 | 同站唯一编号、跨站拒绝、状态机单测 |
| R03-B Handling Unit | Trip 下新增板/笼/袋，扫描或输入外部标签 | 幂等标签、1-N 任务/司机覆盖 fixture |
| R03-C 关联观察 | 关联包裹或从计划推导覆盖关系，展示预计/已扫/异常 | 聚合与明细一致、未知包裹不污染库存 |
| R03-D 运营台 | “到仓接收”页面提供 Trip 列表、到达登记、实物单元抽屉 | 三语言浏览器回归、三站切换无数据残留 |

## 状态与边界

Trip：`EXPECTED → ARRIVED → UNLOADING → READY_FOR_SCAN → CLOSED`，异常可 `CANCELLED`。Handling Unit：`EXPECTED → ARRIVED → OPENED → CLEARED`。到达只证明实物容器进入站点；包裹是否归司机仍由 R04 本人扫描判定，司机 custody 仅在 R05 运营审批后转移。

## Definition of Done

Flyway 可升级；所有查询强制站点上下文；创建、到达、开板、关闭有审计；重复请求幂等；API、字段与状态补入文档；Java 单测、真实 MySQL、Vitest、Playwright 和三站隔离 Gate 全绿。
