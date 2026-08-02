# M07: Development Experience Defaults

Status: COMPLETED

## Background

During local development, operators currently re-enter the demo credentials and manually switch to 2026-07-13, the date with the richest fixture data. This convenience must remain development-only so test credentials never appear in production builds.

## Goals

- Pre-fill the existing demo account `opsadmin / password123` in Vite development mode so the operator can click Sign in immediately.
- Use `2026-07-13` as the service date when development mode has no valid `date` in the URL.
- Preserve an explicitly supplied valid URL date; production builds keep the current date fallback and an empty login form.

## Acceptance Criteria

1. With `pnpm dev`, the login fields contain values and Sign in submits without manual entry.
2. After login without a date parameter, the page and API requests use `2026-07-13`.
3. A valid URL date such as `#page=orders&date=2026-07-20` is not overwritten.
4. `pnpm run typecheck` and `pnpm run build` pass.

## Implementation Result

Development-mode credential prefill and the 2026-07-13 service-date fallback are complete; production builds do not receive demo credentials.
