# Resilience and Recovery

- Set connection, read, and overall timeouts for every external call; a timeout is an observable business outcome, not infinite waiting.
- Retry only known-transient failures with exponential backoff, jitter, and a maximum count; non-idempotent writes require an idempotency key first.
- Make upstream replays, network retries, and duplicate message consumption idempotent using business keys and conditional state writes.
- Use queues, an outbox, or replayable operator tasks when a dependency is unavailable; never swallow failures without a record.
- Partial batch success must expose per-item results, compensation actions, and a retry path.
- Roll out migrations with compatibility reads/writes, feature flags, or a staged release; define rollback and data-recovery conditions.
- Record request id, business id, retry count, latency, and final outcome with sensitive data redacted.
