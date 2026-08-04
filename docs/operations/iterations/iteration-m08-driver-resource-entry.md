# M08：司机建议文档资源入口

状态：COMPLETED

## 背景

司机建议文档与 Last Mile 运营业务无关，只需要在现有网站提供一个方便产品经理访问的入口。文档已作为网站静态资源放置在 `public/docs/driver-suggestion/`。

## 方案

- 顶部用户操作区增加独立的“司机建议文档 / Driver suggestion”菜单。
- 菜单提供中文和 English 两个静态 HTML 链接，并在新标签页打开。
- URL 支持 Vite 环境变量覆盖，同时提供当前仓库静态资源路径作为默认值。
- 不新增业务导航页面、后端 API、数据库表或权限模型。

## 验收标准

1. 登录后可从顶部操作区看到文档入口。
2. 中文链接打开 `/docs/driver-suggestion/index.zh-CN.html`，英文链接打开 `/docs/driver-suggestion/index.en.html`。
3. 链接使用新标签页和 `noopener,noreferrer`。
4. `pnpm run typecheck` 与 `pnpm run build` 通过。

## 实施结果

已完成顶部资源菜单、双语静态文档链接和 Vite URL 配置覆盖；两个 HTML 文件已随构建复制到 `dist/docs/driver-suggestion/`。
