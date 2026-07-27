# Architecture Principles

## Layers and dependency direction

```text
API/Controller → Application Use Case → Domain → Persistence
                                  ↘ Query Service → Query Repository/Read Adapter
```

- Controllers handle protocol, auth context, input validation, and response envelopes only.
- The application layer orchestrates use cases, transactions, authorization, and cross-domain coordination.
- The domain owns state machines, invariants, policies, and domain events without SQL, HTTP, or UI dependencies.
- Command persistence uses entities and repositories for lifecycle changes.
- Query persistence returns dedicated DTOs; complex queries are centralized in query repositories.

## Boundaries and modularity

- Organize modules by business capability, not by dumping every file into technical folders.
- Cross-module dependencies use explicit interfaces, events, or DTOs; modules do not read another module's tables or internal entities directly.
- External systems are translated into the internal canonical model through an anti-corruption layer.
- The database stores facts; it is not a substitute for shared business logic.

## Decision method

Record important technical choices with context, options, decision, trade-offs, impact, migration, and rollback. Define boundaries and non-goals before choosing frameworks; do not sacrifice verifiability or operational stability for uniformity.
