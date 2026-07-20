# 多语言与本地化设计

## 目标与范围

运营 Web、司机 App API 和运营 API 首期支持 `en-CA`、`fr-CA`、`zh-CN`。业务状态、错误码、数据库关联和上游原始数据保持规范编码，不因语言变化；仅展示文案、校验提示和通知模板本地化。

## 语言解析与接口契约

语言优先级为 `Accept-Language`、账户 `preferred_locale`、站点 `default_locale`、`en-CA`。API 始终返回稳定 `biz_code`；`biz_message` 按请求语言生成，客户端不得根据消息文本控制流程。暂不支持的语言回退到英文。日期存 UTC，展示时同时使用站点时区与用户 locale。

## 技术设计

- Spring `MessageSource` 管理 `messages_en_CA/fr_CA/zh_CN.properties`，异常处理器覆盖司机和运营接口。
- `operator_user.preferred_locale`、`driver.preferred_locale` 保存个人选择，`station.default_locale` 保存站点默认值。
- 运营 Web 使用 `i18next/react-i18next`；Android 使用本地 `strings.xml`，保证弱网核心界面可翻译。
- 上游 payload 和客户输入原样保存；规范地址、状态及原因码另字段保存，不机器翻译法律或客户原文。
- 通知模板后续按 `(template_code, locale, version)` 版本化，发送记录保存实际模板版本。

## 安全与测试

不从姓名、国籍或地址推断语言。日志记录业务码而非翻译文本。自动化覆盖三种语言、语言质量权重、非法 locale、缺键回退、切换语言不改变状态机，以及翻译键集合一致性。
