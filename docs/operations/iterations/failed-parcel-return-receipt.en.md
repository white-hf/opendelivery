# Failed Parcel Return Receipt Iteration

> Status: `REVIEWED` (2026-07-21). Product decision: the driver physically brings parcels back; an operator records warehouse receipt. The Driver API does not create or submit return scan sessions.

## Workflow and outcome

After `/delivery` reports failure, the parcel is `DELIVERY_FAILED` and remains in driver custody. The operator finds the parcel in a station-scoped queue, verifies the physical handover, and confirms receipt with a reason.

One transaction moves `DELIVERY_FAILED → RETURNED_TO_STATION`, custody `DRIVER → STATION`, and task item `FAILED → RETURNED`, then records status, custody, audit, and upstream outbox events. Redispatch or upstream return is a separate Operations decision.

## API and acceptance

- `GET /ops/v1/failed-returns?serviceDate=YYYY-MM-DD` lists only failed, driver-held parcels at the selected station.
- `POST /ops/v1/failed-returns/{parcelId}/receive` accepts `reasonCode` and `note`.
- Repeated receipt is idempotent; cross-station, non-failed, and non-driver-custody requests are rejected.
- INBOUND, SUPERVISOR, and ADMIN may operate it; every write is audited.

## Non-goals

No `/driver/v1/**`, driver-side return status mutation, automatic new task, or edits to applied Flyway migrations.
