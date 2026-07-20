# 运营 Web 前端技术设计

## 1. 技术栈

| 层 | 选型 | 用途与理由 |
|---|---|---|
| 语言/视图 | React + TypeScript（strict） | 组件化运营界面、类型化契约；团队生态成熟 |
| 构建 | Vite | SPA 开发、构建和环境配置简单快速 |
| 包管理 | pnpm，锁文件提交 | 可重复安装、节省本地/CI 存储 |
| UI | Ant Design + design tokens | 表格、表单、抽屉、步骤、反馈适合高密度后台 |
| 路由 | React Router | 路由、嵌套布局、权限入口和 URL 筛选 |
| 服务端状态 | TanStack Query | 查询缓存、去重、失效、重试和后台刷新 |
| 表单/校验 | React Hook Form + Zod | 表单性能、共享运行时 schema 和错误映射 |
| HTTP | 原生 `fetch` 封装 | 减少依赖；统一鉴权、request ID、错误和超时 |
| 日期 | Day.js | 站点时区显示和轻量格式化 |
| 国际化 | i18next + react-i18next | 中文首发、英文 key 预留 |
| 单元/组件测试 | Vitest + React Testing Library + MSW | 用户行为测试与稳定 API mock |
| E2E | Playwright | Chromium 主门禁，Firefox/WebKit 冒烟 |
| 质量 | ESLint + typescript-eslint + Prettier | 静态检查和一致格式 |
| 交付 | Nginx 静态站点或同域 CDN | SPA fallback、缓存、安全 Header；API 反向代理 |

依赖在创建前端工程时锁定当时稳定兼容版本，并由 Dependabot/Renovate 小批量升级；本文不把“latest”写死为生产规则。

## 2. 明确不采用

- MOV 不需要 SSR/Next.js：运营系统登录后使用、SEO 无价值。
- 不采用微前端：只有一个前端团队和一个部署单元。
- 不默认使用 Redux：服务端状态由 Query 管理，少量全局客户端状态使用 Context/Zustand 前需提出具体需求。
- 不自建组件库：只封装业务组件和 Ant Design token，避免设计系统先于产品。
- 不使用 WebSocket：MOV 以 30–60 秒轮询满足运营；确有实时 SLA 后再评估 SSE。

## 3. 建议目录

```text
easydelivery-operations-web/
├── src/
│   ├── app/              # bootstrap, router, providers
│   ├── api/              # fetch client, generated/manual contracts
│   ├── auth/             # session, guards, permissions
│   ├── features/         # dashboard, inbound, dispatch, cases...
│   ├── components/       # shared business UI
│   ├── layouts/          # operations shell
│   ├── i18n/
│   ├── styles/
│   ├── test/
│   └── main.tsx
├── e2e/
├── public/
├── package.json
└── vite.config.ts
```

每个 feature 内含 `pages/components/api/hooks/schema/__tests__`，不能从另一个 feature 的内部文件深层导入；跨域复用先走公开 `index.ts`。

## 4. 前后端契约

后端维护 OpenAPI 3.1，CI 生成/校验 TypeScript 类型；在 OpenAPI 完成前使用手写 Zod schema 校验关键响应，不能用 `any`。统一 API client：

- 自动加入 Bearer、`X-Request-Id`、站点上下文和写请求 `Idempotency-Key`。
- 15 秒普通请求超时；文件上传单独配置；AbortController 取消过期查询。
- 401 只允许一个 refresh promise，成功后重放一次；失败统一退出。
- 403 不刷新 Token；409 解析当前 version；429 尊重 `Retry-After`。
- 响应同时检查 HTTP status 和 `biz_code`，兼容旧接口 HTTP 200 业务失败。

## 5. 状态管理

- Query key 统一为 `[domain, stationId, filters/id]`；切站点清除敏感缓存。
- 列表 stale time 30 秒；字典/角色 5 分钟；详情写后精确 invalidate。
- 登录身份只存内存；Refresh Token 优先 Secure、HttpOnly、SameSite cookie。若后端暂不能 cookie，迁移期使用 sessionStorage，禁止 localStorage 长期 Token，并记录风险。
- URL 管理筛选、排序、cursor 前的业务条件；Modal 开关等瞬时 UI 状态留在组件。
- 不对状态命令做乐观更新；等待服务端确认，避免虚假 custody/签字。

## 6. 权限与安全

路由和按钮权限用于体验，后端是最终授权点。前端 permission key 采用 `resource:action`，如 `manifest:close`。PII 组件默认遮罩；查看完整值调用审计 API。禁止 `dangerouslySetInnerHTML` 渲染上游文本；CSP 禁止任意脚本，启用 HSTS、`frame-ancestors 'none'`、`nosniff`、严格 referrer policy。Source map 仅上传受控错误平台，不公开部署。

POD 上传在客户端预检 MIME/大小/数量并显示进度，但服务端必须重复校验。错误日志不得包含请求 body、Token、POD 或 PII。

## 7. 业务组件

- `StationDateContext`：页头站点/营业日。
- `StatusTag`：文字+图标+颜色统一状态。
- `ServerTable`：cursor、URL 筛选、加载/空/错误。
- `ScannerInput`：聚焦、回车、去抖、声音和结果历史。
- `DiscrepancyPanel`：期望/实扫比对及主管决定。
- `ParcelTimeline`：合并状态、custody、attempt、case 和 callback。
- `HighRiskActionDialog`：影响摘要、原因、version 和二次确认。
- `PermissionGate`：隐藏/禁用动作，但不代替后端鉴权。

## 8. 性能与可观测性

目标基线：生产 gzip 后首屏 JS 尽量 ≤350 KB（不含按需页面 chunk），LCP p75 ≤2.5 秒、INP p75 ≤200 ms；以真实站点设备校准。路由级 lazy loading，Ant Design按构建工具 tree-shake；大表服务端分页，不把 10,000 件加载到浏览器。仅出现数百可见行渲染瓶颈时引入虚拟滚动。

前端错误报告包含 release、route、request ID、匿名 user/station ID 和网络类型，不含 PII。记录登录失败率、页面/API 错误、关键命令成功率和前端耗时；业务事实仍以服务端为准。

## 9. 测试与 CI

- 单元：权限、格式化、Zod schema、错误映射。
- 组件：筛选、表格、扫描、差异、确认框的用户行为。
- 契约：对 OpenAPI 示例和 MSW fixture 做 schema 校验。
- E2E：各角色登录、跨站点 403、入站、发布、交接、失败回站、Case、重放和日终。
- 可访问性：axe 自动检查 + 键盘人工走查。

PR 门禁：`pnpm lint`、`pnpm typecheck`、`pnpm test --run`、`pnpm build`；合并环境跑 Chromium E2E，发布候选跑三浏览器 smoke。不得用快照测试替代关键业务断言。

## 10. 部署与配置

构建产物不可包含密钥。仅允许 `VITE_API_BASE_URL` 等公开配置；推荐同域 `/api` 反向代理以简化 cookie/CORS。静态 hashed asset 一年 immutable，`index.html` no-cache。发布按版本目录保留上一个产物，可秒级切换回滚；前端新版本必须兼容当前及前一个后端契约。

## 11. 官方选型依据

- React TypeScript 指南：<https://react.dev/learn/typescript>
- React 从零构建应用（包含 Vite）：<https://react.dev/learn/build-a-react-app-from-scratch>
- Vite 指南：<https://vite.dev/guide/>
- Ant Design 组件：<https://ant.design/components/overview/>
- TanStack Query React：<https://tanstack.com/query/latest/docs/framework/react/overview>
- Playwright：<https://playwright.dev/docs/intro>
