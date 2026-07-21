# Driver API D01–D03 Database Closure Plan

> Status: `REVIEWED` (2026-07-21). `/auth/**` and `/delivery/**` are the delivered Driver App contract; there is no `/driver/v1/**` migration.

Preserve existing Android paths, fields, and responses. D01 hardens own-task selection and state authorization; D02 hardens `/delivery/scan/**` classification, device idempotency, snapshots, and offline recovery; D03 hardens `/delivery`, `/delivery/retry`, POD evidence, storage, idempotency, and concurrency.

Warehouse return is not a Driver API iteration. The driver physically returns failed parcels; Operations records `DELIVERY_FAILED → RETURNED_TO_STATION` and custody `DRIVER → STATION`.

Each slice requires reviewed documentation, 401/403/409 and concurrency coverage, three-language checks, real MySQL evidence, and an execution summary. Applied Flyway migrations remain immutable.
