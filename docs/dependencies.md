# Dependency inventory (SBOM summary)

Runtime dependencies shipped with or required by **pushapp-ionic@0.1.2**.  
Regenerate audit hints with: `npm run audit:deps`

## npm package

| Package | Role | Version (dev) |
|---------|------|----------------|
| `@capacitor/core` | Peer — Capacitor bridge | `>= 7.0.0` |
| `@capacitor/push-notifications` | Peer — FCM/APNs token in host app | `>= 7.0.0` |

## Android (`android/build.gradle`)

| Dependency | Version | Purpose |
|------------|---------|---------|
| `com.android.tools.build:gradle` | 8.7.2 | Build (dev) |
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | 1.9.22 | Build (dev) |
| `org.jetbrains.kotlin:kotlin-stdlib` | 1.9.22 | Runtime |
| `androidx.appcompat:appcompat` | 1.7.0 | UI compat |
| `com.google.android.material:material` | 1.11.0 | In-app UI components |
| `com.github.bumptech.glide:glide` | 4.16.0 | Notification / in-app images |
| `com.google.firebase:firebase-bom` | 32.7.0 | FCM version alignment |
| `com.google.firebase:firebase-messaging` | (BOM) | Push delivery |
| `com.squareup.okhttp3:okhttp` | 4.11.0 | HTTP + WebSocket |
| `com.google.gms:google-services` | 4.4.0 | Gradle plugin (host app) |

**Transitive:** Firebase BOM pulls Google Play Services / Firebase SDKs. Host apps must provide `google-services.json`.

## iOS

| Dependency | Source | Purpose |
|------------|--------|---------|
| Capacitor 7 | `capacitor-swift-pm` / CocoaPods | Plugin bridge |
| EasyTipView | CocoaPods (`PushappIonic.podspec`) | Tooltip campaigns |
| APNs / UserNotifications | System | Push |
| URLSession | System | HTTP / WebSocket |

SPM consumers get Capacitor only; tooltip support requires CocoaPods.

## Security notes

- Review [Firebase release notes](https://firebase.google.com/support/release-notes/android) and [OkHttp changelog](https://square.github.io/okhttp/changelogs/) when bumping Android deps.
- Glide 4.16.0 — check [Glide releases](https://github.com/bumptech/glide/releases) for CVEs before upgrades.
- Run `npm audit` on the plugin repo and on integrator apps separately.

## Last reviewed

2026-06-29 — pushapp-ionic 0.1.2 GA hardening release.
