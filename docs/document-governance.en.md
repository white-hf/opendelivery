# Two-Product Documentation and Review Workflow

## Classification

- `docs/driver/`: Driver App/API product-specific product, design, iteration, test, runbook, and summary material.
- `docs/operations/`: Operations Web/API-specific material.
- `docs/shared/`: index for shared state, data, integration, and joint gates.
- Existing `docs/prd|design|iterations|summaries` remain historical/shared libraries. Product hubs identify authority; old files are not bulk-moved because that would break links and traceability.

## Mandatory delivery sequence

1. **Document**: update the product PRD first; update design/API/data documents when contracts, states, tables, or cross-product events change.
2. **Review**: iteration scope, non-goals, dependencies, migration, compatibility, risk, tests, and DoD move from `DRAFT` to `REVIEWED`.
3. **Implement**: code only reviewed scope. Requirement changes return to documentation before implementation expands.
4. **Validate**: unit → real-MySQL integration → API/client contract → browser or simulated App → joint product E2E.
5. **Summarize/release**: record actual work, commands, measures, residual risk, and rollback. Mark `COMPLETE` only after its gate passes.

## Review checklist

- Is product ownership and the other product's read/command boundary explicit?
- Does the API define inputs, outputs, errors, authorization, idempotency, and versioning?
- Do state and custody conserve responsibility without client-supplied identity or arbitrary target state?
- Is migration append-only with indexes, capacity, and history strategy?
- Are three-city isolation, locales, 401/403/409, duplicate, concurrency, and recovery covered?
- Does the joint flow state whether Driver or Operations commands and how the other observes/approves?

Small defects may use a concise review record but cannot skip document → review → implementation → validation. Emergency fixes add the decision and regression evidence in the same change.
