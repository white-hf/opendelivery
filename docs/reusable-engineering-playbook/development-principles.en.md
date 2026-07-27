# Development Principles

- Update requirements, design, and iteration documents before coding; every behavior change has acceptance criteria.
- Use ORM entities and repositories for commands, projections/JPQL for simple reads, and dedicated query repositories for aggregates, spatial queries, and large lists.
- Do not put SQL in controllers or domain entities; every retained SQL statement documents its escape-hatch rationale.
- Design every write for idempotency, optimistic concurrency, authorization, auditability, and safe retry.
- Keep API error codes, envelopes, pagination, and compatibility rules stable; version or migrate breaking changes.
- Validate and normalize external input before it enters the domain; do not spread vendor fields into the core model.
- Load configuration and secrets from the environment or a secret manager; never log tokens, passwords, or sensitive payloads.
