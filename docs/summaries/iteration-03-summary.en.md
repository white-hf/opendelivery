# I03 Operations Identity and Readiness Summary

## Delivered Scope

I03's backend loop is complete. V4 adds operator users, roles, assignments, hashed-token sessions, and general operation audit. Operations now supports login, refresh, logout, `/me`, user list/create, station context, and shift Readiness. Access tokens last two hours; refresh tokens last 14 days and rotate immediately. Only SHA-256 token digests are stored.

MOV uses simplified RBAC: `ADMIN` can switch active stations and manage users; `SUPERVISOR` can perform high-risk station work; `INBOUND` and `DISPATCHER` access their resource families. Normal users are fixed to their default station. A conflicting header, URL, or business resource returns HTTP 403. `LEGACY_OPS_API_KEY_ENABLED` supports migration and should be disabled after Operations Web cutover.

## Operations and Audit Closure

Readiness reports active drivers, open Manifests, open Cases, unrouted Waybills, and station readiness. Mutations audit operator, station, action, resource, outcome, and request ID without passwords or tokens. Permission denials write `DENIED` audit entries.

## Verification

The real MySQL schema upgraded to Flyway V4. E2E passed login, `/me`, Readiness, missing-token 401, role 403, normal-user cross-station 403, admin station switching, one-time refresh rotation, reused-refresh 401, and successful mutation audit. The Maven reactor remains green.

## Next

I04 adds Manifest list/detail, receiving start, discrepancy classification, linked Cases, and close gates. Operations Web login and shell remain on the MOV plan; I03 delivers the backend contract they consume.
