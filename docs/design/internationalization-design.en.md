# Internationalization and Localization Design

## Goal and Scope

Operations Web, Driver App APIs, and Operations APIs initially support `en-CA`, `fr-CA`, and `zh-CN`. Domain states, error codes, database relationships, and raw upstream content remain canonical; only display messages, validation feedback, and notification templates are localized.

## Locale Resolution and Contract

Precedence is `Accept-Language`, account `preferred_locale`, station `default_locale`, then `en-CA`. APIs always return a stable `biz_code`; `biz_message` follows the resolved locale and must never drive client logic. Unsupported locales fall back to English. Timestamps remain UTC and are displayed using both station timezone and user locale.

## Technical Design

- Spring `MessageSource` serves `messages_en_CA/fr_CA/zh_CN.properties`; global exception handling covers both driver and operator APIs.
- `operator_user.preferred_locale`, `driver.preferred_locale`, and `station.default_locale` persist defaults.
- Operations Web uses `i18next/react-i18next`; Android uses local `strings.xml` for offline-safe core UI.
- Raw partner payloads and customer-entered text are preserved; canonical address, state, and reason codes use separate fields. Legal or customer text is not machine translated.
- Notification templates will be versioned by `(template_code, locale, version)` and delivery history will reference the rendered version.

## Security and Validation

Locale is never inferred from names, nationality, or addresses. Logs use business codes, not localized prose. Automation covers all three locales, quality-weighted headers, unsupported locale fallback, key-set completeness, and invariant domain behavior after language changes.
