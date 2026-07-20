# Database Bootstrap Runbook

Create `opendelivery` with `utf8mb4_0900_ai_ci` and grant the application account access to that schema. Do not store passwords in scripts or source control. Verify schema charset/collation through `information_schema.SCHEMATA`.

Business objects are owned by Flyway V1. Database deletion is intentionally not automated; it requires explicit approval and confirmation that no required data exists.

