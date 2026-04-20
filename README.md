# PushApp-Ionic SDK

A lightweight Capacitor plugin to support push notifications, custom in-app messages (popup, banner, PiP, inline, tooltip), event tracking, and session handling for your Ionic/Capacitor apps.

---

## What Your App Must Add (Quick Checklist)

Your Ionic/Capacitor app should include all of the following:

- Firebase config files:
  - Android: `android/app/google-services.json`
  - iOS: `ios/App/GoogleService-Info.plist`
- Push capability on iOS and foreground notification handling in `AppDelegate.swift`
- SDK initialization at app startup (`PushApp.initialize`)
- Push token registration from app code (`PushApp.registerPushToken`)
- User identity and tracking calls where they match your user journey:
  - `PushApp.login`
  - `PushApp.setPageName`
  - `PushApp.sendEvent`
  - `PushApp.saveUserData` (after login)
- Placeholder/tooltip registration only if you use inline/tooltip in-app surfaces

---

## 📦 Installation

```bash
npm install pushapp-ionic
npx cap sync
```

For iOS, then run:

```bash
cd ios/App && pod install
```

---

## Step-by-Step Setup (Ionic/Capacitor)

### 1) Add Firebase files

- Android: place `google-services.json` in `android/app/`
- iOS: add `GoogleService-Info.plist` to your app target

### 2) iOS push setup

Enable **Push Notifications** capability in Xcode and add foreground handling:

```swift
import UserNotifications

func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    UNUserNotificationCenter.current().delegate = self
    return true
}

func userNotificationCenter(_ center: UNUserNotificationCenter,
                            willPresent notification: UNNotification,
                            withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
    if #available(iOS 14.0, *) {
        completionHandler([.banner, .sound, .badge])
    } else {
        completionHandler([.alert, .sound, .badge])
    }
}
```

### 3) Initialize SDK at app startup

```typescript
import { PushApp } from 'pushapp-ionic';

await PushApp.initialize({
  appId: 'demo_1763369170735', // full channel id (tenant prefix inside this string)
  sandbox: false
});
```

### 4) Register push token from app code

- Android: pass `fcmToken`
- iOS: pass `apnsToken`, optionally `fcmToken`

```typescript
// Android
await PushApp.registerPushToken({ fcmToken });

// iOS
await PushApp.registerPushToken({
  apnsToken,
  fcmToken, // optional
});
```

### 5) Link user/session + events

```typescript
await PushApp.login({ userId: 'user_123' });

await PushApp.setPageName({ pageName: 'home' });

await PushApp.sendEvent({
  eventName: 'button_clicked',
  eventData: {
    source: 'welcome_screen',
    method: 'google'
  }
});
```

### 6) Update customer profile (recommended after login)

```typescript
const headers = await PushApp.getDeviceHeaders();
const deviceId = headers['X-Device-ID'] ?? '';
const code = `user_123_${deviceId}`;

await PushApp.saveUserData({
  code,
  additionalInfo: { city: 'Mumbai', plan: 'free' },
  cohorts: { segment: 'trial' }
});
```

### 7) Optional: inline + tooltip in-app placements

Use:

- `PushApp.registerPlaceholder` / `PushApp.unregisterPlaceholder`
- `PushApp.registerTooltipTarget` / `PushApp.unregisterTooltipTarget`

with element bounds from `getBoundingClientRect()` in your page/component lifecycle.

---

## 📋 API Reference

### Core Methods

#### `initialize(options)`
Initialize the SDK.

**Parameters:**
- `appId` (string, required unless using legacy `identifier`): **App ID** — your full channel id (e.g. `demo_1763369170735`). The SDK derives the tenant subdomain from the substring before the first `_` (`demo` in this example). The channel id sent to APIs is this full App ID string.
- `identifier` (string, optional legacy alias): Same value as `appId`; supported for backward compatibility only.
- `sandbox` (boolean, optional): Set to `true` for sandbox environment, `false` for production

**Returns:** `Promise<{ status: string }>`

#### `registerPushToken(options)`
POST the push token to `/pushapp/api/device/register` (same endpoint as native auto-registration). Use when you obtain the token in TypeScript (e.g. `@capacitor/push-notifications`) instead of relying only on native registration.

**Parameters:**
- `apnsToken` (string, optional): iOS APNs token. This is sent as backend `token`.
- `fcmToken` (string, optional): Android FCM token (required on Android). On iOS, optional Firebase token sent as backend `fcm_token`.
- `token` (string, optional legacy alias): Backward-compatible alias for the platform primary token.

**Returns:** `Promise<{ status: string; success: boolean }>`

**Examples:**

```typescript
import { PushNotifications } from '@capacitor/push-notifications';
import { PushApp } from 'pushapp-ionic';

await PushApp.initialize({ appId: 'demo_1763369170735', sandbox: false });

PushNotifications.addListener('registration', async (info) => {
  await PushApp.registerPushToken({ fcmToken: info.value });
});
```

```typescript
// iOS example: APNs token required, FCM token optional
await PushApp.registerPushToken({
  apnsToken: apnsTokenValue,
  fcmToken: fcmTokenValue, // optional
});
```

#### `login(options)`
Login a user to the SDK.

**Parameters:**
- `userId` (string, required): Unique user identifier

**Returns:** `Promise<{ status: string }>`

#### `sendEvent(options)`
Send a custom event to track user actions.

**Parameters:**
- `eventName` (string, required): Name of the event
- `eventData` (object, required): Event data payload

**Returns:** `Promise<{ status: string }>`

#### `setPageName(options)`
Track the current page/view.

**Parameters:**
- `pageName` (string, required): Name of the current page

**Returns:** `Promise<{ status: string }>`

#### `getDeviceHeaders()`
Get device information headers.

**Returns:** `Promise<{ [key: string]: string }>`

#### `saveUserData(options)`
Create or update customer profile data.

**Parameters:**
- `code` (string, required): Unique customer code. Recommended format: `userId_deviceId`
- `additionalInfo` (object, required): Profile attributes (example: `dob`, `gender`, `expiry_date`)
- `cohorts` (object, required): Segmentation/cohort attributes (example: `plan`, `region`)

**Returns:** `Promise<{ status: string; success: boolean }>`

### Placeholder Methods

#### `registerPlaceholder(options)`
Register a placeholder view for inline content display.

**Parameters:**
- `placeholderId` (string, required): Unique identifier for the placeholder
- `x` (number, required): X coordinate in pixels (from `getBoundingClientRect()`)
- `y` (number, required): Y coordinate in pixels (from `getBoundingClientRect()`)
- `width` (number, required): Width in pixels
- `height` (number, required): Height in pixels

**Returns:** `Promise<{ status: string }>`

#### `unregisterPlaceholder(options)`
Unregister a placeholder view.

**Parameters:**
- `placeholderId` (string, required): The placeholder ID to unregister

**Returns:** `Promise<{ status: string }>`

### Tooltip Methods

#### `registerTooltipTarget(options)`
Register a UI element as an anchor for native tooltips.

**Parameters:**
- `targetId` (string, required): Unique identifier for the tooltip target
- `x` (number, required): X coordinate in pixels (from `getBoundingClientRect()`)
- `y` (number, required): Y coordinate in pixels (from `getBoundingClientRect()`)
- `width` (number, required): Width in pixels
- `height` (number, required): Height in pixels

**Returns:** `Promise<{ status: string }>`

#### `unregisterTooltipTarget(options)`
Unregister a tooltip target.

**Parameters:**
- `targetId` (string, required): The target ID to unregister

**Returns:** `Promise<{ status: string }>`

---

## 🔧 Platform-Specific Notes

### Android

- **Minimum SDK**: Android API 21 (Lollipop)
- **Permissions**: The SDK automatically requests notification permissions
- **ProGuard**: If using ProGuard, add:
  ```proguard
  -keep class com.mehery.pushapp.** { *; }
  ```

### iOS

- **Minimum Version**: iOS 13.0
- **Capabilities**: Enable Push Notifications in Xcode
- **Foreground Notifications**: Requires `UNUserNotificationCenterDelegate` setup (see Platform Setup)

---

## 📄 Example Implementation

See the `example-app/` directory for a complete working example including:
- Initialization and login
- Event tracking
- Placeholder view registration
- Tooltip target registration

---

## 🏷️ Version

Current version: `0.0.2`

---

## 💬 Support

- **GitHub Issues**: [Report issues or request features](https://github.com/mehery-soccom/PushApp-Capacitor/issues)
- **Documentation**: Check the example app for implementation details

---

## 📝 License

MIT
