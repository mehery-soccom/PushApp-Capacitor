# PushApp SDK — Integration Guide

This guide explains how to integrate the **PushApp Ionic/Capacitor SDK** into your mobile app for push notifications, in-app messaging, and user event tracking.

**Supported platforms:** Android and iOS (Ionic / Angular / Capacitor apps)

---

## 1. Before you start

### What you need from PushApp

| Item | Description |
|------|-------------|
| **App ID** | Your channel id, e.g. `yourtenant_1234567890`. The part before the first `_` is your tenant name. |
| **Environment** | Production (`sandbox: false`) or sandbox (`sandbox: true`). |
| **Firebase project** | You configure Firebase in your own app; PushApp uses it for push delivery. |

### Your app requirements

- Ionic + Capacitor project (Capacitor 5+ recommended)
- Node.js 18+
- Android Studio (Android) and/or Xcode (iOS)
- Firebase project with Android and/or iOS apps configured

---

## 2. Install the SDK

```bash
npm install pushapp-ionic @capacitor/push-notifications
npx cap sync
```

For iOS:

```bash
cd ios/App && pod install
```

---

## 3. Firebase setup

### Android

1. In [Firebase Console](https://console.firebase.google.com), add an Android app with your package name.
2. Download `google-services.json`.
3. Place it at: `android/app/google-services.json`
4. Rebuild the Android project.

### iOS

1. In Firebase Console, add an iOS app with your bundle id.
2. Download `GoogleService-Info.plist`.
3. Add it to your Xcode app target (`ios/App/App/`).
4. In Xcode, enable **Push Notifications** capability for your app target.

---

## 4. iOS native setup

Update `AppDelegate.swift` so push notifications work and the SDK receives the APNs token.

```swift
import UserNotifications
import PushappIonic

// AppDelegate must conform to UNUserNotificationCenterDelegate
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        if #available(iOS 15.2, *) {
            PushApp.shared.handleDeviceToken(deviceToken)
        }
    }

    // Show notifications when app is in foreground
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound, .badge])
        }
    }
}
```

**Minimum iOS version for the SDK:** 15.2

---

## 5. SDK integration (required)

### Call order

Always call SDK methods in this order:

```
initialize()  →  register()  →  login()
```

If the order is wrong, the SDK logs an error and rejects the promise. The app will not crash.

### Step A — Initialize (app startup)

Call once when your app starts (e.g. in `AppComponent` or root module).

```typescript
import { PushApp } from 'pushapp-ionic';

await PushApp.initialize({
  appId: 'YOUR_APP_ID',   // e.g. yourtenant_1234567890
  sandbox: false,         // true for sandbox, false for production
});
```

### Step B — Register push token

After `initialize()`, request push permission and register the device token.

**Android** — use the FCM token from Capacitor Push Notifications:

```typescript
import { PushNotifications } from '@capacitor/push-notifications';
import { PushApp } from 'pushapp-ionic';

await PushNotifications.requestPermissions();
await PushNotifications.register();

PushNotifications.addListener('registration', async (token) => {
  await PushApp.register({ fcmToken: token.value });
});
```

**iOS** — pass the APNs token (hex string). You can obtain it from your native layer or pass it from JS when available:

```typescript
await PushApp.register({
  apnsToken: apnsTokenValue,
  fcmToken: fcmTokenValue,  // optional
});
```

### Step C — Login (after user signs in)

Call when the user is authenticated in your app:

```typescript
await PushApp.login({ userId: 'USER_ID' });
```

Use your own user id (email, internal id, etc.) — the same id you use in your backend.

### Complete example

```typescript
import { PushNotifications } from '@capacitor/push-notifications';
import { PushApp } from 'pushapp-ionic';

const APP_ID = 'YOUR_APP_ID';
const SANDBOX = false;

async function setupPushApp(userId: string) {
  // 1. Initialize
  await PushApp.initialize({ appId: APP_ID, sandbox: SANDBOX });

  // 2. Register push token
  await PushNotifications.requestPermissions();
  await PushNotifications.register();

  await new Promise<void>((resolve, reject) => {
    PushNotifications.addListener('registration', async (token) => {
      try {
        await PushApp.register({ fcmToken: token.value });
        resolve();
      } catch (e) {
        reject(e);
      }
    });
  });

  // 3. Login
  await PushApp.login({ userId });
}
```

Call `setupPushApp(userId)` after your user logs in.

---

## 6. Customer profile (recommended after login)

Send user profile and segmentation data after login:

```typescript
const headers = await PushApp.getDeviceHeaders();
const deviceId = headers['X-Device-ID'] ?? '';
const code = `${userId}_${deviceId}`;

await PushApp.saveUserData({
  code,
  additionalInfo: {
    city: 'Mumbai',
    plan: 'premium',
  },
  cohorts: {
    segment: 'active_user',
  },
});
```

---

## 7. Event and page tracking

### Track page views

Call when the user navigates to a screen:

```typescript
await PushApp.setPageName({ pageName: 'home' });
```

### Track custom events

```typescript
await PushApp.sendEvent({
  eventName: 'button_clicked',
  eventData: {
    source: 'checkout',
    method: 'card',
  },
});
```

---

## 8. Optional — In-app message placements

Use these only if PushApp campaigns include **inline** or **tooltip** messages.

### Inline placeholder

1. Add a container in your HTML:

```html
<div id="promo-banner"></div>
```

2. Register its position after the view loads:

```typescript
const el = document.getElementById('promo-banner');
const rect = el.getBoundingClientRect();

await PushApp.registerPlaceholder({
  placeholderId: 'promo_banner',
  x: Math.round(rect.left),
  y: Math.round(rect.top),
  width: Math.round(rect.width),
  height: Math.round(rect.height),
});
```

3. Unregister when leaving the page:

```typescript
await PushApp.unregisterPlaceholder({ placeholderId: 'promo_banner' });
```

### Tooltip target

Same pattern using `registerTooltipTarget` / `unregisterTooltipTarget` with a `targetId`.

---

## 9. Integration checklist

Use this before going live:

- [ ] `google-services.json` (Android) and/or `GoogleService-Info.plist` (iOS) added
- [ ] iOS Push Notifications capability enabled
- [ ] `PushApp.initialize()` called at app startup with your App ID
- [ ] `PushApp.register()` called after push token is received
- [ ] `PushApp.login()` called after user authentication
- [ ] `saveUserData()` called after login (if using profiles/segments)
- [ ] `setPageName()` called on main screens
- [ ] Tested on a real device (push does not work on emulators/simulators for FCM/APNs)

---

## 10. Troubleshooting

| Issue | What to check |
|-------|----------------|
| `initialize` fails | App ID format must be `tenant_suffix` (e.g. `demo_1763369170735`). Check Logcat (Android) or Xcode console (iOS). |
| `register` rejected | Call `initialize()` first. Ensure FCM/APNs token is not empty. |
| `login` rejected | Call `register()` successfully before `login()`. |
| No push on device | Real device required. Check Firebase config files and notification permissions. |
| iOS token not received | Verify `AppDelegate` forwards token via `PushApp.shared.handleDeviceToken()`. |

**Android logs:** Logcat → filter `PushApp`  
**iOS logs:** Xcode console → filter `PushApp`

---

## 11. API summary

| Method | When to call |
|--------|----------------|
| `initialize({ appId, sandbox })` | App startup |
| `register({ fcmToken })` | After initialize, when push token is ready (Android) |
| `register({ apnsToken, fcmToken? })` | After initialize (iOS) |
| `login({ userId })` | After register, when user is signed in |
| `saveUserData({ code, additionalInfo, cohorts })` | After login |
| `setPageName({ pageName })` | On screen change |
| `sendEvent({ eventName, eventData })` | On user actions |
| `getDeviceHeaders()` | Anytime (returns device id and headers) |

---

## 12. Support

For integration help or issues, contact your PushApp account team or open an issue on GitHub:

https://github.com/mehery-soccom/PushApp-Capacitor/issues

---

*Document version: 1.0 — PushApp Ionic SDK*
