# M05: Mobile Date Entry and English UI Cleanup

Status: COMPLETED

## Problem

Responsive rules hid the global service-date selector on phones, and several workspaces still rendered Chinese hard-coded text in the English locale.

## Scope

- Keep station and service-date selectors in the mobile navigation bar with compact sizing.
- Clean high-frequency hard-coded labels in the operations dashboard, order readiness, inbound, dispatch planning, shipment detail, and configuration screens.
- Add English, French, and Chinese labels for the navigation drawer and service-date control.
- Do not change service-date query parameters, station switching, or API contracts.

## Acceptance Criteria

- The service date remains selectable at 375px width.
- With `en-CA`, the headings, actions, table columns, and prompts covered by this iteration contain no Chinese hard-coded text.
- Existing `zh-CN` and `fr-CA` navigation translations remain available.
- `pnpm run typecheck` and `pnpm run build` pass.
