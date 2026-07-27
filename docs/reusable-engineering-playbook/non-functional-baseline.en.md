# Non-functional Baseline

- Quantify throughput, p95/p99 latency, concurrency, data volume, acceptable staleness, and resource limits.
- Use index left-prefix rules, pagination, and necessary covering indexes; never load an unbounded table.
- Archive or partition historical data according to retention policy; design read and write models for their access patterns.
- Track request success, latency, connection pools, slow queries, queue backlog, retries, and business failure rates.
- Use structured logs with request and business ids; propagate traces across APIs, messages, and database boundaries.
- Separate configuration by environment; keep secrets out of source control and grant least-privilege access.
- Define backups, RPO, RTO, and recovery-drill frequency.
