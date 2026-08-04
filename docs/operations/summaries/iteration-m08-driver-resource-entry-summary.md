# M08 交付总结：司机建议文档入口

状态：COMPLETED

已在登录后的顶部用户操作区增加“司机建议文档”菜单，提供中文和英文两个静态 HTML 文档链接，并在新标签页打开。默认地址为：

- `/docs/driver-suggestion/index.zh-CN.html`
- `/docs/driver-suggestion/index.en.html`

地址可通过 `VITE_DRIVER_SUGGESTION_DOC_ZH_URL` 和 `VITE_DRIVER_SUGGESTION_DOC_EN_URL` 覆盖。已验证前端类型检查和生产构建通过。
