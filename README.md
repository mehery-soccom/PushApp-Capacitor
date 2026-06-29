# PushApp-Ionic SDK

Capacitor 7 plugin for push notifications, in-app messaging (popup, banner, roadblock, inline, tooltip), event tracking, and session handling in Ionic/Capacitor apps (iOS + Android).

## Start here

| I want to… | Read this |
|------------|-----------|
| Integrate the SDK into my Ionic app | **[Integration Guide](docs/Integration-Guide.md)** — follow sections 1–9 in order |
| See a working app | **`example-app/`** — copy `pushapp-setup.ts` first |
| Look up a method | [API reference](#api-reference) below · [docs/api-reference.md](docs/api-reference.md) |
| Test before production | [QA Test Plan](docs/QA-Test-Plan.md) |
| Handle errors in code | [Error handling](#error-handling) below |

> **New to Capacitor?** Allow 2–4 hours. Test on a **real device** — push does not work in the browser.

---

## When to call what

| When | Call | Where in your app |
|------|------|-------------------|
| App launches (before login screen) | `initialize()` then `register()` | `app.component.ts` or `pushapp-setup.ts` |
| User signs in | `login()` then `saveUserData()` | Login success handler |
| User signs out | `logout()` | Logout handler (before clearing auth) |
| User returns with saved session | `initialize()` → `register()` → `login()` | App launch (see [Integration Guide §5C](docs/Integration-Guide.md#5c--returning-user-session-restore)) |
| Screen opens | `setPageName()` + `sendEvent()` | Page `ionViewDidEnter` |
| Inline campaign slot on screen | `registerPlaceholder()` / `unregisterPlaceholder()` | Page enter / leave |
| Tooltip campaign anchor | `registerTooltipTarget()` / `unregisterTooltipTarget()` | Page enter / leave |

**Lifecycle order (required):** `initialize()` → `register()` → `login()`

Full code and file paths: **[Integration Guide §5](docs/Integration-Guide.md#5-sdk-integration-required)**

---

## Quick checklist

Your app should include:

- Firebase config: `android/app/google-services.json` and/or `ios/App/GoogleService-Info.plist`
- Android Gradle: `google-services` plugin applied — see [Integration Guide §3b](docs/Integration-Guide.md#3b-android-gradle-required)
- iOS Push Notifications capability + `AppDelegate` token forwarding — see [Integration Guide §4](docs/Integration-Guide.md#4-ios-native-setup)
- SDK calls in order: **`initialize()` → `register()` → `login()`** (call **`logout()`** on sign-out)
- After login (when needed): `saveUserData()`, `setPageName()`, `sendEvent()`
- iOS notification tap tracking — see [Integration Guide §4b](docs/Integration-Guide.md#4b-ios-notification-open--cta-tracking)
- Inline/tooltip registration only if your PushApp campaigns use those surfaces

`register()` is safe to call on every app open — the native SDK **skips the register API** when the device is already registered with the same token. Customer profiles are **not** updated automatically; call `saveUserData()` explicitly after login.

---

## Setup (overview)

Full steps with file paths and native config: **[Integration Guide](docs/Integration-Guide.md)**

1. **Install** — `npm install pushapp-ionic @capacitor/push-notifications` + `npx cap sync`
2. **Firebase** — add config files ([Guide §3](docs/Integration-Guide.md#3-firebase-setup))
3. **Native** — iOS `AppDelegate` + Android Gradle ([Guide §3–4](docs/Integration-Guide.md#3-firebase-setup))
4. **App launch** — `initialize()` + `register()` ([Guide §5A–B](docs/Integration-Guide.md#5-sdk-integration-required))
5. **After login** — `login()` + `saveUserData()` ([Guide §5C, §6](docs/Integration-Guide.md#5-sdk-integration-required))
6. **Per screen** — `setPageName()` / `sendEvent()` ([Guide §7](docs/Integration-Guide.md#7-event-and-page-tracking))

**Recommended:** copy `example-app/src/app/pushapp-setup.ts` into your project.

```typescript
import { PushApp } from 'pushapp-ionic';
import { environment } from '../environments/environment';

await PushApp.initialize({
  appId: environment.pushApp.appId,
  sandbox: environment.pushApp.sandbox,
  debugMode: !environment.production && environment.pushApp.debugMode,
});
// then register() — see pushapp-setup.ts
```

---

## Configuration

Store credentials in environment files — do not hardcode in components:

```typescript
// src/environments/environment.ts
export const environment = {
  production: false,
  pushApp: {
    appId: 'yourtenant_1234567890',
    sandbox: true,
    debugMode: true, // dev only — verbose native logs (tokens redacted)
  },
};
```

Do **not** pass `slackWebhookUrl` in production (integration debugging only).

---

## Environments

The tenant subdomain comes from your App ID (e.g. `synapsewave` from `synapsewave_1773818143216`).

| `sandbox` | Environment | API base | WebSocket |
|-----------|-------------|----------|-----------|
| `false` | **Production** | `https://{tenant}.pushapp.ai` | `wss://{tenant}.pushapp.ai/pushapp` |
| `true` | **Sandbox** | `https://{tenant}.pushapp.co.in` | `wss://{tenant}.pushapp.co.in/pushapp` |

---

## Web / browser

> **Browser dev:** This plugin is native-only. In `ionic serve`, methods return `WEB_NOT_SUPPORTED`. Use `Capacitor.isNativePlatform()` before placeholder/tooltip calls. Test push on a **real device**.

---

## Inline placeholders

Use when PushApp campaigns deliver **inline** content into your app UI. The SDK **automatically** tracks placeholder position on scroll, resize, and fixed headers (`ion-header`).

```html
<div id="promo-banner" class="promo-slot"></div>
```

```typescript
// ionViewDidEnter
await PushApp.registerPlaceholder({ placeholderId: 'promo-banner' });

// ionViewWillLeave
await PushApp.unregisterPlaceholder({ placeholderId: 'promo-banner' });
```

Full details: [Integration Guide §8](docs/Integration-Guide.md#8-optional--in-app-message-placements)

---

## Tooltip targets

Register anchor elements for native tooltips. Register in `ionViewDidEnter` after DOM layout (`requestAnimationFrame` / short `setTimeout`). `targetId` must match your PushApp campaign config.

```typescript
const el = document.getElementById('deals-fab');
const rect = el!.getBoundingClientRect();

await PushApp.registerTooltipTarget({
  targetId: 'center', // campaign target id
  x: Math.round(rect.left),
  y: Math.round(rect.top),
  width: Math.round(rect.width),
  height: Math.round(rect.height),
});

await PushApp.unregisterTooltipTarget({ targetId: 'center' });
```

Full details: [Integration Guide §8](docs/Integration-Guide.md#tooltip-target)

---

## API reference

### Core

| Method | When to call |
|--------|----------------|
| `initialize({ appId, sandbox?, debugMode?, slackWebhookUrl? })` | App startup |
| `register({ fcmToken })` | After initialize — Android |
| `register({ apnsToken, fcmToken? })` | After initialize — iOS |
| `login({ userId })` | After register, when user signs in |
| `logout()` | On sign-out — clears local session and delinks device on server |
| `saveUserData({ code, additionalInfo, cohorts })` | After login |
| `setPageName({ pageName })` | On screen change |
| `sendEvent({ eventName, eventData })` | On user actions |
| `getDeviceHeaders()` | Anytime |
| `trackPushNotificationEvent({ token, event, ctaId? })` | Notification open / CTA tap¹ |

¹ Requires native notification handler — see [Integration Guide §4b](docs/Integration-Guide.md#4b-ios-notification-open--cta-tracking). Android handles taps via the SDK's `NotificationClickReceiver`.

### Inline & tooltip

| Method | When to call |
|--------|----------------|
| `registerPlaceholder({ placeholderId, elementId?, clipTopSelector? })` | View enter |
| `unregisterPlaceholder({ placeholderId })` | View leave |
| `registerTooltipTarget({ targetId, x, y, width, height })` | View enter |
| `unregisterTooltipTarget({ targetId })` | View leave |

Auto-generated details: run `npm run docgen` → [docs/api-reference.md](docs/api-reference.md)

---

## Error handling

Native methods reject with stable `code` values (e.g. `NOT_INITIALIZED`, `REGISTER_FAILED`, `EMPTY_TOKEN`). Import helpers from the package:

```typescript
import { PushApp, PushAppErrorCode, getPushAppErrorCode, isPushAppError } from 'pushapp-ionic';

try {
  await PushApp.register({ fcmToken: '' });
} catch (err) {
  if (getPushAppErrorCode(err) === PushAppErrorCode.EMPTY_TOKEN) {
    // retry when token arrives — see pushapp-setup.ts
  }
}
```

Common codes: [Integration Guide §13](docs/Integration-Guide.md#13-error-handling)

---

## Platform notes

### Android

- Minimum API 23 (Android 6.0)
- Android 13+: notification permission is requested by the SDK on `initialize()` — user must tap **Allow**
- ProGuard (release): add to `android/app/proguard-rules.pro`:

  ```
  -keep class com.mehery.pushapp.** { *; }
  ```

- Gradle: `google-services` plugin required — see [Integration Guide §3b](docs/Integration-Guide.md#3b-android-gradle-required)

### iOS

- Minimum iOS 15.2
- Enable **Push Notifications** capability in Xcode
- `AppDelegate`: forward APNs token + notification tap tracking — see [Integration Guide §4](docs/Integration-Guide.md#4-ios-native-setup)

---

## Example app

Run the demo:

```bash
cd example-app && npm install && npx cap sync
```

| File | What to copy |
|------|----------------|
| `src/app/pushapp-setup.ts` | `initialize` + `register` with retry |
| `src/app/login/login.page.ts` | `login` + `saveUserData` |
| `src/app/home/home.page.ts` | `setPageName`, placeholders, tooltips, `logout` |
| `ios/App/App/AppDelegate.swift` | APNs token + notification tap tracking |

This is the **canonical integration pattern** — prefer it over ad-hoc snippets.

---

## Troubleshooting

| Issue | What to check |
|-------|----------------|
| `initialize` fails | App ID format: `tenant_suffix` (e.g. `demo_1763369170735`) |
| `register` rejected | Call `initialize()` first; token may not be ready on first launch — use retry pattern from `pushapp-setup.ts` |
| `login` rejected | Call `register()` successfully first |
| No push on device | Real device required; verify Firebase config, Gradle plugin, and notification permissions |
| Testing in browser | Expected: `WEB_NOT_SUPPORTED` — use a device |
| After logout, push tied to old user | Call `logout()` before clearing local auth |
| Inline never shows | `placeholderId` mismatch; register after DOM ready; call `setPageName` / `sendEvent` |
| Inline overlaps header | Ensure `clipTopSelector` matches your fixed header (default: `ion-header`) |
| Parse errors in catch | Use `getPushAppErrorCode(err)` instead of parsing message strings |

**Logs:** Android Logcat / iOS Xcode console → filter `PushApp` (use `PushApp:D` on Android for debug logs)

---

## Development (SDK maintainers)

```bash
npm install
npm run lint      # ESLint + Prettier + SwiftLint
npm run build
npm test          # TypeScript unit tests (Node 18+)
npm run test:contract  # verify error codes match across TS / Kotlin / Swift
npm run verify    # build + Android tests + SwiftLint
```

CI: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) · Release: [CHANGELOG.md](CHANGELOG.md) · [docs/RELEASE.md](docs/RELEASE.md)

---

## Version

Current version: `0.1.2` — see [CHANGELOG.md](CHANGELOG.md) and [docs/RELEASE.md](docs/RELEASE.md).

---

## Support

- [GitHub Issues](https://github.com/mehery-soccom/PushApp-Capacitor/issues)
- [Integration Guide](docs/Integration-Guide.md) — step-by-step setup
- [API reference](docs/api-reference.md) — run `npm run docgen` to refresh from TypeScript

---

## License

MIT
