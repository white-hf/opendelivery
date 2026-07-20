# MySQL Deployment Runbook

Grant the application account only required access to `opendelivery.*`. Provide all credentials through approved secrets and mount persistent POD storage. Never execute the development seed in production.

For first deployment: create the UTF-8 schema, grant and test connectivity, take a backup, start one application instance to run Flyway, inspect `flyway_schema_history` and table count, configure real partners/stations/operators/drivers, then open traffic.

Monitor application/Flyway/Hikari health, oldest outbox event, dead letters, disk, and POD volume. A database outage must stop writes; never switch production to memory. Recovery verification checks parcel projection against the event tail, tasks, custody, POD metadata, and callback ACK—not merely table existence.

