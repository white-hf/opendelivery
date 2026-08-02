# M07 Delivery Summary: Development Defaults

Status: COMPLETED

In development mode, the login page now pre-fills `opsadmin / password123` so the operator can click Sign in directly. When the URL has no valid service date, development mode defaults to `2026-07-13`. An explicit URL date still wins; production builds use the current date and do not pre-fill credentials.

Validation: `pnpm run typecheck` and `pnpm run build` both pass.
