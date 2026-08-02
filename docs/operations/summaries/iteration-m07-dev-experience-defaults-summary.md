# M07 交付总结：开发体验默认值

状态：COMPLETED

开发模式登录页现在预填 `opsadmin / password123`，可直接点击登录；当 URL 没有合法营业日时，开发模式默认使用 `2026-07-13`。URL 明确指定的日期优先，生产构建仍使用当前日期且不预填账号密码。

验证：`pnpm run typecheck`、`pnpm run build` 均通过。
