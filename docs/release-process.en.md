# Release and Rollback Process

Before release, freeze requirements/migrations, complete review and tests, back up MySQL, dry-run migrations, validate secrets/storage/callback/alerts, and inspect open cases and outbox backlog.

Apply backward-compatible schema first, then roll the application. Start with one test partner and station; observe authentication, ingestion rejection, task reads, transitions, pool health, callback latency, and 5xx before expansion.

Rollback the application to the previous compatible build. Database rollback is forward-fix by default; destructive automatic DROP is prohibited. Pause intake without losing payload or cursor, then replay idempotently. Never delete POD or domain events during rollback.

Stop promotion on inconsistency, duplicate completion, cross-driver exposure, migration error, sustained callback failure, or elevated 5xx. Record timeline, impact, response, and follow-up.

