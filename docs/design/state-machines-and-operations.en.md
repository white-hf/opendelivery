# State Machines and Operational Controls

> **Migration note:** Current evolution is defined by [Operations Information Architecture and Domain Design](operations-information-architecture.en.md). Low-level constraints remain, but receipt-before-planning is no longer the standard path.

## Parcel Lifecycle

Waybill routing precedes inbound: `PENDING → ROUTED`; no match becomes `UNROUTABLE`, ambiguity becomes `AMBIGUOUS`, and authorized manual resolution may reach `ROUTED`. Only current result is stored; failure creates a Case and manual assignment is audited. Only a routed waybill with an active resolved station creates that station's Manifest. No automatic station change after receipt.

The supported primary sequence is `RECEIVED → AT_STATION → SORTED → READY_FOR_DISPATCH → ASSIGNED → OUT_FOR_DELIVERY → DELIVERED`. Failed delivery can reschedule and reassign, or proceed through `RETURN_PENDING → RETURNED_TO_STATION → RETURNED_TO_UPSTREAM`. Cancellation, loss, damage, and address exception are controlled transitions with reason, actor, evidence, and approval.

Commands validate source state, expected version, idempotency key, task ownership, custody, and evidence. The same transaction updates the projection, appends `parcel_status_event`, and writes `outbox_event`.

## Custody, Task, Scan, Case, and Callback

- Custody owners: `UPSTREAM`, `STATION`, `DRIVER`, `RETURN_CARRIER`, `UNKNOWN`. Every transfer appends `custody_event`.
- Task: `DRAFT → PUBLISHED → ACCEPTING → IN_PROGRESS → CLOSED`, with controlled cancellation.
- Scan session: `OPEN → SUBMITTED → APPROVED`; a discrepancy can be rejected and rescanned. Scan results are expected, extra, duplicate, wrong-task, or unknown.
- Case: `OPEN → ASSIGNED → IN_PROGRESS/WAITING_EXTERNAL → RESOLVED → CLOSED` with owner and SLA.
- Callback: `PENDING → SENDING → ACKNOWLEDGED`; failures move through `RETRY` to `DEAD_LETTER`, or approved `WAIVED`.

## Daily Reconciliation

MOV calculates independently per station: `opening + inbound + driver-return - dispatch - delivery - upstream-return = closing`. Existing transfer-in/out columns remain zero compatibility fields and do not imply inter-station transfer support.

Open tasks, driver holdings, incomplete POD, scan discrepancies, overdue cases, and callback dead letters are also checked. A supervisor may carry a variance only with a recorded reason; records are never deleted to force balance.
