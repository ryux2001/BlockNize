# BlockNize

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

People who organize their own daily agenda and want to consult it across Android, desktop, and web.

## Product Purpose

BlockNize helps people plan their days with weekly schedules, time blocks, one-off tasks, and an activity history. The web client gives an account holder access to that shared agenda from a browser.

## Positioning

The account's schedule and history are shared across BlockNize clients, while Android remains usable locally without a network connection.

## Operating Context

People consult and edit their schedules in their account's IANA time zone. Dates are civil dates. A person may use Android, desktop, and web to change the same schedule.

## Capabilities and Constraints

- The web client signs in through Neon Auth and calls the NestJS API through same-origin Next.js handlers.
- The API owns schedule rules, occurrence calculations, permissions, history, and version checks.
- The web client requires a network connection to save. Offline drafts remain in the open tab's memory.
- The web client does not provide durable offline storage, a service worker, or a PWA.
- The current API has no route for deleting a week template or editing occurrence exceptions.
- The API's history endpoint limits each request to 31 days and paginates with `nextDate`.

## Brand Commitments

The user asked the web client to follow the existing Android visual style. Preserve the BlockNize name and the dark surfaces, light text, and green accent shown in the Android theme and screens.

## Evidence on Hand

- Android interface reference screens in `Instrucciones/pantallas/`.
- Android theme colors in `android/app/src/main/java/com/blocknize/app/ui/theme/Theme.kt`.
- API behavior documented in `Instrucciones/Fase5_Plan_Web.md` and `backend/README.md`.

## Product Principles

- Keep the agenda understandable at a glance.
- Treat the account time zone and civil dates as the source for calendar navigation.
- Show whether a change is saved, stale, conflicted, or awaiting confirmation.
- Keep each account's private agenda isolated.
- Refresh shared data without replacing an edit in progress.
