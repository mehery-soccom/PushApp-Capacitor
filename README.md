# pushapp-ionic

Pushapp Capactior plugin

## Install

```bash
npm install pushapp-ionic
npx cap sync
```

## API

<docgen-index>

* [`initialize(...)`](#initialize)
* [`login(...)`](#login)
* [`getDeviceHeaders()`](#getdeviceheaders)
* [`sendEvent(...)`](#sendevent)
* [`setPageName(...)`](#setpagename)
* [`registerPlaceholder(...)`](#registerplaceholder)
* [`unregisterPlaceholder(...)`](#unregisterplaceholder)
* [`registerTooltipTarget(...)`](#registertooltiptarget)
* [`unregisterTooltipTarget(...)`](#unregistertooltiptarget)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### initialize(...)

```typescript
initialize(options: { identifier: string; sandbox?: boolean; }) => Promise<{ status: string; }>
```

| Param         | Type                                                    |
| ------------- | ------------------------------------------------------- |
| **`options`** | <code>{ identifier: string; sandbox?: boolean; }</code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### login(...)

```typescript
login(options: { userId: string; }) => Promise<{ status: string; }>
```

| Param         | Type                             |
| ------------- | -------------------------------- |
| **`options`** | <code>{ userId: string; }</code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### getDeviceHeaders()

```typescript
getDeviceHeaders() => Promise<{ [key: string]: string; }>
```

**Returns:** <code>Promise&lt;{ [key: string]: string; }&gt;</code>

--------------------


### sendEvent(...)

```typescript
sendEvent(options: { eventName: string; eventData: { [key: string]: any; }; }) => Promise<{ status: string; }>
```

| Param         | Type                                                                    |
| ------------- | ----------------------------------------------------------------------- |
| **`options`** | <code>{ eventName: string; eventData: { [key: string]: any; }; }</code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### setPageName(...)

```typescript
setPageName(options: { pageName: string; }) => Promise<{ status: string; }>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ pageName: string; }</code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### registerPlaceholder(...)

```typescript
registerPlaceholder(options: { placeholderId: string; } & UIOptions) => Promise<{ status: string; }>
```

| Param         | Type                                                                         |
| ------------- | ---------------------------------------------------------------------------- |
| **`options`** | <code>{ placeholderId: string; } & <a href="#uioptions">UIOptions</a></code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### unregisterPlaceholder(...)

```typescript
unregisterPlaceholder(options: { placeholderId: string; }) => Promise<{ status: string; }>
```

| Param         | Type                                    |
| ------------- | --------------------------------------- |
| **`options`** | <code>{ placeholderId: string; }</code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### registerTooltipTarget(...)

```typescript
registerTooltipTarget(options: { targetId: string; } & UIOptions) => Promise<{ status: string; }>
```

Registers a UI element as an anchor target for native tooltips/popovers.

| Param         | Type                                                                    |
| ------------- | ----------------------------------------------------------------------- |
| **`options`** | <code>{ targetId: string; } & <a href="#uioptions">UIOptions</a></code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### unregisterTooltipTarget(...)

```typescript
unregisterTooltipTarget(options: { targetId: string; }) => Promise<{ status: string; }>
```

Unregisters a tooltip anchor target.

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ targetId: string; }</code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### Interfaces


#### UIOptions

Defines the common coordinate and dimension options for UI element registration.

| Prop         | Type                |
| ------------ | ------------------- |
| **`x`**      | <code>number</code> |
| **`y`**      | <code>number</code> |
| **`width`**  | <code>number</code> |
| **`height`** | <code>number</code> |

</docgen-api>

## Placeholder Views

Placeholder views allow you to display in-app notifications directly in your app's UI instead of showing them as banners or roadblocks. When a placeholder view is registered, it automatically sends a `widget_open` event with the placeholder ID, and any matching notifications will be rendered in the placeholder view.

### Android

Use the `PlaceholderView` in your XML layouts:

```xml
<com.mehery.pushapp.PlaceholderView
    android:id="@+id/placeholder_view"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:placeholderId="my_placeholder_id" />
```

Or programmatically:

```kotlin
val placeholderView = PlaceholderView(context)
placeholderView.setPlaceholderId("my_placeholder_id")
// Add to your layout
```

### iOS

Use `PushAppInlineView` in your SwiftUI views:

```swift
import PushAppPlugin

struct ContentView: View {
    var body: some View {
        VStack {
            // Your content
            PushAppInlineView(placeholderId: "my_placeholder_id")
                .frame(height: 200)
        }
    }
}
```

### How It Works

1. When a placeholder view is created/attached, it automatically:
   - Registers itself with the SDK
   - Sends a `widget_open` event with `eventData: { compare: "your_placeholder_id" }`

2. After sending the event, the SDK polls for in-app notifications

3. If a notification response contains a `placeholderId` matching your placeholder (from `event_data.compare`), the HTML content is automatically rendered in your placeholder view instead of showing as a banner or roadblock

4. The placeholder view automatically unregisters when detached/removed
