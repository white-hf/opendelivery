# Driver Product Documentation Hub

This is the current entry point for the Driver App and Driver API team. The driver product owns authenticated-driver tasks, load scan, delivery/POD, failure, and return; it does not own Operations planning, supervision, or approval UI.

## Current documents

| Type | Current document |
|---|---|
| Product | [Driver Platform PRD](../prd/last-mile-driver-platform.en.md) |
| Implementation audit | [Driver API code/database status](summaries/driver-api-current-state-audit.en.md) |
| Current iterations | [D01-D04 database closure plan](iterations/driver-api-d01-d04-plan.en.md) |
| API | [System API contracts, driver sections](../design/api-contracts.en.md) |
| Architecture/data | [System design](../design/last-mile-system-design.en.md), [data model](../design/data-model.en.md), [state machines](../design/state-machines-and-operations.en.md) |
| App contract | [Driver App localization contract](../design/driver-app-localization-contract.en.md) |
| Historical delivery | [I05 load](../summaries/iteration-05-summary.en.md), [I06 failure/return](../summaries/iteration-06-summary.en.md) |

## Team rule

New driver documents belong under `prd/`, `design/`, `iterations/`, `summaries/`, `testing/`, or `runbooks/`. Delivery follows product/contract update → iteration review → implementation → automated validation → execution summary. Operations must never invoke Driver API commands on behalf of a driver.
