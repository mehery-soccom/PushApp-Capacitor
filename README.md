# PushApp-Ionic SDK

A Capacitor plugin for push notifications, in-app messaging (popup, banner, roadblock, inline, tooltip), event tracking, and session handling in Ionic/Capacitor apps.

**Full integration walkthrough:** [docs/Integration-Guide.md](docs/Integration-Guide.md)

**Supported platforms:** Android and iOS (Ionic / Angular / Capacitor)

---

## Quick checklist

Your app should include:

- Firebase config: `android/app/google-services.json` and/or `ios/App/GoogleService-Info.plist`
- iOS Push Notifications capability + `AppDelegate` token forwarding (see Integration Guide)
- SDK calls in order: **`initialize()` → `register()` → `login()`**
- After login (when needed): `saveUserData()`, `setPageName()`, `sendEvent()`
- Inline/tooltip registration only if your PushApp campaigns use those surfaces

`register()` is safe to call on every app open — the native SDK **skips the register API** when the device is already registered with the same token. Customer profiles are **not** updated automatically; call `saveUserData()` explicitly after login.

---

## Installation

```bash
npm install pushapp-ionic @capacitor/push-notifications
npx cap sync
```

iOS:

```bash
cd ios/App && pod install
```

---

## Setup

### 1. Initialize (app startup)

```typescript
import { PushApp } from 'pushapp-ionic';

await PushApp.initialize({
  appId: 'yourtenant_1234567890', // full channel id from PushApp
  sandbox: false,                 // false = production (.pushapp.ai)
});
```

### 2. Register push token

**Android:**

```typescript
import { PushNotifications } from '@capacitor/push-notifications';

await PushNotifications.requestPermissions();
await PushNotifications.register();

PushNotifications.addListener('registration', async (token) => {
  await PushApp.register({ fcmToken: token.value });
});
```

**iOS:**

```typescript
await PushApp.register({
  apnsToken: apnsTokenValue,
  fcmToken: fcmTokenValue, // optional
});
```

Forward the APNs token in `AppDelegate` — see [Integration Guide](docs/Integration-Guide.md#4-ios-native-setup).

### 3. Login (after sign-in)

```typescript
await PushApp.login({ userId: 'USER_ID' });
```

### 4. Profile, pages, and events

```typescript
const headers = await PushApp.getDeviceHeaders();
const code = `${userId}_${headers['X-Device-ID'] ?? ''}`;

await PushApp.saveUserData({
  code,
  additionalInfo: { city: 'Mumbai', plan: 'premium' },
  cohorts: { segment: 'active_user' },
});

await PushApp.setPageName({ pageName: 'home' });

await PushApp.sendEvent({
  eventName: 'page_open',
  eventData: { page: 'home' },
});
```

---

## Environments

The tenant subdomain comes from your App ID (e.g. `synapsewave` from `synapsewave_1773818143216`).

| `sandbox` | Environment | API base | WebSocket |
|-----------|-------------|----------|-----------|
| `false` | **Production** | `https://{tenant}.pushapp.ai` | `wss://{tenant}.pushapp.ai/pushapp` |
| `true` | **Sandbox** | `https://{tenant}.pushapp.co.in` | `wss://{tenant}.pushapp.co.in/pushapp` |

---

## Inline placeholders

Use when PushApp campaigns deliver **inline** content into your app UI.

The SDK **automatically** tracks placeholder position on scroll, resize, and fixed headers (`ion-header`). Your app only registers and unregisters — no manual coordinates or scroll handlers.

### 1. Add a DOM slot

The HTML element `id` must match `placeholderId` (unless you pass `elementId`):

```html
<div id="promo-banner" class="promo-slot"></div>
```

### 2. Register / unregister

```typescript
// ionViewDidEnter / ngAfterViewInit
await PushApp.registerPlaceholder({ placeholderId: 'promo-banner' });

// ionViewWillLeave / ngOnDestroy
await PushApp.unregisterPlaceholder({ placeholderId: 'promo-banner' });
```

### Optional parameters

```typescript
await PushApp.registerPlaceholder({
  placeholderId: 'promo_banner',   // PushApp campaign slot id
  elementId: 'promo-banner',       // HTML id when different from placeholderId
  clipTopSelector: 'ion-header',   // clips inline below fixed chrome (default)
});
```

### Requirements

| Item | Rule |
|------|------|
| `placeholderId` | Must match the PushApp campaign inline slot id |
| HTML element | Use the same id, or pass `elementId` |
| Lifecycle | Register when the view enters; unregister when leaving |
| Scroll sync | Handled by the SDK — do **not** call `updatePlaceholder` from app code |

---

## Tooltip targets

Register anchor elements for native tooltips/popovers:

```html
<ion-fab-button id="deals-fab">...</ion-fab-button>
```

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

---

## API reference

### Core

| Method | When to call |
|--------|----------------|
| `initialize({ appId, sandbox? })` | App startup |
| `register({ fcmToken })` | After initialize — Android |
| `register({ apnsToken, fcmToken? })` | After initialize — iOS |
| `login({ userId })` | After register, when user signs in |
| `saveUserData({ code, additionalInfo, cohorts })` | After login |
| `setPageName({ pageName })` | On screen change |
| `sendEvent({ eventName, eventData })` | On user actions |
| `getDeviceHeaders()` | Anytime |
| `trackPushNotificationEvent({ token, event, ctaId? })` | Notification open / CTA tap |

### Inline

| Method | When to call |
|--------|----------------|
| `registerPlaceholder({ placeholderId, elementId?, clipTopSelector? })` | View enter — SDK syncs position automatically |
| `unregisterPlaceholder({ placeholderId })` | View leave |

### Tooltip

| Method | When to call |
|--------|----------------|
| `registerTooltipTarget({ targetId, x, y, width, height })` | View enter |
| `unregisterTooltipTarget({ targetId })` | View leave |

---

## Platform notes

### Android

- Minimum API 21
- ProGuard: `-keep class com.mehery.pushapp.** { *; }`

### iOS

- **Minimum iOS 15.2**
- Enable Push Notifications in Xcode
- Forward APNs token via `PushApp.shared.handleDeviceToken()` in `AppDelegate`

---

## Example app

See `example-app/` for a working WaveMart demo:

- `pushapp-setup.ts` — initialize + register with retry
- `home.page.ts` — inline placeholder, tooltip target, page events

---

## Troubleshooting

| Issue | What to check |
|-------|----------------|
| `initialize` fails | App ID format: `tenant_suffix` (e.g. `demo_1763369170735`) |
| `register` rejected | Call `initialize()` first; token must not be empty |
| `login` rejected | Call `register()` successfully first |
| No push on device | Use a real device; verify Firebase config and permissions |
| Inline never shows | `placeholderId` mismatch; register after DOM ready; call `setPageName` / `sendEvent` |
| Inline overlaps header | Ensure `clipTopSelector` matches your fixed header (default: `ion-header`) |
| Inline floats on scroll | Rebuild with latest SDK — scroll sync is automatic |

**Logs:** Android Logcat / iOS Xcode console → filter `PushApp`

---

## Version

Current version: `0.1.0`

---

## Support

- [GitHub Issues](https://github.com/mehery-soccom/PushApp-Capacitor/issues)
- [Integration Guide](docs/Integration-Guide.md)
- [Auto-generated API reference](docs/api-reference.md) (from TypeScript definitions)

---

## License

MIT
