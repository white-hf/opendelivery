# R01.1 Internationalization Foundation

## Outcome

Establish one localization foundation before expanding operator and driver workflows, without changing domain contracts or state behavior.

## Vertical Slices

1. Data/API: persist account/station locales, resolve `Accept-Language`, localize common, auth, permission, validation, resource, and R01 area messages; test driver and operator APIs.
2. Operations Web: locale switch/persistence and translations for login, navigation, and R01 areas, including fallback tests.
3. Driver contract: publish Android resource keys and cover login, scan, wrong-task, submit, delivery, and failed-return copy in all three locales.
4. Gate: verify key-set equality, three-locale regression, real-MySQL persistence, and aligned docs/evidence.

## Definition of Done

Login and R01 critical paths work in all three locales; missing or unsupported locales safely fall back to `en-CA`; clients depend only on business codes; CI rejects any new key missing a launch-locale translation.

## Status (2026-07-20)

Slice 1 is complete: V9 is on real MySQL, driver/operator preferences, header resolution, shared API localization, and three bundles are verified. Slice 2 has the framework plus login, navigation, and area UI; manifest, dispatch, case, and closeout copy remains. Android resources and complete UI/E2E gates remain, so R01.1 is in progress.
