# Driver API Current-State Audit

The delivered Driver App contract is `/auth/**` and `/delivery/**`. Authentication, own tasks, load scanning, handover, delivery failure, and `/delivery/retry` already have JDBC paths. The later `/driver/v1/**` surface is a redundant parallel contract and must be removed.

D01–D03 harden the existing contract: task authorization, scan idempotency and snapshots, POD evidence, storage, and concurrency. Drivers physically bring failed parcels back; Operations records warehouse receipt and custody transfer. Applied V1–V12 migrations remain immutable.
