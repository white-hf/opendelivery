# Testing and Validation

## Test pyramid

1. Unit tests for state machines, policies, parsing, validation, and pure functions.
2. Repository/integration tests against a real or containerized database for transactions, indexes, constraints, and projections.
3. API tests for authentication, authorization, error codes, idempotency, pagination, and compatibility.
4. E2E tests for cross-module user workflows; data must be namespaced, repeatable, and cleaned up.
5. Performance tests at target volume for slow queries, concurrent writes, queue backlog, and p95/p99 latency.

## Validation rules

- Add a reproducing test before fixing a defect.
- Record commands, environment, sample size, baseline failures, and residual risks.
- Compilation is not completion; require behavior evidence or real-data validation.
- Shared-environment tests must not assume an empty database and must clean up their own data.
