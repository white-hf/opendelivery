# Two-Product Driver and Operations Delivery Plan

## Product boundaries

The system is planned, accepted, and released as two products:

1. **Driver App and Driver Backend API** serves the authenticated driver: own tasks, load scan, pickup submission, delivery, POD, failure, return scan, and offline synchronization. While the Android repository is external, this repository must still deliver and test the complete Driver API contract, state machines, and simulated-client E2E.
2. **Operations Web and Operations API** serves station operators, dispatchers, and supervisors: order readiness, area/capacity planning, physical arrival, scan observation, handover approval, delivery monitoring, exception decisions, and day close. Operations never scans or delivers on behalf of a driver.

Both products share parcel, task, scan, custody, case, and state-machine facts, but never client permissions. Driver identity comes only from the driver token; Operations commands require role and station context.

## Driver-product iterations

| Iteration | Outcome | Scope | Evidence |
|---|---|---|---|
| D01 Own tasks | Driver sees only own published tasks and expected pieces | summary/detail, ownership, state gates, three locales | own/other/revoked API tests |
| D02 Own scan | Driver scans and submits pickup | open/resume LOAD session; expected/wrong/unknown/duplicate/damaged; offline idempotency; submit snapshot | wrong-driver events never become valid or change custody |
| D03 Delivery | Driver records delivery or failure | depart, worklist, POD, reasons, retry, offline sync | ownership, POD completeness, idempotency, state tests |
| D04 Return | Undelivered pieces are scanned back | RETURN session, scan, submit, closeout | outstanding pieces visible; submission does not itself return station custody |

## Operations-product iterations

| Iteration | Outcome | Scope | Evidence |
|---|---|---|---|
| O03 Arrival closure | Trips/units auto-link to upstream pieces and tasks | upstream unit identifiers, cross-driver coverage, three-city fixtures | multi-driver units, multi-unit tasks, aggregate-detail parity |
| O04 Scan supervision | Operations observes rather than performs driver scans | wave/task/driver expected, valid, missing, wrong, damaged, unsubmitted; drill-down | same Driver Scan Event source; station isolation |
| O05 Handover approval | Supervisor approves responsibility transfer | wave gates, discrepancy decisions, reopen, safe reassignment, atomic custody | one concurrent approval succeeds; no valid scan means no release |
| O06 Delivery/return supervision | Operations monitors in-flight, failures, and returns | map/list, failure queue, RETURN approval, replanning | status/custody conservation |
| O07 Day close | Each station reconciles and signs | inventory, driver custody, tasks, cases, callbacks, carry-over | hard blockers prevent close; results recompute |
| O08 Configuration/exceptions | Pilot master data and compensation loops | driver/station configuration, case owner/SLA, Outbox replay | audit, RBAC, retry/dead-letter tests |

## Joint release gate

Joint E2E runs upstream order → Operations planning → arrival → **D02 driver scan/submit** → **O05 approval** → D03 delivery/D04 return → O06 approval/O07 close. Passing each product alone is insufficient without cross-product contract and custody-conservation evidence.

The priority dependency chain is `O03 → D01 → D02 → O04 → O05 → D03 → D04 → O06 → O07 → joint E2E`. O04 may proceed after D02 stabilizes. No driver scan-input UI will be built in Operations.
