# I03 运营身份与班前准备交付总结

## 交付范围

I03 后端闭环已完成。V4 新增运营用户、角色、用户角色、哈希 Token 会话和通用操作审计表。运营端支持登录、刷新、退出、`/me`、用户查询/创建、站点上下文和班前 Readiness。Access Token 有效期 2 小时，Refresh Token 有效期 14 天且每次刷新立即轮换；数据库只保存 SHA-256 Token 摘要。

权限采用 MOV 所需的简化 RBAC：`ADMIN` 可切换活动站点和管理用户；`SUPERVISOR` 执行站点高风险操作；`INBOUND` 与 `DISPATCHER` 仅能访问对应资源。普通用户固定默认站点，Header、URL 或业务对象指向其他站点时返回 HTTP 403。迁移期开关 `LEGACY_OPS_API_KEY_ENABLED` 默认开启，生产切换运营 Web 后应关闭。

## 运营与审计闭环

Readiness 返回当前站点活动司机、未关闭 Manifest、开放 Case、未路由 Waybill 和可运营标志。运营写请求记录用户、站点、动作、资源、结果和 Request ID；不记录密码或 Token。权限拒绝写 `DENIED` 审计。

## 验证

真实 MySQL 已升级至 Flyway V4。E2E 通过登录、`/me`、Readiness、无 Token 401、角色 403、普通用户跨站 403、管理员切站、Refresh 单次轮换、旧 Refresh 401 和成功写操作审计。Maven 全模块测试持续通过。

## 后续

I04 将补齐 Manifest 列表/详情、开始收货、差异分类、Case 关联和关闭门禁。运营 Web 登录与布局仍按 MOV 计划实现，当前 I03 交付为可供该 Web 使用的后端契约。

