# M01 Mobile Operations Foundation

Status: `COMPLETED` (2026-07-27)

## Background and goal

Operators need to check the business-day journey and blockers from a phone browser. The current console is desktop-first, with a side navigation, two-column workspaces, and wide tables. This iteration establishes the responsive shell without changing APIs or business state transitions.

## Scope

- Define responsive breakpoints for 320/375/414/768px and remove horizontal overflow.
- Replace the desktop side navigation with a closable drawer on small screens while preserving permissions and station context.
- Prioritize the business-day SOP and next action on the mobile home page.
- Use a single-column mobile container and shared spacing primitives; preserve desktop layouts.
- Add shared CSS hooks for the later map, card-list, and bottom-action iterations.

## Non-goals

- No map workflow rewrite, API contract change, or Driver App mobile work in this iteration.

## Acceptance and verification

- No horizontal scrolling from 320px through 768px.
- Navigation opens, changes pages, and closes without losing page or station context.
- The home page exposes the business-day journey, key metrics, and next action.
- Existing desktop pages and Playwright regression remain intact.
- `pnpm run typecheck` and `pnpm run build` pass.
