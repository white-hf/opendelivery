# M08: Driver Suggestion Resource Entry

Status: COMPLETED

## Background

The driver suggestion documents are unrelated to the Last Mile operations workflow. The website only needs a convenient entry for product managers. The documents are already shipped as static assets under `public/docs/driver-suggestion/`.

## Solution

- Add an independent “Driver suggestion” menu in the signed-in user action area.
- Provide Chinese and English static HTML links, opening in a new tab.
- Allow URLs to be overridden by Vite environment variables while keeping repository-relative defaults.
- Do not add a business-navigation page, backend API, database table, or permission model.

## Acceptance Criteria

1. The document entry is visible in the signed-in top action area.
2. Chinese opens `/docs/driver-suggestion/index.zh-CN.html`; English opens `/docs/driver-suggestion/index.en.html`.
3. Links use a new tab with `noopener,noreferrer`.
4. `pnpm run typecheck` and `pnpm run build` pass.

## Implementation Result

The top resource menu, bilingual static-document links, and Vite URL overrides are complete. Both HTML files are copied into `dist/docs/driver-suggestion/` by the build.
