# Operations Web Frontend Technical Design

## 1. Technology Stack

| Layer | Choice | Purpose |
|---|---|---|
| UI language | React + strict TypeScript | Componentized, typed operations UI |
| Build | Vite | Simple fast SPA development/build |
| Package manager | pnpm with committed lockfile | Reproducibility and storage efficiency |
| UI kit | Ant Design + design tokens | Dense tables, forms, drawers, steps, feedback |
| Routing | React Router | Nested shell, guarded routes, URL filters |
| Server state | TanStack Query | Cache, dedupe, invalidation, retry, refresh |
| Forms | React Hook Form + Zod | Performant forms and runtime schemas |
| HTTP | wrapped native `fetch` | Central auth, request ID, errors, timeout |
| Date | Day.js | Lightweight station-timezone display |
| i18n | i18next + react-i18next | Chinese first, English-ready |
| Unit/component | Vitest + React Testing Library + MSW | Behavior tests and API mocks |
| E2E | Playwright | Chromium gate; Firefox/WebKit smoke |
| Quality | ESLint/typescript-eslint + Prettier | Static checks and format |
| Delivery | Nginx static site or same-origin CDN | SPA fallback, cache/security headers, API proxy |

Pin mutually compatible stable versions when scaffolding and upgrade in small automated batches; “latest” is not a production policy.

## 2. Explicit Non-Choices

No SSR/Next.js because the authenticated console has no SEO need. No microfrontend for one team/deployable. No Redux by default: Query owns server state and Context is enough until a concrete client-state problem exists. No custom design system before product validation. No WebSocket in MOV; 30–60 second polling is sufficient, with SSE considered only after measured real-time need.

## 3. Structure

```text
easydelivery-operations-web/
├── src/
│   ├── app/ api/ auth/ features/ components/
│   ├── layouts/ i18n/ styles/ test/
│   └── main.tsx
├── e2e/ public/
├── package.json
└── vite.config.ts
```

Each feature owns `pages/components/api/hooks/schema/__tests__`. Cross-feature access uses public `index.ts`, never deep internal imports.

## 4. Backend Contract and HTTP

Backend owns OpenAPI 3.1; CI validates/generates TypeScript types. Until available, critical responses use handwritten Zod schemas—never `any`.

The client adds Bearer, `X-Request-Id`, station context, and write idempotency. Default timeout is 15 seconds; uploads differ; AbortController cancels stale queries. Only one shared refresh promise handles 401 and replays once. A 403 never refreshes; 409 exposes current version; 429 honors `Retry-After`. Check both HTTP status and legacy `biz_code`.

## 5. State Management

Query keys are `[domain, stationId, filters/id]`; station switch clears sensitive cache. Lists are stale after 30 seconds, dictionaries/roles after five minutes, and mutations invalidate precisely. Identity remains in memory. Prefer Secure HttpOnly SameSite refresh cookie; if backend cannot yet support it, use sessionStorage temporarily, never long-lived localStorage, and record the risk.

URL owns filter/sort business context; components own transient modal state. Do not optimistically update custody, sign-off, or other state commands.

## 6. Authorization and Security

Route/button permissions improve UX; backend remains authoritative. Permission keys use `resource:action`. PII is masked and reveal calls an audited API. Never render upstream text with `dangerouslySetInnerHTML`. Use CSP, HSTS, `frame-ancestors 'none'`, nosniff, and strict referrer policy. Upload prechecks repeat server-side. Logs contain no body, token, POD, or PII; source maps go only to controlled error monitoring.

## 7. Shared Business Components

`StationDateContext`, `StatusTag`, cursor-aware `ServerTable`, scanner-focused `ScannerInput`, `DiscrepancyPanel`, unified `ParcelTimeline`, reason/version-aware `HighRiskActionDialog`, and UX-only `PermissionGate` form the shared layer.

## 8. Performance and Observability

Initial target: production-gzipped initial JS ideally ≤350 KB excluding route chunks; LCP p75 ≤2.5 s and INP p75 ≤200 ms, calibrated on station devices. Lazy-load routes; tree-shake Ant Design; use server pagination. Add virtualization only after hundreds of visible rows prove a bottleneck.

Frontend telemetry includes release, route, request ID, anonymous user/station, network, command success, and timing—never PII. Server remains the business fact source.

## 9. Tests and CI

Unit-test permissions/format/schema/errors; component-test filters/tables/scanner/discrepancy/dialog; contract-test OpenAPI examples and MSW fixtures; E2E each role, cross-station 403, inbound, publish, handover, failed return, Case, replay, and closeout; run axe plus keyboard review.

PR gate: `pnpm lint`, `pnpm typecheck`, `pnpm test --run`, `pnpm build`; merged environment runs Chromium E2E, release candidate runs three-browser smoke. Snapshots do not replace business assertions.

## 10. Deployment

No secret enters the build. Only public configuration such as `VITE_API_BASE_URL`; prefer same-origin `/api` proxy for cookie/CORS simplicity. Hashed assets cache immutable for one year; `index.html` is no-cache. Retain the prior version directory for fast rollback. Frontend must support current and previous backend contract during rollout.

## 11. Official Selection References

- React and TypeScript: <https://react.dev/learn/typescript>
- Building React from scratch (including Vite): <https://react.dev/learn/build-a-react-app-from-scratch>
- Vite guide: <https://vite.dev/guide/>
- Ant Design components: <https://ant.design/components/overview/>
- TanStack Query React: <https://tanstack.com/query/latest/docs/framework/react/overview>
- Playwright: <https://playwright.dev/docs/intro>
