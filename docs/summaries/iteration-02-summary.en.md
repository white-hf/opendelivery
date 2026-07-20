# I02 Multi-City Routing Foundation Summary

## Delivered Scope

I02 completes the minimum routing loop for multiple cities with exactly one station per city. Canonical Shipment sends address and service requirements; `targetStationCode` is now an optional hint. The system normalizes country, province, city, and postal code, then selects one active station by longest valid postal prefix and priority.

Migration `V3__multi_city_routing.sql` adds station geography and the one-city constraint, creates `station_service_area`, and adds Waybill `routing_status`, `resolved_station_id`, `routing_reason_code`, `routed_at`, and queue indexes. Only the current business result is persisted; algorithm candidates are not stored.

## Operations Closure

- Unique match: Waybill becomes `ROUTED`, Parcel receives the station, and an inbound Manifest may be created.
- No/conflicting match: Waybill becomes `UNROUTABLE/AMBIGUOUS`, Parcel becomes `ADDRESS_EXCEPTION`, and a `ROUTING_EXCEPTION` Case opens.
- Manual resolution: an operator selects an active station and supplies a reason; Waybill and Parcel update, a Case Action is written, and the Case resolves.
- Configuration APIs: list/create stations and service areas, reroute a Waybill, and override routing.

## Verification

`./run.sh test` passed 9 tests and `./run.sh build` succeeded. The real `opendelivery` MySQL schema upgraded from Flyway baseline 2 to V3. E2E routed Toronto to `YYZ-01`, opened a Case for uncovered Charlottetown, then resolved it through an override to `YHZ-01`.

## Next

I03 adds operator identities, roles, default station context, and general audit. Fulfilment commands must reject cross-station work. Operations Web pages and richer rule editing continue under the master execution plan.

