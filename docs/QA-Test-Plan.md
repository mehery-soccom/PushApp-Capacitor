# PushApp Ionic SDK — Device QA Test Plan

Use this matrix for pilot and production sign-off (Rocket Inc. or any integrator). Run in **sandbox** first, then staging, then production.

## Prerequisites

- [ ] `PushApp.initialize({ appId, sandbox })` — no `slackWebhookUrl` in production
- [ ] Firebase configured (`google-services.json` / `GoogleService-Info.plist`)
- [ ] `@capacitor/push-notifications` registered; tokens forwarded to `PushApp.register()`
- [ ] Lifecycle order: `initialize` → `register` → `login`
- [ ] Android release build uses ProGuard keep rule: `-keep class com.mehery.pushapp.** { *; }`

## 1. Lifecycle & session

| # | Test | Steps | Expected |
|---|------|-------|----------|
| L1 | Cold start init | Launch app fresh, call `initialize` | Resolves `{ status: 'initialized' }` |
| L2 | Register dedup | Call `register` twice with same token | Second call skips API; no duplicate registration errors |
| L3 | Login required order | Call `login` before `register` | Rejects with `LOGIN_FAILED` or lifecycle error code |
| L4 | Session restore | Login, kill app, relaunch | `userId` restored; socket reconnects; no forced re-login |
| L5 | Logout | Call `logout()` after login | Local session cleared; `{ status: 'logged_out' }` |
| L6 | Re-login | Logout → login as different user | New user linked; events use new `userId` |

## 2. Push notifications

| # | Test | Steps | Expected |
|---|------|-------|----------|
| P1 | Foreground push | Send push while app open | Notification presented per OS rules; in-app if configured |
| P2 | Background tap | Send push, tap from tray | App opens; `trackPushNotificationEvent` fires if wired |
| P3 | CTA action | Push with CTA button | Correct `event: 'cta'` + URL opens |
| P4 | Token refresh | Reinstall / clear app data | New token registered via `register` |

## 3. In-app messaging

| # | Test | Steps | Expected |
|---|------|-------|----------|
| I1 | Popup / banner | Trigger campaign | Correct layout; dismiss works |
| I2 | Roadblock | Trigger roadblock | Blocks interaction until dismissed |
| I3 | Bottom sheet | Trigger bottom sheet | Scroll and CTA work |
| I4 | Inline placeholder | `registerPlaceholder` on screen with DOM slot | Content aligned on scroll/resize |
| I5 | Tooltip | `registerTooltipTarget` with rect | Tooltip anchors to registered target |
| I6 | WebSocket delivery | Real-time campaign | Message appears without app restart |

## 4. Events & profile

| # | Test | Steps | Expected |
|---|------|-------|----------|
| E1 | Page tracking | `setPageName` on route enter | `page_open` event sent |
| E2 | Custom event | `sendEvent` with payload | Event accepted; no rejection |
| E3 | Profile update | `saveUserData` after login | Profile PUT succeeds |
| E4 | Device headers | `getDeviceHeaders()` | Returns `X-Device-ID` and device metadata |

## 5. Error contract (automated-friendly)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| C1 | Not initialized | Call `register` before `initialize` | `error.code === 'NOT_INITIALIZED'` |
| C2 | Empty token (iOS) | `register` without `apnsToken` | `error.code === 'EMPTY_TOKEN'` |
| C3 | Missing userId | `login({ userId: '' })` | `error.code === 'EMPTY_USER_ID'` |

Use `getPushAppErrorCode(err)` from `pushapp-ionic` in integration tests.

## 6. Security checks (production gate)

| # | Test | Expected |
|---|------|----------|
| S1 | No Slack telemetry without explicit webhook | No API bodies sent to third-party Slack |
| S2 | Logcat / Xcode logs | No plaintext FCM/APNs tokens in release builds |
| S3 | `debugMode: false` in prod | Verbose SDK logs suppressed |

## Sign-off

| Environment | Date | Tester | Pass/Fail |
|-------------|------|--------|-----------|
| Sandbox | | | |
| Staging | | | |
| Production | | | |
