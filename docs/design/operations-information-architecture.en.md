# Operations Information Architecture and Domain Design

Commercial instruction, plan, physical observation, custody, and exception are orthogonal. `Parcel.status` is only a projection. The spatial model separates station coverage from versioned delivery-area `MULTIPOLYGON SRID 4326`; stable area identity, published GeoJSON versions, parcel snapshots, driver preferences, daily shifts, and batch assignments are distinct.

Dispatch Wave evolves into a pre-arrival Dispatch Batch: release creates plan membership and expected scans, not custody. `Inbound Trip` and `Handling Unit` represent physical transport independently. Existing manifests remain compatible pre-advice/special receipt rather than the only physical truth. Rejected scan observations never change membership or custody; reconciliation recomputes across tasks; approval revalidates under locks and atomically appends projections, custody, status, audit, and outbox facts.

The global bar fixes station, business date, role, alerts, and blockers. Navigation is Today; Orders & Map; Batch Planning; Arrival & Scan; Handover Approval; Delivery Control; Exceptions; Closeout; Configuration. Maps and lists share filters/selection; server clustering precedes points; parcel detail preserves viewport and bulk context.

A unified reassignment request returns `DIRECT`, `REMOVE_AND_RESCAN`, `RETURN_THEN_ASSIGN`, `BILATERAL_HANDOVER`, or `NOT_ALLOWED`, never overwriting a driver blindly. MOV implements the first three. Domain events, operation audit, and technical logs remain separate; success, failure, and denial form a unified object timeline.

Upstream idempotency, routing, RBAC, audit, outbox, active-task uniqueness, load ownership, custody approval, POD, failure, and return foundations remain. Pre-arrival planning, area geometry/versioning, trips/handling units, rejected scans, cross-task reconciliation, state-aware reassignment, and closeout are added. Operator-led item receipt remains compatible but no longer defines the standard path.
