# Last-Mile Operational Closure Review

> This remains early review evidence. The current loop, feature solutions, and acceptance are consolidated in the [Integrated Product and Operations Specification](product-and-operations-specification.en.md) and [MOV Mandatory Capability Solutions](mandatory-capability-solutions.en.md).

## 1. Review Outcome

The primary shipment-to-delivery flow was not sufficient for daily operations. A closed system must maintain six linked but independent facts: parcel lifecycle, physical custody, inventory location, work task, exception case, and upstream callback. The review added master-data readiness, physical/system reconciliation, dispatch gates, bilateral handover, exception ownership, return closeout, and end-of-day sign-off.

## 2. Operating Day

### Opening and readiness

Administrators configure partner, station, service, reason codes, POD policy, callback contract, and SLA. Supervisors validate drivers, vehicles, shifts, connector health, yesterday's carryover, and expected arrivals. Invalid configuration or credentials block task release and create an action item.

### Pre-advice and station receipt

An upstream manifest represents expected freight. Operators scan unloading and compare expected versus actual. The system classifies missing, extra, duplicate, wrong-station, damaged, unreadable, and data-not-arrived cases. Supervisors accept or resolve discrepancies before closing the manifest.

### Sort and dispatch

The system proposes zone/route assignment using address, postal code, time window, product, and capacity. Dispatch resolves address and capacity exceptions, creates a wave, and freezes it. Only physically held, complete, non-cancelled, unassigned parcels can be published.

### Driver handover

Drivers check in, confirm equipment, and scan the expected task. The system shows expected, loaded, missing, extra, duplicate, and wrong-task items. Custody transfers from station to driver only after driver confirmation and supervisor-approved discrepancies.

### In-transit operations

Driver commands are device-idempotent and synchronize after offline use. Dispatch handles breakdowns, timeouts, reassignment, and route change. Customer-service cancellation or address change is applied automatically only when current custody and policy permit; otherwise an interception case is opened.

### Delivery, failure, and return

POD policy may require photo, signature, OTP, recipient relationship, time, and GPS. Failure requires configured evidence and produces retry, appointment, station return, or upstream return. High-value, refusal, damage, loss, identity failure, and retry-limit cases require human approval.

### Driver closeout and end of day

Undelivered parcels are scanned back into station custody; all offline events and POD must synchronize. The station balances opening inventory plus inbound/transfers/returns minus dispatch/delivery/transfers/upstream returns. Supervisors resolve or explicitly carry over every variance.

### Upstream closure

Technical send success is not closure. Each callback remains open until upstream ACK or approved waiver; daily upstream reconciliation creates cases for differences.

## 3. Human-in-the-Loop Capabilities

The system requires configuration approval, inbound discrepancy workbench, dispatch gates, custody handover, in-transit monitoring, SLA exception queues, return management, end-of-day reconciliation, callback replay/dead-letter handling, and audited access control. Every exception has an owner, priority, due time, evidence, decision, and closure reason.

## 4. Operational Acceptance

For any parcel, an operator can answer: origin, physical location, responsible party, next action, SLA status, and upstream acknowledgment. For any batch or driver shift, expected/actual/difference and accountable closer are visible. No business terminal state is complete without evidence, custody destination, and callback ACK/waiver.
