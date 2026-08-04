# M06: Full English UI Clean-up & i18n Fix

Status: COMPLETED

## Objective & Background

Under `en-CA` (English) language mode, hardcoded Chinese text still exists across several Operations Web workspaces (such as table column names, action buttons, alert messages, input placeholders, drawer titles, etc.). This iteration aims to expand the `i18n.ts` dictionary and replace all hardcoded Chinese text in workflow components, ensuring a clean and consistent English user experience.

## Implementation Scope

1. **`i18n.ts` Key Expansion**:
   - Add missing keys for `en-CA`, `fr-CA`, and `zh-CN` covering delivery areas, arrival batches, scan supervision, dispatch & reassign, manifests, cases, day close, and shipment detail drawer.
2. **Workspace Component Clean-up**:
   - `AreaWorkspace.tsx`: Clean up hardcoded texts in area status, driver roles, add button, form placeholders, tooltips.
   - `ArrivalWorkspace.tsx`: Clean up trip header, transport edit drawer labels/placeholders, validation alerts, mobile action bar, and table columns.
   - `ScanSupervisionWorkspace.tsx`: Clean up wave selection dropdown, status tags, view action buttons.
   - `DispatchWorkspace.tsx` & `DispatchReassignWorkspace.tsx`: Clean up selection counts, layer controls, reassign filters, and confirmation buttons.
   - `ManifestWorkspace.tsx` & `CaseCenterWorkspace.tsx`: Clean up metric cards, action buttons, unassigned tags, case handling modals.
   - `DayCloseWorkspace.tsx` & `ShipmentDetailDrawer.tsx`: Clean up gate check columns, status tags, sign-off buttons, and drawer labels.

## Definition of Done (DoD)

1. Under `en-CA` locale, no hardcoded Chinese characters remain in any page UI.
2. Existing functionality and translations in `zh-CN` and `fr-CA` are preserved without regression.
3. Frontend typecheck (`pnpm run typecheck`) and build (`pnpm run build`) complete cleanly without errors.

## Implementation Result

Workspace hardcoded copy was localized, launch-locale key sets were aligned, and regressions for driver-parcel navigation permission and station-context headers were corrected. All 26 Vitest tests, type checking, and the production build pass.
