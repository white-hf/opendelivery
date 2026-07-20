# 司机 App 多语言契约

## 接入规则

Android 客户端仓库不在本代码库中。本文件是司机 App 必须实现的资源契约；服务端已经支持 `en-CA`、`fr-CA`、`zh-CN`。App 每次 API 请求发送当前 `Accept-Language`，用户切换后调用 `PUT /auth/locale`。业务判断只使用 `biz_code`、状态码和原因码，不解析 `biz_message`。

## 核心资源键

| Android key | 场景 |
|---|---|
| `auth_sign_in`, `auth_username`, `auth_password`, `auth_expired` | 登录与会话 |
| `task_expected_parcels`, `task_scanned_count`, `task_submit_scan` | 待扫列表与提交 |
| `scan_success`, `scan_wrong_task`, `scan_duplicate`, `scan_unknown`, `scan_damaged` | 扫描即时反馈 |
| `handover_waiting_approval`, `handover_approved` | 运营审批与 custody 交接 |
| `delivery_success`, `delivery_retry`, `delivery_failed_return` | 配送结果 |
| `pod_photo`, `pod_signature`, `pod_recipient`, `pod_note` | POD |
| `return_to_station`, `return_scan`, `return_submitted` | 失败回仓 |
| `common_confirm`, `common_cancel`, `common_retry`, `common_offline` | 通用操作 |

`scan_wrong_task` 必须明确显示“不是你的任务包裹，不会加入扫描列表，请交给运营或对应司机”，三种语言语义必须一致。状态编码如 `OUT_FOR_DELIVERY` 保持不变，客户端映射成本地标签。

## 验收门禁

Android 仓库需维护 `values/strings.xml`、`values-fr-rCA/strings.xml`、`values-zh-rCN/strings.xml`，CI 比较键集合；验证切换语言、离线重启、字体截断、扫码语音/震动提示、服务端缺失翻译回退。司机本人扫描限制、幂等键和状态机不得因 locale 改变。
