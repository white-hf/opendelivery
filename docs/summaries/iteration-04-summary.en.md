# I04 Multi-City Inbound Summary

## Delivered Scope

I04 completes an operable Manifest receiving loop. Operators can only list and inspect Manifests for the selected station. Starting receiving records actual arrival. Scanner commands are idempotent by `deviceEventId` and classify normal, damaged, extra, wrong-station, and duplicate scans.

V5 adds `inbound_scan_event` and Manifest/Item links on Cases. Normal and damaged receipts atomically update Parcel, station inventory, custody, status event, and outbox. Damaged, extra, and wrong-station scans open Cases. At close, unscanned expected pieces become missing and open Cases. Open discrepancies reject close unless resolved or explicitly carried over.

## Verification

The real MySQL schema upgraded to Flyway V5. E2E processed two expected pieces and one extra: normal inventory receipt, damaged quarantine with Case, extra Case, duplicate device retry without duplicate writes, close rejection with open Cases, and successful close after decisions. Status event, custody, and outbox were each written once. A Halifax operator reading a Toronto Manifest received HTTP 403.

`mvn clean verify` passed across the reactor.

## Next

I05 splits the current one-step wave publish into draft, candidate inventory, publish, and revoke commands, then adds driver load scanning and supervisor discrepancy approval.
