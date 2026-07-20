# Iteration 01: Persistence Foundation

## Goal

Convert the operational closure model into executable MySQL schema and replace production in-memory paths for driver, token, parcel, scan, task, delivery, events, and callbacks while preserving driver API compatibility.

## Deliverables

State machines, data dictionary, Flyway V1, JDBC repositories and transactional services, upstream canonical ingestion, station receipt, wave/task creation, handover scan/review, delivery attempt/POD, outbox worker, security isolation, unit/application/E2E tests, and deployment/release documentation.

## Definition of Done

Migration succeeds on an empty schema; application data survives restart; invalid state and cross-driver access are rejected; automated tests and real MySQL E2E pass; secrets remain external; and operations, testing, deployment, rollback, and bilingual documents are aligned.

