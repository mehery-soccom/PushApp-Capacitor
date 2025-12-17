# PushApp-Ionic SDK

A lightweight Capacitor plugin to support push notifications, custom in-app messages (popup, banner, PiP, inline, tooltip), event tracking, and session handling for your Ionic/Capacitor apps.

---

## 📦 Installation

Install the package via npm:

```bash
npm install pushapp-ionic
npx cap sync
```

### Platform Setup

#### Android

1. **Firebase Configuration**: Ensure you have `google-services.json` in your `android/app/` directory.

2. **AndroidManifest.xml**: The SDK handles notification permissions automatically.

#### iOS

1. **Push Notifications Capability**: Enable Push Notifications in your Xcode project capabilities.

2. **AppDelegate Setup**: Add the following to your `AppDelegate.swift` to handle foreground notifications:

```swift
import UserNotifications

func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    // Set UNUserNotificationCenter delegate to handle foreground notifications
    UNUserNotificationCenter.current().delegate = self
    return true
}

// MARK: - UNUserNotificationCenterDelegate

func userNotificationCenter(_ center: UNUserNotificationCenter,
                            willPresent notification: UNNotification,
                            withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
    // Show notification banner even when app is in foreground
    if #available(iOS 14.0, *) {
        completionHandler([.banner, .sound, .badge])
    } else {
        completionHandler([.alert, .sound, .badge])
    }
}

func userNotificationCenter(_ center: UNUserNotificationCenter,
                            didReceive response: UNNotificationResponse,
                            withCompletionHandler completionHandler: @escaping () -> Void) {
    completionHandler()
}
```

---

## 🚀 Initialization

Initialize the SDK in your app's entry point (e.g., `app.component.ts` or `main.ts`):

```typescript
import { PushApp } from 'pushapp-ionic';

// Initialize the SDK
await PushApp.initialize({
  identifier: 'yourTenant#yourChannelId',
  sandbox: true // or false for production
});
```

To login the user:

```typescript
await PushApp.login({
  userId: 'user_id'
});
```

---

## 🎯 Event Tracking

Track user actions or custom events:

```typescript
await PushApp.sendEvent({
  eventName: 'button_clicked',
  eventData: {
    button_name: 'checkout',
    page: 'cart'
  }
});
```

### Page Tracking

Track page views:

```typescript
await PushApp.setPageName({
  pageName: 'home'
});
```

---

## 🔔 Notification Handling

The SDK automatically:
- Registers device tokens (FCM for Android, APNs for iOS)
- Handles push notification display
- Manages notification permissions

---

## 📱 In-App Notifications

The SDK automatically handles various in-app notification types:

### 1. Popup (Full-Screen)
Full-screen modal notifications that require user interaction.

### 2. Banner
Inline dismissible banners that appear at the top of the screen.

### 3. Picture-in-Picture (PiP)
Small floating views that can be expanded to full-screen popups.

**No integration required** - The SDK renders them automatically when triggered by your backend.

---

## 🎨 Inline Placeholder Views

Display in-app notifications directly in your app's UI instead of showing them as banners or roadblocks.

### Usage

1. **Create a container element** in your HTML:

```html
<div id="my-placeholder"></div>
```

2. **Register the placeholder** in your component:

```typescript
import { PushApp } from 'pushapp-ionic';
import { AfterViewInit } from '@angular/core';

export class HomePage implements AfterViewInit {
  ngAfterViewInit() {
    // Wait for DOM to be ready
    setTimeout(() => {
      const element = document.getElementById('my-placeholder');
      if (element) {
        const rect = element.getBoundingClientRect();
        
        await PushApp.registerPlaceholder({
          placeholderId: 'my_placeholder_id',
          x: Math.round(rect.left),
          y: Math.round(rect.top),
          width: Math.round(rect.width),
          height: Math.round(rect.height)
        });
      }
    }, 100);
  }
}
```

3. **Unregister when leaving the page**:

```typescript
ngOnDestroy() {
  PushApp.unregisterPlaceholder({
    placeholderId: 'my_placeholder_id'
  });
}
```

---

## 💬 Tooltip Targets

Register UI elements as anchor points for native tooltips/popovers that appear above or below the target element.

### Usage

1. **Create a target element** in your HTML:

```html
<ion-button id="tooltip-target">Click Me</ion-button>
```

2. **Register the tooltip target** in your component:

```typescript
import { PushApp } from 'pushapp-ionic';
import { AfterViewInit } from '@angular/core';

export class HomePage implements AfterViewInit {
  ngAfterViewInit() {
    setTimeout(() => {
      const element = document.getElementById('tooltip-target');
      if (element) {
        const rect = element.getBoundingClientRect();
        
        await PushApp.registerTooltipTarget({
          targetId: 'my_tooltip_target',
          x: Math.round(rect.left),
          y: Math.round(rect.top),
          width: Math.round(rect.width),
          height: Math.round(rect.height)
        });
      }
    }, 100);
  }
}
```

3. **Unregister when leaving the page**:

```typescript
ngOnDestroy() {
  PushApp.unregisterTooltipTarget({
    targetId: 'my_tooltip_target'
  });
}
```

---

## 📋 API Reference

### Core Methods

#### `initialize(options)`
Initialize the SDK.

**Parameters:**
- `identifier` (string, required): Your tenant and channel ID in format `tenant#channelId`
- `sandbox` (boolean, optional): Set to `true` for sandbox environment, `false` for production

**Returns:** `Promise<{ status: string }>`

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

Current version: `0.0.1`

---

## 💬 Support

- **GitHub Issues**: [Report issues or request features](https://github.com/mehery-soccom/PushApp-Capacitor/issues)
- **Documentation**: Check the example app for implementation details

---

## 📝 License

MIT
