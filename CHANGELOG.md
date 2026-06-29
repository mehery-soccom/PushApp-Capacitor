# Changelog

All notable changes to **pushapp-ionic** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.2] - 2026-06-29

Complete hardening release: security, stable error contracts, CI, integration documentation, and push notification fixes. **Use this version** — npm `0.1.1` was published without these changes.

### Added

- **`debugMode`** on `PushApp.initialize()` — verbose native logs in debug builds only (PII/tokens redacted).
- **Stable error codes** on all native `call.reject(message, code)` paths; export `PushAppErrorCode`, `isPushAppError()`, `getPushAppErrorCode()` from npm package.
- **`logout()`** — clears local session and POSTs `/pushapp/api/device/delink` when a user was logged in.
- **`PushAppLogger`** (iOS/Android) — release-safe logging with token/PII redaction.
- **MIT `LICENSE`** and package `author` field.
- **CI** (`.github/workflows/ci.yml`) — TypeScript lint/build/test, Android unit tests, iOS SwiftLint.
- **Contract tests** — `npm test`, `npm run test:contract` (26 native error codes aligned across TS/Kotlin/Swift).
- **Docs** — `docs/QA-Test-Plan.md`, `docs/RELEASE.md`, `docs/dependencies.md`; README + Integration Guide rewritten for self-serve integration.

### Changed

- **Slack API logging is opt-in only** — removed all vendor fallback webhooks; logs sent only when a valid `slackWebhookUrl` is explicitly provided.
- **iOS minimum** raised to **15.2** (podspec, SPM, Integration Guide).
- **Peer dependency** — `@capacitor/push-notifications >= 7.0.0`.
- Example app: `logout()` calls `PushApp.logout()`; canonical `pushapp-setup.ts` register retry pattern.

### Fixed

- **`app_open` event** — fires on session restore, register skip, and app foreground.
- **Android push display** — runtime `POST_NOTIFICATIONS` request; `in_app` FCM fallback to system notification.
- **Debug API logging** — URL/request/response in Logcat when `debugMode: true`.
- **FCM token debug logging** — `logPushToken()` when `debugMode: true`.
- iOS/Android unit tests for error codes; ESLint scoped to `src/**/*.ts`.

### Security

- No default third-party Slack telemetry; integrators are not opted into vendor API logging.

---

## [0.1.1] - 2026-06-29

Repository housekeeping only (`.gitignore` for Capacitor build artifacts). **Integrators should pin `0.1.2` or later.**

---

## [0.1.0] - 2026-03 (prior release)

- Capacitor 7 plugin for push, in-app messaging (popup, banner, roadblock, inline, tooltip), WebSocket campaigns.
- Lifecycle: `initialize` → `register` → `login`.
- Ionic inline **placeholder sync** (scroll, resize, fixed headers).
- Multi-channel distribution: npm, CocoaPods, SPM.

---

## Upgrade guide: 0.1.0 → 0.1.2

1. Pin `pushapp-ionic@^0.1.2` and run `npx cap sync`.
2. **Remove** reliance on implicit Slack logging — pass `slackWebhookUrl` only in non-prod if needed.
3. Add `debugMode: true` only in development builds.
4. Use `getPushAppErrorCode(err)` instead of parsing error message strings.
5. Call `PushApp.logout()` on user sign-out.
6. Ensure **iOS deployment target ≥ 15.2** and **Android minSdk ≥ 23**.
7. Declare `@capacitor/push-notifications` in your app (peer dependency).
8. Follow the updated [Integration Guide](docs/Integration-Guide.md) and [QA Test Plan](docs/QA-Test-Plan.md).

[0.1.2]: https://github.com/mehery-soccom/PushApp-Capacitor/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/mehery-soccom/PushApp-Capacitor/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/mehery-soccom/PushApp-Capacitor/releases/tag/v0.1.0
