# Driver App Localization Contract

## Integration Rules

The Android client repository is not present here. This is its required resource contract; the service supports `en-CA`, `fr-CA`, and `zh-CN`. The app sends its current `Accept-Language` on every request and calls `PUT /auth/locale` after a user change. Logic depends only on `biz_code`, state codes, and reason codes, never `biz_message`.

## Core Resource Keys

| Android key | Use |
|---|---|
| `auth_sign_in`, `auth_username`, `auth_password`, `auth_expired` | authentication/session |
| `task_expected_parcels`, `task_scanned_count`, `task_submit_scan` | expected list and submission |
| `scan_success`, `scan_wrong_task`, `scan_duplicate`, `scan_unknown`, `scan_damaged` | immediate scan result |
| `handover_waiting_approval`, `handover_approved` | approval and custody handover |
| `delivery_success`, `delivery_retry`, `delivery_failed_return` | delivery outcome |
| `pod_photo`, `pod_signature`, `pod_recipient`, `pod_note` | proof of delivery |
| `return_to_station`, `return_scan`, `return_submitted` | failed-return workflow |
| `common_confirm`, `common_cancel`, `common_retry`, `common_offline` | shared actions |

`scan_wrong_task` must explicitly state that the parcel is not part of this driver's task, will not enter the scan list, and must go to operations or the correct driver. Canonical states such as `OUT_FOR_DELIVERY` remain unchanged and map to local labels.

## Acceptance Gate

The Android repository must maintain `values/strings.xml`, `values-fr-rCA/strings.xml`, and `values-zh-rCN/strings.xml`, with CI key-set equality. Tests cover switching, offline restart, text truncation, scan audio/haptic feedback, and server fallback. Owner-scan enforcement, idempotency, and state transitions must remain locale invariant.
