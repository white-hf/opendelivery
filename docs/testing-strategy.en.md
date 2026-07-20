# Testing Strategy

- Unit tests cover JWT, password, token lifecycle, mappings, idempotency, and state rules.
- MockMvc with `memory` validates authentication, response contracts, missing token, and cross-driver isolation.
- Real-MySQL E2E performs upstream ingestion, manifest receipt, wave creation, load scan/review, delivery, and persistence assertions, then cleans its data.
- Migration tests start from a temporary empty schema and verify Flyway history and object counts.
- Regression preserves Android API fields, business codes, and multipart behavior.

Release requires zero automated failures, successful E2E and migration dry-run, no committed secrets, reviewed backup/rollback, and explicit ownership for any unautomated risk. Required failure coverage includes duplicate events, bad scans, illegal state, missing evidence, dependency failure, callback retry/dead-letter, manifest discrepancy, and daily imbalance.

