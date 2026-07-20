# Developer Workflow

Update artifacts in this order: PRD, system design, iteration plan, code, test evidence, and execution summary. Evolve schema only through a new Flyway migration; never edit an applied migration.

Use Java 17 and bundled Maven. Supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `UPSTREAM_API_KEY`, and `OPERATIONS_API_KEY` externally. Development reference data is opt-in through `scripts/db/002_development_seed.sql`.

Run the delivery gate:

```bash
./run.sh test
./run.sh build
DB_PASSWORD='<secret>' scripts/mysql-e2e-test.sh
```

Verify empty-schema migration, API compatibility, rejected illegal transitions, test cleanup, and synchronized Chinese/English docs. MySQL is the production default; `memory` is isolated-test only.

