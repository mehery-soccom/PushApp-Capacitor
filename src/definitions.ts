// Example: In your plugin's definitions.ts or index.d.ts file

export interface PlaceholderRegisterOptions {
  placeholderId: string;
  /** HTML element id; defaults to `placeholderId`. Must exist in the page DOM. */
  elementId?: string;
  /** CSS selector for fixed top chrome; bottom edge is used as `clipTop`. Defaults to `ion-header`. */
  clipTopSelector?: string;
}

export interface UIOptions {
  x: number;
  y: number;
  width: number;
  height: number;
  /** Viewport Y (px) below fixed header/chrome; inline content is clipped/hidden above this line. Set automatically when using registerPlaceholder on native. */
  clipTop?: number;
}

export interface PushAppPlugin {
  // Existing Core Methods
  /**
   * Initialize the SDK. Pass your **channel id** as **App ID** (`appId`).
   * The tenant subdomain is derived from the substring before the first `_` (e.g. `demo` from `demo_1763369170735`).
   * Use `identifier` only for backward compatibility; it is the same value as `appId`.
   *
   * `debugMode` enables verbose native logs in debug builds only (tokens/PII are redacted).
   * `slackWebhookUrl` opts in to posting API request/response logs to your Slack webhook (development only).
   */
  initialize(
    options:
      | { appId: string; sandbox?: boolean; debugMode?: boolean; slackWebhookUrl?: string }
      | { identifier: string; sandbox?: boolean; debugMode?: boolean; slackWebhookUrl?: string },
  ): Promise<{ status: string }>;
  /**
   * POST push token to `/pushapp/api/device/register`.
   * Call after `initialize`. Skips the API when the device is already registered with the same token.
   * - Android: provide `fcmToken` (it is sent as backend `token`).
   * - iOS: provide `apnsToken` (sent as backend `token`) and optional `fcmToken` (sent as backend `fcm_token`).
   * Legacy fallback: `token` is accepted as alias for the platform primary token.
   */
  register(options: {
    apnsToken?: string;
    fcmToken?: string;
    token?: string;
  }): Promise<{ status: string; success: boolean }>;
  login(options: { userId: string }): Promise<{ status: string }>;
  /** Delink the device from the current user. Clears local session; calls server delink when a user was logged in. */
  logout(): Promise<{ status: string }>;
  /** Create or update customer profile (PUT). Call after login with code = userId_deviceId. */
  saveUserData(options: {
    code: string;
    additionalInfo: Record<string, unknown>;
    cohorts: Record<string, unknown>;
  }): Promise<{ status: string; success: boolean }>;
  getDeviceHeaders(): Promise<{ [key: string]: string }>;
  sendEvent(options: { eventName: string; eventData: { [key: string]: any } }): Promise<{ status: string }>;
  setPageName(options: { pageName: string }): Promise<{ status: string }>;

  // Inline Placeholder Methods
  /**
   * Register an inline placeholder. On native platforms the SDK tracks DOM position
   * (scroll, resize, fixed headers) automatically — pass `placeholderId` only.
   * The HTML element id should match `placeholderId` unless `elementId` is set.
   */
  registerPlaceholder(options: PlaceholderRegisterOptions): Promise<{ status: string }>;
  /** @internal Used by the SDK to reposition inline placeholders during scroll sync. */
  updatePlaceholder(options: { placeholderId: string } & UIOptions): Promise<{ status: string }>;
  unregisterPlaceholder(options: { placeholderId: string }): Promise<{ status: string }>;

  // ⭐️ NEW: Tooltip Target Methods
  /** Registers a UI element as an anchor target for native tooltips/popovers. */
  registerTooltipTarget(options: { targetId: string } & UIOptions): Promise<{ status: string }>;
  /** Unregisters a tooltip anchor target. */
  unregisterTooltipTarget(options: { targetId: string }): Promise<{ status: string }>;

  /** Tracks a push notification event (e.g. "opened" or "cta"). Use when user opens a notification or taps a CTA. */
  trackPushNotificationEvent(options: { token: string; event: string; ctaId?: string }): Promise<{ success: boolean }>;
}
