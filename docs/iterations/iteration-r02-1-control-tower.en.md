# R02.1 Operations Control Tower Iteration

## Outcome and Priority

This mandatory pre-pilot version resolves operators not knowing the current stage/next action or why a map is empty. Route optimization and other nonessential scope are excluded.

| Slice | Accepted outcome | Main work | Evidence |
|---|---|---|---|
| CT01 Journey navigation | Menus follow the operating day | Groups, stage/badges, current step, separate configuration, filtered deep links | Three-role task finding; three languages |
| CT02 Today control tower | Home enables decisions/actions | Shared definitions, journey bar, capacity, exceptions, actions, drill-down | Aggregate=detail; three-station API/UI isolation |
| CT03 Map observability | Locatable data always produces points | Auto-fit, clustering, queried/locatable/displayed counts, legend filters, explicit empty/error states | Google browser acceptance; 69/72 fixture definition |
| CT04 Journey regression | A day can be completed through navigation | Shared context, retained viewport/filters, Playwright, accessibility | YHZ/YYZ/YVR E2E and operator sign-off |

## Technical Method

- Control Tower APIs return stable `code/count/severity/target/filters`; clients do not reconstruct definitions.
- Stage state derives from facts, with no editable “current stage” field.
- URLs preserve `station/serviceDate/view/filter`; station changes cancel requests and clear query cache.
- Map APIs return queried, locatable, exception, and point counts and later support viewport/aggregation levels.
- Fixtures use production Waybill/Parcel/Geocode/Area Assignment tables; clients never hard-code markers.

## Definition of Done

The four slices ship in order and remain independently demonstrable. Bilingual API, product, design, test, and summary artifacts stay synchronized. A non-empty API does not complete the map: browser-visible points, consistent counts, and three-station switching are mandatory.

