# Driver API Code and Database Audit

## Conclusion

The Driver backend is not a blank implementation. The original App contract covers authentication, tasks, load scan, delivery, failure, and return. MySQL V1-V7 contains the core tables, and `JdbcDriverRepository`, `JdbcTokenStore`, `JdbcDeliveryOperations`, and `FailureReturnService` use the real data layer. Its current posture is “primary flow exists; contracts are split; gates and tests are incomplete.” D01-D04 should harden and converge existing work rather than blindly rebuild it.

## Implemented capability

| Capability | Code/API | Persistence | Assessment |
|---|---|---|---|
| Authentication | `/auth/register/login/refresh/logout/locale` | hashed sessions in `driver/auth_session` | usable baseline; public registration policy unresolved |
| Own work | `/delivery/parcels/tasks` and `/delivering` | JDBC task/item reads | parcel lists exist; task-level V1 contract missing |
| LOAD session | create/resume/scan/report/submit | `scan_session/scan_event` | primary flow exists; classification/snapshot semantics need repair |
| Handover | driver submits; Operations approves | transactional parcel/task/custody/status/outbox | implemented; joint O05 acceptance required |
| Delivery/POD | legacy multipart `/delivery`; new attempt API | `delivery_attempt/proof_of_delivery` | duplicate contract families must converge |
| Failure/retry | rules, limits, evidence flags, address case | V7 rule/attempt columns | logic exists; uploaded evidence is not atomically bound |
| Return | owned RETURN session/scan/submit | session/events and approval custody | primary flow exists; classification/progress/concurrency gaps remain |
| Localization | three locales and driver preference | `driver.preferred_locale` | baseline complete |

## Required development

1. Converge legacy `/delivery/**` compatibility and canonical `/driver/v1/**`; never authorize from a request `driver_id`.
2. D01 adds task summaries/details, expected pieces, state/version, and explicit task selection; the current batch creation silently selects the latest task.
3. D02 aligns scan facts: add damaged semantics, check device idempotency before unknown lookup, stabilize duplicate classification, define loaded as a pre-approval projection, and persist an immutable submit snapshot.
4. D03 makes POD evidence real: controlled upload, type/size/hash checks, attempt-to-POD gate, storage abstraction, and compensation. The legacy endpoint can currently mark delivered before required POD exists.
5. D04 aligns unknown/duplicate/wrong-task behavior, computes missing pieces, snapshots submit, supports safe reject/reopen, and handles offline ordering.
6. Add locks/version conflict tests and driver/device/idempotency audit without logging address or POD content.
7. Expand beyond the four current Driver API application tests into unit, real-MySQL integration, simulated-App contract, and three-city joint E2E suites.

## Reusable but subject to re-acceptance

V1-V7 schema, active-task uniqueness, hashed token rotation, ownership checks, scan/attempt idempotency, custody/status/outbox writes, and failure/return services are reusable. Re-acceptance is not a rewrite; only capabilities passing the new D01-D04 DoD are marked complete.
