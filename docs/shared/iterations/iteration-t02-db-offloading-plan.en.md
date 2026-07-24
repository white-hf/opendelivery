# T02 Database Computation Offloading Plan

> Status: `REVIEWED` (2026-07-23)  
> Goal: Offload GeoSpatial computation, JSON parsing, `SELECT MAX()+1` sequence calculations, and real-time `COUNT(*)` aggregations from MySQL to the Java memory layer.

---

## 1. Scope & Implementation

1. **GeoSpatial Computation**: Replace MySQL `ST_Contains` with LocationTech JTS in Java.
2. **Strongly Typed JSON**: Replace MySQL `JSON_EXTRACT` with Jackson & JPA Converters.
3. **Sequence Generation**: Replace `SELECT COALESCE(MAX)+1` with DB AUTO_INCREMENT / State Machine derivation.
4. **Aggregation Offloading**: Replace redundant `COUNT(*)` DB queries with Java Stream filtering.

---

## 2. DoD (Definition of Done)

* [x] T02 plan document reviewed (`REVIEWED`)
* [ ] Add JTS dependency and utility converters in `easydelivery-common`
* [ ] Refactor `ShipmentRoutingService`, `ShipmentIngestionService`, and `ControlTowerService`
* [ ] Ensure all JUnit tests pass (`./run.sh test`)
* [ ] Complete execution summary
